package spiderqueenhungrycraft.soulfire;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one tick hook of the contract ledger (round 7E, stage A3).
 *
 * <p>A restore that fails is not a loss - the remains wait in the ledger - but waiting is only worth anything if
 * something comes back for them. That something is this sweep: every {@value #SWEEP_INTERVAL} ticks the server is asked
 * to honour every PENDING contract again, which covers the two cases that can make a restore fail and then stop being
 * true:
 *
 * <ul>
 * <li>the other mod was not installed, or was not ready, when the contract ended,</li>
 * <li>the restore was refused once for a reason that has passed (a chunk that was not loaded yet, a level that was
 * shutting down).</li>
 * </ul>
 *
 * <p>It writes nothing and reads nothing when the ledger is empty, and it never creates a second bone dragon: the
 * identity of the original remains is the lock of every restore.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SoulfireContractEvents
{
    /** How often the ledger is swept, in ticks: twice a minute is often enough for a promise nobody is watching. */
    public static final int SWEEP_INTERVAL = 600;

    private SoulfireContractEvents()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server == null || server.getTickCount() % SWEEP_INTERVAL != 0)
        {
            return;
        }

        if (SoulfireContracts.get(server).pending().isEmpty())
        {
            return;
        }

        int honoured = SoulfireRising.retryPending(server);

        if (honoured > 0)
        {
            SpiderQueenHungryCraft.LOGGER.info("soulfire ledger: the sweep laid {} contract(s) back down", honoured);
        }
    }
}
