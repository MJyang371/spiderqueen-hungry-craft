package spiderqueenhungrycraft.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The short memory of who hit whom (Phase HF1-VISIBLE, part K).
 *
 * <p>Neutral really means neutral: a monster that ignores the queen keeps ignoring her, but the moment she swings at
 * it, it is allowed to hit back for the next twenty seconds. The memory lives in the persistent data of the victim,
 * so it disappears with the creature and is never a long term reputation.
 *
 * <p>This replaces the older queen retaliation tracker of the mod, which stored a nearly identical thing for the
 * queen alone. One memory, one truth.
 */
public final class FactionAggroMemory
{
    /** 400 ticks = 20 seconds. */
    public static final int MEMORY_DURATION_TICKS = 400;

    private static final String TAG_ATTACKER = SpiderQueenHungryCraft.MODID + ":faction_attacker";
    private static final String TAG_UNTIL = SpiderQueenHungryCraft.MODID + ":faction_attack_until";

    private FactionAggroMemory()
    {
    }

    /** Records that the victim was hurt by that attacker. */
    public static void remember(LivingEntity victim, LivingEntity attacker)
    {
        if (!(victim instanceof Mob mob) || attacker == null || attacker == victim)
        {
            return;
        }

        CompoundTag data = mob.getPersistentData();
        data.putUUID(TAG_ATTACKER, attacker.getUUID());
        data.putLong(TAG_UNTIL, mob.level().getGameTime() + MEMORY_DURATION_TICKS);
    }

    /** @return true while the victim still remembers being hit by exactly that attacker. */
    public static boolean isAngryAt(LivingEntity victim, LivingEntity attacker)
    {
        if (!(victim instanceof Mob mob) || attacker == null)
        {
            return false;
        }

        CompoundTag data = mob.getPersistentData();

        if (!data.contains(TAG_UNTIL) || !data.hasUUID(TAG_ATTACKER)
                || !attacker.getUUID().equals(data.getUUID(TAG_ATTACKER)))
        {
            return false;
        }

        if (mob.level().getGameTime() >= data.getLong(TAG_UNTIL))
        {
            forget(mob);
            return false;
        }

        return true;
    }

    /** Drops the memory of that victim. */
    public static void forget(LivingEntity victim)
    {
        if (!(victim instanceof Mob mob))
        {
            return;
        }

        CompoundTag data = mob.getPersistentData();
        data.remove(TAG_ATTACKER);
        data.remove(TAG_UNTIL);
    }
}
