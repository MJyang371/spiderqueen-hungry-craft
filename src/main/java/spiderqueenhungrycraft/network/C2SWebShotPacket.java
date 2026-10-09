package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.web.WebShotHandler;

/**
 * Client to server request to fire a web shot. It carries no payload on purpose: position, rotation,
 * hunger and the queen flag are all read from the real {@link ServerPlayer} on the server.
 */
public class C2SWebShotPacket
{
    public C2SWebShotPacket()
    {
    }

    public C2SWebShotPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SWebShotPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            WebShotHandler.tryFireWebShot(sender);
        }

        context.setPacketHandled(true);
    }
}
