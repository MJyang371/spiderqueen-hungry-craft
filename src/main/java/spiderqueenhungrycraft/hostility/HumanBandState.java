package spiderqueenhungrycraft.hostility;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The battle state of one human unit (human pack aggro round): which enemy he is committed to, for how long, where he
 * fights from, and which squad he belongs to.
 *
 * <p><b>Nothing here is shared.</b> Every field is stored in the persistent data of the human itself, so there is no
 * band object, no map and no life cycle to manage: two humans of the same group only ever agree through the values
 * they were both given by {@link HumanProvocation}, and a human who dies, unloads or is saved takes his own state with
 * him. That is the whole reason this round has no manager.
 *
 * <p><b>Every timer here runs out.</b> The target lock and the peace after a fight both have a hard expiry, so even a
 * mistake in the logic can make a human quiet for at most one lock, never stuck for good.
 *
 * <p><b>The squad is prepared, not used yet.</b> {@code Squad} and {@code SquadLeader} are written and read here, an
 * empty squad meaning "no patrol". {@link HumanComrade} already answers the "is this one of mine" question with them,
 * so the patrol round can begin to hand out squad ids without touching a single line of the fight or of the pack
 * rules.
 *
 * <p>Everything is plain NBT under the mod id, which also means a data pack can write it from a summon: a patrol of a
 * pack author can set {@code ForgeData} without any java change.
 */
public final class HumanBandState
{
    private static final String TAG_SQUAD = SpiderQueenHungryCraft.MODID + ":human_squad";
    private static final String TAG_SQUAD_LEADER = SpiderQueenHungryCraft.MODID + ":human_squad_leader";
    private static final String TAG_TARGET = SpiderQueenHungryCraft.MODID + ":human_band_target";
    private static final String TAG_TARGET_UNTIL = SpiderQueenHungryCraft.MODID + ":human_band_until";
    private static final String TAG_PEACE_UNTIL = SpiderQueenHungryCraft.MODID + ":human_band_peace_until";
    private static final String TAG_RETURNING = SpiderQueenHungryCraft.MODID + ":human_band_returning";
    private static final String TAG_ANCHOR_SET = SpiderQueenHungryCraft.MODID + ":human_band_anchor_set";
    private static final String TAG_ANCHOR_X = SpiderQueenHungryCraft.MODID + ":human_band_anchor_x";
    private static final String TAG_ANCHOR_Y = SpiderQueenHungryCraft.MODID + ":human_band_anchor_y";
    private static final String TAG_ANCHOR_Z = SpiderQueenHungryCraft.MODID + ":human_band_anchor_z";

    private HumanBandState()
    {
    }

    // -----------------------------------------------------------------------------------------------------------
    // The squad of this human: prepared for the patrol round, empty until somebody hands one out.
    // -----------------------------------------------------------------------------------------------------------

    /** @return the squad id of that human, empty when it belongs to no squad. */
    public static String squadOf(Entity human)
    {
        return human.getPersistentData().getString(TAG_SQUAD);
    }

    /** Writes the squad id of that human. An empty id means "no squad", which is what every human starts with. */
    public static void setSquad(Entity human, String squadId)
    {
        human.getPersistentData().putString(TAG_SQUAD, squadId == null ? "" : squadId);
    }

    /** @return true while that human is the one the patrol round would treat as the leader of its squad. */
    public static boolean isSquadLeader(Entity human)
    {
        return human.getPersistentData().getBoolean(TAG_SQUAD_LEADER);
    }

    /** Marks or unmarks that human as the leader of its squad. Unused until the patrol round. */
    public static void setSquadLeader(Entity human, boolean leader)
    {
        human.getPersistentData().putBoolean(TAG_SQUAD_LEADER, leader);
    }

    // -----------------------------------------------------------------------------------------------------------
    // The target lock: the one enemy this human is committed to right now.
    // -----------------------------------------------------------------------------------------------------------

    /** @return the target this human is locked on, or null when he is free to pick one. */
    @Nullable
    public static UUID lockedTarget(LivingEntity human, long now)
    {
        CompoundTag data = human.getPersistentData();

        if (!data.hasUUID(TAG_TARGET) || data.getLong(TAG_TARGET_UNTIL) <= now)
        {
            return null;
        }

        return data.getUUID(TAG_TARGET);
    }

    /** @return true while that human holds that exact enemy as his locked target. */
    public static boolean isLockedOn(LivingEntity human, LivingEntity candidate, long now)
    {
        UUID locked = lockedTarget(human, now);

        return locked != null && locked.equals(candidate.getUUID());
    }

    /** Commits that human to that enemy for the given number of ticks. */
    public static void lock(LivingEntity human, LivingEntity target, long now, int ticks)
    {
        CompoundTag data = human.getPersistentData();
        data.putUUID(TAG_TARGET, target.getUUID());
        data.putLong(TAG_TARGET_UNTIL, now + Math.max(1, ticks));
    }

    /** Lets that human go: nothing of the current fight survives this call. */
    public static void releaseLock(LivingEntity human)
    {
        CompoundTag data = human.getPersistentData();
        data.remove(TAG_TARGET);
        data.remove(TAG_TARGET_UNTIL);
    }

    // -----------------------------------------------------------------------------------------------------------
    // The peace after a fight, the post a human fights from and the walk back to it.
    // -----------------------------------------------------------------------------------------------------------

    /** Starts the peace after a fight, during which only a remembered attacker may start a new one. */
    public static void markPeace(LivingEntity human, long now, int ticks)
    {
        human.getPersistentData().putLong(TAG_PEACE_UNTIL, now + Math.max(0, ticks));
    }

    /** @return true while that human has just finished a fight and looks for no new enemy on his own. */
    public static boolean inPeace(LivingEntity human, long now)
    {
        return human.getPersistentData().getLong(TAG_PEACE_UNTIL) > now;
    }

    /**
     * @return the post this human fights from, or null while he never stood anywhere in peace long enough. The post is
     *         what the leash is measured from, so it is the difference between a soldier who holds ground and a mob
     *         that follows an archer across the map.
     */
    @Nullable
    public static BlockPos anchor(LivingEntity human)
    {
        CompoundTag data = human.getPersistentData();

        if (!data.getBoolean(TAG_ANCHOR_SET))
        {
            return null;
        }

        return BlockPos.containing(data.getDouble(TAG_ANCHOR_X), data.getDouble(TAG_ANCHOR_Y),
                data.getDouble(TAG_ANCHOR_Z));
    }

    /** Sets the post of that human to where he stands right now. */
    public static void rememberAnchor(LivingEntity human)
    {
        CompoundTag data = human.getPersistentData();
        data.putBoolean(TAG_ANCHOR_SET, true);
        data.putDouble(TAG_ANCHOR_X, human.getX());
        data.putDouble(TAG_ANCHOR_Y, human.getY());
        data.putDouble(TAG_ANCHOR_Z, human.getZ());
    }

    /** @return true while that human is walking back to his post after being dragged out of it. */
    public static boolean isReturning(LivingEntity human)
    {
        return human.getPersistentData().getBoolean(TAG_RETURNING);
    }

    /** Marks or unmarks that human as walking home. */
    public static void setReturning(LivingEntity human, boolean returning)
    {
        human.getPersistentData().putBoolean(TAG_RETURNING, returning);
    }
}
