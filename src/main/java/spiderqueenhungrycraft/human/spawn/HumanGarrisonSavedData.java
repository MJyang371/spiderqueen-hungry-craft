package spiderqueenhungrycraft.human.spawn;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The list of villages that already received their garrison (Phase HF0D).
 *
 * <p>It lives in the save file of the world, so a village is only ever garrisoned once - across reloads, across
 * sessions and also for villages that existed long before this mod was installed. The key of a village is derived
 * from its own anchor, see {@link HumanVillageGarrisonSpawner}.
 */
public class HumanGarrisonSavedData extends SavedData
{
    /** The name of the file inside the world save folder. */
    public static final String FILE_ID = SpiderQueenHungryCraft.MODID + "_village_garrison";

    private static final String TAG_VILLAGES = "Villages";

    private final Set<String> processed = new HashSet<>();

    /** @return the data of that level, loaded from the save file when it is there. */
    public static HumanGarrisonSavedData get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(HumanGarrisonSavedData::load, HumanGarrisonSavedData::new,
                FILE_ID);
    }

    public boolean isProcessed(String villageKey)
    {
        return this.processed.contains(villageKey);
    }

    public void markProcessed(String villageKey)
    {
        if (this.processed.add(villageKey))
        {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();

        for (String key : this.processed)
        {
            list.add(StringTag.valueOf(key));
        }

        tag.put(TAG_VILLAGES, list);
        return tag;
    }

    private static HumanGarrisonSavedData load(CompoundTag tag)
    {
        HumanGarrisonSavedData data = new HumanGarrisonSavedData();
        ListTag list = tag.getList(TAG_VILLAGES, Tag.TAG_STRING);

        for (int i = 0; i < list.size(); ++i)
        {
            data.processed.add(list.getString(i));
        }

        return data;
    }
}
