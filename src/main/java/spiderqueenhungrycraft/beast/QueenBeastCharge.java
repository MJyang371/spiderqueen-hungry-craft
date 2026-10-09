package spiderqueenhungrycraft.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastChargeStatePacket;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The continuous charge of the beast (Phase 8D).
 *
 * <p>The old design of a charged dash is gone for good: there is no wind up, no fixed distance, no fixed
 * duration and no release that launches anything. Holding the key starts the charge immediately, holding it
 * keeps the beast running, and letting go ends it. The only price is time: every twenty active ticks cost one
 * food level, never a lump of four at the start.
 *
 * <p>The server owns everything that matters. It steers the direction with a limited turn rate, sets the
 * velocity of the beast each tick and hands it to the client, decides what is destroyed, who is hit, what it
 * costs and when a hard impact happens. The client only tells the server that the key went down or up, and it
 * is told back when a charge runs so it may rumble the screen.
 *
 * <p>A hard stop ends the charge and leaves the beast standing exactly where it hit: no rotation, no rotated
 * box, no ragdoll, no recoil and no teleport backwards. The impact itself is a real explosion, which is why
 * obsidian, netherite and bedrock are exactly what they should be for a charging spider: an immovable wall.
 */
public final class QueenBeastCharge
{
    /** How long the charge stays locked after a hard impact, in ticks, as a safety net for a lost release. */
    private static final int BLOCK_FALLBACK_TICKS = 200;

    private QueenBeastCharge()
    {
    }

    /** Client request: the charge key went down. The charge starts right away, without any wind up. */
    public static void press(ServerPlayer player)
    {
        QueenBeastState state = usable(player);

        if (state == null || state.isChargeActive() || state.isChargeBlocked())
        {
            return;
        }

        // No fuel, no charge: the beast runs on its hunger, like everything else of the form.
        if (player.getFoodData().getFoodLevel() <= 0)
        {
            return;
        }

        state.setChargeActive(true);
        state.setChargeActiveTicks(0);
        state.setChargeDirection(QueenBeastJump.horizontalForward(player));
        state.getChargeHits().clear();

        syncState(player, true);
    }

    /** Client request: the charge key went up. The charge ends here, and the hard impact lock is lifted. */
    public static void release(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        // The release always unlocks the hard impact lock, that is the only way to charge again.
        state.setChargeBlocked(false);
        state.setChargeBlockedTicks(0);

        if (state.isChargeActive())
        {
            end(player, state);
        }
    }

    /** Ends a running charge for good, used by every exit out of the form so a human never keeps charging. */
    public static void cancel(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        state.setChargeBlocked(false);
        end(player, state);
    }

    /** One server tick of a beast that might be charging. */
    public static void tick(ServerPlayer player, QueenBeastState state)
    {
        if (state.isChargeBlocked())
        {
            // Safety net: a lost release packet may never lock the charge forever. The counter belongs to that
            // one beast state, so two charging queens can never reset each other.
            state.setChargeBlockedTicks(state.getChargeBlockedTicks() + 1);

            if (state.getChargeBlockedTicks() > BLOCK_FALLBACK_TICKS)
            {
                state.setChargeBlocked(false);
                state.setChargeBlockedTicks(0);
            }
        }

        if (!state.isChargeActive())
        {
            return;
        }

        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || !QueenDataCapability.isQueen(player) || !QueenBeastState.isTransformed(player))
        {
            end(player, state);
            return;
        }

        QueenBeastTuning.Charge charge = QueenBeastTuning.get().charge();
        Vec3 direction = steer(player, state, charge.maxTurnPerTick());
        ServerLevel level = (ServerLevel) player.level();
        AABB body = player.getBoundingBox();

        // The corridor of this tick: the full body, moving exactly where the charge wants to go. The vertical
        // part of the movement is zero on purpose, so gravity, a landing or a small negative vertical velocity
        // can never turn the charge into a shaft digger, and the feet plane is never inflated downwards.
        Vec3 motion = new Vec3(direction.x * charge.speed(), 0.0D, direction.z * charge.speed());
        QueenBeastMovementCorridor.Scan scan = QueenBeastMovementCorridor.destroy(level, player, body, motion,
                QueenBeastConstants.BEAST_CORRIDOR_MARGIN_HORIZONTAL,
                QueenBeastConstants.BEAST_CORRIDOR_MARGIN_TOP, QueenBeastTuning.RuleProfile.CHARGE,
                QueenBeastConstants.BEAST_CHARGE_MAX_BLOCKS_PER_TICK);

        // A real hard stop ends the charge with one explosion, right where the full body was refused.
        if (scan.hardStop())
        {
            hardImpact(player, state, direction);
            return;
        }

        // Only a corridor the whole body can really occupy lets the charge advance this tick. A wall that is
        // merely waiting for the next tick of block budget holds the beast for one tick instead of exploding,
        // because stone is breakable and clearing it is only a question of time.
        boolean advancing = QueenBeastMovementCorridor.canOccupy(level, player, body.move(motion.x, 0.0D,
                motion.z));

        if (!advancing && !scan.pendingBreakable())
        {
            QueenBeastMovementCorridor.warn("charge held at " + player.blockPosition()
                    + " without a rule source, nothing was broken");
        }

        if (!advancing)
        {
            motion = Vec3.ZERO;
        }

        // The speed is set, not added, so the pace is exactly the pace of the tuning file. The vertical motion
        // stays untouched, gravity and falls keep working like they always did.
        player.setDeltaMovement(motion.x, player.getDeltaMovement().y, motion.z);
        player.hurtMarked = true;
        player.hasImpulse = true;

        AABB reached = body.move(motion.x, 0.0D, motion.z);
        AABB sweep = QueenBeastMovementCorridor.entitySweep(state.getLastBody() == null ? body : state.getLastBody(),
                reached, 0.2D);

        hitTargets(level, player, state, sweep, direction);
        state.setLastBody(body);

        // Only a tick that really pushed the beast forward counts as an active charge tick and may cost food.
        if (advancing)
        {
            state.setChargeActiveTicks(state.getChargeActiveTicks() + 1);
            payFood(player, state);
        }
    }

    /** Ends the charge session and tells the client to stop its rumble. */
    private static void end(ServerPlayer player, QueenBeastState state)
    {
        if (!state.isChargeActive())
        {
            return;
        }

        state.setChargeActive(false);
        state.setChargeActiveTicks(0);
        state.setChargeDirection(Vec3.ZERO);
        state.getChargeHits().clear();
        syncState(player, false);
    }

    /** Turns the charge direction towards the look direction, at most the allowed turn rate per tick. */
    private static Vec3 steer(ServerPlayer player, QueenBeastState state, double maxTurnPerTick)
    {
        Vec3 wanted = QueenBeastJump.horizontalForward(player);
        Vec3 current = state.getChargeDirection();

        if (current.lengthSqr() < 1.0E-6D)
        {
            state.setChargeDirection(wanted);
            return wanted;
        }

        double currentAngle = Math.atan2(current.z, current.x);
        double wantedAngle = Math.atan2(wanted.z, wanted.x);
        double turn = Mth.clamp(Mth.wrapDegrees(Math.toDegrees(wantedAngle - currentAngle)), -maxTurnPerTick,
                maxTurnPerTick);
        double next = currentAngle + Math.toRadians(turn);
        Vec3 direction = new Vec3(Math.cos(next), 0.0D, Math.sin(next));

        state.setChargeDirection(direction);
        return direction;
    }

    /** Hits every legal target of the swept volume, and never the same target twice in one charge. */
    private static int hitTargets(ServerLevel level, ServerPlayer player, QueenBeastState state, AABB swept,
            Vec3 direction)
    {
        QueenBeastTuning.Charge charge = QueenBeastTuning.get().charge();
        int hits = 0;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, swept,
                candidate -> isCandidate(player, candidate)))
        {
            if (!state.getChargeHits().add(target.getUUID()))
            {
                continue;
            }

            if (!target.hurt(QueenBeastDamageTypes.charge(player), (float) charge.damage()))
            {
                continue;
            }

            // Launched forward along the charge and lifted up, never thrown across the whole biome.
            target.push(direction.x * charge.knockForward(), charge.knockUp(),
                    direction.z * charge.knockForward());
            target.hurtMarked = true;
            hits++;
        }

        return hits;
    }

    /** The one and only hard impact: the charge stops, the beast is locked, and a real explosion happens. */
    private static void hardImpact(ServerPlayer player, QueenBeastState state, Vec3 direction)
    {
        ServerLevel level = (ServerLevel) player.level();
        QueenBeastTuning.Charge charge = QueenBeastTuning.get().charge();
        Vec3 at = frontPosition(player, direction);

        end(player, state);
        state.setChargeBlocked(true);
        state.setChargeBlockedTicks(0);

        // A real explosion with the normal world interaction of TNT: it hurts the queen, hurts everybody else
        // and breaks what TNT breaks. The absolute super armor of the beast still refuses the impulse, so the
        // queen eats the damage but is never thrown around by it.
        level.explode(player, at.x, at.y, at.z, (float) charge.explosionPower(), Level.ExplosionInteraction.TNT);

        spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.debug("[charge] hard impact at {} {} {}", at.x, at.y,
                at.z);
    }

    /** @return the spot in front of the beast where a hard impact really happened. */
    private static Vec3 frontPosition(ServerPlayer player, Vec3 direction)
    {
        return player.position().add(direction.scale(player.getBbWidth() / 2.0D + 0.6D))
                .add(0.0D, player.getBbHeight() * 0.5D, 0.0D);
    }

    /** Every twenty active ticks cost one food level, and an empty bar ends the form like it always does. */
    private static void payFood(ServerPlayer player, QueenBeastState state)
    {
        QueenBeastTuning.Charge charge = QueenBeastTuning.get().charge();

        if (charge.foodInterval() <= 0 || state.getChargeActiveTicks() % charge.foodInterval() != 0)
        {
            return;
        }

        FoodData food = player.getFoodData();
        int remaining = Math.max(food.getFoodLevel() - charge.foodCost(), 0);
        food.setFoodLevel(remaining);

        if (remaining <= 0)
        {
            end(player, state);
            QueenBeastController.forceUntransform(player, QueenBeastController.RevertCause.STARVING);
        }
    }

    /** Tells the client whether a charge of the local beast runs, only for its rumble and lens widening. */
    private static void syncState(ServerPlayer player, boolean active)
    {
        HungryCraftNetwork.sendToPlayer(new S2CBeastChargeStatePacket(active), player);
    }

    /** @return true when that creature may be hit by a charge of that player. */
    private static boolean isCandidate(ServerPlayer player, LivingEntity candidate)
    {
        if (candidate == player || candidate.isRemoved() || !candidate.isAlive())
        {
            return false;
        }

        if (candidate instanceof Player other)
        {
            return !other.isSpectator() && player.canHarmPlayer(other);
        }

        return true;
    }

    /** @return the form state when that player may charge at all, null otherwise. */
    private static QueenBeastState usable(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || player.isPassenger()
                || !QueenDataCapability.isQueen(player) || !QueenBeastState.isTransformed(player))
        {
            return null;
        }

        return QueenBeastState.of(player).orElse(null);
    }
}
