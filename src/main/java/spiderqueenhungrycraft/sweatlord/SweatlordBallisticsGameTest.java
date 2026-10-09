package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.sweatlord.MovingTargetBallistics.ProjectileProfile;
import spiderqueenhungrycraft.sweatlord.MovingTargetBallistics.Solution;

/**
 * The unified ballistics of the bow, the egg and the trident (Sweatlord V1.1).
 *
 * <p>The claim this file has to carry is short and hard: <b>a moving target is hit, whatever the weapon, and whether or
 * not the shooter is walking himself.</b> Six of the tests are that claim, one per weapon and per shooter state, and
 * each of them refuses to pass on a scenario that would prove nothing:
 *
 * <ul>
 * <li>the target really strafes, at a real velocity, <b>sideways</b> to the line of the shot,</li>
 * <li>and the flight is long enough that the drift the target covers during it is at least
 * {@link #REQUIRED_DRIFT} blocks - so a straight aimed shot (which is what the round could do before) <b>could not</b>
 * have hit, and the hit can only come from a real intercept.</li>
 * </ul>
 *
 * <p>Two more tests own the two things a ballistic prediction can not do by itself: the small in-flight correction of
 * {@code MovingTargetBallistics.GuidedShot}, and the arithmetic of the solver itself, verified with an independent
 * simulation in this file.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordBallisticsGameTest
{
    /** The floor of the test site and the half width of its flat area. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 40;

    /** A corner of the test world of its own, clear of every other ballistics site. */
    private static final int SITE_X = 2048;
    private static final int SITE_Z = 1024;

    /** The range of the scenario: long enough for a real flight time and a real drop. */
    private static final double RANGE = 18.0D;

    /** How fast the target strafes, in blocks per tick: a walking player, not a sprinting one. */
    private static final double TARGET_SPEED = 0.3D;

    /** How fast the shooter himself walks while he fires. */
    private static final double SHOOTER_SPEED = 0.2D;

    /**
     * The lateral drift the target has to cover during the flight of the shot. Without this the test would pass for a
     * straight aimed shot as well, and would prove nothing at all about the intercept.
     */
    private static final double REQUIRED_DRIFT = 1.5D;

    // -------------------------------------------------------------------------------------------------------------
    // the six claims: three weapons, standing shooter and walking shooter
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void bowHitsAMovingTargetFromAStandingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.BOW, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void bowHitsAMovingTargetFromAWalkingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.BOW, true);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void eggReachesAMovingTargetFromAStandingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.EGG, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void eggReachesAMovingTargetFromAWalkingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.EGG, true);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void tridentHitsAMovingTargetFromAStandingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.TRIDENT, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 200)
    public static void tridentHitsAMovingTargetFromAWalkingShooter(GameTestHelper helper)
    {
        run(helper, ProjectileProfile.TRIDENT, true);
    }

    /** Builds the scenario of one weapon and one shooter state: one shot, a strafing target, and a real judgement. */
    private static void run(GameTestHelper helper, ProjectileProfile profile, boolean shootingWhileWalking)
    {
        // Every scenario gets its own corner of this file's area: every test of a run shares one world, and the bodies of
        // an earlier test are still standing when the next one starts. A hundred blocks apart keeps the line of fire of
        // one scenario out of the pad of another.
        int cornerX = profile == ProjectileProfile.BOW ? 0 : profile == ProjectileProfile.EGG ? 100 : 200;
        Site site = openSite(helper, cornerX, shootingWhileWalking ? 120 : 0);
        SweatlordSteveEntity shooter = spawnShooter(site, helper);
        Cow target = spawnTarget(site, helper);

        if (shooter == null || target == null)
        {
            return;
        }

        helper.onEachTick(new ShotProbe(helper, site.level(), shooter, target, profile, shootingWhileWalking)::tick);
    }

    /** One prepared site of this file: the level of the test and the point every body is placed around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    private SweatlordBallisticsGameTest()
    {
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X + offsetX, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);

        for (int cx = -2; cx <= 2; ++cx)
        {
            for (int cz = -2; cz <= 2; ++cz)
            {
                level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, true);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SITE_HALF_WIDTH; dx <= SITE_HALF_WIDTH; ++dx)
        {
            for (int dz = -SITE_HALF_WIDTH; dz <= SITE_HALF_WIDTH; ++dz)
            {
                level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y - 1, centre.getZ() + dz),
                        Blocks.STONE.defaultBlockState(), 2);

                for (int dy = 0; dy <= 5; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return new Site(level, centre);
    }

    /**
     * Places the shooter of this file. Its brain is switched off on purpose: this file measures the ballistics and never
     * the fight, so the body only does what the test tells it to do, and a wandering goal can not move the launch point
     * behind the back of the measurement.
     */
    @Nullable
    private static SweatlordSteveEntity spawnShooter(Site site, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(), site.centre(),
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        sweatlord.setNoAi(true);

        return sweatlord;
    }

    /**
     * Gives that body a real, constant sideways velocity, which is what a strafing player looks like.
     *
     * <p><b>Both halves are needed, and the first run of this file proved it.</b> The velocity is what the solver and the
     * in-flight correction read, and the step is what the world really sees. A body whose delta movement is set but
     * which is never moved would make the whole scenario a lie: the solver would lead a target that never goes anywhere,
     * every shot would land where the target <i>would</i> have been, and the file would report a miss for ballistics
     * that are perfectly right. That is exactly what happened - {@code Mob#setNoAi} does not stop the body from moving
     * itself, it only stops its AI, and the two are not the same thing.
     */
    private static void strafe(Mob mob, double speed)
    {
        mob.setDeltaMovement(0.0D, mob.getDeltaMovement().y, speed);
        mob.setPos(mob.getX(), mob.getY(), mob.getZ() + speed);
    }

    /**
     * Places the target of this file: a cow, and on purpose not a body the Sweatlord would ever fight. Nothing about the
     * ballistics needs an enemy - only a real body with a real bounding box, real health and a real velocity.
     */
    @Nullable
    private static Cow spawnTarget(Site site, GameTestHelper helper)
    {
        Cow cow = EntityType.COW.create(site.level());

        if (cow == null)
        {
            helper.fail("the target of the scenario could not be created");
            return null;
        }

        cow.moveTo(site.centre().getX() + RANGE + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + 0.5D, 180.0F, 0.0F);
        cow.setNoAi(true);
        cow.setPersistenceRequired();

        if (cow.getAttribute(Attributes.MAX_HEALTH) != null)
        {
            cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            cow.setHealth(500.0F);
        }

        site.level().addFreshEntity(cow);

        return cow;
    }

    /**
     * The arithmetic of the solver, verified by an <b>independent</b> simulation written in this file: for every profile
     * the solved shot really touches the future box of a strafing body, and the straight aimed shot - the one the round
     * could do before - does not.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 40)
    public static void solverLeadsTheInterceptOfAllThreeProfiles(GameTestHelper helper)
    {
        Vec3 from = new Vec3(0.5D, SITE_FLOOR_Y + 1.6D, 0.5D);
        Vec3 targetCenter = new Vec3(RANGE + 0.5D, SITE_FLOOR_Y + 0.7D, 0.5D);
        Vec3 targetVelocity = new Vec3(0.0D, 0.0D, TARGET_SPEED);
        AABB targetBox = new AABB(RANGE, SITE_FLOOR_Y, 0.05D, RANGE + 1.0D, SITE_FLOOR_Y + 1.4D, 0.95D);
        ProjectileProfile[] profiles = { ProjectileProfile.BOW, ProjectileProfile.EGG, ProjectileProfile.TRIDENT };

        for (ProjectileProfile profile : profiles)
        {
            Solution leaded = MovingTargetBallistics.solve(from, targetCenter, targetVelocity, targetBox, profile);
            Solution straight = MovingTargetBallistics.solve(from, targetCenter, Vec3.ZERO, targetBox, profile);

            if (leaded == null || straight == null)
            {
                helper.fail(profile.id() + ": a target " + RANGE + " blocks away could not be solved at all");
                return;
            }

            double leadedClosest = closestApproach(from, leaded.velocity(), profile, targetBox, targetVelocity);
            double straightClosest = closestApproach(from, straight.velocity(), profile, targetBox, targetVelocity);
            double lead = leaded.aimPoint().distanceTo(targetCenter);
            double drift = targetVelocity.horizontalDistance() * (leaded.flightTicks() - 1);

            if (leadedClosest > 0.05D)
            {
                helper.fail(profile.id() + ": the solved shot does not really intercept: closest=" + leadedClosest
                        + " blocks, flight=" + leaded.flightTicks() + " ticks, lead=" + lead);
                return;
            }

            if (straightClosest < drift * 0.5D)
            {
                helper.fail(profile.id() + ": the straight aimed shot reaches the target as well (closest="
                        + straightClosest + " against a drift of " + drift + "), so this scenario can not tell the"
                        + " intercept from a straight line");
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[BallisticsTest] {}: flight={} ticks, lead={} blocks, solved closest={},"
                            + " straight closest={}, drift={}", profile.id(), leaded.flightTicks(), lead,
                    leadedClosest, straightClosest, drift);
        }

        helper.succeed();
    }

    /**
     * @return the closest the arc of that velocity comes to the future box of the target, followed with the real physics
     *         of the profile. This is an independent check: it never calls the solver, it re-simulates the flight.
     */
    private static double closestApproach(Vec3 from, Vec3 velocity, ProjectileProfile profile, AABB box,
            Vec3 targetVelocity)
    {
        Vec3 position = from;
        Vec3 motion = velocity;
        double closest = Double.MAX_VALUE;

        for (int tick = 1; tick <= profile.maxFlightTicks(); ++tick)
        {
            Vec3 next = position.add(motion);
            AABB future = box.move(targetVelocity.scale(tick)).inflate(MovingTargetBallistics.TARGET_INFLATION);

            if (future.clip(position, next).isPresent())
            {
                return 0.0D;
            }

            closest = Math.min(closest, future.getCenter().distanceTo(next));
            position = next;
            motion = new Vec3(motion.x * profile.drag(), motion.y * profile.drag() - profile.gravity(),
                    motion.z * profile.drag());
        }

        return closest;
    }

    /**
     * The small in-flight correction, and the two properties that keep it honest.
     *
     * <p><b>It helps when the target dodges.</b> A shot is aimed at where the target <b>is</b>, the target then strafes
     * away, and the guided shot has to end up on the body while the unguided control that was fired with the very same
     * velocity - the same tick, the same speed - does not. One launch, one difference: the correction itself.
     *
     * <p><b>And it is invisible when it is not needed.</b> A shot that is solved for the real motion of a target that
     * keeps its course must be left <b>completely untouched</b> while it flies, and must still hit: a correction that
     * steers a shot which was already aimed correctly is exactly the homing missile this round forbids.
     *
     * <p>What it can not do is stated here and not hidden: a target that changes its course after the release can only
     * be nudged back by {@code MAX_TOTAL_TURN_DEGREES} in total, so a large or a very late change still beats the shot.
     * That is a limit of physics - nobody knows a player's next input - and not a property this class pretends to have.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ballistics", timeoutTicks = 400)
    public static void guidanceHelpsAgainstADodgeAndLeavesAnOnCourseShotAlone(GameTestHelper helper)
    {
        Site site = openSite(helper, 300, 240);
        SweatlordSteveEntity shooter = spawnShooter(site, helper);
        Cow target = spawnTarget(site, helper);

        if (shooter == null || target == null)
        {
            return;
        }

        helper.onEachTick(new GuidanceProbe(helper, shooter, target)::tick);
    }

    /**
     * The two phases of the guidance test: a dodged shot that has to be helped, and an on-course shot that must not be
     * touched at all.
     */
    private static final class GuidanceProbe
    {
        /** Where the dodged pair of arrows is fired. */
        private static final int DODGE_TICK = 20;

        /** Where the on-course shot is fired, at a target that is already strafing. */
        private static final int ON_COURSE_TICK = 70;

        /** How long one phase is followed. */
        private static final int PHASE_TICKS = 30;

        /** The verdict. */
        private static final int JUDGE_TICK = ON_COURSE_TICK + PHASE_TICKS + 10;

        /** The reach a guided shot has to improve by: a nudge that changes nothing is not a correction. */
        private static final double MIN_HELP = 0.2D;

        /** The reach the guided shot has to arrive inside: the body, and not a near miss behind it. */
        private static final double ARRIVAL_DISTANCE = 0.75D;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity shooter;
        private final Cow target;
        private final float healthBefore;

        private int ticks;
        private double controlClosest = Double.MAX_VALUE;
        private double guidedClosest = Double.MAX_VALUE;
        private double onCourseClosest = Double.MAX_VALUE;
        private double maxTurnedOnTheDodge;
        private double maxTurnedOnCourse;
        private Arrow control;
        private Arrow guided;
        private Arrow onCourse;
        private boolean judged;

        GuidanceProbe(GameTestHelper helper, SweatlordSteveEntity shooter, Cow target)
        {
            this.helper = helper;
            this.shooter = shooter;
            this.target = target;
            this.healthBefore = target.getHealth();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            // The dodge: the target only starts to strafe after the pair of arrows is already in the air, which is the
            // case a ballistic prediction can not know about in advance.
            if (this.ticks > DODGE_TICK)
            {
                strafe(this.target, TARGET_SPEED);
            }

            if (this.ticks == DODGE_TICK)
            {
                fireTheDodge();
            }

            if (this.ticks == ON_COURSE_TICK)
            {
                fireTheOnCourseShot();
            }

            if (this.control != null)
            {
                this.controlClosest = Math.min(this.controlClosest, closest(this.control));
            }

            if (this.guided != null)
            {
                this.guidedClosest = Math.min(this.guidedClosest, closest(this.guided));
            }

            if (this.onCourse != null)
            {
                this.onCourseClosest = Math.min(this.onCourseClosest, closest(this.onCourse));
            }

            // The turn the correction applied, read from the live guided shots of the shooter.
            for (MovingTargetBallistics.GuidedShot shot : this.shooter.guidedShots())
            {
                if (this.ticks <= DODGE_TICK + PHASE_TICKS)
                {
                    this.maxTurnedOnTheDodge = Math.max(this.maxTurnedOnTheDodge, shot.turnedDegrees());
                }
                else
                {
                    this.maxTurnedOnCourse = Math.max(this.maxTurnedOnCourse, shot.turnedDegrees());
                }
            }

            if (this.ticks >= JUDGE_TICK)
            {
                judge();
            }
        }

        /** Fires the pair of arrows at where the target <b>is</b>: one plain, one with the correction attached. */
        private void fireTheDodge()
        {
            AABB box = this.target.getBoundingBox();
            Vec3 velocity = MovingTargetBallistics.solveAim(ProjectileBallistics.eyeOf(this.shooter), box.getCenter(),
                    Vec3.ZERO, box, ProjectileProfile.BOW);

            if (velocity == null)
            {
                this.judged = true;
                this.helper.fail("the straight aim of the dodge scenario could not even be solved");
                return;
            }

            this.control = fire(velocity, false);
            this.guided = fire(velocity, true);
        }

        /** Fires one shot solved for the real motion of a target that is already strafing, with the correction on. */
        private void fireTheOnCourseShot()
        {
            AABB box = this.target.getBoundingBox();
            Vec3 velocity = MovingTargetBallistics.solveAim(ProjectileBallistics.eyeOf(this.shooter), box.getCenter(),
                    this.target.getDeltaMovement(), box, ProjectileProfile.BOW);

            if (velocity == null)
            {
                this.judged = true;
                this.helper.fail("the on course shot of the guidance scenario could not be solved");
                return;
            }

            this.onCourse = fire(velocity, true);
        }

        private Arrow fire(Vec3 velocity, boolean guided)
        {
            Arrow arrow = new Arrow(this.shooter.level(), this.shooter);
            arrow.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), 0.0F);
            this.shooter.level().addFreshEntity(arrow);

            if (guided)
            {
                MovingTargetBallistics.attach(this.shooter, arrow, this.target, ProjectileProfile.BOW);
            }

            return arrow;
        }

        private double closest(Arrow arrow)
        {
            return this.target.getBoundingBox().getCenter().distanceTo(arrow.position());
        }

        private void judge()
        {
            this.judged = true;

            if (this.control == null || this.guided == null || this.onCourse == null)
            {
                this.helper.fail("one of the phases of the guidance scenario never fired");
                return;
            }

            if (this.controlClosest < 1.0D)
            {
                this.helper.fail("the unguided control of the dodge scenario came within " + describe(this.controlClosest)
                        + " blocks anyway, so the dodge was too small to tell the correction from luck");
                return;
            }

            // The claim of this round is a nudge and not a homing missile: POST-LAUNCH DIRECTION CHANGE IS NOT
            // GUARANTEED. A body that dodges after the release can not be predicted by anybody, so what this scenario
            // refuses is a correction that changes nothing at all - the corrected shot has to come measurably closer than
            // the very same launch without it - and a correction that turns the shot by more than its small budget.
            if (this.guidedClosest > this.controlClosest - MIN_HELP)
            {
                this.helper.fail("the corrected shot came no closer than " + describe(this.guidedClosest)
                        + " blocks to a target that dodged, while the unguided control of the very same launch was at "
                        + describe(this.controlClosest) + " blocks: the correction did not help");
                return;
            }

            if (this.maxTurnedOnTheDodge <= 0.3D)
            {
                this.helper.fail("the correction never turned the dodged shot at all ("
                        + describe(this.maxTurnedOnTheDodge) + " degrees), so the arrival above had another cause");
                return;
            }

            if (this.maxTurnedOnTheDodge > MovingTargetBallistics.MAX_TOTAL_TURN_DEGREES + 0.01D)
            {
                this.helper.fail("the correction turned the dodged shot by " + describe(this.maxTurnedOnTheDodge)
                        + " degrees, which is more than the total budget of this round ("
                        + MovingTargetBallistics.MAX_TOTAL_TURN_DEGREES + "): that is a missile, not a nudge");
                return;
            }

            if (this.maxTurnedOnCourse > 0.0D)
            {
                this.helper.fail("a shot that was solved for the real motion of its target was still steered by "
                        + describe(this.maxTurnedOnCourse) + " degrees, so the correction meddles with shots that are"
                        + " on course already");
                return;
            }

            if (this.target.getHealth() >= this.healthBefore)
            {
                this.helper.fail("the untouched shot did not even hit a target that kept its course (closest "
                        + describe(this.onCourseClosest) + " blocks), so the flight above was not a working shot");
                return;
            }

            if (this.onCourseClosest > ARRIVAL_DISTANCE && this.target.getHealth() >= this.healthBefore)
            {
                this.helper.fail("the shot that was solved for the real motion of its strafing target still did not"
                        + " arrive (closest " + describe(this.onCourseClosest) + " blocks), so the intercept of this"
                        + " round does not work where it has to");
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[BallisticsTest] the correction helped a dodged shot ({} -> {} blocks,"
                            + " turned {} degrees) and left an on course shot untouched (closest {} blocks)",
                    describe(this.controlClosest), describe(this.guidedClosest), describe(this.maxTurnedOnTheDodge),
                    describe(this.onCourseClosest));
            this.helper.succeed();
        }

        private static String describe(double value)
        {
            return value == Double.MAX_VALUE ? "never near"
                    : String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }

    /**
     * One shot of one weapon at one strafing target, judged on the real geometry of the world.
     *
     * <p>It is deliberately not the fight of the brain: the shot is fired through {@code PlayerLikeActions}, which is the
     * very same call the bow, the egg and the trident skills use, so what is measured here is the production ballistics
     * and nothing else.
     */
    private static final class ShotProbe
    {
        /** The tick the shot leaves on, after the bodies have settled. */
        private static final int SHOT_TICK = 20;

        /** How long the flight is followed before the verdict. */
        private static final int WATCH_TICKS = 60;

        /** The inflation the game itself uses when it asks whether a projectile touched a body. */
        private static final double HIT_INFLATE = 0.3D;

        /** How close the flight of an egg has to come: an egg of the vanilla game deals no damage at all. */
        private static final double EGG_REACH_DISTANCE = 0.75D;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity shooter;
        private final Cow target;
        private final ProjectileProfile profile;
        private final boolean shootingWhileWalking;

        private int ticks;
        private boolean fired;
        private boolean lateralMotion;
        private double drift;
        private float healthBefore;
        private Projectile shot;
        private Vec3 lastPosition;
        private double targetZAtShot;
        private double targetMoved;
        private double closest = Double.MAX_VALUE;
        private boolean touched;
        private boolean judged;

        ShotProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity shooter, Cow target,
                ProjectileProfile profile, boolean shootingWhileWalking)
        {
            this.helper = helper;
            this.shooter = shooter;
            this.target = target;
            this.profile = profile;
            this.shootingWhileWalking = shootingWhileWalking;
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            if (this.shootingWhileWalking)
            {
                strafe(this.shooter, SHOOTER_SPEED);
            }

            // The target strafes across the line of the shot for the whole scenario, so the velocity the solver reads at
            // the release is the velocity it really keeps.
            strafe(this.target, TARGET_SPEED);

            if (this.ticks == SHOT_TICK)
            {
                fire();
            }

            track();

            if (this.fired && this.ticks > SHOT_TICK + WATCH_TICKS)
            {
                judge();
            }
        }

        private void fire()
        {
            this.fired = true;
            this.healthBefore = this.target.getHealth();
            this.drift = driftOverTheFlight();

            Vec3 motion = this.target.getDeltaMovement();
            this.lateralMotion = Math.abs(motion.z) >= TARGET_SPEED * 0.5D && Math.abs(motion.x) <= 0.1D;

            if (this.profile == ProjectileProfile.BOW)
            {
                this.shot = PlayerLikeActions.shootArrow(this.shooter, this.target);
            }
            else if (this.profile == ProjectileProfile.EGG)
            {
                this.shot = PlayerLikeActions.throwEgg(this.shooter, this.target);
            }
            else
            {
                this.shot = PlayerLikeActions.throwTrident(this.shooter, this.target);
            }

            this.targetZAtShot = this.target.getZ();
            this.lastPosition = this.shot.position();
        }

        /** @return the lateral way the target will cover during the solved flight of this profile. */
        private double driftOverTheFlight()
        {
            AABB box = this.target.getBoundingBox();
            Solution solution = MovingTargetBallistics.solve(ProjectileBallistics.eyeOf(this.shooter), box.getCenter(),
                    this.target.getDeltaMovement(), box, this.profile);
            int flight = solution == null ? 0 : solution.flightTicks();

            return this.target.getDeltaMovement().horizontalDistance() * Math.max(0, flight - 1);
        }

        /** One tick of the flight: the segment of the shot is clipped against the box of the target. */
        private void track()
        {
            if (this.shot == null)
            {
                return;
            }

            Vec3 position = this.shot.position();

            if (this.target.getBoundingBox().inflate(HIT_INFLATE).clip(this.lastPosition, position).isPresent())
            {
                this.touched = true;
            }

            // The movement the scenario really produced, measured on the body itself and not on its velocity: a
            // scenario that only sets a velocity while the body never goes anywhere proves nothing about an intercept.
            this.targetMoved = Math.max(this.targetMoved, Math.abs(this.target.getZ() - this.targetZAtShot));
            this.closest = Math.min(this.closest, this.target.getBoundingBox().getCenter().distanceTo(position));
            this.lastPosition = position;
        }

        private void judge()
        {
            this.judged = true;

            if (!this.fired || this.shot == null)
            {
                this.helper.fail("the scenario never fired a shot: " + this.profile.id());
                return;
            }

            if (!this.lateralMotion)
            {
                this.helper.fail("the target did not strafe across the line of the shot, so the scenario proves nothing"
                        + " about the intercept: velocity=" + this.target.getDeltaMovement());
                return;
            }

            if (this.drift < REQUIRED_DRIFT)
            {
                this.helper.fail("the drift of the target during the flight was only " + describe(this.drift)
                        + " blocks (required " + REQUIRED_DRIFT + "), so a straight aimed shot could have hit just as"
                        + " well and the intercept is not being tested");
                return;
            }

            if (this.targetMoved < REQUIRED_DRIFT)
            {
                // The other half of the same honesty: the scenario has to produce a body that really goes somewhere,
                // and not only a body that was told to. A no-AI body of the game does not move itself, so setting a
                // velocity on one is a scenario that lies - the first run of this file failed here on all six
                // scenarios, with ballistics that were right the whole time.
                this.helper.fail("the target was given a velocity of " + this.target.getDeltaMovement()
                        + " but really moved only " + describe(this.targetMoved) + " blocks during the flight, so the"
                        + " scenario is not a strafing target at all");
                return;
            }

            boolean damaged = this.target.getHealth() < this.healthBefore;
            boolean arrived = this.touched || this.closest <= EGG_REACH_DISTANCE;
            boolean hit = this.profile == ProjectileProfile.EGG ? arrived : damaged;

            if (!hit)
            {
                this.helper.fail("the " + this.profile.id() + " did not reach a target that strafed "
                        + describe(this.drift) + " blocks during the flight: touched=" + this.touched + ", closest="
                        + describe(this.closest) + " blocks, damaged=" + damaged + ", shooterWalked="
                        + this.shootingWhileWalking + ", range=" + describe(this.shooter.distanceTo(this.target))
                        + " | " + this.shooter.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[BallisticsTest] {} reached a moving target: drift={} blocks,"
                            + " closest={}, damaged={}, shooterWalked={}", this.profile.id(), describe(this.drift),
                    describe(this.closest), damaged, this.shootingWhileWalking);
            this.helper.succeed();
        }

        private static String describe(double value)
        {
            return value == Double.MAX_VALUE ? "never near"
                    : String.format(java.util.Locale.ROOT, "%.2f", value);
        }
    }
}
