package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.brood.SpiderAbilityCast;

/**
 * Client to server request to cast the brood ability (Brood Ability framework).
 *
 * <p>It carries nothing at all - not even the hand the sceptre is in. Which spiders answer the key and what the ability
 * of each of them needs is decided on the server, from the real {@link ServerPlayer} and from the server's own cast of
 * the command ray. A modified client can therefore ask "I pressed the ability key", and nothing more.
 */
public class C2SBroodAbilityCastPacket
{
    public C2SBroodAbilityCastPacket()
    {
    }

    public C2SBroodAbilityCastPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SBroodAbilityCastPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            SpiderAbilityCast.cast(sender);
        }

        context.setPacketHandled(true);
    }
}
