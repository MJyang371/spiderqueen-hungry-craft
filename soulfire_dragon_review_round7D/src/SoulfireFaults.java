package spiderqueenhungrycraft.soulfire;

/**
 * The fault injector of the contract of a soul fire dragon (round 7E, stage A3).
 *
 * <p><b>Why a class like this exists at all.</b> Round 7E asks for two failures to be provable, not argued:
 * <i>the new unit cannot be put into the world</i> and <i>the original remains cannot be laid back down</i>. Neither
 * can be provoked from the outside in a test - the first would need the mob cap to be full or a duplicate identity, the
 * second needs the other mod to refuse - so the two failure points are written as counters that a test may arm and
 * everything else leaves at zero.
 *
 * <p><b>It is inert in a real game.</b> Both counters start at zero and are only ever raised by a test; every
 * production path asks them once and gets "no failure" forever. The counters are not saved, not synced and not exposed
 * to a command: this is a seam, not a feature.
 */
public final class SoulfireFaults
{
    /** How many of the next spawns of a soul fire dragon must fail. */
    private static int spawnFailures;

    /** How many of the next restores of remains must fail. */
    private static int restoreFailures;

    private SoulfireFaults()
    {
    }

    /** Arms the injector: the next {@code times} attempts to put a risen unit into the world will fail. */
    public static void failNextSpawns(int times)
    {
        spawnFailures = Math.max(0, times);
    }

    /** Arms the injector: the next {@code times} attempts to lay remains back down will fail. */
    public static void failNextRestores(int times)
    {
        restoreFailures = Math.max(0, times);
    }

    /** Disarms both injectors, which is what a test does when it is done with them. */
    public static void clear()
    {
        spawnFailures = 0;
        restoreFailures = 0;
    }

    /** @return true when this spawn has to fail, and counts the failure off. */
    public static boolean takeSpawnFailure()
    {
        if (spawnFailures <= 0)
        {
            return false;
        }

        spawnFailures--;

        return true;
    }

    /** @return true when this restore has to fail, and counts the failure off. */
    public static boolean takeRestoreFailure()
    {
        if (restoreFailures <= 0)
        {
            return false;
        }

        restoreFailures--;

        return true;
    }
}
