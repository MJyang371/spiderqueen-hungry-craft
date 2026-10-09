package spiderqueenhungrycraft.player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastController;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.movement.SimpleWallClimbController;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CWallClimbStatePacket;
import spiderqueenhungrycraft.progression.ProgressionService;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * The one place that pushes the runtime state of a queen to her client (Phase 8E1-DIMENSION-SYNC-POLISH-1).
 *
 * <p>The queen flag itself lives on the server and is never trusted to a client, but a client does have to know the
 * shapes of the runtime state: whether the player runs around as a beast, whether the wall climb is on and how much
 * silk her glands hold. A client rebuilds its level on every dimension change, so that knowledge has to be repeated
 * afterwards, exactly like it is repeated after a login and after a respawn. One single entry point keeps those
 * moments from drifting apart, and the existing packets of the mod are reused instead of inventing a second queen
 * state.
 *
 * <p>Nothing here ever writes {@link QueenData}: the sync only reports what the serverside gameplay already decided.
 * It is also never a periodic broadcast, only a small one shot with a single delayed repetition after a dimension
 * change, because a client needs a few ticks to build its new level.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenRuntimeSync
{
    /** Ticks after which a dimension change repeats its sync, once the client has its new level. */
    private static final int RETRY_DELAY_TICKS = 3;

    /** Players waiting for that single repetition. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();

    private QueenRuntimeSync()
    {
    }

    /** Pushes the whole runtime state a client has to know about that player. */
    public static void syncAll(ServerPlayer player, String reason)
    {
        if (player == null || player.isRemoved() || !player.isAlive())
        {
            return;
        }

        // The beast form goes through the sync of its own controller, which sends the state packet this mod
        // always used, to the player itself and to everybody who tracks them.
        QueenBeastController.sync(player);

        // The wall climb mode only has to be repeated while it is on: switching it off is sent by its own
        // controller on every login, respawn and dimension change already.
        if (SimpleWallClimbController.isEnabled(player))
        {
            HungryCraftNetwork.sendToPlayer(new S2CWallClimbStatePacket(true), player);
        }

        // The silk of the queen (feature SILK-CAPACITY) is a server value with a client bar, so it is
        // repeated here too: a client that just built its level knows nothing about the amount yet.
        QueenSilk.syncTo(player);

        // The bought skills of the queen (Skill Progression round) are a permission list like the others: they are what
        // keeps the key of a locked skill silent while she runs around, so a client that just built its level is told
        // again what its player really owns.
        ProgressionService.sendSkillState(player);

        if (SpiderQueenHungryCraft.LOGGER.isDebugEnabled())
        {
            SpiderQueenHungryCraft.LOGGER.debug("[DIMSYNC] syncAll reason={} queen={} beast={} climb={} silk={}"
                    + " skills={}", reason, QueenDataCapability.isQueen(player),
                    QueenBeastState.isTransformed(player), SimpleWallClimbController.isEnabled(player),
                    QueenSilk.getSilkAmount(player), QueenSkills.of(player).size());
        }
    }

    /** A dimension change is synced at once and once more a few ticks later, when the client is ready. */
    public static void syncAfterDimensionChange(ServerPlayer player)
    {
        syncAll(player, "changed_dimension");
        PENDING.put(player.getUUID(), RETRY_DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            syncAll(player, "logged_in");
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            syncAll(player, "respawn");
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            syncAfterDimensionChange(player);
        }
    }

    /** The single delayed repetition of a dimension change; it removes itself and never becomes a loop. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null)
        {
            return;
        }

        for (UUID owner : new ArrayList<>(PENDING.keySet()))
        {
            if (PENDING.merge(owner, -1, Integer::sum) > 0)
            {
                continue;
            }

            PENDING.remove(owner);
            ServerPlayer player = server.getPlayerList().getPlayer(owner);

            if (player != null)
            {
                syncAll(player, "changed_dimension_retry");
            }
        }
    }
}
