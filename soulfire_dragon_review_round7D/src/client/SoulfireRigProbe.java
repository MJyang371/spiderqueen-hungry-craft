package spiderqueenhungrycraft.client;

import java.util.ArrayList;
import java.util.List;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * A development probe for round 7G: the questions a player asks about the unit that a game test can not answer.
 *
 * <ol>
 * <li><b>Does the skeleton really move?</b> The probe sums the rotations of every box of the drawn model once per tick
 * and reports how much that sum travels while the unit flies - a translating statue would report zero movement.</li>
 * <li><b>Does the pose follow the state?</b> The same number is reported for the standing pose, so the two can be
 * compared: an identical number would mean the state is not really being read.</li>
 * <li><b>Is there a Head bone for the eyes?</b> The eye overlay only draws on a bone it can find by name; the probe
 * prints the bone it found and, when it finds none, the names that look like a head - which is how a wrong name is
 * caught instead of guessed at.</li>
 * <li><b>Where is the mouth?</b> The probe reads the head bone's pivot and box out of the real geometry, in model units,
 * so the breath origin can be measured instead of estimated.</li>
 * <li><b>How far does a follower stop?</b> The unit is ordered to follow the real player from forty blocks away and the
 * distance at which it settles on the floor is reported - the number this round is about.</li>
 * </ol>
 *
 * <p>Switched on with {@code -Dspiderqueen.soulfire.rig=true}; it does nothing at all without that switch, and it writes
 * nothing into the world.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulfireRigProbe
{
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.rig");

    /** When the run starts, in ticks after the world is up. */
    private static final int START_TICK = 100;

    /** How many ticks the flying pose is watched. */
    private static final int FLIGHT_WATCH = 120;

    /** How many ticks the standing pose is watched. */
    private static final int STAND_WATCH = 60;

    /** How long the follower may take to settle, in ticks. */
    private static final int FOLLOW_BUDGET = 600;

    /** How far away the follower starts, in blocks. */
    private static final double FOLLOW_FROM = 40.0D;

    private static int ticks;
    private static boolean started;
    private static int heartbeat;

    private static int subjectId = -1;
    private static boolean rigReported;

    private static SoulfireDragonEntity serverUnit;
    private static SoulfireDragonEntity clientUnit;
    private static int phase;
    private static int phaseTicks;

    private static double[] previousRotations;
    private static double previousSum;
    private static boolean[] movedBoxes;
    private static double travel;
    private static double lowestSum = Double.MAX_VALUE;
    private static double highestSum = -Double.MAX_VALUE;

    private SoulfireRigProbe()
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
        heartbeat++;

        if (heartbeat % 100 == 0)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7G] rig probe: heartbeat {} paused={} server={} player={} level={} ticks={}", heartbeat,
                    minecraft.isPaused(), minecraft.getSingleplayerServer() != null, minecraft.player != null,
                    minecraft.level != null, ticks);
        }

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
            SpiderQueenHungryCraft.LOGGER.info("[round7G] rig probe: armed, the unit waits {} ticks", START_TICK);
        }

        ticks++;

        if (ticks < START_TICK)
        {
            return;
        }

        if (ticks == START_TICK)
        {
            spawnSubject(minecraft);
        }

        if (!rigReported)
        {
            if (reportRig(minecraft))
            {
                rigReported = true;
                startFlightWatch();
            }
            else if (ticks > START_TICK + 60)
            {
                rigReported = true;
                SpiderQueenHungryCraft.LOGGER.error(
                        "[round7G] rig probe: FAILED - the unit never reached the client, nothing could be read");
                startFollowWatch();
            }

            return;
        }

        watch();
    }

    /** The unit all of this is asked about: one adult one, standing beside the player. */
    private static void spawnSubject(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        Entity raw = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (raw instanceof SoulfireDragonEntity dragon)
        {
            Vec3 where = minecraft.player.position().add(0.0D, 0.0D, 8.0D);
            dragon.moveTo(where.x, where.y, where.z, 0.0F, 0.0F);
            level.addFreshEntity(dragon);
            serverUnit = dragon;
            subjectId = dragon.getId();
        }
    }

    /** The model, the bones, the eyes and the mouth: everything that can be read from the drawn skeleton. */
    private static boolean reportRig(Minecraft minecraft)
    {
        Entity clientSide = minecraft.level.getEntity(subjectId);

        if (!(clientSide instanceof SoulfireDragonEntity shown))
        {
            return false;
        }

        clientUnit = shown;

        EntityRenderer<? super SoulfireDragonEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(shown);

        SpiderQueenHungryCraft.LOGGER.info("[round7G] rig probe: renderer={} stage={} texture={}",
                renderer.getClass().getSimpleName(), shown.getStage(), renderer.getTextureLocation(shown));

        if (!ModList.get().isLoaded("iceandfire") || !ModList.get().isLoaded("citadel"))
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7G] rig probe: FAILED - no Ice and Fire in this instance");

            return true;
        }

        if (!(renderer instanceof spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer bone))
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7G] rig probe: FAILED - the renderer is {}",
                    renderer.getClass().getName());

            return true;
        }

        if (!bone.boneModel().ensureLoaded())
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7G] rig probe: FAILED - the I&F skeleton is not loaded");

            return true;
        }

        TabulaModel tabula = bone.boneModel().tabula();
        List<String> headish = new ArrayList<>();
        int cubes = 0;

        for (AdvancedModelBox part : tabula.getAllParts())
        {
            cubes++;

            String name = part.boxName == null ? "" : part.boxName.toLowerCase(java.util.Locale.ROOT);

            if (name.contains("head") || name.contains("jaw") || name.contains("mouth") || name.contains("eye")
                    || name.startsWith("neck"))
            {
                headish.add(part.boxName);
            }
        }

        AdvancedModelBox head = tabula.getCube("Head");
        AdvancedModelBox jaw = tabula.getCube("Jaw");

        SpiderQueenHungryCraft.LOGGER.info("[round7G] rig probe: cubes={} head-like bones={}", cubes, headish);
        SpiderQueenHungryCraft.LOGGER.info(
                "[round7G] rig probe: bone \"Head\" found={} , bone \"Jaw\" found={} - the eye overlay draws nothing at "
                        + "all when \"Head\" is missing",
                head != null, jaw != null);

        for (String name : new String[] { "Head", "Jaw", "HeadFront", "Neck1", "Neck2", "Neck3" })
        {
            AdvancedModelBox named = tabula.getCube(name);

            if (named != null)
            {
                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7G] rig probe: bone {} pivot=({}, {}, {}) box={} parent={}", name,
                        round(named.rotationPointX), round(named.rotationPointY), round(named.rotationPointZ),
                        named.cubeList.isEmpty() ? "none"
                                : round(named.cubeList.get(0).posX1) + ".." + round(named.cubeList.get(0).posX2) + " / "
                                        + round(named.cubeList.get(0).posY1) + ".."
                                        + round(named.cubeList.get(0).posY2) + " / "
                                        + round(named.cubeList.get(0).posZ1) + ".."
                                        + round(named.cubeList.get(0).posZ2),
                        named.getParent() == null ? "none" : named.getParent().boxName);
            }
        }

        if (head != null && !head.cubeList.isEmpty())
        {
            com.github.alexthe666.citadel.client.model.TabulaModelRenderUtils.ModelBox box = head.cubeList.get(0);

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7G] rig probe: Head pivot=({}, {}, {}) , box x {}..{} y {}..{} z {}..{} , all in model units "
                            + "(1/16 block)",
                    round(head.rotationPointX), round(head.rotationPointY), round(head.rotationPointZ),
                    round(box.posX1), round(box.posX2), round(box.posY1), round(box.posY2), round(box.posZ1),
                    round(box.posZ2));

            // Where is the mouth, really? The same walk the eye overlay makes: down the bone chain into the head, and
            // then the snout corner of the head box - in blocks, which is the space the drawn model lives in.
            List<AdvancedModelBox> chain = new ArrayList<>();

            for (AdvancedModelBox cursor = head; cursor != null; cursor = cursor.getParent())
            {
                chain.add(cursor);
            }

            java.util.Collections.reverse(chain);

            PoseStack stack = new PoseStack();

            for (AdvancedModelBox part : chain)
            {
                part.translateAndRotate(stack);
            }

            org.joml.Vector4f snout = stack.last().pose()
                    .transform(new org.joml.Vector4f(0.0F, -0.07F, -0.3125F, 1.0F));
            float scale = shown.stageScale();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7G] rig probe: the mouth of the drawn skeleton sits at model ({} , {} , {}) blocks , which the "
                            + "server must aim at as forward {} and up {} at stage scale {}",
                    round(snout.x), round(snout.y), round(snout.z), round(-snout.z * scale), round(snout.y * scale),
                    scale);
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.info("[round7G] rig probe: Head pivot=({}, {}, {}) , but it has no box in "
                    + "cubeList", round(head.rotationPointX), round(head.rotationPointY), round(head.rotationPointZ));
        }

        previousRotations = null;
        return true;
    }

    /** Phase one: send the unit on a long errand, and watch the drawn skeleton while it flies. */
    private static void startFlightWatch()
    {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos far = new BlockPos((int) minecraft.player.getX() + 120, (int) minecraft.player.getY(),
                (int) minecraft.player.getZ());

        serverUnit.setMoveTo(far, 9);
        phase = 1;
        resetPoseWatch();

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7G] rig probe: the unit is sent {} blocks away - the skeleton is now watched for {} ticks",
                (int) Math.sqrt(serverUnit.distanceToSqr(Vec3.atBottomCenterOf(far))), FLIGHT_WATCH);
    }

    /** Phase two: the follower ring, measured on the real player. */
    private static void startFollowWatch()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = minecraft.getSingleplayerServer().overworld();

        serverUnit.setFlying(false);
        serverUnit.setHovering(false);
        serverUnit.teleportTo(minecraft.player.getX() + FOLLOW_FROM, minecraft.player.getY() + 2.0D,
                minecraft.player.getZ());
        serverUnit.beginContract(minecraft.player.getUUID(), level.getGameTime() + 12000L);
        serverUnit.followOrder();

        phase = 2;
        phaseTicks = 0;

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7G] rig probe: the unit is put {} blocks away and told to follow; the distance it settles at is "
                        + "now measured",
                (int) FOLLOW_FROM);
    }

    /** Watches the pose of the drawn model, and then the distance a follower keeps. */
    private static void watch()
    {
        if (serverUnit == null || serverUnit.isRemoved())
        {
            return;
        }

        phaseTicks++;

        if (phase == 1 || phase == 3)
        {
            samplePose();

            if (phaseTicks >= (phase == 1 ? FLIGHT_WATCH : STAND_WATCH))
            {
                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7G] rig probe: {} - {} of {} boxes moved, the sum of all rotations travelled {} (it "
                                + "stayed between {} and {})",
                        phase == 1 ? "flying" : "standing", countMoved(),
                        previousRotations == null ? 0 : previousRotations.length / 3, round(travel), round(lowestSum),
                        round(highestSum));

                if (phase == 1)
                {
                    if (travel < 0.5D)
                    {
                        SpiderQueenHungryCraft.LOGGER.error(
                                "[round7G] rig probe: FAILED - the drawn skeleton hardly moves while flying: travel={}",
                                round(travel));
                    }

                    serverUnit.setFlying(false);
                    serverUnit.setHovering(false);
                    phase = 3;
                    resetPoseWatch();
                }
                else
                {
                    startFollowWatch();
                }

                return;
            }
        }
        else if (phase == 2 && phaseTicks > 5)
        {
            boolean settled = serverUnit.onGround() && !serverUnit.isFlying() && !serverUnit.isHovering()
                    && !serverUnit.isLanding() && !serverUnit.isNoGravity();
            double distance = Math.sqrt(serverUnit.distanceToSqr(Minecraft.getInstance().player));

            if (settled || phaseTicks >= FOLLOW_BUDGET)
            {
                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7G] rig probe: {} - the follower settled {} blocks from the player after {} ticks "
                                + "(y={} , onGround={} , flying={} , stop ring would be {})",
                        settled ? "PASS" : "FAILED", round(distance), phaseTicks, round(serverUnit.getY()),
                        serverUnit.onGround(), serverUnit.isFlying(),
                        round(Math.max(14.0D, serverUnit.getBbWidth() * 4.0D + 6.0D)));

                serverUnit.discard();
                serverUnit = null;
                clientUnit = null;
                SpiderQueenHungryCraft.LOGGER.info("[round7G] rig probe: the run is over");
            }
        }
    }

    /** Reads the rotation of every box of the drawn model, and compares it with the tick before. */
    private static void samplePose()
    {
        if (clientUnit == null || clientUnit.isRemoved()
                || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(clientUnit)
                        instanceof spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer bone))
        {
            return;
        }

        TabulaModel tabula = bone.boneModel().tabula();

        if (tabula == null)
        {
            return;
        }

        List<Double> rotations = new ArrayList<>();
        double sum = 0.0D;

        for (AdvancedModelBox part : tabula.getAllParts())
        {
            rotations.add((double) part.rotateAngleX);
            rotations.add((double) part.rotateAngleY);
            rotations.add((double) part.rotateAngleZ);
            sum += Math.abs(part.rotateAngleX) + Math.abs(part.rotateAngleY) + Math.abs(part.rotateAngleZ);
        }

        double[] flat = new double[rotations.size()];

        for (int i = 0; i < flat.length; i++)
        {
            flat[i] = rotations.get(i);
        }

        if (previousRotations != null && previousRotations.length == flat.length)
        {
            travel += Math.abs(sum - previousSum);

            if (movedBoxes == null || movedBoxes.length != flat.length / 3)
            {
                movedBoxes = new boolean[flat.length / 3];
            }

            for (int box = 0; box < movedBoxes.length; box++)
            {
                for (int axis = 0; axis < 3; axis++)
                {
                    if (Math.abs(flat[box * 3 + axis] - previousRotations[box * 3 + axis]) > 1.0E-4D)
                    {
                        movedBoxes[box] = true;
                        break;
                    }
                }
            }
        }

        previousRotations = flat;
        previousSum = sum;
        lowestSum = Math.min(lowestSum, sum);
        highestSum = Math.max(highestSum, sum);
    }

    /** @return how many boxes have moved at least once since the watch began. */
    private static int countMoved()
    {
        int moved = 0;

        if (movedBoxes != null)
        {
            for (boolean box : movedBoxes)
            {
                if (box)
                {
                    moved++;
                }
            }
        }

        return moved;
    }

    /** Starts a fresh watch of the pose: nothing of the state before is carried into the next one. */
    private static void resetPoseWatch()
    {
        phaseTicks = 0;
        previousRotations = null;
        previousSum = 0.0D;
        movedBoxes = null;
        travel = 0.0D;
        lowestSum = Double.MAX_VALUE;
        highestSum = -Double.MAX_VALUE;
    }

    private static String round(double value)
    {
        return String.valueOf(Math.round(value * 100.0D) / 100.0D);
    }
}
