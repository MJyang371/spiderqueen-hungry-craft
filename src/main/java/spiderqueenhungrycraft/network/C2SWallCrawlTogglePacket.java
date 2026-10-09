package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.movement.SimpleWallClimbController;

/**
 * Client to server request to switch the wall climb mode on or off. It carries no payload on purpose: the
 * server decides from the real {@link ServerPlayer} whether that is allowed.
 */
public class C2SWallCrawlTogglePacket
{
    public C2SWallCrawlTogglePacket()
    {
    }

    public C2SWallCrawlTogglePacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SWallCrawlTogglePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            SimpleWallClimbController.toggle(sender);
        }

        context.setPacketHandled(true);
    }
}
