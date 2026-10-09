package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.brood.SpiderCommand;

/**
 * Client to server request of one of the brood keys (Brood Selection + Inspect HUD round, reworked by the Brood Command
 * Group round).
 *
 * <p>Only the "select what I can see" key carries anything: the entity ids of the spiders the client <b>believes</b> are
 * in its view. The server never trusts that list as an order - it looks every id up among the spiders that are loaded,
 * owned by that player and in his dimension, and ignores everything else. The other keys carry an action and nothing
 * more, because the server can see the order, the hover and the selection itself.
 *
 * <p>The four selection actions replace the selection of that queen; the stand-down key orders the selected spiders to
 * hold where they stand; the soft rally key makes the selected spiders march with the queen on foot; the order key steps
 * the selected spiders through the order cycle follow, hold, patrol; the hard rally calls the whole brood home; and the
 * deselect clears the group.
 */
public class C2SBroodSelectionPacket
{
    /** What the queen asked for. */
    public enum Action
    {
        SELECT_VISIBLE,
        SELECT_SAME_TYPES,
        HOLD_SELECTED,

        /**
         * The soft rally (Brood Command Group round, open to the wheel since the Command Wheel round): the selected
         * spiders drop what they are doing and march with the queen on foot - no teleport, no selection change.
         *
         * <p>It is the quiet half of a pair. Its loud half is the hard rally ({@link #RALLY}), which calls the whole
         * brood home at once and moves it there: the difference between the two is one teleport, and it is the whole
         * reason both exist.
         */
        FOLLOW_SELECTED,

        /**
         * The hard rally: every loaded spider of the queen comes home, teleport included, and the selection is dropped
         * with it (Command Wheel round).
         *
         * <p>It is the one command of this class that means <b>everybody</b> and not a selected group, which is why it is
         * the one that is not aimed at anything: the wheel casts it and the server calls the whole brood home. It used to
         * be the shift and right click of the sceptre; that click belongs to the wheel now.
         */
        RALLY,

        /**
         * Step the selected spiders through the order cycle: follow, hold, patrol, follow (Brood Command Group round).
         *
         * <p>It is the group version of the order that the sceptre right click used to cycle: since that click became the
         * selection of a unit, the order of the units that are selected is given here, all of them together and from the
         * state of the group (see {@code SpiderCommand#cycleOrder}).
         */
        CYCLE_ORDER,

        /**
         * Switch the hunt of the selected spiders on (free attack round): every one of them attacks everything around it
         * that is not of its own side - a human, a villager, a monster, an animal, a creature of another mod, the spiders
         * of another queen - and it walks its rounds while it hunts.
         *
         * <p>It is a persistent <b>order</b> and not an errand, exactly like the post of the stand-down key: the spiders
         * stay on it until they are told something else. Being told something else is what leaves it - a scout move, an
         * attack order, a rally, a post, a march - while asking for the hunt again is not a new command.
         */
        FREE_ATTACK,

        /**
         * Give the selection up (the left button of the sceptre, Brood Command Group round).
         *
         * <p>It is the one action of this class that asks for nothing about any unit: it clears the group, and the next
         * command needs a group again - a command without a selection is refused and said out loud. The client sends it
         * when the queen swings at a <b>block</b> with the sceptre in her hand - the attempt to mine is the gesture of
         * letting go, whether or not the block ever breaks - and a swing at a creature is an attack and means nothing
         * here.
         */
        DESELECT
    }

    private final Action action;
    private final int[] candidates;

    public C2SBroodSelectionPacket(Action action, int[] candidates)
    {
        this.action = action;
        this.candidates = candidates;
    }

    public C2SBroodSelectionPacket(FriendlyByteBuf buffer)
    {
        this.action = Action.values()[Math.abs(buffer.readVarInt()) % Action.values().length];

        int count = Math.min(buffer.readVarInt(), 512);
        this.candidates = new int[count];

        for (int index = 0; index < count; index++)
        {
            this.candidates[index] = buffer.readVarInt();
        }
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.action.ordinal());
        buffer.writeVarInt(this.candidates.length);

        for (int candidate : this.candidates)
        {
            buffer.writeVarInt(candidate);
        }
    }

    public Action action()
    {
        return this.action;
    }

    public int[] candidates()
    {
        return this.candidates;
    }

    public static void handle(C2SBroodSelectionPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            // The selection itself lives in the command class of the brood, next to the sceptre commands it belongs to.
            spiderqueenhungrycraft.brood.BroodSelection.perform(sender, packet);
        }
        context.setPacketHandled(true);
    }
}
