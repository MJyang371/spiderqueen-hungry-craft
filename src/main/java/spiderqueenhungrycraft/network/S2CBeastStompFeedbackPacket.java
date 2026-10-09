package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.QueenBeastStompClient;

/**
 * Server to client feedback of one real stomp landing (Phase 8C): which beast landed, so the attacker feels the
 * heavy impulse of the landing and nobody else does.
 *
 * <p>Only the server ever sends this, and only after a charged jump really armed and really landed, so a hop
 * over a single block never shakes anybody's screen.
 */
public class S2CBeastStompFeedbackPacket
{
    private final int entityId;

    public S2CBeastStompFeedbackPacket(int entityId)
    {
        this.entityId = entityId;
    }

    public S2CBeastStompFeedbackPacket(FriendlyByteBuf buffer)
    {
        this.entityId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.entityId);
    }

    public static void handle(S2CBeastStompFeedbackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastStompClient.onStompFeedback(packet.entityId);
        }

        context.setPacketHandled(true);
    }
}
