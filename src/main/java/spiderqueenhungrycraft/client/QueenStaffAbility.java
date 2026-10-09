package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;


import net.minecraft.world.InteractionHand;
import spiderqueenhungrycraft.network.C2SQueenScepterUsePacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;

/**
 * What the queen sceptre does with a click and with a hold (Queen Staff input timing round).
 *
 * <p>This class is the whole skill side of the sceptre, and it is deliberately the only place that knows what a press
 * <b>means</b>. {@link QueenStaffInputHandler} decides how long the button was down and calls one of the two methods
 * below; neither of them ever looks at a tick count, and the input handler never learns what a command is. One day a
 * third gesture can be added without touching the timing, and a second ability can be added without touching the
 * mouse.
 *
 * <p>Both methods only ever <b>ask the server</b>. Which creature or which block the queen points at is not sent and
 * not known here - the server casts the command ray of the sceptre itself, from the eyes of the real player - so
 * nothing this client says can make the world change.
 */
public final class QueenStaffAbility
{
    private QueenStaffAbility()
    {
    }

    /** A short press: the ordinary command pipeline of the sceptre (select an own spider, order an attack or order a move). */
    public static void onClick(InteractionHand hand, @Nullable Vec3 pressPoint)
    {
        // While the command wheel is open the sceptre is silent: the ring is what the queen is using, and the click that
        // opened the ring must not also select, order or mine something (Command Wheel round).
        if (BroodWheelClient.isOpen())
        {
            return;
        }

        HungryCraftNetwork.sendToServer(
                new C2SQueenScepterUsePacket(hand, false, C2SQueenScepterUsePacket.Action.CLICK, pressPoint));
    }

    /**
     * The release of a long press.
     *
     * <p><b>Empty on purpose.</b> This round builds the input framework only: the press/release timing of the sceptre
     * is measured and classified, the network protocol carries the two cases ({@code CLICK} and {@code HOLD}), and the
     * server has a place where the hold ability will live - but no ability is bound to a hold yet, so a hold has no
     * effect at all today.
     *
     * <p>When one is bound, it is sent from here and only from here:
     * {@code new C2SQueenScepterUsePacket(hand, false, Action.HOLD)}. Nothing else about the input has to change for
     * that, and nothing about the input has to know what it will be.
     */
    public static void onHoldRelease(InteractionHand hand)
    {
        // Reserved for the hold ability of the sceptre: no ability is bound yet (see the class comment).
    }
}
