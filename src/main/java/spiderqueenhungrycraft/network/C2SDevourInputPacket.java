package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.devour.DevourInput;

/**
 * Client to server: one edge of the devour key (Phase 8E2A).
 *
 * <p>It carries an action and nothing else - no target, no area, no flag for "beast", no long or short press.
 * The server decides everything from the real player: the hold threshold is measured in server ticks, the
 * nature of the player decides whether a devour is possible at all, and the client can therefore never ask for
 * something it is not allowed to do.
 *
 * <p>It is deliberately not a "held" message: exactly two of these are sent per physical key press, so a player
 * who holds the key does not produce one packet per tick.
 */
public class C2SDevourInputPacket
{
    /** The two edges of a physical key. */
    public enum Action
    {
        PRESS,
        RELEASE
    }

    private final Action action;

    public C2SDevourInputPacket(Action action)
    {
        this.action = action;
    }

    public C2SDevourInputPacket(FriendlyByteBuf buffer)
    {
        this.action = buffer.readEnum(Action.class);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeEnum(this.action);
    }

    public static void handle(C2SDevourInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            if (packet.action == Action.PRESS)
            {
                DevourInput.onPress(sender);
            }
            else
            {
                DevourInput.onRelease(sender);
            }
        }

        context.setPacketHandled(true);
    }
}
