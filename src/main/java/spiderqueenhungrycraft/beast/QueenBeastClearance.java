package spiderqueenhungrycraft.beast;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The transformation clearance of the Queen Beast form (Phase 8A, moved onto the unified block rules in
 * Phase 8C).
 *
 * <p>Its single job is to guarantee that the transformation succeeds: ordinary blocks that really overlap the
 * beast body are removed so the body has room. It is not a mining ability, so the removed blocks never drop
 * anything, and it never touches a block that only touches the body with a bounding box face, so the floor the
 * player stands on survives a transformation.
 *
 * <p>What may be removed is not decided here any more: {@link QueenBeastBlockRules} answers it from the
 * {@code block_breaking.transform_clearance} section of the datapack tuning, and
 * {@link QueenBeastBlockBreaker} removes the block with the protection hooks of the game intact. The clearance
 * therefore follows exactly the same priority as the body crush, the stomp and the charge: protected never
 * breaks, unbreakable never breaks, stored data is left alone unless the rule allows it.
 */
public final class QueenBeastClearance
{
    /** Hard cap of blocks one transformation may remove. */
    private static final int CLEAR_BUDGET = 512;

    private QueenBeastClearance()
    {
    }

    /**
     * Removes every allowed block that really overlaps that box.
     *
     * @return how many blocks were removed
     */
    public static int clear(ServerLevel level, ServerPlayer player, AABB box)
    {
        return QueenBeastBlockBreaker.breakBlocks(level, player, obstructions(level, box),
                QueenBeastTuning.RuleProfile.TRANSFORM_CLEARANCE, CLEAR_BUDGET);
    }

    /** @return true when nothing with a collision shape overlaps that box, so the body really fits. */
    public static boolean hasRoom(Level level, AABB box)
    {
        return obstructions(level, box).isEmpty();
    }

    /**
     * @return every block position whose collision shape really overlaps the box in volume. Blocks that only
     *         touch the box, and everything without a collision shape such as air, grass or water, are not
     *         part of the result.
     */
    public static List<BlockPos> obstructions(Level level, AABB box)
    {
        List<BlockPos> found = new ArrayList<>();

        // The body is shrunk by a very small epsilon, otherwise a box that rests exactly on a floor would
        // count that floor as an obstruction and a transformation would eat the ground below the player.
        VoxelShape body = Shapes.create(box.deflate(QueenBeastConstants.CLEARANCE_EPSILON));

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = Mth.floor(box.minX); x <= Mth.floor(box.maxX); x++)
        {
            for (int y = Mth.floor(box.minY); y <= Mth.floor(box.maxY); y++)
            {
                for (int z = Mth.floor(box.minZ); z <= Mth.floor(box.maxZ); z++)
                {
                    pos.set(x, y, z);

                    BlockState state = level.getBlockState(pos);
                    VoxelShape shape = state.getCollisionShape(level, pos);

                    if (shape.isEmpty())
                    {
                        continue;
                    }

                    // A real volume overlap against the true block shape, so stairs, slabs and every other
                    // partial block are judged by what they really occupy.
                    if (Shapes.joinIsNotEmpty(body, shape.move(x, y, z), BooleanOp.AND))
                    {
                        found.add(pos.immutable());
                    }
                }
            }
        }

        return found;
    }

    /** @return true when that block may be removed to make room for the beast body under the current rules. */
    public static boolean isClearable(Level level, BlockPos pos, BlockState state)
    {
        return QueenBeastBlockRules.canBreak(level, pos, state,
                QueenBeastTuning.RuleProfile.TRANSFORM_CLEARANCE);
    }
}
