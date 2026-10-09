package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * The soul state of one player (Soul Foundation V1), sent by the server to that player alone.
 *
 * <p>The amount is gameplay data only the server ever decides; the client needs it for one single thing: the number
 * in its bottom left corner. It travels as a {@code long}, so a balance of any size survives the wire unchanged.
 */
public class S2CSoulsPacket
{
    private final long souls;

    public S2CSoulsPacket(long souls)
    {
        this.souls = souls;
    }

    public S2CSoulsPacket(FriendlyByteBuf buffer)
    {
        this.souls = buffer.readVarLong();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarLong(this.souls);
    }

    public long souls()
    {
        return this.souls;
    }

    public static void handle(S2CSoulsPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // The handler is enqueued on the main thread by the channel, and the client class is only touched on a
        // client, so registering this packet on a dedicated server stays safe.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.SoulClientState.acceptServerSouls(packet.souls);
        }

        context.setPacketHandled(true);
    }
}
