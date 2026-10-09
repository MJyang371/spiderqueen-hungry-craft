package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the sceptre of a queen does when her click lands near a mine (Spider Mine and Egg Arrow round, widened by the
 * Spider Trigger round).
 *
 * <p><b>A click does not have to hit the block.</b> The point the cursor indicates is the answer, and every mine of
 * this mod whose centre lies within {@link #COMMAND_RADIUS} blocks of it offers itself as the target. That is what
 * makes a mine usable in a wall or under a carpet: the queen aims at the spot, not at the block.
 *
 * <p><b>Both ends of the click have to be inside.</b> The cursor point of the moment the button went <b>down</b> and
 * the one of the moment it came back <b>up</b> both have to lie in the ball of the same mine, or the click is not a
 * mine command at all. A drag that happens to pass over a mine is therefore never a trigger - it is a box selection -
 * and a click that starts somewhere else and ends on a mine stays an ordinary command.
 *
 * <p><b>One mine, the nearest one.</b> If several mines answer, only the one closest to the point of the release is
 * fired, and mines at exactly the same distance are settled by a coin toss of the world's random source.
 *
 * <p>The whole class works on a plain {@link Level} and not on a server level, because the client asks it the very
 * same question once per tick to draw its mine indicator over the mines a click would fire.
 *
 * <p>Nothing here loads a chunk: the search walks the blocks of a small cube around the point, and a mine in an
 * unloaded chunk is simply not there to be found.
 */
public final class SpiderMineTargeting
{
    /**
     * The ball around the point of the click in which a mine answers the sceptre: four blocks.
     *
     * <p>Four, and not two: a mine is a block a queen aims at from a distance, and two blocks turned out to be so small
     * that a click plainly meant for the mine landed an ordinary command instead. Four is a hand's width of aim, and it
     * is exactly the blast radius of the block of TNT the mine is built around.
     */
    public static final double COMMAND_RADIUS = 4.0D;

    /** Two distances are compared with a little slack, so floating point noise never costs a mine a tie. */
    private static final double EPSILON = 1.0E-6D;

    private SpiderMineTargeting()
    {
    }

    /** @return true when that block is a mine the sceptre may fire: the spider mine or its delayed twin. */
    public static boolean isMine(BlockState state)
    {
        return state.getBlock() instanceof SpiderMineBlock;
    }

    /**
     * @return every mine whose ball contains that point, the nearest one first (Spider Trigger round follow-up).
     *
     * <p>It is the question of the read only half of that round: the client of a queen asks it once per tick to know
     * which mines her crosshair could fire right now, and {@link #nearestMine} asks it again to pick the one a real
     * click fires.
     */
    public static List<BlockPos> minesAt(Level level, Vec3 point)
    {
        double radiusSqr = COMMAND_RADIUS * COMMAND_RADIUS;
        int range = (int) Math.ceil(COMMAND_RADIUS) + 1;
        BlockPos min = BlockPos.containing(point.x - range, point.y - range, point.z - range);
        BlockPos max = BlockPos.containing(point.x + range, point.y + range, point.z + range);

        List<BlockPos> found = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            if (isMine(level.getBlockState(pos)) && centreOf(pos).distanceToSqr(point) <= radiusSqr)
            {
                found.add(pos.immutable());
            }
        }

        found.sort(Comparator.comparingDouble(pos -> centreOf(pos).distanceToSqr(point)));

        return found;
    }

    /**
     * @return the mine a click means, or null when no mine is close enough to <b>both</b> points of the click.
     *
     * @param level   the level of the queen, or the level of her client, which asks the same question
     * @param release the point the cursor indicated when the button came back up
     * @param press   the point it indicated when the button went down
     */
    @Nullable
    public static BlockPos nearestMine(Level level, Vec3 release, @Nullable Vec3 press)
    {
        double radiusSqr = COMMAND_RADIUS * COMMAND_RADIUS;
        List<BlockPos> best = new ArrayList<>();
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : minesAt(level, release))
        {
            Vec3 centre = centreOf(pos);

            // A mine the press point was not inside is not a candidate: the click has to live inside one mine.
            if (press != null && centre.distanceToSqr(press) > radiusSqr)
            {
                continue;
            }

            double distance = centre.distanceToSqr(release);

            if (distance < bestDistance - EPSILON)
            {
                bestDistance = distance;
                best.clear();
                best.add(pos.immutable());
            }
            else if (distance <= bestDistance + EPSILON)
            {
                best.add(pos.immutable());
            }
        }

        if (best.isEmpty())
        {
            return null;
        }

        if (best.size() == 1)
        {
            return best.get(0);
        }

        // Several mines at the very same distance: the design asks for one of them at random, and no better rule
        // exists for a tie.
        return best.get(level.random.nextInt(best.size()));
    }

    /**
     * Fires the mine at that position, in whichever way that kind of mine fires: the spider mine goes off on the spot,
     * the delayed one only lights its fuse.
     */
    public static void fire(ServerLevel level, BlockPos pos, ServerPlayer queen)
    {
        if (level.getBlockState(pos).getBlock() instanceof SpiderMineBlock mine)
        {
            mine.triggerFromScepter(level, pos, queen);
        }
    }

    /** @return the middle of that block, which is what every distance of this class is measured against. */
    private static Vec3 centreOf(BlockPos pos)
    {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }
}
