package spiderqueenhungrycraft.stomach;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * One Stomach Cell: the persistent identity of the private pocket world of one player.
 *
 * <p>The cell is addressed by its allocation index, which is stable forever, and its centre, which is always the
 * middle of a chunk. Nothing about the cell is runtime state, so a cell can be described at any time, whether it
 * is loaded, empty or not.
 *
 * @param owner the player this stomach belongs to
 * @param index the allocation index inside the shared stomach dimension
 * @param center the centre block of the cell
 */
public record StomachCell(UUID owner, int index, BlockPos center)
{
    /** @return the one chunk that carries the whole cell. */
    public ChunkPos chunkPos()
    {
        return new ChunkPos(center.getX() >> 4, center.getZ() >> 4);
    }

    /** @return the block position a visitor of this cell is placed at. */
    public BlockPos spawnPos()
    {
        return StomachCellGeometry.spawnPos(center);
    }

    /** @return true when that position is part of this cell, wall shell included. */
    public boolean contains(double x, double y, double z)
    {
        return StomachCellGeometry.isWithinCell(center, x, y, z);
    }

    /** @return true when that position is inside the cavity of this cell. */
    public boolean containsInner(double x, double y, double z)
    {
        return StomachCellGeometry.isInside(center, x, y, z);
    }
}
