package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.brood.QueenCommandRay;

import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The press/release timing of the queen sceptre (Queen Staff input timing round).
 *
 * <p>The sceptre used to fire the moment the right button went down, which made a click and a hold impossible to tell
 * apart. From here on the button is only <b>measured</b>:
 *
 * <ol>
 * <li>the press records the tick it happened on, and executes nothing at all,</li>
 * <li>every tick of the press is counted; at {@value #HOLD_THRESHOLD_TICKS} ticks the press becomes <b>armed</b> - a
 * state and nothing else, still no ability,</li>
 * <li>the release adds up the ticks and classifies once: shorter than the threshold is a click, the threshold or more
 * is a hold. The ability runs there, on the release, and never before.</li>
 * </ol>
 *
 * <p>Two things deliberately do not change. A press that really travelled is not a command at all but the drag selection
 * of {@link BroodSelectionBoxClient}: that one is asked first, and only when it says "not a drag" is the press classified.
 * And the click keeps working while the queen sneaks - since the Command Wheel round the rally is a command of the wheel
 * and no longer the sneaking click, so a sneaking click is simply the ordinary click, and the server refuses it outright
 * (Shift is what opens the wheel).
 *
 * <p>This class only classifies. It never touches the world, never looks at a target and never decides what a command
 * is - it calls {@link QueenStaffAbility} and forgets the press.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenStaffInputHandler
{
    /**
     * How long the right button has to be held before the release counts as a hold.
     *
     * <p>Eight ticks, about four hundred milliseconds. It is a constant with a name and not a literal in a comparison,
     * so balancing it later is one edit in one place.
     */
    public static final int HOLD_THRESHOLD_TICKS = 8;

    /** Development logging of the classification. Turn it off when the timing is settled. */
    private static final boolean DEBUG_INPUT = true;

    /** The label of a press that was long enough and is now waiting for its release. */
    private static final String ACTION_ARMED = "HOLD_ARMED";

    private static boolean pressed;
    private static boolean holdArmed;
    private static int pressTick;
    @Nullable
    private static InteractionHand pressHand;

    /**
     * Where the crosshair pointed the moment the right button went down (Spider Trigger round).
     *
     * <p>It is measured here because a click that fires a mine has to have started inside that mine: the release is
     * measured by the server, which casts the command ray itself, but the press happened long before any request left
     * this client, so it is the one point that has to travel with the request.
     */
    @Nullable
    private static Vec3 pressPoint;

    private QueenStaffInputHandler()
    {
    }

    /** The right button: the press starts the measurement, the release classifies it. */
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event)
    {
        if (event.getButton() != InputConstants.MOUSE_BUTTON_RIGHT)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null)
        {
            return;
        }

        if (event.getAction() == InputConstants.PRESS)
        {
            InteractionHand hand = scepterHand(player);

            // Not a press of the sceptre: nothing is measured and nothing will be classified.
            if (hand == null)
            {
                clear();

                return;
            }

            pressed = true;
            holdArmed = false;
            pressTick = player.tickCount;
            pressHand = hand;

            // And where she was pointing at that very moment, which is half of what a mine command means.
            pressPoint = QueenCommandRay.cursorPoint(player);

            return;
        }

        if (event.getAction() == InputConstants.RELEASE && pressed)
        {
            release(player, minecraft.level);
        }
    }

    /** The press is counted down tick by tick, and armed as soon as it is long enough - still without an ability. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !pressed)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // The sceptre left the hand, a screen opened or the world is gone: the press is dropped without a verdict.
        if (player == null || minecraft.level == null || minecraft.screen != null || scepterHand(player) == null)
        {
            clear();

            return;
        }

        int duration = player.tickCount - pressTick;

        if (!holdArmed && duration >= HOLD_THRESHOLD_TICKS)
        {
            // Armed, and that is all: a long press executes nothing until the button comes back up.
            holdArmed = true;
            log(duration, ACTION_ARMED);
        }
    }

    /** @return true while a long press is armed and waiting for its release, for a HUD or a test to read. */
    public static boolean isHoldArmed()
    {
        return pressed && holdArmed;
    }

    /** Adds up the press and says once what it was. */
    private static void release(LocalPlayer player, ClientLevel level)
    {
        InteractionHand hand = pressHand;
        Vec3 point = pressPoint;
        int duration = player.tickCount - pressTick;
        clear();

        if (hand == null)
        {
            return;
        }

        // The drag selection of the sceptre goes first: a press that really travelled is a box, not a command.
        if (BroodSelectionBoxClient.finishDrag(player, level))
        {
            log(duration, "SELECT_BOX");

            return;
        }

        // Sneaking changed nothing about a click any more since the Command Wheel round: the rally it used to cast is one
        // of the commands of the wheel. A sneaking click therefore runs the ordinary path below, and the server refuses
        // it anyway (see QueenScepterCommands#use) - so Shift and the sceptre never order anything by accident.
        if (duration >= HOLD_THRESHOLD_TICKS)
        {
            QueenStaffAbility.onHoldRelease(hand);
            log(duration, "HOLD");

            return;
        }

        // Whatever the click was - a selection of one own spider, an attack order or a move order - the visuals of it are
        // the answer of the server: the outline of a selection and the command marker both arrive as packets, so the input
        // side has nothing to decide about them any more (Brood Command Group round).
        QueenStaffAbility.onClick(hand, point);
        log(duration, "CLICK");
    }

    /** Forgets the press. Called by the release, by a cancelled press and by nothing else. */
    private static void clear()
    {
        pressed = false;
        holdArmed = false;
        pressTick = 0;
        pressHand = null;
        pressPoint = null;
    }

    /** @return the hand of that player that holds the sceptre, or null when neither of them does. */
    @Nullable
    private static InteractionHand scepterHand(LocalPlayer player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = player.getItemInHand(hand);

            if (held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return hand;
            }
        }

        return null;
    }

    /** One line per judged press, so the timing can be watched while it is being balanced. */
    private static void log(int duration, String action)
    {
        if (DEBUG_INPUT)
        {
            SpiderQueenHungryCraft.LOGGER.info("QueenStaff input: duration = {} ticks, action = {}", duration, action);
        }
    }
}
