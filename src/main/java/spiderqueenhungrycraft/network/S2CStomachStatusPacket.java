package spiderqueenhungrycraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import spiderqueenhungrycraft.client.StomachClientState;

/**
 * The stomach status a cell owner receives (Phase 8E1-GATE2B).
 *
 * <p>The server is the only authority here: a client can never look into the stomach dimension on its own, so
 * everything the small status panel shows arrives in this packet. It only ever travels to the owner of the cell,
 * it carries a sequence number so an older packet can never overwrite a newer one, and it carries no core at all,
 * because the wall is not a guest.
 */
public class S2CStomachStatusPacket
{
    private final UUID owner;
    private final int cellIndex;
    private final int sequence;
    private final boolean loaded;
    private final List<Occupant> occupants;

    public S2CStomachStatusPacket(UUID owner, int cellIndex, int sequence, boolean loaded, List<Occupant> occupants)
    {
        this.owner = owner;
        this.cellIndex = cellIndex;
        this.sequence = sequence;
        this.loaded = loaded;
        this.occupants = occupants;
    }

    public S2CStomachStatusPacket(FriendlyByteBuf buffer)
    {
        this.owner = buffer.readUUID();
        this.cellIndex = buffer.readVarInt();
        this.sequence = buffer.readVarInt();
        this.loaded = buffer.readBoolean();

        int size = buffer.readVarInt();
        this.occupants = new ArrayList<>(size);

        for (int i = 0; i < size; i++)
        {
            this.occupants.add(new Occupant(buffer.readUUID(), buffer.readUtf(), buffer.readUtf(), buffer.readFloat(),
                    buffer.readFloat()));
        }
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(owner);
        buffer.writeVarInt(cellIndex);
        buffer.writeVarInt(sequence);
        buffer.writeBoolean(loaded);
        buffer.writeVarInt(occupants.size());

        for (Occupant occupant : occupants)
        {
            buffer.writeUUID(occupant.id());
            buffer.writeUtf(occupant.type());
            buffer.writeUtf(occupant.name());
            buffer.writeFloat(occupant.health());
            buffer.writeFloat(occupant.maxHealth());
        }
    }

    public static void handle(S2CStomachStatusPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        StomachClientState.accept(packet);
        context.setPacketHandled(true);
    }

    public UUID owner()
    {
        return owner;
    }

    public int cellIndex()
    {
        return cellIndex;
    }

    public int sequence()
    {
        return sequence;
    }

    public boolean loaded()
    {
        return loaded;
    }

    public List<Occupant> occupants()
    {
        return occupants;
    }

    /** One guest of a cell, as small as the panel needs it. */
    public record Occupant(UUID id, String type, String name, float health, float maxHealth)
    {
        /** @return the entity type of that guest if this client knows it. */
        public EntityType<?> entityType()
        {
            return ForgeRegistries.ENTITY_TYPES.getValue(net.minecraft.resources.ResourceLocation.tryParse(type));
        }
    }
}
