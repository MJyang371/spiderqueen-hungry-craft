package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.bed.SpiderBedHibernation;

/**
 * The leave bed button of the hibernation screen (Phase 8E3 patch).
 *
 * <p>It carries no payload on purpose: the client only says "I want to get up". Whether a session is running
 * at all, and what happens to the clock afterwards, is decided by the server from the real player.
 */
public class C2SSpiderBedLeaveHibernationPacket
{
    public C2SSpiderBedLeaveHibernationPacket()
    {
    }

    public C2SSpiderBedLeaveHibernationPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SSpiderBedLeaveHibernationPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            SpiderBedHibernation.leave(sender);
        }

        context.setPacketHandled(true);
    }
}
