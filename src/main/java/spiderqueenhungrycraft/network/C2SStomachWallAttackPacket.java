package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.stomach.StomachWallAttack;

/**
 * Client to server request to hit the stomach wall (Phase 8E1-GATE2A).
 *
 * <p>It carries no payload on purpose: the position, the rotation, the cell and the attack cooldown are all read
 * from the real {@link ServerPlayer} on the server, and the server alone decides whether the swing was a hit on
 * the wall of the cell.
 */
public class C2SStomachWallAttackPacket
{
    public C2SStomachWallAttackPacket()
    {
    }

    public C2SStomachWallAttackPacket(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public void encode(FriendlyByteBuf buffer)
    {
        // no payload
    }

    public static void handle(C2SStomachWallAttackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            StomachWallAttack.handle(sender);
        }

        context.setPacketHandled(true);
    }
}
