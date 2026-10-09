package spiderqueenhungrycraft.hostility;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The grudge list of one human unit (human pack aggro round).
 *
 * <p>This is the half of the pack design that must never be dropped: whoever hit a human is <b>remembered</b>, even
 * while the pack is busy with somebody else. The list is written by {@link HumanProvocation} on every provocation and
 * read by {@code HumanCombatantTargeting}, which lets a human answer a remembered attacker even when the faction
 * matrix would not hand him that kind of creature at all - that is how a web shot of a spider, which is not a legal
 * target of a human by alignment, still ends with a whole group turning on that spider.
 *
 * <p><b>No manager, no map, no life cycle.</b> The list lives in the persistent data of the victim, exactly like
 * {@code FactionAggroMemory} of the faction layer, so it saves, loads and dies with the human that carries it. Three
 * entries at most, each with a score, a hit counter, an expiry and the tick of the last pack broadcast of that
 * attacker.
 *
 * <p>It is a memory of the <b>pack</b> layer and it decides nothing by itself: the target of a human is chosen by
 * {@link HumanProvocation} and by the target goals, and this class only answers what that human still holds against
 * whom.
 */
public final class HumanThreatMemory
{
    /** How many attackers one human remembers at once. The list keeps the three highest scores. */
    public static final int MAX_ENTRIES = 3;

    /** 400 ticks = 20 seconds, the same memory the faction layer uses. */
    public static final int MEMORY_TICKS = 400;

    private static final String TAG_ENTRIES = SpiderQueenHungryCraft.MODID + ":human_threats";
    private static final String TAG_UUID = "Uuid";
    private static final String TAG_SCORE = "Score";
    private static final String TAG_HITS = "Hits";
    private static final String TAG_UNTIL = "Until";
    private static final String TAG_BROADCAST = "Broadcast";

    private HumanThreatMemory()
    {
    }

    /**
     * Writes one attack into the list of that victim: the score is the higher of the two, the hit counter grows by
     * one and the memory runs for another {@link #MEMORY_TICKS} ticks.
     */
    public static void remember(LivingEntity victim, LivingEntity attacker, double score, long now)
    {
        if (victim == attacker || !attacker.isAlive())
        {
            return;
        }

        ListTag entries = entries(victim);
        CompoundTag found = findAny(victim, attacker);

        if (found == null)
        {
            found = new CompoundTag();
            found.putUUID(TAG_UUID, attacker.getUUID());
            found.putInt(TAG_HITS, 0);
            entries.add(found);
        }

        found.putDouble(TAG_SCORE, Math.max(found.getDouble(TAG_SCORE), score));
        found.putInt(TAG_HITS, Math.min(99, found.getInt(TAG_HITS) + 1));
        found.putLong(TAG_UNTIL, now + MEMORY_TICKS);
        trim(entries);
    }

    /** @return the score that victim holds against that attacker right now, 0 when the memory ran out or never was. */
    public static double scoreOf(LivingEntity victim, LivingEntity attacker, long now)
    {
        CompoundTag entry = find(victim, attacker, now);

        return entry == null ? 0.0D : entry.getDouble(TAG_SCORE);
    }

    /** @return how often that attacker hit that victim inside the current memory, 0 when it is not remembered. */
    public static int hitsOf(LivingEntity victim, LivingEntity attacker, long now)
    {
        CompoundTag entry = find(victim, attacker, now);

        return entry == null ? 0 : entry.getInt(TAG_HITS);
    }

    /** @return the game tick of the last pack broadcast of that attacker, or a value far in the past. */
    public static long lastBroadcastOf(LivingEntity victim, LivingEntity attacker)
    {
        CompoundTag entry = findAny(victim, attacker);

        return entry == null ? Long.MIN_VALUE / 2L : entry.getLong(TAG_BROADCAST);
    }

    /** Marks that the pack was told about that attacker, which is the throttle of the broadcast. */
    public static void markBroadcast(LivingEntity victim, LivingEntity attacker, long now)
    {
        CompoundTag entry = findAny(victim, attacker);

        if (entry != null)
        {
            entry.putLong(TAG_BROADCAST, now);
        }
    }

    /** Drops one attacker from the list, for example once a human has settled his fight with it. */
    public static void forget(LivingEntity victim, LivingEntity attacker)
    {
        ListTag entries = entries(victim);

        for (int i = entries.size() - 1; i >= 0; --i)
        {
            CompoundTag entry = entries.getCompound(i);

            if (entry.hasUUID(TAG_UUID) && entry.getUUID(TAG_UUID).equals(attacker.getUUID()))
            {
                entries.remove(i);
            }
        }
    }

    /** The raw list of that victim, created when it is not there yet. */
    private static ListTag entries(LivingEntity victim)
    {
        CompoundTag data = victim.getPersistentData();

        if (!data.contains(TAG_ENTRIES, Tag.TAG_LIST))
        {
            data.put(TAG_ENTRIES, new ListTag());
        }

        return data.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
    }

    /** @return the live entry of that attacker, or null when it is not remembered or its memory ran out. */
    @Nullable
    private static CompoundTag find(LivingEntity victim, LivingEntity attacker, long now)
    {
        CompoundTag entry = findAny(victim, attacker);

        return entry == null || entry.getLong(TAG_UNTIL) <= now ? null : entry;
    }

    /** @return the entry of that attacker, whether or not its memory already ran out. */
    @Nullable
    private static CompoundTag findAny(LivingEntity victim, LivingEntity attacker)
    {
        ListTag entries = entries(victim);

        for (int i = 0; i < entries.size(); ++i)
        {
            CompoundTag entry = entries.getCompound(i);

            if (entry.hasUUID(TAG_UUID) && entry.getUUID(TAG_UUID).equals(attacker.getUUID()))
            {
                return entry;
            }
        }

        return null;
    }

    /**
     * Keeps the list at {@link #MAX_ENTRIES}: the entry with the lowest score is dropped first, and a tie is settled
     * by the expiry, so the list is always the same for the same history and never grows inside a long fight.
     */
    private static void trim(ListTag entries)
    {
        while (entries.size() > MAX_ENTRIES)
        {
            int weakest = 0;

            for (int i = 1; i < entries.size(); ++i)
            {
                CompoundTag candidate = entries.getCompound(i);
                CompoundTag current = entries.getCompound(weakest);

                if (candidate.getDouble(TAG_SCORE) < current.getDouble(TAG_SCORE) - 0.001D
                        || (Math.abs(candidate.getDouble(TAG_SCORE) - current.getDouble(TAG_SCORE)) <= 0.001D
                                && candidate.getLong(TAG_UNTIL) < current.getLong(TAG_UNTIL)))
                {
                    weakest = i;
                }
            }

            entries.remove(weakest);
        }
    }
}
