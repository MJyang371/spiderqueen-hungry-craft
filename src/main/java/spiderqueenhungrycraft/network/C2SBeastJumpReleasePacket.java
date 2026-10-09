package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastJump;

/**
 * Client to server request: the jump key of a beast was released (Phase 8C). The server measures how long it
 * was held and launches the jump itself.
 */
public class C2SBeastJumpReleasePacket
{
    public C2SBeastJumpReleasePacket()
    {
    }

    public C2SBeastJumpReleasePacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBeastJumpReleasePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastJump.release(sender);
        }

        context.setPacketHandled(true);
    }
}
