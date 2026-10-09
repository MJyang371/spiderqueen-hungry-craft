package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.brood.QueenScepterCommands;

/**
 * Client to server request to use the queen sceptre (Queen Scepter command patch).
 *
 * <p>It carries the hand that was used and whether the queen was sneaking, and nothing else: which creature or which
 * block she pointed at is not sent at all, because the server casts the command ray itself from the eyes and the view
 * of the real {@link ServerPlayer}. A modified client can therefore ask for "I used the sceptre", but it can never
 * choose a target the queen could not have chosen.
 */
public class C2SQueenScepterUsePacket
{
    /**
     * How the sceptre was used, decided by the client from the press and release ticks (Queen Staff input timing
     * round).
     *
     * <p>It is the <b>only</b> thing this packet says about the gesture: a duration never travels, only its verdict.
     * The client measures; the server executes.
     */
    public enum Action
    {
        /** A press shorter than the hold threshold: the ordinary command pipeline of the sceptre. */
        CLICK,

        /**
         * A press of at least the hold threshold, released.
         *
         * <p>Reserved: no ability is bound to a hold yet, so the server acknowledges it and does nothing. The moment an
         * ability is bound, it is added on the server side alone - the protocol already carries the case.
         */
        HOLD
    }

    private final int hand;
    private final boolean sneaking;
    private final Action action;

    /**
     * Where the crosshair of the queen pointed the moment the right button went <b>down</b> (Spider Trigger round).
     *
     * <p>It is the one thing about a click that only a client can know - the server samples nothing per tick - and it
     * travels for exactly one reason: the command that fires a mine only counts when the cursor was <b>inside</b> that
     * mine when the button went down as well as when it came back up. The server checks the point against the reach of
     * the sceptre and treats a nonsense one as if the queen had never moved the mouse.
     *
     * <p>Null only for a request that was built without one, which means "pressed where released".
     */
    @Nullable
    private final Vec3 pressPoint;

    public C2SQueenScepterUsePacket(InteractionHand hand, boolean sneaking, Action action, @Nullable Vec3 pressPoint)
    {
        this.hand = hand.ordinal();
        this.sneaking = sneaking;
        this.action = action;
        this.pressPoint = pressPoint;
    }

    public C2SQueenScepterUsePacket(FriendlyByteBuf buffer)
    {
        this.hand = buffer.readVarInt();
        this.sneaking = buffer.readBoolean();
        this.action = Action.values()[Math.abs(buffer.readVarInt()) % Action.values().length];
        this.pressPoint = buffer.readBoolean() ? new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                : null;
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.hand);
        buffer.writeBoolean(this.sneaking);
        buffer.writeVarInt(this.action.ordinal());
        buffer.writeBoolean(this.pressPoint != null);

        if (this.pressPoint != null)
        {
            buffer.writeDouble(this.pressPoint.x);
            buffer.writeDouble(this.pressPoint.y);
            buffer.writeDouble(this.pressPoint.z);
        }
    }

    /** @return how the sceptre was used: the classification of the press, and nothing else. */
    public Action action()
    {
        return this.action;
    }

    /** @return where the crosshair pointed when the button went down, or null when the request does not say. */
    @Nullable
    public Vec3 pressPoint()
    {
        return this.pressPoint;
    }

    public static void handle(C2SQueenScepterUsePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            QueenScepterCommands.use(sender, InteractionHand.values()[packet.hand % InteractionHand.values().length],
                    packet.sneaking, packet.action, packet.pressPoint);
        }

        context.setPacketHandled(true);
    }
}
