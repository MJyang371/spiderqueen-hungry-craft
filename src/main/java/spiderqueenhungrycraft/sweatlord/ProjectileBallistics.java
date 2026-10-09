package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The low level physics this round needs (Sweatlord round).
 *
 * <p>Written from scratch on the server side, because the reference implementation solves its trajectories with
 * client side helpers this project does not have. The model is the real one of the game, not a vacuum approximation: a
 * projectile moves by its velocity, its velocity is multiplied by {@link #DRAG} and its gravity is taken off the
 * vertical part, every single tick - which is exactly what {@code Projectile#tick} does.
 *
 * <p>Since V1.1 the <b>aiming</b> of a shot is not done here any more: bow, egg and trident all go through
 * {@link MovingTargetBallistics}, which owns the profiles, the intercept and the in-flight correction. What stays here
 * are the primitives the rest of the kit and the shield reaction are built on:
 *
 * <ul>
 * <li>{@link #sample} and {@link #willHit} - where a projectile with a given velocity is after N ticks, and whether it
 * would touch a box on the way (what {@link ProjectileThreatDetector} uses),</li>
 * <li>{@link #solveThrow} and {@link #landingPoint} - a velocity that really lands a thrown item on a spot (what the
 * pearl planner uses),</li>
 * <li>and the small position helpers of a body.</li>
 * </ul>
 */
public final class ProjectileBallistics
{
    /** The drag every projectile of the game applies to its velocity each tick. */
    public static final double DRAG = 0.99D;

    /** Gravity of an arrow. */
    public static final double ARROW_GRAVITY = 0.05D;

    /** Gravity of a thrown item (egg, ender pearl). */
    public static final double THROWN_GRAVITY = 0.03D;

    /** How many ticks a simulation may run before it gives up on ever landing. */
    public static final int MAX_FLIGHT_TICKS = 200;

    /** How close a simulated throw has to land to its destination to be accepted as the solution. */
    public static final double LANDING_TOLERANCE = 1.0D;

    private ProjectileBallistics()
    {
    }

    /** @return where a projectile with that velocity is after that many ticks, gravity and drag included. */
    public static Vec3 sample(Vec3 from, Vec3 velocity, double gravity, int ticks)
    {
        Vec3 position = from;
        Vec3 motion = velocity;

        for (int i = 0; i < ticks; ++i)
        {
            position = position.add(motion);
            motion = new Vec3(motion.x * DRAG, motion.y * DRAG - gravity, motion.z * DRAG);
        }

        return position;
    }

    /**
     * @return true when a projectile with that velocity would touch that box within that many ticks.
     *
     * <p>The box is used as it is, with no inflation: a shot that misses is a miss, and the shield reaction of the
     * Sweatlord must not fire for shots that were never going to land on his body in the first place.
     */
    public static boolean willHit(Vec3 from, Vec3 velocity, double gravity, AABB box, int ticks)
    {
        Vec3 position = from;
        Vec3 motion = velocity;

        for (int i = 0; i < ticks; ++i)
        {
            position = position.add(motion);

            if (box.contains(position))
            {
                return true;
            }

            motion = new Vec3(motion.x * DRAG, motion.y * DRAG - gravity, motion.z * DRAG);

            if (position.y < box.minY - 64.0D)
            {
                return false;
            }
        }

        return false;
    }

    /**
     * @return a velocity that lands a thrown item of that speed on that destination, or null when no reasonable throw
     *         reaches it.
     *
     * <p>Candidates are simulated rather than solved: the drag makes the closed form wrong at exactly the ranges where
     * this matters, and twenty-four simulations cost nothing when they only run on a pearl throw. The first candidate
     * that lands within {@link #LANDING_TOLERANCE} blocks of the destination wins, so the answer is deterministic and
     * reproducible in a debug log.
     */
    @Nullable
    public static Vec3 solveThrow(Level level, Vec3 from, Vec3 destination, double speed, double gravity)
    {
        Vec3 best = null;
        double bestError = Double.MAX_VALUE;
        double flatX = destination.x - from.x;
        double flatZ = destination.z - from.z;
        double flat = Math.sqrt(flatX * flatX + flatZ * flatZ);

        if (flat < 0.05D)
        {
            return null;
        }

        double yaw = Math.atan2(flatZ, flatX);

        for (double pitchDegrees = 30.0D; pitchDegrees >= -60.0D; pitchDegrees -= 2.5D)
        {
            double pitch = Math.toRadians(pitchDegrees);
            double horizontal = Math.cos(pitch) * speed;
            Vec3 velocity = new Vec3(Math.cos(yaw) * horizontal, Math.sin(pitch) * speed,
                    Math.sin(yaw) * horizontal);
            Vec3 landing = landingPoint(level, from, velocity, gravity);

            if (landing == null)
            {
                continue;
            }

            double error = landing.distanceTo(destination);

            if (error < bestError)
            {
                bestError = error;
                best = velocity;
            }

            if (error <= LANDING_TOLERANCE)
            {
                break;
            }
        }

        return bestError <= LANDING_TOLERANCE * 3.0D ? best : null;
    }

    /**
     * @return where a thrown item of that velocity first touches a block, or null when it never does (the void or a
     *         spot that is not loaded). The simulation stops at the first solid block, so the answer is the real
     *         landing point and never a point inside a wall.
     */
    @Nullable
    public static Vec3 landingPoint(Level level, Vec3 from, Vec3 velocity, double gravity)
    {
        Vec3 position = from;
        Vec3 motion = velocity;

        for (int i = 0; i < MAX_FLIGHT_TICKS; ++i)
        {
            Vec3 next = position.add(motion);
            BlockPos block = BlockPos.containing(next);

            if (next.y < level.getMinBuildHeight() || !level.isLoaded(block))
            {
                return null;
            }

            if (!level.getBlockState(block).getCollisionShape(level, block).isEmpty())
            {
                return position;
            }

            position = next;
            motion = new Vec3(motion.x * DRAG, motion.y * DRAG - gravity, motion.z * DRAG);
        }

        return null;
    }

    /** @return the velocity of a projectile that is already in the air. */
    public static Vec3 velocityOf(Entity projectile)
    {
        return projectile.getDeltaMovement();
    }

    /** @return the eye position a shot of that body leaves from. */
    public static Vec3 eyeOf(LivingEntity body)
    {
        return new Vec3(body.getX(), body.getEyeY(), body.getZ());
    }

    /** @return the point of that body a shot is aimed at: the middle of its chest, never its feet. */
    public static Vec3 aimPointOf(Entity body)
    {
        return body.position().add(0.0D, body.getBbHeight() * 0.5D, 0.0D);
    }
}
