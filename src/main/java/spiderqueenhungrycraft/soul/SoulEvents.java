package spiderqueenhungrycraft.soul;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The wiring of the soul system into the game (Soul Foundation V1): experience in, souls out on death, and the
 * client kept up to date.
 *
 * <p>Everything here is a thin translation between a game event and the server logic of
 * {@link Souls}, {@link SoulDeathDrop} and {@link SoulRules} - the rules themselves live in those classes, so they
 * can be reasoned about (and tested) without a running event bus.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SoulEvents
{
    private SoulEvents()
    {
    }

    /**
     * <b>Experience becomes souls.</b>
     *
     * <p>It is the experience change of the game itself ({@code Player#giveExperiencePoints}) and nothing else, so
     * every ordinary source is covered without a single line about minecarts, mobs, furnaces or bottles - and the
     * two directions of that event are told apart here: a gain pays, a spend (an anvil, a repair, a command that
     * takes levels away) is ignored.
     */
    @SubscribeEvent
    public static void onExperienceChanged(PlayerXpEvent.XpChange event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            Souls.grantForExperience(player, event.getAmount());
        }
    }

    /**
     * <b>A death drops the souls of that life.</b>
     *
     * <p>A canceled death (a totem of undying, another mod of the pack) never dropped anything and never replaced a
     * remnant - the event is the moment the game decides that a creature really dies.
     */
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && !event.isCanceled())
        {
            SoulDeathDrop.onPlayerDeath(player);
        }
    }

    /** A client that just joined knows nothing about the souls of its player yet. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            Souls.syncTo(player);
        }
    }

    /** And the client of a player who just respawned still shows the balance of the life that ended. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            Souls.syncTo(player);
        }
    }

    /** The same for a player who walks through a portal, so nothing has to be re-derived on the client. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            Souls.syncTo(player);
        }
    }
}
