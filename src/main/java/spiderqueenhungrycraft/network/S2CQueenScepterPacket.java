package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client confirmation of a queen sceptre command (Queen Scepter command patch).
 *
 * <p>The client never assumes that a click worked: it shows a command marker or an attack confirmation only when this
 * packet says that the server really gave the order. Everything about it is therefore an answer, never a request.
 */
public class S2CQueenScepterPacket
{
    /** What the server really did. */
    public enum Kind
    {
        /** Nothing happened: the ray found nothing, the target was refused or the queen has no spider loaded. */
        NONE,
        /** One spider of the queen changed its order. */
        MODE_CHANGED,
        /** The brood was sent at a victim: the client shows the attack confirmation. */
        ATTACK_ACCEPTED,
        /** The brood was sent to a spot: the client shows the command marker of this command. */
        MOVE_ACCEPTED,
        /** Somebody of that move command arrived: the marker of that command disappears. */
        MOVE_ARRIVED,
        /** The rally was answered: marker and attack confirmation are cleared. */
        RALLY_ACCEPTED
    }

    private final Kind kind;
    private final int commandId;
    private final BlockPos markerPos;
    private final int targetEntityId;

    private S2CQueenScepterPacket(Kind kind, int commandId, BlockPos markerPos, int targetEntityId)
    {
        this.kind = kind;
        this.commandId = commandId;
        this.markerPos = markerPos;
        this.targetEntityId = targetEntityId;
    }

    public static S2CQueenScepterPacket none()
    {
        return new S2CQueenScepterPacket(Kind.NONE, 0, BlockPos.ZERO, 0);
    }

    public static S2CQueenScepterPacket modeChanged()
    {
        return new S2CQueenScepterPacket(Kind.MODE_CHANGED, 0, BlockPos.ZERO, 0);
    }

    public static S2CQueenScepterPacket attackAccepted(int targetEntityId)
    {
        return new S2CQueenScepterPacket(Kind.ATTACK_ACCEPTED, 0, BlockPos.ZERO, targetEntityId);
    }

    public static S2CQueenScepterPacket moveAccepted(int commandId, BlockPos markerPos)
    {
        return new S2CQueenScepterPacket(Kind.MOVE_ACCEPTED, commandId, markerPos, 0);
    }

    public static S2CQueenScepterPacket arrived(int commandId)
    {
        return new S2CQueenScepterPacket(Kind.MOVE_ARRIVED, commandId, BlockPos.ZERO, 0);
    }

    public static S2CQueenScepterPacket rallyAccepted()
    {
        return new S2CQueenScepterPacket(Kind.RALLY_ACCEPTED, 0, BlockPos.ZERO, 0);
    }

    public S2CQueenScepterPacket(FriendlyByteBuf buffer)
    {
        this.kind = Kind.values()[buffer.readVarInt() % Kind.values().length];
        this.commandId = buffer.readVarInt();
        this.markerPos = buffer.readBlockPos();
        this.targetEntityId = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.kind.ordinal());
        buffer.writeVarInt(this.commandId);
        buffer.writeBlockPos(this.markerPos);
        buffer.writeVarInt(this.targetEntityId);
    }

    public Kind kind()
    {
        return this.kind;
    }

    public int commandId()
    {
        return this.commandId;
    }

    public BlockPos markerPos()
    {
        return this.markerPos;
    }

    public int targetEntityId()
    {
        return this.targetEntityId;
    }

    public static void handle(S2CQueenScepterPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client draws a marker or a confirmation, so the handler stays a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.QueenScepterVisuals.onFeedback(packet);
        }

        context.setPacketHandled(true);
    }
}
