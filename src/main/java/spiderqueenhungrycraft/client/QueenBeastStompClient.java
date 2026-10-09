package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The stomp impulse of the local beast (Phase 8C).
 *
 * <p>A charged landing is the heaviest thing the queen does, so its impulse of the first person camera is a
 * whole tier above the bite: a longer, stronger kick with the same shape, a hard first frame, a fast decay and
 * a small settle. Only the beast that really landed feels it, everybody else just watches the crater.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastStompClient
{
    private static int clientTick;
    private static int shakeStartTick = Integer.MIN_VALUE;

    private QueenBeastStompClient()
    {
    }

    /** The server says that beast really landed a stomp: only the attacker shakes. */
    public static void onStompFeedback(int entityId)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && player.getId() == entityId)
        {
            shakeStartTick = clientTick;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        clientTick++;

        if (Minecraft.getInstance().level == null)
        {
            shakeStartTick = Integer.MIN_VALUE;
        }
    }

    /** The impulse itself, heavier but shaped exactly like the bite impulse. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event)
    {
        float strength = shakeStrength((float) event.getPartialTick());

        if (strength == 0.0F || !canShake())
        {
            return;
        }

        QueenBeastClientTuning.Shake stomp = QueenBeastClientTuning.stompShake();

        event.setPitch(event.getPitch() + stomp.shakePitch() * strength);
        event.setYaw(event.getYaw() + stomp.shakeYaw() * strength);
        event.setRoll(event.getRoll() + stomp.shakeRoll() * strength);
    }

    /** The lens punch of the landing, again heavier than the one of the bite. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event)
    {
        float strength = shakeStrength((float) event.getPartialTick());

        if (strength == 0.0F || !canShake())
        {
            return;
        }

        event.setFOV(event.getFOV() - QueenBeastClientTuning.stompShake().shakeFov() * strength);
    }

    private static boolean canShake()
    {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.screen == null && minecraft.options.getCameraType().isFirstPerson();
    }

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

        float[] profile = QueenBeastConstants.BEAST_STOMP_SHAKE_PROFILE;
        int duration = QueenBeastClientTuning.stompShake().shakeTicks();

        if (age >= duration)
        {
            return 0.0F;
        }

        float position = (age + partialTick) * profile.length / (float) duration;
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
