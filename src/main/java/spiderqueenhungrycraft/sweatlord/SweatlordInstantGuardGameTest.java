package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.attack.ShieldBlockFeedback;
import spiderqueenhungrycraft.attack.ShieldProjectileInteraction;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.web.WebShotProjectile;

/**
 * The instant projectile guard of the Sweatlord (PvP round), measured on the projectiles that deal nothing.
 *
 * <p>The arrow is covered by {@code SweatlordShieldGameTest} (a shot from four blocks, answered although the shield had
 * been down when it left). This file adds what that test can not show:
 *
 * <ul>
 * <li>a <b>snowball</b>, an <b>egg</b> and a <b>web shot</b> - the three impacts that can never enter the shield path
 * of {@code LivingEntity#hurt}, because that path begins with {@code damage > 0}. Nothing in the game can block them
 * for him, so the guard answers them itself and broadcasts exactly one block feedback,</li>
 * <li>a shot from <b>behind</b>: the body has to snap around, because the front rule of the shield would otherwise
 * refuse the block,</li>
 * <li>and the <b>release</b>: after the shot the guard has to let go of the body again, because a reflex that parks the
 * body for ever is exactly the bug this round removes.</li>
 * </ul>
 *
 * <p>Every projectile is a real entity of the game, launched on a solved trajectory at the chest of the body, and every
 * claim is read from the game: the feedback counts are the ones {@code ShieldBlockFeedback} records for the vanilla
 * entity event, and the block itself is the one the client turns into a shield sound.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordInstantGuardGameTest
{
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 44;

    /** A corner of the world of its own, far from every other test site of this round. */
    private static final int SITE_X = 3584;
    private static final int SITE_Z = 512;

    private static final int PAD_SNOWBALL = 0;
    private static final int PAD_EGG = 200;
    private static final int PAD_WEB = 400;
    private static final int PAD_REAR = 600;
    private static final int PAD_RELEASE = 800;

    /** The monster that keeps the brain of the body awake, put to the side of the shots of this file. */
    private static final double ENEMY_DISTANCE = 12.0D;

    /** How far from the body the shot of a scenario starts: close enough that no vanilla wind up could ever win. */
    private static final double SHOT_DISTANCE = 6.0D;

    private SweatlordInstantGuardGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_guard", timeoutTicks = 160)
    public static void snowballInstantGuard(GameTestHelper helper)
    {
        run(helper, PAD_SNOWBALL, Kind.SNOWBALL, SHOT_DISTANCE, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_guard", timeoutTicks = 160)
    public static void eggInstantGuard(GameTestHelper helper)
    {
        run(helper, PAD_EGG, Kind.EGG, SHOT_DISTANCE, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_guard", timeoutTicks = 160)
    public static void webShotInstantGuard(GameTestHelper helper)
    {
        run(helper, PAD_WEB, Kind.WEB_SHOT, SHOT_DISTANCE, false);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_guard", timeoutTicks = 160)
    public static void rearProjectileSnapTurn(GameTestHelper helper)
    {
        run(helper, PAD_REAR, Kind.SNOWBALL, SHOT_DISTANCE, true);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_guard", timeoutTicks = 200)
    public static void theGuardReleasesTheBodyAfterTheShot(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_RELEASE);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);
        helper.onEachTick(new GuardProbe(helper, site, sweatlord, enemy, Kind.SNOWBALL, SHOT_DISTANCE, false,
                true)::tick);
    }

    private static void run(GameTestHelper helper, int offsetZ, Kind kind, double distance, boolean fromBehind)
    {
        Site site = openSite(helper, 0, offsetZ);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);
        helper.onEachTick(new GuardProbe(helper, site, sweatlord, enemy, kind, distance, fromBehind, false)::tick);
    }

    /** The harmless impacts of this file: none of them deals damage, so none of them can enter the shield path. */
    private enum Kind
    {
        SNOWBALL,
        EGG,
        WEB_SHOT
    }

    /** One prepared site of this file: the level of the test and the point every body is placed around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X + offsetX, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int cx = -2; cx <= 2; ++cx)
        {
            for (int cz = -2; cz <= 2; ++cz)
            {
                level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, true);
            }
        }

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

    @Nullable
    private static SweatlordSteveEntity spawnSweatlord(Site site, GameTestHelper helper)
    {
        BlockPos at = new BlockPos(site.centre().getX(), SITE_FLOOR_Y, site.centre().getZ());
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(), at,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    /** The monster that keeps the brain of the body awake: no AI, persistent, and never in the way of a shot. */
    @Nullable
    private static Zombie spawnPassiveZombie(Site site, double offsetX, double offsetZ, GameTestHelper helper)
    {
        Zombie zombie = EntityType.ZOMBIE.create(site.level());

        if (zombie == null)
        {
            helper.fail("the monster of the scenario could not be created");
            return null;
        }

        zombie.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                180.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        site.level().addFreshEntity(zombie);

        return zombie;
    }

    /**
     * Fires one harmless projectile at the body and demands that the guard answers it: one block feedback, a snap turn
     * into the shot (a shot from behind only makes the claim harder), and the release afterwards when it is asked of it.
     */
    private static final class GuardProbe
    {
        /** The tick the shot leaves on: late enough that the fight and the brain of the body are really running. */
        private static final int SHOT_TICK = 25;

        /** How long the probe watches after the shot before it judges. */
        private static final int TAIL_TICKS = 45;

        /** The speed a thrown egg or snowball of the game leaves the hand with. */
        private static final double THROWN_SPEED = 1.5D;

        /** The speed of the web shot of this mod, which flies straight instead of falling. */
        private static final double WEB_SPEED = 1.6D;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity mob;
        private final Vec3 post;
        private final Kind kind;
        private final boolean fromBehind;
        private final boolean checkRelease;
        private final Vec3 shotOrigin;
        @Nullable
        private final Vec3 shotVelocity;

        @Nullable
        private Projectile projectile;

        private int ticks;
        private boolean fired;
        private boolean threatProven;
        private int guardUpTicks;
        private int feedbacksBefore;
        private int blockedBefore;
        private int facingTicksAfterShot;
        private float yawBeforeShot;
        private float yawAfterShot;
        private boolean projectileGone;
        private int releaseTick = -1;
        private boolean judged;

        /** The enemy of this scenario, which is never cleared off the pad. */
        @Nullable
        private final LivingEntity enemy;

        GuardProbe(GameTestHelper helper, Site site, SweatlordSteveEntity mob, Zombie enemy, Kind kind, double distance,
                boolean fromBehind, boolean checkRelease)
        {
            this.helper = helper;
            this.mob = mob;
            this.enemy = enemy;
            this.kind = kind;
            this.fromBehind = fromBehind;
            this.checkRelease = checkRelease;
            this.post = mob.position();
            this.shotOrigin = shotOrigin(mob, distance, fromBehind);
            this.shotVelocity = ProjectileBallistics.solveThrow(site.level(), this.shotOrigin, aimPoint(mob),
                    speedOf(kind), gravityOf(kind));
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            clearThePad();
            holdPost();

            if (this.enemy != null && this.mob.getTarget() != this.enemy)
            {
                // The scenario commits its enemy: a body that is held at a post reads as one that is on its way back to
                // it, and a brain without an enemy does not run the fight this guard belongs to.
                this.mob.setTarget(this.enemy);
            }

            if (this.mob.brain().instantGuard().isActive())
            {
                ++this.guardUpTicks;
            }

            if (this.ticks == SHOT_TICK)
            {
                fire();
            }

            if (this.projectile != null)
            {
                if (!this.projectile.isRemoved())
                {
                    if (faces(this.projectile))
                    {
                        ++this.facingTicksAfterShot;
                    }
                }
                else if (!this.projectileGone)
                {
                    this.projectileGone = true;
                    this.yawAfterShot = this.mob.getYRot();
                }
            }

            if (this.projectileGone && this.releaseTick < 0 && !this.mob.brain().instantGuard().isActive()
                    && !ShieldProjectileInteraction.holdsUpAShield(this.mob))
            {
                // The shield has to be down again, and nothing else of the body counts: he is allowed to draw a bow the
                // moment the shot is answered.
                this.releaseTick = this.ticks;
            }

            if (this.ticks >= SHOT_TICK + TAIL_TICKS)
            {
                judge();
            }
        }

        /** Fires the real projectile of this scenario, on the solved trajectory at the chest of the body. */
        private void fire()
        {
            if (this.shotVelocity == null)
            {
                fail("the shot of this scenario could not be solved at all, so nothing could be fired");
                return;
            }

            LivingEntity owner = this.enemy == null ? this.mob : this.enemy;
            Projectile shot = switch (this.kind)
            {
                case SNOWBALL -> new Snowball(this.mob.level(), owner);
                case EGG -> new ThrownEgg(this.mob.level(), owner);
                case WEB_SHOT -> new WebShotProjectile(this.mob.level(), owner);
            };

            shot.setPos(this.shotOrigin);
            shot.setDeltaMovement(this.shotVelocity);
            this.mob.level().addFreshEntity(shot);
            this.projectile = shot;
            this.fired = true;
            this.threatProven = ProjectileThreatDetector.isThreatTo(this.mob, shot);
            this.feedbacksBefore = ShieldBlockFeedback.feedbacks(this.mob);
            this.blockedBefore = this.mob.brain().instantGuard().blockedProjectiles();
            this.yawBeforeShot = this.mob.getYRot();
        }

        private void judge()
        {
            this.judged = true;

            if (!this.fired || !this.threatProven)
            {
                fail("the shot of this scenario was never a real threat to the body, so nothing could be measured:"
                        + " fired=" + this.fired + ", onACollisionCourse=" + this.threatProven + " | "
                        + this.mob.getDebugState());
                return;
            }

            if (!this.projectileGone)
            {
                fail("the " + this.kind + " was still flying " + TAIL_TICKS + " ticks after it was fired at a body"
                        + " six blocks away, so the guard never answered it | " + this.mob.getDebugState());
                return;
            }

            int feedbacks = ShieldBlockFeedback.feedbacks(this.mob) - this.feedbacksBefore;
            int blocked = this.mob.brain().instantGuard().blockedProjectiles() - this.blockedBefore;

            if (feedbacks < 1 || blocked < 1)
            {
                fail("a " + this.kind + " that deals no damage was not blocked at all: the guard answered " + blocked
                        + " impacts and the block feedback of this body moved by " + feedbacks + " | "
                        + this.mob.getDebugState());
                return;
            }

            if (feedbacks != 1)
            {
                fail("one blocked " + this.kind + " produced " + feedbacks + " block feedbacks instead of exactly one,"
                        + " so the shield of this body is either silent or doubled | " + this.mob.getDebugState());
                return;
            }

            if (this.guardUpTicks == 0)
            {
                fail("the guard of the body was never up during the whole scenario | " + this.mob.getDebugState());
                return;
            }

            if (this.facingTicksAfterShot == 0)
            {
                fail("the body never faced the " + this.kind + ", and a shield only covers the front of a body | "
                        + this.mob.getDebugState());
                return;
            }

            if (this.fromBehind
                    && Math.abs(net.minecraft.util.Mth.wrapDegrees(this.yawAfterShot - this.yawBeforeShot)) < 60.0F)
            {
                fail("a shot from behind was answered without the body snapping around (yaw " + this.yawBeforeShot
                        + " -> " + this.yawAfterShot + "), so no shield could have covered it | "
                        + this.mob.getDebugState());
                return;
            }

            if (this.checkRelease)
            {
                if (this.releaseTick < 0)
                {
                    fail("the guard never let go of the body again after the shot, so the reflex has become the parked"
                            + " shield this round removes | " + this.mob.getDebugState());
                    return;
                }

                if (this.releaseTick - SHOT_TICK > InstantProjectileGuard.MAX_HOLD_TICKS
                        + InstantProjectileGuard.POST_BLOCK_VISUAL_HOLD + 10)
                {
                    fail("the shield stayed up for " + (this.releaseTick - SHOT_TICK) + " ticks after one shot, which"
                            + " is a lock and not a reflex | " + this.mob.getDebugState());
                    return;
                }
            }

            SpiderQueenHungryCraft.LOGGER.info("[GuardTest] the {} was answered: fromBehind={}, guardUpTicks={},"
                            + " feedbacks={}, facingTicks={}, releaseTick={} | {}",
                    this.kind, this.fromBehind, this.guardUpTicks, feedbacks, this.facingTicksAfterShot,
                    this.releaseTick, this.mob.getDebugState());
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }

        /** @return where the shot of this scenario starts: at the chest height, in front of or behind the body. */
        private static Vec3 shotOrigin(SweatlordSteveEntity mob, double distance, boolean fromBehind)
        {
            Vec3 view = mob.getViewVector(1.0F);
            Vec3 flat = new Vec3(view.x, 0.0D, view.z);

            if (flat.lengthSqr() < 1.0E-6D)
            {
                flat = new Vec3(1.0D, 0.0D, 0.0D);
            }

            return mob.position().add(flat.normalize().scale(fromBehind ? -distance : distance))
                    .add(0.0D, mob.getBbHeight() * 0.6D, 0.0D);
        }

        /** @return the chest of the body, which is what a shooter aims at. */
        private static Vec3 aimPoint(SweatlordSteveEntity mob)
        {
            return mob.position().add(0.0D, mob.getBbHeight() * 0.6D, 0.0D);
        }

        private static double speedOf(Kind kind)
        {
            return kind == Kind.WEB_SHOT ? WEB_SPEED : THROWN_SPEED;
        }

        private static double gravityOf(Kind kind)
        {
            return kind == Kind.WEB_SHOT ? 0.0D : ProjectileBallistics.THROWN_GRAVITY;
        }

        /** @return true when the body looks at that projectile: a shield only covers the front. */
        private boolean faces(net.minecraft.world.entity.Entity projectile)
        {
            Vec3 view = this.mob.getViewVector(1.0F);
            Vec3 toShot = projectile.position().subtract(this.mob.position());
            Vec3 flatView = new Vec3(view.x, 0.0D, view.z);
            Vec3 flatTo = new Vec3(toShot.x, 0.0D, toShot.z);

            if (flatView.lengthSqr() < 1.0E-6D || flatTo.lengthSqr() < 1.0E-4D)
            {
                return false;
            }

            return flatTo.normalize().dot(flatView.normalize()) > 0.5D;
        }

        /** Keeps the body at its post: the reaction of this file is measured on a body that is not walking away. */
        private void holdPost()
        {
            if (this.mob.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        /** Removes every body that wandered into the scenario and would start a fight of its own. */
        private void clearThePad()
        {
            for (LivingEntity stray : this.mob.level().getEntitiesOfClass(LivingEntity.class,
                    this.mob.getBoundingBox().inflate(24.0D)))
            {
                if (stray != this.mob && stray != this.enemy && CombatContext.isValidEnemy(this.mob, stray))
                {
                    stray.discard();
                }
            }
        }
    }
}
