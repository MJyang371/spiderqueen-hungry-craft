package spiderqueenhungrycraft.devour;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.stomach.StomachCell;
import spiderqueenhungrycraft.stomach.StomachCellGeometry;

/**
 * Where a swallowed creature lands inside its new stomach (Phase 8E2A).
 *
 * <p>A cell is never resized for a guest and nothing is ever forced: the spot has to be a place where the real
 * hitbox of that creature fits without touching the wall. The search is deterministic - the same index always
 * produces the same ring position - so a group of swallowed creatures spreads over the cavity instead of
 * suffocating in one point, and a reload of the server reproduces the very same layout.
 *
 * <p>A creature that does not fit - a ghast in a small cavity, for example - gets no spot at all. The caller
 * then leaves it where it was, which is the only safe outcome: a half swallowed creature would either be stuck
 * in the wall or duplicated between two worlds.
 */
public final class DevourPlacement
{
    private DevourPlacement()
    {
    }

    /**
     * @return the position a swallowed entity has to be placed at, or null when its body does not fit anywhere
     *         in the cavity of that cell.
     */
    @Nullable
    public static Vec3 spotFor(ServerLevel level, StomachCell cell, Entity entity, int index)
    {
        AABB box = entity.getBoundingBox();
        double height = Math.max(box.getYsize(), 0.5D);

        for (int attempt = 0; attempt < DevourTuning.CELL_DROP_TRIES; attempt++)
        {
            Vec3 candidate = ringSpot(cell, index + attempt);

            if (!cell.containsInner(candidate.x, candidate.y + height * 0.5D, candidate.z))
            {
                continue;
            }

            if (level.noCollision(box.move(candidate.subtract(entity.position()))))
            {
                return candidate;
            }
        }

        // The middle of the cavity, just above the water: the last chance for a creature whose hitbox is wider
        // than the ring the search walks.
        Vec3 center = center(cell);

        if (level.noCollision(box.move(center.subtract(entity.position()))))
        {
            return center;
        }

        return null;
    }

    /** One point of the golden angle ring around the cell centre, deterministic and always inside the cavity. */
    private static Vec3 ringSpot(StomachCell cell, int index)
    {
        Vec3 center = center(cell);

        if (index <= 0)
        {
            return center;
        }

        double angle = index * 2.399963229728653D;
        double radius = DevourTuning.CELL_DROP_RING_RADIUS * Math.sqrt((double) (index % 9) / 9.0D);

        return new Vec3(center.x + Math.cos(angle) * radius,
                center.y + DevourTuning.CELL_DROP_HEIGHT * (index % 3),
                center.z + Math.sin(angle) * radius);
    }

    /** The middle of the cavity, a little above the water line. */
    private static Vec3 center(StomachCell cell)
    {
        return new Vec3(cell.center().getX() + 0.5D,
                StomachCellGeometry.WATER_SURFACE_Y + 1.0D,
                cell.center().getZ() + 0.5D);
    }
}
