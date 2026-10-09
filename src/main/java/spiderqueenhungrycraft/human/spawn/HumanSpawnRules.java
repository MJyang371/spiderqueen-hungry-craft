package spiderqueenhungrycraft.human.spawn;

import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one place that hands out the spawn rules (Phase HF0D).
 *
 * <p>The rules are read from the data pack by {@link HumanSpawnReloadListener}, so a pack author changes the spawn
 * behaviour of the humans by editing one json file - and can hot reload it with {@code /reload}. Until the first load
 * happened, or if no file could be read, nothing spawns at all, which is the safe default.
 */
public final class HumanSpawnRules
{
    private static volatile HumanSpawnConfig config;

    private HumanSpawnRules()
    {
    }

    /** @return the rules of the last successful load, or null when nothing was loaded yet. */
    public static HumanSpawnConfig config()
    {
        return config;
    }

    /** Called by the reload listener with a freshly parsed configuration. */
    static void setConfig(HumanSpawnConfig loaded)
    {
        config = loaded;

        if (loaded == null)
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[Spawn] human spawn rules loaded from {}", loaded.source());

        // The numbers a player tunes when the humans feel too many or too few. Every one of them comes out of the json
        // and none of them is hard coded, so printing them at every load - world start and /reload - is what answers
        // "did the file I edited really take effect?" with one look in the log.
        HumanSpawnConfig.SurfaceRules surface = loaded.surface();

        if (surface == null)
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info(
                "[Spawn] surface spawn: one attempt every {} ticks ({} per second) with chance {} by day and {} by night,"
                        + " aiming at {}/{} humans in the ring, at most {} human(s) per chunk, {}",
                surface.attemptIntervalTicks(), 20.0F / Math.max(1, surface.attemptIntervalTicks()), surface.dayChance(),
                surface.nightChance(), surface.dayTarget(), surface.nightTarget(), surface.perChunkCap(),
                surface.localCap().enabled()
                        ? "and at most " + surface.localCap().maxHumans() + " humans within "
                                + (int) surface.localCap().radius() + " blocks"
                        : "and no local cap");
    }
}
