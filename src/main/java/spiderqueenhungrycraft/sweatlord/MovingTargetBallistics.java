package spiderqueenhungrycraft.sweatlord;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The one ballistics layer of the Sweatlord kit (Sweatlord V1.1).
 *
 * <p>Bow, egg and trident do not each carry their own aiming patch any more. They all ask this class the same
 * question - "which launch velocity reaches that moving body?" - and only the {@link ProjectileProfile} they pass
 * differs, so the three weapons can never drift apart again.
 *
 * <p><b>The model is the game's own, tick by tick.</b> Every value of a profile is read from the real 1.20.1
 * implementation and not guessed:
 *
 * <ul>
 * <li>{@code AbstractArrow} (arrow and trident): the velocity is multiplied by {@code 0.99} and {@code 0.05} is taken
 * off the vertical part every tick,</li>
 * <li>{@code ThrowableProjectile} (egg): the same with a drag of {@code 0.99} and a gravity of {@code 0.03},</li>
 * <li>{@code BowItem}: a fully drawn bow launches at {@code 1.0 * 3.0} blocks per tick, an egg is thrown at
 * {@code 1.5} and a trident at {@code 2.5}.</li>
 * </ul>
 *
 * <p><b>Shooter motion.</b> A vanilla projectile does <b>not</b> inherit the velocity of whoever fired it -
 * {@code Projectile#shoot} only sets a direction and a speed - so a moving shooter is handled correctly by solving
 * from the <b>current</b> eye position at the moment of the release, which is what the calling skill does. Nothing
 * here adds an imaginary inherited velocity to a shot.
 *
 * <p><b>The target is intercepted, not just led.</b> For every candidate flight time the future position of the
 * target is predicted ({@code position + velocity * t}), the closed form launch velocity for that future point is
 * computed (the displacement of a projectile is linear in its launch velocity once the flight time is fixed), and the
 * candidate is then <b>verified by a full simulation</b> - the target box moving with its own velocity, the game's own
 * hit test - before it is accepted. The flattest verified candidate wins.
 *
 * <p><b>What this can not do.</b> A target that changes its velocity <i>after</i> the shot has left the hand can not
 * be predicted by any solver: nobody knows a player's next input. The kit answers that with the small in-flight nudge
 * of {@link GuidedShot}, never with a claim of a mathematical guarantee.
 */
public final class MovingTargetBallistics
{
    /** The inflation the game itself uses when it asks whether a projectile touched a body. */
    public static final double TARGET_INFLATION = 0.3D;

    /** How long a shot of this body may still be nudged after it left the hand. */
    public static final int GUIDANCE_TICKS = 10;

    /** The largest turn one tick of guidance may apply: a nudge, never a homing missile. */
    public static final double MAX_TURN_DEGREES_PER_TICK = 1.2D;

    /** The largest total turn a shot may receive, so a shot that was aimed well is never visibly steered. */
    public static final double MAX_TOTAL_TURN_DEGREES = 12.0D;

    /** Below this speed a projectile is spent - in the ground, in a wall - and is not steered any more. */
    private static final double MIN_GUIDED_SPEED = 0.05D;

    /**
     * A shot that only misses the body by this much is left alone: nudging a near miss would be meddling with a shot
     * that was aimed well, and that is exactly what this correction must never do.
     */
    private static final double NEAR_MISS_DISTANCE = 0.6D;

    /** A turn smaller than this is not written at all, so a shot that is on course stays bit for bit untouched. */
    private static final double MIN_CORRECTION_DEGREES = 0.1D;

    /**
     * One projectile of this kit, with the real physics of the game.
     *
     * @param id             the name of the profile, for the debug output
     * @param launchSpeed    the launch speed the item really has
     * @param gravity        the gravity {@code Projectile#tick} takes off the vertical part
     * @param drag           the factor the velocity is multiplied with
     * @param hitboxWidth    the width of the projectile hitbox, the corridor a shot needs
     * @param maxFlightTicks the longest flight this solver may consider
     */
    public record ProjectileProfile(String id, double launchSpeed, double gravity, double drag, double hitboxWidth,
            int maxFlightTicks)
    {
        /** A fully drawn bow: an arrow at three blocks per tick, with the gravity of an arrow. */
        public static final ProjectileProfile BOW = new ProjectileProfile("bow", 3.0D, 0.05D, 0.99D, 0.5D, 120);

        /** A thrown egg: one and a half blocks per tick, with the lighter gravity of a thrown item. */
        public static final ProjectileProfile EGG = new ProjectileProfile("egg", 1.5D, 0.03D, 0.99D, 0.25D, 120);

        /** A thrown trident: two and a half blocks per tick, with the gravity of an arrow. */
        public static final ProjectileProfile TRIDENT = new ProjectileProfile("trident", 2.5D, 0.05D, 0.99D, 0.5D, 120);

        /** @return the same profile for a different available speed, which is what the in-flight guidance uses. */
        public ProjectileProfile withSpeed(double speed)
        {
            return new ProjectileProfile(this.id, speed, this.gravity, this.drag, this.hitboxWidth,
                    this.maxFlightTicks);
        }

        /** @return the half width of the corridor this projectile needs when it flies past blocks. */
        public double halfWidth()
        {
            return this.hitboxWidth * 0.5D;
        }
    }

    /** One solved shot: the launch velocity, the tick it arrives on and the point it was aimed at. */
    public record Solution(Vec3 velocity, int flightTicks, Vec3 aimPoint)
    {
    }

    private MovingTargetBallistics()
    {
    }

    /**
     * @return the flattest launch velocity that really reaches that moving body, or null when no shot of that profile
     *         can reach it at all.
     */
    @Nullable
    public static Solution solve(Vec3 from, Vec3 targetCenter, Vec3 targetVelocity, AABB targetBox,
            ProjectileProfile profile)
    {
        for (int flight = 1; flight <= profile.maxFlightTicks(); ++flight)
        {
            Vec3 predicted = targetCenter.add(targetVelocity.scale(flight));
            Vec3 velocity = requiredVelocity(from, predicted, flight, profile);

            if (velocity == null)
            {
                continue;
            }

            if (!touches(from, velocity, profile, targetBox, targetVelocity, flight))
            {
                continue;
            }

            return new Solution(velocity, flight, predicted);
        }

        return null;
    }

    /** @return the launch velocity of {@link #solve} alone, for callers that do not need the flight time. */
    @Nullable
    public static Vec3 solveAim(Vec3 from, Vec3 targetCenter, Vec3 targetVelocity, AABB targetBox,
            ProjectileProfile profile)
    {
        Solution solution = solve(from, targetCenter, targetVelocity, targetBox, profile);

        return solution == null ? null : solution.velocity();
    }

    /**
     * @return the launch velocity that lands a shot on that point after exactly that many ticks, or null when the
     *         profile can not do it in that time.
     *
     * <p>The displacement of a projectile is linear in its launch velocity once the flight time is fixed: the
     * horizontal part is the velocity times the sum of the drag factors, and the vertical part additionally has the
     * gravity that this flight will really accumulate taken off it. That is why this is a closed form and not a search.
     */
    @Nullable
    private static Vec3 requiredVelocity(Vec3 from, Vec3 destination, int flight, ProjectileProfile profile)
    {
        double factor = dragFactor(flight, profile.drag());

        if (factor < 1.0E-6D)
        {
            return null;
        }

        Vec3 offset = destination.subtract(from);
        double drop = profile.gravity() * ((double) flight - factor) / (1.0D - profile.drag());
        Vec3 velocity = new Vec3(offset.x / factor, (offset.y + drop) / factor, offset.z / factor);

        return velocity.length() <= profile.launchSpeed() ? velocity : null;
    }

    /** @return the sum of the drag factors of that many ticks: launch velocity times this is the way it covers. */
    public static double dragFactor(int flightTicks, double drag)
    {
        return (1.0D - Math.pow(drag, flightTicks)) / (1.0D - drag);
    }

    /**
     * @return true when a shot with that velocity really touches that body, simulated tick by tick with the real
     *         physics and with the game's own hit test.
     *
     * <p>The segment of every tick is clipped against the <b>future</b> box of the target - the box moved by the
     * velocity the target has right now - and the box is inflated by {@link #TARGET_INFLATION}, exactly the way
     * {@code ProjectileUtil#getEntityHitResult} does it. A candidate that only comes close is refused here and never
     * offered to a skill as a solution.
     */
    private static boolean touches(Vec3 from, Vec3 velocity, ProjectileProfile profile, AABB targetBox,
            Vec3 targetVelocity, int flight)
    {
        Vec3 position = from;
        Vec3 motion = velocity;

        for (int tick = 1; tick <= flight + 1; ++tick)
        {
            Vec3 next = position.add(motion);

            if (targetBox.move(targetVelocity.scale(tick)).inflate(TARGET_INFLATION).clip(position, next).isPresent())
            {
                return true;
            }

            position = next;
            motion = new Vec3(motion.x * profile.drag(), motion.y * profile.drag() - profile.gravity(),
                    motion.z * profile.drag());
        }

        return false;
    }

    /**
     * @return true when the first stretch of that arc is free of blocks, so a shot is never fired straight into the
     *         wall the body happens to stand next to.
     */
    public static boolean pathIsClear(Level level, Vec3 from, Vec3 velocity, ProjectileProfile profile, int ticks)
    {
        Vec3 position = from;
        Vec3 motion = velocity;

        for (int tick = 1; tick <= ticks; ++tick)
        {
            Vec3 next = position.add(motion);
            BlockHitResult hit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, null));

            if (hit.getType() != HitResult.Type.MISS)
            {
                return false;
            }

            position = next;
            motion = new Vec3(motion.x * profile.drag(), motion.y * profile.drag() - profile.gravity(),
                    motion.z * profile.drag());
        }

        return true;
    }

    // -------------------------------------------------------------------------------------------------------------
    // The small in-flight correction
    // -------------------------------------------------------------------------------------------------------------

    /**
     * One shot of this body that may still be nudged, and the target it was fired at.
     *
     * <p><b>What it is for, and what it is not.</b> A target that changes its velocity after the release can not be
     * predicted, and this class does not pretend otherwise: the nudge only takes the edge off the error that a
     * strafing or jumping enemy produces. It is deliberately tiny - at most {@link #MAX_TURN_DEGREES_PER_TICK} per
     * tick, for at most {@link #GUIDANCE_TICKS} ticks, so at most twelve degrees in total. The speed, the gravity, the
     * drag and the collision of the projectile are never touched: only its direction, and only by that hair.
     *
     * <p>A shot that was aimed well is not touched at all: when the solved direction and the flown direction agree -
     * which is exactly the case for a target that keeps its course - the turn is zero and nothing is written.
     */
    public static final class GuidedShot
    {
        private final Entity projectile;
        private final LivingEntity target;
        private final ProjectileProfile profile;

        private int age;
        private double turnedDegrees;

        GuidedShot(Entity projectile, LivingEntity target, ProjectileProfile profile)
        {
            this.projectile = projectile;
            this.target = target;
            this.profile = profile;
        }

        /** @return true while the shot is still worth keeping an eye on. */
        boolean tick()
        {
            ++this.age;

            if (this.projectile.isRemoved() || !this.target.isAlive() || this.target.isRemoved()
                    || this.projectile.level() != this.target.level() || this.age > GUIDANCE_TICKS
                    || this.turnedDegrees >= MAX_TOTAL_TURN_DEGREES)
            {
                return false;
            }

            Vec3 motion = this.projectile.getDeltaMovement();
            double speed = motion.length();

            if (speed < MIN_GUIDED_SPEED)
            {
                // Spent, stuck in the ground or in a wall: there is no flight left to steer.
                return false;
            }

            Vec3 toTarget = this.target.getBoundingBox().getCenter().subtract(this.projectile.position());

            if (toTarget.lengthSqr() < 1.0E-4D || motion.dot(toTarget) <= 0.0D)
            {
                // On top of it or already past it: nothing left to correct.
                return false;
            }

            Course course = this.followCurrentCourse();

            if (course.reaches() || course.closestDistance() <= NEAR_MISS_DISTANCE)
            {
                // The shot is on the body, or so close to it that steering would only be meddling. Nothing is written:
                // this is what makes the correction invisible against a target that keeps its course.
                return true;
            }

            int flight = Math.max(1, course.closestTick());
            AABB box = this.target.getBoundingBox();
            Vec3 future = this.target.getDeltaMovement().scale(flight);
            Solution solution = solve(this.projectile.position(), box.getCenter().add(future),
                    this.target.getDeltaMovement(), box.move(future), this.profile.withSpeed(speed));

            if (solution == null)
            {
                return true;
            }

            Vec3 current = motion.normalize();
            Vec3 wanted = solution.velocity().normalize();
            double angle = Math.toDegrees(Math.acos(Mth.clamp(current.dot(wanted), -1.0D, 1.0D)));

            if (angle <= MIN_CORRECTION_DEGREES)
            {
                return true;
            }

            double step = Math.min(angle,
                    Math.min(MAX_TURN_DEGREES_PER_TICK, MAX_TOTAL_TURN_DEGREES - this.turnedDegrees));
            this.projectile.setDeltaMovement(rotateTowards(current, wanted, step).scale(speed));
            this.turnedDegrees += step;

            return true;
        }

        /**
         * @return the course this shot is on right now, followed with the real physics of the game and the future box of
         *         the target.
         *
         * <p>This is the question that keeps the correction invisible: a shot whose course already reaches the body is
         * <b>not touched at all</b>, and neither is one that only just misses. Only a shot that is really off - which
         * means the target changed its velocity after the release - is nudged, and only by the small step of the class.
         */
        private Course followCurrentCourse()
        {
            Vec3 position = this.projectile.position();
            Vec3 motion = this.projectile.getDeltaMovement();
            AABB box = this.target.getBoundingBox();
            Vec3 velocity = this.target.getDeltaMovement();
            double closest = Double.MAX_VALUE;
            int closestTick = 0;

            for (int tick = 1; tick <= this.profile.maxFlightTicks(); ++tick)
            {
                Vec3 next = position.add(motion);
                AABB future = box.move(velocity.scale(tick)).inflate(TARGET_INFLATION);

                if (future.clip(position, next).isPresent())
                {
                    return new Course(true, tick, 0.0D);
                }

                double distance = future.getCenter().distanceTo(next);

                if (distance < closest)
                {
                    closest = distance;
                    closestTick = tick;
                }

                position = next;
                motion = new Vec3(motion.x * this.profile.drag(), motion.y * this.profile.drag()
                        - this.profile.gravity(), motion.z * this.profile.drag());
            }

            return new Course(false, closestTick, closest);
        }

        /** @return how far this shot has been turned so far, for the debug output and the tests. */
        public double turnedDegrees()
        {
            return this.turnedDegrees;
        }
    }

    /** One course of a shot in flight: whether it already reaches the body, and where it comes closest. */
    private record Course(boolean reaches, int closestTick, double closestDistance)
    {
    }

    /**

    /**

    /**

    /** Registers a shot of that body as one that may still be nudged in flight. */
    public static void attach(SweatlordSteveEntity mob, Entity projectile, LivingEntity target,
            ProjectileProfile profile)
    {
        if (mob.level().isClientSide || target == null || projectile == null)
        {
            return;
        }

        mob.guidedShots().add(new GuidedShot(projectile, target, profile));
    }

    /** One tick of the in-flight correction of that body, called by the body itself. */
    public static void tickGuidance(SweatlordSteveEntity mob)
    {
        List<GuidedShot> shots = mob.guidedShots();

        if (shots.isEmpty())
        {
            return;
        }

        shots.removeIf(shot -> !shot.tick());
    }

    /**
     * @return the unit vector that lies at most that many degrees from {@code from}, on the way to {@code to}.
     *
     * <p>A spherical interpolation, so the length stays one and a small step is exactly a small turn: a plain lerp
     * would shorten the vector and normalising after it would overshoot the step.
     */
    static Vec3 rotateTowards(Vec3 from, Vec3 to, double degrees)
    {
        double omega = Math.acos(Mth.clamp(from.dot(to), -1.0D, 1.0D));

        if (omega < 1.0E-6D)
        {
            return to;
        }

        double step = Math.min(Math.toRadians(degrees), omega);
        double sinOmega = Math.sin(omega);

        if (Math.abs(sinOmega) < 1.0E-6D)
        {
            return to;
        }

        return from.scale(Math.sin(omega - step) / sinOmega).add(to.scale(Math.sin(step) / sinOmega)).normalize();
    }
}
