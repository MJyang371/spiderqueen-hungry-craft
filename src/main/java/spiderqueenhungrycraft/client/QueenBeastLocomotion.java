package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The locomotion feel of the Queen Beast form (Phase 8B2): a little inertia on the movement input and no
 * sprinting.
 *
 * <p>A four times larger spider that starts and stops exactly like a player feels weightless, so the raw
 * movement input is smoothed towards the wanted one: about four ticks to reach a full input and about three
 * ticks to release it, which also means a direction change passes through zero instead of flipping instantly.
 *
 * <p>Only the input is smoothed, never the velocity of the entity: knockback from a mob, an explosion, a fall
 * or a jump always stays exactly as vanilla produced it. Everything here is client side, only for the local
 * player and only while it is transformed and standing on the ground.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastLocomotion
{
    private static LocalPlayer trackedPlayer;
    private static ClientLevel trackedLevel;
    private static float smoothedForward;
    private static float smoothedStrafe;

    private QueenBeastLocomotion()
    {
    }

    /** Smooths the raw movement input of the local beast. Everything else keeps the vanilla input. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event)
    {
        if (!(event.getEntity() instanceof LocalPlayer player) || !QueenBeastState.isTransformed(player))
        {
            forget();
            return;
        }

        Input input = event.getInput();
        float rawForward = input.forwardImpulse;
        float rawStrafe = input.leftImpulse;

        // Air, water, flight and riding keep the vanilla handling: a heavy air control is a later phase.
        if (!canSmooth(player))
        {
            smoothedForward = rawForward;
            smoothedStrafe = rawStrafe;
            return;
        }

        if (trackedPlayer != player || trackedLevel != Minecraft.getInstance().level)
        {
            trackedPlayer = player;
            trackedLevel = Minecraft.getInstance().level;
            smoothedForward = rawForward;
            smoothedStrafe = rawStrafe;
        }

        smoothedForward = approach(smoothedForward, rawForward);
        smoothedStrafe = approach(smoothedStrafe, rawStrafe);

        // Diagonal movement may never become faster than straight movement, exactly like the vanilla input.
        float magnitude = smoothedForward * smoothedForward + smoothedStrafe * smoothedStrafe;

        if (magnitude > 1.0F)
        {
            float scale = Mth.sqrt(magnitude);
            smoothedForward /= scale;
            smoothedStrafe /= scale;
        }

        input.forwardImpulse = smoothedForward;
        input.leftImpulse = smoothedStrafe;
    }

    /** The beast never sprints, and its leftover inertia never survives a logout, a death or a dimension. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || !QueenBeastState.isTransformed(player))
        {
            forget();
            return;
        }

        if (player.isSprinting())
        {
            // Its movement attribute already carries the whole pace of a beast.
            player.setSprinting(false);
        }
    }

    private static boolean canSmooth(LocalPlayer player)
    {
        return player.onGround() && !player.isSwimming() && !player.getAbilities().flying && !player.isPassenger()
                && !player.isFallFlying();
    }

    private static float approach(float current, float wanted)
    {
        // Easing off or turning around is a slow brake; only a wanted input beyond the current one accelerates.
        float rate = isReversing(current, wanted) || Math.abs(wanted) <= Math.abs(current)
                ? QueenBeastConstants.BEAST_INPUT_DECEL_PER_TICK
                : QueenBeastConstants.BEAST_INPUT_ACCEL_PER_TICK;

        return Mth.approach(current, wanted, rate);
    }

    /**
     * @return true while the wanted input points the other way round than the current one, which is what a
     *         change from forward to backward looks like. Such a turn is braked through zero first and only
     *         accelerated again once the input really points the new way.
     */
    private static boolean isReversing(float current, float wanted)
    {
        return current != 0.0F && wanted != 0.0F && Math.signum(current) != Math.signum(wanted);
    }

    private static void forget()
    {
        trackedPlayer = null;
        trackedLevel = null;
        smoothedForward = 0.0F;
        smoothedStrafe = 0.0F;
    }
}
