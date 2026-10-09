package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.QueenBeastChargeClient;

/**
 * Server to client state of the charge key of the local beast (Phase 8D).
 *
 * <p>The rumble and the lens widening of the charge are a client presentation, but they may only run while the
 * server really runs a charge, so the server tells the client when one starts and when it ends. The client
 * never decides a charge, a block break, a food cost or an impact from this.
 */
public class S2CBeastChargeStatePacket
{
    private final boolean active;

    public S2CBeastChargeStatePacket(boolean active)
    {
        this.active = active;
    }

    public S2CBeastChargeStatePacket(FriendlyByteBuf buffer)
    {
        this.active = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(this.active);
    }

    public static void handle(S2CBeastChargeStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastChargeClient.acceptState(packet.active);
        }

        context.setPacketHandled(true);
    }
}
