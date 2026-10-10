package spiderqueenhungrycraft.soulfire.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.soulfire.SoulfireBreath;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireOrders;
import spiderqueenhungrycraft.soulfire.SoulfireTargeting;

/**
 * The one goal that commands a soul fire dragon (round 7C).
 *
 * <p>It is the whole behaviour of the unit, and it is deliberately one goal instead of five: a single owner of the
 * movement flag can not fight itself, and the priority of the round is written in one place instead of being spread
 * over half a dozen goals that all believe they are in charge:
 *
 * <ol>
 * <li><b>the breath skill</b> of the ability key (a place the queen pointed at) - it runs until another order
 * arrives,</li>
 * <li><b>the forced victim</b> (a creature the queen pointed at),</li>
 * <li><b>the scout move</b> (the errand: walk when it is close, fly when it is further than fifteen blocks),</li>
 * <li><b>the standing order</b>: follow, hold, patrol or free attack.</li>
 * </ol>
 *
 * <p>Every one of those is a decision of its own and none of them is remembered here: the goal reads them from the
 * entity every tick, so a new command is obeyed in the very tick it arrives - there is no state in this class that
 * could make a unit "finish what it was doing first". The only memory it keeps is the bookkeeping of a single action
 * (a repath timer, a bite timer, a stuck counter), and every one of those is dropped the moment the command changes.
 */
public class SoulfireCommandGoal extends Goal
{
    /** How close to a spot counts as standing at it, in blocks. */
    private static final double SPOT_DISTANCE = 3.0D;

    /** How often a walking unit asks for a fresh path, in ticks. */
    private static final int REPATH_TICKS = 20;

    /** How many ticks of no progress make the goal give an unreachable errand up out loud. */
    private static final int STUCK_LIMIT = 200;

    /** How far a follower may fall behind before it is flown after its queen instead of walked, in blocks. */
    private static final double FOLLOW_MIN_STOP = 14.0D;
    private static final double FOLLOW_RESUME_GAP = 5.0D;
    /** Hysteresis: a stationary dragon stays put until the queen leaves its safe outer ring. */
    private boolean followIdle = false;

    /** How far around its anchor a patrol or a hunt wanders, in blocks. */
    private static final double PATROL_RADIUS = 8.0D;

    /** How often a patrolling unit picks a new point of its rounds, in ticks. */
    private static final int PATROL_TICKS = 120;

    /** How long a bite is rested, in ticks. */
    private static final int BITE_COOLDOWN = 20;

    /** How far sideways a landing site may be chosen from the requested point. */
    private static final int LANDING_SEARCH = 4;

    /** A dragon can start a descent much more than four blocks above the ground. */
    private static final int LANDING_MAX_DROP = 192;

    /** How high over a distant destination an errand flies while it is on its way there, in blocks. */
    private static final double CRUISE_CLEARANCE = 6.0D;

    /** The "no cruising height was chosen yet" value of {@link #cruiseY}. */
    private static final double NO_CRUISE = Double.NaN;

    private final SoulfireDragonEntity dragon;

    private int repathTicks;
    private int patrolTicks;
    private int biteCooldown;
    private int stuckTicks;

    /** The command this goal's bookkeeping belongs to: when it moves on, everything below is dropped at once. */
    private int lastVersion = -1;
    private double lastDistance = Double.MAX_VALUE;
    private Vec3 patrolPoint;

    /**
     * The cruising height of the current errand, decided once when the flight starts (round 7E, stage B1).
     *
     * <p>It used to be recomputed on every tick from {@code max(destination, currentHeight) + 2}, which is a ladder:
     * every tick the unit was two blocks higher than the tick before, and a long MOVE climbed into the sky for as long
     * as it lasted. A height that is chosen once can not do that.
     */
    private double cruiseY = NO_CRUISE;

    /** Throttle expensive terrain searches when no safe landing exists. */
    private int noLandingSearchUntil;

    public SoulfireCommandGoal(SoulfireDragonEntity dragon)
    {
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return true;
    }

    @Override
    public void start()
    {
        this.forget();
    }

    @Override
    public void stop()
    {
        this.dragon.setBreathing(false);
        this.dragon.stopFlight();
    }

    /** Drops every scrap of bookkeeping of the previous command. */
    private void forget()
    {
        this.repathTicks = 0;
        this.patrolTicks = 0;
        this.stuckTicks = 0;
        this.patrolPoint = null;
        this.lastDistance = Double.MAX_VALUE;

        // The cruising height belongs to the command that chose it, so a new command chooses its own.
        this.cruiseY = NO_CRUISE;
        this.noLandingSearchUntil = 0;
        this.followIdle = false;
    }

    @Override
    public void tick()
    {
        if (!(this.dragon.level() instanceof ServerLevel) || this.dragon.isDeadOrDying())
        {
            return;
        }

        if (this.biteCooldown > 0)
        {
            this.biteCooldown--;
        }

        int version = this.dragon.getCommandVersion();

        // <b>The newest command wins, in this very tick.</b> Every command of the sceptre moves the version of the
        // unit forward, so the first tick of a new command drops every trace of the previous one - the repath timer,
        // the patrol point, the stuck counter and the last distance. Nothing of an older order can make this unit
        // "finish what it was doing first".
        if (version != this.lastVersion)
        {
            this.lastVersion = version;
            this.forget();
        }

        // 1. The skill of the ability key: breathe at the place the queen pointed at, until she says otherwise.
        if (this.dragon.hasBreathOrder())
        {
            this.skill(this.dragon.getBreathTarget(), version);

            return;
        }

        // 2. A victim the queen pointed at: bite it if it can be bitten, burn it if it can not.
        LivingEntity victim = this.dragon.getForcedTarget();

        if (victim != null)
        {
            if (SoulfireTargeting.isHuntable(this.dragon, victim))
            {
                this.fight(victim, version);

                return;
            }

            // The victim is gone, or turned into somebody this unit may not touch: the order is over.
            this.dragon.clearTemporaryCommand();
            this.forget();
        }

        // 3. The errand of the scout move.
        if (this.dragon.getMoveTarget() != null)
        {
            this.errand(this.dragon.getMoveTarget(), version);

            return;
        }

        // 4. And nothing else answers: the standing order of the unit.
        this.order(version);
    }

    /**
     * The skill of the ability key.
     *
     * <p><b>The rule of the round, in one method.</b> The place the queen pointed at is looked at from above first: if
     * the column of {@value SoulfireDragonEntity#SKY_HEIGHT} blocks over it is open air, the unit flies to
     * {@value SoulfireDragonEntity#HOVER_HEIGHT} blocks above it, hovers there and sprays the soul fire straight down.
     * If something covers that column, the unit comes down instead, stands on the ground beside the point and breathes
     * at it from there.
     *
     * <p>There is <b>no cooldown</b> and no end of its own: the goal keeps this method running tick after tick until
     * the entity no longer carries a breath order, which is exactly what happens the moment a move, an attack, a post
     * or another skill cast arrives.
     */
    private void skill(Vec3 point, int version)
    {
        if (point == null)
        {
            return;
        }

        if (SoulfireOrders.skyOpen(this.dragon.level(), BlockPos.containing(point)))
        {
            this.hoverOver(point, version);

            return;
        }

        this.standNear(point, version);
    }

    /** The open sky half of the skill: up to fifteen blocks over the point, and then a breath straight down. */
    private void hoverOver(Vec3 point, int version)
    {
        Vec3 perch = point.add(0.0D, SoulfireDragonEntity.HOVER_HEIGHT, 0.0D);
        double horizontal = this.horizontalTo(perch);

        if (horizontal > SPOT_DISTANCE || Math.abs(this.dragon.getY() - perch.y) > 3.0D)
        {
            // Still on the way: the flight of this class steers in a straight line, and the hover waits until the unit
            // is really over the point.
            this.dragon.setHovering(false);
            this.dragon.flyTo(perch, version);

            return;
        }

        this.dragon.stopFlight();
        this.dragon.setFlying(false);
        this.dragon.setHovering(true);
        this.dragon.holdStill();
        SoulfireBreath.tick(this.dragon, point);
    }

    /** The covered half of the skill: down to the ground beside the point, and then a breath at it from there. */
    private void standNear(Vec3 point, int version)
    {
        if (this.dragon.isLanding())
        {
            return;
        }

        Vec3 landing = this.landingSpot(point);
        if (landing == null)
        {
            this.dragon.holdStill();
            return;
        }
        double horizontal = this.horizontalTo(landing);

        if (this.dragon.onGround() && horizontal <= SPOT_DISTANCE
                && Math.abs(this.dragon.getY() - landing.y) < 3.0D)
        {
            this.dragon.stopFlight();
            this.dragon.setFlying(false);
            this.dragon.setHovering(false);
            this.dragon.holdStill();
            SoulfireBreath.tick(this.dragon, point);

            return;
        }

        this.dragon.setHovering(false);

        if (horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.flyTo(landing.add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        if (this.dragon.isFlying() || this.dragon.isHovering())
        {
            this.dragon.landTo(landing, version);

            return;
        }

        this.walkTo(landing, version);
    }

    /** A victim of the round: bitten when it can be reached, burned with soul fire when it can not. */
    private void fight(LivingEntity victim, int version)
    {
        Vec3 centre = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
        double distance = this.dragon.distanceTo(victim);
        boolean airborne = this.dragon.isFlying() || this.dragon.isHovering();

        // 1. Within the jaws: a unit that can reach its victim bites it - on the ground, on the wing or while it
        // hovers, because a dragon does not lose its teeth by leaving the floor.
        if (distance <= SoulfireDragonEntity.MELEE_REACH && this.dragon.getY() - centre.y < 2.5D
                && this.dragon.hasLineOfSight(victim))
        {
            this.bite(victim);

            return;
        }

        // 2. Far away: fly at it in a straight line, which is the whole flight rule of this round - no circles, no
        // climb to a cruising altitude, just the line between the two.
        if (!airborne && this.distanceToFar(victim) > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.getNavigation().stop();
            this.dragon.flyTo(centre, version);

            return;
        }

        if (airborne)
        {
            // In the air the unit closes in, and it hangs in the air and breathes the moment the victim is in range.
            if (distance > SoulfireDragonEntity.MELEE_REACH && distance > SoulfireDragonEntity.BREATH_RANGE * 0.5D)
            {
                this.dragon.flyTo(centre, version);
            }

            if (distance <= SoulfireDragonEntity.BREATH_RANGE)
            {
                this.dragon.stopFlight();
                this.dragon.setFlying(false);
                this.dragon.setHovering(true);
                this.dragon.holdStill();
                SoulfireBreath.tick(this.dragon, centre);
            }

            return;
        }

        if (distance <= SoulfireDragonEntity.BREATH_RANGE)
        {
            // On the ground and in range of the breath: stand still and burn it, which is what a dragon does with
            // everything it can not reach with its teeth.
            this.dragon.holdStill();
            SoulfireBreath.tick(this.dragon, centre);

            return;
        }

        this.walkTo(victim.position(), version);
    }

    /** @return how far the victim is, counted from the floor the unit stands on. */
    private double distanceToFar(LivingEntity victim)
    {
        return this.horizontalTo(victim.position());
    }

    /**
     * The melee half: a bite, on a cooldown, and only when the victim is really within the jaws.
     *
     * <p>The flight is ended without touching the gravity of the unit in the air: a unit that bites while it hovers
     * keeps hovering, and the goal that owns the standing order brings it back down.
     */
    private void bite(LivingEntity victim)
    {
        this.dragon.stopFlight();
        this.dragon.getNavigation().stop();
        this.dragon.getLookControl().setLookAt(victim, 30.0F, 30.0F);
        this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().scale(0.35D));

        if (this.biteCooldown <= 0 && this.dragon.startBite(victim))
        {
            // Round 7H: the jaws do not hurt where they stand. The bite is an animation of the real head and neck bones
            // and the damage falls in its middle ({@link SoulfireDragonEntity#BITE_HIT_TICK}), so what the player sees is
            // what happens. The cooldown already covers the whole bite, so a second one can not start on top of it.
            this.biteCooldown = BITE_COOLDOWN * 2;
        }
    }

    /**
     * The scout move: close by it is walked (the ordinary navigation of the game, with its own pathfinder), further
     * away than {@value SoulfireDragonEntity#FLY_DISTANCE} blocks it is <b>flown in a straight line</b> - never with the
     * circles, climbs and hovers the dragon AI of the other mod would take.
     *
     * <p>When the errand is reached it is dropped, and the unit falls back to its standing order: an errand is
     * something to do, never a mode to be stuck in.
     */
    private void errand(Vec3 target, int version)
    {
        double horizontal = this.horizontalTo(target);
        boolean airborne = this.dragon.isFlying() || this.dragon.isHovering();

        if (!airborne && horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            // Taking off. The cruising height of this errand is decided <b>here, once</b>, and the flight of every tick
            // after this one aims at that same height: no tick can push the goal post up, which is what used to make a
            // long MOVE climb without end.
            this.dragon.getNavigation().stop();
            this.dragon.flyTo(new Vec3(target.x, this.cruiseHeight(target.y), target.z), version);

            return;
        }

        if (airborne)
        {
            if (this.dragon.isLanding())
            {
                // Already coming down onto the destination: the descent owns the movement now.
                return;
            }

            if (horizontal > SoulfireDragonEntity.ARRIVE_DISTANCE)
            {
                // The straight line to the destination, at the one height this errand chose. An obstacle is still
                // handled by the one temporary waypoint of the flight executor, and the unit returns to this same line
                // the moment the way is clear.
                this.dragon.flyTo(new Vec3(target.x, this.cruiseHeight(target.y), target.z), version);

                return;
            }

            // Over the destination: look for actual ground, then complete a controlled descent.
            this.beginLanding(target, version);

            return;
        }

        if (horizontal <= SoulfireDragonEntity.ARRIVE_DISTANCE
                && Math.abs(this.dragon.getY() - target.y) < 3.0D)
        {
            this.dragon.getNavigation().stop();
            this.dragon.clearTemporaryCommand();
            this.forget();

            return;
        }

        this.walkTo(target, version);
        this.watchProgress(horizontal, target);
    }

    /**
     * @return the height this errand flies at: high enough to clear ordinary terrain over the destination, and never
     *         lower than where the unit took off from - <b>chosen once per command</b> and remembered until it changes.
     */
    private double cruiseHeight(double destinationY)
    {
        if (Double.isNaN(this.cruiseY))
        {
            this.cruiseY = Math.max(destinationY + CRUISE_CLEARANCE, this.dragon.getY());
        }

        return this.cruiseY;
    }

    /**
     * Sends the unit towards a point on the ground, and <b>the only way this goal is allowed to walk</b>.
     *
     * <p>Round 7E stage B3: a unit in the air is <b>not</b> handed a ground path, because that path is a command the
     * game would never carry out while the flight executor owns the movement - the navigation would report a path that
     * is running while the unit flies somewhere else. A unit that is told to walk while it is in the air first comes
     * down, in a line, and is only handed to the navigation once it really stands on a floor.
     */
    private void walkTo(Vec3 target, int version)
    {
        if (this.dragon.isLanding())
        {
            return;
        }
        if (this.dragon.isFlying() || this.dragon.isHovering())
        {
            this.beginLanding(target, version);
            return;
        }

        if (this.repathTicks <= 0 || this.dragon.getNavigation().isDone())
        {
            this.dragon.requestWalkTo(target);
            this.repathTicks = REPATH_TICKS;
        }
        else
        {
            this.repathTicks--;
        }
    }

    /** Counts the ticks without progress, and gives an unreachable errand up out loud instead of walking forever. */
    private void watchProgress(double horizontal, Vec3 target)
    {
        if (horizontal < this.lastDistance - 0.5D)
        {
            this.lastDistance = horizontal;
            this.stuckTicks = 0;

            return;
        }

        this.stuckTicks++;

        if (this.stuckTicks > STUCK_LIMIT)
        {
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.info(
                    "soulfire dragon {} can not reach its move order ({} blocks left, target {}): standing down",
                    this.dragon.getUUID(), (int) horizontal, target);
            this.dragon.clearTemporaryCommand();
            this.forget();
        }
    }

    /** @return the horizontal distance from the unit to that point. */
    private double horizontalTo(Vec3 point)
    {
        return Math.sqrt(this.dragon.distanceToSqr(point.x, this.dragon.getY(), point.z));
    }

    /**
     * Requests a landing ONCE, never using a mid-air coordinate as a fake landing place.
     * If the loaded terrain has no room for the full hitbox, the dragon hovers and
     * retries on the next command tick rather than entering a dead LANDING state.
     */
    private void beginLanding(Vec3 near, int version)
    {
        if (this.dragon.isLanding())
        {
            return;
        }
        Vec3 safe = this.landingSpot(near);
        if (safe == null)
        {
            this.dragon.holdStill();
            if (this.dragon.tickCount % 100 == 0)
            {
                spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.warn(
                        "[Soulfire7F] no loaded safe landing surface near {} for {}",
                        near, this.dragon.getUUID());
            }
            return;
        }
        this.dragon.landTo(safe, version);
    }

    /**
     * Searches downward from the dragon's REAL flying altitude. The original
     * implementation checked only four blocks below the destination and then
     * returned an arbitrary point in the air, which produced infinite flapping.
     *
     * An eligible position needs a full floor, empty fluid, AND enough collision-
     * free volume for the dragon's actual AABB. Never loads new chunks.
     */
    @Nullable
    private Vec3 landingSpot(Vec3 near)
    {
        if (this.dragon.tickCount < this.noLandingSearchUntil)
        {
            return null;
        }
        int top = (int) Math.floor(this.dragon.getY());
        int bottom = Math.max(this.dragon.level().getMinBuildHeight() + 1, top - LANDING_MAX_DROP);
        BlockPos centre = BlockPos.containing(near);

        for (int y = top; y >= bottom; y--)
        {
            for (int radius = 0; radius <= LANDING_SEARCH; radius++)
            {
                for (int dx = -radius; dx <= radius; dx++)
                {
                    for (int dz = -radius; dz <= radius; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
                        {
                            continue; // each square ring only once
                        }
                        BlockPos feet = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
                        if (!this.dragon.level().hasChunkAt(feet)
                                || !this.dragon.level().hasChunkAt(feet.below()))
                        {
                            continue;
                        }
                        if (!this.dragon.level().getBlockState(feet.below())
                                .isFaceSturdy(this.dragon.level(), feet.below(), Direction.UP)
                                || !this.dragon.level().getFluidState(feet).isEmpty())
                        {
                            continue;
                        }
                        Vec3 at = Vec3.atBottomCenterOf(feet);
                        AABB atBody = this.dragon.getBoundingBox().move(
                                at.x - this.dragon.getX(), at.y - this.dragon.getY(), at.z - this.dragon.getZ());
                        if (this.dragon.level().noCollision(this.dragon, atBody))
                        {
                            return at;
                        }
                    }
                }
            }
        }
        this.noLandingSearchUntil = this.dragon.tickCount + 40;
        return null;
    }

    // ------------------------------------------------------------------------------------------------------------
    // The standing order: the fallback the unit returns to when no temporary command is running
    // ------------------------------------------------------------------------------------------------------------

    private void order(int version)
    {
        switch (this.dragon.getOrderMode())
        {
            case FOLLOW -> this.follow(version);
            case HOLD -> this.hold(version);
            case PATROL -> this.patrol(version);
            case FREE_ATTACK -> this.hunt(version);
        }
    }

    /** Follow: walk with the queen, and fly after her when she is more than fifteen blocks away. */
    private void follow(int version)
    {
        Player queen = this.queen();

        if (queen == null || !queen.isAlive() || queen.level() != this.dragon.level())
        {
            this.stand(version);

            return;
        }

        Vec3 difference = this.dragon.position().subtract(queen.position());
        Vec3 lateral = new Vec3(difference.x, 0.0D, difference.z);
        double distance = lateral.length();
        // Stage-4 dragon is 4.5x Tabula scale, with wings far outside its central AABB.
        // The centre must stop outside the visible dragon, not at the player's feet.
        double stop = Math.max(FOLLOW_MIN_STOP, this.dragon.getBbWidth() * 4.0D + 6.0D);
        double restart = stop + FOLLOW_RESUME_GAP;

        if (distance <= stop || (this.followIdle && distance <= restart))
        {
            this.followIdle = true;
            this.stand(version);
            return;
        }

        this.followIdle = false;
        // Keep a formation ring centred on the queen. Never navigate to queen.position().
        Vec3 outwards = distance > 1.0E-3D ? lateral.scale(1.0D / distance)
                : new Vec3(Math.cos(Math.toRadians(queen.getYRot())), 0,
                        Math.sin(Math.toRadians(queen.getYRot())));
        Vec3 formation = queen.position().add(outwards.scale(stop));
        double gap = Math.max(0.0D, distance - stop);

        if (gap > SoulfireDragonEntity.FLY_DISTANCE || this.dragon.isFlying() || this.dragon.isHovering())
        {
            // Once flying, approach the offset point, then LAND there through stand().
            this.dragon.flyTo(formation.add(0.0D, 3.0D, 0.0D), version);
            return;
        }

        this.walkTo(formation, version);
    }

    /** Hold: stand on the post, and sit down on it once it is reached - the pose of a garrison. */
    private void hold(int version)
    {
        BlockPos post = this.dragon.getAnchor();

        if (post == null)
        {
            this.dragon.setAnchor(this.dragon.blockPosition());
            this.stand(version);

            return;
        }

        Vec3 spot = Vec3.atBottomCenterOf(post);
        double horizontal = this.horizontalTo(spot);

        if (horizontal <= SPOT_DISTANCE && this.dragon.onGround())
        {
            // An anchor selected while airborne must not command a second takeoff
            // just because the nearest safe ground is far below its saved Y value.
            this.dragon.setAnchor(this.dragon.blockPosition());
            this.dragon.stopFlight();
            this.dragon.setFlying(false);
            this.dragon.setHovering(false);
            this.dragon.setSitting(true);
            this.dragon.getNavigation().stop();
            return;
        }

        this.dragon.setSitting(false);

        if (horizontal > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.dragon.flyTo(spot.add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        this.walkTo(spot, version);
    }

    /** Patrol: walk the rounds around the anchor, and fly back to it when the unit has drifted too far. */
    private void patrol(int version)
    {
        BlockPos post = this.dragon.getAnchor();

        if (post == null)
        {
            this.dragon.setAnchor(this.dragon.blockPosition());
            this.stand(version);

            return;
        }

        this.dragon.setSitting(false);

        if (this.horizontalTo(Vec3.atBottomCenterOf(post)) > SoulfireDragonEntity.FLY_DISTANCE)
        {
            this.patrolPoint = null;
            this.dragon.flyTo(Vec3.atBottomCenterOf(post).add(0.0D, 2.0D, 0.0D), version);

            return;
        }

        if (this.patrolPoint == null || this.patrolTicks++ > PATROL_TICKS
                || this.horizontalTo(this.patrolPoint) <= SPOT_DISTANCE)
        {
            double angle = this.dragon.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = 2.0D + this.dragon.getRandom().nextDouble() * PATROL_RADIUS;
            this.patrolPoint = new Vec3(post.getX() + 0.5D + Math.cos(angle) * radius, post.getY(),
                    post.getZ() + 0.5D + Math.sin(angle) * radius);
            this.patrolTicks = 0;
        }

        this.walkTo(this.patrolPoint, version);
    }

    /** Free attack: hunt whatever stands around the anchor that is not of the queen's own side. */
    private void hunt(int version)
    {
        LivingEntity victim = this.dragon.getTarget();

        if (victim != null && SoulfireTargeting.isHuntable(this.dragon, victim))
        {
            this.fight(victim, version);

            return;
        }

        // Nothing to hunt right now: the rounds of a patrol, which is what a hunter does between two victims.
        this.patrol(version);
    }

    /** Comes down where it is, and then stands absolutely still: the pose of a unit with nothing to do. */
    private void stand(int version)
    {
        if (this.dragon.isLanding())
        {
            return;
        }
        if (this.dragon.isFlying() || this.dragon.isHovering())
        {
            this.beginLanding(this.dragon.position(), version);
            return;
        }

        this.dragon.getNavigation().stop();
        this.dragon.setDeltaMovement(this.dragon.getDeltaMovement().multiply(0.6D, 1.0D, 0.6D));
    }

    /** @return the player who holds the contract of the unit, or null when that player is not in this level. */
    @Nullable
    private Player queen()
    {
        java.util.UUID owner = this.dragon.getOwnerUuid();

        return owner == null ? null : this.dragon.level().getPlayerByUUID(owner);
    }
}