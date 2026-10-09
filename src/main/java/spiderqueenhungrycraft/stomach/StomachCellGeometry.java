package spiderqueenhungrycraft.stomach;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The pure geometry of a Stomach Cell (Phase 8E1).
 *
 * <p>A cell is a voxel sphere: the whole outer sphere is filled with the stomach wall, and the inner sphere is
 * carved out again, so the shell can never end up with a hole. The lower part of the inner sphere is a body of
 * still source water, the upper part is air. Everything is derived from the constants below, so the size of a
 * cell is changed in exactly one place.
 */
public final class StomachCellGeometry
{
    /** Inner radius of the cavity, in blocks. */
    public static final int INNER_RADIUS = 5;

    /** Outer radius of the wall shell, in blocks. */
    public static final int OUTER_RADIUS = 6;

    /** Y of every cell centre. One layer for all cells keeps the world simple. */
    public static final int CENTER_Y = 64;

    /** The water surface inside the cavity; the blocks below it are filled with source water. */
    public static final int WATER_SURFACE_Y = CENTER_Y - 2;

    /** Distance between two cell centres, in blocks. Two chunks, so two spheres can never touch. */
    public static final int CELL_SPACING = 32;

    /** Cells per row of the allocation grid. */
    private static final int CELLS_PER_ROW = 16;

    /** Flag of a mass write: the clients are updated, the neighbours are not, so nothing cascades. */
    private static final int WRITE_FLAG = 2;

    private StomachCellGeometry()
    {
    }

    /** @return the stable centre of the cell with that allocation index, always in the middle of a chunk. */
    public static BlockPos centerFor(int index)
    {
        int column = index % CELLS_PER_ROW;
        int row = index / CELLS_PER_ROW;

        return new BlockPos(column * CELL_SPACING + 8, CENTER_Y, row * CELL_SPACING + 8);
    }

    /** @return the spawn position inside that cell: the centre, just above the water surface. */
    public static BlockPos spawnPos(BlockPos center)
    {
        return new BlockPos(center.getX(), CENTER_Y, center.getZ());
    }

    /** @return true when that position lies inside the cavity of the cell around that centre. */
    public static boolean isInside(BlockPos center, double x, double y, double z)
    {
        return distanceSqr(center, x, y, z) <= (double) INNER_RADIUS * INNER_RADIUS;
    }

    /** @return true when that position lies inside the wall shell, cavity included. */
    public static boolean isWithinCell(BlockPos center, double x, double y, double z)
    {
        return distanceSqr(center, x, y, z) <= (double) OUTER_RADIUS * OUTER_RADIUS;
    }

    private static double distanceSqr(BlockPos center, double x, double y, double z)
    {
        double dx = x - (center.getX() + 0.5D);
        double dy = y - (center.getY() + 0.5D);
        double dz = z - (center.getZ() + 0.5D);

        return dx * dx + dy * dy + dz * dz;
    }

    /** @return the state that position of that cell must have, whatever is there right now. */
    public static BlockState wantedState(BlockPos center, BlockPos pos)
    {
        double dx = pos.getX() + 0.5D - (center.getX() + 0.5D);
        double dy = pos.getY() + 0.5D - (center.getY() + 0.5D);
        double dz = pos.getZ() + 0.5D - (center.getZ() + 0.5D);
        double squared = dx * dx + dy * dy + dz * dz;

        if (squared > (double) OUTER_RADIUS * OUTER_RADIUS)
        {
            return Blocks.AIR.defaultBlockState();
        }

        if (squared > (double) INNER_RADIUS * INNER_RADIUS)
        {
            return spiderqueenhungrycraft.registry.HungryCraftBlocks.STOMACH_WALL.get().defaultBlockState();
        }

        if (pos.getY() <= WATER_SURFACE_Y)
        {
            return Fluids.WATER.getSource(false).createLegacyBlock();
        }

        return Blocks.AIR.defaultBlockState();
    }

    /** Builds the whole cell: wall shell first, then the cavity, so the shell is always closed. */
    public static void build(ServerLevel level, BlockPos center)
    {
        for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++)
        {
            for (int dy = -OUTER_RADIUS; dy <= OUTER_RADIUS; dy++)
            {
                for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++)
                {
                    BlockPos pos = center.offset(dx, dy, dz);
                    level.setBlock(pos, wantedState(center, pos), WRITE_FLAG);
                }
            }
        }
    }

    /**
     * Compares the whole cell with the template and restores every block that does not match.
     *
     * @return how many blocks had to be repaired
     */
    public static int repair(ServerLevel level, BlockPos center)
    {
        int repaired = 0;

        for (int dx = -OUTER_RADIUS; dx <= OUTER_RADIUS; dx++)
        {
            for (int dy = -OUTER_RADIUS; dy <= OUTER_RADIUS; dy++)
            {
                for (int dz = -OUTER_RADIUS; dz <= OUTER_RADIUS; dz++)
                {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState wanted = wantedState(center, pos);

                    if (level.getBlockState(pos) != wanted)
                    {
                        level.setBlock(pos, wanted, WRITE_FLAG);
                        repaired++;
                    }
                }
            }
        }

        return repaired;
    }

    /** @return the size of the bounding cube of one cell, used by the debug log and the HUD. */
    public static int boundingCubeSize()
    {
        return OUTER_RADIUS * 2 + 1;
    }
}
