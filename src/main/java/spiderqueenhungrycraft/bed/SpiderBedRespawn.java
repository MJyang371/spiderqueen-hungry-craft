package spiderqueenhungrycraft.bed;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The safe respawn position of a spider bed (Phase 8E3).
 *
 * <p>A spider bed has no head end and only an axis, so there is no "stand up next to the pillow" like a
 * vanilla bed has. Instead the two sides of the plane are tried in a fixed order, closest to the bed first,
 * and the first spot that is really safe wins:
 *
 * <ul>
 * <li>the bed itself, which never collides, so a queen may simply be placed into her own mat,</li>
 * <li>both sides of the bed plane, one and two blocks out, which covers a wall bed on either side and a
 * ceiling bed underneath,</li>
 * <li>the plain neighbours of the bed block as a last resort.</li>
 * </ul>
 *
 * <p>Safe means: inside the build height, the whole player box free of collisions (no suffocation, real
 * head room), no lava anywhere in that box, not directly above the void, and either the bed itself or a
 * floor within a few blocks below. When nothing is safe the result stays empty and the vanilla respawn
 * fallback takes over, exactly as if the bed had been broken.
 */
public final class SpiderBedRespawn
{
    /** How many blocks out from the bed the two sides of its plane are tried. */
    private static final int SIDE_DISTANCE = 2;

    /** How deep below a candidate a real floor may be, so a bed over the void is never used. */
    private static final int FLOOR_SCAN = 4;

    private SpiderBedRespawn()
    {
    }

    /**
     * @return the position the player is moved to, or an empty result when vanilla has to fall back
     */
    public static Optional<Vec3> findSafePosition(EntityType<?> type, ServerLevel level, BlockPos bedPos,
            Direction.Axis axis)
    {
        for (BlockPos candidate : candidates(bedPos, axis))
        {
            Optional<Vec3> position = check(type, level, bedPos, candidate);

            if (position.isPresent())
            {
                return position;
            }
        }

        return Optional.empty();
    }

    /** The fixed order of candidates: the bed, both sides of its plane and then its plain neighbours. */
    private static Set<BlockPos> candidates(BlockPos bedPos, Direction.Axis axis)
    {
        Direction negative = negativeSide(axis);
        Direction positive = positiveSide(axis);
        Set<BlockPos> candidates = new LinkedHashSet<>();

        candidates.add(bedPos);

        for (int distance = 1; distance <= SIDE_DISTANCE; distance++)
        {
            candidates.add(bedPos.relative(negative, distance));
            candidates.add(bedPos.relative(positive, distance));
        }

        for (Direction direction : Direction.values())
        {
            candidates.add(bedPos.relative(direction));
        }

        return candidates;
    }

    /** The side of a bed plane that comes first, for example down for a horizontal bed. */
    private static Direction negativeSide(Direction.Axis axis)
    {
        return switch (axis)
        {
            case Y -> Direction.DOWN;
            case X -> Direction.WEST;
            case Z -> Direction.NORTH;
        };
    }

    /** The other side of a bed plane. */
    private static Direction positiveSide(Direction.Axis axis)
    {
        return negativeSide(axis).getOpposite();
    }

    /** @return the spawn position of that candidate, or an empty result when it is not safe */
    private static Optional<Vec3> check(EntityType<?> type, ServerLevel level, BlockPos bedPos, BlockPos candidate)
    {
        if (level.isOutsideBuildHeight(candidate))
        {
            return Optional.empty();
        }

        double x = candidate.getX() + 0.5D;
        double y = candidate.getY();
        double z = candidate.getZ() + 0.5D;
        AABB box = type.getDimensions().makeBoundingBox(x, y, z);

        // No suffocation and real head room: the complete body of the player has to fit without a collision.
        if (!level.noCollision(box))
        {
            return Optional.empty();
        }

        if (touchesLava(level, box))
        {
            return Optional.empty();
        }

        // Not over the void: a bed at the very bottom of the world is never used as a spawn.
        if (candidate.getY() - 1 < level.getMinBuildHeight())
        {
            return Optional.empty();
        }

        // Something to stand on: the bed the queen built herself counts, everything else needs a floor
        // close enough below, so nobody ever wakes up in the middle of nothing.
        if (!candidate.equals(bedPos) && !hasFloorBelow(level, candidate))
        {
            return Optional.empty();
        }

        return Optional.of(new Vec3(x, y, z));
    }

    /** @return true when any block of that box carries lava */
    private static boolean touchesLava(ServerLevel level, AABB box)
    {
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY - 1.0E-4D, box.maxZ);

        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            if (level.getFluidState(pos).is(FluidTags.LAVA))
            {
                return true;
            }
        }

        return false;
    }

    /** @return true when a real floor is close enough below that position */
    private static boolean hasFloorBelow(ServerLevel level, BlockPos candidate)
    {
        for (int depth = 1; depth <= FLOOR_SCAN; depth++)
        {
            BlockPos below = candidate.below(depth);

            if (level.isOutsideBuildHeight(below))
            {
                return false;
            }

            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty())
            {
                return true;
            }
        }

        return false;
    }
}
