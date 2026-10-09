package spiderqueenhungrycraft.territory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The territory cores of one level and the sections they protect (Phase LAIR-CORE-1).
 *
 * <p>Two things live here, and they answer two different questions:
 * <ul>
 * <li>{@code cores} is the persistent truth: which cores exist in this dimension. It is what a reload reads back.</li>
 * <li>{@code protectedSections} is the live index: how many cores protect each section. It is rebuilt from the cores
 * whenever the file is loaded, and a section is only forgotten when its count reaches zero - which is what makes two
 * overlapping cores behave correctly when one of them is broken.</li>
 * </ul>
 *
 * <p>Because the list is per level and stored in the save file, the protection of a core is active the moment the
 * world is loaded, no matter whether its chunk is loaded or not.
 */
public class TerritoryCoreSavedData extends SavedData
{
    /** The name of the file inside the save folder of that dimension. */
    public static final String FILE_ID = SpiderQueenHungryCraft.MODID + "_territory_cores";

    private static final String TAG_CORES = "Cores";

    /** Every core of this level, as an encoded block position. */
    private final Set<Long> cores = new HashSet<>();

    /** How many cores protect each of their sections. */
    private final Map<Long, Integer> protectedSections = new HashMap<>();

    /** @return the data of that level, loaded from the save file when it is there. */
    public static TerritoryCoreSavedData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(TerritoryCoreSavedData::load, TerritoryCoreSavedData::new,
                FILE_ID);
    }

    /** Adds a core and registers its 27 sections. @return false when that core was already known. */
    public boolean addCore(BlockPos corePos)
    {
        if (!this.cores.add(corePos.asLong()))
        {
            return false;
        }

        for (long section : TerritoryCoreIndex.sectionsOf(corePos))
        {
            this.protectedSections.merge(section, 1, Integer::sum);
        }

        this.setDirty();
        return true;
    }

    /** Removes a core and gives its sections one reference back. @return false when that core was not known. */
    public boolean removeCore(BlockPos corePos)
    {
        if (!this.cores.remove(corePos.asLong()))
        {
            return false;
        }

        for (long section : TerritoryCoreIndex.sectionsOf(corePos))
        {
            Integer count = this.protectedSections.get(section);

            if (count == null)
            {
                continue;
            }

            if (count <= 1)
            {
                this.protectedSections.remove(section);
            }
            else
            {
                this.protectedSections.put(section, count - 1);
            }
        }

        this.setDirty();
        return true;
    }

    public boolean isSectionProtected(long sectionKey)
    {
        return this.protectedSections.containsKey(sectionKey);
    }

    /** @return how many cores this level holds, used by the debug output. */
    public int coreCount()
    {
        return this.cores.size();
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        long[] encoded = new long[this.cores.size()];
        int index = 0;

        for (long core : this.cores)
        {
            encoded[index++] = core;
        }

        tag.put(TAG_CORES, new LongArrayTag(encoded));
        return tag;
    }

    private static TerritoryCoreSavedData load(CompoundTag tag)
    {
        TerritoryCoreSavedData data = new TerritoryCoreSavedData();

        for (long encoded : tag.getLongArray(TAG_CORES))
        {
            data.addCore(BlockPos.of(encoded));
        }

        // addCore marks the data dirty, the freshly loaded file is clean until something really changes again.
        data.setDirty(false);
        return data;
    }
}
