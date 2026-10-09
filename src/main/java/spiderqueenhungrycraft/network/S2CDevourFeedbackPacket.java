package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.DevourFeedbackClient;

/**
 * Server to client feedback of one successful devour or regurgitation (Phase 8E2A-POLISH-1).
 *
 * <p>Only the server sends this, and only when the action really happened: at least one creature was swallowed,
 * or at least one thing left the stomach. An empty snap or an empty stomach therefore stays completely silent,
 * because there is no packet to react to.
 *
 * <p>The action is carried instead of a pile of flags, so a client can pick the right presentation without
 * ever deciding anything about the gameplay: a beast lunges and shakes like it does when it bites, an ordinary
 * queen swings her arm with a light kick, and both are the very same camera and pose feedback the bite already
 * uses.
 */
public class S2CDevourFeedbackPacket
{
    /** The two flavours of the feedback. */
    public enum Action
    {
        /** A beast swallowed something. */
        DEVOUR,
        /** Something left a stomach, thrown up in front of the queen. */
        REGURGITATE
    }

    private final int actorId;
    private final Action action;

    public S2CDevourFeedbackPacket(int actorId, Action action)
    {
        this.actorId = actorId;
        this.action = action;
    }

    public S2CDevourFeedbackPacket(FriendlyByteBuf buffer)
    {
        this.actorId = buffer.readVarInt();
        this.action = buffer.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.actorId);
        buffer.writeEnum(this.action);
    }

    public static void handle(S2CDevourFeedbackPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has a camera and a renderer, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            DevourFeedbackClient.onFeedback(packet.actorId, packet.action);
        }

        context.setPacketHandled(true);
    }
}
