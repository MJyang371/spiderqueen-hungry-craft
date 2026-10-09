package spiderqueenhungrycraft.client;

import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The client side picture of the souls of the local player (Soul Foundation V1): the number the server last sent,
 * plus the roll the HUD plays from the old number to the new one.
 *
 * <p>Nothing here is ever authoritative. The server writes the real balance the moment it changes; this class only
 * decides what the corner of the screen shows while it catches up, so a bug of the animation can never cost a soul.
 *
 * <p><b>The first number of a session is never rolled.</b> A login, a respawn into another world or a client that
 * just connected receives a full sync, and the very first sync of a session is adopted as it is - nobody wants to
 * watch a fresh client count from zero up to the balance of a long lived queen.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class SoulClientState
{
    /**
     * Fraction of the remaining difference that is closed per second, as an exponential approach: the bigger the
     * gap, the bigger the step, and the step shrinks by itself as the number closes in.
     */
    private static final double APPROACH_PER_SECOND = 9.0D;

    /**
     * The floor of the roll, in souls per second. Without it an exponential approach would crawl through its last
     * hundreds; with it even a very large balance finishes in a second or two instead of minutes.
     */
    private static final double MIN_STEP_PER_SECOND = 900.0D;

    /**
     * A frame that took longer than this - an alt tab, a lag spike, a stalled renderer - is treated as this long.
     * The roll keeps its shape instead of jumping because the game was busy.
     */
    private static final double MAX_FRAME_SECONDS = 0.25D;

    /** False until the first number of this session arrives, which is the one that is adopted without a roll. */
    private static boolean initialized;

    /** The number the server last sent: the value the roll is heading for. */
    private static long targetSouls;

    /** The number the corner of the screen really shows right now. */
    private static double displayedSouls;

    /** When the HUD last advanced the roll, in nanoseconds; zero means "no frame yet". */
    private static long lastFrameNanos;

    private SoulClientState()
    {
    }

    /** Applies the state the server sent for the local player. Called from the packet handler only. */
    public static void acceptServerSouls(long souls)
    {
        long clamped = Math.max(0L, souls);

        if (!initialized)
        {
            // The first number of a session is simply true.
            initialized = true;
            targetSouls = clamped;
            displayedSouls = clamped;
            return;
        }

        targetSouls = clamped;
    }

    /** @return the number the server last sent, the value the roll is heading for. */
    public static long targetSouls()
    {
        return targetSouls;
    }

    /**
     * Advances the roll by the real time since the last frame and returns the number to draw.
     *
     * <p>It is called once per frame by the HUD, which is why one call is one frame here: the roll is frame rate
     * independent and never overshoots, because the step is capped by the remaining difference and the very last
     * fraction of it is snapped exactly onto the target.
     */
    public static long drawableSouls()
    {
        long now = System.nanoTime();
        double elapsed = lastFrameNanos == 0L ? 0.0D : (double)(now - lastFrameNanos) / 1_000_000_000.0D;
        lastFrameNanos = now;
        elapsed = Mth.clamp(elapsed, 0.0D, MAX_FRAME_SECONDS);

        double remaining = (double)targetSouls - displayedSouls;

        if (remaining != 0.0D && elapsed > 0.0D)
        {
            double proportional = Math.abs(remaining) * (1.0D - Math.exp(-APPROACH_PER_SECOND * elapsed));
            double floor = MIN_STEP_PER_SECOND * elapsed;
            double step = Math.min(Math.max(proportional, floor), Math.abs(remaining));

            displayedSouls += Math.signum(remaining) * step;

            // The end is exact and never nearly: a number that stopped one short would keep the roll alive forever.
            if (Math.abs((double)targetSouls - displayedSouls) < 0.5D)
            {
                displayedSouls = targetSouls;
            }
        }

        return Math.round(displayedSouls);
    }

    /** A client that joins a world starts a new session: the next number it is told is adopted without a roll. */
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event)
    {
        reset();
    }

    /** And a client that leaves one starts over as well, so the next world never rolls out of the old balance. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        reset();
    }

    /** Forgets everything, including the fact that a number was ever seen. */
    private static void reset()
    {
        initialized = false;
        targetSouls = 0L;
        displayedSouls = 0.0D;
        lastFrameNanos = 0L;
    }
}
