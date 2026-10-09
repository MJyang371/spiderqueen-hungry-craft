package spiderqueenhungrycraft.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import spiderqueenhungrycraft.player.QueenData;

/**
 * The progression definition of the server (Progression Foundation round): how far the main growth line goes and what
 * one step of it costs.
 *
 * <p>It is read from a data pack file ({@code data/<namespace>/progression/main.json}) and hot reloadable with
 * {@code /reload}, so a pack author moves the numbers without a mod update. All four numbers live here and nowhere
 * else: no rule of this mod writes {@code 200}, {@code 250}, {@code 20} or {@code 50} into an {@code if}.
 *
 * <p><b>Every cost is an integer number of souls.</b> No float is involved anywhere in the price of a level, and the
 * arithmetic is done in {@code long}, so a cost can never round and a balance can never overflow.
 */
public record ProgressionDefinition(int maxLevel, int baseCost, int levelsPerTier, int tierIncrement)
{
    /** The official numbers of this round, and the safe fallback as long as no file was read. */
    public static final int DEFAULT_MAX_LEVEL = 200;
    public static final int DEFAULT_BASE_COST = 250;
    public static final int DEFAULT_LEVELS_PER_TIER = 20;
    public static final int DEFAULT_TIER_INCREMENT = 50;

    /** The definition a world uses until its data pack says otherwise. */
    public static final ProgressionDefinition DEFAULT =
            new ProgressionDefinition(DEFAULT_MAX_LEVEL, DEFAULT_BASE_COST, DEFAULT_LEVELS_PER_TIER,
                    DEFAULT_TIER_INCREMENT);

    /**
     * The souls one upgrade <b>to</b> that level costs.
     *
     * <pre>
     * cost(targetLevel) = baseCost + floor((targetLevel - 1) / levelsPerTier) * tierIncrement
     * </pre>
     *
     * <p>With the official numbers: reaching 2 and reaching 20 both cost 250, reaching 21 costs 300, and every tier
     * of twenty levels adds fifty. Level one is where everybody starts, so reaching it costs nothing.
     *
     * @return the price in souls, never negative.
     */
    public long costToReach(int targetLevel)
    {
        if (targetLevel <= QueenData.MIN_QUEEN_LEVEL)
        {
            return 0L;
        }

        // The tier size is guarded here and not only at parse time: a definition that arrived over the wire is
        // rebuilt without validation on the client, and a division by zero must never be able to take a screen down.
        int perTier = Math.max(1, this.levelsPerTier);
        long tier = (long)(targetLevel - 1) / perTier;

        return (long)this.baseCost + tier * this.tierIncrement;
    }

    /**
     * @return true when this definition can be used at all: a level line of at least one level, a price that is not
     *         negative and a tier that holds at least one level.
     */
    public boolean isUsable()
    {
        return this.maxLevel >= QueenData.MIN_QUEEN_LEVEL && this.baseCost >= 0 && this.levelsPerTier >= 1
                && this.tierIncrement >= 0;
    }

    /**
     * Reads one definition out of a json object.
     *
     * <p>Every value is checked here, and anything that does not make sense throws with a message a pack author can
     * act on. The caller keeps the previous definition when it throws, so an unreadable file can never take a world
     * down - it only leaves the numbers as they were.
     *
     * @throws IllegalArgumentException when a value is missing of out of range.
     */
    public static ProgressionDefinition parse(JsonObject root)
    {
        int maxLevel = readInt(root, "max_level", DEFAULT_MAX_LEVEL);
        int baseCost = readInt(root, "base_cost", DEFAULT_BASE_COST);
        int levelsPerTier = readInt(root, "levels_per_tier", DEFAULT_LEVELS_PER_TIER);
        int tierIncrement = readInt(root, "tier_increment", DEFAULT_TIER_INCREMENT);

        if (maxLevel < QueenData.MIN_QUEEN_LEVEL)
        {
            throw new IllegalArgumentException("max_level must be at least " + QueenData.MIN_QUEEN_LEVEL);
        }
        if (baseCost < 0)
        {
            throw new IllegalArgumentException("base_cost must not be negative");
        }
        if (levelsPerTier < 1)
        {
            throw new IllegalArgumentException("levels_per_tier must be at least 1");
        }
        if (tierIncrement < 0)
        {
            throw new IllegalArgumentException("tier_increment must not be negative");
        }

        return new ProgressionDefinition(maxLevel, baseCost, levelsPerTier, tierIncrement);
    }

    /**
     * @return the integer of that key, or the fallback when it is absent.
     * @throws IllegalArgumentException when it is present but not an integer number.
     */
    private static int readInt(JsonObject root, String key, int fallback)
    {
        JsonElement element = root.get(key);

        if (element == null || element.isJsonNull())
        {
            return fallback;
        }

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            throw new IllegalArgumentException(key + " must be a whole number");
        }

        double value = element.getAsDouble();

        if (value != Math.floor(value))
        {
            throw new IllegalArgumentException(key + " must be a whole number");
        }

        return (int)value;
    }
}
