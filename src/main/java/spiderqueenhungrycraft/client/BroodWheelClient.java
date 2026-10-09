package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The command wheel of the queen sceptre (Command Wheel round).
 *
 * <p><b>Hold Shift and the ring appears.</b> It hangs in the middle of the screen with one command per direction - the
 * view selection, the species selection, the soft rally, the order cycle, the hunt, the hold, the ability of the brood
 * and the hard rally - and every command prints its own shortcut underneath, so the ring is also the list of the keys a
 * queen has given herself. Moving the mouse picks a command, letting Shift go casts it. Letting go while the pointer is
 * still in the middle cancels, so an opened ring is never a cast by accident.
 *
 * <p><b>Shift, not "sneak".</b> The two Shift keys are asked for by <b>key code</b> and not through the sneak binding of
 * the game: a queen who rebinds her sneak still opens the ring with Shift, and a player who never presses Shift never
 * sees it. Shift is the vanilla sneak key as well, so she crouches while the ring is open - harmless, and the one thing
 * that used to happen with Shift and the sceptre at the same time (the hard rally of a sneaking right click) is gone from
 * this round on: the rally is a command of the ring.
 *
 * <p><b>The ring casts nothing of its own.</b> {@link BroodWheelAction} knows which command a direction means and sends
 * exactly the packet that command's own key sends, so every command of the brood exists once in this mod and is merely
 * reachable in two ways. Nothing is sent while the ring is open - it is one request at the moment it closes, and none at
 * all when it closes on the middle.
 *
 * <p><b>The camera is handed over while the ring is open.</b> The mouse is released the moment the ring opens, so moving
 * it picks a command and <b>nothing else</b> - the view does not turn while the queen is choosing - and it is grabbed
 * again the moment the ring closes, unless the player opened a screen in the meantime. That is the same state a screen
 * puts the mouse in, only without a screen.
 *
 * <p>The ring is drawn as a real ring of sectors and not as a row of boxes: one sector per command, a small angular gap
 * between two of them so they never touch, a hole in the middle for the pointer to be parked in, and one colour per group
 * of commands (see {@link BroodWheelAction.Group}).
 *
 * <p>While the ring is open the attack and the use button of the game are swallowed ({@link #onInteraction}), so picking a
 * command with the mouse can never swing a sword, mine or place a block.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BroodWheelClient implements IGuiOverlay
{
    /** The hole of the ring in GUI pixels: nothing is picked inside it, which is how the ring is left again. */
    private static final int INNER_RADIUS = 34;

    /** The outer edge of the ring in GUI pixels. Both radii shrink together on a small screen, see {@link #render}. */
    private static final int OUTER_RADIUS = 118;

    /** The angular gap between two sectors, in degrees, so the commands of the ring never touch each other. */
    private static final double SECTOR_GAP_DEGREES = 3.0D;

    /** The angular steps one sector is drawn with: more steps are a rounder arc and cost a few vertices more. */
    private static final int ARC_STEPS = 24;

    /** The rim of a picked sector is this much wider and thicker than the sector itself, in GUI pixels. */
    private static final int RIM = 2;

    /** How small the line of the shortcut is drawn, compared to the name of the command. */
    private static final float SHORTCUT_SCALE = 0.75F;

    private static final int TEXT = 0xFFF2F2F2;
    private static final int SHORTCUT_TEXT = 0xFFB0B0B0;
    private static final int TITLE_TEXT = 0xFFE9C46A;
    private static final int HINT_TEXT = 0xFFDDDDDD;

    /** The wash over the sector the pointer is on: the accent of its group, at half strength. */
    private static final int PICKED_WASH_ALPHA = 0x80;

    /** True while the ring is open, which is exactly while Shift is held with the sceptre in hand. */
    private static boolean open;

    /** True while this class itself released the mouse, so it is the one that has to give it back. */
    private static boolean cursorReleased;

    /** The command the pointer is on right now, -1 while the pointer is in the middle of the ring. */
    private static int highlighted = -1;

    /** @return true while the command wheel is open. The sceptre stays silent while a command is being picked. */
    public static boolean isOpen()
    {
        return open;
    }

    /**
     * The whole state machine of the ring, one step per client tick: it opens on the tick Shift goes down (with the
     * sceptre in a hand and no screen open), it follows the pointer every tick while it is open, and it closes - casting
     * what was picked - on the tick Shift comes back up.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean keyDown = shiftDown(minecraft);
        boolean allowed = keyDown && mayCommand(minecraft);

        if (!open)
        {
            if (allowed)
            {
                openRing(minecraft);
            }

            return;
        }

        // Open: the pointer picks. A ring that is no longer allowed (a screen opened, the sceptre left the hand) closes
        // without casting anything, and only a real release casts.
        if (!keyDown)
        {
            BroodWheelAction picked = picked();

            closeRing(minecraft);

            if (picked != null)
            {
                picked.cast();
            }

            return;
        }

        if (!allowed)
        {
            closeRing(minecraft);

            return;
        }

        highlighted = entryUnderPointer(minecraft);
    }

    /**
     * Opens the ring and takes the mouse away from the camera.
     *
     * <p>The cursor is <b>released</b> here, which is what the queen asked for: while the ring is open the mouse belongs
     * to the ring and no movement of it turns her view around. A freed cursor also sits exactly where the ring expects
     * it, in the middle of the window, and the game itself keeps it there (that is the very state a screen puts the
     * mouse in, only without a screen).
     */
    private static void openRing(Minecraft minecraft)
    {
        open = true;
        highlighted = entryUnderPointer(minecraft);

        if (minecraft.mouseHandler.isMouseGrabbed())
        {
            cursorReleased = true;
            minecraft.mouseHandler.releaseMouse();
        }
    }

    /** Closes the ring and gives the mouse back to the camera, unless something else is using it now. */
    private static void closeRing(Minecraft minecraft)
    {
        open = false;
        highlighted = -1;

        if (!cursorReleased)
        {
            return;
        }

        cursorReleased = false;

        // Never over a screen the player opened in the meantime (grabbing the mouse would close that screen), and never
        // when the mouse was already free before the ring opened.
        if (minecraft.screen == null && !minecraft.mouseHandler.isMouseGrabbed())
        {
            minecraft.mouseHandler.grabMouse();
        }
    }

    /** @return the command that would be cast right now, null while the pointer is in the middle of the ring. */
    private static BroodWheelAction picked()
    {
        BroodWheelAction[] actions = BroodWheelAction.values();

        return highlighted < 0 || highlighted >= actions.length ? null : actions[highlighted];
    }

    /** @return true while one of the two Shift keys is held down, by key code and not by the sneak binding. */
    private static boolean shiftDown(Minecraft minecraft)
    {
        long window = minecraft.getWindow().getWindow();

        return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
    }

    /** @return true when this player may command at all: in the world, no menu open and sceptre in hand. */
    private static boolean mayCommand(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.getConnection() == null)
        {
            return false;
        }

        for (ItemStack stack : player.getHandSlots())
        {
            if (stack.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the command the pointer is on, -1 while it is inside the dead zone of the middle. Only the direction of the
     *         pointer counts, never its distance: the ring is a circle of directions, and the first command sits at the
     *         top.
     */
    private static int entryUnderPointer(Minecraft minecraft)
    {
        Window window = minecraft.getWindow();
        double scale = Math.max(1.0D, window.getGuiScale());
        double mouseX = minecraft.mouseHandler.xpos() / scale;
        double mouseY = minecraft.mouseHandler.ypos() / scale;
        double offsetX = mouseX - window.getGuiScaledWidth() / 2.0D;
        double offsetY = mouseY - window.getGuiScaledHeight() / 2.0D;

        // The dead zone of the picking is the hole of the drawn ring, so the middle of the ring is always the way out.
        float room = Math.min(window.getGuiScaledWidth(), window.getGuiScaledHeight()) / 2.0F - 12.0F;
        float ringScale = Math.min(1.0F, room / (float) OUTER_RADIUS);
        double deadZone = INNER_RADIUS * (double) ringScale;

        if (offsetX * offsetX + offsetY * offsetY < deadZone * deadZone)
        {
            return -1;
        }

        int count = BroodWheelAction.values().length;
        double step = Math.PI * 2.0D / (double) count;

        // atan2 measures from the right, the first command sits at the top, and half a step turns the borders of the
        // sectors onto the commands instead of between them.
        double angle = Math.atan2(offsetY, offsetX) + Math.PI / 2.0D + step / 2.0D;

        while (angle < 0.0D)
        {
            angle += Math.PI * 2.0D;
        }

        while (angle >= Math.PI * 2.0D)
        {
            angle -= Math.PI * 2.0D;
        }

        return Math.min(count - 1, (int) (angle / step));
    }

    /**
     * Swallows the attack and the use button of the game while the ring is open, so a click that picks a command can
     * never swing at a creature, mine a block or place one.
     */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (open && (event.isAttack() || event.isUseItem()))
        {
            event.setCanceled(true);
        }
    }

    /**
     * Draws the ring: one sector per command around a hole in the middle, the name of the command on it and its shortcut
     * in small letters under that, the sector of the pointer lit up, and every group of commands in its own colour. The
     * hole in the middle says what a release would do - the group and the name of the picked command, or that the ring
     * will simply close again.
     */
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        if (!open)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        BroodWheelAction[] actions = BroodWheelAction.values();
        int count = actions.length;
        float centreX = width / 2.0F;
        float centreY = height / 2.0F;

        // A small screen keeps the ring inside itself: both radii shrink together, and the picking shrinks with them.
        float room = Math.min(width, height) / 2.0F - 12.0F;
        float scale = Math.min(1.0F, room / (float) OUTER_RADIUS);
        float inner = INNER_RADIUS * scale;
        float outer = OUTER_RADIUS * scale;

        double step = Math.PI * 2.0D / (double) count;
        double gap = Math.toRadians(SECTOR_GAP_DEGREES) / 2.0D;

        for (int index = 0; index < count; index++)
        {
            // The first command sits at the top and the rest follow clockwise.
            double middle = -Math.PI / 2.0D + step * (double) index;
            double from = middle - step / 2.0D + gap;
            double to = middle + step / 2.0D - gap;
            BroodWheelAction action = actions[index];
            boolean picked = index == highlighted;

            if (picked)
            {
                // The rim first: a little wider and a little thicker than the sector itself, so it reads as a border.
                sector(graphics, centreX, centreY, inner - RIM, outer + RIM,
                        middle - step / 2.0D + gap / 2.0D, middle + step / 2.0D - gap / 2.0D,
                        action.group().accentColor());
            }

            sector(graphics, centreX, centreY, inner, outer, from, to, action.group().fillColor());

            if (picked)
            {
                // A wash of the group colour over the lit sector, so it is clearly the one under the pointer.
                sector(graphics, centreX, centreY, inner, outer, from, to,
                        (action.group().accentColor() & 0x00FFFFFF) | (PICKED_WASH_ALPHA << 24));
            }
        }

        // The names and the shortcuts, each on the middle of its own sector.
        float middleRadius = (inner + outer) / 2.0F;

        for (int index = 0; index < count; index++)
        {
            double middle = -Math.PI / 2.0D + step * (double) index;
            int x = Math.round(centreX + (float) Math.cos(middle) * middleRadius);
            int y = Math.round(centreY + (float) Math.sin(middle) * middleRadius);
            Component label = actions[index].label();
            graphics.drawString(font, label, x - font.width(label) / 2, y - 9, TEXT, true);

            Component shortcut = actions[index].shortcut();
            graphics.pose().pushPose();
            graphics.pose().translate((float) x, (float) (y + 2), 0.0F);
            graphics.pose().scale(SHORTCUT_SCALE, SHORTCUT_SCALE, 1.0F);
            graphics.drawString(font, shortcut, -font.width(shortcut) / 2, 0, SHORTCUT_TEXT, true);
            graphics.pose().popPose();
        }

        // The hole in the middle: what a release would do right now.
        BroodWheelAction pickedAction = picked();
        Component title = Component.translatable("wheel.spiderqueen_hungrycraft.title");

        graphics.drawString(font, title, Math.round(centreX) - font.width(title) / 2, Math.round(centreY) - 16,
                TITLE_TEXT, true);

        if (pickedAction == null)
        {
            Component hint = Component.translatable("wheel.spiderqueen_hungrycraft.none");
            graphics.drawString(font, hint, Math.round(centreX) - font.width(hint) / 2, Math.round(centreY) - 3,
                    HINT_TEXT, true);

            return;
        }

        Component group = pickedAction.group().label();
        Component name = pickedAction.label();
        graphics.drawString(font, group, Math.round(centreX) - font.width(group) / 2, Math.round(centreY) - 6,
                pickedAction.group().accentColor(), true);
        graphics.drawString(font, name, Math.round(centreX) - font.width(name) / 2, Math.round(centreY) + 5, TEXT, true);
    }

    /**
     * Draws one sector of the ring: the piece of it between the two radii and between the two angles, as a fan of quads.
     *
     * <p>It is added to the very buffer the text of this overlay goes into ({@code RenderType.gui} through the buffer
     * source of the given graphics), so the sectors and their labels are drawn in the order they are written here and
     * nothing of the wheel can end up under the rest of the HUD.
     */
    private static void sector(GuiGraphics graphics, float centreX, float centreY, float inner, float outer,
            double fromAngle, double toAngle, int color)
    {
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer consumer = graphics.bufferSource().getBuffer(RenderType.gui());
        float alpha = (float) (color >> 24 & 0xFF) / 255.0F;
        float red = (float) (color >> 16 & 0xFF) / 255.0F;
        float green = (float) (color >> 8 & 0xFF) / 255.0F;
        float blue = (float) (color & 0xFF) / 255.0F;
        double step = (toAngle - fromAngle) / (double) ARC_STEPS;

        for (int index = 0; index < ARC_STEPS; index++)
        {
            double first = fromAngle + step * (double) index;
            double second = first + step;
            float firstSin = (float) Math.sin(first);
            float firstCos = (float) Math.cos(first);
            float secondSin = (float) Math.sin(second);
            float secondCos = (float) Math.cos(second);

            consumer.vertex(matrix, centreX + firstCos * inner, centreY + firstSin * inner, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, centreX + firstCos * outer, centreY + firstSin * outer, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, centreX + secondCos * outer, centreY + secondSin * outer, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
            consumer.vertex(matrix, centreX + secondCos * inner, centreY + secondSin * inner, 0.0F)
                    .color(red, green, blue, alpha).endVertex();
        }
    }
}
