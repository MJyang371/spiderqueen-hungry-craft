package spiderqueenhungrycraft.human.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Runs the two spawners of Phase HF0D (Phase HF0D).
 *
 * <p>One hook, on the level tick of every server level. Both spawners decide on their own whether this tick is theirs:
 * they compare the game time with the interval of their rules, so this class never has to carry a clock of its own.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HumanSpawnEvents
{
    private HumanSpawnEvents()
    {
    }

    // ===================== DIAGNOSTIC COUNTERS =====================
    private static int tickSeen;
    private static int surfaceSpawnerCalled;
    private static int rulesNullSkip;
    private static int surfaceDisabledSkip;
    private static int dimensionReject;
    private static int tickIntervalSkip;
    private static int playerLoopEntries;
    private static int spectatorSkip;
    private static int chanceReject;
    private static int targetReachedSkip;
    private static int chunkNotLoadedSkip;
    private static int perChunkCapReject;
    private static int typePickNull;
    private static int localCapReject;
    private static int positionsTested;
    private static int ringReject;
    private static final int[] reasonRejects = new int[HumanSpawnValidator.Reason.values().length];
    private static int validatorReject;
    private static int spawnFailed;
    private static int spawnSuccess;
    private static long lastReportGameTime = -1L;

    /** Last known human population of the overworld, kept up to date by the spawner itself and by its log line. */
    private static int lastPopulation;

    /** Called by the surface spawner once per level tick with the number of humans that stand around the players. */
    public static synchronized void recordPopulation(int population)
    {
        lastPopulation = population;
    }

    /** @return a snapshot of all counters; the counters themselves are left untouched. */
    public static synchronized SpawnDiagnostics diagnostics()
    {
        SpawnDiagnostics d = new SpawnDiagnostics(tickSeen, surfaceSpawnerCalled, rulesNullSkip,
                surfaceDisabledSkip, dimensionReject, tickIntervalSkip, playerLoopEntries, spectatorSkip,
                chanceReject, targetReachedSkip, chunkNotLoadedSkip, perChunkCapReject, typePickNull, localCapReject, positionsTested,
                ringReject, validatorReject, spawnFailed, spawnSuccess, lastPopulation);
        return d;
    }

    /**
     * @return the position rejections of this run, one {@code name=count} entry per condition that ever said no. An
     *         empty string means the validator never refused a position, which is what a spawner that never reached
     *         the validator looks like from the outside.
     */
    public static synchronized String reasonSummary()
    {
        StringBuilder text = new StringBuilder();
        HumanSpawnValidator.Reason[] reasons = HumanSpawnValidator.Reason.values();

        for (int i = 0; i < reasons.length; ++i)
        {
            if (reasonRejects[i] > 0)
            {
                if (text.length() > 0)
                {
                    text.append(' ');
                }

                text.append(reasons[i].name()).append('=').append(reasonRejects[i]);
            }
        }

        return text.toString();
    }

    /** @return how often the given condition refused a position since the last reset. */
    public static synchronized int reasonCount(HumanSpawnValidator.Reason reason)
    {
        return reason != null && reason.ordinal() < reasonRejects.length ? reasonRejects[reason.ordinal()] : 0;
    }

    /** Resets all counters to zero. */
    public static synchronized void resetCounters()
    {
        tickSeen = 0;
        surfaceSpawnerCalled = 0;
        rulesNullSkip = 0;
        surfaceDisabledSkip = 0;
        dimensionReject = 0;
        tickIntervalSkip = 0;
        playerLoopEntries = 0;
        spectatorSkip = 0;
        chanceReject = 0;
        targetReachedSkip = 0;
        chunkNotLoadedSkip = 0;
        perChunkCapReject = 0;
        typePickNull = 0;
        localCapReject = 0;
        positionsTested = 0;
        ringReject = 0;
        java.util.Arrays.fill(reasonRejects, 0);
        validatorReject = 0;
        spawnFailed = 0;
        spawnSuccess = 0;
        lastReportGameTime = -1L;
    }

    private static void periodicReport(ServerLevel level)
    {
        if (!diagnosticsEnabled())
        {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - lastReportGameTime < 600L)
            return;
        lastReportGameTime = gameTime;

        SpawnDiagnostics d = diagnostics();
        SpiderQueenHungryCraft.LOGGER.info(
                "[Spawn-DIAG] gameTime={} tickSeen={} surfaceCalled={} rulesNull={} surfDisabled={} "
                        + "dimReject={} tickIntervalSkip={} playersSeen={} specSkip={} "
                        + "chanceReject={} targetR={} chunkNL={} perChunkCap={} typeNull={} "
                        + "localCapR={} posTested={} ringR={} validatorR={} rejectReasons=[{}] spawnF={} spawnS={} pop={}",
                gameTime, d.tickSeen, d.surfaceSpawnerCalled, d.rulesNullSkip, d.surfaceDisabledSkip,
                d.dimensionReject, d.tickIntervalSkip, d.playerLoopEntries, d.spectatorSkip,
                d.chanceReject, d.targetReachedSkip, d.chunkNotLoadedSkip, d.perChunkCapReject, d.typePickNull,
                d.localCapReject, d.positionsTested, d.ringReject, d.validatorReject, reasonSummary(),
                d.spawnFailed, d.spawnSuccess, d.population);
    }

    /**
     * The counters and the log line they produce are the diagnosis of a game test run. A normal client or server has
     * no game test namespace enabled, so it keeps counting silently (fifteen int increments per attempt) and never
     * writes a line into its log.
     */
    private static boolean diagnosticsEnabled()
    {
        String namespaces = System.getProperty("forge.enabledGameTestNamespaces");
        return namespaces != null && namespaces.contains(SpiderQueenHungryCraft.MODID);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level))
        {
            return;
        }

        tickSeen++;
        periodicReport(level);
        HumanSurfaceSpawner.tick(level, HumanSpawnerHooks.forSurface());
        HumanVillageGarrisonSpawner.tick(level);
    }

    // ===================== INCREMENT METHODS =====================
    static synchronized void incSurfaceSpawnerCalled() { surfaceSpawnerCalled++; }
    static synchronized void incRulesNullSkip() { rulesNullSkip++; }
    static synchronized void incSurfaceDisabledSkip() { surfaceDisabledSkip++; }
    static synchronized void incDimensionReject() { dimensionReject++; }
    static synchronized void incTickIntervalSkip() { tickIntervalSkip++; }
    static synchronized void incPlayerLoopEntries() { playerLoopEntries++; }
    static synchronized void incSpectatorSkip() { spectatorSkip++; }
    static synchronized void incChanceReject() { chanceReject++; }
    static synchronized void incTargetReachedSkip() { targetReachedSkip++; }
    static synchronized void incChunkNotLoadedSkip() { chunkNotLoadedSkip++; }
    static synchronized void incPerChunkCapReject() { perChunkCapReject++; }
    static synchronized void incTypePickNull() { typePickNull++; }
    static synchronized void incLocalCapReject() { localCapReject++; }
    static synchronized void incPositionTested() { positionsTested++; }
    static synchronized void incRingReject() { ringReject++; }
    static synchronized void incValidatorReject(HumanSpawnValidator.Reason reason)
    {
        validatorReject++;

        if (reason != null && reason.ordinal() < reasonRejects.length)
        {
            reasonRejects[reason.ordinal()]++;
        }
    }
    static synchronized void incSpawnFailed() { spawnFailed++; }
    static synchronized void incSpawnSuccess() { spawnSuccess++; }

    /** Immutable snapshot of all diagnostic counters. {@code population} is the human count of the last level tick. */
    public record SpawnDiagnostics(int tickSeen, int surfaceSpawnerCalled, int rulesNullSkip,
            int surfaceDisabledSkip, int dimensionReject, int tickIntervalSkip, int playerLoopEntries,
            int spectatorSkip, int chanceReject, int targetReachedSkip, int chunkNotLoadedSkip, int perChunkCapReject,
            int typePickNull, int localCapReject, int positionsTested, int ringReject, int validatorReject,
            int spawnFailed, int spawnSuccess, int population)
    {
    }
}
