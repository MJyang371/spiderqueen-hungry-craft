package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.QueenBeastHurtClient;

/**
 * Server to client feedback of one incoming hit on a beast (Phase 8B-POLISH-2).
 *
 * <p>Only the server knows what a hit really took off the health bar, so only the server decides whether the
 * vanilla hurt camera is allowed to shake. The packet carries the result of that judgement and the final
 * damage for the log, and the client does nothing but remember it for the next few ticks.
 *
 * <p>It is sent to the wounded player alone: the hurt camera of a player is about that player's own screen,
 * every other client would ignore it anyway.
 */
public class S2CBeastHurtFeedbackPacket
{
    private final int entityId;
    private final boolean suppressHurtBob;
    private final float finalDamage;

    public S2CBeastHurtFeedbackPacket(int entityId, boolean suppressHurtBob, float finalDamage)
    {
        this.entityId = entityId;
        this.suppressHurtBob = suppressHurtBob;
        this.finalDamage = finalDamage;
    }

    public S2CBeastHurtFeedbackPacket(FriendlyByteBuf buffer)
    {
        this.entityId = buffer.readVarInt();
        this.suppressHurtBob = buffer.readBoolean();
        this.finalDamage = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.entityId);
        buffer.writeBoolean(this.suppressHurtBob);
        buffer.writeFloat(this.finalDamage);
    }

    public static void handle(S2CBeastHurtFeedbackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastHurtClient.onHurtFeedback(packet.entityId, packet.suppressHurtBob, packet.finalDamage);
        }

        context.setPacketHandled(true);
    }
}
