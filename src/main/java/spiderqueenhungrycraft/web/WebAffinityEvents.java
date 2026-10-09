package spiderqueenhungrycraft.web;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * Web Affinity events (Phase 6-SIMPLE-2): the gentle web physics for web natives and the fast web tearing of
 * the Spider Queen. Both only ever look at the tags, never at block names or class names.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class WebAffinityEvents
{
    /** Break speed the queen gets on any web block, about one to two ticks of mining. */
    private static final float QUEEN_WEB_BREAK_SPEED = 60.0F;

    private WebAffinityEvents()
    {
    }

    /** Runs for every living entity on both sides, the helper itself decides whether anything happens. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        LivingEntity entity = event.getEntity();
        WebAffinity.applyWebPhysics(entity);
    }

    /** The queen tears webs open bare handed or with anything in her hands. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();

        if (!QueenDataCapability.isQueen(player) || !WebAffinity.isWebBlock(event.getState()))
        {
            return;
        }

        event.setNewSpeed(Math.max(event.getNewSpeed(), QUEEN_WEB_BREAK_SPEED));
    }
}
