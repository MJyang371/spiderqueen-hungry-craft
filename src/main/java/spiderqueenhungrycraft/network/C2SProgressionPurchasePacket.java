package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.progression.ProgressionFeedback;
import spiderqueenhungrycraft.progression.ProgressionService;
import spiderqueenhungrycraft.progression.SoulShopService;

/**
 * Client to server request to buy one product of the soul shop (Progression Foundation round).
 *
 * <p>It names the product and the core, and nothing else - not the price and not the contents, both of which come
 * from the products the server holds. A client that asks for a product that a reload took away, or that names a price
 * of its own, changes nothing: the server looks the product up again, checks that it is still for sale and checks the
 * price of its own list.
 */
public class C2SProgressionPurchasePacket
{
    private final BlockPos corePos;
    private final ResourceLocation entryId;

    public C2SProgressionPurchasePacket(BlockPos corePos, ResourceLocation entryId)
    {
        this.corePos = corePos;
        this.entryId = entryId;
    }

    public C2SProgressionPurchasePacket(FriendlyByteBuf buffer)
    {
        this.corePos = buffer.readBlockPos();
        this.entryId = buffer.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(this.corePos);
        buffer.writeResourceLocation(this.entryId);
    }

    public static void handle(C2SProgressionPurchasePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            ProgressionFeedback feedback = SoulShopService.purchase(sender, packet.corePos, packet.entryId);

            HungryCraftNetwork.sendToPlayer(new S2CProgressionFeedbackPacket(feedback), sender);

            if (feedback == ProgressionFeedback.PURCHASE_OK)
            {
                ProgressionService.sendSnapshot(sender, packet.corePos);
            }
        }

        context.setPacketHandled(true);
    }
}
