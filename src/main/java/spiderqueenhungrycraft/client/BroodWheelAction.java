package spiderqueenhungrycraft.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import spiderqueenhungrycraft.network.C2SBroodSelectionPacket;

/**
 * One command of the brood on the command wheel of the sceptre (Command Wheel round).
 *
 * <p>The wheel is the entrance of the brood commands that is always there: the queen holds Shift, the ring appears, she
 * moves the mouse to a command and lets Shift go to cast it. Every command of the ring is also a key binding - one that
 * has no key until the player gives it one - and the ring prints that key under the name of the command, so the wheel
 * doubles as the list of her own shortcuts.
 *
 * <p>The order of these constants <b>is</b> the order on the ring, clockwise from the top. A command here holds no logic
 * of its own: it names the translation key, it names the binding to show, and {@link #cast()} sends exactly the packet
 * the shortcut of that command sends - no second command path exists for the wheel.
 */
public enum BroodWheelAction
{
    /**
     * The two selection commands come first, side by side, because they are the same kind of command: they replace the
     * group the queen is working with.
     */
    SELECT_VISIBLE("select_visible", HungryCraftKeyMappings.SELECT_VISIBLE, Group.SELECTION),

    /** Every spider of the species the queen picked joins the selection. */
    SELECT_SAME_TYPES("select_same", HungryCraftKeyMappings.SELECT_SAME_TYPES, Group.SELECTION),

    /** The soft rally: the selected spiders march along on foot, nobody is teleported. */
    FOLLOW_SELECTED("follow_selected", HungryCraftKeyMappings.FOLLOW_SELECTED, Group.ORDER),

    /** Step the selected spiders through the order cycle follow, hold, patrol, free attack. */
    CYCLE_ORDER("cycle_order", HungryCraftKeyMappings.CYCLE_ORDER, Group.ORDER),

    /**
     * Switch the hunt of the selected spiders on: they attack everything around them that is not of their own side.
     *
     * <p>It stands next to the order cycle on purpose, because it is the same kind of thing - an order a group is given -
     * and it is deliberately <b>not</b> part of that cycle: the cycle walks the four orders and leaves the hunt with the
     * next one, while this command sets the hunt and touches nothing else.
     */
    FREE_ATTACK("free_attack", HungryCraftKeyMappings.FREE_ATTACK, Group.ORDER),

    /** The selected spiders hold where they stand. */
    HOLD_SELECTED("hold_selected", HungryCraftKeyMappings.HOLD_SELECTED, Group.ORDER),

    /** The ability of every selected spider that has one. */
    BROOD_ABILITY("brood_ability", HungryCraftKeyMappings.BROOD_ABILITY, Group.ORDER),

    /**
     * The hard rally: the whole brood comes home, teleport included. It stands at the end of the ring, alone in its own
     * group, because it is the one command that ignores the selection and means everybody.
     */
    RALLY_HOME("rally_home", HungryCraftKeyMappings.RALLY_HOME, Group.SPECIAL);

    /**
     * The three kinds of command of the ring, each with its own colour on it (Command Wheel round, second pass).
     *
     * <p>The colours are what makes the ring readable at a glance: the two commands that <b>pick</b> a group are one
     * colour, the commands that <b>act on</b> the group that is already picked are another, and the one command that
     * means everybody has a colour of its own. Nothing about a command depends on its group - a group is a colour and a
     * word in the middle of the ring, nothing else.
     */
    public enum Group
    {
        /** Everything that replaces the selection: the view selection and the species selection. */
        SELECTION("selection", 0xB01B2E4A, 0xFF7FC8FF),

        /** Everything that acts on the group the queen has already picked. */
        ORDER("order", 0xB01B3A24, 0xFF8FE39B),

        /** The hard rally, and nothing else: it means everybody. */
        SPECIAL("special", 0xB03A2A0A, 0xFFE9C46A);

        private final String id;

        /** The fill of one sector of this group. */
        private final int fill;

        /** The border of a sector of this group, and the wash over the sector the pointer is on. */
        private final int accent;

        Group(String id, int fill, int accent)
        {
            this.id = id;
            this.fill = fill;
            this.accent = accent;
        }

        /** @return the word the middle of the ring shows for a command of this group. */
        public Component label()
        {
            return Component.translatable("wheel.spiderqueen_hungrycraft.group." + this.id);
        }

        /** @return the colour a sector of this group is filled with. */
        public int fillColor()
        {
            return this.fill;
        }

        /** @return the colour of the rim of a sector of this group, and of the wash on the picked one. */
        public int accentColor()
        {
            return this.accent;
        }
    }

    private final String id;
    private final KeyMapping binding;
    private final Group group;

    BroodWheelAction(String id, KeyMapping binding, Group group)
    {
        this.id = id;
        this.binding = binding;
        this.group = group;
    }

    /** @return the kind of command this is, which is also its colour on the ring. */
    public Group group()
    {
        return this.group;
    }

    /** @return the short name of this command on the ring. */
    public Component label()
    {
        return Component.translatable("wheel.spiderqueen_hungrycraft." + this.id);
    }

    /**
     * @return the key this command answers to right now, as the game writes it in the controls screen ("J", "Mouse
     *         Button 4", or "Not bound" while the queen has given it no key).
     */
    public Component shortcut()
    {
        return this.binding.getTranslatedKeyMessage();
    }

    /** Casts this command: one request, the very one its own shortcut sends. */
    public void cast()
    {
        switch (this)
        {
            case SELECT_VISIBLE -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.SELECT_VISIBLE, BroodSelectionInputClient.visibleCandidates());
            case RALLY_HOME -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.RALLY, NO_CANDIDATES);
            case FOLLOW_SELECTED -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.FOLLOW_SELECTED, NO_CANDIDATES);
            case CYCLE_ORDER -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.CYCLE_ORDER, NO_CANDIDATES);
            case FREE_ATTACK -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.FREE_ATTACK, NO_CANDIDATES);
            case HOLD_SELECTED -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.HOLD_SELECTED, NO_CANDIDATES);
            case SELECT_SAME_TYPES -> BroodSelectionInputClient.sendSelection(
                    C2SBroodSelectionPacket.Action.SELECT_SAME_TYPES, NO_CANDIDATES);
            case BROOD_ABILITY -> BroodAbilityInputClient.sendCast();
        }
    }

    private static final int[] NO_CANDIDATES = new int[0];
}
