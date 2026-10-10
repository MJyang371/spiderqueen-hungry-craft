package spiderqueenhungrycraft.soulfire;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The round 7I flight course, walked as a game test: a ninety degree order, a full reversal, a descent and a breath.
 *
 * <p>Every number here is read from the real flight - the real heading, the real velocity, the real synced landing
 * numbers - because the round is about what the animal does in the air and not about what it was told to do.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class Soulfire7IFlightCourseGameTest
{
    private static final int LEG_TICKS = 120;
    private static final double MOVING = 0.08D;
    private static final double TURN_LIMIT = 8.05D;
    private static final BlockPos START = new BlockPos(2, 1, 2);

    private Soulfire7IFlightCourseGameTest()
    {
    }

    private static void expect(GameTestHelper helper, String what, boolean ok)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }

    /** One airborne unit, facing yaw 0 (that is +Z), with a clear lane of air around it. */
    private static SoulfireDragonEntity airborneUnit(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), START);
        BlockPos start = helper.absolutePos(START);

        // A large skeleton dragon needs air to fly in, and the lane is carved wider than the errand so that the walls of
        // the structure are never the thing being measured.
        for (int x = -6; x <= 30; x++)
        {
            for (int z = -6; z <= 6; z++)
            {
                for (int y = 1; y <= 26; y++)
                {
                    helper.getLevel().setBlockAndUpdate(start.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }

        dragon.teleportTo(start.getX() + 0.5D, start.getY() + 20.0D, start.getZ() + 0.5D);
        dragon.setYRot(0.0F);
        dragon.yBodyRot = 0.0F;
        dragon.setFlying(true);
        dragon.setHovering(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        return dragon;
    }

    /** A ninety degree order: the unit must turn, and only then must its motion agree with its facing. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire7i_flight", timeoutTicks = 400)
    public static void aninetydegreeorderturnsbeforeitflies(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = airborneUnit(helper);
        BlockPos start = helper.absolutePos(START);
        double dx = 20.0D;
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, 0.0D));

        // The errand is placed at the height the unit is really flying at: a target below it would be a descent, and this
        // test is about the turn.
        dragon.setMoveTo(BlockPos.containing(dragon.position()).offset((int) dx, 0, 0), 7001);

        int[] ticks = {0};
        float[] lastYaw = {dragon.getYRot()};
        int[] moving = {0};
        int[] misaligned = {0};
        int[] backwards = {0};
        int[] travelledWhileTurning = {0};
        double[] worstTurn = {0.0D};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            float yaw = dragon.getYRot();
            double turn = Math.abs(Mth.wrapDegrees(yaw - lastYaw[0]));
            double remaining = Math.abs(Mth.wrapDegrees(desiredYaw - yaw));
            Vec3 motion = dragon.getDeltaMovement();
            double speed = motion.horizontalDistance();

            worstTurn[0] = Math.max(worstTurn[0], turn);

            expect(helper, "no turn over " + TURN_LIMIT + " degrees in one tick: " + turn, turn <= TURN_LIMIT);

            if (speed > MOVING)
            {
                moving[0]++;

                double cosine = (motion.x * -Math.sin(Math.toRadians(yaw)) + motion.z * Math.cos(Math.toRadians(yaw)))
                        / speed;

                if (cosine < 0.94D)
                {
                    misaligned[0]++;
                }

                if (cosine < -0.05D)
                {
                    backwards[0]++;
                }
            }

            if (remaining > 60.0D && speed > 0.25D)
            {
                travelledWhileTurning[0]++;
            }

            lastYaw[0] = yaw;

            if (ticks[0] == LEG_TICKS)
            {
                expect(helper, "the unit really moved on the order: " + moving[0] + " moving samples", moving[0] >= 20);
                expect(helper, "and every moving sample agreed with the facing: " + misaligned[0] + " of " + moving[0]
                        + " disagreed (worst turn " + worstTurn[0] + ")", misaligned[0] == 0);
                expect(helper, "and it never travelled backwards: " + backwards[0] + " samples", backwards[0] == 0);
                expect(helper, "and it never travelled while it still had to turn: " + travelledWhileTurning[0]
                        + " samples", travelledWhileTurning[0] == 0);
                helper.succeed();
            }
        });
    }

    /**
     * A full reversal: an order to the opposite side. A large animal has to turn on the spot first, and nothing of the
     * turn may be flown backwards.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire7i_flight", timeoutTicks = 400)
    public static void afullreversalturnsinsteadofflyingbackwards(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = airborneUnit(helper);
        BlockPos start = helper.absolutePos(START);

        // Already facing -X, then ordered to +X: a reversal of one hundred and eighty degrees.
        dragon.setYRot(-90.0F);
        dragon.yBodyRot = -90.0F;
        Leg leg = new Leg(-90.0F, 90.0D);

        dragon.setMoveTo(BlockPos.containing(dragon.position()).offset(20, 0, 0), 7002);

        helper.onEachTick(() ->
        {
            leg.sample(helper, dragon);

            if (leg.ticks == LEG_TICKS)
            {
                leg.report(helper, "the reversal");
                expect(helper, "and the unit really turned around, it faces "
                        + Math.round(Mth.wrapDegrees(dragon.getYRot() - 90.0F)) + " degrees off the new bearing",
                        Math.abs(Mth.wrapDegrees(dragon.getYRot() - 90.0F)) < 25.0F);
                helper.succeed();
            }
        });
    }

    /** The descent: the unit is ordered onto the floor it was lifted from, and the synced landing numbers are watched. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire7i_flight", timeoutTicks = 600)
    public static void adescentpassesmeasuredlandingposes(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = airborneUnit(helper);
        BlockPos floor = helper.absolutePos(START);

        dragon.setMoveTo(floor, 7003);

        int[] ticks = {0};
        boolean[] sawLanding = {false};
        float[] maxBlend = {0.0F};
        boolean[] blendCameHome = {false};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            float blend = dragon.landingAnimationProgress();
            maxBlend[0] = Math.max(maxBlend[0], blend);

            if (dragon.isLanding())
            {
                sawLanding[0] = true;
            }

            boolean standing = dragon.onGround() && !dragon.isFlying() && !dragon.isHovering() && !dragon.isLanding();

            if (sawLanding[0] && standing && blend <= 0.001F)
            {
                blendCameHome[0] = true;
            }

            if (standing && ticks[0] > 20)
            {
                expect(helper, "the descent was seen as a landing", sawLanding[0]);
                expect(helper, "and the animation blend rose during it: " + maxBlend[0], maxBlend[0] > 0.25F);
                expect(helper, "and the blend went back to rest after touchdown: " + blend, blendCameHome[0]);
                expect(helper, "and the unit stands on the floor", Math.abs(dragon.getY() - floor.getY()) < 3.0D);
                helper.succeed();
            }
            else if (ticks[0] > 560)
            {
                helper.fail("the unit never came down: landing=" + dragon.isLanding() + " blend=" + blend + " y="
                        + dragon.getY() + " onGround=" + dragon.onGround());
            }
        });
    }

    /** The breath: the head must hold its aim while the soul fire runs, and it must not shake on its neck. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire7i_flight", timeoutTicks = 500)
    public static void theheadholdsthelinethroughoutthebreath(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = airborneUnit(helper);

        dragon.breatheAt(helper.absolutePos(START).offset(8, 0, 0));

        int[] ticks = {0};
        int[] samples = {0};
        int[] flips = {0};
        double[] biggestStep = {0.0D};
        float[] minYaw = {Float.MAX_VALUE};
        float[] maxYaw = {-Float.MAX_VALUE};
        float[] lastYaw = {0.0F};
        double[] lastStep = {0.0D};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] == 80)
            {
                lastYaw[0] = dragon.syncedHeadYaw();
            }

            if (ticks[0] > 80 && dragon.isBreathing())
            {
                float headYaw = dragon.syncedHeadYaw();
                double signed = Mth.wrapDegrees(headYaw - lastYaw[0]);
                double step = Math.abs(signed);

                samples[0]++;
                biggestStep[0] = Math.max(biggestStep[0], step);
                minYaw[0] = Math.min(minYaw[0], headYaw);
                maxYaw[0] = Math.max(maxYaw[0], headYaw);

                if (step > 0.10D && Math.abs(lastStep[0]) > 0.10D && Math.signum(signed) != Math.signum(lastStep[0]))
                {
                    flips[0]++;
                }

                lastStep[0] = signed;
                lastYaw[0] = headYaw;
            }

            if (ticks[0] == 160)
            {
                float width = samples[0] == 0 ? 0.0F : maxYaw[0] - minYaw[0];

                expect(helper, "the unit really breathed: " + samples[0] + " samples", samples[0] >= 30);
                expect(helper, "and the head never jumped: " + biggestStep[0] + " degrees in one tick",
                        biggestStep[0] <= 3.0D);
                expect(helper, "and the aim stayed where it was pointed: " + width + " degrees", width <= 30.0F);
                expect(helper, "and the aim did not shake: " + flips[0] + " changes of direction in " + samples[0]
                        + " ticks", flips[0] <= samples[0] / 4);
                helper.succeed();
            }
        });
    }

    /** The running measurement of one leg of the course: what the animal did, tick by tick. */
    private static final class Leg
    {
        private final double desiredYaw;
        private int ticks;
        private float lastYaw;
        private int moving;
        private int misaligned;
        private int backwards;
        private int travelledWhileTurning;
        private double worstTurn;

        private Leg(float startYaw, double desiredYaw)
        {
            this.lastYaw = startYaw;
            this.desiredYaw = desiredYaw;
        }

        private void sample(GameTestHelper helper, SoulfireDragonEntity dragon)
        {
            this.ticks++;

            float yaw = dragon.getYRot();
            double turn = Math.abs(Mth.wrapDegrees(yaw - this.lastYaw));
            double remaining = Math.abs(Mth.wrapDegrees(this.desiredYaw - yaw));
            Vec3 motion = dragon.getDeltaMovement();
            double speed = motion.horizontalDistance();

            this.worstTurn = Math.max(this.worstTurn, turn);

            expect(helper, "no turn over " + TURN_LIMIT + " degrees in one tick: " + turn, turn <= TURN_LIMIT);

            if (speed > MOVING)
            {
                this.moving++;

                double cosine = (motion.x * -Math.sin(Math.toRadians(yaw)) + motion.z * Math.cos(Math.toRadians(yaw)))
                        / speed;

                if (cosine < 0.94D)
                {
                    this.misaligned++;
                }

                if (cosine < -0.05D)
                {
                    this.backwards++;
                }
            }

            if (remaining > 60.0D && speed > 0.25D)
            {
                this.travelledWhileTurning++;
            }

            this.lastYaw = yaw;
        }

        private void report(GameTestHelper helper, String name)
        {
            expect(helper, name + ": the unit really moved (" + this.moving + " moving samples)", this.moving >= 15);
            expect(helper, name + ": every moving sample agreed with the facing (" + this.misaligned + " of " + this.moving
                    + " disagreed, worst turn " + this.worstTurn + " degrees)", this.misaligned == 0);
            expect(helper, name + ": it never travelled backwards (" + this.backwards + " samples)", this.backwards == 0);
            expect(helper, name + ": it never travelled while it still had to turn ("
                    + this.travelledWhileTurning + " samples)", this.travelledWhileTurning == 0);
        }
    }
}
