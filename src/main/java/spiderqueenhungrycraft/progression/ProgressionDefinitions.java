package spiderqueenhungrycraft.progression;

import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The progression definition of the current logical side (Progression Foundation round).
 *
 * <p>It is one static holder instead of a field on every caller, so a {@code /reload} replaces the numbers in exactly
 * one place and every rule of the mod reads the new ones from the next call on.
 *
 * <p><b>The server is the only authority.</b> A client never asks this class for the rules of the world it is
 * playing in: it may have a data pack of its own that says something entirely different. What a client draws comes
 * from the snapshot the server sends when the growth interface is opened, so the client side of this holder is only
 * ever the local file, never the rules that are being enforced.
 */
public final class ProgressionDefinitions
{
    /** The definition in force right now; the official numbers until a data pack replaces them. */
    private static volatile ProgressionDefinition current = ProgressionDefinition.DEFAULT;

    private ProgressionDefinitions()
    {
    }

    /** @return the definition of this logical side. */
    public static ProgressionDefinition current()
    {
        return current;
    }

    /** Replaces the definition, refusing anything that is not usable so a broken file can not take a world down. */
    public static void set(ProgressionDefinition definition)
    {
        if (definition == null || !definition.isUsable())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Progression] refused an unusable definition, keeping {}",
                    current);
            return;
        }

        current = definition;
        SpiderQueenHungryCraft.LOGGER.info("[Progression] definition is now max_level={}, base_cost={},"
                + " levels_per_tier={}, tier_increment={}", definition.maxLevel(), definition.baseCost(),
                definition.levelsPerTier(), definition.tierIncrement());
    }

    /** Puts the official numbers back, used when a world has no progression file at all. */
    public static void reset()
    {
        current = ProgressionDefinition.DEFAULT;
    }
}
