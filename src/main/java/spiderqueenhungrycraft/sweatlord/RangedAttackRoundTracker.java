package spiderqueenhungrycraft.sweatlord;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The ranged rounds of the Sweatlord and the adaptation they carry (PvP round).
 *
 * <p><b>A round and not a projectile.</b> One bow shot, one trident throw and one whole egg burst are each <b>one</b>
 * ranged attack round. The difference matters for exactly one number: a burst of seven eggs in which a single egg
 * reached the enemy is a round that hit, and one of them is not a miss because the other six were. Counting projectiles
 * would turn one bad burst into seven misses and press the panic button three times in a second.
 *
 * <p><b>Hits are observed and never assumed.</b> Nothing here guesses that a shot "should" land: every projectile a
 * round fired is watched until it is really gone (it hit something, it hit a wall, it expired), and the round is judged
 * on where it was when it left the world - <b>inside the enemy box</b> means the shot arrived, whatever it dealt (an
 * egg deals no damage at all and still counts).
 *
 * <p><b>Misses are per enemy.</b> A streak belongs to the enemy it was built against: two misses against one body and
 * then a change of enemy starts the new enemy at zero, so a pearl is never thrown at somebody because of somebody else.
 * An enemy that dies, is removed or is simply no longer the target invalidates its open round instead of counting it as
 * a miss, and the counter is reset when the fight ends.
 *
 * <p><b>What the number is for.</b> {@link #MISSES_FOR_FORCED_PEARL} full rounds in a row that never arrived is the
 * moment the ranged plan has clearly failed, and the brain answers it with a pearl into melee
 * ({@code SweatlordCombatBrain#forcePearlGapClose}) instead of a fourth round that would miss as well.
 */
public final class RangedAttackRoundTracker
{
    /** How many missed ranged rounds in a row turn "keep shooting" into "get in there". */
    public static final int MISSES_FOR_FORCED_PEARL = 3;

    /** How long a round may stay open before it is judged on what it has: nothing flies for two seconds. */
    public static final int ROUND_TIMEOUT_TICKS = 120;

    /** How much the projectile box is grown when it is asked whether it arrived inside the enemy. */
    private static final double HIT_MARGIN = 0.35D;

    /** Which action fired the round, for the debug output and the tests. */
    public enum Kind
    {
        BOW,
        TRIDENT,
        EGG_BURST
    }

    /** One projectile of a round. */
    private static final class Shot
    {
        @Nullable
        private final Entity projectile;
        private boolean resolved;

        private Shot(@Nullable Entity projectile)
        {
            this.projectile = projectile;
        }
    }

    /** One ranged attack round: what it was fired at, how many shots it owes and what happened to them. */
    private static final class Round
    {
        private final int id;
        @Nullable
        private final LivingEntity target;
        private final UUID targetId;
        private final Kind kind;
        private final int expected;
        private final long startTick;
        private final List<Shot> shots = new ArrayList<>();
        private int resolved;
        private boolean hit;

        private Round(int id, LivingEntity target, Kind kind, int expected, long startTick)
        {
            this.id = id;
            this.target = target;
            this.targetId = target.getUUID();
            this.kind = kind;
            this.expected = expected;
            this.startTick = startTick;
        }
    }

    private final List<Round> open = new ArrayList<>();
    private int nextId;
    private int hits;
    private int misses;

    @Nullable
    private UUID streakTarget;

    @Nullable
    private UUID lastRoundTarget;
    private Kind lastRoundKind = Kind.BOW;
    private boolean lastRoundHit;
    private boolean lastRoundInvalidated;

    /** @return the number of full ranged rounds in a row that missed the enemy it was fired at. */
    public int missStreak()
    {
        return this.misses;
    }

    /** @return how many ranged rounds really reached their enemy. */
    public int hits()
    {
        return this.hits;
    }

    /** @return how many rounds are still being watched (projectiles in the air and not resolved yet). */
    public int openRounds()
    {
        return this.open.size();
    }

    /** @return one short line for the debug state. */
    public String describe()
    {
        return "rounds=" + this.hits + " hit/" + this.misses + " missed streak, open=" + this.open.size();
    }

    /** @return true when the enemy the streak belongs to is still the one being fought. */
    public boolean streakTargetIs(LivingEntity target)
    {
        return target != null && target.getUUID().equals(this.streakTarget);
    }

    /**
     * Opens a round and returns its id, which is what {@link #registerProjectile} needs while the shots are coming.
     *
     * @param expected how many projectiles this round is going to fire (one arrow, one trident, five to seven eggs).
     */
    public int beginRound(@Nullable LivingEntity target, Kind kind, int expected, long now)
    {
        if (target == null)
        {
            return 0;
        }

        Round round = new Round(++this.nextId, target, kind, Math.max(1, expected), now);
        this.open.add(round);
        this.lastRoundTarget = round.targetId;
        this.lastRoundKind = kind;
        this.lastRoundHit = false;
        this.lastRoundInvalidated = false;

        return round.id;
    }

    /** Registers one really spawned projectile of that round, which is what makes a hit observable later. */
    public void registerProjectile(int roundId, @Nullable Entity projectile)
    {
        if (roundId <= 0)
        {
            return;
        }

        for (Round round : this.open)
        {
            if (round.id == roundId)
            {
                round.shots.add(new Shot(projectile));

                return;
            }
        }
    }

    /**
     * One tick of bookkeeping: resolves every projectile that is really gone, judges the rounds that are complete, and
     * drops the rounds whose enemy died or was changed.
     */
    public void tick(SweatlordSteveEntity mob)
    {
        if (this.open.isEmpty())
        {
            return;
        }

        long now = mob.level().getGameTime();

        for (Iterator<Round> iterator = this.open.iterator(); iterator.hasNext();)
        {
            Round round = iterator.next();
            boolean invalid = this.targetInvalid(mob, round);

            for (Shot shot : round.shots)
            {
                if (shot.resolved || !this.isGone(shot.projectile, round, now))
                {
                    continue;
                }

                shot.resolved = true;
                ++round.resolved;

                if (!invalid && this.reached(shot.projectile, round.target))
                {
                    round.hit = true;
                }
            }

            if (round.resolved >= round.expected || now - round.startTick > ROUND_TIMEOUT_TICKS)
            {
                this.close(round, invalid);
                iterator.remove();
            }
        }
    }

    /** @return true when the enemy of that round is gone, dead, or simply not the enemy any more. */
    private boolean targetInvalid(SweatlordSteveEntity mob, Round round)
    {
        LivingEntity target = round.target;

        if (target == null || target.isRemoved() || !target.isAlive())
        {
            return true;
        }

        return mob.getTarget() != target;
    }

    /** @return true when that projectile left the world (it hit something, it hit a wall, it expired). */
    private boolean isGone(@Nullable Entity projectile, Round round, long now)
    {
        if (projectile == null || projectile.isRemoved() || !projectile.isAlive())
        {
            return true;
        }

        return now - round.startTick > ROUND_TIMEOUT_TICKS;
    }

    /**
     * @return true when that projectile was inside the enemy box when it stopped, which is the only honest answer to
     *         "did this shot arrive": a shot that ended on the ground next to the enemy did not, and one the shield of
     *         the enemy stopped did (it reached the body, whatever happened after).
     */
    private boolean reached(@Nullable Entity projectile, @Nullable LivingEntity target)
    {
        if (projectile == null || target == null)
        {
            return false;
        }

        return projectile.getBoundingBox().inflate(HIT_MARGIN).intersects(target.getBoundingBox());
    }

    /** Judges one finished round: a hit clears the streak, a miss adds to it, and an invalidated round is not counted. */
    private void close(Round round, boolean invalid)
    {
        this.lastRoundTarget = round.targetId;
        this.lastRoundKind = round.kind;
        this.lastRoundHit = round.hit && !invalid;
        this.lastRoundInvalidated = invalid;

        if (invalid)
        {
            // The enemy of this round is not the enemy any more: nothing about it says anything about the aim.
            if (this.streakTarget != null && !this.streakTarget.equals(round.targetId))
            {
                this.misses = 0;
                this.streakTarget = null;
            }

            return;
        }

        if (round.hit)
        {
            this.streakTarget = round.targetId;
            this.misses = 0;
            ++this.hits;

            return;
        }

        if (!round.targetId.equals(this.streakTarget))
        {
            // A different enemy starts its own count: two misses against one body are not three against nobody.
            this.streakTarget = round.targetId;
            this.misses = 1;
        }
        else
        {
            ++this.misses;
        }
    }

    /**
     * @return true when the ranged plan has clearly failed against the enemy being fought right now, so the pearl has
     *         to close the gap: {@link #MISSES_FOR_FORCED_PEARL} full rounds in a row that never arrived.
     */
    public boolean wantsForcedPearl(CombatContext context)
    {
        return this.misses >= MISSES_FOR_FORCED_PEARL && context.hasTarget() && context.target() != null
                // A pearl closes a gap: inside the melee band the sword is already the answer, and the streak simply
                // waits for the next ranged round instead of turning into a jump into the enemy's face.
                && context.distance() > CombatContext.BOW_MIN_DISTANCE
                && this.streakTargetIs(context.target());
    }

    /** Spends the streak, so one panic pearl is thrown per streak and not one per tick. */
    public void consumeMissStreak()
    {
        this.misses = 0;
    }

    /** Forgets everything, which is what the end of a fight does. */
    public void reset()
    {
        this.open.clear();
        this.streakTarget = null;
        this.misses = 0;
        this.hits = 0;
    }

    /** @return the kind of the last judged round, for the debug output and the tests. */
    public Kind lastRoundKind()
    {
        return this.lastRoundKind;
    }

    /** @return true when the last judged round really reached its enemy. */
    public boolean lastRoundHit()
    {
        return this.lastRoundHit;
    }

    /** @return true when the last judged round was thrown away because its enemy was gone or changed. */
    public boolean lastRoundInvalidated()
    {
        return this.lastRoundInvalidated;
    }

    /** @return the enemy of the last judged round, or null. */
    @Nullable
    public UUID lastRoundTarget()
    {
        return this.lastRoundTarget;
    }
}
