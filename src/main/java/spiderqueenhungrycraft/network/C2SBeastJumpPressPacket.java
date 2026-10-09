package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastJump;

/**
 * Client to server request: the jump key of a beast was pressed (Phase 8C).
 *
 * <p>Only the press itself is sent, never a charge power. How long the key is held, how strong the jump
 * becomes and whether a stomp is armed is decided by the server from its own clock, so a client can never ask
 * for a stronger jump than it really charged.
 */
public class C2SBeastJumpPressPacket
{
    public C2SBeastJumpPressPacket()
    {
    }

    public C2SBeastJumpPressPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBeastJumpPressPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastJump.press(sender);
        }

        context.setPacketHandled(true);
    }
}
