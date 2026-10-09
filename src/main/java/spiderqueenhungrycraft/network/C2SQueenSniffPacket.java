package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.ability.QueenSniff;

/**
 * Client to server request to use the sniff of the queen (Spider Trigger round follow-up).
 *
 * <p>It carries no payload on purpose, exactly like the wall crawl toggle: the server decides from the real
 * {@link ServerPlayer} whether that player is a queen at all, whether she can pay the hunger it costs and whether her
 * cooldown is over. A modified client can therefore ask for "I pressed the key", and nothing more.
 */
public class C2SQueenSniffPacket
{
    public C2SQueenSniffPacket()
    {
    }

    public C2SQueenSniffPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SQueenSniffPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenSniff.cast(sender);
        }

        context.setPacketHandled(true);
    }
}
