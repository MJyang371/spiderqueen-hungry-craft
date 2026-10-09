package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.compat.iceandfire.NecromancySkill;

/**
 * Client to server request to cast 亡灵契约 IV (Necromancy IV round).
 *
 * <p>It carries no payload on purpose, exactly like the sniff of the queen: the server decides from the real
 * {@link ServerPlayer} whether that player is the Spider Queen, whether she owns the node and what her crosshair really
 * points at - it casts the ray itself. A modified client can therefore ask for "I pressed the key" and nothing more,
 * and it can not name a target, a coordinate or a dragon of somebody else.
 */
public class C2SNecromancyCastPacket
{
    public C2SNecromancyCastPacket()
    {
    }

    public C2SNecromancyCastPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SNecromancyCastPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            NecromancySkill.cast(sender);
        }

        context.setPacketHandled(true);
    }
}
