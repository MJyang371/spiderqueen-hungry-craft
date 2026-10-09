package spiderqueenhungrycraft.sweatlord;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * Chooses where a pearl may be thrown (Sweatlord round).
 *
 * <p>The reference pearl task only answers "given this spot, how do I throw the pearl"; it never asks whether the spot
 * is a good idea. This class is that missing half: it looks for candidate spots, throws every dangerous one away and
 * only then hands the winner to the ballistics.
 *
 * <p>Two modes, one rule set:
 *
 * <ul>
 * <li>{@link Mode#OFFENSIVE} - a ring of {@value #OFFENSIVE_MIN} to {@value #OFFENSIVE_MAX} blocks around the enemy,
 * preferred on their side or behind them, so the jump lands next to the target and not in its face.</li>
 * <li>{@link Mode#ESCAPE} - a ring of {@value #ESCAPE_MIN} to {@value #ESCAPE_MAX} blocks around himself, away from
 * the enemy, for the retreat.</li>
 * </ul>
 *
 * <p>A candidate is refused when it is not real, standable ground: lava, fire, cactus, a web, a rim, a cliff, a deep
 * pit, the void, an unloaded chunk, no room for a body, or a spot no pearl can actually reach. Nothing here ever
 * forces a throw: {@code null} is a valid answer and the brain simply keeps fighting with the bow.
 */
public final class SafePearlPlanner
{
    /** The offensive ring around the enemy. */
    public static final double OFFENSIVE_MIN = 2.5D;
    public static final double OFFENSIVE_MAX = 4.5D;

    /** The escape ring around himself. */
    public static final double ESCAPE_MIN = 10.0D;
    public static final double ESCAPE_MAX = 16.0D;

    /** The speed a thrown ender pearl leaves the hand with, the same the game gives a player. */
    public static final double PEARL_SPEED = 1.5D;

    /** An escape pearl that would not move him at least this far is not a retreat. */
    private static final double MIN_ESCAPE_GAIN = 6.0D;

    /** The offensive distance he wants to end up at, and the distance the score is measured against. */
    private static final double PREFERRED_OFFENSIVE_DISTANCE = 3.5D;

    public enum Mode
    {
        OFFENSIVE,
        ESCAPE
    }

    /** Why a candidate was thrown away; counted so a debug log can tell a bad plan from a bad world. */
    public enum Rejection
    {
        DANGEROUS_GROUND,
        CLIFF,
        VOID,
        NO_ROOM,
        UNLOADED,
        NO_TRAJECTORY,
        NO_GAIN
    }

    /** One accepted plan. */
    public record Plan(BlockPos destination, Vec3 velocity, Mode mode, double score, String note)
    {
    }

    /** The answer of one planning call: a plan or the reasons why there is none. */
    public static final class Result
    {
        @Nullable
        private final Plan plan;
        private final int candidates;
        private final Map<Rejection, Integer> rejections;

        private Result(@Nullable Plan plan, int candidates, Map<Rejection, Integer> rejections)
        {
            this.plan = plan;
            this.candidates = candidates;
            this.rejections = rejections;
        }

        @Nullable
        public Plan plan()
        {
            return this.plan;
        }

        public boolean found()
        {
            return this.plan != null;
        }

        public int candidates()
        {
            return this.candidates;
        }

        public int rejected(Rejection rejection)
        {
            return this.rejections.getOrDefault(rejection, 0);
        }

        /** @return one line about this planning call, for the gated debug log. */
        public String summary()
        {
            StringBuilder text = new StringBuilder(this.plan == null ? "no plan" : "plan " + this.plan.destination()
                    + " score=" + Math.round(this.plan.score()));

            text.append(" of ").append(this.candidates).append(" candidates");

            for (Map.Entry<Rejection, Integer> entry : this.rejections.entrySet())
            {
                text.append(", ").append(entry.getKey()).append('=').append(entry.getValue());
            }

            return text.toString();
        }
    }

    private SafePearlPlanner()
    {
    }

    /**
     * @return the safest useful spot for a pearl, or a result without a plan when every candidate was refused.
     *
     * @param enemy the enemy for the offensive ring, null when there is none (an escape never needs one)
     * @param mode  which ring and which score to use
     */
    public static Result plan(SweatlordSteveEntity mob, @Nullable LivingEntity enemy, Mode mode)
    {
        Map<Rejection, Integer> rejections = new EnumMap<>(Rejection.class);
        Vec3 centre = mode == Mode.OFFENSIVE && enemy != null ? enemy.position() : mob.position();
        double min = mode == Mode.OFFENSIVE ? OFFENSIVE_MIN : ESCAPE_MIN;
        double max = mode == Mode.OFFENSIVE ? OFFENSIVE_MAX : ESCAPE_MAX;
        java.util.List<BlockPos> candidates = EnvironmentSafety.ringCandidates(mob.level(), centre, min, max);
        Plan best = null;

        for (BlockPos candidate : candidates)
        {
            Plan plan = judge(mob, enemy, candidate, mode, rejections);

            if (plan != null && (best == null || plan.score() > best.score()))
            {
                best = plan;
            }
        }

        return new Result(best, candidates.size(), rejections);
    }

    private static void reject(Map<Rejection, Integer> rejections, Rejection rejection)
    {
        rejections.merge(rejection, 1, Integer::sum);
    }

    /**
     * @return the plan for that one candidate, or null when it has to be refused - in which case the reason is
     *         counted in the map, which is what makes a rejected plan readable in the debug log.
     */
    @Nullable
    private static Plan judge(SweatlordSteveEntity mob, @Nullable LivingEntity enemy, BlockPos candidate, Mode mode,
            Map<Rejection, Integer> rejections)
    {
        if (!mob.level().isLoaded(candidate))
        {
            reject(rejections, Rejection.UNLOADED);
            return null;
        }

        if (!EnvironmentSafety.isStandable(mob.level(), candidate))
        {
            reject(rejections, Rejection.DANGEROUS_GROUND);
            return null;
        }

        int fall = EnvironmentSafety.fallHeight(mob.level(), candidate);

        if (fall >= EnvironmentSafety.LETHAL_FALL)
        {
            reject(rejections, Rejection.VOID);
            return null;
        }

        if (fall > EnvironmentSafety.FREE_FALL)
        {
            reject(rejections, Rejection.CLIFF);
            return null;
        }

        AABB space = EnvironmentSafety.bodyBox(candidate, mob.getBbWidth(), mob.getBbHeight());

        if (!mob.level().noCollision(mob, space))
        {
            reject(rejections, Rejection.NO_ROOM);
            return null;
        }

        Vec3 destination = Vec3.atBottomCenterOf(candidate);
        Vec3 from = mob.position();

        if (mode == Mode.ESCAPE && from.distanceTo(destination) < MIN_ESCAPE_GAIN)
        {
            reject(rejections, Rejection.NO_GAIN);
            return null;
        }

        Vec3 velocity = ProjectileBallistics.solveThrow(mob.level(), ProjectileBallistics.eyeOf(mob), destination,
                PEARL_SPEED, ProjectileBallistics.THROWN_GRAVITY);

        if (velocity == null)
        {
            reject(rejections, Rejection.NO_TRAJECTORY);
            return null;
        }

        double score = mode == Mode.OFFENSIVE ? offensiveScore(enemy, destination, mob) : escapeScore(enemy, from, destination);

        return new Plan(candidate, velocity, mode, score, mode == Mode.OFFENSIVE ? "flank" : "retreat");
    }

    /**
     * @return how good that landing spot is for an attack: a spot at the preferred distance next to the enemy scores
     *         best, and being on their side or behind them is worth a good deal more than standing in front of them.
     */
    private static double offensiveScore(@Nullable LivingEntity enemy, Vec3 destination, SweatlordSteveEntity mob)
    {
        if (enemy == null)
        {
            return 0.0D;
        }

        double distance = destination.distanceTo(enemy.position());
        double score = 100.0D - Math.abs(distance - PREFERRED_OFFENSIVE_DISTANCE) * 12.0D;
        Vec3 toSpot = destination.subtract(enemy.position());
        Vec3 flat = new Vec3(toSpot.x, 0.0D, toSpot.z);

        if (flat.lengthSqr() > 1.0E-6D)
        {
            Vec3 enemyView = enemy.getViewVector(1.0F);
            Vec3 flatView = new Vec3(enemyView.x, 0.0D, enemyView.z);

            if (flatView.lengthSqr() > 1.0E-6D && flat.normalize().dot(flatView.normalize()) < 0.1D)
            {
                // Side or back: they have to turn around to answer, which is the whole point of a pearl jump.
                score += 25.0D;
            }
        }

        // A landing spot that is on his own side of the fight is worth less than one that closes the distance.
        if (destination.distanceTo(enemy.position()) > mob.distanceTo(enemy))
        {
            score -= 15.0D;
        }

        return score;
    }

    /** @return how good that landing spot is for a retreat: away from the enemy and really somewhere else. */
    private static double escapeScore(@Nullable LivingEntity enemy, Vec3 from, Vec3 destination)
    {
        double away = enemy == null ? 0.0D : destination.distanceTo(enemy.position());
        double moved = from.distanceTo(destination);

        return away * 2.0D + moved * 0.5D;
    }
}
