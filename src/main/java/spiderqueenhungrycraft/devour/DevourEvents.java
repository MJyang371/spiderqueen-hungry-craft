package spiderqueenhungrycraft.devour;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The Forge hooks of the devour key (Phase 8E2A).
 *
 * <p>It only connects the moments to {@link DevourInput}: the heartbeat that turns a held key into a
 * regurgitation, and the three ways a press of a key must never survive - a logout, a death and a dimension
 * change. Every rule lives in that class, so there is exactly one authority for the key instead of one code path
 * per event.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class DevourEvents
{
    private DevourEvents()
    {
    }

    /** Server side heartbeat of the key: the only place a hold can mature, and where the fluid keeps flying. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
        {
            return;
        }

        if (event.player instanceof ServerPlayer player)
        {
            DevourInput.tick(player);

            // The remaining batches of a vomit are released one per server tick, from the same heartbeat.
            DevourFluid.tick(player.getServer());
        }
    }

    /** A player who leaves the server is not holding anything. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            DevourInput.clear(player);
        }
    }

    /** Death drops every input of that player, so the next life starts with a free key. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            DevourInput.clear(player);
        }
    }

    /** A new world means new input: a key of the old dimension is never carried over as held. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            DevourInput.clear(player);
        }
    }
}
