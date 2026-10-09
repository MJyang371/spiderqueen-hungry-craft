package spiderqueenhungrycraft.devour;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastBite;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastBiteFeedbackPacket;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.stomach.StomachCell;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachHudBroadcast;
import spiderqueenhungrycraft.stomach.StomachTeleport;

/**
 * The devour of the Queen Beast (Phase 8E2A).
 *
 * <p>One devour is one short press of the devour key while transformed: the jaws of the beast open in front of
 * her, and every creature that a bite could reach is swallowed - not damaged, not cloned, not replaced, but
 * moved. The very same entity that stood in front of the mouth ends up in the stomach of the queen, with its
 * UUID, its health, its name, its equipment, its effects and its AI untouched, because nothing is recreated
 * anywhere in this file.
 *
 * <p>The volume, the line of sight and the vertical semantics are not written a second time: the search is the
 * bite search of {@link QueenBeastBite}, only with the eligibility of {@link DevourTargets} instead of "who may
 * be bitten". A devour therefore never differs from a bite in reach, and retuning the jaws retunes the throat.
 *
 * <p>A devour deals no damage at all - no bite, no venom, no ground impact, no pack aggro - and it never touches
 * a prepared venom strike. It also never loads a stomach before it knows that it has something to swallow, so an
 * empty snap at the air costs nothing.
 */
public final class DevourHandler
{
    private DevourHandler()
    {
    }

    /**
     * Server entry point of the short press of the devour key.
     *
     * @return how many creatures were really swallowed
     */
    public static int tryDevour(ServerPlayer beast)
    {
        if (!QueenDataCapability.isQueen(beast) || !QueenBeastState.isTransformed(beast) || !beast.isAlive()
                || beast.isSpectator() || !(beast.level() instanceof ServerLevel level))
        {
            return 0;
        }

        // The throat of the beast is the Giant Devour node and not a part of the form (Skill Progression round): a
        // beast without that node swallows nothing. It is asked here as well as at the key, so an execution path that
        // does not come from the key can never swallow either.
        if (!QueenSkills.has(beast, QueenSkills.GIANT_DEVOUR))
        {
            return 0;
        }

        MinecraftServer server = beast.getServer();
        ServerLevel stomach = StomachCellManager.stomachLevel(server);

        if (server == null || stomach == null)
        {
            return 0;
        }

        Vec3 forward = QueenBeastBite.horizontalForward(beast);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        List<LivingEntity> targets = new ArrayList<>(
                QueenBeastBite.targetsInFront(beast, forward, right,
                        candidate -> DevourTargets.eligible(beast, candidate)));

        // A snap is a snap: the beast lunges, the screen shakes, the frog swallows and the mouth throws its
        // saliva, whether or not something stood in front of it. Nothing else of this class happens for an
        // empty snap - no cell, no ticket, no HUD, no teleport, and no trace of it anywhere.
        DevourFeedback.devour(beast, forward);

        if (targets.isEmpty())
        {
            return 0;
        }

        // The stomach of the queen is the home of everything she swallows: it is created if it never existed and
        // kept loaded as long as it holds a guest, an item or an orb.
        StomachCell cell = StomachCellManager.cellFor(server, beast.getUUID());
        StomachCellManager.loadCell(stomach, cell);

        if (!StomachCellManager.isLoaded(cell))
        {
            // The cell could not be kept alive: the prey stays where it is, nothing is discarded.
            SpiderQueenHungryCraft.LOGGER.warn("The stomach cell of {} could not be loaded, devour refused",
                    beast.getUUID());
            return 0;
        }

        // The direction the jaws point at is needed before the search, so it is computed right above it.
        int moved = 0;
        int index = 0;

        for (LivingEntity target : targets)
        {
            if (target.isRemoved() || !target.isAlive())
            {
                continue;
            }

            // A mount is only swallowed alone: a passenger tree is left behind as a whole.
            if (!target.getPassengers().isEmpty())
            {
                continue;
            }

            if (target.isPassenger())
            {
                target.stopRiding();
            }

            Vec3 spot = DevourPlacement.spotFor(stomach, cell, target, index);

            if (spot == null)
            {
                // The body does not fit into the cavity: it stays in the outside world, never duplicated.
                continue;
            }

            index++;

            if (swallow(target, stomach, cell, spot, beast.getYRot()))
            {
                moved++;
            }
        }

        if (moved <= 0)
        {
            return 0;
        }

        // The owner sees the new guest at once instead of waiting for the next snapshot.
        StomachHudBroadcast.send(stomach, cell);

        return moved;
    }

    /** @return true when that creature really arrived in the cell of the queen. */
    private static boolean swallow(LivingEntity target, ServerLevel stomach, StomachCell cell, Vec3 spot, float yaw)
    {
        try
        {
            StomachTeleport.move(target, stomach, spot.x, spot.y, spot.z, yaw);
        }
        catch (RuntimeException exception)
        {
            SpiderQueenHungryCraft.LOGGER.error("A {} could not be swallowed", target.getType(), exception);
            return false;
        }

        // Identity and place are checked on the real entity: a move that did not happen leaves it in its world.
        return target.level() == stomach && !target.isRemoved() && cell.contains(target.getX(),
                target.getY() + target.getBbHeight() * 0.5D, target.getZ());
    }
}
