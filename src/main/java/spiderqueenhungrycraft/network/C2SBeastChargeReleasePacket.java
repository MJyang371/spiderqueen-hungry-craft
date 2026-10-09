package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastCharge;

/**
 * Client to server request: the charge key of a beast was released (Phase 8D). The charge ends immediately and
 * the unlock of a previous hard impact is lifted.
 */
public class C2SBeastChargeReleasePacket
{
    public C2SBeastChargeReleasePacket()
    {
    }

    public C2SBeastChargeReleasePacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBeastChargeReleasePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastCharge.release(sender);
        }

        context.setPacketHandled(true);
    }
}
