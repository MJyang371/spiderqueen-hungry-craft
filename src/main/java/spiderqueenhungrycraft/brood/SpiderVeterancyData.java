package spiderqueenhungrycraft.brood;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;

/**
 * The veterancy of one spider of the brood (Brood Veterancy round): what it was worth when it was born, what it has
 * killed since, and what that makes it today.
 *
 * <p>It is deliberately a separate little record and not a handful of fields spread over the entity: it is the one
 * thing this feature owns. Ability data, faction data and order data are untouched by it, and this record is the only
 * place a rank is ever computed or changed.
 *
 * <h2>Base health</h2>
 * {@link #baseHealth()} is the maximum health the spider was <b>born</b> with, before any rank touched it - the unit
 * cost of a Command &amp; Conquer unit, so to speak. Every threshold is measured against it and never against the
 * health the spider has as a veteran, which is what makes a promotion something a spider earns once instead of having
 * to earn it again at every step of the ladder.
 *
 * <h2>Experience</h2>
 * A spider earns experience for one thing only: it killed something. The last blow counts, the whole credit goes to the
 * creature that struck it, and the amount is the maximum health of the victim at the moment it died. Damage dealt is
 * worth nothing, damage taken is worth nothing, and being nearby while somebody else kills something is worth nothing.
 * A victim that was captured in web and turned into a cocoon did not die at all, so it is worth nothing either.
 *
 * <h2>Ranks only ever rise</h2>
 * Nothing here can lower a rank: not death, not a wound, not a reload of the world. A spider that is promoted stays
 * promoted, which also means the rank is never recomputed downwards from a smaller number of kills.
 */
public final class SpiderVeterancyData
{
    /** The NBT key of this whole record. */
    public static final String KEY_VETERANCY = "SpiderVeterancy";

    private static final String KEY_BASE_HEALTH = "BaseHealth";
    private static final String KEY_KILL_EXPERIENCE = "KillExperience";
    private static final String KEY_RANK = "Rank";

    /** The maximum health this spider was born with, or 0 while it has not been measured yet. */
    private int baseHealth;

    /** Everything this spider has killed, counted as the maximum health of the victims. */
    private int killExperience;

    /** The rank this spider holds right now. */
    private SpiderRank rank = SpiderRank.DEFAULT;

    /** @return the maximum health this spider was born with, or 0 while it has not been measured yet. */
    public int baseHealth()
    {
        return this.baseHealth;
    }

    /** @return true once the health this spider was born with has been measured. */
    public boolean hasBaseHealth()
    {
        return this.baseHealth > 0;
    }

    /** @return the experience this spider has gathered, in the maximum health of its victims. */
    public int killExperience()
    {
        return this.killExperience;
    }

    /** @return the rank this spider holds right now. */
    public SpiderRank rank()
    {
        return this.rank;
    }

    /**
     * Records what this spider was worth when it was born. Called exactly once in its life, on the first tick it lives,
     * and never changed afterwards - not by a promotion and not by a reload.
     */
    public void setBaseHealth(int baseHealth)
    {
        if (!this.hasBaseHealth() && baseHealth > 0)
        {
            this.baseHealth = baseHealth;
        }
    }

    /**
     * Writes the kill of one creature into the record.
     *
     * @return true when this kill promoted the spider, so the caller can re-apply its attributes and celebrate once
     */
    public boolean addExperience(int experience)
    {
        if (experience <= 0)
        {
            return false;
        }

        this.killExperience += experience;

        SpiderRank earned = this.rankEarned();

        if (earned.ordinal() <= this.rank.ordinal())
        {
            return false;
        }

        this.rank = earned;

        return true;
    }

    /**
     * @return the rank that the kills of this spider are worth right now, which is never lower than the rank it holds
     *
     * <p>The two prices are the rule of the whole ladder and they live here, not in a caller: a veteran is worth three
     * times the health the spider was born with, an elite twice that. All of it is measured against
     * {@link #baseHealth()}, never against a health that a rank has already raised.
     */
    public SpiderRank rankEarned()
    {
        if (!this.hasBaseHealth())
        {
            return this.rank;
        }

        double price = (double) this.baseHealth;

        if ((double) this.killExperience >= price * SpiderRank.ELITE_KILLS)
        {
            return SpiderRank.ELITE;
        }

        if ((double) this.killExperience >= price * SpiderRank.VETERAN_KILLS)
        {
            return SpiderRank.VETERAN;
        }

        return SpiderRank.NORMAL;
    }

    /**
     * @return the experience a spider of that base health needs for that rank, 0 for {@link SpiderRank#NORMAL}.
     *
     * <p>It exists so that the rule of the ladder can be read in one place - by a probe or by a test - instead of being
     * copied into every caller.
     */
    public static int experienceFor(SpiderRank rank, int baseHealth)
    {
        return switch (rank)
        {
            case NORMAL -> 0;
            case VETERAN -> (int) Math.ceil((double) baseHealth * SpiderRank.VETERAN_KILLS);
            case ELITE -> (int) Math.ceil((double) baseHealth * SpiderRank.ELITE_KILLS);
        };
    }

    /** @return what the kill of that creature is worth: its maximum health at the moment it died. */
    public static int experienceFrom(LivingEntity victim)
    {
        return Math.max(0, (int) Math.round((double) victim.getMaxHealth()));
    }

    /** Writes the record into the save file, nested under its own key inside the tag of the spider. */
    public void save(CompoundTag tag)
    {
        CompoundTag data = new CompoundTag();
        data.putInt(KEY_BASE_HEALTH, this.baseHealth);
        data.putInt(KEY_KILL_EXPERIENCE, this.killExperience);
        data.putString(KEY_RANK, this.rank.id());
        tag.put(KEY_VETERANCY, data);
    }

    /** Reads the record back. A spider that was never saved keeps its default record. */
    public void load(CompoundTag tag)
    {
        if (!tag.contains(KEY_VETERANCY, CompoundTag.TAG_COMPOUND))
        {
            return;
        }

        CompoundTag data = tag.getCompound(KEY_VETERANCY);
        this.baseHealth = Math.max(this.baseHealth, data.getInt(KEY_BASE_HEALTH));
        this.killExperience = Math.max(this.killExperience, data.getInt(KEY_KILL_EXPERIENCE));

        // A rank that is read back can only ever go up as well: a save file can never demote a spider.
        SpiderRank stored = SpiderRank.byName(data.getString(KEY_RANK));

        if (stored.ordinal() > this.rank.ordinal())
        {
            this.rank = stored;
        }
    }
}
