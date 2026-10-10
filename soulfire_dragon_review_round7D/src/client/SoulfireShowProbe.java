package spiderqueenhungrycraft.client;

import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireDragonPart;
import spiderqueenhungrycraft.soulfire.SoulfireRising;

/**
 * The probe of round 7H: it takes the pictures, and it walks the death chain, because those are the only two honest ways
 * to answer the questions of this round.
 *
 * <ol>
 * <li><b>The eyes, the neck and the jaws:</b> it stands the camera at the muzzle of a unit - in front of it, at its side,
 * at an angle, from below and at night - and writes those frames into {@code build\probe_shots\}. A floating eye patch, an
 * eye patch inside the skull, or a neck that does not turn can all be seen there.</li>
 * <li><b>The death of a real unit:</b> it raises a soul fire dragon out of a genuine Ice and Fire bone dragon, kills it
 * with a real damage source, and then asks the world whether the remains <b>entity itself</b> is standing there again -
 * not whether a ledger says it should be. It does that three times, once for each kind of bone dragon.</li>
 * </ol>
 *
 * <p>Switched on with {@code -Dspiderqueen.soulfire.show=true}; it does nothing at all without that switch.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulfireShowProbe
{
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.show");

    /** When the run starts, in ticks after the world is up. */
    private static final int START_TICK = 120;

    /** How far the camera stands from the muzzle of the subject, in blocks. */
    private static final double CAMERA_DISTANCE = 3.2D;

    /** How many death routes are walked: a blow, an arrow and an explosion. */
    private static final int DEATH_ROUTES = 3;

    private static int ticks;
    private static boolean started;

    private static SoulfireDragonEntity subject;
    private static ServerPlayer queen;
    private static int phase;
    private static int phaseTicks;
    private static boolean reported;

    private static LivingEntity dummyRef;
    private static float dummyHealthBefore;
    private static double idleSumA;

    /** The tick of the idle phase at which the unit was first seen standing still, or 0 while it is still walking. */
    private static int idleSince;

    /** The three questions of the rig phase of round 7H, and the health of the unit before a real blow at its wing. */
    private static boolean partsServerOk;
    private static boolean partsClientOk;
    private static boolean partsPickOk;
    private static boolean partsBlowSent;
    private static float partsHealthBefore;
    private static float partsServerSmall;
    private static float partsServerFull;
    private static float partsClientSmall;
    private static float partsClientFull;

    private static SoulfireDragonEntity raised;
    private static UUID remainsId;
    private static int deathAttempt;

    private SoulfireShowProbe()
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
            SpiderQueenHungryCraft.LOGGER.info("[round7H] show probe: armed, the subject waits {} ticks", START_TICK);
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

    /** Builds the subject: one adult unit of this mod, standing where the queen stands. */
    private static void begin(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        queen = minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0);

        Entity raw = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (!(raw instanceof SoulfireDragonEntity dragon))
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7H] show probe: FAILED - the subject could not be built");

            return;
        }

        Vec3 where = queen.position();
        dragon.moveTo(where.x, where.y, where.z, 0.0F, 0.0F);
        level.addFreshEntity(dragon);
        dragon.setStage(5);

        // The unit must stay where it is put: a follower keeps its eighteen block ring around the queen (round 7G), so a
        // camera at its muzzle would only ever photograph the back of a walking animal.
        dragon.setOrderMode(spiderqueenhungrycraft.brood.SpiderOrderMode.HOLD);
        dragon.setAnchor(dragon.blockPosition());
        subject = dragon;
        phase = 1;
        phaseTicks = 0;

        SpiderQueenHungryCraft.LOGGER.info("[round7H] show probe: the subject stands at {} , stage {} , scale {}",
                dragon.blockPosition(), dragon.getStage(), dragon.stageScale());
    }

    /** The timeline of the run: place the camera, take the frame, move on. */
    private static void step(Minecraft minecraft)
    {
        if (subject == null || subject.isRemoved())
        {
            return;
        }

        phaseTicks++;

        switch (phase)
        {
            case 1 -> shotPhase(minecraft, "1_front", 0.0D, 0.0D, 60);
            case 2 -> shotPhase(minecraft, "2_side", 1.0D, 0.0D, 60);
            case 3 -> shotPhase(minecraft, "3_angle", 0.6D, 0.15D, 60);
            case 4 -> shotPhase(minecraft, "4_below", 0.25D, -0.7D, 60);
            case 5 -> nightPhase(minecraft);
            case 6 -> lookPhase(minecraft);
            case 7 -> bitePhase(minecraft);
            case 8 -> idlePhase(minecraft);
            case 9 -> partsPhase(minecraft);
            case 10 -> deathPhase(minecraft);
            default -> finishOnce();
        }
    }

    /**
     * A close up of the head from one direction.
     *
     * @param around 0 in front of the muzzle, 1 at its side, values in between at an angle
     * @param lift how far the camera is raised above the muzzle, in blocks
     */
    private static void shotPhase(Minecraft minecraft, String name, double around, double lift, int wait)
    {
        // The camera is aimed every tick: the unit keeps walking back to its post, so a camera aimed once would photograph
        // the landscape where the head used to be.
        frameTheHead(minecraft, around, lift);

        if (phaseTicks == wait)
        {
            shoot(minecraft, name);
            phase++;
            phaseTicks = 0;
        }
    }

    /** The same close up at night: a glow that only shows in the dark is not a glow. */
    private static void nightPhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            minecraft.getSingleplayerServer().overworld().setDayTime(18000L);
        }

        frameTheHead(minecraft, 0.4D, 0.1D);

        if (phaseTicks == 80)
        {
            shoot(minecraft, "5_night");
            minecraft.getSingleplayerServer().overworld().setDayTime(6000L);
            phase++;
            phaseTicks = 0;
        }
    }

    /** Stands the camera close to the muzzle of the subject and aims it at the head. */
    private static void frameTheHead(Minecraft minecraft, double around, double lift)
    {
        Vec3 muzzle = subject.getMuzzlePosition();
        Vec3 forward = subject.getLookAngle();
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);

        // around = 0 stands the camera in front of the muzzle, around = 1 at its side, and anything between is the angle
        // between those two - the three classic views of a model.
        Vec3 direction = forward.scale(-(1.0D - around)).add(side.scale(around)).add(0.0D, lift, 0.0D);

        if (direction.length() < 1.0E-4D)
        {
            direction = forward.scale(-1.0D);
        }

        direction = direction.normalize();
        placeCamera(minecraft, muzzle.add(direction.scale(CAMERA_DISTANCE)), muzzle);
    }

    /** Puts the queen (and with her, the camera) at that spot, looking at that point. */
    private static void placeCamera(Minecraft minecraft, Vec3 camera, Vec3 aim)
    {
        double dx = aim.x - camera.x;
        double dy = aim.y - camera.y;
        double dz = aim.z - camera.z;
        float yaw = (float) (Mth.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * 180.0D / Math.PI));

        queen.setNoGravity(true);
        queen.setInvulnerable(true);

        // The teleport goes through the ordinary command of the game: this is the one path that always moves the player
        // and the camera that follows her, where setting the position by hand only moved one of the two.
        String command = String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.1f %.1f", camera.x, camera.y,
                camera.z, yaw, pitch);
        queen.getServer().getCommands().performPrefixedCommand(
                queen.createCommandSourceStack().withSuppressedOutput().withPermission(2), command);

        minecraft.player.setYRot(yaw);
        minecraft.player.setXRot(pitch);
    }

    /** The ordinary screenshot of the game, into the shots folder of the project. */
    private static void shoot(Minecraft minecraft, String name)
    {
        Screenshot.grab(minecraft.gameDirectory, "soulfire_show_" + name, minecraft.getMainRenderTarget(),
                component -> SpiderQueenHungryCraft.LOGGER.info("[round7H] show probe: frame {} written ({})", name,
                        component.getString()));

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7H] show probe: frame {} taken - camera {} , head {} ({} blocks away) , neck angle {} degrees", name,
                minecraft.player.blockPosition(), subject.getMuzzlePosition(),
                Math.round(minecraft.player.position().distanceTo(subject.getMuzzlePosition()) * 100.0D) / 100.0D,
                Math.round(subject.syncedHeadYaw()));
    }

    /** @return the sum of every rotation of the drawn skeleton: the number that tells a still picture from a moving one. */
    private static double poseSum()
    {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(subject)
                instanceof spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer bone)
                || bone.boneModel().tabula() == null)
        {
            return 0.0D;
        }

        double sum = 0.0D;

        for (com.github.alexthe666.citadel.client.model.AdvancedModelBox part : bone.boneModel().tabula()
                .getAllParts())
        {
            sum += Math.abs(part.rotateAngleX) + Math.abs(part.rotateAngleY) + Math.abs(part.rotateAngleZ);
        }

        return sum;
    }

    /**
     * The neck test of the round: the queen moves to the left of the unit, the server turns its head to her, and the frame
     * is taken while the turn is really there. The angle printed here is the one the client animation bends the neck by,
     * because the animation is driven by exactly that synced number.
     */
    private static void lookPhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            subject.beginContract(queen.getUUID(), queen.level().getGameTime() + 6000L);

            Vec3 side = new Vec3(-subject.getLookAngle().z, 0.0D, subject.getLookAngle().x);
            Vec3 where = subject.position().add(side.scale(6.0D));
            queen.teleportTo(where.x, subject.getY() + 3.0D, where.z);
            minecraft.player.setPos(where.x, subject.getY() + 3.0D, where.z);

            SpiderQueenHungryCraft.LOGGER.info("[round7H] show probe: the queen stands to the left of the unit, so the "
                    + "server turns its head; the neck and the head have to follow in the picture");
        }

        if (phaseTicks > 20 && Math.abs(subject.syncedHeadYaw()) > 12.0F)
        {
            frameTheHead(minecraft, 0.55D, 0.15D);
            shoot(minecraft, "6_look_left");
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: PASS - the head turned to a synced yaw of {} degrees",
                    Math.round(subject.syncedHeadYaw()));
            phase++;
            phaseTicks = 0;
        }
        else if (phaseTicks > 400)
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7H] show probe: FAILED - the head never turned (synced yaw {})",
                    subject.syncedHeadYaw());
            phase++;
            phaseTicks = 0;
        }
    }

    /** The bite test: a dummy stands in front of the jaws, the bite runs, and a frame is taken as they close. */
    private static void bitePhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            ServerLevel level = minecraft.getSingleplayerServer().overworld();
            Vec3 front = subject.getMuzzlePosition().add(subject.getLookAngle().scale(2.0D));
            Zombie dummy = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);

            if (dummy != null)
            {
                dummy.moveTo(front.x, subject.getY(), front.z, 0.0F, 0.0F);
                dummy.setNoAi(true);
                level.addFreshEntity(dummy);
                dummyHealthBefore = dummy.getHealth();
                dummyRef = dummy;
                subject.startBite(dummy);
                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7H] show probe: a dummy stands in front of the jaws and the bite was started={}",
                        subject.isBiting());
            }
        }

        frameTheHead(minecraft, 0.6D, 0.2D);

        if (phaseTicks == SoulfireDragonEntity.BITE_HIT_TICK + 1)
        {
            shoot(minecraft, "7_bite");
        }

        if (phaseTicks == SoulfireDragonEntity.BITE_TICKS + 25)
        {
            float left = dummyRef == null ? dummyHealthBefore : dummyRef.getHealth();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: {} - the bite is over (biting={}) and it took {} of the dummy health: {} -> {} "
                            + "(one bite, one blow)",
                    Math.abs(dummyHealthBefore - left) > 0.01F && !subject.isBiting() ? "PASS" : "FAILED",
                    subject.isBiting(), Math.round(dummyHealthBefore - left), Math.round(dummyHealthBefore),
                    Math.round(left));

            if (dummyRef != null)
            {
                dummyRef.discard();
            }

            phase++;
            phaseTicks = 0;
        }
    }

    /** Two frames of a standing unit, a second apart, and the number that says whether anything moved between them. */
    private static void idlePhase(Minecraft minecraft)
    {
        frameTheHead(minecraft, 0.4D, 0.2D);

        // The idle sway is what a unit shows when it is doing nothing else, so the probe waits until the animal really
        // stands still (it walks back to its post first) instead of measuring a walking unit and calling that a failure.
        // The walk animation is the animation's own input, so it is the client copy of the animal that is asked, not the
        // server original.
        Entity clientCopy = minecraft.level.getEntity(subject.getId());
        float swing = clientCopy instanceof SoulfireDragonEntity copy ? copy.walkAnimation.speed() : 1.0F;

        boolean still = subject.onGround() && !subject.isFlying() && !subject.isHovering() && !subject.isLanding()
                && subject.getDeltaMovement().horizontalDistanceSqr() < 4.0E-4D && swing <= 0.035F;

        if (still && idleSince == 0)
        {
            idleSince = phaseTicks;
            idleSumA = poseSum();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: the unit stands still at {} (walk animation {}) , and the idle animation is "
                            + "measured from here",
                    subject.blockPosition(), Math.round(swing * 1000.0F) / 1000.0F);
        }

        int waited = idleSince == 0 ? 0 : phaseTicks - idleSince;

        if (still && waited == 20)
        {
            shoot(minecraft, "8_idle_a");
        }

        if (still && waited == 45)
        {
            shoot(minecraft, "8_idle_b");
            double now = poseSum();
            double moved = Math.abs(now - idleSumA);

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: {} - the standing skeleton moved by {} between the two frames (sum of all "
                            + "rotations {} -> {})",
                    moved > 0.05D ? "PASS" : "FAILED", Math.round(moved * 100.0D) / 100.0D,
                    Math.round(idleSumA * 100.0D) / 100.0D, Math.round(now * 100.0D) / 100.0D);

            phase++;
            phaseTicks = 0;
        }
        else if (phaseTicks > 600)
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "[round7H] show probe: FAILED - the unit never stood still (flying={} hovering={} landing={} onGround={}"
                            + " speed={})",
                    subject.isFlying(), subject.isHovering(), subject.isLanding(), subject.onGround(),
                    Math.round(subject.getDeltaMovement().horizontalDistanceSqr() * 1000.0D) / 1000.0D);
            phase++;
            phaseTicks = 0;
        }
    }

    /**
     * The rig of the round, asked on a live client - three questions, and the last one is the end to end test:
     *
     * <ol>
     * <li><b>Does the server carry the regions?</b> Six regions, and the world itself has to know each of them by its own
     * identity ({@code getEntityOrPart}), because a region that is only a number in the head of the unit can never be hit
     * by an arrow.</li>
     * <li><b>Does the client carry them too?</b> The game fills its own map of parts out of the parent entity, and the
     * crosshair is tested against exactly that map - an empty one would be a wing that can be seen and never clicked.</li>
     * <li><b>Does a real click of the queen land on the wing?</b> The probe traces the very ray the game traces when the
     * crosshair is pressed, and then sends the real attack of a player at the region that ray found: client, packet and
     * server are all in the chain, and the proof is the health of the unit itself.</li>
     * </ol>
     */
    private static void partsPhase(Minecraft minecraft)
    {
        if (phaseTicks == 1)
        {
            ServerLevel level = minecraft.getSingleplayerServer().overworld();
            PartEntity<?>[] parts = subject.getParts();
            StringBuilder report = new StringBuilder();

            partsServerOk = subject.isMultipartEntity() && parts != null && parts.length == 6;

            for (PartEntity<?> part : parts)
            {
                boolean known = level.getEntityOrPart(part.getId()) == part;
                partsServerOk = partsServerOk && known;
                report.append(' ').append(part instanceof SoulfireDragonPart named ? named.getPartName() : "?")
                        .append('#').append(part.getId()).append(known ? "(known)" : "(UNKNOWN)");
            }

            SpiderQueenHungryCraft.LOGGER.info("[round7H] show probe: {} - the server carries the regions of the unit:{}",
                    partsServerOk ? "PASS" : "FAILED", report);

            // The queen steps out to the left wing, close enough that the game accepts her blow: an attack from further
            // than six blocks is refused by the server, and a test standing further away would only measure its distance.
            Vec3 wing = wingCentre();
            Vec3 outward = wing.subtract(subject.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
            placeCamera(minecraft, wing.add(outward.scale(2.0D)), wing);
            partsHealthBefore = subject.getHealth();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: the queen steps up to the left wing at {}, carrying {} health on the unit",
                    wing, partsHealthBefore);
        }

        // The client is asked on its own side of the fence: its own copy of the unit, its own map of regions, and its own
        // ray - the three things a click really goes through.
        if (phaseTicks == 8)
        {
            Entity clientEntity = minecraft.level.getEntity(subject.getId());

            if (!(clientEntity instanceof SoulfireDragonEntity clientDragon))
            {
                SpiderQueenHungryCraft.LOGGER.error(
                        "[round7H] show probe: FAILED - the client has no copy of the unit at all");

                phase++;
                phaseTicks = 0;

                return;
            }

            PartEntity<?>[] clientParts = clientDragon.getParts();
            partsClientOk = clientParts != null && clientParts.length == 6;

            for (PartEntity<?> part : clientParts)
            {
                partsClientOk = partsClientOk && minecraft.level.getPartEntities().contains(part);
            }

            // The crosshair looks out of the eyes, not out of the feet: the queen was set down on the level of the wing, so
            // an aim taken from her feet would send the ray over the wing it is meant to hit. The aim is corrected here,
            // from her eyes to the wing, exactly the way the camera of a player is aimed.
            Vec3 eye = minecraft.player.getEyePosition();
            Vec3 toWing = wingCentre().subtract(eye);
            minecraft.player.setYRot((float) (Mth.atan2(toWing.z, toWing.x) * 180.0D / Math.PI) - 90.0F);
            minecraft.player.setXRot((float) (-(Mth.atan2(toWing.y, Math.sqrt(toWing.x * toWing.x + toWing.z * toWing.z))
                    * 180.0D / Math.PI)));

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: {} - the client knows the same regions (its own map holds {} of them and all six "
                            + "are in it), and its left wing stands {} blocks from the wing of the server",
                    partsClientOk ? "PASS" : "FAILED", minecraft.level.getPartEntities().size(),
                    Math.round(Math.sqrt(clientParts[3].getBoundingBox().getCenter().distanceToSqr(wingCentre()))
                            * 100.0D) / 100.0D);

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: DIAGNOSTIC - server at {} (body yaw {}) , client at {} (body yaw {}) , server "
                            + "wing {} , client wing {} , server head {} , client head {}",
                    subject.position(), subject.yBodyRot, clientDragon.position(), clientDragon.yBodyRot, wingCentre(),
                    clientParts[3].getBoundingBox().getCenter(), subject.getParts()[0].getBoundingBox().getCenter(),
                    clientParts[0].getBoundingBox().getCenter());

            askClientForAWing(minecraft, clientEntity);
        }

        if (phaseTicks == 30)
        {
            float now = subject.getHealth();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: {} - a real blow of the queen at the left wing (sent over the network like a "
                            + "click) took {} of the health of the unit: {} -> {}",
                    partsBlowSent && now < partsHealthBefore ? "PASS" : "FAILED", Math.round(partsHealthBefore - now),
                    Math.round(partsHealthBefore), Math.round(now));

            partsPickOk = partsBlowSent && now < partsHealthBefore;
            frameTheHead(minecraft, 0.7D, 0.25D);
        }

        if (phaseTicks == 35)
        {
            // The regions grow with the animal: the same unit at its smallest and at its full size, on the server and on
            // the copy of the animal the client draws - the client is asked two ticks after the change, because the news
            // of a new stage reaches it over the network.
            subject.setStage(1);
            partsServerSmall = subject.getParts()[0].getBbWidth();
        }

        if (phaseTicks == 37)
        {
            partsClientSmall = clientHeadWidth();
        }

        if (phaseTicks == 40)
        {
            subject.setStage(5);
            partsServerFull = subject.getParts()[0].getBbWidth();
        }

        if (phaseTicks == 42)
        {
            partsClientFull = clientHeadWidth();

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: {} - the head region grows with the stage: server {}->{} , client {}->{}",
                    partsServerFull > partsServerSmall && partsClientFull > partsClientSmall ? "PASS" : "FAILED",
                    partsServerSmall, partsServerFull, partsClientSmall, partsClientFull);
        }

        if (phaseTicks == 50)
        {
            shoot(minecraft, "9_parts");
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: the rig of the unit was asked in three places - server={} client={} blow={}",
                    partsServerOk, partsClientOk, partsPickOk);
            phase++;
            phaseTicks = 0;
        }
    }

    /** @return the width of the head region as the client knows it, or -1 when the client has no copy of the unit. */
    private static float clientHeadWidth()
    {
        Entity client = Minecraft.getInstance().level == null ? null
                : Minecraft.getInstance().level.getEntity(subject.getId());

        return client instanceof SoulfireDragonEntity dragon && dragon.getParts() != null
                ? dragon.getParts()[0].getBbWidth()
                : -1.0F;
    }

    /**
     * Asks the client what its crosshair finds, and then sends the blow a click would send: the ray is traced the way the
     * game traces it (against the entities of the client level, which is where the regions of the animal live for the
     * player), and the attack goes out over the network as a real player attack at the region the ray found.
     */
    private static void askClientForAWing(Minecraft minecraft, Entity clientEntity)
    {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        EntityHitResult hit = crosshairHit(minecraft);
        AABB swept = minecraft.player.getBoundingBox().expandTowards(look.scale(8.0D)).inflate(1.0D);
        PartEntity<?> wing = ((SoulfireDragonEntity) clientEntity).getParts()[3];

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7H] show probe: DIAGNOSTIC - the queen stands at {} , her eyes are at {} and she looks {} , the "
                        + "client wing is at {} with the box {} , the swept box holds {} entities of which {} are regions of "
                        + "a unit , the ray meets the box of the wing: {}",
                minecraft.player.position(), eye, look, wing.getBoundingBox().getCenter(), wing.getBoundingBox(),
                minecraft.level.getEntities(minecraft.player, swept, candidate -> true).size(),
                minecraft.level.getEntities(minecraft.player, swept, candidate -> candidate instanceof SoulfireDragonPart)
                        .size(),
                wing.getBoundingBox().clip(eye, eye.add(look.scale(8.0D))).isPresent());

        if (hit != null && hit.getEntity() instanceof SoulfireDragonPart part && part.getParent() == clientEntity)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: PASS - the crosshair of the queen found the {} region of the unit, so a wing is "
                            + "really clickable",
                    part.getPartName());

            minecraft.gameMode.attack(minecraft.player, part);
            partsBlowSent = true;
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "[round7H] show probe: FAILED - the crosshair of the queen found {} instead of a region of the unit",
                    hit == null ? "nothing" : hit.getEntity().getType().toString());
        }
    }

    /** @return the centre of the left wing region of the subject, which is the region this phase aims at. */
    private static Vec3 wingCentre()
    {
        return subject.getParts()[3].getBoundingBox().getCenter();
    }

    /**
     * @return what the crosshair of the queen points at, asked the way the game itself asks it: a ray from her eyes along
     *         her look direction, tested against the entities of the level she is in - the client level, whose own map of
     *         regions is the reason a wing of a multi part animal can be clicked at all.
     */
    private static EntityHitResult crosshairHit(Minecraft minecraft)
    {
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 look = minecraft.player.getLookAngle();
        double reach = 8.0D;
        AABB box = minecraft.player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0D);

        return ProjectileUtil.getEntityHitResult(minecraft.player, eye, eye.add(look.scale(reach)), box,
                candidate -> !candidate.isSpectator() && candidate.isPickable(), reach * reach);
    }

    /**
     * The death chain of the round, walked three times over: a genuine Ice and Fire bone dragon, raised through the real
     * skill, the unit that comes out of it killed with a real damage source, and then the question that matters - is the
     * remains <b>entity</b> itself standing in the world again? Not "does a ledger say it should be".
     */
    private static void deathPhase(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();

        if (phaseTicks == 1)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7H] show probe: death route {} of {}: asking for a genuine {} bone dragon", deathAttempt + 1,
                    DEATH_ROUTES, routeKind());
            IceAndFireSupport.spawnBoneDragon(queen, routeKind(), 75);
        }
        else if (phaseTicks == 30)
        {
            raiseNearestRemains(level);
        }
        else if (phaseTicks == 60)
        {
            strikeRaised(level);
        }
        else if (phaseTicks == 100)
        {
            checkRemainsBack(level);
            deathAttempt++;
            phaseTicks = 0;

            if (deathAttempt >= DEATH_ROUTES)
            {
                phase = 99;
            }
        }
    }

    /** @return the kind of dragon of this route, so all three kinds of remains are exercised. */
    private static String routeKind()
    {
        return switch (deathAttempt)
        {
            case 1 -> "ice";
            case 2 -> "lightning";
            default -> "fire";
        };
    }

    /** Raises the nearest genuine remains through the skill of the queen, exactly as the sceptre does. */
    private static void raiseNearestRemains(ServerLevel level)
    {
        LivingEntity corpse = null;
        double best = 96.0D;

        for (Entity candidate : level.getEntities(queen, queen.getBoundingBox().inflate(48.0D)))
        {
            if (IceAndFireSupport.isBoneDragon(candidate) && candidate instanceof LivingEntity living)
            {
                double distance = candidate.distanceTo(queen);

                if (distance < best)
                {
                    best = distance;
                    corpse = living;
                }
            }
        }

        if (corpse == null)
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "[round7H] show probe: FAILED - no genuine bone dragon was found to raise");

            return;
        }

        SoulfireRising.Rising rising = SoulfireRising.raise(queen, corpse);
        raised = rising.dragon();

        if (raised == null)
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7H] show probe: FAILED - the skill refused the remains: {}",
                    rising.report().lines());

            return;
        }

        remainsId = raised.getOriginalBoneUuid();

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7H] show probe: raised a unit of kind {} stage {} that carries the remains of {}",
                raised.getOrigin(), raised.getStage(), remainsId);
    }

    /** Kills the raised unit with the damage source of this route. */
    private static void strikeRaised(ServerLevel level)
    {
        if (raised == null || raised.isRemoved())
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7H] show probe: FAILED - there is no unit to kill");

            return;
        }

        boolean hurt = switch (deathAttempt)
        {
            case 1 -> raised.hurt(level.damageSources().arrow(new net.minecraft.world.entity.projectile.Arrow(level,
                    queen), raised), 10000.0F);
            case 2 -> raised.hurt(level.damageSources().explosion(queen, queen), 10000.0F);
            default -> raised.hurt(level.damageSources().playerAttack(queen), 10000.0F);
        };

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7H] show probe: the unit of route {} was struck: hurt={} , dead={} , removed={}", deathAttempt + 1,
                hurt, raised.isDeadOrDying(), raised.isRemoved());
    }

    /** The one question of this chain: is the remains entity itself back in the world? */
    private static void checkRemainsBack(ServerLevel level)
    {
        Entity back = remainsId == null ? null : level.getEntity(remainsId);

        SpiderQueenHungryCraft.LOGGER.info(
                "[round7H] show probe: {} - after route {} the original remains {} are {} in the world{}",
                back != null ? "PASS" : "FAILED", deathAttempt + 1, remainsId, back != null ? "standing" : "NOT back",
                back == null ? " (if a ledger record is left, the sweep is still retrying)"
                        : " as " + back.getType() + " at " + back.blockPosition());
    }

    /** Prints the closing line of the run once, and puts the world back the way it was found. */
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
                "[round7H] show probe: the run is over - the frames are in build\\probe_shots");
    }
}
