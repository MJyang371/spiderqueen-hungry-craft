package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.player.QueenSilk;

/**
 * The silk state of one queen (feature SILK-CAPACITY), sent by the server to the player herself.
 *
 * <p>The amount is gameplay data that only the server ever decides, and the client needs it for one single
 * thing: to draw the web bar of the queen. The queen flag travels with it, so a client that lost the flag
 * really hides the bar instead of keeping the last amount forever.
 */
public class S2CQueenSilkPacket
{
    private final boolean queen;
    private final int amount;

    public S2CQueenSilkPacket(boolean queen, int amount)
    {
        this.queen = queen;
        this.amount = amount;
    }

    public S2CQueenSilkPacket(FriendlyByteBuf buffer)
    {
        this.queen = buffer.readBoolean();
        this.amount = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBoolean(this.queen);
        buffer.writeVarInt(this.amount);
    }

    public static void handle(S2CQueenSilkPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // The handler is enqueued on the main thread by the channel, and the queen silk class is common, so
        // registering this packet on a dedicated server stays safe.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenSilk.acceptClientState(packet.queen, packet.amount);
        }

        context.setPacketHandled(true);
    }
}
