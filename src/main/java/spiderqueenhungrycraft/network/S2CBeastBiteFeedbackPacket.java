package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.QueenBeastBiteClient;

/**
 * Server to client feedback of one accepted beast bite: which beast bit, so every client that can see it can
 * play the head lunge, and the attacker can additionally play its first person impulse.
 *
 * <p>Only the server ever sends this, and only after the cooldown and every other rule were checked, so a bite
 * that was rejected never produces an animation or a shake anywhere.
 */
public class S2CBeastBiteFeedbackPacket
{
    private final int attackerId;

    public S2CBeastBiteFeedbackPacket(int attackerId)
    {
        this.attackerId = attackerId;
    }

    public S2CBeastBiteFeedbackPacket(FriendlyByteBuf buffer)
    {
        this.attackerId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.attackerId);
    }

    public static void handle(S2CBeastBiteFeedbackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has entities and a camera, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastBiteClient.onBiteFeedback(packet.attackerId);
        }

        context.setPacketHandled(true);
    }
}
