package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.TerritoryCoreRangeClient;

/**
 * Asks one client to draw the protected range of a territory core for five seconds (Phase LAIR-CORE-1).
 *
 * <p>It is sent to the player who clicked the core and to nobody else, it is never stored and it is never repeated:
 * the client keeps the box in a plain cache with an expiry tick, so a second click simply restarts the timer.
 */
public class S2CTerritoryRangePacket
{
    private final BlockPos corePos;

    public S2CTerritoryRangePacket(BlockPos corePos)
    {
        this.corePos = corePos;
    }

    public S2CTerritoryRangePacket(FriendlyByteBuf buffer)
    {
        this.corePos = buffer.readBlockPos();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(this.corePos);
    }

    public static void handle(S2CTerritoryRangePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // The cache class is client only, so the dist check keeps this packet safe on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            TerritoryCoreRangeClient.show(packet.corePos);
        }

        context.setPacketHandled(true);
    }
}
