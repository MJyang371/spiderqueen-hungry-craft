package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * The round 7I flight probe: it asks the questions of this round on a live client, because only a real flight can answer
 * them.
 *
 * <ol>
 * <li><b>A ninety degree order:</b> the unit is put in the air facing one way and ordered to fly across. The probe reads
 * the REAL heading and the REAL motion every tick: how fast the turn is, whether the animal travels while it is still
 * aimed away, and whether the motion agrees with the facing once the bearing is taken.</li>
 * <li><b>A reversal:</b> an order to the opposite side. A large animal has to turn first, and it must never fly backwards
 * while doing it.</li>
 * <li><b>The landing:</b> the descent is measured on the synced numbers the animation reads: {@code LANDING},
 * {@code LANDING_BLEND}, and the moment the animal really stands.</li>
 * <li><b>The breath:</b> the head is watched while soul fire runs, because a breath that shakes its own neck is not aimed
 * at anything.</li>
 * </ol>
 *
 * <p>Switched on with {@code -Dspiderqueen.soulfire.flight=true}; it does nothing at all without that switch.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Soulfire7IFlightProbe
{
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.flight");

    /** When the run starts, in ticks after the world is up. */
    private static final int START_TICK = 120;

    /** A horizontal speed above this counts as real travelling (the same rule the round 7I game test uses). */
    private static final double MOVING = 0.08D;

    /** The turn limit of the unit, in degrees per tick, plus a hair of tolerance for the reading. */
    private static final double TURN_LIMIT = 8.05D;

    /** How many ticks one leg of the course is measured over. */
    private static final int LEG_TICKS = 140;

    private static int ticks;
    private static boolean started;
    private static boolean cleaned;

    private static SoulfireDragonEntity subject;
    private static ServerPlayer queen;
    private static int phase;
    private static int phaseTicks;
    private static boolean reported;

    /** The spot the subject was put in the air at: every leg is measured from here. */
    private static BlockPos origin;

    // The counters of one leg.
    private static int samples;
    private static int badTurnRate;
    private static int misaligned;
    private static int backwards;
    private static int travelledWhileTurning;
    private static double maxTurnRate;
    private static float lastYaw;
    private static float startYaw;
    private static float desiredYaw;
    private static double startProgress;
    private static int leg;

    // The findings of the landing and of the breath.
    private static boolean sawLanding;
    private static float maxBlend;
    private static boolean blendCameHome;

    /** The spot on the ground the landing leg is ordered to. */
    private static BlockPos landingSpot;

    private static int breathSamples;
    private static double breathMaxStep;
    private static float breathMinYaw;
    private static float breathMaxYaw;
    private static int breathFlips;
    private static float breathLastYaw;
    private static double breathLastStep;

    private Soulfire7IFlightProbe()
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
            if (minecraft.screen != null)
            {
                minecraft.setScreen(null);
            }

            return;
        }

        if (minecraft.getSingleplayerServer() == null || minecraft.player == null || minecraft.level == null)
        {
            return;
        }

        if (!started)
        {
            started = true;
            SpiderQueenHungryCraft.LOGGER.info("[round7i] flight probe: armed, the course starts in {} ticks",
                    START_TICK);
        }

        // A harness must not be paused by a missing window focus: the world would stop ticking underneath it.
        minecraft.options.pauseOnLostFocus = false;

        // A test world collects the units of earlier runs, and every one of them is a real animal with six hit regions:
        // a hundred of them make the client crawl and turn every measurement into a measurement of the lag. They are
        // cleared out here, before anything is measured.
        if (!cleaned)
        {
            cleaned = true;
            ServerPlayer player = minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0);

            minecraft.getSingleplayerServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                    "kill @e[type=spiderqueen_hungrycraft:soulfire_dragon]");
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: the units of earlier runs were cleared out of this test world");
        }

        ticks++;

        if (ticks < START_TICK)
        {
            return;
        }

        if (ticks == START_TICK)
        {
            begin(minecraft);
        }

        step(minecraft);
    }

    /** Puts the subject in the open air above the queen, facing a known way, with a contract of its own. */
    private static void begin(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        queen = minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0);

        SoulfireDragonEntity dragon = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (dragon == null)
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7i] flight probe: FAILED - the subject could not be built");

            return;
        }

        Vec3 where = queen.position();
        origin = BlockPos.containing(where.x, where.y + 40.0D, where.z);
        dragon.moveTo(origin.getX() + 0.5D, origin.getY(), origin.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(dragon);
        dragon.beginContract(queen.getUUID(), level.getGameTime() + 40000L);
        dragon.setOrderMode(SpiderOrderMode.HOLD);
        dragon.setAnchor(origin);
        dragon.setStage(5);

        // A probe must not die of its own course: a bird's eye lesson in a fall is not what this round asks about. The
        // descent posture is measured on the landing leg, where it belongs.
        dragon.setInvulnerable(true);
        subject = dragon;
        phase = 1;
        phaseTicks = 0;

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7i] flight probe: the subject stands above {} at {} facing yaw 0; the course is 90 degrees, then "
                        + "a full reversal, then a landing, then a breath",
                queen.blockPosition(), origin);
    }

    /** The course: take off, then one leg after the other. */
    private static void step(Minecraft minecraft)
    {
        if (subject == null || subject.isRemoved())
        {
            return;
        }

        phaseTicks++;

        switch (phase)
        {
            case 1 -> takeOff(minecraft);
            case 2 -> legPhase(minecraft, "90", 4001, 24.0D, 0.0D);
            case 3 -> legPhase(minecraft, "180", 4002, -24.0D, 0.0D);
            case 4 -> landingPhase(minecraft);
            case 5 -> breathPhase(minecraft);
            default -> finishOnce();
        }
    }

    /** Leaves the floor: the unit is told to fly, and the probe waits until it is really in the air. */
    private static void takeOff(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            subject.setFlying(true);
            subject.setHovering(true);
            subject.setDeltaMovement(Vec3.ZERO);
        }

        watch(minecraft);

        if (phaseTicks > 10 && subject.isFlying() && !subject.onGround())
        {
            subject.setYRot(0.0F);
            subject.yBodyRot = 0.0F;
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: airborne at {} , speed {} , taking the courses",
                    subject.blockPosition(), Math.round(subject.getDeltaMovement().horizontalDistance() * 1000.0D)
                            / 1000.0D);
            phase++;
            phaseTicks = 0;
        }
        else if (phaseTicks > 200)
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "[round7i] flight probe: FAILED - the subject never left the ground (flying={} hovering={} onGround={})",
                    subject.isFlying(), subject.isHovering(), subject.onGround());
            phase = 99;
        }
    }

    /** One leg of the course: a real MOVE order across the sky, and every tick of it is measured. */
    private static void legPhase(Minecraft minecraft, String name, int commandId, double dx, double dz)
    {
        if (phaseTicks == 1)
        {
            leg++;
            // The errand is placed at the height the animal is really flying at: a target below it is a descent in this
            // design, and a descent is a different lesson (the landing leg teaches that one).
            BlockPos target = BlockPos.containing(subject.position()).offset((int) dx, 0, (int) dz);
            subject.setMoveTo(target, commandId);
            startYaw = subject.getYRot();
            lastYaw = startYaw;
            desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            samples = 0;
            badTurnRate = 0;
            misaligned = 0;
            backwards = 0;
            travelledWhileTurning = 0;
            maxTurnRate = 0.0D;
            startProgress = 0.0D;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: leg {} ({}) - ordered across to {} from yaw {} , so the bearing it asks for is "
                            + "{} degrees",
                    leg, name, target, Math.round(startYaw), Math.round(desiredYaw));
        }

        watch(minecraft);

        float yaw = subject.getYRot();
        double turnRate = Math.abs(Mth.wrapDegrees(yaw - lastYaw));
        double remaining = Math.abs(Mth.wrapDegrees(desiredYaw - yaw));
        Vec3 motion = subject.getDeltaMovement();
        double speed = motion.horizontalDistance();

        maxTurnRate = Math.max(maxTurnRate, turnRate);

        if (turnRate > TURN_LIMIT)
        {
            badTurnRate++;
        }

        if (speed > MOVING)
        {
            samples++;

            double forwardX = -Math.sin(Math.toRadians(yaw));
            double forwardZ = Math.cos(Math.toRadians(yaw));
            double cosine = (motion.x * forwardX + motion.z * forwardZ) / speed;

            if (cosine < 0.94D)
            {
                misaligned++;
            }

            if (cosine < -0.05D)
            {
                backwards++;
            }
        }

        // A large change of bearing has to be turned first and not flown through: while more than sixty degrees are left
        // the animal may hover, but it must not travel.
        if (remaining > 60.0D && speed > 0.25D)
        {
            travelledWhileTurning++;
        }

        if (phaseTicks % 40 == 0)
        {
            float synced = subject.syncedBodyYaw();
            float clientBody = clientBodyYaw(minecraft);

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: leg {} tick {} - yaw {} , remaining {} , speed {} , bank {} , y {} hp {} flying {} "
                            + "landing {} , body: server {} synced {} client {} (client off by {})",
                    leg, phaseTicks, Math.round(yaw), Math.round(remaining),
                    Math.round(speed * 1000.0D) / 1000.0D, Math.round(subject.syncedAirBank()), Math.round(subject.getY()),
                    Math.round(subject.getHealth()), subject.isFlying(), subject.isLanding(),
                    Math.round(subject.yBodyRot), Math.round(synced), Math.round(clientBody),
                    Math.round(Math.abs(Mth.wrapDegrees(clientBody - synced))));
            shoot(minecraft, "7i_" + name + "_t" + phaseTicks);
        }

        lastYaw = yaw;

        if (phaseTicks == LEG_TICKS)
        {
            boolean ok = badTurnRate == 0 && misaligned == 0 && backwards == 0 && travelledWhileTurning == 0
                    && samples >= 20;
            float synced = subject.syncedBodyYaw();
            float clientBody = clientBodyYaw(minecraft);

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: {} - leg {} ({}) turned {} degrees (max {}/tick, {} samples over the limit), "
                            + "{} moving samples, {} misaligned, {} backwards, {} travelled while still turning; at the end "
                            + "the client body is {} off the server bearing",
                    ok ? "PASS" : "FAILED", leg, name, Math.round(Math.abs(Mth.wrapDegrees(yaw - startYaw))),
                    Math.round(maxTurnRate * 100.0D) / 100.0D, badTurnRate, samples, misaligned, backwards,
                    travelledWhileTurning, Math.round(Math.abs(Mth.wrapDegrees(clientBody - synced))));
            phase++;
            phaseTicks = 0;
        }
    }

    /** The descent: a real order to the ground, watched through the very numbers the client animation reads. */
    private static void landingPhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            ServerLevel level = minecraft.getSingleplayerServer().overworld();
            landingSpot = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, origin.offset(0, 0, 30));
            subject.setMoveTo(landingSpot, 4003);
            sawLanding = false;
            maxBlend = 0.0F;
            blendCameHome = false;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: ordered down to the ground at {} , {} blocks below", landingSpot,
                    Math.round(subject.getY() - landingSpot.getY()));
        }

        watch(minecraft);

        float blend = subject.landingAnimationProgress();
        maxBlend = Math.max(maxBlend, blend);

        if (subject.isLanding())
        {
            sawLanding = true;
        }

        boolean standing = subject.onGround() && !subject.isFlying() && !subject.isHovering() && !subject.isLanding();

        if (sawLanding && standing && blend <= 0.001F)
        {
            blendCameHome = true;
        }

        if (phaseTicks % 20 == 0)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: descending - landing {} blend {} y {} onGround {} flying {} hovering {} speed {}",
                    subject.isLanding(), Math.round(blend * 100.0F) / 100.0F, Math.round(subject.getY()),
                    subject.onGround(), subject.isFlying(), subject.isHovering(),
                    Math.round(subject.getDeltaMovement().horizontalDistance() * 100.0D) / 100.0D);
        }

        if (blend > 0.5F && phaseTicks % 20 == 0)
        {
            shoot(minecraft, "7i_landing_blend_" + phaseTicks);
        }

        if (standing && phaseTicks > 20)
        {
            shoot(minecraft, "7i_landing_done");

            boolean ok = sawLanding && maxBlend > 0.25F && blendCameHome;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: {} - the descent ended standing on the ground: landing was seen {} , the "
                            + "highest blend was {} , and the blend came home {} (y {} , at {})",
                    ok ? "PASS" : "FAILED", sawLanding, Math.round(maxBlend * 100.0F) / 100.0F, blendCameHome,
                    Math.round(subject.getY()), subject.blockPosition());
            phase++;
            phaseTicks = 0;
        }
        else if (phaseTicks > 800)
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "[round7i] flight probe: FAILED - the unit never stood down (landing={} blend={} y={} onGround={} "
                            + "flying={} hovering={})",
                    subject.isLanding(), blend, Math.round(subject.getY()), subject.onGround(), subject.isFlying(),
                    subject.isHovering());
            phase = 99;
        }
    }

    /**
     * The breath: the unit is told to burn a spot ahead, and the head it aims with is watched tick by tick. A steady aim
     * is a small step between ticks; a shaking neck is a large step that keeps changing direction.
     */
    private static void breathPhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            BlockPos ahead = subject.blockPosition().offset(10, -1, 10);

            subject.setFlying(true);
            subject.breatheAt(ahead);
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: told to breathe at {} and left to settle before the head is measured", ahead);
        }

        watch(minecraft);

        if (phaseTicks == 40)
        {
            breathSamples = 0;
            breathMaxStep = 0.0D;
            breathFlips = 0;
            breathMinYaw = Float.MAX_VALUE;
            breathMaxYaw = -Float.MAX_VALUE;
            breathLastYaw = subject.syncedHeadYaw();
            breathLastStep = 0.0D;
        }

        if (phaseTicks > 40 && subject.isBreathing())
        {
            float headYaw = subject.syncedHeadYaw();
            double signed = Mth.wrapDegrees(headYaw - breathLastYaw);
            double step = Math.abs(signed);

            breathSamples++;
            breathMaxStep = Math.max(breathMaxStep, step);
            breathMinYaw = Math.min(breathMinYaw, headYaw);
            breathMaxYaw = Math.max(breathMaxYaw, headYaw);

            if (step > 0.10D && Math.abs(breathLastStep) > 0.10D && Math.signum(signed) != Math.signum(breathLastStep))
            {
                breathFlips++;
            }

            breathLastStep = signed;
            breathLastYaw = headYaw;
        }

        if (phaseTicks % 30 == 0)
        {
            shoot(minecraft, "7i_breath_" + phaseTicks);
        }

        if (phaseTicks == 140)
        {
            float width = breathSamples == 0 ? 0.0F : breathMaxYaw - breathMinYaw;
            boolean ok = breathSamples >= 30 && breathMaxStep <= 3.0D && width <= 30.0F
                    && breathFlips <= breathSamples / 4;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7i] flight probe: {} - the head while breathing: {} samples, the biggest step in one tick was {} "
                            + "degrees, the aim moved over {} degrees, and it changed direction {} times",
                    ok ? "PASS" : "FAILED", breathSamples, Math.round(breathMaxStep * 100.0D) / 100.0D,
                    Math.round(width), breathFlips);
            subject.stopBreathOrder();
            phase++;
            phaseTicks = 0;
        }
    }

    /** @return the body yaw of the client's own copy of the subject, which is the one the picture is drawn with. */
    private static float clientBodyYaw(Minecraft minecraft)
    {
        net.minecraft.world.entity.Entity copy = minecraft.level.getEntity(subject.getId());

        return copy instanceof SoulfireDragonEntity dragon ? dragon.yBodyRot : Float.NaN;
    }

    /** Stands the queen (and with her the camera) beside the subject, so every frame really shows the animal. */
    private static void watch(Minecraft minecraft)
    {
        // Moving the camera is a teleport for a player, and a teleport every tick makes the client load chunks until it
        // stops responding: the camera follows in steps of five ticks instead, which is still fast enough to frame an
        // animal that flies half a block per tick.
        if (phaseTicks % 5 != 0)
        {
            return;
        }

        Vec3 look = subject.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        Vec3 camera = subject.position().add(side.scale(14.0D)).add(0.0D, 6.0D, 0.0D);
        Vec3 aim = subject.position().add(0.0D, subject.getBbHeight() * 0.5D, 0.0D);
        double dx = aim.x - camera.x;
        double dy = aim.y - camera.y;
        double dz = aim.z - camera.z;
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI));

        queen.setNoGravity(true);
        queen.setInvulnerable(true);
        queen.getServer().getCommands().performPrefixedCommand(
                queen.createCommandSourceStack().withSuppressedOutput().withPermission(2),
                String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", camera.x, camera.y, camera.z, yaw,
                        pitch));
        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
    }

    /** The ordinary screenshot of the game, into the shots folder of the instance. */
    private static void shoot(Minecraft minecraft, String name)
    {
        Screenshot.grab(minecraft.gameDirectory, "soulfire_" + name, minecraft.getMainRenderTarget(),
                component -> SpiderQueenHungryCraft.LOGGER.info("[round7i] flight probe: frame {} written ({})", name,
                        component.getString()));
    }

    /** Prints the closing line of the run once. */
    private static void finishOnce()
    {
        if (reported)
        {
            return;
        }

        reported = true;

        if (queen != null)
        {
            queen.setNoGravity(false);
            queen.setInvulnerable(false);
        }

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7i] flight probe: the course is over - the frames are in the screenshots folder of the instance");
    }
}
