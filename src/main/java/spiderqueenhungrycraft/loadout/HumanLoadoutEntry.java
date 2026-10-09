package spiderqueenhungrycraft.loadout;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.item.Item;

/**
 * One row of the loadout table (Phase HF0C): an item, its score inside its pool, its optional slot and its weight for
 * each tier.
 *
 * <p>Everything about this entry comes from the json file, nothing about it is written in java.
 */
public final class HumanLoadoutEntry
{
    private final Item item;
    private final String slot;
    private final int score;
    private final int maxCount;
    private final Map<HumanTier, Integer> weights = new EnumMap<>(HumanTier.class);

    public HumanLoadoutEntry(Item item, String slot, int score, int maxCount)
    {
        this.item = item;
        this.slot = slot;
        this.score = score;
        this.maxCount = maxCount;
    }

    public Item item()
    {
        return this.item;
    }

    /** @return the armour slot this entry belongs to, or an empty string for everything without a slot. */
    public String slot()
    {
        return this.slot;
    }

    public int score()
    {
        return this.score;
    }

    public int maxCount()
    {
        return this.maxCount;
    }

    public void setWeight(HumanTier tier, int weight)
    {
        this.weights.put(tier, weight);
    }

    public int weight(HumanTier tier)
    {
        return this.weights.getOrDefault(tier, 0);
    }

    /**
     * @return true when this entry is offered to that tier, fits the remaining budget and matches the wanted slot.
     *         A weight of zero means "never for this tier", which is how the table keeps diamond out of the hands of
     *         a low prepared human.
     */
    public boolean fits(HumanTier tier, int budget, String wantedSlot)
    {
        return this.weight(tier) > 0 && this.score <= budget
                && (wantedSlot == null || wantedSlot.equals(this.slot));
    }
}
