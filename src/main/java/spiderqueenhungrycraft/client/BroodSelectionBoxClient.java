package spiderqueenhungrycraft.client;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSelection;
import spiderqueenhungrycraft.brood.QueenCommandRay;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.network.C2SBroodSelectionPacket;
import spiderqueenhungrycraft.network.C2SSelectionBoxPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The mouse side of the RTS selection of the queen (Brood RTS Mouse Selection round, reworked into command groups by the
 * Brood Command Group round).
 *
 * <p>A quick right click stays exactly what it was: a command of the sceptre, which the pipeline of
 * {@link QueenStaffInputHandler} sends on the release. <b>Holding</b> the button and dragging, on the other hand, is a
 * selection: the drag is followed here, tick by tick, with nothing but the ordinary command ray of the sceptre, and when
 * the mouse is released the two corners of the drag are sent to the server - once.
 *
 * <p>What this class draws is the <b>group the queen commands right now</b>: the green outline on her selected spiders
 * and the box while it is being dragged. The outline is neither a guess nor a permanent wall of boxes: the server owns
 * the selection, so it tells this client what it is ({@code S2CBroodSelectionPacket}), the outline shows up with that
 * answer - ten seconds, see {@link #SELECTION_VISIBLE_TICKS} - and then the drawing fades while the selection itself
 * lives on, because the commands of the sceptre keep acting on it. The box is drawn from the two rays that are being
 * dragged, as before.
 *
 * <p><b>The left button is the deselect.</b> A swing at a block with the sceptre in hand gives the group up - the
 * attempt to mine is the gesture, long before the block would ever break - and a swing at a creature is an attack and is
 * ignored here. It is the way back to "nothing is selected", and it changes nothing about the spiders themselves.
 *
 * <p>Nothing visual is ever sent: the box is this client's own drawing, the outline is the answer to a packet that was
 * sent to this queen alone, and neither of them is written into a spider, a save file or anybody else's screen.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BroodSelectionBoxClient
{
    /** How long the right button has to be held before the release counts as a drag rather than a command. */
    public static final int DRAG_TICKS = 4;

    /**
     * How long the outline of a selection stays on the screen after the last selection gesture: ten seconds (Brood
     * Command Group round). The selection itself is not touched by it - it lives until the queen selects something else
     * or gives it up with the left button - the green boxes are a confirmation of a gesture and not a permanent frame
     * around a group that is commanded anyway.
     */
    public static final int SELECTION_VISIBLE_TICKS = 200;

    /** How far a drag reaches where the ray hit nothing at all, so the sky can be part of a box. */
    private static final double AIR_REACH = 64.0D;

    /** How far two corners of a drag have to be apart before it is a box and not a click. */
    private static final double MIN_DRAG_DISTANCE = 1.5D;

    private static boolean dragging;
    private static boolean dragLongEnough;
    @Nullable
    private static Vec3 start;
    @Nullable
    private static Vec3 end;
    private static int heldTicks;

    /**
     * The selected spiders as entity ids of this client, for the outline and for nothing else.
     *
     * <p>It is what the server last told this queen - or, in the moment between the release of a drag and that answer,
     * what this client itself caught in the box. The two can not really disagree: the server resolves the very same box
     * against the very same entities and answers within the same tick.
     */
    private static final Set<Integer> selected = new LinkedHashSet<>();

    /** The level this selection belongs to, so an id of an old level is never matched against a new level's entity. */
    @Nullable
    private static ClientLevel selectionLevel;

    /** The game time until which the outline of the selection is drawn, see {@link #highlightActive()}. */
    private static long visibleUntil;

    private BroodSelectionBoxClient()
    {
    }

    /** The right mouse button: the press starts a drag, the release ends it. */
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
            // Sneaking is what opens the command wheel and never a drag, and without the sceptre there is no selection.
            if (player.isShiftKeyDown() || !holdsScepter(player))
            {
                return;
            }

            Vec3 point = rayPoint(player);
            dragging = true;
            dragLongEnough = false;
            start = point;
            end = point;
            heldTicks = 0;

            return;
        }

        if (event.getAction() == InputConstants.RELEASE)
        {
            // The release is not handled here any more: the input handler of the sceptre owns it and asks this class
            // whether the press travelled far enough to be a box (see finishDrag).
            return;
        }
    }

    /** Every tick of the drag: one ray, no packet, and a look whether this client is still in the right level. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null)
        {
            cancel();

            return;
        }

        // Another world or another dimension: the ids of the old level mean nothing in the new one.
        if (selectionLevel != level)
        {
            clearSelection();
        }

        if (!dragging)
        {
            return;
        }

        // The sceptre left the hand, a screen opened or the drag was abandoned: no selection is pending any more.
        if (minecraft.screen != null || !holdsScepter(player))
        {
            cancel();

            return;
        }

        end = rayPoint(player);
        ++heldTicks;
        dragLongEnough = heldTicks >= DRAG_TICKS;
    }

    /** @return the box that is being dragged right now, or null when the mouse is not dragging. */
    @Nullable
    public static AABB dragBox()
    {
        if (!dragging || start == null || end == null)
        {
            return null;
        }

        return BroodSelection.selectionBox(start, end);
    }

    /** @return true while the outline of the current selection should be drawn at all. */
    public static boolean highlightActive()
    {
        ClientLevel level = Minecraft.getInstance().level;

        return !selected.isEmpty() && level != null && level.getGameTime() <= visibleUntil;
    }

    /** @return true when that entity is one of the spiders of the current selection of the queen. */
    public static boolean isHighlighted(int entityId)
    {
        return selected.contains(entityId);
    }

    /**
     * The answer of the server: this is what that queen has selected right now (Brood Command Group round).
     *
     * <p>An empty list is an answer as well - it means "nothing is selected", which is what a rally and a deselect leave
     * behind, and what makes the next command refuse and say so instead of guessing at a group.
     */
    public static void onSelectionEcho(int[] entityIds)
    {
        selected.clear();

        for (int entityId : entityIds)
        {
            selected.add(entityId);
        }

        selectionLevel = Minecraft.getInstance().level;
        refreshVisibility();
    }

    /**
     * Starts the ten seconds of {@link #SELECTION_VISIBLE_TICKS} again: every selection gesture shows the outline of the
     * group for that long, and so does every answer of the server about one.
     */
    private static void refreshVisibility()
    {
        ClientLevel level = Minecraft.getInstance().level;

        visibleUntil = level == null ? 0L : level.getGameTime() + SELECTION_VISIBLE_TICKS;
    }

    /**
     * The left button: a mining swing at a block gives the selection up (Brood Command Group round).
     *
     * <p>It is the one gesture of the mouse that is about letting go instead of about the crosshair target: a queen who
     * wants to drop her group simply swings at the ground in front of her, and the selection is gone the moment the swing
     * starts - long before the block would ever break, and whether or not it ever does.
     *
     * <p>A swing at a creature is deliberately not this: that is an attack, and an attack with a sceptre in the hand is
     * still an attack. Only a hit result of type <b>block</b> counts, which is also what the mining path of the game
     * reports while the button is held down.
     *
     * <p>Nothing is cancelled here: the swing still mines as it always did, it only carries the deselect with it. And it
     * carries it once, because the selection of this client is dropped at once - the repeats of a held button find
     * nothing left to give up.
     */
    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!event.isAttack())
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null)
        {
            return;
        }

        if (minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK)
        {
            return;
        }

        if (!holdsScepter(player) || selected.isEmpty())
        {
            return;
        }

        // The outline goes with the swing, and the server is asked for the same thing: the selection is his to keep, and
        // his answer is the truth the next command is measured against.
        clearSelection();
        HungryCraftNetwork.sendToServer(
                new C2SBroodSelectionPacket(C2SBroodSelectionPacket.Action.DESELECT, NO_CANDIDATES));
    }

    /** The empty claim list of an action that carries nothing, see {@link C2SBroodSelectionPacket}. */
    private static final int[] NO_CANDIDATES = new int[0];

    /** Drops the outline, used when this client left the level the selection belonged to or gave the selection up. */
    private static void clearSelection()
    {
        selected.clear();
        selectionLevel = null;
        visibleUntil = 0L;
    }

    /**
     * Ends the drag and answers whether the press was a selection rather than a command.
     *
     * <p>It is called by {@link QueenStaffInputHandler} on the release of the right button, before the press is
     * classified as a click or a hold: a press that travelled far enough while it was down is a box, and the release
     * belongs to the selection. Both conditions of the round are checked here - the button was held for at least
     * {@link #DRAG_TICKS} ticks <b>and</b> the two corners are at least {@value #MIN_DRAG_DISTANCE} blocks apart - so a
     * plain click with a trembling hand is never mistaken for a drag.
     *
     * @return true when a box selection was asked for, false when the press is a command after all
     */
    public static boolean finishDrag(LocalPlayer player, ClientLevel level)
    {
        Vec3 from = start;
        Vec3 to = end;
        boolean wasDrag = dragLongEnough && from != null && to != null
                && from.distanceToSqr(to) >= MIN_DRAG_DISTANCE * MIN_DRAG_DISTANCE;

        cancel();

        if (!wasDrag)
        {
            return false;
        }

        HungryCraftNetwork.sendToServer(new C2SSelectionBoxPacket(from, to));

        // The outline is shown at once, from this client's own entities, and the answer of the server replaces it a tick
        // later: a queen who drags a box wants to see what she caught now and not in two ticks.
        selectBox(player, level, BroodSelection.selectionBox(from, to));

        return true;
    }

    /**
     * Remembers the spiders of that box as the selection this client shows, until the server answers with its own list.
     *
     * <p>Deliberately a second look through <b>this client's own</b> entities and not an answer from the server: the drag
     * just happened here, so the two ticks until the echo arrives are the only moment this class may see something the
     * server has not confirmed yet. An empty box keeps the old selection, exactly like the server keeps it.
     */
    private static void selectBox(LocalPlayer player, ClientLevel level, AABB box)
    {
        Set<Integer> caught = new LinkedHashSet<>();

        for (SpiderMinionEntity spider : level.getEntitiesOfClass(SpiderMinionEntity.class, box))
        {
            if (spider.isOwnedBy(player))
            {
                caught.add(spider.getId());
            }
        }

        if (caught.isEmpty())
        {
            return;
        }

        selected.clear();
        selected.addAll(caught);
        selectionLevel = level;
        refreshVisibility();
    }

    private static void cancel()
    {
        dragging = false;
        dragLongEnough = false;
        start = null;
        end = null;
        heldTicks = 0;
    }

    /**
     * @return where the crosshair points right now, in the world
     *
     * <p>It is the command ray of the sceptre itself - the same sixty four blocks the server casts for every command -
     * so the box the player drags is the box he aimed at. A ray that hit nothing still answers with a point far along
     * the line of sight, which is what makes a drag through the sky possible at all.
     */
    private static Vec3 rayPoint(LocalPlayer player)
    {
        QueenCommandRay.Hit hit = QueenCommandRay.cast(player);

        if (hit.location() != null)
        {
            return hit.location();
        }

        return player.getEyePosition().add(player.getLookAngle().scale(AIR_REACH));
    }

    /** @return true when the sceptre is in one of the hands of that player. */
    private static boolean holdsScepter(LocalPlayer player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = player.getItemInHand(hand);

            if (held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }
}
