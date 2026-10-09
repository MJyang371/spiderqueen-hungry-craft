package spiderqueenhungrycraft.stomach;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * What is really inside a Stomach Cell (Phase 8E2A).
 *
 * <p>{@link StomachCellManager#occupants} answers who is alive in there, which is what the HUD and the digestion
 * care about. The life cycle of a cell needs a second, wider answer: a stomach that only holds the loot of a
 * dead guest is still occupied, otherwise the drops of a death would be thrown away with the cell.
 *
 * <p>So this is the one place that decides what "content" means: every living creature except the wall itself,
 * plus the items and the experience orbs that lie in the cavity. The core is never content - it is the wall, and
 * a wall that counted as content would keep its own cell loaded forever.
 */
public final class StomachContents
{
    private StomachContents()
    {
    }

    /** @return everything inside that cell that counts as content, the core excluded. */
    public static List<Entity> collect(ServerLevel level, StomachCell cell)
    {
        AABB box = cellBox(cell);
        List<Entity> found = new ArrayList<>();

        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof StomachCoreEntity || entity.isRemoved() || !isContent(entity))
            {
                continue;
            }

            if (box.intersects(entity.getBoundingBox()) && cell.contains(entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ()))
            {
                found.add(entity);
            }
        }

        return found;
    }

    /** @return true when that cell holds nothing at all any more. */
    public static boolean isEmpty(ServerLevel level, StomachCell cell)
    {
        return collect(level, cell).isEmpty();
    }

    /** @return true when that entity is something a stomach holds: a guest, an item or an orb. */
    public static boolean isContent(Entity entity)
    {
        return entity instanceof net.minecraft.world.entity.LivingEntity || entity instanceof ItemEntity
                || entity instanceof ExperienceOrb;
    }

    /** The search box of one cell, one block wider than its wall shell. */
    private static AABB cellBox(StomachCell cell)
    {
        net.minecraft.core.BlockPos center = cell.center();
        double radius = StomachCellGeometry.OUTER_RADIUS + 1.0D;

        return new AABB(center.getX() + 0.5D - radius, center.getY() + 0.5D - radius,
                center.getZ() + 0.5D - radius, center.getX() + 0.5D + radius, center.getY() + 0.5D + radius,
                center.getZ() + 0.5D + radius);
    }
}
