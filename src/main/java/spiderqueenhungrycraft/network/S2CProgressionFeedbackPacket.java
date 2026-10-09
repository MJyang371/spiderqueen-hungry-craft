package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.progression.ProgressionFeedback;

/**
 * The verdict of the server on one request of the growth interface (Progression Foundation round).
 *
 * <p>It is a code and not a sentence: the server says what happened, the client says it in the language of the player,
 * and the one message the task names by hand ({@code message.spiderqueen_hungrycraft.insufficient_souls}) is a key of
 * the language file like every other line of this mod.
 *
 * <p>Nothing is logged for a refusal: a player who asks for something she can not have yet is an ordinary event, and
 * a command or a log line per click would be noise.
 */
public class S2CProgressionFeedbackPacket
{
    private final ProgressionFeedback feedback;

    public S2CProgressionFeedbackPacket(ProgressionFeedback feedback)
    {
        this.feedback = feedback;
    }

    public S2CProgressionFeedbackPacket(FriendlyByteBuf buffer)
    {
        this.feedback = buffer.readEnum(ProgressionFeedback.class);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeEnum(this.feedback);
    }

    public ProgressionFeedback feedback()
    {
        return this.feedback;
    }

    public static void handle(S2CProgressionFeedbackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has a screen to show it in, so the handler stays a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.ProgressionClientState.acceptFeedback(packet.feedback);
        }

        context.setPacketHandled(true);
    }
}
