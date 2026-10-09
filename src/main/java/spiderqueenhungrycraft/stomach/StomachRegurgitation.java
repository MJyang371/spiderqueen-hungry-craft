package spiderqueenhungrycraft.stomach;

import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.beast.QueenBeastBite;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.devour.DevourFeedback;
import spiderqueenhungrycraft.devour.DevourFluid;
import spiderqueenhungrycraft.devour.DevourTuning;

/**
 * The one way out of a stomach (Phase 8E2A).
 *
 * <p>Whatever is inside the cell of a queen is thrown out in front of her mouth - guests, items and experience
 * orbs together - with a real impulse, a llama spit and a cloud of dark green fluid. Nothing is discarded, cloned
 * or respawned: the very same entities that were inside simply leave, which keeps every UUID and every piece of
 * their state exactly as it was.
 *
 * <p>The same backend serves three callers: the long press of a beast, the safety press of an ordinary queen and
 * the automatic vomit before the beast form ends. Only the presentation differs - a beast lunges, an ordinary
 * queen swings her arm - and the caller says which of the two it wants.
 *
 * <p>A cell that is not loaded is never loaded by this class. Vomiting is always about what is already in the
 * stomach, so an empty or closed stomach simply does nothing at all: no sound, no particles, no ticket.
 */
public final class StomachRegurgitation
{
    /** Why a queen threw her stomach content up. Used for the feedback a caller asks for. */
    public enum Cause
    {
        /** A beast held the devour key long enough. */
        BEAST_HOLD,
        /** An ordinary queen pressed the same key, the safety fallback. */
        QUEEN_FALLBACK,
        /** The beast form is about to end, so the stomach is emptied first. */
        BEAST_REVERT
    }

    /** Tries of the small search around the ideal landing spot before that spot is used as it is. */
    private static final int RELEASE_TRIES = 9;

    private StomachRegurgitation()
    {
    }

    /**
     * Throws everything that is inside the cell of that queen out in front of her.
     *
     * @return how many things really left the stomach
     */
    public static int regurgitateAll(ServerPlayer owner, Cause cause)
    {
        if (owner == null || owner.isRemoved() || !owner.isAlive() || !(owner.level() instanceof ServerLevel target))
        {
            return 0;
        }

        MinecraftServer server = owner.getServer();
        ServerLevel stomach = StomachCellManager.stomachLevel(server);

        if (server == null || stomach == null)
        {
            return 0;
        }

        StomachCell cell = StomachOwnership.allocatedCellOf(server, owner.getUUID()).orElse(null);

        // Only a stomach that is loaded right now can be emptied: this button never wakes a closed cell up.
        if (cell == null || !StomachCellManager.isLoaded(cell))
        {
            return 0;
        }

        List<Entity> contents = StomachContents.collect(stomach, cell);

        if (contents.isEmpty())
        {
            return 0;
        }

        Vec3 forward = QueenBeastBite.horizontalForward(owner);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        // The presentation of the vomit comes first: the pose, the impulse, the spit and the first wave of fluid
        // are already happening while the contents are still inside, and the very same tick then throws them out.
        playFeedback(owner, target, forward, cause);
        int moved = 0;
        int index = 0;

        for (Entity entity : contents)
        {
            if (entity.isRemoved())
            {
                continue;
            }

            Vec3 spot = releaseSpot(target, owner, forward, right, entity, index);

            if (release(entity, target, spot, owner.getYRot(), forward, right, index))
            {
                moved++;
                index++;
            }
        }

        if (moved <= 0)
        {
            return 0;
        }

        // Only a stomach that really gave everything away may be closed: anything left would lose its home.
        if (StomachContents.isEmpty(stomach, cell))
        {
            StomachCellManager.closeCell(stomach, cell);
        }
        else
        {
            StomachHudBroadcast.send(stomach, cell);
        }

        // The rest of the fluid follows the contents, one batch per tick, so the jet trails behind the vomit.
        DevourFeedback.regurgitateFollowUp(owner, forward);

        return moved;
    }

    /** @return the landing spot of one vomited thing: in front of the mouth, never further away than the search. */
    private static Vec3 releaseSpot(ServerLevel level, ServerPlayer owner, Vec3 forward, Vec3 right, Entity entity,
            int index)
    {
        // The ideal spot: a little in front of the queen, at her own height. Everything else is a tiny correction
        // around it, so nothing is ever thrown behind her or thirty blocks away.
        Vec3 base = owner.position().add(forward.scale(DevourTuning.RELEASE_DISTANCE)).add(0.0D, 0.15D, 0.0D);
        AABB box = entity.getBoundingBox();

        for (int attempt = 0; attempt < RELEASE_TRIES; attempt++)
        {
            double wideness = attempt == 0 ? 0.0D : 0.4D + 0.6D * attempt / (double) RELEASE_TRIES;
            double angle = attempt * 2.399963229728653D;
            Vec3 candidate = base
                    .add(forward.scale(Math.cos(angle) * DevourTuning.RELEASE_FORWARD_ADJUST * wideness))
                    .add(right.scale(Math.sin(angle) * DevourTuning.RELEASE_SIDE_SPREAD * wideness))
                    .add(0.0D, DevourTuning.RELEASE_HEIGHT_ADJUST * (attempt % 4) / 3.0D, 0.0D);

            if (level.noCollision(box.move(candidate.subtract(entity.position()))))
            {
                return candidate;
            }
        }

        return base;
    }

    /** @return true when that thing really left the stomach of the queen. */
    private static boolean release(Entity entity, ServerLevel target, Vec3 spot, float yaw, Vec3 forward, Vec3 right,
            int index)
    {
        try
        {
            if (entity instanceof ServerPlayer player)
            {
                // A player is moved with the ordinary player teleport, never discarded and never rebuilt.
                StomachTeleport.movePlayer(player, target, spot.x, spot.y, spot.z, yaw, player.getXRot());
            }
            else
            {
                StomachTeleport.moveAny(entity, target, spot.x, spot.y, spot.z, yaw);
            }
        }
        catch (RuntimeException exception)
        {
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.error("A {} could not be thrown up",
                    entity.getType(), exception);
            return false;
        }

        if (entity.level() != target || entity.isRemoved())
        {
            return false;
        }

        // The fly out: forward, a little up, a small sideways jitter. The motion packet of the game is the
        // hurtMarked flag, which the tracking of the entity turns into a real velocity update for everybody.
        entity.setDeltaMovement(impulse(forward, right, index));
        entity.hurtMarked = true;
        entity.fallDistance = 0.0F;

        return true;
    }

    /** @return the impulse of one vomited thing. */
    public static Vec3 impulse(Vec3 forward, Vec3 right, int index)
    {
        double jitter = index == 0 ? 0.0D : Math.sin(index * 2.399963229728653D) * DevourTuning.SIDE_JITTER;

        return forward.scale(DevourTuning.FORWARD_SPEED).add(0.0D, DevourTuning.UP_SPEED, 0.0D)
                .add(right.scale(jitter));
    }

    /**
     * The one pose, the one sound and the first wave of fluid of a vomit that really happened (Phase 8E2A-POLISH-1).
     *
     * <p>It runs before anything is moved: the queen throws her head back, the screen shakes, the llama spits
     * and the very first droplets are already in the air when the contents follow them out of the mouth. Only
     * the following batches of fluid come after the move, a tick later, so the jet has a length.
     */
    private static void playFeedback(ServerPlayer owner, ServerLevel target, Vec3 forward, Cause cause)
    {
        if (!QueenBeastState.isTransformed(owner) && cause != Cause.BEAST_REVERT)
        {
            // An ordinary queen swings her arm, the ordinary melee animation of the game. No beast renderer is
            // ever put on a humanoid queen, and a beast has no arm to swing.
            owner.swing(InteractionHand.MAIN_HAND, true);
        }

        DevourFeedback.regurgitate(owner, forward);
    }
}
