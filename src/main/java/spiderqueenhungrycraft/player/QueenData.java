package spiderqueenhungrycraft.player;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * Player owned Spider Queen data.
 *
 * <p>It stores whether the player is the Spider Queen, the beast state of that player since the Queen Beast
 * form of phase 8A, and the silk of her web glands (feature SILK-CAPACITY): the ability resource that is the
 * queen's own, separate from the hunger bar which stays the energy of her body. The class is kept small and
 * self contained on purpose so later stages can extend it without touching the storage plumbing, and every
 * value in it belongs to exactly one player, never to an item.
 *
 * <p>Gameplay code never writes these fields directly: {@link QueenSilk} wraps the silk, so consuming,
 * restoring, clamping and synchronising the resource happen in one single place.
 */
public class QueenData
{
    /** The lowest Queen Level there is: every player starts at level one (Progression Foundation round). */
    public static final int MIN_QUEEN_LEVEL = 1;

    private static final String TAG_IS_QUEEN = "isQueen";
    private static final String TAG_BEAST = "BeastState";
    private static final String TAG_SILK = "SilkAmount";

    /** The souls of this player (Soul Foundation round): the very first RPG resource of the mod. */
    private static final String TAG_SOULS = "Souls";

    /** The permanent Queen Level of this player (Progression Foundation round). */
    private static final String TAG_QUEEN_LEVEL = "QueenLevel";

    /**
     * The skill nodes this player has bought (Skill Progression round). One entry per unlocked skill, stored as its
     * stable {@link ResourceLocation}.
     */
    private static final String TAG_UNLOCKED_SKILLS = "UnlockedSkills";

    private boolean isQueen = true;

    /** The one and only beast state of this player, on this logical side. */
    private final QueenBeastState beast = new QueenBeastState();

    /**
     * The silk in the glands of this queen (feature SILK-CAPACITY), between zero and
     * {@link QueenSilk#MAX_AMOUNT}. It is server authoritative and saved with the player, and it is
     * deliberately not part of the beast state: the two resources of a queen never share a field.
     *
     * <p>A player who has never held silk starts with full glands, which is also what an old save without
     * the field gets, so nobody loses a shot to an upgrade.
     */
    private int silkAmount = QueenSilk.MAX_AMOUNT;

    /**
     * The souls of this player (Soul Foundation V1): a resource of its own, deliberately unrelated to the vanilla
     * experience bar, to the item inventory and to the silk of the queen.
     *
     * <p>It is a {@code long} and not an int on purpose: the souls of a long lived queen may leave the range of an
     * int by a wide margin, and an overflow of a currency is the kind of bug that only shows itself after a wipe.
     * It is server authoritative and saved with the player, and gameplay code never writes it directly - see
     * {@code spiderqueenhungrycraft.soul.Souls}.
     */
    private long souls;

    /**
     * The permanent Queen Level of this player (Progression Foundation round), 1 to whatever the current progression
     * definition allows.
     *
     * <p>It is <b>permanent growth</b>: a death costs the souls of a life, never a level, and it is deliberately a
     * number of its own - it is not a level of the vanilla experience bar, it is not derived from one, and nothing in
     * this mod changes an attribute because of it. It only marks how far a queen has come, and later rounds hang the
     * purchase of skill nodes off the levels reached here.
     *
     * <p>The value is only ever clamped at the <b>bottom</b> ({@link #MIN_QUEEN_LEVEL}): a world whose progression
     * definition is lowered later must never silently erase a level a player already earned, so an existing level
     * above the current maximum simply stays.
     */
    private int queenLevel = MIN_QUEEN_LEVEL;

    /**
     * The spiders that march with this queen (Brood Selection + Escort Relocation round): one entry per spider whose
     * persistent order is FOLLOW, each remembering where it was last seen. It lives in the queen data on purpose - that
     * is the structure the game already saves, copies on a player clone and keeps across a logout.
     */
    private final FollowRoster followRoster = new FollowRoster();

    /**
     * The spiders this queen has selected (Brood Command Group round): the units her sceptre commands act on. It is
     * deliberately the same kind of little structure as the roster - player data, saved with the player, copied on a
     * player clone - so a later squadron round can put several of them side by side without moving anything.
     */
    private final BroodSelectionSet broodSelection = new BroodSelectionSet();

    /**
     * The skill nodes this queen has bought (Skill Progression round): one stable {@link ResourceLocation} per
     * unlocked skill, in the order they were bought.
     *
     * <p>It is <b>permanent growth</b> like the Queen Level, and it is deliberately a set of ids and not a pile of
     * booleans: a later round may add, rename or remove a node in a data pack without touching this field, and no
     * ability of the mod ever reads this field directly - every gate goes through
     * {@code spiderqueenhungrycraft.progression.QueenSkills}.
     *
     * <p>An empty set is what a new player has and what an old save without this field gets, which is exactly the
     * intended behaviour of this round: nobody is handed a skill for free, every ability has to be bought at a
     * territory core.
     */
    private final Set<ResourceLocation> unlockedSkills = new LinkedHashSet<>();

    /** The selected units of this queen, the only way anything gets hold of them. */
    public BroodSelectionSet broodSelection()
    {
        return this.broodSelection;
    }

    /** The followers of this queen, the only way anything gets hold of them. */
    public FollowRoster followRoster()
    {
        return this.followRoster;
    }

    public boolean isQueen()
    {
        return this.isQueen;
    }

    public void setQueen(boolean queen)
    {
        this.isQueen = queen;
    }

    /** The beast state of this player. The only way anything gets hold of it. */
    public QueenBeastState beast()
    {
        return this.beast;
    }

    /** @return the silk in the glands of this queen. */
    public int getSilkAmount()
    {
        return this.silkAmount;
    }

    /** Writes the amount, clamped into the allowed range. */
    public void setSilkAmount(int amount)
    {
        this.silkAmount = Mth.clamp(amount, 0, QueenSilk.MAX_AMOUNT);
    }

    /** @return the souls of this player, never negative. */
    public long getSouls()
    {
        return this.souls;
    }

    /** Writes the souls, clamped at zero: a currency can never be a debt in this system. */
    public void setSouls(long amount)
    {
        this.souls = Math.max(0L, amount);
    }

    /** @return the permanent Queen Level of this player, never below {@link #MIN_QUEEN_LEVEL}. */
    public int getQueenLevel()
    {
        return this.queenLevel;
    }

    /**
     * Writes the Queen Level.
     *
     * <p>Only the bottom is clamped. There is deliberately no upper clamp here: the maximum is a property of the
     * progression definition of the world and can be lowered by a pack author, and a level that was already earned
     * must never be taken away by that.
     */
    public void setQueenLevel(int level)
    {
        this.queenLevel = Math.max(MIN_QUEEN_LEVEL, level);
    }

    /** @return true when this queen really owns that skill. */
    public boolean isSkillUnlocked(ResourceLocation skill)
    {
        return skill != null && this.unlockedSkills.contains(skill);
    }

    /**
     * Adds one skill to the bought ones.
     *
     * @return true when it was really new, false when this queen already owned it - which is what stops a repeated
     *         request from being counted twice.
     */
    public boolean unlockSkill(ResourceLocation skill)
    {
        return skill != null && this.unlockedSkills.add(skill);
    }

    /** Takes one skill away, used by the administrator command and by nothing else. */
    public boolean revokeSkill(ResourceLocation skill)
    {
        return skill != null && this.unlockedSkills.remove(skill);
    }

    /** @return the bought skills of this queen, read only, in the order they were bought. */
    public Set<ResourceLocation> unlockedSkills()
    {
        return Collections.unmodifiableSet(this.unlockedSkills);
    }

    /**
     * Takes silk out of the glands.
     *
     * @return true when the amount was really paid, false when the glands hold less and nothing happened.
     */
    public boolean consumeSilk(int amount)
    {
        if (amount <= 0)
        {
            return true;
        }
        if (this.silkAmount < amount)
        {
            return false;
        }

        this.silkAmount -= amount;
        return true;
    }

    /** Adds silk to the glands, never above the maximum. */
    public void restoreSilk(int amount)
    {
        if (amount <= 0)
        {
            return;
        }

        setSilkAmount(this.silkAmount + amount);
    }

    /**
     * Copies the data of another instance into this one. Used when the player entity is cloned, for example
     * after death or a dimension change. The silk of the glands is copied with it, because a rebuilt entity
     * must not hand out free silk; a real death resets it afterwards, see {@code QueenDataEvents}.
     *
     * <p>The beast state is deliberately not copied here: {@code PlayerEvent.Clone} handles it, because a
     * death must never bring the beast form back to life.
     */
    public void copyFrom(QueenData other)
    {
        this.isQueen = other.isQueen;
        this.silkAmount = other.silkAmount;

        // The souls of the player (Soul Foundation V1) follow the entity. A death zeroes them in the death handler
        // before this copy runs, so what is copied after a death is exactly what the death left behind.
        this.souls = other.souls;

        // The Queen Level is permanent growth and travels with the entity like the souls do - a death never touches
        // it, and neither does a dimension change.
        this.queenLevel = other.queenLevel;

        // The bought skills are permanent growth as well: they follow the entity through a death, a respawn and a
        // dimension change exactly like the level does. Only the administrator command can take one away.
        this.unlockedSkills.clear();
        this.unlockedSkills.addAll(other.unlockedSkills);

        this.followRoster.copyFrom(other.followRoster);
        this.broodSelection.copyFrom(other.broodSelection);
    }

    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_IS_QUEEN, this.isQueen);
        tag.put(TAG_BEAST, this.beast.serializeNBT());
        tag.putInt(TAG_SILK, this.silkAmount);
        tag.putLong(TAG_SOULS, this.souls);
        tag.putInt(TAG_QUEEN_LEVEL, this.queenLevel);
        tag.put(TAG_UNLOCKED_SKILLS, writeSkills());
        tag.put(FollowRoster.TAG_KEY, this.followRoster.serializeNBT().getList(FollowRoster.TAG_KEY, Tag.TAG_COMPOUND));
        this.broodSelection.save(tag);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        // Data without the flag (for example from a save created before the flag existed) uses the
        // default, which is "this player is the Spider Queen".
        this.isQueen = !tag.contains(TAG_IS_QUEEN) || tag.getBoolean(TAG_IS_QUEEN);
        this.beast.deserializeNBT(tag.getCompound(TAG_BEAST));

        // A save written before the silk existed has no amount in it: those glands start full.
        setSilkAmount(tag.contains(TAG_SILK) ? tag.getInt(TAG_SILK) : QueenSilk.MAX_AMOUNT);

        // A save written before the souls existed starts with none of them, which is exactly what a new player has.
        setSouls(tag.contains(TAG_SOULS) ? tag.getLong(TAG_SOULS) : 0L);

        // And a save written before the Queen Level existed starts at level one (Progression Foundation round).
        setQueenLevel(tag.contains(TAG_QUEEN_LEVEL) ? tag.getInt(TAG_QUEEN_LEVEL) : MIN_QUEEN_LEVEL);

        // A save written before the skills existed has none of them: the added abilities of the mod are locked by
        // default on every old save, exactly as this round asks. Nothing is handed out for free here.
        readSkills(tag);

        // The followers of this queen (Brood Selection + Escort Relocation round). An older save simply has none.
        this.followRoster.deserializeNBT(tag);

        // The selected units of this queen (Brood Command Group round). An older save has no selection, which simply
        // means "nothing is commanded until she picks a group" - the state every queen starts a session in.
        this.broodSelection.load(tag);
    }

    /** @return the bought skills as a list of strings, the shape a save file can hold. */
    private ListTag writeSkills()
    {
        ListTag list = new ListTag();

        for (ResourceLocation skill : this.unlockedSkills)
        {
            list.add(StringTag.valueOf(skill.toString()));
        }

        return list;
    }

    /**
     * Reads the bought skills of a save.
     *
     * <p>Anything that is not a valid identity is skipped instead of throwing: a save that was edited or written by a
     * pack that no longer exists must not be able to keep a player out of their own world. A skill that no longer has
     * a definition stays in the set all the same - it is what the administrator command revoked, not this reader.
     */
    private void readSkills(CompoundTag tag)
    {
        this.unlockedSkills.clear();

        if (!tag.contains(TAG_UNLOCKED_SKILLS, Tag.TAG_LIST))
        {
            return;
        }

        ListTag list = tag.getList(TAG_UNLOCKED_SKILLS, Tag.TAG_STRING);

        for (int index = 0; index < list.size(); index++)
        {
            ResourceLocation skill = ResourceLocation.tryParse(list.getString(index));

            if (skill != null)
            {
                this.unlockedSkills.add(skill);
            }
        }
    }
}
