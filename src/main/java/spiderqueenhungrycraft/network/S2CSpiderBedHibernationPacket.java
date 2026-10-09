package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * The hibernation of a Spider Queen, as the client has to see it (Phase 8E3 patch).
 *
 * <p>It only ever says "a hibernation started, it takes this many ticks" or "it is over". Everything about
 * the rest itself - the freeze, the clock and the rewards - stays on the server, the client only draws the
 * screen and the fade and sends the leave request back.
 */
public class S2CSpiderBedHibernationPacket
{
    private final boolean active;
    private final int durationTicks;

    public S2CSpiderBedHibernationPacket(boolean active, int durationTicks)
    {
        this.active = active;
        this.durationTicks = durationTicks;
    }

    public S2CSpiderBedHibernationPacket(FriendlyByteBuf buffer)
    {
        this.active = buffer.readBoolean();
        this.durationTicks = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(this.active);
        buffer.writeVarInt(this.durationTicks);
    }

    /** @return true when a hibernation runs on the client side now. */
    public boolean isActive()
    {
        return this.active;
    }

    /** How long the hibernation lasts, in ticks. Only meaningful while {@link #isActive()} is true. */
    public int durationTicks()
    {
        return this.durationTicks;
    }

    public static void handle(S2CSpiderBedHibernationPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // The handler is enqueued on the main thread by the channel. The client class is only touched on a
        // client, so registering this packet on a dedicated server stays safe.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.SpiderBedHibernationClientState.acceptServerState(packet.active,
                    packet.durationTicks);
        }

        context.setPacketHandled(true);
    }
}
