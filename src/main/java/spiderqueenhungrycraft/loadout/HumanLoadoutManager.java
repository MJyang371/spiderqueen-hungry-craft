package spiderqueenhungrycraft.loadout;

import net.minecraft.util.RandomSource;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one place that hands out the loadout table (Phase HF0C).
 *
 * <p>The table itself is read from the data pack by {@link HumanLoadoutReloadListener}, so a pack author changes the
 * gear of the prepared humans by editing one json file - and can hot reload it with {@code /reload}. Until the first
 * load happened, or if no file could be read, the entity falls back to its own built-in loadout.
 */
public final class HumanLoadoutManager
{
    private static volatile HumanLoadoutTable table;

    private HumanLoadoutManager()
    {
    }

    /** @return the table of the last successful load, or null when nothing was loaded yet. */
    public static HumanLoadoutTable table()
    {
        return table;
    }

    /** Called by the reload listener with a freshly parsed table. */
    static void setTable(HumanLoadoutTable loaded)
    {
        table = loaded;

        if (loaded != null)
        {
            SpiderQueenHungryCraft.LOGGER.info("[Loadout] human equipment table loaded from {}", loaded.source());
        }
    }

    /**
     * @return a freshly rolled loadout of that tier, or an empty one when no table is available. The caller decides
     *         what to do with an empty loadout, the entity keeps its built-in gear in that case.
     */
    public static HumanLoadout roll(HumanTier tier, RandomSource random)
    {
        HumanLoadoutTable current = table;
        return current == null ? null : current.roll(tier, random);
    }
}
