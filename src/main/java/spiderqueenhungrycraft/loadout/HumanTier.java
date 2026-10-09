package spiderqueenhungrycraft.loadout;

import javax.annotation.Nullable;

/**
 * The three prepared human tiers of Phase HF0C.
 *
 * <p>The id is the key inside the json loadout table, the weight key is the short form the item weights use
 * ({@code low} / {@code mid} / {@code high}), exactly as the data file is written.
 */
public enum HumanTier
{
    LOW("low_prepared_human", "low"),
    MID("mid_prepared_human", "mid"),
    HIGH("high_prepared_human", "high");

    private final String id;
    private final String weightKey;

    HumanTier(String id, String weightKey)
    {
        this.id = id;
        this.weightKey = weightKey;
    }

    /** @return the json key of this tier, also the value that is saved to the nbt of a combatant. */
    public String id()
    {
        return this.id;
    }

    /** @return the short key the item weights of this tier are written under. */
    public String weightKey()
    {
        return this.weightKey;
    }

    /** @return the tier with that id, or null when the nbt holds something unknown. */
    @Nullable
    public static HumanTier byId(@Nullable String id)
    {
        if (id != null)
        {
            for (HumanTier tier : values())
            {
                if (tier.id.equals(id))
                {
                    return tier;
                }
            }
        }

        return null;
    }
}
