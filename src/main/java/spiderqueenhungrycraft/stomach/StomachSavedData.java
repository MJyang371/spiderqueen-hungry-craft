package spiderqueenhungrycraft.stomach;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The persistent half of the stomach system (Phase 8E1).
 *
 * <p>It only stores what must survive a restart: which cell belongs to which owner, and where that owner last
 * stood in an ordinary dimension. Everything else - tickets, occupants, the core entity - is runtime state and
 * is deliberately not written to disk, so a restart can never leave a cell force loaded.
 */
public final class StomachSavedData extends SavedData
{
    private static final String FILE_NAME = "spiderqueen_hungrycraft_stomach";

    private static final String KEY_OWNERS = "Owners";
    private static final String KEY_NEXT_INDEX = "NextIndex";
    private static final String KEY_UUID = "Owner";
    private static final String KEY_INDEX = "CellIndex";
    private static final String KEY_ANCHOR = "Anchor";
    private static final String KEY_ANCHOR_DIMENSION = "Dim";
    private static final String KEY_POS = "Pos";
    private static final String KEY_ROT = "Rot";

    private final Map<UUID, OwnerEntry> owners = new HashMap<>();
    private int nextIndex;

    /** Mutable per owner record; kept as a small class instead of a record because the anchor moves. */
    public static final class OwnerEntry
    {
        private final int cellIndex;
        @Nullable
        private final Anchor anchor;

        private OwnerEntry(int cellIndex, @Nullable Anchor anchor)
        {
            this.cellIndex = cellIndex;
            this.anchor = anchor;
        }

        public int cellIndex()
        {
            return cellIndex;
        }

        @Nullable
        public Anchor anchor()
        {
            return anchor;
        }
    }

    /**
     * The last position of an owner outside of the stomach dimension. It is the destination every occupant of
     * the cell is released to when the stomach closes.
     */
    public record Anchor(ResourceKey<Level> dimension, double x, double y, double z, float yaw, float pitch)
    {
        public BlockPos blockPos()
        {
            return BlockPos.containing(x, y, z);
        }
    }

    /** @return the saved data of the server, stored with the overworld so it always exists. */
    public static StomachSavedData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage()
                .computeIfAbsent(StomachSavedData::load, StomachSavedData::new, FILE_NAME);
    }

    /** @return the index of the cell of that owner, allocating a brand new cell when there is none yet. */
    public int indexFor(UUID owner)
    {
        OwnerEntry entry = owners.get(owner);

        if (entry != null)
        {
            return entry.cellIndex();
        }

        int index = nextIndex++;
        owners.put(owner, new OwnerEntry(index, null));
        setDirty();

        return index;
    }

    /** @return the cell of that owner, allocating it when needed. */
    public StomachCell cellFor(UUID owner)
    {
        int index = indexFor(owner);

        return new StomachCell(owner, index, StomachCellGeometry.centerFor(index));
    }

    /** @return the cell of that owner when one was ever allocated. */
    public Optional<StomachCell> existingCell(UUID owner)
    {
        OwnerEntry entry = owners.get(owner);

        if (entry == null)
        {
            return Optional.empty();
        }

        return Optional.of(new StomachCell(owner, entry.cellIndex(),
                StomachCellGeometry.centerFor(entry.cellIndex())));
    }

    /** @return the last external position of that owner, when it was ever recorded. */
    public Optional<Anchor> anchorOf(UUID owner)
    {
        OwnerEntry entry = owners.get(owner);

        if (entry == null || entry.anchor() == null)
        {
            return Optional.empty();
        }

        return Optional.of(entry.anchor());
    }

    /** Stores where that owner stands right now; ignored for positions inside the stomach itself. */
    public void updateAnchor(UUID owner, ResourceKey<Level> dimension, double x, double y, double z, float yaw,
            float pitch)
    {
        if (dimension.equals(StomachCellManager.DIMENSION))
        {
            return;
        }

        owners.put(owner, new OwnerEntry(indexFor(owner), new Anchor(dimension, x, y, z, yaw, pitch)));
        setDirty();
    }

    /** @return how many cells were ever allocated, used by the debug output. */
    public int allocatedCells()
    {
        return owners.size();
    }

    /** @return the owner of that cell index, when that cell was ever allocated. */
    public Optional<UUID> ownerForIndex(int index)
    {
        for (Map.Entry<UUID, OwnerEntry> entry : owners.entrySet())
        {
            if (entry.getValue().cellIndex() == index)
            {
                return Optional.of(entry.getKey());
            }
        }

        return Optional.empty();
    }

    public static StomachSavedData load(CompoundTag tag)
    {
        StomachSavedData data = new StomachSavedData();
        data.nextIndex = tag.getInt(KEY_NEXT_INDEX);
        ListTag list = tag.getList(KEY_OWNERS, Tag.TAG_COMPOUND);

        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);

            if (entry.hasUUID(KEY_UUID))
            {
                UUID owner = entry.getUUID(KEY_UUID);
                int index = entry.getInt(KEY_INDEX);
                data.owners.put(owner, new OwnerEntry(index, readAnchor(entry)));
                data.nextIndex = Math.max(data.nextIndex, index + 1);
            }
        }

        return data;
    }

    @Nullable
    private static Anchor readAnchor(CompoundTag entry)
    {
        if (!entry.contains(KEY_ANCHOR, Tag.TAG_COMPOUND))
        {
            return null;
        }

        CompoundTag anchorTag = entry.getCompound(KEY_ANCHOR);
        ResourceLocation dimensionId = ResourceLocation.tryParse(anchorTag.getString(KEY_ANCHOR_DIMENSION));

        if (dimensionId == null || !anchorTag.contains(KEY_POS, Tag.TAG_LIST))
        {
            return null;
        }

        ListTag pos = anchorTag.getList(KEY_POS, Tag.TAG_DOUBLE);

        if (pos.size() < 3)
        {
            return null;
        }

        ListTag rot = anchorTag.getList(KEY_ROT, Tag.TAG_FLOAT);

        return new Anchor(ResourceKey.create(Registries.DIMENSION, dimensionId), pos.getDouble(0), pos.getDouble(1),
                pos.getDouble(2), rot.size() > 0 ? rot.getFloat(0) : 0.0F, rot.size() > 1 ? rot.getFloat(1) : 0.0F);
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putInt(KEY_NEXT_INDEX, nextIndex);
        ListTag list = new ListTag();

        owners.forEach((owner, entry) ->
        {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID(KEY_UUID, owner);
            entryTag.putInt(KEY_INDEX, entry.cellIndex());
            Anchor anchor = entry.anchor();

            if (anchor != null)
            {
                CompoundTag anchorTag = new CompoundTag();
                anchorTag.putString(KEY_ANCHOR_DIMENSION, anchor.dimension().location().toString());
                ListTag pos = new ListTag();
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.x()));
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.y()));
                pos.add(net.minecraft.nbt.DoubleTag.valueOf(anchor.z()));
                anchorTag.put(KEY_POS, pos);
                ListTag rot = new ListTag();
                rot.add(net.minecraft.nbt.FloatTag.valueOf(anchor.yaw()));
                rot.add(net.minecraft.nbt.FloatTag.valueOf(anchor.pitch()));
                anchorTag.put(KEY_ROT, rot);
                entryTag.put(KEY_ANCHOR, anchorTag);
            }

            list.add(entryTag);
        });

        tag.put(KEY_OWNERS, list);
        return tag;
    }
}
