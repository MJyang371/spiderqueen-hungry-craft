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
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * Three missed ranged rounds and the pearl that answers them (PvP round).
 *
 * <p>The rule of this file is short and it is about <b>rounds</b> and not about projectiles: one bow shot, one trident
 * throw and one whole egg burst are each one round, a round that a single projectile of it reached the enemy is a hit,
 * a hit clears the streak, and a different enemy starts its own count. Only three missed rounds in a row against the
 * enemy being fought right now make the brain stop shooting and close the gap with a pearl.
 *
 * <p>Every round here is driven through the real {@link RangedAttackRoundTracker} with <b>real</b> projectiles: a shot
 * that is thrown away far from the enemy is a miss, one that is thrown away inside the enemy box is a hit - the rule the
 * tracker itself uses ("where was the projectile when it left the world"). The body of every scenario has <b>no AI</b>,
 * so the only thing that ever touches the tracker is this test, and the pearl decision is asked of the brain itself
 * ({@code forcePearlGapClose}) instead of being imitated.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordRangedAdaptationGameTest
{
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 44;

    /** A corner of the world of its own, far from every other test site of this round. */
    private static final int SITE_X = 4096;
    private static final int SITE_Z = 512;

    /** Where the enemy of a scenario stands: beyond the melee band, so a pearl is a way in and not a joke. */
    private static final double ENEMY_DISTANCE = 12.0D;

    /** How far away from the enemy a shot is thrown away to make it a miss. */
    private static final double MISS_DISTANCE = 20.0D;

    private SweatlordRangedAdaptationGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 200)
    public static void oneMissedRoundDoesNotForceAPearl(GameTestHelper helper)
    {
        Scene scene = scene(helper, 0);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);

        if (scene.tracker.missStreak() != 1)
        {
            helper.fail("one missed round produced a streak of " + scene.tracker.missStreak() + " instead of 1");
            return;
        }

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] one miss is not a reason to panic: {}", scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 200)
    public static void twoMissedRoundsStillDoNotForceAPearl(GameTestHelper helper)
    {
        Scene scene = scene(helper, 200);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.TRIDENT, 1);

        if (scene.tracker.missStreak() != 2)
        {
            helper.fail("two missed rounds produced a streak of " + scene.tracker.missStreak() + " instead of 2");
            return;
        }

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] two misses are still not a reason to panic: {}",
                scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 200)
    public static void threeMissedRoundsForceThePearl(GameTestHelper helper)
    {
        Scene scene = scene(helper, 400);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.TRIDENT, 1);

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.EGG_BURST, 5);

        if (scene.tracker.missStreak() != 3)
        {
            helper.fail("three missed rounds produced a streak of " + scene.tracker.missStreak() + " instead of 3");
            return;
        }

        if (!scene.brain.forcePearlGapClose(scene.context()))
        {
            helper.fail("the third missed round did not start the panic pearl, so the adaptation never happens at all:"
                    + " " + scene.describe());
            return;
        }

        if (!scene.pearlIsForced())
        {
            helper.fail("the pearl was started for another reason than the adaptation of the brain");
            return;
        }

        if (scene.tracker.missStreak() != 0)
        {
            helper.fail("the streak of three misses was not spent by the panic pearl, so the next tick would throw"
                    + " another one: streak=" + scene.tracker.missStreak());
            return;
        }

        if (scene.brain.forcePearlGapClose(scene.context()))
        {
            helper.fail("a second panic pearl was started from the same streak, which is the pearl spam this round"
                    + " forbids");
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] three misses closed the gap with a pearl: {}",
                scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 200)
    public static void aHitResetsTheStreak(GameTestHelper helper)
    {
        Scene scene = scene(helper, 600);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.hitRound(RangedAttackRoundTracker.Kind.BOW);

        if (scene.tracker.missStreak() != 0)
        {
            helper.fail("a round that reached the enemy did not clear the streak: it is still "
                    + scene.tracker.missStreak());
            return;
        }

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] a hit cleared the streak: {}", scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 240)
    public static void oneEggOfARoundIsEnough(GameTestHelper helper)
    {
        Scene scene = scene(helper, 800);

        if (scene == null)
        {
            return;
        }

        // One burst of seven eggs, six of them thrown away into the field and one that really reaches the enemy.
        scene.missRoundWithOneHit(RangedAttackRoundTracker.Kind.EGG_BURST, 7);

        if (scene.tracker.missStreak() != 0)
        {
            helper.fail("a burst of eggs in which one egg really reached the enemy was counted as a miss (streak "
                    + scene.tracker.missStreak() + "), so the burst is counted per projectile and not per round");
            return;
        }

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] one egg of the burst was enough for the whole round: {}",
                scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 240)
    public static void threeFailedEggRoundsForceExactlyOnePearl(GameTestHelper helper)
    {
        Scene scene = scene(helper, 1000);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.EGG_BURST, 7);
        scene.missRound(RangedAttackRoundTracker.Kind.EGG_BURST, 7);

        if (scene.tracker.missStreak() != 2)
        {
            helper.fail("two bursts of seven eggs each produced a streak of " + scene.tracker.missStreak()
                    + " instead of 2, so the eggs are counted one by one");
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.EGG_BURST, 7);

        if (scene.tracker.missStreak() != 3)
        {
            helper.fail("three bursts produced a streak of " + scene.tracker.missStreak() + " instead of 3");
            return;
        }

        if (!scene.brain.forcePearlGapClose(scene.context()))
        {
            helper.fail("three missed egg bursts did not start the panic pearl: " + scene.describe());
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] three whole bursts were one pearl, not twenty one misses: {}",
                scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 240)
    public static void aWrongEntityIsNotAHit(GameTestHelper helper)
    {
        Scene scene = scene(helper, 1200);

        if (scene == null)
        {
            return;
        }

        scene.hitRoundAgainstAStranger(RangedAttackRoundTracker.Kind.BOW);

        if (scene.tracker.missStreak() != 1)
        {
            helper.fail("a shot that ended inside a body that is not the enemy of the round counted as a hit (streak "
                    + scene.tracker.missStreak() + "), so the tracker never asks what was hit");
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] a hit on the wrong body is still a miss: {}",
                scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 240)
    public static void changingTheEnemyStartsItsOwnCount(GameTestHelper helper)
    {
        Scene scene = scene(helper, 1400);

        if (scene == null)
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);

        if (scene.tracker.missStreak() != 2)
        {
            helper.fail("two misses against the first enemy did not build a streak of 2");
            return;
        }

        if (!scene.switchEnemy(helper))
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);

        if (scene.tracker.missStreak() != 1)
        {
            helper.fail("the first missed round against a new enemy continued the streak of the old one (streak "
                    + scene.tracker.missStreak() + "), so a pearl would be thrown at somebody because of somebody else");
            return;
        }

        if (!scene.brainWantsNoPearl(helper))
        {
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] a new enemy starts at zero: {}", scene.describe());
        helper.succeed();
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_ranged_rounds", timeoutTicks = 300)
    public static void anUnsafeForcedPearlFailsCleanly(GameTestHelper helper)
    {
        Scene scene = scene(helper, 1600);

        if (scene == null || !scene.floatTheEnemy())
        {
            return;
        }

        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);
        scene.missRound(RangedAttackRoundTracker.Kind.BOW, 1);

        if (!scene.brain.forcePearlGapClose(scene.context()))
        {
            helper.fail("the third miss did not even try the panic pearl in a world with no safe landing spot");
            return;
        }

        if (scene.pearlsInTheWorld() > 0)
        {
            helper.fail("a pearl was thrown although there is no safe landing spot anywhere near the enemy: the"
                    + " safety planner of the pearl was bypassed");
            return;
        }

        if (scene.tracker.missStreak() != 0)
        {
            helper.fail("the failed panic pearl did not spend the streak, so it would be retried every tick");
            return;
        }

        scene.waitTicks(60);

        if (scene.pearlsInTheWorld() > 0 || scene.tracker.missStreak() != 0)
        {
            helper.fail("the body kept throwing pearls after the refused jump: pearls=" + scene.pearlsInTheWorld()
                    + ", streak=" + scene.tracker.missStreak());
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[RangedRounds] the unsafe panic pearl failed cleanly: {}",
                scene.describe());
        helper.succeed();
    }

    /** One prepared scenario: a body with no AI, its enemy, and the round tracker of the brain. */
    private static final class Scene
    {
        /** How many ticks a round may need before it is judged, in the loop of a test that has no server ticking. */
        private static final int SETTLE_TICKS = 40;

        private final SweatlordSteveEntity mob;
        private final SweatlordCombatBrain brain;
        private final RangedAttackRoundTracker tracker;
        private final BlockPos centre;

        @Nullable
        private Zombie enemy;

        Scene(SweatlordSteveEntity mob, Zombie enemy, BlockPos centre)
        {
            this.mob = mob;
            this.brain = mob.brain();
            this.tracker = this.brain.rangedRounds();
            this.enemy = enemy;
            this.centre = centre;
            this.mob.setTarget(enemy);
        }

        /** One round whose every projectile is thrown away far from the enemy: a miss. */
        void missRound(RangedAttackRoundTracker.Kind kind, int projectiles)
        {
            int round = this.tracker.beginRound(this.enemy, kind, projectiles, this.now());

            for (int index = 0; index < projectiles; ++index)
            {
                this.tracker.registerProjectile(round, this.thrownAway());
            }

            this.settle();
        }

        /** One round whose single projectile really ends inside the enemy: a hit. */
        void hitRound(RangedAttackRoundTracker.Kind kind)
        {
            int round = this.tracker.beginRound(this.enemy, kind, 1, this.now());
            this.tracker.registerProjectile(round, this.endedInside(this.enemy));
            this.settle();
        }

        /** One round of which exactly one projectile reaches the enemy, the rest missing. */
        void missRoundWithOneHit(RangedAttackRoundTracker.Kind kind, int projectiles)
        {
            int round = this.tracker.beginRound(this.enemy, kind, projectiles, this.now());

            for (int index = 1; index < projectiles; ++index)
            {
                this.tracker.registerProjectile(round, this.thrownAway());
            }

            this.tracker.registerProjectile(round, this.endedInside(this.enemy));
            this.settle();
        }

        /** One round whose projectile ends inside a body that is not the enemy of the round. */
        void hitRoundAgainstAStranger(RangedAttackRoundTracker.Kind kind)
        {
            Zombie stranger = spawnZombie(this.mob, this.centre.getX() + 3.0D, this.centre.getZ() + 3.0D);

            if (stranger == null)
            {
                return;
            }

            int round = this.tracker.beginRound(this.enemy, kind, 1, this.now());
            this.tracker.registerProjectile(round, this.endedInside(stranger));
            this.settle();
        }

        /** Switches the fight to a second enemy, which has to start a miss count of its own. */
        boolean switchEnemy(GameTestHelper helper)
        {
            Zombie next = spawnZombie(this.mob, this.centre.getX() + 4.0D, this.centre.getZ() - 4.0D);

            if (next == null)
            {
                helper.fail("the second enemy of the scenario could not be placed");
                return false;
            }

            this.enemy = next;
            this.mob.setTarget(next);

            return true;
        }

        /** Takes the enemy off the ground so that no landing spot of a pearl is left anywhere near it. */
        boolean floatTheEnemy()
        {
            if (this.enemy == null)
            {
                return false;
            }

            this.enemy.setNoGravity(true);
            this.enemy.moveTo(this.enemy.getX(), this.enemy.getY() + 18.0D, this.enemy.getZ(), 0.0F, 0.0F);

            return true;
        }

        boolean brainWantsNoPearl(GameTestHelper helper)
        {
            if (this.tracker.wantsForcedPearl(this.context()))
            {
                helper.fail("the brain wants a panic pearl after only " + this.tracker.missStreak()
                        + " missed ranged rounds: " + this.describe());
                return false;
            }

            return true;
        }

        boolean pearlIsForced()
        {
            return this.brain.skillNamed("offensive_pearl") instanceof spiderqueenhungrycraft.sweatlord.skill
                    .SupportSkills.OffensivePearlSkill pearl && pearl.isForced();
        }

        int pearlsInTheWorld()
        {
            return this.mob.level().getEntitiesOfClass(
                    net.minecraft.world.entity.projectile.ThrownEnderpearl.class,
                    this.mob.getBoundingBox().inflate(64.0D)).size();
        }

        CombatContext context()
        {
            return CombatContext.capture(this.mob);
        }

        void waitTicks(int ticks)
        {
            for (int tick = 0; tick < ticks; ++tick)
            {
                this.tracker.tick(this.mob);
            }
        }

        String describe()
        {
            return this.tracker.describe() + ", pearl=" + this.mob.debugPearlMode();
        }

        private long now()
        {
            return this.mob.level().getGameTime();
        }

        /** One shot that is already out of the world, far away from the enemy: a resolved miss. */
        private Arrow thrownAway()
        {
            Vec3 at = new Vec3(this.centre.getX() + MISS_DISTANCE + 0.5D, SITE_FLOOR_Y + 4.0D,
                    this.centre.getZ() + 0.5D);
            Arrow arrow = new Arrow(this.mob.level(), this.mob);
            arrow.setPos(at);
            this.mob.level().addFreshEntity(arrow);
            arrow.discard();

            return arrow;
        }

        /** One shot that really ends inside that body: a resolved hit against it. */
        private Arrow endedInside(@Nullable LivingEntity target)
        {
            Arrow arrow = new Arrow(this.mob.level(), this.mob);

            if (target != null)
            {
                arrow.setPos(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
            }

            this.mob.level().addFreshEntity(arrow);
            arrow.discard();

            return arrow;
        }

        /** Gives the tracker the ticks it needs to resolve the round of this scenario. */
        private void settle()
        {
            for (int tick = 0; tick < SETTLE_TICKS && this.tracker.openRounds() > 0; ++tick)
            {
                this.tracker.tick(this.mob);
            }
        }
    }

    /** Builds the flat stone pad of this file and returns its centre. */
    private static BlockPos openSite(GameTestHelper helper, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);
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

                for (int dy = 0; dy <= 4; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return centre;
    }

    /**
     * The body of every scenario, with <b>no AI</b> on purpose: nothing may touch the tracker of this file except the
     * test itself, or the rounds of the body's own fight would land in the middle of a measured streak.
     */
    @Nullable
    private static Scene scene(GameTestHelper helper, int offsetZ)
    {
        BlockPos centre = openSite(helper, offsetZ);
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), helper.getLevel(),
                new BlockPos(centre.getX(), SITE_FLOOR_Y, centre.getZ()), MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord of the scenario could not be placed: " + placed);
            return null;
        }

        sweatlord.setNoAi(true);
        sweatlord.setPersistenceRequired();

        Zombie enemy = spawnZombie(sweatlord, centre.getX() + ENEMY_DISTANCE, centre.getZ());

        if (enemy == null)
        {
            helper.fail("the enemy of the scenario could not be created");
            return null;
        }

        sweatlord.setTarget(enemy);

        return new Scene(sweatlord, enemy, centre);
    }

    /** The monster every scenario is fought against: no AI, persistent, and held where it was placed. */
    @Nullable
    private static Zombie spawnZombie(LivingEntity owner, double x, double z)
    {
        Zombie zombie = EntityType.ZOMBIE.create(owner.level());

        if (zombie == null)
        {
            return null;
        }

        zombie.moveTo(x + 0.5D, SITE_FLOOR_Y, z + 0.5D, 180.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        owner.level().addFreshEntity(zombie);

        return zombie;
    }
}
