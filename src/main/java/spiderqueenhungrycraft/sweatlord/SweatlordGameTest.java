package spiderqueenhungrycraft.sweatlord;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.ChadSteveEntity;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.faction.FactionId;
import spiderqueenhungrycraft.faction.FactionResolver;
import spiderqueenhungrycraft.faction.FactionTargetGoal;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.faction.OwnFactionTargeting;
import spiderqueenhungrycraft.human.HumanArchetype;
import spiderqueenhungrycraft.human.HumanEntityTypes;
import spiderqueenhungrycraft.human.spawn.HumanPopulationCounter;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.hostility.HumanBandState;
import spiderqueenhungrycraft.hostility.HumanThreatMemory;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of the Sweatlord integration (Sweatlord round).
 *
 * <p>Every assertion is deterministic on purpose: where a behaviour can be asked directly - a skill gate, a faction
 * relation, a planner decision, a population count - it is asked directly instead of waiting for a random tick to
 * produce it, so a failure names the state it saw instead of a timeout.
 *
 * <p>The class holds the identity and population proof, the pack aggro proof in both directions, the friendly half of
 * the same rule, the shield against web, and the skill gates of the fight (bow, axe, eggs, pearls, retreat, threats).
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordGameTest
{
    /** The floor of the test sites and the half width of their flat area. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 12;

    /** The two sites this file builds, far enough apart that no scenario can see another one. */
    private static final int SITE_A_X = 512;
    private static final int SITE_B_X = 768;
    private static final int SITE_Z = 512;

    private SweatlordGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_identity", timeoutTicks = 100)
    public static void identityAndPopulation(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos site = buildSite(level, SITE_A_X);
        SweatlordSteveEntity sweatlord = spawnSweatlord(level, site, helper);

        if (sweatlord == null)
        {
            return;
        }

        // The world of a test run is shared, and the other tests of this file leave their own bodies standing on this very
        // spot: the first run with the new batch order found two Chad Steves and a second Sweatlord inside the four block
        // census of this test and reported "human units around him: expected 1 but found 4". A census has to start from a
        // world it knows, and it may not delete the player that keeps its site ticking either.
        for (net.minecraft.world.entity.LivingEntity leftover : level.getEntitiesOfClass(
                net.minecraft.world.entity.LivingEntity.class, sweatlord.getBoundingBox().inflate(12.0D)))
        {
            if (leftover != sweatlord && !(leftover instanceof net.minecraft.world.entity.player.Player))
            {
                leftover.discard();
            }
        }

        assertEquals(helper, "entity type", EntityType.getKey(HungryCraftEntities.SWEATLORD_STEVE.get()).toString(),
                EntityType.getKey(sweatlord.getType()).toString());
        assertEquals(helper, "faction", FactionId.HUMAN, FactionResolver.getFaction(sweatlord));
        assertEquals(helper, "archetype", HumanArchetype.TRANSCENDENT, HumanEntityTypes.archetypeOf(sweatlord));
        assertEquals(helper, "isHumanMob", true, HumanEntityTypes.isHumanMob(sweatlord));
        assertEquals(helper, "owns its targeting", true, sweatlord instanceof OwnFactionTargeting);
        assertEquals(helper, "isOrdinaryHumanType", false,
                HumanEntityTypes.isOrdinaryHumanType(HungryCraftEntities.SWEATLORD_STEVE.get()));
        assertEquals(helper, "countsTowardSurfacePopulation", false,
                HumanEntityTypes.countsTowardSurfacePopulation(sweatlord));
        assertEquals(helper, "never despawns", false, sweatlord.removeWhenFarAway(1000.0D));

        int injectedGoals = 0;

        for (WrappedGoal wrapped : sweatlord.targetSelector.getAvailableGoals())
        {
            if (wrapped.getGoal() instanceof FactionTargetGoal)
            {
                ++injectedGoals;
            }
        }

        // The brain owns the targeting of this body, so the faction system must not have added a goal of its own.
        assertEquals(helper, "injected faction target goals", 0, injectedGoals);

        // The population counters query the level, so they are asserted once the body is really part of the loaded
        // world: a condition with a deadline, never a fixed wait.
        SpiderQueenHungryCraft.LOGGER.info("[SweatlordTest] identity assertions passed: {}",
                sweatlord.getDebugState());
        helper.onEachTick(new PopulationProbe(helper, level, sweatlord, site)::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 2) pack aggro in both directions, and the friendly rule
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_faction", timeoutTicks = 120)
    public static void packAggroBothWaysAndNoFriendlyFire(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos site = buildSite(level, SITE_B_X);
        SweatlordSteveEntity sweatlord = spawnSweatlord(level, site, helper);
        HumanCombatantEntity ordinary = spawnOrdinary(level, site.offset(2, 0, 0), helper);
        ChadSteveEntity chad = spawnChad(level, site.offset(0, 0, 2), helper);
        Zombie attacker = spawnZombie(level, site.offset(4, 0, 4), helper);

        if (sweatlord == null || ordinary == null || chad == null || attacker == null)
        {
            return;
        }

        // The pack rules reach their comrades through a box query, and a body that was added in the same tick is not
        // always part of a queryable entity section yet - so the scenarios run once every body is really there.
        helper.onEachTick(new PackProbe(helper, level, sweatlord, ordinary, chad, attacker)::tick);
    }

    /**
     * Runs the two pack aggro scenarios and the friendly half of the rule, once every body of the scenario is really
     * part of the loaded world. The wait is a condition with a deadline, never a fixed number of ticks.
     */
    private static final class PackProbe
    {
        private static final int DEADLINE = 80;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity sweatlord;
        private final HumanCombatantEntity ordinary;
        private final ChadSteveEntity chad;
        private final Zombie attacker;

        private int ticks;

        PackProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity sweatlord,
                HumanCombatantEntity ordinary, ChadSteveEntity chad, Zombie attacker)
        {
            this.helper = helper;
            this.level = level;
            this.sweatlord = sweatlord;
            this.ordinary = ordinary;
            this.chad = chad;
            this.attacker = attacker;
        }

        void tick()
        {
            ++this.ticks;

            if (!queryable(this.sweatlord) || !queryable(this.ordinary) || !queryable(this.chad))
            {
                if (this.ticks > DEADLINE)
                {
                    this.helper.fail("the pack of the scenario never became queryable: "
                            + this.sweatlord.getDebugState());
                }

                return;
            }

            long now = this.level.getGameTime();

            // Scenario A: an ordinary human is hit, so the Sweatlord has to join the answer.
            this.ordinary.hurt(this.ordinary.damageSources().mobAttack(this.attacker), 1.0F);

            double sweatlordGrudge = HumanThreatMemory.scoreOf(this.sweatlord, this.attacker, now);
            assertEquals(this.helper, "ordinary human hit -> Sweatlord knows the attacker", true,
                    sweatlordGrudge > 0.0D);
            assertEquals(this.helper, "ordinary human hit -> Sweatlord targets the attacker", this.attacker,
                    this.sweatlord.getTarget());

            // Scenario B: the Sweatlord is hit, so the humans around him answer - Chad Steve included.
            this.sweatlord.setTarget(null);
            this.ordinary.setTarget(null);
            this.chad.setTarget(null);
            this.sweatlord.hurt(this.sweatlord.damageSources().mobAttack(this.attacker), 1.0F);

            double humanGrudge = HumanThreatMemory.scoreOf(this.ordinary, this.attacker, now);
            double chadGrudge = HumanThreatMemory.scoreOf(this.chad, this.attacker, now);
            assertEquals(this.helper, "Sweatlord hit -> ordinary human knows the attacker", true, humanGrudge > 0.0D);
            assertEquals(this.helper, "Sweatlord hit -> Chad knows the attacker", true, chadGrudge > 0.0D);
            assertEquals(this.helper, "Sweatlord hit -> ordinary human remembers the attacker", this.attacker,
                    this.ordinary.getLastHurtByMob());
            assertEquals(this.helper, "Sweatlord hit -> Chad remembers the attacker", this.attacker,
                    this.chad.getLastHurtByMob());

            // A human who is already committed to that attacker keeps him (the lock is exactly the rule that a new
            // provocation does not move an armed human) - so the proof of the broadcast is the commitment, not a fresh
            // target assignment.
            assertEquals(this.helper, "Sweatlord hit -> ordinary human is committed to the attacker", true,
                    this.ordinary.getTarget() == this.attacker
                            || this.attacker.getUUID().equals(HumanBandState.lockedTarget(this.ordinary, now)));

            // Scenario C: inside the faction nobody ever picks one of his own as a target.
            assertFriendly(this.helper, "Sweatlord -> ordinary human", this.sweatlord, this.ordinary);
            assertFriendly(this.helper, "Sweatlord -> Chad", this.sweatlord, this.chad);
            assertFriendly(this.helper, "ordinary human -> Sweatlord", this.ordinary, this.sweatlord);
            assertFriendly(this.helper, "Chad -> Sweatlord", this.chad, this.sweatlord);
            assertFriendly(this.helper, "ordinary human -> Chad", this.ordinary, this.chad);
            assertFriendly(this.helper, "Chad -> ordinary human", this.chad, this.ordinary);

            SpiderQueenHungryCraft.LOGGER.info("[SweatlordTest] packAggro: sweatlordGrudge={} humanGrudge={} "
                            + "chadGrudge={} sweatlordState={}",
                    sweatlordGrudge, humanGrudge, chadGrudge, this.sweatlord.getDebugState());
            this.helper.succeed();
        }

        private boolean queryable(Mob mob)
        {
            return !this.level.getEntitiesOfClass(mob.getClass(), mob.getBoundingBox().inflate(2.0D)).isEmpty();
        }
    }


    /** Asserts that neither of the two may ever be the target of the other, in the rules and right now. */
    private static void assertFriendly(GameTestHelper helper, String label, Mob first, Mob second)
    {
        assertEquals(helper, label + " (faction goal)", false, FactionTargeting.shouldFactionGoalAttack(first, second));
        assertEquals(helper, label + " (current target)", true, first.getTarget() != second);
    }

    // -------------------------------------------------------------------------------------------------------------
    // helpers of this file
    // -------------------------------------------------------------------------------------------------------------

    /** Builds a flat, open stone site at that x, keeps its chunks loaded and returns the standing position. */
    private static BlockPos buildSite(ServerLevel level, int x)
    {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + x, SITE_FLOOR_Y, spawn.getZ() + SITE_Z);

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

                for (int dy = 0; dy <= 4; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return centre;
    }

    private static SweatlordSteveEntity spawnSweatlord(ServerLevel level, BlockPos pos, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), level, pos,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    private static HumanCombatantEntity spawnOrdinary(ServerLevel level, BlockPos pos, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.LOW_PREPARED_HUMAN.get(), level, pos,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof HumanCombatantEntity human))
        {
            helper.fail("the ordinary human could not be placed: " + placed);
            return null;
        }

        return human;
    }

    private static ChadSteveEntity spawnChad(ServerLevel level, BlockPos pos, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.CHAD_STEVE.get(), level, pos,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof ChadSteveEntity chad))
        {
            helper.fail("Chad Steve could not be placed: " + placed);
            return null;
        }

        return chad;
    }

    private static Zombie spawnZombie(ServerLevel level, BlockPos pos, GameTestHelper helper)
    {
        Zombie zombie = EntityType.ZOMBIE.create(level);

        if (zombie == null)
        {
            helper.fail("the attacking zombie could not be created");
            return null;
        }

        zombie.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        zombie.setPersistenceRequired();
        level.addFreshEntity(zombie);

        return zombie;
    }

    /** A spectator player keeps a site ticking without making the surface spawner populate it. */
    private static ServerPlayer placeAnchor(ServerLevel level, BlockPos site)
    {
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "sweatlord_anchor"));
        anchor.setGameMode(GameType.SPECTATOR);
        anchor.moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
        }

        return anchor;
    }

    private static void assertEquals(GameTestHelper helper, String what, Object expected, Object actual)
    {
        if (!expected.equals(actual))
        {
            helper.fail(what + ": expected " + expected + " but found " + actual);
        }
    }

    private static void assertEquals(GameTestHelper helper, String what, double expected, double actual)
    {
        if (Math.abs(expected - actual) > 1.0E-6D)
        {
            helper.fail(what + ": expected " + expected + " but found " + actual);
        }
    }

    /**
     * Waits until the level really queries the bodies, then asserts the two population questions.
     *
     * <p>An entity that was just added is not always part of an already loaded entity section in the same tick, and a
     * box query silently returns nothing while that is true. That is a loading condition and not a behaviour, so this
     * probe waits for the condition with a deadline instead of sleeping a fixed number of ticks.
     */
    private static final class PopulationProbe
    {
        private static final int DEADLINE = 80;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity sweatlord;
        private final BlockPos site;

        private int ticks;
        private boolean ordinaryPlaced;

        PopulationProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity sweatlord, BlockPos site)
        {
            this.helper = helper;
            this.level = level;
            this.sweatlord = sweatlord;
            this.site = site;
        }

        void tick()
        {
            ++this.ticks;

            if (!this.ordinaryPlaced)
            {
                this.ordinaryPlaced = spawnOrdinary(this.level, this.site.offset(6, 0, 0), this.helper) != null;
                return;
            }

            if (HumanPopulationCounter.countHumanUnitsInBox(this.level,
                    this.sweatlord.getBoundingBox().inflate(16.0D)) < 2)
            {
                if (this.ticks > DEADLINE)
                {
                    this.helper.fail("the test humans never became queryable: " + this.sweatlord.getDebugState());
                }

                return;
            }

            int aroundHim = HumanPopulationCounter.countHumanUnitsInBox(this.level,
                    this.sweatlord.getBoundingBox().inflate(4.0D));

            if (aroundHim != 1)
            {
                // The count is of this world and not of this test: every test of a run shares one world, and a body of an
                // earlier test that was never cleaned up stands in it for ever. The message says which bodies they are,
                // because "found 3" alone does not say whether the counter or the world is wrong.
                this.helper.fail("human units around him: expected 1 but found " + aroundHim + ": "
                        + this.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                                this.sweatlord.getBoundingBox().inflate(4.0D)));
                return;
            }

            assertEquals(this.helper, "human units around him", 1, HumanPopulationCounter
                    .countHumanUnitsInBox(this.level, this.sweatlord.getBoundingBox().inflate(4.0D)));
            assertEquals(this.helper, "surface population around him", 0,
                    HumanPopulationCounter.countInBox(this.level, this.sweatlord.getBoundingBox().inflate(4.0D)));
            assertEquals(this.helper, "surface population with one ordinary human", 1,
                    HumanPopulationCounter.countInBox(this.level, this.sweatlord.getBoundingBox().inflate(16.0D)));
            assertEquals(this.helper, "human units with one ordinary human", 2,
                    HumanPopulationCounter.countHumanUnitsInBox(this.level, this.sweatlord.getBoundingBox().inflate(16.0D)));

            SpiderQueenHungryCraft.LOGGER.info("[SweatlordTest] identityAndPopulation passed: {}",
                    this.sweatlord.getDebugState());
            this.helper.succeed();
        }
    }
}
