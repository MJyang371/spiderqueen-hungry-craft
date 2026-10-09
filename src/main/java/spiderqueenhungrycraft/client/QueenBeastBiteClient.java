package spiderqueenhungrycraft.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.network.C2SBeastBitePacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;

/**
 * Client side of the beast bite (Phase 8B2, input and impulse redefined by 8B-POLISH-2): the attack input, the
 * head lunge state of every visible beast and the short first person impulse of the attacker.
 *
 * <p>The two mouse buttons are strictly separated while transformed:
 *
 * <ul>
 * <li>the left button belongs to the blocks. Looking at a block keeps the ordinary claw mining of phase 8A
 * completely untouched, while a creature or the empty air is swallowed without a swing and without a single
 * point of damage, because the beast has no weapon to swing,</li>
 * <li>the right button is the bite, always. A press is read as a real press edge, so one physical click sends
 * exactly one request no matter how long the button is held, and the server keeps its own cooldown on top of
 * that.</li>
 * </ul>
 *
 * <p>The client never decides a bite. It only announces the press, and every animation or impulse here is
 * driven by the feedback packet the server sends after it really accepted the bite, so a rejected bite stays
 * completely silent.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastBiteClient
{
    /** Entity id to the client tick its bite animation started on. Client only, never sent anywhere. */
    private static final Map<Integer, Integer> BITE_START_TICKS = new HashMap<>();

    /** How much stronger than a bite the lunge of that entity is, for a devour or a regurgitation. */
    private static final Map<Integer, Float> LUNGE_SCALES = new HashMap<>();

    private static int clientTick;
    private static int shakeStartTick = Integer.MIN_VALUE;

    /** Press edge memory of the right mouse button, so one physical press is exactly one bite request. */
    private static boolean useWasDown;

    /** The impulse of the last action feedback, or null while the ordinary bite values apply. */
    private static ShakeFlavour flavour;

    /** Name of that impulse, for the debug output only. */
    private static String flavourName = "bite";

    /**
     * One flavour of the shared camera impulse (Phase 8E2A-POLISH-1).
     *
     * <p>A devour and a regurgitation use the very same lunge and the very same camera system as a bite; the only
     * thing that differs is how long and how strong that impulse is. This record carries those five numbers, so
     * no second camera system is needed.
     */
    public record ShakeFlavour(int ticks, float pitch, float yaw, float roll, double fov)
    {
    }

    private QueenBeastBiteClient()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        clientTick++;

        // Both maps only live for a few ticks, and a world change clears them completely.
        BITE_START_TICKS.entrySet().removeIf(
                entry -> clientTick - entry.getValue() > QueenBeastConstants.BEAST_BITE_ANIMATION_TICKS);

        if (Minecraft.getInstance().level == null)
        {
            BITE_START_TICKS.clear();
            LUNGE_SCALES.clear();
            shakeStartTick = Integer.MIN_VALUE;
            useWasDown = false;
            return;
        }

        tickBiteInput(Minecraft.getInstance());
    }

    /**
     * The right mouse button of a beast is the bite and nothing else. The press is read here instead of in the
     * click event because the vanilla use action repeats itself every few ticks while the button is held: an
     * edge is a real press, a held button is silence.
     */
    private static void tickBiteInput(Minecraft minecraft)
    {
        LocalPlayer player = minecraft.player;
        boolean down = player != null && minecraft.screen == null && minecraft.options.keyUse.isDown();
        boolean pressed = down && !useWasDown;
        useWasDown = down;

        if (!pressed || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        HungryCraftNetwork.sendToServer(new C2SBeastBitePacket());
    }

    /** The mouse buttons of a transformed beast: left click is all about blocks, right click is the bite. */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        if (event.isAttack())
        {
            // A block in the crosshair belongs to the claw mining of phase 8A, never to the bite.
            if (minecraft.hitResult instanceof BlockHitResult)
            {
                return;
            }

            // A creature or the air: no ordinary melee, no held weapon, no bite. Only a creature gets the
            // small swing of the front legs, the empty air stays completely silent.
            event.setCanceled(true);
            event.setSwingHand(minecraft.hitResult instanceof EntityHitResult);
            return;
        }

        if (event.isUseItem())
        {
            // The right button is reserved for the bite, so no chest, no door, no button, no placed block and
            // no eaten item may come out of it. The bite request itself is sent by the press edge above.
            event.setCanceled(true);
            event.setSwingHand(true);
        }
    }

    /**
     * The server accepted a devour or a regurgitation (Phase 8E2A-POLISH-1).
     *
     * <p>This is the same feedback a bite gets, only with a chosen impulse and with a name. A beast lunges
     * exactly like it bites; an ordinary queen does not lunge at all - she has no beast body to throw - but she
     * still feels a light kick, because her arm swing alone is not enough to tell that something left her
     * stomach.
     *
     * @param entityId the player who did it
     * @param beast    true when that player is transformed, so the pose of a beast is played
     * @param chosen   the impulse of that action
     * @param name     the name of that action, for the debug output only
     */
    public static void onActionFeedback(int entityId, boolean beast, ShakeFlavour chosen, String name,
            float lungeScale)
    {
        if (beast)
        {
            BITE_START_TICKS.put(entityId, clientTick);
            LUNGE_SCALES.put(entityId, lungeScale);
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && player.getId() == entityId)
        {
            shakeStartTick = clientTick;
            flavour = chosen;
            flavourName = name;
        }

        if (SpiderQueenHungryCraft.LOGGER.isDebugEnabled())
        {
            SpiderQueenHungryCraft.LOGGER.debug(
                    "[DEVOUR_FX] feedback received action={} actor={} beast={} local={} lunge={} shakeTicks={}",
                    name, entityId, beast, player != null && player.getId() == entityId, beast, chosen.ticks());
        }
    }

    /** Debug output of the running feedback, used by the temporary polish probe. */
    public static String debugState()
    {
        int remaining = shakeStartTick == Integer.MIN_VALUE ? 0
                : Math.max(0, (flavour != null ? flavour.ticks() : QueenBeastClientTuning.shake().shakeTicks())
                        - (clientTick - shakeStartTick));

        return "lungeEntities=" + BITE_START_TICKS.size() + " shake=" + flavourName + " shakeTicksLeft=" + remaining
                + " peakPitch=" + peakPitch() + " peakYaw=" + peakYaw() + " peakFov=" + peakFov()
                + " lungeScale=" + lungeScaleOfSelf();
    }

    /** The server accepted a bite: start the head lunge for everyone and the impulse for the attacker. */
    public static void onBiteFeedback(int attackerId)
    {
        BITE_START_TICKS.put(attackerId, clientTick);
        LUNGE_SCALES.put(attackerId, 1.0F);
        flavour = null;
        flavourName = "bite";

        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && player.getId() == attackerId)
        {
            shakeStartTick = clientTick;
        }
    }

    /** @return how strong the head lunge of that entity is right now, zero while it is not biting. */
    public static float lungeAmount(int entityId, float partialTick)
    {
        Integer start = BITE_START_TICKS.get(entityId);

        if (start == null)
        {
            return 0.0F;
        }

        int age = clientTick - start;
        float[] profile = QueenBeastConstants.BEAST_BITE_LUNGE_PROFILE;

        if (age < 0 || age >= profile.length)
        {
            // The pose is over: the next lunge of that entity is a bite again.
            LUNGE_SCALES.remove(entityId);
            return 0.0F;
        }

        float current = profile[age];
        float next = age + 1 < profile.length ? profile[age + 1] : 0.0F;

        // A devour or a regurgitation throws the same body a little harder than a bite does.
        return Mth.lerp(partialTick, current, next) * LUNGE_SCALES.getOrDefault(entityId, 1.0F);
    }

    /** A short impulse of the first person camera of the attacker, and only there. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event)
    {
        float strength = shakeStrength((float) event.getPartialTick());

        if (strength == 0.0F || !canShake())
        {
            return;
        }

        // The shape of the impulse is chosen in shakeStrength; only the amplitude is needed here.

        event.setPitch(event.getPitch() + peakPitch() * strength);
        event.setYaw(event.getYaw() + peakYaw() * strength);
        event.setRoll(event.getRoll() + peakRoll() * strength);
    }

    /** The lens punch of the bite: a short pull in, decaying with the very same impulse. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event)
    {
        float strength = shakeStrength((float) event.getPartialTick());

        if (strength == 0.0F || !canShake())
        {
            return;
        }

        event.setFOV(event.getFOV() - peakFov() * strength);
    }

    /** @return the lunge multiplier of the local beast right now, one while it is a plain bite. */
    private static float lungeScaleOfSelf()
    {
        LocalPlayer player = Minecraft.getInstance().player;

        return player == null ? 1.0F : LUNGE_SCALES.getOrDefault(player.getId(), 1.0F);
    }

    /** Peak pitch of the running impulse: the flavour of the last action, or the tuning of an ordinary bite. */
    private static float peakPitch()
    {
        return flavour != null ? flavour.pitch() : QueenBeastClientTuning.shake().shakePitch();
    }

    /** Peak yaw of the running impulse. */
    private static float peakYaw()
    {
        return flavour != null ? flavour.yaw() : QueenBeastClientTuning.shake().shakeYaw();
    }

    /** Peak roll of the running impulse. */
    private static float peakRoll()
    {
        return flavour != null ? flavour.roll() : QueenBeastClientTuning.shake().shakeRoll();
    }

    /** Lens punch of the running impulse. */
    private static double peakFov()
    {
        return flavour != null ? flavour.fov() : QueenBeastClientTuning.shake().shakeFov();
    }

    /** The impulse is only ever felt in the first person view of the attacker, never behind a menu. */
    private static boolean canShake()
    {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.screen == null && minecraft.options.getCameraType().isFirstPerson();
    }

    /**
     * A hard first frame with a fast decay and a tiny settle, interpolated over the frame.
     *
     * <p>The shape of {@link QueenBeastConstants#BEAST_BITE_SHAKE_PROFILE} is stretched over the duration the
     * server sent, so a pack that raises {@code duration_ticks} in the tuning file really gets a longer kick
     * instead of the same eight ticks with a longer silence behind them.
     */
    private static float shakeStrength(float partialTick)
    {
        if (shakeStartTick == Integer.MIN_VALUE)
        {
            return 0.0F;
        }

        int age = clientTick - shakeStartTick;

        if (age < 0)
        {
            return 0.0F;
        }

        float[] profile = QueenBeastConstants.BEAST_BITE_SHAKE_PROFILE;
        int duration = flavour != null ? flavour.ticks() : QueenBeastClientTuning.shake().shakeTicks();

        if (age >= duration)
        {
            // The impulse is over: the next one is a bite again until the server says otherwise.
            flavour = null;
            flavourName = "bite";
            return 0.0F;
        }

        // Position inside the shape: eight ticks of duration sample the profile one tick at a time.
        float position = (age + partialTick) * profile.length / (float) duration;

        return sampleProfile(profile, position);
    }

    /** Linear sample of a normalized shape at a fractional position, zero outside of it. */
    private static float sampleProfile(float[] profile, float position)
    {
        int index = (int) Math.floor(position);

        if (index < 0 || index >= profile.length)
        {
            return 0.0F;
        }

        float current = profile[index];
        float next = index + 1 < profile.length ? profile[index + 1] : 0.0F;

        return Mth.lerp(position - index, current, next);
    }
}
