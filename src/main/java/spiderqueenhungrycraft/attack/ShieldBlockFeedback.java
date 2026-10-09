package spiderqueenhungrycraft.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one place a shield block of this mod is answered to the client (PvP round).
 *
 * <p><b>What the game really does.</b> A shield block is not a sound the mod plays: {@code LivingEntity#hurt} asks
 * {@code isDamageSourceBlocked}, fires {@link ShieldBlockEvent}, takes the damage off the hit and then broadcasts
 * entity event {@value #SHIELD_BLOCK_EVENT}. The client plays {@code SoundEvents.SHIELD_BLOCK} and the shield flash for
 * that event, and nothing else does. So the requirement of this round is exactly one broadcast per block, from whoever
 * answered it:
 *
 * <ul>
 * <li>a <b>damaging</b> hit (a sword, an arrow, a thrown trident) is blocked by the game itself, and the game
 * broadcasts the feedback. Nothing here does anything for those, which is what makes a double sound impossible,</li>
 * <li>a <b>harmless</b> impact - an egg, a snowball, a web shot of this mod - never reaches that path at all, because
 * the path begins with {@code damage > 0}. Nothing in the game can block them, so the body that answers them has to
 * broadcast the feedback itself: {@link #feedbackOnce},</li>
 * <li>and every answer goes through one tick guard, so two answers for one body in one tick can never become two
 * sounds.</li>
 * </ul>
 *
 * <p>The per body record lives in the persistent data of the body itself, so there is no static map to leak and a
 * GameTest can read what happened: {@link #feedbacks}, {@link #vanillaBlocks}, {@link #lastFeedbackTick}.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class ShieldBlockFeedback
{
    /** The entity event vanilla broadcasts when a shield really blocks a hit: the client turns it into sound and flash. */
    public static final byte SHIELD_BLOCK_EVENT = 29;

    private static final String TAG_FEEDBACK_TICK = SpiderQueenHungryCraft.MODID + ":shield_feedback_tick";
    private static final String TAG_FEEDBACK_COUNT = SpiderQueenHungryCraft.MODID + ":shield_feedback_count";
    private static final String TAG_VANILLA_TICK = SpiderQueenHungryCraft.MODID + ":shield_vanilla_tick";
    private static final String TAG_VANILLA_COUNT = SpiderQueenHungryCraft.MODID + ":shield_vanilla_count";

    private ShieldBlockFeedback()
    {
    }

    /**
     * The game blocked a hit with the shield of that body and is about to broadcast the vanilla feedback.
     *
     * <p>This listener exists for two reasons and does nothing to the hit: it records that the vanilla pipeline really
     * ran (the instrumentation of this round, see {@link #vanillaBlocks}), and it marks the feedback of this tick as
     * already played so no second one can follow from anywhere in this mod.
     */
    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event)
    {
        LivingEntity body = event.getEntity();

        if (body == null || body.level().isClientSide())
        {
            return;
        }

        CompoundTag data = body.getPersistentData();
        long now = body.level().getGameTime();

        data.putLong(TAG_VANILLA_TICK, now);
        data.putInt(TAG_VANILLA_COUNT, data.getInt(TAG_VANILLA_COUNT) + 1);

        // The game itself is the one that plays the feedback of a real block: right after this event it broadcasts
        // entity event 29 for every client, so this tick counts as a feedback that was really played.
        data.putLong(TAG_FEEDBACK_TICK, now);
        data.putInt(TAG_FEEDBACK_COUNT, data.getInt(TAG_FEEDBACK_COUNT) + 1);
    }

    /**
     * @return true when a shield block feedback was really broadcast by this call, which can happen at most once per
     *         body per tick - the guard that makes "one block, one sound" true even when two systems answer the same
     *         impact.
     */
    public static boolean feedbackOnce(LivingEntity body)
    {
        if (body == null || body.level().isClientSide())
        {
            return false;
        }

        CompoundTag data = body.getPersistentData();
        long now = body.level().getGameTime();

        if (data.contains(TAG_FEEDBACK_TICK) && data.getLong(TAG_FEEDBACK_TICK) == now)
        {
            return false;
        }

        data.putLong(TAG_FEEDBACK_TICK, now);
        data.putInt(TAG_FEEDBACK_COUNT, data.getInt(TAG_FEEDBACK_COUNT) + 1);

        if (body.level() instanceof ServerLevel level)
        {
            level.broadcastEntityEvent(body, SHIELD_BLOCK_EVENT);
        }

        return true;
    }

    /** @return true when the game itself blocked a hit of that body in this very tick. */
    public static boolean vanillaBlockedThisTick(LivingEntity body)
    {
        return body != null && blockTick(body, TAG_VANILLA_TICK) == body.level().getGameTime();
    }

    /** @return true when that body was blocked at all in this tick, whoever answered the impact. */
    public static boolean blockedThisTick(LivingEntity body)
    {
        return body != null && blockTick(body, TAG_FEEDBACK_TICK) == body.level().getGameTime();
    }

    /** @return how many shield block feedbacks were broadcast for that body since it spawned. */
    public static int feedbacks(LivingEntity body)
    {
        return body == null ? 0 : body.getPersistentData().getInt(TAG_FEEDBACK_COUNT);
    }

    /** @return how many hits the vanilla shield pipeline blocked for that body since it spawned. */
    public static int vanillaBlocks(LivingEntity body)
    {
        return body == null ? 0 : body.getPersistentData().getInt(TAG_VANILLA_COUNT);
    }

    /** @return the tick of the last feedback of that body, or -1 when it never had one. */
    public static long lastFeedbackTick(LivingEntity body)
    {
        return blockTick(body, TAG_FEEDBACK_TICK);
    }

    /** @return the tick of the last block the vanilla pipeline answered for that body, or -1 when there was none. */
    public static long lastVanillaBlockTick(LivingEntity body)
    {
        return blockTick(body, TAG_VANILLA_TICK);
    }

    private static long blockTick(LivingEntity body, String tag)
    {
        if (body == null)
        {
            return -1L;
        }

        CompoundTag data = body.getPersistentData();

        return data.contains(tag) ? data.getLong(tag) : -1L;
    }
}
