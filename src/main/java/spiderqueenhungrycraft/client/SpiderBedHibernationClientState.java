package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.bed.SpiderBedHibernation;

/**
 * The client half of a Spider Bed Hibernation session (Phase 8E3 patch).
 *
 * <p>The server owns the session: it decides when one starts, how long it lasts and when it is over. This
 * class only remembers what it was told, keeps the local input quiet while it lasts, opens the hibernation
 * screen and draws the same fade the vanilla bed draws.
 *
 * <p>The fade follows the vanilla numbers: it grows to an alpha of 220 over the length of the rest and the
 * colour is the very same dark blue black the vanilla sleep overlay uses.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class SpiderBedHibernationClientState
{
    /** The colour of the vanilla sleep overlay. */
    private static final int FADE_COLOR = 1052704;

    /** The alpha the vanilla sleep overlay reaches at the end of the rest. */
    private static final int FADE_MAX_ALPHA = 220;

    private static boolean active;
    private static int durationTicks = SpiderBedHibernation.DURATION_TICKS;
    private static int elapsedTicks;

    private SpiderBedHibernationClientState()
    {
    }

    public static boolean isActive()
    {
        return active;
    }

    /** How far the rest has come, from 0 to 1. */
    public static float progress()
    {
        if (!active)
        {
            return 0.0F;
        }

        return Math.min(1.0F, (float) elapsedTicks / (float) Math.max(1, durationTicks));
    }

    /** The state the server just sent: start a session, or end the one that runs. */
    public static void acceptServerState(boolean hibernating, int duration)
    {
        if (!hibernating)
        {
            stop();
            return;
        }

        if (!active)
        {
            elapsedTicks = 0;
        }

        active = true;
        durationTicks = Math.max(1, duration);

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen != null && !(minecraft.screen instanceof SpiderBedHibernationScreen))
        {
            // A player who happened to have a menu open gets the hibernation screen as well, the way the
            // vanilla bed screen simply takes over.
            minecraft.setScreen(null);
        }

        if (minecraft.screen == null)
        {
            minecraft.setScreen(new SpiderBedHibernationScreen());
        }
    }

    /** Ends the local half of a session and closes the screen that belongs to it. */
    public static void stop()
    {
        active = false;
        elapsedTicks = 0;

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof SpiderBedHibernationScreen)
        {
            minecraft.setScreen(null);
        }
    }

    /** Draws the vanilla-like sleep fade over the whole screen. */
    public static void renderFade(GuiGraphics graphics, int width, int height)
    {
        int alpha = (int) ((float) FADE_MAX_ALPHA * progress());

        if (alpha <= 0)
        {
            return;
        }

        graphics.fill(RenderType.guiOverlay(), 0, 0, width, height, alpha << 24 | FADE_COLOR);
    }

    /**
     * One client tick: count up and hold the local player still. The server refuses every action of a
     * hibernating queen anyway, this only keeps the local movement in step with it.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !active)
        {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null)
        {
            stop();
            return;
        }

        elapsedTicks++;

        player.input.leftImpulse = 0.0F;
        player.input.forwardImpulse = 0.0F;
        player.input.jumping = false;
        player.input.shiftKeyDown = false;
        player.setSprinting(false);
    }
}
