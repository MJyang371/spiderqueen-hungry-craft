package spiderqueenhungrycraft.progression;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Keeps the open growth interface of a player honest (Progression Foundation round).
 *
 * <p>A session is only ever valid while the player is alive, in the same world and at the same core, so a stale one
 * already can not do anything - but a closed interface of a player who left, died or walked through a portal has no
 * reason to stay in memory at all. This class is the one place that forgets them, so nothing has to remember to.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class ProgressionEvents
{
    private ProgressionEvents()
    {
    }

    /** A player who logs out is not standing at a core any more. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        ProgressionSessions.close(event.getEntity().getUUID());
    }

    /** And neither is one who just died and came back somewhere else. */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        ProgressionSessions.close(event.getEntity().getUUID());
    }

    /** The core of the old world is not the core of the new one, whatever its coordinates say. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        ProgressionSessions.close(event.getEntity().getUUID());
    }
}
