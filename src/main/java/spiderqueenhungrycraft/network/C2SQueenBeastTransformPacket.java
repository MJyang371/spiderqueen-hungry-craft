package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastController;

/**
 * Client to server request: the Queen Beast transform key was pressed once.
 *
 * <p>It carries no payload on purpose, above all no tap counter: the client only says "pressed", the server
 * counts the presses in its own window and decides whether that was the third one. Everything else about the
 * transformation, the conditions and the state comes from the real {@link ServerPlayer}.
 */
public class C2SQueenBeastTransformPacket
{
    public C2SQueenBeastTransformPacket()
    {
    }

    public C2SQueenBeastTransformPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SQueenBeastTransformPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastController.onTransformKeyPressed(sender);
        }

        context.setPacketHandled(true);
    }
}
