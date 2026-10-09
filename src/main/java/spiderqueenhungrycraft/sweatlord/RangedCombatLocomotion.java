package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The movement of the Sweatlord while a ranged action is running (Sweatlord V1.1).
 *
 * <p>Before this class every ranged skill called {@code brake} on every tick it owned the body, so a bow draw, an egg
 * burst and a trident throw all turned him into a stationary turret - the exact opposite of the player he is supposed
 * to be. Now each of the three skills ticks through this class instead:
 *
 * <ul>
 * <li>he keeps his eyes on the enemy (the aim and the shots are solved from the moving body, never from a frozen
 * one),</li>
 * <li>he keeps a distance band that belongs to the weapon - the bow wants room, the eggs want to be close, the trident
 * sits in between,</li>
 * <li>inside that band he <b>walks sideways</b>, and the direction changes on a random timer between
 * {@link #MIN_SWITCH_TICKS} and {@link #MAX_SWITCH_TICKS} ticks, so it is a strafe and not a metronome,</li>
 * <li>and no step is ever taken into a cliff, lava, fire, a cactus or a spot without ground: every candidate is asked
 * of {@link EnvironmentSafety} first, and when both sides are refused he holds his ground.</li>
 * </ul>
 *
 * <p>The state lives in the skill that owns it (one instance per ranged skill), so switching from the bow to the eggs
 * does not teleport the strafe phase, and nothing here is shared between two bodies.
 */
public final class RangedCombatLocomotion
{
    /** The distance band one ranged action wants to fight from. */
    public record Band(double nearest, double farthest)
    {
        /** The bow wants room: it is the weapon with the longest reach of the kit. */
        public static final Band BOW = new Band(9.0D, 20.0D);

        /** The eggs want to be close: a hand thrown item does not fly far. */
        public static final Band EGG = new Band(6.0D, 12.0D);

        /** The trident sits in between: a heavier throw than an egg, less range than a bow. */
        public static final Band TRIDENT = new Band(7.0D, 16.0D);
    }

    /** How fast he steps sideways while he shoots. */
    private static final double STRAFE_SPEED = 1.0D;

    /** How fast he closes a distance that is too long. */
    private static final double CLOSE_SPEED = 1.1D;

    /** How far one step to the side is. */
    private static final double STEP = 2.5D;

    /** The shortest and the longest time one strafe direction is kept. */
    private static final int MIN_SWITCH_TICKS = 12;
    private static final int MAX_SWITCH_TICKS = 28;

    /** How far up and down a candidate spot may be from his feet and still be a step he can walk. */
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_STEP_DOWN = 3;

    private int strafeSign = 1;
    private int switchIn = MIN_SWITCH_TICKS;
    private int sidesteps;

    /**
     * The spot of the step he is walking right now, or null when he needs a new one.
     *
     * <p>This is the whole difference between a body that walks and a body that stands still: a navigation that is
     * asked for a new path on every tick - which is what this class did at first - never finishes one, and the body
     * barely moves at all. The first run of the round measured exactly that: 0.098 blocks per tick, while the same body
     * walks about 0.25 when it is left alone for a few ticks with one destination. So a step is planned once, walked,
     * and only replaced when the path is done or the direction of the strafe changes.
     */
    @Nullable
    private Vec3 destination;

    /** @return how many real steps to the side this locomotion has taken, for the debug output and the tests. */
    public int sidesteps()
    {
        return this.sidesteps;
    }

    /**
     * One tick of movement while an attack is running: face the enemy, keep the band, and strafe inside it.
     */
    public void tick(SweatlordSteveEntity mob, CombatContext context, Band band)
    {
        LivingEntity target = context.target();

        if (target == null)
        {
            return;
        }

        PlayerLikeActions.faceTowards(mob, target.position());

        if (!mob.onGround())
        {
            // In the air nothing is steered: the shot goes out on the arc it was solved for and the feet are busy with
            // the landing. Steering a jump is what makes a body look like it is on rails.
            return;
        }

        double distance = context.distance();

        if (distance > band.farthest())
        {
            // Walking in is the same rule as stepping aside: one navigation at a time, and never a new path per tick.
            if (mob.getNavigation().isDone())
            {
                this.destination = null;
                mob.getNavigation().moveTo(target, CLOSE_SPEED);
            }

            return;
        }

        Vec3 toTarget = target.position().subtract(mob.position());
        Vec3 flat = new Vec3(toTarget.x, 0.0D, toTarget.z);

        if (flat.lengthSqr() < 1.0E-4D)
        {
            return;
        }

        Vec3 direction = flat.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);

        if (distance < band.nearest())
        {
            // Too close for this weapon: he backs off, and if the way back is a cliff he takes a side step instead.
            this.stepChoosing(mob, direction.scale(-1.0D), side);
            return;
        }

        if (--this.switchIn <= 0)
        {
            this.strafeSign = -this.strafeSign;
            this.switchIn = MIN_SWITCH_TICKS + mob.getRandom().nextInt(MAX_SWITCH_TICKS - MIN_SWITCH_TICKS + 1);
            this.destination = null;
        }

        this.stepChoosing(mob, side.scale(this.strafeSign), side.scale(-this.strafeSign));
    }

    /**
     * Takes the first of those directions that is really walkable, and holds his ground when none of them is.
     *
     * <p>A refused direction is never walked anyway and never silently becomes a dash off a cliff: the whole point of
     * the environment safety of this round is that a clever boy may look clever, not suicidal.
     */
    private void stepChoosing(SweatlordSteveEntity mob, Vec3 first, Vec3 second)
    {
        if (this.step(mob, first) || this.step(mob, second))
        {
            return;
        }

        this.destination = null;
        mob.getNavigation().stop();
    }

    /**
     * Walks one step in that direction, and only plans it if there is no step of this body in progress.
     *
     * @return true when he really is walking one step in that direction.
     */
    private boolean step(SweatlordSteveEntity mob, Vec3 direction)
    {
        if (this.destination != null && !mob.getNavigation().isDone())
        {
            // He is already on his way to the spot of this step, and a navigation that is re-asked on every tick would
            // never let him arrive: the path is thrown away and rebuilt on every tick instead of being walked.
            return true;
        }

        Vec3 candidate = mob.position().add(direction.normalize().scale(STEP));
        BlockPos footing = footingAt(mob.level(), candidate);

        if (footing == null)
        {
            return false;
        }

        this.destination = new Vec3(footing.getX() + 0.5D, footing.getY(), footing.getZ() + 0.5D);
        mob.getNavigation().moveTo(this.destination.x, this.destination.y, this.destination.z, STRAFE_SPEED);
        ++this.sidesteps;

        return true;
    }

    /**
     * @return the spot of that column a body could really walk onto, or null when the column is a cliff, a wall, lava
     *         or the void.
     */
    @Nullable
    private static BlockPos footingAt(Level level, Vec3 column)
    {
        BlockPos base = BlockPos.containing(column.x, column.y, column.z);

        for (int dy = MAX_STEP_UP; dy >= -MAX_STEP_DOWN; --dy)
        {
            BlockPos candidate = base.offset(0, dy, 0);

            if (EnvironmentSafety.isStandable(level, candidate)
                    && EnvironmentSafety.fallHeight(level, candidate) <= 1)
            {
                return candidate;
            }
        }

        return null;
    }
}
