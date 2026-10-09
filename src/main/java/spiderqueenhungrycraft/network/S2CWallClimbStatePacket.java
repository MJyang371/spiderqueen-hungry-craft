package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.movement.SimpleWallClimbController;

/**
 * The only crawl state that is ever synchronised (Phase 6-RESET): whether the wall climb mode is on.
 *
 * <p>Everything else about the climb is pure vanilla-friendly movement, so there is no attachment, no
 * forward direction, no anchor and no transition to send.
 */
public class S2CWallClimbStatePacket
{
    private final boolean enabled;

    public S2CWallClimbStatePacket(boolean enabled)
    {
        this.enabled = enabled;
    }

    public S2CWallClimbStatePacket(FriendlyByteBuf buffer)
    {
        this.enabled = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(this.enabled);
    }

    public static void handle(S2CWallClimbStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // The handler is enqueued on the main thread by the channel, and the controller class is common, so
        // registering this packet on a dedicated server stays safe.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            SimpleWallClimbController.acceptClientState(packet.enabled);
        }

        context.setPacketHandled(true);
    }
}
