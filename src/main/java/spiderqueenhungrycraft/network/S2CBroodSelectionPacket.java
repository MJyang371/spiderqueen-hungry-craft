package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client echo of the selection of a queen (Brood Command Group round).
 *
 * <p>The selection itself lives on the server (it is what her commands act on), but the queen has to <b>see</b> which
 * units she picked or she can not command them: the outline of a selected spider is drawn by her own client, so the
 * server tells that one client what its selection is. The list travels to the queen herself and to nobody else, which
 * is also why it is an entity id list and not a flag on the spider.
 *
 * <p>It is an answer and never a request: the client only draws what it is told, and an empty list means "nothing is
 * selected any more".
 */
public class S2CBroodSelectionPacket
{
    /** The same cap the request side uses: a selection is small, and a packet is never trusted blindly. */
    private static final int MAX_IDS = 512;

    private final int[] selectedIds;

    public S2CBroodSelectionPacket(int[] selectedIds)
    {
        this.selectedIds = selectedIds;
    }

    public S2CBroodSelectionPacket(FriendlyByteBuf buffer)
    {
        int count = Math.min(buffer.readVarInt(), MAX_IDS);
        this.selectedIds = new int[count];

        for (int index = 0; index < count; index++)
        {
            this.selectedIds[index] = buffer.readVarInt();
        }
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.selectedIds.length);

        for (int id : this.selectedIds)
        {
            buffer.writeVarInt(id);
        }
    }

    public int[] selectedIds()
    {
        return this.selectedIds;
    }

    public static void handle(S2CBroodSelectionPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client draws the outline of a selection, so the handler stays a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.BroodSelectionBoxClient.onSelectionEcho(packet.selectedIds());
        }

        context.setPacketHandled(true);
    }
}
