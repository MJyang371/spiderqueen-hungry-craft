package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * A development probe that measures the survival and flight fixes of round 7E <b>on a real server</b>.
 *
 * <p>The game tests of this round run on a bare test world; this probe runs on the <b>integrated server of the real
 * game</b>, with the real world, the real terrain and the real physics, and it writes what it measured into the log. It
 * is the evidence behind claims like "a dragon does not take falling damage" and "a long MOVE order does not climb",
 * which are exactly the kind of thing a test can quietly get wrong.
 *
 * <p>It is switched on with {@code -Dspiderqueen.soulfire.hazards=true} and does <b>nothing at all</b> without it. It
 * places its units through the ordinary entity type of this mod and reports:
 *
 * <ol>
 * <li>a unit dropped from thirty blocks: its health before and after it lands,</li>
 * <li>a unit hurt by fire and lava, and then by an ordinary blow (the control),</li>
 * <li>a unit sent on a long MOVE order: its height over a hundred and forty ticks,</li>
 * <li>a unit whose errand is cancelled by a new order: whether the old flight died with it.</li>
 * </ol>
 *
 * <p>It writes nothing to the world: the lava of step two is a damage source, not a block placed in the save.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulfireHazardProbe
{
    /** The switch of this probe. Off unless the launcher asks for it. */
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.hazards");

    /** When the first unit is dropped, in ticks after the world is up. */
    private static final int START_TICK = 80;

    /** How high the first unit is dropped from, in blocks. */
    private static final double DROP_HEIGHT = 30.0D;

    /** How long the errand of the third unit is watched, in ticks. */
    private static final int ERRAND_WATCH_TICKS = 140;

    private static int ticks;
    private static boolean started;

    private static SoulfireDragonEntity falling;
    private static float fallHealthBefore;
    private static boolean fallReported;

    private static SoulfireDragonEntity flying;
    private static double flightStartY;
    private static double flightHighestY;
    private static int flightTicks;
    private static boolean flightReported;

    private SoulfireHazardProbe()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (!ENABLED || event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isPaused())
        {
            // The window of a probe run is not focused, so the game pauses itself the moment the world is up.
            if (minecraft.screen != null)
            {
                minecraft.setScreen(null);
            }

            return;
        }

        MinecraftServer server = minecraft.getSingleplayerServer();

        if (server == null || minecraft.player == null)
        {
            return;
        }

        ServerLevel level = server.getLevel(minecraft.player.level().dimension());

        if (level == null)
        {
            return;
        }

        ticks++;

        if (!started && ticks >= START_TICK)
        {
            start(level);
        }

        if (started)
        {
            watch();
        }
    }

    /** Places the four units of the probe and measures everything that can be measured at once. */
    private static void start(ServerLevel level)
    {
        Minecraft minecraft = Minecraft.getInstance();
        started = true;

        Vec3 eyes = minecraft.player.getEyePosition();

        // 1. The fall.
        falling = spawn(level, eyes.add(0.0D, DROP_HEIGHT, 8.0D));
        fallHealthBefore = falling.getHealth();

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7E] hazard probe: a unit was dropped from {} blocks with {} of {} health", (int) DROP_HEIGHT,
                fallHealthBefore, falling.getMaxHealth());

        // 2. Fire, lava and the control.
        SoulfireDragonEntity burning = spawn(level, eyes.add(4.0D, 0.0D, 8.0D));
        float before = burning.getHealth();

        boolean fireImmune = HungryCraftEntities.SOULFIRE_DRAGON.get().fireImmune();

        burning.hurt(level.damageSources().inFire(), 6.0F);
        float afterFire = burning.getHealth();
        burning.hurt(level.damageSources().lava(), 6.0F);
        float afterLava = burning.getHealth();
        burning.hurt(level.damageSources().generic(), 6.0F);
        float afterBlow = burning.getHealth();

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7E] hazard probe: fireImmune={} health {} -> fire {} -> lava {} -> ordinary blow {}", fireImmune,
                before, afterFire, afterLava, afterBlow);

        burning.discard();

        // 3. The long errand.
        flying = spawn(level, eyes.add(-6.0D, 0.0D, 8.0D));
        flightStartY = flying.getY();
        flightHighestY = flightStartY;
        flying.setMoveTo(new BlockPos((int) eyes.x + 90, (int) eyes.y, (int) eyes.z), 1);

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7E] hazard probe: a unit was sent on a MOVE order 90 blocks away from y={}", (int) flightStartY);

        // 4. The arbitration: an errand in flight, then a new order on top of it.
        SoulfireDragonEntity ordered = spawn(level, eyes.add(8.0D, 0.0D, 8.0D));
        ordered.setMoveTo(new BlockPos((int) eyes.x + 60, (int) eyes.y, (int) eyes.z + 60), 2);

        int version = ordered.getCommandVersion();

        ordered.flyTo(ordered.position().add(0.0D, 8.0D, 30.0D), version);

        boolean errandFlying = ordered.isFlightActive();

        ordered.followOrder();

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7E] hazard probe: the errand was steering={} ; after a new order: version {}->{} , errand gone={} , "
                        + "flight still steering={} , order={}",
                errandFlying, version, ordered.getCommandVersion(), ordered.getMoveTarget() == null,
                ordered.isFlightActive(), ordered.getOrderMode());

        ordered.discard();
    }

    /** Watches the falling unit and the errand, and reports each of them once. */
    private static void watch()
    {
        if (falling != null && !fallReported && (falling.onGround() || falling.isRemoved()))
        {
            fallReported = true;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7E] hazard probe: the dropped unit landed at y={} with {} of {} health (it started with {}) - "
                            + "noGravity={}",
                    (int) falling.getY(), falling.getHealth(), falling.getMaxHealth(), fallHealthBefore,
                    falling.isNoGravity());

            falling.discard();
        }

        if (flying != null && !flightReported)
        {
            flightTicks++;
            flightHighestY = Math.max(flightHighestY, flying.getY());

            if (flightTicks >= ERRAND_WATCH_TICKS)
            {
                flightReported = true;

                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7E] hazard probe: after {} ticks of the errand the unit is at y={} (started {}, highest {}) - "
                                + "climbed {} blocks",
                        flightTicks, (int) flying.getY(), (int) flightStartY, (int) flightHighestY,
                        (int) (flightHighestY - flightStartY));

                flying.discard();
            }
        }
    }

    /** One unit of this mod, on the server, at that spot. */
    private static SoulfireDragonEntity spawn(ServerLevel level, Vec3 where)
    {
        Entity raw = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (!(raw instanceof SoulfireDragonEntity dragon))
        {
            throw new IllegalStateException("the soul fire dragon could not be built");
        }

        dragon.moveTo(where.x, where.y, where.z, 0.0F, 0.0F);
        level.addFreshEntity(dragon);

        return dragon;
    }
}
