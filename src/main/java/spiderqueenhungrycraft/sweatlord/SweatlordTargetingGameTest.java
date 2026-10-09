package spiderqueenhungrycraft.sweatlord;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof that the Sweatlord finds his own enemies (Sweatlord fix round).
 *
 * <p>He is the one body of this mod that owns its targeting ({@code OwnFactionTargeting}): nothing is injected into
 * him and the brain asks the shared faction rules itself. That design has exactly one trap, and this file is written
 * against it: the brain is only ever ticked by its fight goal, and that goal is only started while the brain already
 * wants the body - so a body whose <b>only</b> way of wanting something is the enemy it has not found yet can stand
 * next to a Spider Queen for ever and never look up.
 *
 * <p>Both tests place him and an enemy and then <b>touch nothing</b>: no target is handed to him, nobody hits him, no
 * projectile is in the air. If he still has no target and no encounter after the deadline, the test fails with the
 * state of the body.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordTargetingGameTest
{
    /** The site numbers of this round, at its own corner of the test world. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 12;
    private static final int SITE_X = 1280;
    private static final int SITE_Z = 512;

    /** How far the enemy of the scenario stands from him: inside his search range of 32 blocks. */
    private static final double ENEMY_DISTANCE = 10.0D;

    /** How long he has to look up and pick the enemy. */
    private static final int DEADLINE = 80;

    private SweatlordTargetingGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_targeting", timeoutTicks = 140)
    public static void sweatlord_huntsAMonsterOnItsOwn(GameTestHelper helper)
    {
        Scenario scenario = openScenario(helper, 0.0D, ENEMY_DISTANCE);

        if (scenario == null)
        {
            return;
        }

        // The rule of this mod has to allow the pair before the test may ask why he did not use it.
        if (!CombatContext.isValidEnemy(scenario.sweatlord(), scenario.enemy()))
        {
            helper.fail("the monster of this test is not a legal enemy of the Sweatlord, so nothing else can be"
                    + " tested here: " + scenario.enemy());
            return;
        }

        helper.onEachTick(new LookUpProbe(helper, scenario, "a monster")::tick);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_targeting", timeoutTicks = 140)
    public static void sweatlord_huntsASpiderQueenOnItsOwn(GameTestHelper helper)
    {
        Site site = buildSite(helper);
        // A real player body: the queen flag of this mod is a capability of a player, never a tag.
        ServerPlayer queen = FakePlayerFactory.get(site.level(), new GameProfile(UUID.randomUUID(), "sweatlord_prey"));
        queen.setGameMode(GameType.SURVIVAL);
        queen.moveTo(site.centre().getX() + ENEMY_DISTANCE + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + 0.5D, 180.0F,
                0.0F);
        QueenDataCapability.setQueen(queen, true);
        site.level().addNewPlayer(queen);

        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (sweatlord == null)
        {
            return;
        }

        if (!CombatContext.isValidEnemy(sweatlord, queen))
        {
            helper.fail("a player with the queen flag is not a legal enemy of the Sweatlord, so nothing else can be"
                    + " tested here");
            return;
        }

        helper.onEachTick(new LookUpProbe(helper, new Scenario(site, sweatlord, queen), "a Spider Queen")::tick);
    }

    /** One scenario of this file: the site, the body under test and the enemy that is never pointed out to him. */
    private record Scenario(Site site, SweatlordSteveEntity sweatlord, LivingEntity enemy)
    {
    }

    /** The site of this file: the level of the test and the point the scenario is built around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    @Nullable
    private static Scenario openScenario(GameTestHelper helper, double enemyX, double enemyZ)
    {
        Site site = buildSite(helper);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);
        Zombie enemy = spawnPassiveZombie(site, enemyX, enemyZ, helper);

        if (sweatlord == null || enemy == null)
        {
            return null;
        }

        return new Scenario(site, sweatlord, enemy);
    }

    /**
     * Watches whether he looks up on his own. The scenario is never touched: no target is handed to him, nobody hits
     * him and no projectile is in the air - the probe only reads the body and fails on the deadline.
     */
    private static final class LookUpProbe
    {
        private final GameTestHelper helper;
        private final Scenario scenario;
        private final String what;

        private int ticks;

        LookUpProbe(GameTestHelper helper, Scenario scenario, String what)
        {
            this.helper = helper;
            this.scenario = scenario;
            this.what = what;
        }

        void tick()
        {
            ++this.ticks;

            boolean pickedSomething = this.scenario.sweatlord().getTarget() != null;
            boolean inAFight = this.scenario.sweatlord().brain().isEncounterActive();

            if (pickedSomething && inAFight)
            {
                SpiderQueenHungryCraft.LOGGER.info("[TargetingTest] he picked {} on his own after {} ticks: {}",
                        this.what, this.ticks, this.scenario.sweatlord().getDebugState());
                this.helper.succeed();
                return;
            }

            if (this.ticks > DEADLINE)
            {
                this.helper.fail("the Sweatlord never picked " + this.what + " that stood "
                        + Math.round(this.scenario.sweatlord().distanceTo(this.scenario.enemy())) + " blocks away and"
                        + " never touched him: target=" + this.scenario.sweatlord().getTarget()
                        + ", pickedAnything=" + pickedSomething + ", encounter=" + inAFight
                        + ", goalRunning=" + !this.scenario.sweatlord().getNavigation().isDone() + " | "
                        + this.scenario.sweatlord().getDebugState());
            }
        }
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and places a spectator anchor on it. */
    private static Site buildSite(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z);

        for (int cx = -1; cx <= 1; ++cx)
        {
            for (int cz = -1; cz <= 1; ++cz)
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

        // A spectator keeps the site ticking without making the surface spawner populate it with other humans.
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "targeting_anchor"));
        anchor.setGameMode(GameType.SPECTATOR);
        anchor.moveTo(centre.getX() + 0.5D, SITE_FLOOR_Y, centre.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
        }

        return new Site(level, centre);
    }

    @Nullable
    private static SweatlordSteveEntity spawnSweatlord(Site site, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(), site.centre(),
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    /** The prey of the scenario: no AI and persistent, so it can never walk out of the test or despawn. */
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
}
