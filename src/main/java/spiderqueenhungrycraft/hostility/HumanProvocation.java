package spiderqueenhungrycraft.hostility;

import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ai.HumanCombatBrain;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.entity.HumanCombatantTargeting;
import spiderqueenhungrycraft.human.HumanEntityTypes;

/**
 * The shared anger of the human units (human pack aggro round, modelled on the anger of the piglins).
 *
 * <p>One human is hit and the group answers: that is the whole idea, and this class is the one place that turns an
 * attack into it. It is written the <b>centerless</b> way on purpose. There is no band object, no map and no
 * membership list; every decision is taken by the human who takes it, from the state in his own persistent data
 * ({@link HumanBandState}, {@link HumanThreatMemory}). A provocation only <b>writes memory</b> into the humans around
 * the victim and hands the quota of them an enemy to commit to. If a broadcast is late, lost or repeated, the worst
 * outcome is one human who is a moment behind - never a group that is stuck, and never a state nobody owns.
 *
 * <p><b>The five rules that keep a pack from being pulled back and forth by two archers:</b>
 * <ol>
 * <li><b>Memory, not a target.</b> Every hit writes a grudge into the victim and into every comrade in reach
 * ({@link HumanThreatMemory}), so nobody is ever forgotten - but a grudge alone never changes a target.</li>
 * <li><b>Quota.</b> Only the nearest {@code human_band_max_responders} comrades are <i>armed</i> with the enemy; the
 * rest are only alerted (they turn and raise the shield) and stay where they are. Ten humans are never dragged away by
 * one arrow.</li>
 * <li><b>Lock.</b> An armed human commits to that enemy for {@code human_band_lock_ticks}; while the lock runs, a new
 * provocation is recorded and does not move him. Nothing is permanent: the lock always runs out.</li>
 * <li><b>Margin.</b> The victim himself, and only he, may switch to a new attacker while locked - and only when the
 * new threat beats the current one by {@code human_band_switch_margin}, or when the new attacker is a melee attacker
 * in his face (see below).</li>
 * <li><b>Throttle.</b> The same attacker may not broadcast to the pack more than once per
 * {@code human_band_provoke_cooldown_ticks}, so a volley is one event and not ten.</li>
 * </ol>
 *
 * <p><b>The one forced interrupt.</b> A <b>melee</b> attacker that reaches a human is answered at once, lock or no
 * lock: whoever is in your face is not a distraction you may ignore, and unlike an archer he can not walk away from the
 * answer. Nothing else breaks a lock, which is exactly the difference between a soldier and a weathervane.
 *
 * <p>Ranged attackers are deliberately the cheapest threat: an arrow or a web shot from afar is remembered and
 * answered when the human is free, and {@code human_band_answer_projectiles} can switch even that off for a pack that
 * wants its soldiers to hold the line against archers instead.
 *
 * <p><b>Isolation.</b> This is the human side of the mod and it touches nothing of the brood: a spider is still blamed
 * by {@code SpiderProvocation} and the pack rules of the queen side are unchanged. The two fights stay two fights.
 */
public final class HumanProvocation
{
    /** Diagnostic switch of this round: one line per decision, never one per tick. */
    public static final boolean DEBUG = true;

    /**
     * How much closer than the melee reach a melee attacker has to be to count as "in the face". It only ever widens
     * the forced answer by a fraction of a block, so a hit that landed can never be missed by a rounding error.
     */
    private static final double FORCED_MELEE_MARGIN = 0.75D;

    /** What moved a human to anger. Only the melee case can break a lock. */
    public enum Cause
    {
        MELEE_OR_DAMAGE,
        PROJECTILE,
        WEB_SHOT
    }

    private HumanProvocation()
    {
    }

    /**
     * The one entry point: that attacker just hit that human. It writes the grudge, lets the victim answer and tells
     * the pack about it.
     */
    public static void provoke(LivingEntity victim, LivingEntity attacker, Cause cause)
    {
        if (victim.level().isClientSide() || !HungryCraftConfig.humanBandAggro())
        {
            return;
        }

        if (attacker == null || attacker == victim || attacker.isRemoved() || !attacker.isAlive())
        {
            return;
        }

        if (!HumanEntityTypes.isHumanMob(victim))
        {
            return;
        }

        // Our own are never blamed: a human does not wake the pack against a human of his own group, however hard the
        // two of them may be fighting. This is also the gate that keeps a squad from ever turning on itself.
        if (HumanComrade.isComrade(victim, attacker))
        {
            return;
        }

        long now = victim.level().getGameTime();
        double score = scoreOf(victim, attacker, cause);

        HumanThreatMemory.remember(victim, attacker, score, now);
        victim.setLastHurtByMob(attacker);

        answerFor(victim, attacker, cause, score, now);
        broadcast(victim, attacker, cause, now);
    }

    /**
     * @return the threat of that attacker for that human right now: how close it is, whether it is in his face, what
     *         kind of attack it was, how often it already hit him and whether he is already fighting it.
     *
     * <p>A null cause means "ask without an attack", which is how the score of the <b>current</b> target is taken when
     * the two are compared. The situational half is recomputed every time, the historical half comes from
     * {@link HumanThreatMemory}, so a score can never inflate itself.
     */
    public static double scoreOf(LivingEntity victim, LivingEntity attacker, @Nullable Cause cause)
    {
        long now = victim.level().getGameTime();
        double radius = Math.max(1.0D, HungryCraftConfig.humanBandRadius());
        double distance = victim.distanceTo(attacker);
        double proximity = 40.0D * (1.0D - Math.min(1.0D, distance / radius));
        double inTheFace = distance <= HumanCombatBrain.MELEE_HIT_REACH + FORCED_MELEE_MARGIN ? 100.0D : 0.0D;
        double causeBonus = cause == Cause.MELEE_OR_DAMAGE ? 30.0D : cause == Cause.WEB_SHOT ? 15.0D : 10.0D;
        double hits = 20.0D * (double) Math.min(3, HumanThreatMemory.hitsOf(victim, attacker, now));
        double engaged = victim instanceof Mob mob && mob.getTarget() == attacker ? 25.0D : 0.0D;

        return proximity + inTheFace + causeBonus + hits + engaged;
    }

    /**
     * The decision of the victim himself: keep fighting what he fights, or turn on the one that just hit him. This is
     * where the margin rule and the one forced interrupt live.
     */
    private static void answerFor(LivingEntity victim, LivingEntity attacker, Cause cause, double score, long now)
    {
        if (!(victim instanceof Mob mob))
        {
            return;
        }

        LivingEntity current = mob.getTarget();
        boolean forced = cause == Cause.MELEE_OR_DAMAGE
                && victim.distanceTo(attacker) <= HumanCombatBrain.MELEE_HIT_REACH + FORCED_MELEE_MARGIN;

        // A human on his way back to his post does not turn around for a new enemy: he remembers the hit (the grudge is
        // already written above) and keeps walking. Without this the leash would only hold until the next arrow.
        if (HumanBandState.isReturning(victim) && current == null)
        {
            log(victim, "records {} but keeps walking back to the post (cause {})", name(attacker), cause);
            return;
        }

        if (current == attacker)
        {
            HumanBandState.lock(victim, attacker, now, HungryCraftConfig.humanBandLockTicks());
            log(victim, "holds {} (score {})", name(attacker), score);
            return;
        }

        double currentScore = current == null ? 0.0D : scoreOf(victim, current, null);

        if (current == null || forced
                || score >= currentScore * HungryCraftConfig.humanBandSwitchMargin())
        {
            mob.setTarget(attacker);
            HumanBandState.lock(victim, attacker, now, HungryCraftConfig.humanBandLockTicks());
            log(victim, "switch to {} (cause {}, score {}, was {} {}, forced {})", name(attacker), cause, score,
                    name(current), currentScore, forced);
            return;
        }

        log(victim, "kept {} (new {} {}, kept {}, not forced)", name(current), name(attacker), score, currentScore);
    }

    /**
     * Tells the pack. Only the nearest quota of the comrades in reach is <b>armed</b> with the enemy; everybody else
     * is alerted - he turns, he raises the shield, he remembers the attacker - and holds his ground. Both groups get
     * the grudge, so whoever of them is attacked next may answer that attacker on his own.
     */
    private static void broadcast(LivingEntity victim, LivingEntity attacker, Cause cause, long now)
    {
        if (!(victim.level() instanceof ServerLevel level))
        {
            return;
        }

        if ((cause == Cause.PROJECTILE || cause == Cause.WEB_SHOT)
                && !HungryCraftConfig.humanBandAnswerProjectiles())
        {
            return;
        }

        if (now - HumanThreatMemory.lastBroadcastOf(victim, attacker)
                < HungryCraftConfig.humanBandProvokeCooldownTicks())
        {
            log(victim, "no broadcast for {} (throttled, cause {})", name(attacker), cause);
            return;
        }

        HumanThreatMemory.markBroadcast(victim, attacker, now);

        double radius = Math.max(1.0D, HungryCraftConfig.humanBandRadius());
        AABB area = victim.getBoundingBox().inflate(radius);
        List<LivingEntity> comrades = level.getEntitiesOfClass(LivingEntity.class, area,
                candidate -> candidate != victim && candidate.isAlive() && !candidate.isRemoved()
                        && HumanComrade.isComrade(victim, candidate));

        // Deterministic order: nearest first, a tie settled by the uuid. The same fight always arms the same humans,
        // which is what lets the smoke of this round assert anything at all.
        comrades.sort(Comparator.comparingDouble((LivingEntity comrade) -> comrade.distanceToSqr(victim))
                .thenComparing(comrade -> comrade.getUUID().toString()));

        int quota = Math.max(0, HungryCraftConfig.humanBandMaxResponders());
        int armed = 0;
        int alerted = 0;

        for (LivingEntity comrade : comrades)
        {
            HumanThreatMemory.remember(comrade, attacker, scoreOf(comrade, attacker, cause), now);
            comrade.setLastHurtByMob(attacker);

            if (armed >= quota || HumanBandState.lockedTarget(comrade, now) != null)
            {
                ++alerted;
                continue;
            }

            HumanBandState.lock(comrade, attacker, now, HungryCraftConfig.humanBandLockTicks());

            if (comrade instanceof Mob mob && HumanCombatantTargeting.isTargetStillAllowed(comrade, attacker))
            {
                mob.setTarget(attacker);
                ++armed;
            }
            else
            {
                ++alerted;
            }
        }

        log(victim, "broadcast {} to the pack: {} of {} comrades armed, {} alerted (cause {})", name(attacker), armed,
                comrades.size(), alerted, cause);
    }

    /** @return the name of that creature for the log, "none" when there is none. */
    private static String name(@Nullable LivingEntity entity)
    {
        return entity == null ? "none" : entity.getName().getString();
    }

    private static void log(LivingEntity human, String message, Object... arguments)
    {
        if (DEBUG)
        {
            SpiderQueenHungryCraft.LOGGER.info("[Band/" + human.getStringUUID() + "] " + message, arguments);
        }
    }
}
