package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
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
 * The bucket emergencies of the Sweatlord (Sweatlord V1.1): being on fire, and falling to death.
 *
 * <p>Every test of this file asks a question the round really cares about, and none of them is ever satisfied by "a
 * bucket was equipped somewhere":
 *
 * <ul>
 * <li>the fire test asks for a <b>real water source</b> in the world, for the fire to be <b>really</b> out afterwards,
 * for his own source to be taken <b>back</b>, and for the hand to go water bucket, empty bucket, weapon -</li>
 * <li>the lonely fire test asks the same of a body with <b>no enemy at all</b>, because a fire nobody is fighting still
 * has to wake the brain up - otherwise he burns down with a bucket on his hip and only ever waters his feet while he
 * happens to be fighting somebody -</li>
 * <li>the failed recovery test breaks the pickup on purpose (he is knocked out of the reach of his own source) and then
 * demands that the <b>next</b> emergency works exactly the same: a bucket is never a lock,</li>
 * <li>the fall tests demand that a lethal fall really ends in water and without damage, that a harmless fall does not
 * cost a bucket at all, and that the water is never poured ten blocks early,</li>
 * <li>and the rule test asks the placement rule itself: out of reach is refused, a block a fluid can not replace is
 * refused, and the spot a player really could pour into is allowed.</li>
 * </ul>
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordWaterGameTest
{
    /** The floor of the test sites and the half width of their flat area. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 40;

    /** A corner of the test world of its own. */
    private static final int SITE_X = 3072;
    private static final int SITE_Z = 512;

    /** The enemy that keeps the brain awake in the scenarios that need a real fight running. */
    private static final double ENEMY_DISTANCE = 8.0D;

    /** A fall that really hurts: twenty-five blocks is death for a body of twenty health without the water. */
    private static final int LETHAL_FALL = 25;

    /** A fall that costs nothing: four blocks, well under the seven a bucket would be worth. */
    private static final int HARMLESS_FALL = 4;

    private SweatlordWaterGameTest()
    {
    }

    /**
     * The placement rule, asked directly: out of the reach of a player is refused, a block a fluid can not replace is
     * refused, and the spot a player really could pour into is allowed. It is the same rule both emergencies use, so a
     * broken rule would be caught here even if the two emergencies happened to pass.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 80)
    public static void thePlacementRuleRefusesWhatAPlayerCouldNotDo(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 480);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, 0.0D, helper);

        if (sweatlord == null)
        {
            return;
        }

        ServerLevel level = site.level();
        Vec3 eye = sweatlord.getEyePosition();
        BlockPos here = sweatlord.blockPosition();

        // The positive control first: the spot a player stands in is pour-able, or the rest of this test would be empty.
        if (!PlayerLikeActions.canPlaceWater(level, here, eye))
        {
            helper.fail("a spot the body stands in was refused by the placement rule, so the rule is broken in the"
                    + " other direction: " + here);
            return;
        }

        if (PlayerLikeActions.canPlaceWater(level, here.offset(20, 0, 0), eye))
        {
            helper.fail("a bucket was allowed twenty blocks away, which is far outside the reach of a player");
            return;
        }

        BlockPos stone = here.offset(1, 0, 0);
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());

        if (PlayerLikeActions.canPlaceWater(level, stone, eye))
        {
            helper.fail("a water source was allowed inside a stone block, which no bucket of the game could do");
            return;
        }

        SpiderQueenHungryCraft.LOGGER.info("[WaterTest] the placement rule holds: reach and replaceable both asked");
        helper.succeed();
    }

    /**
     * Watches the fire emergency: a real source in the world, the fire really out, the hand going bucket, empty bucket
     * and weapon, and the source taken back - or, in the failed recovery scenario, a second emergency that still works.
     */
    private static final class FireProbe
    {
        private static final int IGNITE_TICK = 20;
        private static final int SECOND_IGNITE_TICK = 150;
        private static final int JUDGE_TICK = 250;

        /** How far he is knocked away from his own source, which is what breaks the scoop up. */
        private static final double KNOCKBACK = 9.0D;

        /** How far the second fire is lit from the first source, so it is a fire of its own and not its smoke. */
        private static final double DRY_OFFSET = 10.0D;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity mob;
        private final boolean breakTheFirstRecovery;
        private final Vec3 knockedAwayTo;

        /** The enemy of this scenario: a bucket emergency is only asked of a brain that is really fighting. */
        @Nullable
        private final net.minecraft.world.entity.LivingEntity enemy;

        private int ticks;
        private int placements;
        private int firstWaterTick = -1;
        private boolean waterLastTick;
        private boolean waterBucketSeen;
        private boolean emptyBucketSeen;
        private boolean burned;
        private boolean fireWentOut;
        private boolean knockedAway;
        @Nullable
        private BlockPos firstSpot;
        @Nullable
        private BlockPos lastSpot;
        private boolean judged;

        FireProbe(GameTestHelper helper, SweatlordSteveEntity mob, boolean breakTheFirstRecovery)
        {
            this.helper = helper;
            this.mob = mob;
            this.breakTheFirstRecovery = breakTheFirstRecovery;
            this.knockedAwayTo = mob.position().add(KNOCKBACK, 0.0D, 0.0D);
            this.enemy = mob.getTarget();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            if (this.enemy != null && this.mob.getTarget() != this.enemy)
            {
                // The scenario commits its enemy. A body that is held or knocked around reads as one that is on its way
                // back to its post, and the band rules of the human units then refuse to pick a target again - a brain
                // without a target does not fight, and an emergency that is never asked is not a failed emergency.
                this.mob.setTarget(this.enemy);
            }

            if (this.ticks == IGNITE_TICK || (this.breakTheFirstRecovery && this.ticks == SECOND_IGNITE_TICK))
            {
                if (this.breakTheFirstRecovery && this.ticks == SECOND_IGNITE_TICK)
                {
                    // The second fire has to be a fire of its own, and not the smoke of the first one: the body is put on
                    // a dry spot first, because a body that stands in the water it poured is extinguished by the water
                    // and not by a bucket - and then the second ignition would prove nothing about the bucket at all.
                    this.mob.moveTo(this.knockedAwayTo.x, this.knockedAwayTo.y, this.knockedAwayTo.z + DRY_OFFSET,
                            this.mob.getYRot(), 0.0F);
                    this.mob.setDeltaMovement(Vec3.ZERO);
                }

                this.mob.setRemainingFireTicks(200);
            }

            // The second fire of the scenario is <b>kept</b> burning until the bucket has answered it: a single ignition
            // is put out by anything - the water of the earlier pour that the body happened to walk through, the rain, a
            // puddle - and the emergency is never asked at all. The first fire is deliberately not kept alight, because
            // "the fire really went out" is part of what this file measures; and the moment the second source is really in
            // the world, the flame is left alone again, so what put it out is the bucket.
            boolean fireIsWanted = this.breakTheFirstRecovery && this.placements == 1
                    && this.ticks >= SECOND_IGNITE_TICK;

            if (fireIsWanted && !this.mob.isOnFire())
            {
                this.mob.setRemainingFireTicks(200);
            }

            if (this.breakTheFirstRecovery && this.knockedAway && this.ticks > SECOND_IGNITE_TICK)
            {
                // The body is held on the dry spot for the rest of the scenario: the environment rule of the brain walks a
                // burning body around, and the water of the first recovery still lies a few blocks away - a body that
                // walks into it is put out by that water and not by a bucket, and the scenario would be measuring the
                // first pour instead of the second one.
                this.mob.moveTo(this.knockedAwayTo.x, this.knockedAwayTo.y, this.knockedAwayTo.z + DRY_OFFSET,
                        this.mob.getYRot(), 0.0F);
                this.mob.setDeltaMovement(Vec3.ZERO);
            }

            if (this.mob.isOnFire())
            {
                this.burned = true;
            }
            else if (this.burned)
            {
                this.fireWentOut = true;
            }

            this.waterBucketSeen |= this.mob.getMainHandItem().is(VirtualLoadout.WATER_BUCKET);
            this.emptyBucketSeen |= this.mob.getMainHandItem().is(VirtualLoadout.EMPTY_BUCKET);

            BlockPos feet = this.mob.blockPosition();
            boolean water = this.mob.level().getBlockState(feet).is(Blocks.WATER);

            if (water && !this.waterLastTick)
            {
                ++this.placements;
                this.lastSpot = feet;

                if (this.firstSpot == null)
                {
                    this.firstSpot = feet;
                    this.firstWaterTick = this.ticks;

                    // The recovery is broken the way a fight breaks it, and it has to be broken <b>now</b>: scooping
                    // your own source back up is a single tick of work for a player, so a knock away two ticks later
                    // does not break the recovery at all - it only makes the premise of this scenario ("the recovery
                    // really failed") untrue, which is exactly how this test failed the first time it ran.
                    if (this.breakTheFirstRecovery)
                    {
                        this.mob.moveTo(this.knockedAwayTo.x, this.knockedAwayTo.y, this.knockedAwayTo.z,
                                this.mob.getYRot(), 0.0F);
                        this.mob.setDeltaMovement(Vec3.ZERO);
                        this.knockedAway = true;
                    }
                }
            }

            this.waterLastTick = water;

            if (this.ticks >= JUDGE_TICK)
            {
                judge();
            }
        }

        private void judge()
        {
            this.judged = true;
            BlockPos first = this.firstSpot;
            BlockPos last = this.lastSpot;
            boolean firstStillWater = first != null && this.mob.level().getBlockState(first).is(Blocks.WATER);
            boolean lastStillWater = last != null && this.mob.level().getBlockState(last).is(Blocks.WATER);

            if (this.placements < 1)
            {
                fail("no water source was ever poured, so the fire was never answered with a bucket at all: "
                        + this.mob.getDebugState());
                return;
            }

            if (!this.burned || !this.fireWentOut)
            {
                fail("the body was never really on fire, or the fire never went out: burned=" + this.burned
                        + ", out=" + this.fireWentOut + " | " + this.mob.getDebugState());
                return;
            }

            if (!this.waterBucketSeen || !this.emptyBucketSeen)
            {
                fail("the hand never showed the water bucket and the empty bucket, so the bucket was equipped on paper"
                        + " only: waterBucketSeen=" + this.waterBucketSeen + ", emptyBucketSeen=" + this.emptyBucketSeen);
                return;
            }

            if ("water_extinguish".equals(this.mob.debugSkill()))
            {
                fail("the extinguish action is still holding the body at the end, so it never ended");
                return;
            }

            if (this.breakTheFirstRecovery)
            {
                if (!firstStillWater)
                {
                    fail("the first source is not in the world any more, so the recovery of this scenario did not"
                            + " really fail and the test proves nothing");
                    return;
                }

                if (this.placements < 2)
                {
                    fail("the second fire was not answered with a second source, so a failed recovery locked the"
                            + " bucket for ever: placements=" + this.placements + " | " + this.mob.getDebugState());
                    return;
                }

                if (lastStillWater)
                {
                    fail("the second source was not taken back, so the machine only works while the first one fails");
                    return;
                }
            }
            else if (firstStillWater)
            {
                fail("the source he placed himself was never taken back: it is still water at " + first);
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[WaterTest] the fire emergency held: placements={}, firstSource={},"
                            + " lastSource={}, knockedAway={}", this.placements, firstStillWater, lastStillWater,
                    this.knockedAway);
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /**
     * Watches a fall: whether a real source appeared in the world, how high above the floor it was poured, whether the
     * body landed and how much health the landing cost.
     */
    private static final class FallProbe
    {
        private static final int LANDING_TAIL = 25;
        private static final int DEADLINE = 90;

        /** How far above the floor the water may be poured and still be the timing of a real clutch. */
        private static final int MAX_EARLY_HEIGHT = 8;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;
        private final boolean lethal;
        private final int floorY;
        private final float healthBefore;

        private int ticks;
        private boolean waterSeen;
        private int waterHeightAboveTheFloor = Integer.MAX_VALUE;
        private boolean landed;
        private int landingTick = -1;
        private boolean judged;

        FallProbe(GameTestHelper helper, Site site, SweatlordSteveEntity mob, boolean lethal)
        {
            this.helper = helper;
            this.level = site.level();
            this.mob = mob;
            this.lethal = lethal;
            this.floorY = SITE_FLOOR_Y;
            this.healthBefore = mob.getHealth();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            if (!this.waterSeen && this.findWater())
            {
                this.waterSeen = true;
            }

            if (!this.landed && this.mob.onGround())
            {
                this.landed = true;
                this.landingTick = this.ticks;
            }

            boolean done = this.lethal ? this.landed && this.ticks > this.landingTick + LANDING_TAIL
                    : this.ticks >= DEADLINE;

            if (done)
            {
                judge();
            }
        }

        /** @return true when a real water source stands in the column of the falling body right now. */
        private boolean findWater()
        {
            BlockPos base = this.mob.blockPosition();

            for (int dy = -3; dy <= 3; ++dy)
            {
                BlockPos pos = base.above(dy);

                if (this.level.getBlockState(pos).is(Blocks.WATER))
                {
                    this.waterHeightAboveTheFloor = pos.getY() - this.floorY;
                    return true;
                }
            }

            return false;
        }

        private void judge()
        {
            this.judged = true;
            float damage = this.healthBefore - this.mob.getHealth();

            if (!this.landed)
            {
                this.helper.fail("the body never landed, so the scenario can not judge the clutch");
                return;
            }

            if (this.lethal)
            {
                if (!this.waterSeen)
                {
                    this.helper.fail("a " + LETHAL_FALL + " block fall was survived without a single water source in"
                            + " the world, so either the clutch never ran or it cheated the fall: damage=" + damage
                            + " | " + this.mob.getDebugState());
                    return;
                }

                if (damage > 0.0F)
                {
                    this.helper.fail("the clutch did not prevent the fall damage of a " + LETHAL_FALL + " block fall:"
                            + " health " + this.healthBefore + " -> " + this.mob.getHealth());
                    return;
                }

                if (this.waterHeightAboveTheFloor > MAX_EARLY_HEIGHT)
                {
                    this.helper.fail("the water was poured " + this.waterHeightAboveTheFloor + " blocks above the"
                            + " floor, which is neither the timing of a player nor inside the reach of one");
                    return;
                }

                if ("mlg_water_clutch".equals(this.mob.debugSkill()))
                {
                    this.helper.fail("the clutch is still holding the body at the end, so it never ended");
                    return;
                }
            }
            else if (this.waterSeen)
            {
                this.helper.fail("a harmless fall of " + HARMLESS_FALL + " blocks cost a bucket: water was poured "
                        + this.waterHeightAboveTheFloor + " blocks above the floor");
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[WaterTest] the fall was handled: lethal={}, waterSeen={},"
                            + " heightAboveFloor={}, damage={}", this.lethal, this.waterSeen,
                    this.waterHeightAboveTheFloor, damage);
            this.helper.succeed();
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 1) the fire: his own water, his own source back, and a bucket in his hand
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 300)
    public static void fireIsPutOutWithHisOwnWaterAndTheSourceIsTakenBack(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 0);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, 0.0D, helper);
        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);

        if (sweatlord == null || enemy == null)
        {
            return;
        }

        sweatlord.setTarget(enemy);
        helper.onEachTick(new FireProbe(helper, sweatlord, false)::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 2) the fire of a body nobody is fighting: the wake up call
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The lonely fire, which is the bug this scenario was written for.
     *
     * <p>A player who catches fire puts a bucket at their own feet in the same tick it happens, whether they are in the
     * middle of a duel or walking home alone. The Sweatlord did not: the brain that owns the bucket is only ticked by his
     * fight goal, and that goal only starts while the brain already <b>wants</b> the body - an enemy, an encounter, a
     * threat, a fall that would kill him. Burning was not on that list, so a fire nobody was fighting was never even
     * looked at: he burned down with a water bucket on his hip, and the water only ever came out while he happened to be
     * fighting somebody.
     *
     * <p>The scenario therefore removes every other reason first (nothing is spawned here at all, and the body is left
     * alone until the brain is really asleep) and only then sets him alight. What is demanded is the whole chain of the
     * real trick: the fire is noticed, a real source stands in the very block his own feet are in, the fire really goes
     * out, the source is taken back, and the body is handed back in the same breath - a clutch that parks him in his own
     * puddle is a body that stopped fighting to watch water evaporate.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 200)
    public static void aLoneBurningBodyWatersItsOwnFeetAndWalksOn(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 600);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, 0.0D, helper);

        if (sweatlord == null)
        {
            return;
        }

        helper.onEachTick(new LoneFireProbe(helper, site, sweatlord)::tick);
    }

    /**
     * Watches the fire of a body with no enemy at all: whether the brain wakes up for it, how fast the water comes out,
     * where it comes out, whether the fire really dies, and whether the body is free again at once.
     */
    private static final class LoneFireProbe
    {
        /** How long the body may take to answer the fire once it was lit: a player answers in the very tick. */
        private static final int ANSWER_TICKS = 6;

        /** How long after the pour the fire has to be out, because the body stands in the water it just placed. */
        private static final int OUT_TICKS = 8;

        /** How long the clutch may still hold the body after the fire is out: one tick of scooping, and no more. */
        private static final int RELEASE_TICKS = 2;

        /** The body is left alone until the brain is asleep, and that sleep is the premise of this scenario. */
        private static final int SETTLE_DEADLINE = 60;

        /** The flame of the scenario, long enough that only water can put it out. */
        private static final int FIRE_TICKS = 200;

        /** How far up and down his own column the water is looked for. */
        private static final int WATER_SCAN = 3;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;

        private int ticks;
        private int litTick = -1;

        /** The premise: the brain of the body was really asleep when the fire started. */
        private boolean asleepBefore;

        private boolean burned;
        private int pourTick = -1;
        private int pourHeightAboveFeet = Integer.MAX_VALUE;

        @Nullable
        private BlockPos pourSpot;

        private int outTick = -1;
        private boolean judged;

        LoneFireProbe(GameTestHelper helper, Site site, SweatlordSteveEntity mob)
        {
            this.helper = helper;
            this.level = site.level();
            this.mob = mob;
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            if (this.litTick < 0)
            {
                this.settleAndLight();
                return;
            }

            if (this.mob.isOnFire())
            {
                this.burned = true;
            }
            else if (this.burned && this.outTick < 0)
            {
                this.outTick = this.ticks;
            }

            if (this.pourTick < 0)
            {
                if (this.findPour())
                {
                    return;
                }

                if (this.ticks > this.litTick + ANSWER_TICKS)
                {
                    fail("the body burned for " + (this.ticks - this.litTick) + " ticks with nobody around it and never"
                            + " poured a source at its own feet, so a lonely fire is not answered at all:"
                            + " asleepBeforeTheFire=" + this.asleepBefore + " | " + this.mob.getDebugState());
                }

                return;
            }

            if (this.outTick > 0 && this.ticks > this.outTick + RELEASE_TICKS)
            {
                judge();
            }
            else if (this.ticks > this.litTick + ANSWER_TICKS + OUT_TICKS + RELEASE_TICKS + 4)
            {
                fail("the fire was not put out by the source at his own feet: lit=" + this.litTick
                        + ", poured=" + this.pourTick + ", out=" + this.outTick + " | " + this.mob.getDebugState());
            }
        }

        private void settleAndLight()
        {
            if (this.ticks < 20)
            {
                return;
            }

            // The premise of this scenario is a body that nothing in the world is interested in, so every waking reason
            // that wandered in is removed first - a monster in reach starts a fight, and a brain that is fighting already
            // answered the fire before this round, which is the whole difference the scenario is about.
            for (net.minecraft.world.entity.LivingEntity stray : this.level.getEntitiesOfClass(
                    net.minecraft.world.entity.LivingEntity.class, this.mob.getBoundingBox().inflate(48.0D)))
            {
                if (stray != this.mob && CombatContext.isValidEnemy(this.mob, stray))
                {
                    stray.discard();
                }
            }

            if (this.mob.brain().wantsControl())
            {
                if (this.ticks >= SETTLE_DEADLINE)
                {
                    fail("the brain of the body never went to sleep, so this scenario can not ask its question at all:"
                            + " " + this.mob.getDebugState());
                }

                return;
            }

            this.asleepBefore = true;
            this.litTick = this.ticks;
            this.mob.setRemainingFireTicks(FIRE_TICKS);
        }

        /** @return true when a real source of his own stands in his own feet, which is where a bucket has to go. */
        private boolean findPour()
        {
            BlockPos feet = this.mob.blockPosition();

            for (int dy = -WATER_SCAN; dy <= WATER_SCAN; ++dy)
            {
                BlockPos pos = feet.above(dy);

                if (!this.level.getBlockState(pos).is(Blocks.WATER))
                {
                    continue;
                }

                this.pourTick = this.ticks;
                this.pourSpot = pos.immutable();
                this.pourHeightAboveFeet = pos.getY() - feet.getY();

                return true;
            }

            return false;
        }

        private void judge()
        {
            this.judged = true;
            BlockPos spot = this.pourSpot;

            if (!this.burned || spot == null)
            {
                fail("the body never really burned, so the scenario proves nothing: burned=" + this.burned
                        + " | " + this.mob.getDebugState());
                return;
            }

            if (this.pourHeightAboveFeet != 0)
            {
                fail("the water went " + this.pourHeightAboveFeet + " blocks away from his own feet, but a player"
                        + " pours the bucket into the block they are standing in: " + spot);
                return;
            }

            if (this.outTick - this.pourTick > OUT_TICKS)
            {
                fail("the fire burned for " + (this.outTick - this.pourTick) + " ticks after the water stood in his own"
                        + " feet, which is not the extinguish of a bucket at all");
                return;
            }

            if (this.level.getBlockState(spot).is(Blocks.WATER))
            {
                fail("the source at his own feet was never taken back, so a lonely fire leaves the bucket behind: "
                        + spot + " | " + this.mob.getDebugState());
                return;
            }

            if ("water_extinguish".equals(this.mob.debugSkill()))
            {
                fail("the clutch is still holding the body after the fire is out, so the body is parked in its own"
                        + " water instead of walking on: " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[WaterTest] a lonely fire was answered: asleepBeforeTheFire={},"
                            + " ticksToAnswer={}, ticksToOut={}, waterAtHisOwnFeet={}, sourceTakenBack={}, bodyReleased={}",
                    this.asleepBefore, this.pourTick - this.litTick, this.outTick - this.pourTick,
                    this.pourHeightAboveFeet == 0, this.level.getBlockState(spot).isAir(),
                    !"water_extinguish".equals(this.mob.debugSkill()));
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 3) the failed recovery: a bucket is never a lock
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The requirement that matters most in a fight, and the one this mod could get wrong for ever without anyone
     * noticing: a recovery that <b>fails</b> must not take the bucket away for the rest of the encounter.
     *
     * <p>The failure is real and not simulated: he is knocked out of the reach of his own source right after the pour, so
     * the scoop up can not happen and the water stays in the world - which is exactly what a knockback, a flowing source
     * or a replaced block does. Then a <b>second</b> fire has to be answered with a <b>second</b> source, and that one
     * has to be taken back normally.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 400)
    public static void aFailedPickupDoesNotLockTheBucket(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 120);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, 0.0D, helper);
        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);

        if (sweatlord == null || enemy == null)
        {
            return;
        }

        sweatlord.setTarget(enemy);
        helper.onEachTick(new FireProbe(helper, sweatlord, true)::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 4) the MLG clutch, and the fall that must not cost a bucket
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 300)
    public static void mlgWaterClutchSurvivesALethalFall(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 240);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, LETHAL_FALL, helper);

        if (sweatlord == null)
        {
            return;
        }

        helper.onEachTick(new FallProbe(helper, site, sweatlord, true)::tick);
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_water", timeoutTicks = 200)
    public static void aHarmlessFallDoesNotCostABucket(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, 360);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, HARMLESS_FALL, helper);

        if (sweatlord == null)
        {
            return;
        }

        helper.onEachTick(new FallProbe(helper, site, sweatlord, false)::tick);
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
    private static SweatlordSteveEntity spawnSweatlord(Site site, double height, GameTestHelper helper)
    {
        BlockPos at = new BlockPos(site.centre().getX(), SITE_FLOOR_Y + (int) height, site.centre().getZ());
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(), at,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    /** The monster that keeps the brain of the body awake: no AI, persistent, and never in the way. */
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
