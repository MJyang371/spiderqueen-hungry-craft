package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

/**
 * The walking of a climbing spider that fights like a skeleton (Special Spiders round I, climbing pass).
 *
 * <p><b>The problem it solves.</b> A vanilla spider does not climb because its pathfinder plans a climb - it cannot, a
 * pathfinder only knows walkable ground. A spider climbs because its navigation, {@code WallClimberNavigation}, never
 * gives up: when it finds no path it keeps pushing straight at its victim with the move control, and the body does the
 * rest (pushing into a wall raises the spider's own climbing flag, and {@code LivingEntity#travel} then replaces the
 * horizontal slide with an upward 0.2 blocks per tick - that is the whole climb, and it lives in the spider's body, not
 * in the navigation).
 *
 * <p>That one fallback is both the spider's greatest strength and the reason it cannot be a ranged unit: it makes the
 * spider walk straight at its victim through anything, forever, so it always ends up in melee. Removing the navigation
 * entirely (the previous pass) did stop the charge - and took the climbing with it.
 *
 * <p><b>What this class does instead:</b> it keeps the ordinary ground pathing, and it keeps the fallback - but only
 * where the fallback is really a climb:
 *
 * <ul>
 * <li>the victim is at least one block above the spider (a wall, a ledge, a roof - a place a spider would climb to), or
 * <li>the spider is already standing against a wall (a bump is a climb in progress).
 * </ul>
 *
 * <p>On open ground with no path - a gap, a pond, a fence it could walk around - there is no push at all: the spider
 * stands where it is and shoots, exactly like the skeleton whose firing rhythm it borrowed. And even a real climb is
 * abandoned the moment the spider stops making height: pushing for ten ticks without rising means this is not a wall it
 * can use, so it steps back into its firing stance instead of grinding into the rock.
 */
public class SpiderClimbNavigation extends GroundPathNavigation
{
    /** How far above the spider a target has to be before the climb fallback may start. */
    private static final double CLIMB_HEIGHT = 0.5D;

    /** How long the fallback pushes without gaining height before the climb is called impossible. */
    private static final int CLIMB_PATIENCE_TICKS = 10;

    /** How long the fallback stays quiet after that, so a spider does not grind against the same rock forever. */
    private static final int CLIMB_COOLDOWN_TICKS = 100;

    /** How much the spider has to rise before the push counts as a climb that is working. */
    private static final double CLIMB_PROGRESS = 0.05D;

    /** The arrival rule of the vanilla climber: how close to the climbed spot counts as arrived. */
    private static final double ARRIVE_RADIUS = 1.0D;

    /** Where the spider is trying to climb to, or null when it is not climbing at all. */
    @Nullable
    private BlockPos climbTarget;

    /** Ticks the current climb has been pushing without rising. */
    private int patienceTicks;

    /** Ticks left before the fallback may be tried again. */
    private int cooldownTicks;

    /** The highest point the current climb has reached, which is how progress is measured. */
    private double climbBestY;

    public SpiderClimbNavigation(Mob mob, Level level)
    {
        super(mob, level);
    }

    /**
     * Walks to that creature along a real path, or - only when this is a climb - pushes straight at it the way the
     * vanilla spider navigation does.
     *
     * @return true when the spider is now doing something towards that creature
     */
    @Override
    public boolean moveTo(Entity target, double speed)
    {
        Path path = this.createPath(target, 0);

        if (path != null)
        {
            // There is a way to walk: nothing special, exactly like any other creature of the game.
            this.climbTarget = null;

            return this.moveTo(path, speed);
        }

        if (this.cooldownTicks > 0 || !this.mayClimbTo(target))
        {
            // No path and nothing to climb: the spider stays where it is. This is the line that keeps it out of melee -
            // the vanilla spider would throw itself at the target here, through gaps, ponds and walls, until it arrived.
            this.climbTarget = null;

            return false;
        }

        this.climbTarget = target.blockPosition();
        this.speedModifier = speed;
        this.climbBestY = this.mob.getY();
        this.patienceTicks = 0;

        return true;
    }

    @Override
    public void tick()
    {
        if (this.cooldownTicks > 0)
        {
            --this.cooldownTicks;
        }

        if (!this.isDone())
        {
            // A real path is being walked: the ordinary navigation owns the spider.
            super.tick();

            return;
        }

        BlockPos target = this.climbTarget;

        if (target == null)
        {
            return;
        }

        if (this.onWall() || this.mob.getY() > this.climbBestY + CLIMB_PROGRESS)
        {
            // Either the spider is pressed against the wall it is about to climb, or it is already rising along it.
            this.climbBestY = Math.max(this.climbBestY, this.mob.getY());
            this.patienceTicks = 0;
        }
        else if (++this.patienceTicks > CLIMB_PATIENCE_TICKS)
        {
            // Pushing without rising: this is not a wall it can use. The spider lets go, stands and shoots.
            this.abandonClimb();

            return;
        }

        if (this.arrived(target))
        {
            this.climbTarget = null;

            return;
        }

        this.mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), this.speedModifier);
    }

    /** @return true when the spider may try to climb towards that creature at all. */
    private boolean mayClimbTo(Entity target)
    {
        return target.getY() > this.mob.getY() + CLIMB_HEIGHT || this.onWall();
    }

    /**
     * A spider that is told to stand still is not climbing any more either.
     *
     * <p>The shooting goal of this spider calls this the moment it wants to hold its ground, and the push has to end
     * with the order - otherwise the navigation would keep shoving the spider at its victim while the goal is trying to
     * make it stand and shoot.
     */
    @Override
    public void stop()
    {
        super.stop();
        this.climbTarget = null;
        this.patienceTicks = 0;
    }

    /**
     * @return true when the body of this spider is against a wall right now.
     *
     * <p>It asks the spider itself first, because that flag is set by the spider body from exactly the collision the
     * climb needs, and falls back to the plain collision flag of the entity.
     */
    private boolean onWall()
    {
        return this.mob.horizontalCollision
                || this.mob instanceof SpiderMinionEntity spider && spider.isClimbing();
    }

    /** Gives up the current climb and leaves the fallback alone for a while. */
    private void abandonClimb()
    {
        this.climbTarget = null;
        this.patienceTicks = 0;
        this.cooldownTicks = CLIMB_COOLDOWN_TICKS;
    }

    /**
     * The arrival rule of the vanilla climber, including the Forge fix for MC-94054: a spider that has reached the spot
     * lets go of it, even when the spot is the block it is standing on or the one just above it.
     */
    private boolean arrived(BlockPos target)
    {
        double radius = Math.max((double) this.mob.getBbWidth(), ARRIVE_RADIUS);

        if (target.closerToCenterThan(this.mob.position(), radius))
        {
            return true;
        }

        return this.mob.getY() > (double) target.getY() && BlockPos
                .containing(target.getX(), this.mob.getY(), target.getZ())
                .closerToCenterThan(this.mob.position(), radius);
    }
}
