package spiderqueenhungrycraft.player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * The list of the spiders that march with a queen (Brood Selection + Escort Relocation round).
 *
 * <p>A follower can be far away, in a chunk that is not loaded any more. When the queen is suddenly somewhere else
 * entirely - a teleport of a thousand blocks, a portal - the server can no longer find her escort by looking around her,
 * so it needs to know <b>where each follower was last seen</b>. That is all this class is: a spider UUID, the dimension
 * it was in and the chunk it stood in, kept for every spider whose persistent order is FOLLOW and for nobody else.
 *
 * <p>It is deliberately small: it is not a squad database, it holds no health, no order and no combat state (the spider
 * itself is the truth for all of that), and it is stored inside the queen data of the player, so it travels with a world
 * save and with a player clone exactly like the silk of her glands does.
 */
public final class FollowRoster
{
    /** Root NBT key of the roster inside the queen data. */
    public static final String TAG_KEY = "FollowRoster";

    private static final String TAG_SPIDER = "Spider";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_CHUNK = "Chunk";

    /** One follower of the queen. */
    public record Entry(UUID spider, ResourceLocation dimension, long chunk)
    {
        /** @return the chunk of this entry, or null when its coordinates cannot be read any more. */
        @Nullable
        public ChunkPos chunkPos()
        {
            return new ChunkPos(chunk);
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    /** @return every follower the queen had, in the order they joined. */
    public Collection<Entry> entries()
    {
        return this.entries.values();
    }

    public int size()
    {
        return this.entries.size();
    }

    public boolean contains(UUID spider)
    {
        return this.entries.containsKey(spider);
    }

    /**
     * Notes where that spider is right now, and does nothing at all when it did not move to another chunk: a follower
     * that simply walks around is not written down again and again.
     */
    public void addOrTouch(UUID spider, ResourceKey<Level> dimension, ChunkPos chunk)
    {
        Entry known = this.entries.get(spider);

        if (known != null && known.dimension().equals(dimension.location()) && known.chunk() == chunk.toLong())
        {
            return;
        }

        this.entries.put(spider, new Entry(spider, dimension.location(), chunk.toLong()));
    }

    public void remove(UUID spider)
    {
        this.entries.remove(spider);
    }

    /** Drops one entry that turned out to be dead weight (a spider that cannot be found any more). */
    public void dropStale(UUID spider)
    {
        this.entries.remove(spider);
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (Entry entry : this.entries.values())
        {
            CompoundTag one = new CompoundTag();
            one.putUUID(TAG_SPIDER, entry.spider());
            one.putString(TAG_DIMENSION, entry.dimension().toString());
            one.putLong(TAG_CHUNK, entry.chunk());
            list.add(one);
        }

        tag.put(TAG_KEY, list);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        this.entries.clear();

        if (tag == null || !tag.contains(TAG_KEY, Tag.TAG_LIST))
        {
            return;
        }

        ListTag list = tag.getList(TAG_KEY, Tag.TAG_COMPOUND);

        for (int index = 0; index < list.size(); index++)
        {
            CompoundTag one = list.getCompound(index);

            if (!one.hasUUID(TAG_SPIDER))
            {
                continue;
            }

            ResourceLocation dimension = ResourceLocation.tryParse(one.getString(TAG_DIMENSION));

            if (dimension == null)
            {
                continue;
            }

            this.entries.put(one.getUUID(TAG_SPIDER),
                    new Entry(one.getUUID(TAG_SPIDER), dimension, one.getLong(TAG_CHUNK)));
        }
    }

    /** Copies the roster of another queen data, used when the player entity is rebuilt. */
    public void copyFrom(FollowRoster other)
    {
        this.entries.clear();
        this.entries.putAll(other.entries);
    }

    /** @return the dimension keys the roster knows about, without duplicates. */
    public List<ResourceLocation> dimensions()
    {
        List<ResourceLocation> dimensions = new ArrayList<>();

        for (Entry entry : this.entries.values())
        {
            if (!dimensions.contains(entry.dimension()))
            {
                dimensions.add(entry.dimension());
            }
        }

        return dimensions;
    }

    /** @return the dimension key of that resource location, or null when it is not a dimension any more. */
    @Nullable
    public static ResourceKey<Level> dimensionKey(ResourceLocation location)
    {
        return location == null ? null : ResourceKey.create(Registries.DIMENSION, location);
    }
}
