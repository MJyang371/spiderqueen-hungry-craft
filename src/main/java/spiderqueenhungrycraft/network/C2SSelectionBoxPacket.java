package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.brood.BroodSelection;

/**
 * Client to server request of one drag selection (Brood RTS Mouse Selection round).
 *
 * <p>It carries <b>two points and nothing else</b>: where the drag started and where it ended, both taken from the
 * command ray of the sceptre on the client. No list of entities, no amount, no ids - which spiders are inside the box
 * is worked out by the server, from the entities it has loaded itself and from the owner of each one. A client can
 * therefore not hand itself a spider it does not own, and it cannot name a spider it cannot even see.
 *
 * <p>One packet per drag, on release. The preview of the drag never leaves the client.
 */
public class C2SSelectionBoxPacket
{
    private final Vec3 start;
    private final Vec3 end;

    public C2SSelectionBoxPacket(Vec3 start, Vec3 end)
    {
        this.start = start;
        this.end = end;
    }

    public C2SSelectionBoxPacket(FriendlyByteBuf buffer)
    {
        this.start = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        this.end = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeDouble(this.start.x);
        buffer.writeDouble(this.start.y);
        buffer.writeDouble(this.start.z);
        buffer.writeDouble(this.end.x);
        buffer.writeDouble(this.end.y);
        buffer.writeDouble(this.end.z);
    }

    public static void handle(C2SSelectionBoxPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            // The selection itself lives with the other selection commands of the brood, never in a class of its own.
            BroodSelection.selectBox(sender, packet.start, packet.end);
        }

        context.setPacketHandled(true);
    }
}
