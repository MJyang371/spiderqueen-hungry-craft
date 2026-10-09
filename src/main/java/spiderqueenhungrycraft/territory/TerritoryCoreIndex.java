package spiderqueenhungrycraft.territory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * The geometry and the bookkeeping of a territory core (Phase LAIR-CORE-1).
 *
 * <p>The protection is section aligned on purpose: a core protects the 3x3x3 <b>chunk sections</b> around its own
 * section, which is 27 sections and therefore 48x48x48 blocks. It is not a box that is centred on the block the
 * player clicked, so a core in the corner of a section protects a range that starts where the section starts - what
 * the player sees is exactly what is protected.
 *
 * <p>The index itself lives in {@link TerritoryCoreSavedData}, which is per level, so every dimension has its own
 * list and its own reference counted section table. A lookup is one {@link SectionPos#asLong(BlockPos)} and one map
 * access, never a scan of 48x48x48 blocks.
 */
public final class TerritoryCoreIndex
{
    /** How many sections the protection reaches in every direction: one. */
    public static final int SECTION_RADIUS = 1;

    /** The number of protected sections: 3 * 3 * 3. */
    public static final int PROTECTED_SECTIONS = 27;

    /** The size of the protected box in blocks: 3 * 16. */
    public static final int PROTECTED_BLOCKS = PROTECTED_SECTIONS / 9 * 16;

    private TerritoryCoreIndex()
    {
    }

    /** @return the 27 section keys a core at that position protects. */
    public static List<Long> sectionsOf(BlockPos corePos)
    {
        SectionPos centre = SectionPos.of(corePos);
        List<Long> sections = new ArrayList<>(PROTECTED_SECTIONS);

        for (int dx = -SECTION_RADIUS; dx <= SECTION_RADIUS; ++dx)
        {
            for (int dy = -SECTION_RADIUS; dy <= SECTION_RADIUS; ++dy)
            {
                for (int dz = -SECTION_RADIUS; dz <= SECTION_RADIUS; ++dz)
                {
                    sections.add(SectionPos.asLong(centre.x() + dx, centre.y() + dy, centre.z() + dz));
                }
            }
        }

        return sections;
    }

    /**
     * @return the section aligned box a core protects: the whole 3x3x3 sections, from the first block of the lowest
     *         section to the last block of the highest one. Both the server and the client draw from this one method,
     *         so the outline can never disagree with the protection.
     */
    public static AABB protectedBox(BlockPos corePos)
    {
        SectionPos centre = SectionPos.of(corePos);
        SectionPos min = centre.offset(-SECTION_RADIUS, -SECTION_RADIUS, -SECTION_RADIUS);
        SectionPos max = centre.offset(SECTION_RADIUS, SECTION_RADIUS, SECTION_RADIUS);

        return new AABB(min.minBlockX(), min.minBlockY(), min.minBlockZ(), max.maxBlockX() + 1.0D,
                max.maxBlockY() + 1.0D, max.maxBlockZ() + 1.0D);
    }

    /** Registers a core that was placed, so its range is protected from this tick on. */
    public static void register(ServerLevel level, BlockPos corePos)
    {
        TerritoryCoreSavedData.get(level).addCore(corePos);
    }

    /** Unregisters a core that is gone, so its range stops being protected at once. */
    public static void unregister(ServerLevel level, BlockPos corePos)
    {
        TerritoryCoreSavedData.get(level).removeCore(corePos);
    }

    /** @return true when that position lies inside the protection of any territory core of that level. */
    public static boolean isProtected(ServerLevel level, BlockPos pos)
    {
        return TerritoryCoreSavedData.get(level).isSectionProtected(SectionPos.asLong(pos));
    }
}
