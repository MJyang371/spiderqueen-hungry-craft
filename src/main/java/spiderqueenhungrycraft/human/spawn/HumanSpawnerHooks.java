package spiderqueenhungrycraft.human.spawn;

/**
 * Seam interface for counting and probing the spawner path (Phase HF0D).
 *
 * <p>Every call into {@link HumanSurfaceSpawner} goes through a hooks object so that the
 * production path can accumulate counters in {@link HumanSpawnEvents} while a test seam
 * can assert conditions directly without spinning up a real server.
 */
public final class HumanSpawnerHooks
{
    private HumanSpawnerHooks() {}

    /** Production hooks that delegate to {@link HumanSpawnEvents} counters. */
    private static final SurfaceHooks SURFACE = new SurfaceHooks()
    {
        @Override public void onSurfaceSpawnerCalled() { HumanSpawnEvents.incSurfaceSpawnerCalled(); }
        @Override public void onRulesNullSkip() { HumanSpawnEvents.incRulesNullSkip(); }
        @Override public void onSurfaceDisabledSkip() { HumanSpawnEvents.incSurfaceDisabledSkip(); }
        @Override public void onDimensionReject() { HumanSpawnEvents.incDimensionReject(); }
        @Override public void onTickIntervalSkip() { HumanSpawnEvents.incTickIntervalSkip(); }
        @Override public void onPlayerLoopEntries() { HumanSpawnEvents.incPlayerLoopEntries(); }
        @Override public void onSpectatorSkip() { HumanSpawnEvents.incSpectatorSkip(); }
        @Override public void onChanceReject() { HumanSpawnEvents.incChanceReject(); }
        @Override public void onTargetReachedSkip() { HumanSpawnEvents.incTargetReachedSkip(); }
        @Override public void onChunkNotLoadedSkip() { HumanSpawnEvents.incChunkNotLoadedSkip(); }
        @Override public void onPerChunkCapReject() { HumanSpawnEvents.incPerChunkCapReject(); }
        @Override public void onTypePickNull() { HumanSpawnEvents.incTypePickNull(); }
        @Override public void onLocalCapReject() { HumanSpawnEvents.incLocalCapReject(); }
        @Override public void onPositionTested() { HumanSpawnEvents.incPositionTested(); }
        @Override public void onRingReject() { HumanSpawnEvents.incRingReject(); }
        @Override public void onValidatorReject(HumanSpawnValidator.Reason reason) { HumanSpawnEvents.incValidatorReject(reason); }
        @Override public void onSpawnFailed() { HumanSpawnEvents.incSpawnFailed(); }
        @Override public void onSpawnSuccess() { HumanSpawnEvents.incSpawnSuccess(); }
    };

    /** @return the hooks object for the surface spawner path. */
    public static SurfaceHooks forSurface() { return SURFACE; }

    /** All the counter callbacks that the surface spawner may trigger. */
    public interface SurfaceHooks
    {
        void onSurfaceSpawnerCalled();
        void onRulesNullSkip();
        void onSurfaceDisabledSkip();
        void onDimensionReject();
        void onTickIntervalSkip();
        void onPlayerLoopEntries();
        void onSpectatorSkip();
        void onChanceReject();
        void onTargetReachedSkip();
        void onChunkNotLoadedSkip();
        void onPerChunkCapReject();
        void onTypePickNull();
        void onLocalCapReject();
        void onPositionTested();
        void onRingReject();
        void onValidatorReject(HumanSpawnValidator.Reason reason);
        void onSpawnFailed();
        void onSpawnSuccess();
    }

    /** A test hook that records counts without delegating to events. */
    public static final class TestHooks implements SurfaceHooks
    {
        public int surfaceSpawnerCalled;
        public int rulesNullSkip;
        public int surfaceDisabledSkip;
        public int dimensionReject;
        public int tickIntervalSkip;
        public int playerLoopEntries;
        public int spectatorSkip;
        public int chanceReject;
        public int targetReachedSkip;
        public int chunkNotLoadedSkip;
        public int perChunkCapReject;
        public int typePickNull;
        public int localCapReject;
        public int positionTested;
        public int ringReject;
        public int validatorReject;
        public final java.util.EnumMap<HumanSpawnValidator.Reason, Integer> validatorRejectReasons =
                new java.util.EnumMap<>(HumanSpawnValidator.Reason.class);
        public int spawnFailed;
        public int spawnSuccess;

        @Override public void onSurfaceSpawnerCalled() { surfaceSpawnerCalled++; }
        @Override public void onRulesNullSkip() { rulesNullSkip++; }
        @Override public void onSurfaceDisabledSkip() { surfaceDisabledSkip++; }
        @Override public void onDimensionReject() { dimensionReject++; }
        @Override public void onTickIntervalSkip() { tickIntervalSkip++; }
        @Override public void onPlayerLoopEntries() { playerLoopEntries++; }
        @Override public void onSpectatorSkip() { spectatorSkip++; }
        @Override public void onChanceReject() { chanceReject++; }
        @Override public void onTargetReachedSkip() { targetReachedSkip++; }
        @Override public void onChunkNotLoadedSkip() { chunkNotLoadedSkip++; }
        @Override public void onPerChunkCapReject() { perChunkCapReject++; }
        @Override public void onTypePickNull() { typePickNull++; }
        @Override public void onLocalCapReject() { localCapReject++; }
        @Override public void onPositionTested() { positionTested++; }
        @Override public void onRingReject() { ringReject++; }
        @Override public void onValidatorReject(HumanSpawnValidator.Reason reason)
        {
            validatorReject++;
            validatorRejectReasons.merge(reason, 1, Integer::sum);
        }
        @Override public void onSpawnFailed() { spawnFailed++; }
        @Override public void onSpawnSuccess() { spawnSuccess++; }

        public void reset() { surfaceSpawnerCalled = rulesNullSkip = surfaceDisabledSkip = dimensionReject = tickIntervalSkip = playerLoopEntries = spectatorSkip = chanceReject = targetReachedSkip = chunkNotLoadedSkip = perChunkCapReject = typePickNull = localCapReject = positionTested = ringReject = validatorReject = spawnFailed = spawnSuccess = 0; validatorRejectReasons.clear(); }
    }
}
