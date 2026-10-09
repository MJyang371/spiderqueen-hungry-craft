package spiderqueenhungrycraft.soulfire.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.soulfire.SoulfireBreath;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireOrders;
import spiderqueenhungrycraft.soulfire.SoulfireTargeting;

/**
 * The one goal that commands a soul fire dragon (round 7C).
 *
 * <p>It is the whole behaviour of the unit, and it is deliberately one goal instead of five: a single owner of the
 * movement flag can not fight itself, and the priority of the round is written in one place instead of being spread
 * over half a dozen goals that all believe they are in charge:
 *
 * <ol>
 * <li><b>the breath skill</b> of the ability key (a place the queen pointed at) - it runs until another order
 * arrives,</li>
 * <li><b>the forced victim</b> (a creature the queen pointed at),</li>
 * <li><b>the scout move</b> (the errand: walk when it is close, fly when it is further than fifteen blocks),</li>
 * <li><b>the standing order</b>: follow, hold, patrol or free attack.</li>
 * </ol>
 *
 * <p>Every one of those is a decision of its own and none of them is remembered here: the goal reads them from the
 * entity every tick, so a new command is obeyed in the very tick it arrives - there is no state in this class that
 * could make a unit "finish what it was doing first". The only memory it keeps is the bookkeeping of a single action
 * (a repath timer, a bite timer, a stuck counter), and every one of those is dropped the moment the command changes.
 */
public class SoulfireCommandGoal extends Goal
{
    /** How close to a spot counts as standing at it, in blocks. */
    private static final double SPOT_DISTANCE = 3.0D;

    /** How often a walking unit asks for a fresh path, in ticks. */
    private static final int REPATH_TICKS = 20;

    /** How many ticks of no progress make the goal give an unreachable errand up out loud. */
    private static final int STUCK_LIMIT = 200;

    /** How far a follower may fall behind before it is flown after its queen instead of walked, in blocks. */
    private static final double FOLLOW_STOP = 6.0D;

    /** How far around its anchor a patrol or a hunt wanders, in blocks. */
    private static final double PATROL_RADIUS = 8.0D;

    /** How often a patrolling unit picks a new point of its rounds, in ticks. */
    private static final int PATROL_TICKS = 120;

    /** How long a bite is rested, in ticks. */
    private static final int BITE_COOLDOWN = 20;

    /** How far around a point the search for a place to land looks, in blocks. */
    private static final int LANDING_SEARCH = 4;

    private final SoulfireDragonEntity dragon;

    private int repathTicks;
    private int patrolTicks;
    private int biteCooldown;
    private int stuckTicks;

    /** The command this goal's bookkeeping belongs to: when it moves on, everything below is dropped at once. */
    private int lastVersion = -1;
    private double lastDistance = Double.MAX_VALUE;
    private Vec3 patrolPoint;

    public SoulfireCommandGoal(SoulfireDragonEntity dragon)
    {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return true;
    }

    @Override
    public void start()
    {
        this.forget();
    }

    @Override
    public void stop()
    {
        this.dragon.setBreathing(false);
        this.dragon.stopFlight();
    }

    /** Drops every scrap of bookkeeping of the previous command. */
    private void forget()
    {
        this.repathTicks = 0;
        this.patrolTicks = 0;
        this.stuckTicks = 0;
        this.lastDistance = Double.MAX_VALUE;
    }

    @Override
    public void tick()
    {
        if (!(this.dragon.level() instanceof ServerLevel) || this.dragon.isDeadOrDying())
        {
            return;
        }

        if (this.biteCooldown > 0)
        {
            this.biteCooldown--;
        }

        int version = this.dragon.getCommandVersion();

        // <b>The newest command wins, in this very tick.</b> Every command of the sceptre moves the version of the
        // unit forward, so the first tick of a new command drops every trace of the previous one - the repath timer,
        // the patrol point, the stuck counter and the last distance. Nothing of an older order can make this unit
        // "finish what it was doing first".
        if (version != this.lastVersion)
        {
            this.lastVersion = version;
            this.forget();
        }

        // 1. The skill of the ability key: breathe at the place the queen pointed at, until she says otherwise.
        if (this.dragon.hasBreathOrder())
        {
            this.skill(this.dragon.getBreathTarget(), version);

            return;
        }

        // 2. A victim the queen pointed at: bite it if it can be bitten, burn it if it can not.
        LivingEntity victim = this.dragon.getForcedTarget();

        if (victim != null)
        {
            if (SoulfireTargeting.isHuntable(this.dragon, victim))
            {
                this.fight(victim, version);

                return;
            }

            // The victim is gone, or turned into somebody this unit may not touch: the order is over.
            this.dragon.clearTemporaryCommand();
            this.forget();
        }

        // 3. The errand of the scout move.
        if (this.dragon.getMoveTarget() != null)
        {
            this.errand(this.dragon.getMoveTarget(), version);

            return;
        }

        // 4. And nothing else answers: the standing order of the unit.
        this.order(version);
    }

    /**
     * The skill of the ability key.
     *
     * <p><b>The rule of the round, in one method.</b> The place the queen pointed at is looked at from above first: if
     * the column of {@value SoulfireDragonEntity#SKY_HEIGHT} blocks over it is open air, the unit flies to
     * {@value SoulfireDragonEntity#HOVER_HEIGHT} blocks above it, hovers there and sprays the soul fire straight down.
     * If something covers that column, the unit comes down instead, stands on the ground beside the point and breathes
     * at it from there.
     *
     * <p>There is <b>no cooldown</b> and no end of its own: the goal keeps this method running tick after tick until
     * the entity no longer carries a breath order, which is exactly what happens the moment a move, an attack, a post
     * or another skill cast arrives.
     */
    private void skill(Vec3 point, int version)
    {
        if (point == null)
        {
            return;
        }

        if (SoulfireOrders.skyOpen(this.dragon.level(), BlockPos.containing(point)))
        {
            this.hoverOver(point, version);

            return;
        }

        this.standNear(point, version);
    }

    /** The open sky half of the skill: up to fifteen blocks over the point, and then a breath straight down. */
    private void hoverOver(Vec3 point, int version)
    {
        Vec3 perch = point.add(0.0D, SoulfireDragonEntity.HOVER_HEIGHT, 0.0D);
        double horizontal = this.horizontalTo(perch);

        if (horizontal > SPOT_DISTANCE || Math.abs(this.dragon.getY() - perch.y) > 3.0D)
        {
            // Still on the way: the flight of this class steers in a straight line, and the hover waits until the unit
            // is really over the point.
            this.dragon.setHovering(false);
            this.dragon.flyTo(perch, version);

            return;
        }

        this.dragon.stopFlight();
        this.dragon.setFlying(false);
        this.dragon.setHovering(true);
        this.dragon.holdStill();
        SoulfireBreath.tick(this.dragon, point);
    }

    /** The covered half of the skill: down to the ground beside the point, and then a breath at it from there. */
    private void standNear(Vec3 point, int version)
    {
        Vec3 landing = this.landingSpot(point);
        double horizontal = this.horizontalTo(landing);

        if (this.dragon.onGround() && horizontal <= SPOT_DISTANCE
                && Math.abs(this.dragon.getY() - landing.y) < 3.0D)
        {
            this.dragon.stopFlight();
            this.dragon.setFlying(false);
            this.dragon.setHovering(false);
            this.dragon.holdStill();
            SoulfireBreath.tick(this.dragon, point);

            return;
        }

        this.dragon.setHovering(false);

        if (horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.flyTo(landing.add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        if (this.dragon.isFlying())
        {
            this.dragon.flyTo(landing.add(0.0D, 1.0D, 0.0D), version);

            return;
        }

        this.walkTo(landing);
    }

    /** A victim of the round: bitten when it can be reached, burned with soul fire when it can not. */
    private void fight(LivingEntity victim, int version)
    {
        Vec3 centre = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        double distance = this.dragon.distanceTo(victim);
        boolean airborne = this.dragon.isFlying() || this.dragon.isHovering();

        // 1. Within the jaws: a unit that can reach its victim bites it - on the ground, on the wing or while it
        // hovers, because a dragon does not lose its teeth by leaving the floor.
        if (distance <= SoulfireDragonEntity.MELEE_REACH && this.dragon.getY() - centre.y < 2.5D
                && this.dragon.hasLineOfSight(victim))
        {
            this.bite(victim);

            return;
        }

        // 2. Far away: fly at it in a straight line, which is the whole flight rule of this round - no circles, no
        // climb to a cruising altitude, just the line between the two.
        if (!airborne && this.distanceToFar(victim) > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.getNavigation().stop();
            this.dragon.flyTo(centre, version);

            return;
        }

        if (airborne)
        {
            // In the air the unit closes in, and it hangs in the air and breathes the moment the victim is in range.
            if (distance > SoulfireDragonEntity.MELEE_REACH && distance > SoulfireDragonEntity.BREATH_RANGE * 0.5D)
            {
                this.dragon.flyTo(centre, version);
            }

            if (distance <= SoulfireDragonEntity.BREATH_RANGE)
            {
                this.dragon.stopFlight();
                this.dragon.setFlying(false);
                this.dragon.setHovering(true);
                this.dragon.holdStill();
                SoulfireBreath.tick(this.dragon, centre);
            }

            return;
        }

        if (distance <= SoulfireDragonEntity.BREATH_RANGE)
        {
            // On the ground and in range of the breath: stand still and burn it, which is what a dragon does with
            // everything it can not reach with its teeth.
            this.dragon.holdStill();
            SoulfireBreath.tick(this.dragon, centre);

            return;
        }

        this.walkTo(victim.position());
    }

    /** @return how far the victim is, counted from the floor the unit stands on. */
    private double distanceToFar(LivingEntity victim)
    {
        return this.horizontalTo(victim.position());
    }

    /**
     * The melee half: a bite, on a cooldown, and only when the victim is really within the jaws.
     *
     * <p>The flight is ended without touching the gravity of the unit in the air: a unit that bites while it hovers
     * keeps hovering, and the goal that owns the standing order brings it back down.
     */
    private void bite(LivingEntity victim)
    {
        this.dragon.stopFlight();
        this.dragon.getNavigation().stop();
        this.dragon.getLookControl().setLookAt(victim, 30.0F, 30.0F);
        this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().scale(0.35D));

        if (this.biteCooldown <= 0)
        {
            this.dragon.doHurtTarget(victim);
            this.biteCooldown = BITE_COOLDOWN;
        }
    }

    /**
     * The scout move: close by it is walked (the ordinary navigation of the game, with its own pathfinder), further
     * away than {@value SoulfireDragonEntity#FLY_DISTANCE} blocks it is <b>flown in a straight line</b> - never with the
     * circles, climbs and hovers the dragon AI of the other mod would take.
     *
     * <p>When the errand is reached it is dropped, and the unit falls back to its standing order: an errand is
     * something to do, never a mode to be stuck in.
     */
    private void errand(Vec3 target, int version)
    {
        double horizontal = this.horizontalTo(target);
        boolean airborne = this.dragon.isFlying() || this.dragon.isHovering();

        if (!airborne && horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.getNavigation().stop();
            this.dragon.flyTo(target.add(0.0D, 8.0D, 0.0D), version);

            return;
        }

        if (airborne)
        {
            if (horizontal > SoulfireDragonEntity.ARRIVE_DISTANCE)
            {
                this.dragon.flyTo(new Vec3(target.x, Math.max(target.y, this.dragon.getY()) + 2.0D, target.z), version);

                return;
            }

            if (this.dragon.getY() - target.y > 1.5D)
            {
                this.dragon.flyTo(target.add(0.0D, 1.0D, 0.0D), version);

                return;
            }

            // Over the destination and low enough: the flight ends, gravity takes over and the unit lands where it was
            // sent. The errand itself is finished (the walking branch below is the one that drops it).
            this.dragon.stopFlight();
            this.dragon.setFlying(false);
            this.dragon.setHovering(false);

            return;
        }

        if (horizontal <= SoulfireDragonEntity.ARRIVE_DISTANCE
                && Math.abs(this.dragon.getY() - target.y) < 3.0D)
        {
            this.dragon.getNavigation().stop();
            this.dragon.clearTemporaryCommand();
            this.forget();

            return;
        }

        this.walkTo(target);
        this.watchProgress(horizontal, target);
    }

    /** Hands a destination to the ordinary ground navigation, refreshing it now and then. */
    private void walkTo(Vec3 target)
    {
        if (this.repathTicks <= 0 || this.dragon.getNavigation().isDone())
        {
            this.dragon.getNavigation().moveTo(target.x, target.y, target.z, 1.0D);
            this.repathTicks = REPATH_TICKS;
        }
        else
        {
            this.repathTicks--;
        }
    }

    /** Counts the ticks without progress, and gives an unreachable errand up out loud instead of walking forever. */
    private void watchProgress(double horizontal, Vec3 target)
    {
        if (horizontal < this.lastDistance - 0.5D)
        {
            this.lastDistance = horizontal;
            this.stuckTicks = 0;

            return;
        }

        this.stuckTicks++;

        if (this.stuckTicks > STUCK_LIMIT)
        {
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.info(
                    "soulfire dragon {} can not reach its move order ({} blocks left, target {}): standing down",
                    this.dragon.getUUID(), (int) horizontal, target);
            this.dragon.clearTemporaryCommand();
            this.forget();
        }
    }

    /** @return the horizontal distance from the unit to that point. */
    private double horizontalTo(Vec3 point)
    {
        return Math.sqrt(this.dragon.distanceToSqr(point.x, this.dragon.getY(), point.z));
    }

    /**
     * @return the first place near that point where the unit could stand: free air with a floor under it, or the point
     *         itself when there is none, so a covered spot in mid air is still approached as closely as it can be.
     */
    private Vec3 landingSpot(Vec3 point)
    {
        BlockPos from = BlockPos.containing(point);

        for (int depth = 0; depth <= LANDING_SEARCH; depth++)
        {
            for (int dx = -LANDING_SEARCH; dx <= LANDING_SEARCH; dx++)
            {
                for (int dz = -LANDING_SEARCH; dz <= LANDING_SEARCH; dz++)
                {
                    BlockPos candidate = from.offset(dx, -depth, dz);

                    if (this.dragon.level().isEmptyBlock(candidate)
                            && this.dragon.level().isEmptyBlock(candidate.above())
                            && !this.dragon.level().isEmptyBlock(candidate.below()))
                    {
                        return Vec3.atBottomCenterOf(candidate);
                    }
                }
            }
        }

        return point;
    }

    // ------------------------------------------------------------------------------------------------------------
    // The standing order: the fallback the unit returns to when no temporary command is running
    // ------------------------------------------------------------------------------------------------------------

    private void order(int version)
    {
        switch (this.dragon.getOrderMode())
        {
            case FOLLOW -> this.follow(version);
            case HOLD -> this.hold(version);
            case PATROL -> this.patrol(version);
            case FREE_ATTACK -> this.hunt(version);
        }
    }

    /** Follow: walk with the queen, and fly after her when she is more than fifteen blocks away. */
    private void follow(int version)
    {
        Player queen = this.queen();

        if (queen == null || !queen.isAlive() || queen.level() != this.dragon.level())
        {
            this.stand(version);

            return;
        }

        double distance = this.dragon.distanceTo(queen);

        if (distance <= FOLLOW_STOP)
        {
            this.stand(version);

            return;
        }

        if (distance > SoulfireDragonEntity.FLY_DISTANCE && !this.dragon.isHovering())
        {
            this.dragon.flyTo(queen.position().add(0.0D, 3.0D, 0.0D), version);

            return;
        }

        this.walkTo(queen.position());
    }

    /** Hold: stand on the post, and sit down on it once it is reached - the pose of a garrison. */
    private void hold(int version)
    {
        BlockPos post = this.dragon.getAnchor();

        if (post == null)
        {
            this.dragon.setAnchor(this.dragon.blockPosition());
            this.stand(version);

            return;
        }

        Vec3 spot = Vec3.atBottomCenterOf(post);
        double horizontal = this.horizontalTo(spot);

        if (horizontal <= SPOT_DISTANCE && Math.abs(this.dragon.getY() - spot.y) < 3.0D)
        {
            this.dragon.stopFlight();
            this.dragon.setFlying(false);
            this.dragon.setHovering(false);
            this.dragon.setSitting(true);
            this.dragon.getNavigation().stop();

            return;
        }

        this.dragon.setSitting(false);

        if (horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.flyTo(spot.add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        this.walkTo(spot);
    }

    /** Patrol: walk the rounds around the anchor, and fly back to it when the unit has drifted too far. */
    private void patrol(int version)
    {
        BlockPos post = this.dragon.getAnchor();

        if (post == null)
        {
            this.dragon.setAnchor(this.dragon.blockPosition());
            this.stand(version);

            return;
        }

        this.dragon.setSitting(false);

        if (this.horizontalTo(Vec3.atBottomCenterOf(post)) > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.patrolPoint = null;
            this.dragon.flyTo(Vec3.atBottomCenterOf(post).add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        if (this.patrolPoint == null || this.patrolTicks++ > PATROL_TICKS
                || this.horizontalTo(this.patrolPoint) <= SPOT_DISTANCE)
        {
            double angle = this.dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 2.0D + this.dragon.getRandom().nextDouble() * PATROL_RADIUS;
            this.patrolPoint = new Vec3(post.getX() + 0.5D + Math.cos(angle) * radius, post.getY(),
                    post.getZ() + 0.5D + Math.sin(angle) * radius);
            this.patrolTicks = 0;
        }

        this.walkTo(this.patrolPoint);
    }

    /** Free attack: hunt whatever stands around the anchor that is not of the queen's own side. */
    private void hunt(int version)
    {
        LivingEntity victim = this.dragon.getTarget();

        if (victim != null && SoulfireTargeting.isHuntable(this.dragon, victim))
        {
            this.fight(victim, version);

            return;
        }

        // Nothing to hunt right now: the rounds of a patrol, which is what a hunter does between two victims.
        this.patrol(version);
    }

    /** Comes down where it is, and then stands absolutely still: the pose of a unit with nothing to do. */
    private void stand(int version)
    {
        if (this.dragon.isFlying() || this.dragon.isHovering())
        {
            this.dragon.setHovering(false);
            this.dragon.flyTo(new Vec3(this.dragon.getX(), this.dragon.getY() - 1.0D, this.dragon.getZ()), version);

            if (this.dragon.onGround())
            {
                this.dragon.stopFlight();
                this.dragon.setFlying(false);
            }

            return;
        }

        this.dragon.getNavigation().stop();
        this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
    }

    /** @return the player who holds the contract of the unit, or null when that player is not in this level. */
    @Nullable
    private Player queen()
    {
        java.util.UUID owner = this.dragon.getOwnerUuid();

        return owner == null ? null : this.dragon.level().getPlayerByUUID(owner);
    }
}