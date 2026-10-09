package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastBite;

/**
 * Client to server request: the Queen Beast attack key was pressed while the crosshair was not on a block.
 *
 * <p>It carries no payload on purpose: no target, no damage, no area, no venom flag and no hit list. The server
 * decides all of that from the real {@link ServerPlayer}, including the cooldown, so a client can never ask for
 * a cheaper, longer or venomous bite than it is allowed to have.
 */
public class C2SBeastBitePacket
{
    public C2SBeastBitePacket()
    {
    }

    public C2SBeastBitePacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBeastBitePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenBeastBite.tryBite(sender);
        }

        context.setPacketHandled(true);
    }
}
