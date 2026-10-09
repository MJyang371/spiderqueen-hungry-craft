package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.QueenBeastClientSync;

/**
 * Server to client form state of one player: whether it is a beast and whether that beast is compressed.
 *
 * <p>It is the only thing that ever changes the client copy of the state, and it is sent to the player itself
 * and to everyone who can currently see it, plus one extra time to every player who starts tracking it. A new
 * player therefore never sees an ordinary skin where the owner already sees a spider.
 */
public class S2CQueenBeastStatePacket
{
    private final int entityId;
    private final boolean transformed;
    private final boolean compressed;

    public S2CQueenBeastStatePacket(int entityId, boolean transformed, boolean compressed)
    {
        this.entityId = entityId;
        this.transformed = transformed;
        this.compressed = compressed;
    }

    public S2CQueenBeastStatePacket(FriendlyByteBuf buffer)
    {
        this.entityId = buffer.readVarInt();
        this.transformed = buffer.readBoolean();
        this.compressed = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.entityId);
        buffer.writeBoolean(this.transformed);
        buffer.writeBoolean(this.compressed);
    }

    public static void handle(S2CQueenBeastStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has an entity to look up, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastClientSync.apply(packet.entityId, packet.transformed, packet.compressed);
        }

        context.setPacketHandled(true);
    }
}
