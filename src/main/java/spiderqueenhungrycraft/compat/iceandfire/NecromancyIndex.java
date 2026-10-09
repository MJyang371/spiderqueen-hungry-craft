package spiderqueenhungrycraft.compat.iceandfire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The index of every live contract of one level (Necromancy IV round), and the reason the server never has to scan a
 * world to find its skeletons.
 *
 * <p>A contract itself lives on the dragon (see {@link NecromancyContract}), which is what makes it survive a chunk
 * unload and a restart. What the dragon can not answer is "is there anything at all to look at right now", and asking
 * a whole level that question every tick would be exactly the sweep the round forbids. So the level keeps this list -
 * dragon id to deadline - and the tick pass walks <b>it</b> and nothing else: an empty index costs one map lookup per
 * tick, and a level with three live contracts costs three.
 *
 * <p>It is a {@link SavedData}, so the list is written into the save file next to the contracts it describes and is
 * there again after a restart. An entry whose dragon is not loaded right now is kept untouched: the deadline is
 * absolute game time, so the contract still runs out on schedule and is ended the moment the dragon is loaded again.
 */
public final class NecromancyIndex extends SavedData
{
    /** The name of the file this index is written under, inside the data folder of the level. */
    public static final String FILE_ID = "spiderqueen_hungrycraft_necromancy";

    private static final String CONTRACTS = "contracts";
    private static final String DRAGON = "dragon";
    private static final String EXPIRES_AT = "expires_at";

    /** Dragon id to the game time its contract ends at. */
    private final Map<UUID, Long> deadlines = new HashMap<>();

    /** @return the index of that level, read from the save file the first time it is asked for. */
    public static NecromancyIndex get(ServerLevel level)
    {
        return level.getDataStorage().computeIfAbsent(NecromancyIndex::load, NecromancyIndex::new, FILE_ID);
    }

    /** @return the index of that level, or empty when it has none - which is the ordinary case of a young world. */
    public static Optional<NecromancyIndex> find(ServerLevel level)
    {
        return Optional.of(get(level));
    }

    /** @return true when nothing is under contract in this level, which is the cheapest question of them all. */
    public boolean isEmpty()
    {
        return this.deadlines.isEmpty();
    }

    /** @return how many dragons this level has under contract right now. */
    public int size()
    {
        return this.deadlines.size();
    }

    /** Writes or replaces the deadline of one dragon. */
    public void put(UUID dragon, long expiresAt)
    {
        this.deadlines.put(dragon, expiresAt);
        this.setDirty();
    }

    /** Forgets one dragon, which is what ending a contract does. */
    public void remove(UUID dragon)
    {
        if (this.deadlines.remove(dragon) != null)
        {
            this.setDirty();
        }
    }

    /** @return the deadline of that dragon, or empty when it is not under contract. */
    public Optional<Long> deadlineOf(UUID dragon)
    {
        return Optional.ofNullable(this.deadlines.get(dragon));
    }

    /** @return every dragon this level has under contract right now. */
    public List<UUID> dragons()
    {
        return new ArrayList<>(this.deadlines.keySet());
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();

        for (Map.Entry<UUID, Long> entry : this.deadlines.entrySet())
        {
            CompoundTag one = new CompoundTag();
            one.putUUID(DRAGON, entry.getKey());
            one.putLong(EXPIRES_AT, entry.getValue());
            list.add(one);
        }

        tag.put(CONTRACTS, list);

        return tag;
    }

    /** @return the index of that tag, empty when the world has never had a contract. */
    private static NecromancyIndex load(CompoundTag tag)
    {
        NecromancyIndex index = new NecromancyIndex();
        ListTag list = tag.getList(CONTRACTS, Tag.TAG_COMPOUND);

        for (int position = 0; position < list.size(); position++)
        {
            CompoundTag one = list.getCompound(position);

            if (one.hasUUID(DRAGON))
            {
                index.deadlines.put(one.getUUID(DRAGON), one.getLong(EXPIRES_AT));
            }
        }

        return index;
    }
}
