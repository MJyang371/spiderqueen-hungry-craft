package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The rank of a spider of the brood (Brood Veterancy round): the Command &amp; Conquer style ladder a unit climbs by
 * fighting.
 *
 * <p>There are exactly three steps, and the ladder is the whole ladder - there is no level number behind it and no
 * fourth step reserved for later. A rank says three things and nothing else:
 *
 * <ul>
 * <li>how much tougher, how much harder hitting and how much faster the spider is than the spider that was born
 * (multipliers applied to the attributes it was registered with),</li>
 * <li>whether it knits its wounds back together between fights (elite only),</li>
 * <li>and which icon the inspect HUD of the queen shows next to its name.</li>
 * </ul>
 *
 * <p>The multipliers are deliberately modest on speed and generous on life and damage: an elite spider is a veteran of
 * many fights, not a race car, and it is never faster at attacking - it still bites (or shoots) exactly as often as it
 * did at rank one.
 */
public enum SpiderRank
{
    /** A spider that was just born. No icon, no bonus, nothing to show. */
    NORMAL("normal", 1.0D, 1.0D, 1.0D, false, null),

    /** A spider that has fought its way up. */
    VETERAN("veteran", 1.2D, 1.25D, 1.1D, false, "textures/gui/spider_rank_veteran.png"),

    /** A spider that has survived a war. Wounds close by themselves. */
    ELITE("elite", 2.0D, 2.5D, 1.25D, true, "textures/gui/spider_rank_elite.png");

    /** All ranks, in the order of the ladder. */
    public static final SpiderRank[] VALUES = values();

    /** The rank every spider starts with. */
    public static final SpiderRank DEFAULT = NORMAL;

    /**
     * The veteran threshold, in "kills of something as tough as this spider itself".
     *
     * <p>The price of a promotion is always measured against the health the spider was <b>born</b> with
     * ({@link SpiderVeterancyData#baseHealth()}), never against the health it has as a veteran: a promotion is earned
     * once, and a veteran never has to earn itself twice.
     */
    public static final double VETERAN_KILLS = 3.0D;

    /** The elite threshold, in the same unit. Twice the veteran price. */
    public static final double ELITE_KILLS = 6.0D;

    /** How often an elite spider closes one point of damage: one point per second. */
    public static final int REGENERATION_INTERVAL_TICKS = 20;

    /** How much an elite spider heals per interval. */
    public static final float REGENERATION_AMOUNT = 1.0F;

    private final String id;
    private final double healthMultiplier;
    private final double damageMultiplier;
    private final double speedMultiplier;
    private final boolean regeneration;
    @Nullable
    private final String iconPath;

    SpiderRank(String id, double healthMultiplier, double damageMultiplier, double speedMultiplier, boolean regeneration,
            @Nullable String iconPath)
    {
        this.id = id;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.speedMultiplier = speedMultiplier;
        this.regeneration = regeneration;
        this.iconPath = iconPath;
    }

    /** @return the stable name of this rank, as it is written into the save file. */
    public String id()
    {
        return this.id;
    }

    /** @return the multiplier of the maximum health of this rank. */
    public double healthMultiplier()
    {
        return this.healthMultiplier;
    }

    /** @return the multiplier of the attack damage of this rank. */
    public double damageMultiplier()
    {
        return this.damageMultiplier;
    }

    /** @return the multiplier of the walking speed of this rank. */
    public double speedMultiplier()
    {
        return this.speedMultiplier;
    }

    /** @return true when a spider of this rank heals one point per second, whatever it is doing. */
    public boolean hasRegeneration()
    {
        return this.regeneration;
    }

    /** @return the icon the inspect HUD shows for this rank, or null for a rank that shows no icon at all. */
    @Nullable
    public ResourceLocation icon()
    {
        return this.iconPath == null ? null
                : ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, this.iconPath);
    }

    /** @return the rank of that id, {@link #DEFAULT} for an id this build does not know. */
    public static SpiderRank byId(int id)
    {
        return id < 0 || id >= VALUES.length ? DEFAULT : VALUES[id];
    }

    /** @return the rank of that name as it is written into the save file, {@link #DEFAULT} when it is unknown. */
    public static SpiderRank byName(String name)
    {
        for (SpiderRank rank : VALUES)
        {
            if (rank.id.equals(name))
            {
                return rank;
            }
        }

        return DEFAULT;
    }
}
