package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.attack.EmpoweredAttackHandler;

/**
 * Client to server request to arm the next empowered melee attack. It carries no payload on purpose:
 * the queen state, the deadline and the damage are all decided from the real {@link ServerPlayer}.
 */
public class C2SEmpoweredAttackPacket
{
    public C2SEmpoweredAttackPacket()
    {
    }

    public C2SEmpoweredAttackPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SEmpoweredAttackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            EmpoweredAttackHandler.tryArm(sender);
        }

        context.setPacketHandled(true);
    }
}
