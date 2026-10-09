package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastCharge;

/**
 * Client to server request: the charge key of a beast was pressed (Phase 8D). One press is one request, a held
 * key never repeats it: the charge runs on the server until the matching release arrives.
 */
public class C2SBeastChargePressPacket
{
    public C2SBeastChargePressPacket()
    {
    }

    public C2SBeastChargePressPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBeastChargePressPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastCharge.press(sender);
        }

        context.setPacketHandled(true);
    }
}
