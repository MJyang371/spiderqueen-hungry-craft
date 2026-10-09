package spiderqueenhungrycraft.sweatlord;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The environment safety of the Sweatlord (Sweatlord round, first pass).
 *
 * <p>This is the minimum the round asks for: his own actions must not obviously kill him. He gets no full escape AI,
 * no digging and no pathfinder - only the answers a pearl jump, a jump attack and a retreat really need:
 *
 * <ul>
 * <li>is this spot real ground, with room for a body and nothing lethal in it,</li>
 * <li>how far would he fall there (the vanilla rule is free up to three blocks, six are lethal),</li>
 * <li>is the ground in front of him about to end (which is what refuses a jump attack at a rim),</li>
 * <li>and is he standing in or on something that burns.</li>
 * </ul>
 */
public final class EnvironmentSafety
{
    /** A drop of this many blocks still costs nothing (vanilla is free up to three). */
    public static final int FREE_FALL = 3;

    /** A drop of this many blocks is refused in every plan, however useful the spot would be. */
    public static final int LETHAL_FALL = 6;

    /** The blocks a candidate spot must have free above its floor for a body like his. */
    private static final int BODY_HEIGHT = 2;

    /** How far the drop below a spot is followed before it counts as the void. */
    private static final int MAX_SCAN_DEPTH = 48;

    private EnvironmentSafety()
    {
    }

    /** @return true when a body could stand at that position: solid floor, no fluid, nothing lethal, room above. */
    public static boolean isStandable(Level level, BlockPos pos)
    {
        if (!level.isLoaded(pos) || !level.hasChunkAt(pos))
        {
            return false;
        }

        BlockState floor = level.getBlockState(pos.below());

        if (floor.isAir() || isDangerous(floor) || !floor.isFaceSturdy(level, pos.below(), Direction.UP))
        {
            return false;
        }

        for (int dy = 0; dy < BODY_HEIGHT; ++dy)
        {
            if (isDangerous(level.getBlockState(pos.above(dy))))
            {
                return false;
            }
        }

        return true;
    }

    /** @return true when a body must not stand in, on or next to that block. */
    public static boolean isDangerous(BlockState state)
    {
        if (state.isAir())
        {
            return false;
        }

        if (state.getFluidState().is(FluidTags.LAVA))
        {
            return true;
        }

        return state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.COBWEB)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE);
    }

    /**
     * The drop below a spot, counted until the first solid ground: {@link #MAX_SCAN_DEPTH} means "no ground found",
     * which is the void or a lava pool and is always refused.
     */
    public static int fallHeight(Level level, BlockPos pos)
    {
        for (int dy = 1; dy <= MAX_SCAN_DEPTH; ++dy)
        {
            BlockPos below = pos.below(dy);

            if (below.getY() < level.getMinBuildHeight() || !level.isLoaded(below))
            {
                return MAX_SCAN_DEPTH;
            }

            BlockState state = level.getBlockState(below);

            if (state.getFluidState().is(FluidTags.LAVA))
            {
                return MAX_SCAN_DEPTH;
            }

            if (!state.getCollisionShape(level, below).isEmpty())
            {
                return dy - 1;
            }
        }

        return MAX_SCAN_DEPTH;
    }

    /** @return true when a body may land there without losing anything: real ground and no cliff. */
    public static boolean isSafeLanding(Level level, BlockPos pos)
    {
        return isStandable(level, pos) && fallHeight(level, pos) <= FREE_FALL;
    }

    /** @return true when that body stands in or on something that burns him right now. */
    public static boolean isInDangerousFluid(LivingEntity body)
    {
        if (body.isInLava() || body.isOnFire())
        {
            return true;
        }

        return body.level().getBlockState(body.blockPosition().below()).is(Blocks.MAGMA_BLOCK)
                && body.getY() - body.blockPosition().getY() < 0.05D;
    }

    /** @return true when the ground in front of that body is a rim, the void or lava below. */
    public static boolean isGroundAheadUnsafe(LivingEntity body)
    {
        Vec3 forward = body.getViewVector(1.0F);
        BlockPos ahead = BlockPos.containing(body.getX() + forward.x * 1.5D, body.getY() - 1.0D,
                body.getZ() + forward.z * 1.5D);

        return fallHeight(body.level(), ahead.above()) > FREE_FALL;
    }

    /** @return the box a body of that size would occupy at that position, used to check for suffocation. */
    public static net.minecraft.world.phys.AABB bodyBox(BlockPos pos, double width, double height)
    {
        double half = width / 2.0D;

        return new net.minecraft.world.phys.AABB(pos.getX() + 0.5D - half, pos.getY(), pos.getZ() + 0.5D - half,
                pos.getX() + 0.5D + half, pos.getY() + height, pos.getZ() + 0.5D + half);
    }

    /**
     * The candidate spots around a centre: every horizontal neighbour in the ring between the two radii, snapped down
     * to the first standable position. Nearest ring first is not needed - the caller scores all of them - but the order
     * is deterministic, which is what makes a rejected plan reproducible in a debug log.
     */
    public static List<BlockPos> ringCandidates(Level level, Vec3 centre, double minRadius, double maxRadius)
    {
        List<BlockPos> candidates = new ArrayList<>();
        int outer = (int) Math.ceil(maxRadius);

        for (int dx = -outer; dx <= outer; ++dx)
        {
            for (int dz = -outer; dz <= outer; ++dz)
            {
                double horizontal = Math.sqrt((double) dx * dx + (double) dz * dz);

                if (horizontal < minRadius || horizontal > maxRadius)
                {
                    continue;
                }

                int x = (int) Math.floor(centre.x) + dx;
                int z = (int) Math.floor(centre.z) + dz;
                int base = (int) Math.floor(centre.y);

                for (int dy = 3; dy >= -4; --dy)
                {
                    BlockPos pos = new BlockPos(x, base + dy, z);

                    if (isStandable(level, pos))
                    {
                        candidates.add(pos);
                        break;
                    }
                }
            }
        }

        return candidates;
    }
}
