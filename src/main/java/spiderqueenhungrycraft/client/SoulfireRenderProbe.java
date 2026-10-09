package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.client.renderer.SoulfireDragonRenderer;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * A development probe that proves the soul fire dragon is really drawn on a screen (round 7D).
 *
 * <p>It exists because the failure of round 7C could not be seen from a server side test: the unit spawned, held its
 * contract and obeyed its orders, and was <b>invisible</b> - a client side problem that no {@code runGameTestServer}
 * can ever fail on. This probe therefore runs in the real client, on the real render thread, and it does three things
 * when it is switched on with {@code -Dspiderqueen.soulfire.probe=true}:
 *
 * <ol>
 * <li>it puts three soul fire dragons in front of the camera - one standing, one in the air and one sitting while it
 * breathes - as <b>client side</b> entities of the local level, so no server, no operator and no cheat is needed,</li>
 * <li>it takes screenshots of them through the ordinary screenshot of the game, and</li>
 * <li>it writes the frame counter of {@link SoulfireDragonRenderer} into the log, so the picture and the counter can
 * be read together.</li>
 * </ol>
 *
 * <p><b>It does nothing at all without that flag</b> (the field is a constant read once), it never touches the save
 * (the entities live in the client level only) and it can be deleted without leaving a trace anywhere else.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulfireRenderProbe
{
    /** The switch of this probe. Off unless the launcher asks for it. */
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.probe");

    /** The tick at which the empty baseline picture is taken, after the world has settled and before anything is placed. */
    private static final int BASELINE_SHOT_TICK = 100;

    /** The tick at which the three units are placed and the camera is levelled. */
    private static final int SPAWN_TICK = 160;

    /** The ticks at which the front and the side picture are taken. */
    private static final int FRONT_SHOT_TICK = 200;
    private static final int SIDE_SHOT_TICK = 280;

    /** How far in front of the camera the three units are placed, in blocks. */
    private static final double DISTANCE = 4.5D;

    /** How far to the sides they are spread, in blocks. */
    private static final double SPREAD = 3.0D;

    private static int ticks;
    private static boolean placed;
    private static int shots;

    /** The camera the baseline picture was taken from: every later picture is taken from exactly this spot. */
    @Nullable
    private static Vec3 anchor;

    /** The yaw of that camera. */
    private static float anchorYaw;

    /** The yaw the camera is held at, which is the anchor yaw except for the turned side picture. */
    private static float wantedYaw;

    private SoulfireRenderProbe()
    {
    }

    /** The three states the probe asks for, so one picture shows every pose of the model. */
    private enum ProbeState
    {
        STANDING, FLYING, SITTING_BREATHING
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (!ENABLED || event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        // The window of a probe run is not focused, so the game pauses itself the moment the world is up - and a
        // paused game draws its world behind a dark menu, which is a worthless picture. The probe closes that screen.
        if (minecraft.screen instanceof PauseScreen)
        {
            minecraft.setScreen(null);
        }

        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused())
        {
            return;
        }

        // Once the baseline camera exists, the player is held on it <b>every tick</b>: a camera that drifts by a single
        // block between two pictures, or that the game is still interpolating towards, turns the whole screen into a
        // difference - and this probe has to be able to tell a soul fire dragon from a moving cloud.
        if (anchor != null)
        {
            freeze(minecraft, wantedYaw);
        }

        ticks++;

        if (shots == 0 && ticks == BASELINE_SHOT_TICK)
        {
            // The empty picture, from a camera that is <b>pinned</b>: the player may be falling or gliding when the
            // world loads, and a camera that drifts between two pictures turns the whole screen into a difference.
            // This spot is where every later picture of the run is taken from, so only what was added can differ.
            anchor = minecraft.player.position();
            anchorYaw = minecraft.player.getYRot();
            wantedYaw = anchorYaw;
            freeze(minecraft, anchorYaw);
            shoot(minecraft, "baseline");
        }

        if (!placed && ticks == SPAWN_TICK)
        {
            place(minecraft);
        }

        if (placed && shots == 1 && ticks >= FRONT_SHOT_TICK)
        {
            freeze(minecraft, anchorYaw);
            shoot(minecraft, "front");
        }

        if (placed && shots == 2 && ticks == SIDE_SHOT_TICK)
        {
            // A second angle: the same spot, turned by forty degrees, so a wing and the tail can be judged too.
            wantedYaw = anchorYaw + 40.0F;
            freeze(minecraft, wantedYaw);
            shoot(minecraft, "side");
        }
    }

    /** Puts the camera back on the anchor of the baseline picture, facing that way and perfectly level. */
    private static void freeze(Minecraft minecraft, float yaw)
    {
        if (anchor == null || minecraft.player == null)
        {
            return;
        }

        minecraft.player.moveTo(anchor.x, anchor.y, anchor.z, yaw, 0.0F);
        minecraft.player.setDeltaMovement(Vec3.ZERO);
        minecraft.player.setXRot(0.0F);
        minecraft.player.setYRot(yaw);

        // The position the game interpolates from has to be moved as well, or the camera slides towards the anchor over
        // the next few frames and two pictures of the same place no longer line up.
        minecraft.player.setOldPosAndRot();
    }

    /** Puts one standing, one flying and one sitting unit in front of the camera, all of them client side only. */
    private static void place(Minecraft minecraft)
    {
        if (anchor == null)
        {
            return;
        }

        // The units are placed from the very camera the baseline picture was taken with, so they are guaranteed to be
        // in the frame of every picture of this run.
        freeze(minecraft, anchorYaw);

        Vec3 look = minecraft.player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();

        spawn(minecraft, look, side, -SPREAD, 0.0D, ProbeState.STANDING);
        spawn(minecraft, look, side, 0.0D, 0.0D, ProbeState.FLYING);
        spawn(minecraft, look, side, SPREAD, 0.0D, ProbeState.SITTING_BREATHING);

        placed = true;
        SpiderQueenHungryCraft.LOGGER.info("[round7D] soulfire render probe placed three units {} blocks ahead",
                (int) DISTANCE);
    }

    /** One probe unit of that state, in the local level of the client only. */
    private static void spawn(Minecraft minecraft, Vec3 look, Vec3 side, double offset, double lift, ProbeState state)
    {
        if (minecraft.level == null || minecraft.player == null)
        {
            return;
        }

        Entity raw = HungryCraftEntities.SOULFIRE_DRAGON.get().create(minecraft.level);

        if (!(raw instanceof SoulfireDragonEntity dragon))
        {
            return;
        }

        Vec3 eyes = minecraft.player.getEyePosition();
        Vec3 where = eyes.add(look.scale(DISTANCE)).add(side.scale(offset)).add(0.0D, lift, 0.0D);
        float yaw = minecraft.player.getYRot() + 180.0F;

        dragon.moveTo(where.x, where.y, where.z, yaw, 0.0F);
        dragon.setOrderMode(
                state == ProbeState.SITTING_BREATHING ? SpiderOrderMode.HOLD : SpiderOrderMode.FOLLOW);
        dragon.setSitting(state == ProbeState.SITTING_BREATHING);
        dragon.setBreathing(state == ProbeState.SITTING_BREATHING);
        dragon.setFlying(state == ProbeState.FLYING);
        dragon.setHovering(state == ProbeState.FLYING);

        if (state == ProbeState.FLYING)
        {
            dragon.setPos(where.x, where.y + 2.5D, where.z);
        }

        minecraft.level.addFreshEntity(dragon);

        SpiderQueenHungryCraft.LOGGER.info("[round7D] probe unit placed at {} state={}", dragon.blockPosition(), state);
    }

    /** Takes one picture of the frame that is about to be drawn, and reports the frame counter of the renderer. */
    private static void shoot(Minecraft minecraft, String name)
    {
        shots++;

        SpiderQueenHungryCraft.LOGGER.info("[round7D] soulfire render probe shot '{}': {} frames drawn so far",
                name, SoulfireDragonRenderer.renderedFrames());

        Screenshot.grab(minecraft.gameDirectory, "soulfire_probe_" + name, minecraft.getMainRenderTarget(),
                (Component message) -> SpiderQueenHungryCraft.LOGGER.info("[round7D] soulfire render probe shot '{}': {}",
                        name, message.getString()));
    }
}
