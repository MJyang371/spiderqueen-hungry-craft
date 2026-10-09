package spiderqueenhungrycraft.compat.iceandfire;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The Forge events of the optional Ice and Fire compatibility (Necromancy IV prototype 2).
 *
 * <p>It holds exactly one handler, and that one answers the only question the compatibility can not answer by itself:
 * <b>what a client is allowed to know</b>.
 *
 * <p>The necromancy marker lives in the persistent data of the dragon, and Forge never sends that over the wire - it
 * is written and read from disk and nothing else. So the server mirrors the value of the marker to whoever is able to
 * see the dragon, and the moment that happens first is the moment a player <i>starts tracking</i> it. That event is
 * therefore what makes the skeleton of a woken dragon survive a relog, a chunk reload and a dimension change with no
 * bookkeeping of our own: the client forgets everything whenever it leaves a world, and is told again the next time it
 * sees one of these dragons.
 *
 * <p>Nothing here knows an Ice and Fire type: the handler speaks vanilla and hands the work to the facade, so a game
 * without that mod never loads a single line of it.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class IceAndFireCompatEvents
{
    private IceAndFireCompatEvents()
    {
    }

    /** Answers one player who just began to see one entity, when that entity is a dragon this mod has woken. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (!IceAndFireSupport.present())
        {
            return;
        }

        Entity target = event.getTarget();

        if (target != null && event.getEntity() instanceof ServerPlayer player)
        {
            IceAndFireSupport.resyncTracking(player, target);
        }
    }

    /**
     * The contract clock of the world (Necromancy IV round).
     *
     * <p>It runs at the end of every tick of every level and it costs nothing while no dragon is under contract: the
     * compatibility answers whether Ice and Fire is even installed, and the service of the level asks its index
     * whether it holds anything at all before it looks at a single entity. A world with no skeleton on a payroll
     * therefore pays two map lookups per tick and nothing else.
     *
     * <p>The tick of the level was chosen over a tick of the player for one reason: a contract belongs to a dragon and
     * to the world, and it has to end whether or not its owner is still logged in.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide())
        {
            return;
        }

        if (event.level instanceof ServerLevel level)
        {
            IceAndFireSupport.tickContracts(level);
        }
    }
}
