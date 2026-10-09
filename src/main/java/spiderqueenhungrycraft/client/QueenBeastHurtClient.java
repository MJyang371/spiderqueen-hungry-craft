package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The client half of the small hit answer of the beast (Phase 8B-POLISH-2).
 *
 * <p>The server decides whether an incoming hit was small enough to leave the camera alone and tells this
 * class about it. The client never guesses a damage number and never reads the health bar for it: it only
 * remembers the verdict of the server for the next few ticks, which is exactly the span of the vanilla hurt
 * animation it wants to sit out.
 *
 * <p>A big hit clears the mark immediately, so a heavy blow in the middle of a scratch still shakes the
 * screen as it should, and leaving the form, dying or leaving the world clears it as well. The mark is only
 * read by the very narrow camera hook in {@code BeastHurtCameraMixin}, nothing else in the game looks at it.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastHurtClient
{
    /** True while the server asked for the hurt bob of the local player to be skipped. */
    private static boolean suppressHurtBob;

    /** Ticks of suppression left. Never negative. */
    private static int suppressTicks;

    private QueenBeastHurtClient()
    {
    }

    /** The server told us how big that hit really was. */
    public static void onHurtFeedback(int entityId, boolean suppress, float finalDamage)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        // A hurt camera only ever belongs to the own player, everything else is none of our business.
        if (player == null || player.getId() != entityId)
        {
            return;
        }

        suppressHurtBob = suppress;
        suppressTicks = suppress ? QueenBeastConstants.BEAST_SMALL_HIT_SUPPRESSION_TICKS : 0;
    }

    /**
     * @return true when the vanilla hurt bob of this frame has to be skipped: a small hit arrived a moment ago
     *         and the local player is really still a living beast. The death tilt of the vanilla camera is never
     *         suppressed, a dying player keeps its ordinary animation.
     */
    public static boolean suppressesHurtBob()
    {
        if (!suppressHurtBob || suppressTicks <= 0)
        {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        return player != null && QueenBeastState.isTransformed(player) && !player.isDeadOrDying()
                && !player.isSpectator();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (suppressTicks > 0)
        {
            suppressTicks--;
        }

        // A world change or a lost form always ends the suppression, so it can never leak into a later life.
        if (Minecraft.getInstance().level == null)
        {
            suppressHurtBob = false;
            suppressTicks = 0;
        }
    }
}
