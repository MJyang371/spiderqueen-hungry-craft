package spiderqueenhungrycraft.beast;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one destruction corridor of the beast (Phase 8D-POLISH-1).
 *
 * <p>It answers the only question that matters for a moving beast: which blocks does the full body really need
 * to occupy while it moves from where it is to where it wants to be? Everything that blocks that path is
 * handled here, everything else is left completely alone.
 *
 * <p>The geometry is deliberately split in two, because the old code mixed both into one swept box:
 *
 * <ul>
 * <li><b>entity sweep</b> stays a plain swept box (see {@link #entitySweep}), because a fast charge may never
 * pass through a creature without hitting it,</li>
 * <li><b>destruction corridor</b> is this class: the full body box, sampled along the horizontal movement,
 * with a broad phase for candidates and a real collision shape test as the narrow phase.</li>
 * </ul>
 *
 * <p>Three rules make the corridor safe:
 *
 * <ol>
 * <li>the movement vector is horizontal only, so gravity, a landing or a small negative vertical motion can
 * never turn a charge into a shaft digger,</li>
 * <li>the bottom of the corridor is exactly the feet plane and is never inflated downwards,</li>
 * <li>a block whose collision shape only reaches up to the feet plane is a support floor and is never broken,
 * whatever tag it carries. Slabs, stairs, fences and modded shapes are judged by their real shape, never by
 * their block coordinate.</li>
 * </ol>
 */
public final class QueenBeastMovementCorridor
{
    /** How far apart the body samples of one movement are, in blocks. Small steps, but only a handful. */
    private static final double SAMPLE_STEP = 0.25D;

    private QueenBeastMovementCorridor()
    {
    }

    /**
     * One scan of a forward corridor.
     *
     * @param hardStop         something the rules may not break really blocks the body, or a protection hook
     *                         refused a block that the rules allow
     * @param broken           how many blocks were really removed by this scan
     * @param pendingBreakable the corridor still holds breakable blocks that the per tick budget could not
     *                         handle, so the beast has to wait a tick instead of forcing its way through
     */
    public record Scan(boolean hardStop, int broken, boolean pendingBreakable)
    {
        /** @return true when the corridor is clear enough that the body may move on. */
        public boolean cleared()
        {
            return !this.hardStop && !this.pendingBreakable;
        }
    }

    /**
     * Clears the corridor of one movement: every breakable block the full body would run into, nothing else.
     *
     * @param body             the body box before the step
     * @param motion           the intended movement, horizontal only
     * @param horizontalMargin the small side clearance of the corridor
     * @param topMargin        the small head clearance of the corridor
     * @param profile          which block rule decides what may be broken
     * @param budget           how many blocks this scan may remove at most
     */
    public static Scan destroy(ServerLevel level, ServerPlayer player, AABB body, Vec3 motion,
            double horizontalMargin, double topMargin, QueenBeastTuning.RuleProfile profile, int budget)
    {
        List<AABB> samples = samples(body, motion, horizontalMargin, topMargin);
        AABB broad = broadPhase(samples);
        QueenBeastTuning.Rule rule = QueenBeastTuning.rule(profile);
        double feetY = body.minY;
        boolean hardStop = false;
        int broken = 0;
        boolean pending = false;

        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(broad.minX), Mth.floor(broad.minY),
                Mth.floor(broad.minZ), Mth.floor(broad.maxX), Mth.floor(broad.maxY), Mth.floor(broad.maxZ)))
        {
            BlockState state = level.getBlockState(pos);

            if (state.isAir())
            {
                continue;
            }

            VoxelShape shape = state.getCollisionShape(level, pos);

            // Narrow phase: the block has to reach into the corridor volume for real, a cell that only touches
            // it is not enough. Blocks without any collision shape are judged by their cell, so grass, flowers,
            // vines and cobwebs inside the corridor are still cleared.
            if (!intersects(samples, shape, pos))
            {
                continue;
            }

            // Floor lock: a shape that only reaches the feet plane carries the beast, it never blocks it.
            if (!shape.isEmpty() && isSupportFloor(shape, pos, feetY))
            {
                continue;
            }

            if (!QueenBeastBlockRules.canBreak(level, pos, state, profile))
            {
                // A block that really guards the body and may not be broken stops the beast. A block without a
                // collision shape cannot stop anything, so an unbreakable light or marker node is ignored.
                if (!shape.isEmpty())
                {
                    hardStop = true;
                }

                continue;
            }

            if (broken >= budget)
            {
                pending = true;
                continue;
            }

            // The rules and the protection hooks are asked exactly once per block, and the break itself then
            // uses the granted answer instead of asking a second time.
            if (QueenBeastBlockBreaker.canClaim(level, player, pos, profile))
            {
                QueenBeastBlockBreaker.claim(level, player, pos, rule.dropBlocks());
                broken++;
            }
            else
            {
                // A protection hook refused a block the rules allow: for a charge that is a hard stop.
                hardStop = true;
            }
        }

        return new Scan(hardStop, broken, pending);
    }

    /**
     * @return the body boxes of that movement: the body box at the start, at the end and at every small step in
     *         between, so a diagonal charge is covered by the body itself and not by one big enclosing box.
     */
    public static List<AABB> samples(AABB body, Vec3 motion, double horizontalMargin, double topMargin)
    {
        AABB widened = new AABB(body.minX - horizontalMargin, body.minY, body.minZ - horizontalMargin,
                body.maxX + horizontalMargin, body.maxY + topMargin, body.maxZ + horizontalMargin);
        double distance = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        int steps = Math.max(1, Mth.ceil(distance / SAMPLE_STEP));
        List<AABB> samples = new ArrayList<>(steps + 1);

        for (int index = 0; index <= steps; index++)
        {
            double fraction = index / (double) steps;
            samples.add(widened.move(motion.x * fraction, 0.0D, motion.z * fraction));
        }

        return samples;
    }

    /** The plain swept box of one movement, used for high speed entity hits and for nothing else. */
    public static AABB entitySweep(AABB from, AABB to, double margin)
    {
        return from.minmax(to).inflate(margin, Math.max(margin * 0.5D, 0.05D), margin);
    }

    /**
     * @return true when the beast body may really occupy that box in this world. The vanilla block collision
     *         test is the only authority here, so a hole that is too small for the whole body is never assumed
     *         to be big enough.
     */
    public static boolean canOccupy(Level level, ServerPlayer player, AABB box)
    {
        return level.noCollision(player, box);
    }

    /** @return the enclosing candidate box of all body samples, used as the broad phase of a scan. */
    public static AABB broadPhase(List<AABB> samples)
    {
        AABB broad = samples.get(0);

        for (AABB sample : samples)
        {
            broad = broad.minmax(sample);
        }

        return broad.inflate(0.05D);
    }

    /**
     * @return true when that block shape reaches into one of the body samples. Blocks without a collision shape
     *         are judged by their cell, everything else by a real volume intersection, exactly like the
     *         transformation clearance does it.
     */
    private static boolean intersects(List<AABB> samples, VoxelShape shape, BlockPos pos)
    {
        if (shape.isEmpty())
        {
            AABB cell = new AABB(pos);

            for (AABB sample : samples)
            {
                if (sample.intersects(cell))
                {
                    return true;
                }
            }

            return false;
        }

        VoxelShape moved = shape.move(pos.getX(), pos.getY(), pos.getZ());

        for (AABB sample : samples)
        {
            VoxelShape volume = Shapes.create(sample.deflate(QueenBeastConstants.CLEARANCE_EPSILON));

            if (Shapes.joinIsNotEmpty(volume, moved, BooleanOp.AND))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return true when that shape only reaches up to the feet plane, which means the block carries the beast
     *         instead of blocking it. Slabs, stairs, fences and modded shapes are judged by their real top, so
     *         a half block under the feet is a floor and a half block in the way is an obstacle.
     */
    private static boolean isSupportFloor(VoxelShape shape, BlockPos pos, double feetY)
    {
        return shape.max(Direction.Axis.Y) + pos.getY() <= feetY + QueenBeastConstants.BEAST_FLOOR_EPSILON;
    }

    /** Only for the debug log of the charge, never used to decide anything. */
    static void warn(String message)
    {
        SpiderQueenHungryCraft.LOGGER.warn("[corridor] {}", message);
    }
}
