package spiderqueenhungrycraft.sweatlord;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * Which projectiles are really about to hit the Sweatlord (Sweatlord round).
 *
 * <p>"Something is within twenty blocks, raise the shield" is exactly the rule this class exists to avoid: it would
 * park him behind a shield forever, because arrows in the wall next to him, eggs thrown at somebody else and his own
 * pearls all count as "something nearby".
 *
 * <p>The question asked here is the honest one: for every projectile in reach, simulate its flight with the real
 * gravity of its kind and ask whether it touches <b>his own bounding box</b> within the next {@link #HORIZON_TICKS}
 * ticks. A shot that misses him is not a threat, no matter how close it flies by.
 *
 * <p>It is written against {@code Projectile}, so arrows, snowballs, eggs, tridents, potions, fireballs and the web
 * shots of this mod are all covered, and only the gravity differs per kind.
 */
public final class ProjectileThreatDetector
{
    /**
     * How far a projectile may be and still be looked at.
     *
     * <p>Forty blocks and not twenty-four, because of the budget of a reaction: the game only lets a shield block after
     * five ticks of use, a detection costs one tick and the tick order of a server costs one more, so a reaction that
     * only ever looks at shots that are already in the air needs about seven ticks of warning. An arrow crosses three
     * blocks in a tick, so it has to be seen {@code 7 * 3 = 21} blocks out at the very least - and with the drag of the
     * game it is closer to twenty-four. A radius of forty leaves the same reaction a comfortable thirteen ticks, which
     * is what turns "he sometimes blocks" into "he blocks".
     */
    public static final double SCAN_RADIUS = 40.0D;

    /** How far into the future a flight is simulated: two seconds of arrow flight is a very long shot. */
    public static final int HORIZON_TICKS = 40;

    /** One projectile that is on a collision course. */
    public record Threat(Entity projectile, int ticksToImpact, Vec3 direction)
    {
    }

    private ProjectileThreatDetector()
    {
    }

    /** @return every projectile that would really hit that body, soonest impact first. */
    public static List<Threat> scan(SweatlordSteveEntity mob)
    {
        List<Threat> threats = new ArrayList<>();
        AABB body = mob.getBoundingBox();

        for (Projectile projectile : mob.level().getEntitiesOfClass(Projectile.class, body.inflate(SCAN_RADIUS)))
        {
            if (projectile.isRemoved() || projectile.getOwner() == mob)
            {
                continue;
            }

            Vec3 from = projectile.position();
            Vec3 velocity = projectile.getDeltaMovement();

            if (velocity.lengthSqr() < 1.0E-6D)
            {
                continue;
            }

            int impact = ticksToImpact(from, velocity, gravityOf(projectile), body, mob);

            if (impact >= 0)
            {
                threats.add(new Threat(projectile, impact, from.subtract(body.getCenter())));
            }
        }

        threats.sort((first, second) -> Integer.compare(first.ticksToImpact(), second.ticksToImpact()));

        return threats;
    }

    /** @return the projectile that would hit soonest, or null when nothing is on a collision course. */
    @Nullable
    public static Threat worst(SweatlordSteveEntity mob)
    {
        List<Threat> threats = scan(mob);

        return threats.isEmpty() ? null : threats.get(0);
    }

    /** @return true when that projectile is aimed at that body, checked against the body box itself. */
    public static boolean isThreatTo(SweatlordSteveEntity mob, Projectile projectile)
    {
        if (projectile.getOwner() == mob)
        {
            return false;
        }

        return ticksToImpact(projectile.position(), projectile.getDeltaMovement(),
                gravityOf(projectile), mob.getBoundingBox(), mob) >= 0;
    }

    /**
     * @return the tick the shot would land on that body, or -1 when it misses.
     *
     * <p>The body box is used as it is: a shot that passes a hand width away is not going to be stopped by a shield
     * held in front of the chest, and pretending otherwise is what makes a reaction fire at everything.
     */
    private static int ticksToImpact(Vec3 from, Vec3 velocity, double gravity, AABB body, SweatlordSteveEntity mob)
    {
        Vec3 position = from;
        Vec3 motion = velocity;
        AABB reach = body.inflate(0.05D);

        for (int tick = 1; tick <= HORIZON_TICKS; ++tick)
        {
            Vec3 previous = position;
            position = position.add(motion);

            // The segment of this tick and not the sampled point: a shot of three blocks per tick can pass straight
            // through a body between two tick positions, and a reaction that only ever looks at positions is blind to
            // exactly the shots that were aimed well. This is the same rule the game itself uses to decide a hit.
            if (reach.clip(previous, position).isPresent())
            {
                return tick;
            }

            BlockPos block = BlockPos.containing(position);

            // A projectile that reached the ground next to him is over, whatever it was aimed at - unless that ground
            // is not loaded, in which case the shot is simply not judged.
            if (mob.level().isLoaded(block) && !mob.level().getBlockState(block).isAir())
            {
                return -1;
            }

            motion = new Vec3(motion.x * ProjectileBallistics.DRAG,
                    motion.y * ProjectileBallistics.DRAG - gravity, motion.z * ProjectileBallistics.DRAG);
        }

        return -1;
    }

    /** @return the gravity of that projectile's flight, from its own class and never guessed from a registry id. */
    public static double gravityOf(Projectile projectile)
    {
        if (projectile.isNoGravity())
        {
            // A shot that the game gave no gravity does not fall: the web shot of this mod flies in a straight line, and
            // a prediction that insists on a drop would look under a shot that is coming straight at the chest.
            return 0.0D;
        }

        if (projectile instanceof AbstractArrow)
        {
            return ProjectileBallistics.ARROW_GRAVITY;
        }

        if (projectile instanceof AbstractHurtingProjectile)
        {
            // Fireballs fly straight: the game gives them no gravity at all.
            return 0.0D;
        }

        if (projectile instanceof ThrowableProjectile)
        {
            return ProjectileBallistics.THROWN_GRAVITY;
        }

        return ProjectileBallistics.THROWN_GRAVITY;
    }

    /** @return the direction a threat comes from, seen from the body. */
    public static Vec3 incomingDirection(SweatlordSteveEntity mob, Threat threat)
    {
        return threat.direction().lengthSqr() < 1.0E-6D ? mob.getViewVector(1.0F)
                : threat.direction().normalize();
    }

    /** @return the living shooter behind a threat, or null when it has none. */
    @Nullable
    public static LivingEntity shooterOf(Threat threat)
    {
        return threat.projectile() instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity living ? living : null;
    }

    /**
     * How much the body box is grown when a projectile is asked whether it is already touching him.
     *
     * <p>The segment test of a flight is the question for "will this hit me", and this one is the question for "has it
     * arrived". A shot whose own tick runs in the same tick as his answer can be a fraction of a block away from the box
     * it is about to hit, so the contact is measured with a margin instead of with an exact overlap.
     */
    public static final double CONTACT_MARGIN = 0.35D;

    /**
     * @return every projectile that is <b>touching</b> that body right now, whatever it deals.
     *
     * <p>This is the list the instant projectile guard of the Sweatlord needs, and the reason it exists is a line of the
     * game: the shield path of {@code LivingEntity#hurt} begins with {@code damage > 0}. An egg, a snowball and a web
     * shot of this mod deal no damage at all, so none of them can ever enter that path - nothing in the game can block
     * them for him, no matter how long the shield has been up. He answers those himself, and for that he has to be able
     * to hold the projectile for one tick.
     */
    public static List<Projectile> inContact(SweatlordSteveEntity mob)
    {
        List<Projectile> touching = new ArrayList<>();
        AABB reach = mob.getBoundingBox().inflate(CONTACT_MARGIN);

        for (Projectile projectile : mob.level().getEntitiesOfClass(Projectile.class, reach))
        {
            if (!projectile.isRemoved() && projectile.getOwner() != mob && projectile.getBoundingBox().intersects(reach))
            {
                touching.add(projectile);
            }
        }

        return touching;
    }

}
