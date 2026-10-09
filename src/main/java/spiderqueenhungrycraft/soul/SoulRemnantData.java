package spiderqueenhungrycraft.soul;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The one global registry of soul remnants (Soul Foundation V1), shared by every dimension of a server.
 *
 * <p>It answers the question a {@link SoulRemnantEntity} can never answer alone: <b>which remnant of this player is
 * the real one right now?</b> A player may leave a remnant somewhere they never return to, and die again a hundred
 * blocks and one dimension further away - the old record is then replaced here, without the old chunk ever being
 * loaded, and the old entity discovers that it is obsolete the moment its chunk ticks again.
 *
 * <p>It is stored with the overworld of the server, which is the one place the game always loads, so a remnant in a
 * dimension nobody visits is still governed by a record that a restart cannot lose.
 */
public final class SoulRemnantData extends SavedData
{
    private static final String FILE_NAME = "spiderqueen_hungrycraft_souls";

    private static final String KEY_REMNANTS = "Remnants";
    private static final String KEY_OWNER = "Owner";
    private static final String KEY_TOKEN = "Token";
    private static final String KEY_SOULS = "Souls";
    private static final String KEY_DIMENSION = "Dim";
    private static final String KEY_POS = "Pos";

    /**
     * The one active remnant of a player.
     *
     * @param token     the identity of this remnant: the value a {@link SoulRemnantEntity} compares itself against
     * @param souls     how many souls the remnant holds
     * @param dimension where it stands
     * @param x         its position, kept for diagnostics and so a record can be read by a human
     * @param y         its position
     * @param z         its position
     */
    public record ActiveRemnant(UUID token, long souls, ResourceKey<Level> dimension, double x, double y, double z)
    {
    }

    /** One record per player, at most one each: the whole point of this registry. */
    private final Map<UUID, ActiveRemnant> active = new HashMap<>();

    /** @return the registry of that server, stored with its overworld so it always exists. */
    public static SoulRemnantData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SoulRemnantData::load, SoulRemnantData::new, FILE_NAME);
    }

    /** @return the active remnant record of that player, or null when that player has none. */
    @Nullable
    public ActiveRemnant activeFor(UUID owner)
    {
        return this.active.get(owner);
    }

    /** Writes the record of that player, replacing whatever they had before - at most one, always. */
    public void put(UUID owner, ActiveRemnant record)
    {
        this.active.put(owner, record);
        setDirty();
    }

    /**
     * Drops the record of that player, whatever it was.
     *
     * @return the record that was dropped, or null when that player had none.
     */
    @Nullable
    public ActiveRemnant remove(UUID owner)
    {
        ActiveRemnant removed = this.active.remove(owner);

        if (removed != null)
        {
            setDirty();
        }

        return removed;
    }

    /**
     * Drops the record of that player, but only while it still is the token the caller believes it is.
     *
     * <p>This is the transaction behind a claim: two creatures that touch the same remnant in the same tick both
     * ask here, and exactly one of them is answered with true. Whoever loses the race never pays out anything.
     *
     * @return true when the record was really removed by this call.
     */
    public boolean removeIfMatches(UUID owner, UUID token)
    {
        ActiveRemnant record = this.active.get(owner);

        if (record == null || !record.token().equals(token))
        {
            return false;
        }

        this.active.remove(owner);
        setDirty();

        return true;
    }

    /** @return how many players have an active remnant right now. */
    public int size()
    {
        return this.active.size();
    }

    /** Forgets every remnant of every player. @return how many records were dropped. */
    public int clearAll()
    {
        int cleared = this.active.size();

        if (cleared > 0)
        {
            this.active.clear();
            setDirty();
        }

        return cleared;
    }

    public static SoulRemnantData load(CompoundTag tag)
    {
        SoulRemnantData data = new SoulRemnantData();
        ListTag list = tag.getList(KEY_REMNANTS, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            ActiveRemnant record = readRecord(entry);

            if (record != null && entry.hasUUID(KEY_OWNER))
            {
                data.active.put(entry.getUUID(KEY_OWNER), record);
            }
        }

        return data;
    }

    @Nullable
    private static ActiveRemnant readRecord(CompoundTag entry)
    {
        if (!entry.hasUUID(KEY_TOKEN))
        {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(entry.getString(KEY_DIMENSION));

        if (dimensionId == null || !entry.contains(KEY_POS, Tag.TAG_LIST))
        {
            return null;
        }

        ListTag pos = entry.getList(KEY_POS, Tag.TAG_DOUBLE);

        if (pos.size() < 3)
        {
            return null;
        }

        return new ActiveRemnant(entry.getUUID(KEY_TOKEN), entry.getLong(KEY_SOULS),
                ResourceKey.create(Registries.DIMENSION, dimensionId), pos.getDouble(0), pos.getDouble(1),
                pos.getDouble(2));
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();

        this.active.forEach((owner, record) ->
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_OWNER, owner);
            entry.putUUID(KEY_TOKEN, record.token());
            entry.putLong(KEY_SOULS, record.souls());
            entry.putString(KEY_DIMENSION, record.dimension().location().toString());

            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(record.x()));
            pos.add(DoubleTag.valueOf(record.y()));
            pos.add(DoubleTag.valueOf(record.z()));
            entry.put(KEY_POS, pos);

            list.add(entry);
        });

        tag.put(KEY_REMNANTS, list);
        return tag;
    }
}
