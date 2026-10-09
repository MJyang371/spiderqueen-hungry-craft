package spiderqueenhungrycraft.beast;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The charged jump of the beast (Phase 8C).
 *
 * <p>The ordinary player jump is replaced while transformed: the client only reports that the jump key was
 * pressed and that it was released again, and the server measures how long it was held on its own clock. No
 * client ever sends a power, a velocity or a target height, so a modified client cannot ask for a stronger
 * jump than it really charged.
 *
 * <p>The power is linear for this first version: {@code power = clamp(heldTicks / max_charge_ticks, 0, 1)}.
 * The vertical velocity is interpolated between the minimum and the maximum of the tuning file, and a small
 * forward velocity is added along the look direction, scaled by how fast the beast is already moving, so a
 * running beast carries its momentum over a gap while a standing one jumps straight up.
 *
 * <p>A jump strong enough to arm a stomp remembers its power in the form state. {@link QueenBeastStomp} reads
 * it on the next landing, which is deliberately decoupled from the vanilla fall distance: the beast is immune
 * to fall damage, so the distance would not be a reliable source for anything.
 */
public final class QueenBeastJump
{
    private QueenBeastJump()
    {
    }

    /** Client request: the jump key went down. Only remembered, nothing happens yet. */
    public static void press(ServerPlayer player)
    {
        QueenBeastState state = usable(player);

        if (state == null || state.getJumpChargeStartGameTime() >= 0L)
        {
            return;
        }

        // The server clock, never a number from the client.
        state.setJumpChargeStartGameTime(player.level().getGameTime());
    }

    /** Client request: the jump key went up. The charge ends here and the real jump is launched. */
    public static void release(ServerPlayer player)
    {
        QueenBeastState state = usable(player);

        if (state == null || state.getJumpChargeStartGameTime() < 0L)
        {
            return;
        }

        long heldTicks = player.level().getGameTime() - state.getJumpChargeStartGameTime();
        state.setJumpChargeStartGameTime(-1L);

        // A released key in mid air is simply dropped: the beast does not fly, it jumps from the ground.
        if (!player.onGround() || player.isPassenger())
        {
            return;
        }

        QueenBeastTuning.Jump jump = QueenBeastTuning.get().jump();
        float power = chargePower(heldTicks, jump.maxChargeTicks());
        double vertical = Mth.lerp(power, jump.minVertical(), jump.maxVertical());
        double forward = jump.forwardVelocity() * forwardFactor(player);
        Vec3 look = horizontalForward(player);

        player.setDeltaMovement(look.x * forward, vertical, look.z * forward);

        // The server owns the motion and hands it to every client, so the local player never rubber-bands.
        player.hurtMarked = true;
        player.hasImpulse = true;

        if (power >= jump.minStompCharge())
        {
            state.setStompArmed(true);
            state.setStompPower(power);
        }
        else
        {
            state.setStompArmed(false);
            state.setStompPower(0.0F);
        }
    }

    /** The one formula of the charge power: linear with the held ticks, clamped to a full charge. */
    public static float chargePower(long heldTicks, int maxChargeTicks)
    {
        if (heldTicks <= 0L || maxChargeTicks <= 0)
        {
            return 0.0F;
        }

        return Mth.clamp((float) heldTicks / maxChargeTicks, 0.0F, 1.0F);
    }

    /** @return the horizontal unit vector the beast looks along, body direction when it looks straight down. */
    public static Vec3 horizontalForward(ServerPlayer player)
    {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);

        if (forward.lengthSqr() < 1.0E-4D)
        {
            forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        return forward.normalize();
    }

    /** @return 0 for a standing beast, 1 from the ordinary walking pace upwards, so a hop stays a hop. */
    private static double forwardFactor(ServerPlayer player)
    {
        return Mth.clamp(player.getDeltaMovement().horizontalDistance() * 5.0D, 0.0D, 1.0D);
    }

    /** @return the form state when that player may use the jump at all, null otherwise. */
    private static QueenBeastState usable(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player)
                || !QueenBeastState.isTransformed(player))
        {
            return null;
        }

        return QueenBeastState.of(player).orElse(null);
    }
}
