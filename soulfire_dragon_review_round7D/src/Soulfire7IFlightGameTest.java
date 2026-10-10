package spiderqueenhungrycraft.soulfire;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/** Round 7I airborne heading regression: checks ACTUAL entity motion, not a queued command. */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class Soulfire7IFlightGameTest
{
    private Soulfire7IFlightGameTest() {}

    @GameTest(template = "humanspawn_flat", batch = "soulfire7i_flight", timeoutTicks = 100)
    public static void flightmotionfollowsdragonsfacing(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), new BlockPos(2, 1, 2));
        dragon.setYRot(180.0F);
        dragon.yBodyRot = 180.0F;
        // Far-away MOVE forces the same Goal and flight controller the sceptre uses.
        dragon.setMoveTo(helper.absolutePos(new BlockPos(2, 1, 45)), 7001);
        final int[] samples = {0};
        final int[] ticks = {0};
        helper.onEachTick(() ->
        {
            ticks[0]++;
            Vec3 motion = dragon.getDeltaMovement();
            if (dragon.isFlying() && motion.horizontalDistance() > 0.08D)
            {
                double yaw = Math.toRadians(dragon.getYRot());
                Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
                double cosine = motion.x * forward.x + motion.z * forward.z;
                cosine /= motion.horizontalDistance();
                if (cosine < 0.94D)
                {
                    helper.fail("air motion disagrees with yaw: " + cosine + " yaw=" + dragon.getYRot());
                    return;
                }
                samples[0]++;
            }
            if (ticks[0] == 55)
            {
                if (samples[0] < 5)
                {
                    helper.fail("too few actual airborne movement samples: " + samples[0]);
                    return;
                }
                helper.succeed();
            }
        });
    }
}
