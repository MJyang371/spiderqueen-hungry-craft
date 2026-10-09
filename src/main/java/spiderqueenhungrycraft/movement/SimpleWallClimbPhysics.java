package spiderqueenhungrycraft.movement;

import javax.annotation.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The physics of the simple ladder-style wall climb (Phase 6-RESET).
 *
 * <p>It only ever modifies the vanilla velocity of a player who is already pressed against a wall, exactly
 * like a vanilla ladder does. It never touches gravity, the hitbox, the camera, the player model or the
 * position, which is what keeps this version safe:
 * <ul>
 * <li>while the player pushes into the wall the ascend speed is clamped to a ladder-like value,</li>
 * <li>while the player only rests against it the fall speed is limited,</li>
 * <li>as soon as the wall is gone nothing is applied at all and vanilla gravity does its normal work.</li>
 * </ul>
 *
 * <p>The very same method runs on the client and on the server, so both sides agree without any extra packet.
 */
public final class SimpleWallClimbPhysics
{
    /** Ladder-like ascend speed per tick. Vanilla clamps a climbable ascend to 0.2. */
    private static final double CLIMB_UP_SPEED = 0.2D;

    /** Ladder-like maximum fall speed per tick while only hanging on the wall. Vanilla uses 0.15. */
    private static final double MAX_WALL_SLIDE_SPEED = -0.15D;

    /** How close a solid block face has to be to one of the four horizontal body sides to count as "on a wall". */
    private static final double WALL_CONTACT_REACH = 0.08D;

    /** Movement input below this counts as "only resting against the wall". */
    private static final double MIN_MOVE_INTENT = 0.05D;

    /** How far the intent is probed into the wall direction before it counts as "pushing into it". */
    private static final double WALL_PROBE_DISTANCE = 0.05D;

    private SimpleWallClimbPhysics()
    {
    }

    /**
     * Applies one tick of ladder-like wall movement to that player, if the mode is on and the body is still
     * on a wall. Active climbing gives an ascend speed, hanging on gives a limited slide speed. Outside of
     * that range nothing is applied at all, gravity simply does its normal work.
     */
    public static void apply(Player player)
    {
        if (!SimpleWallClimbController.isEnabled(player) || !canClimb(player))
        {
            return;
        }

        // Touching the wall is either "pressing into it" (the vanilla collision flag, which stops firing as
        // soon as W is released) or "still hanging right next to it" (the nearby face probe). Without the
        // second one a released W would fall freely instead of sliding.
        if (!player.horizontalCollision && !hasNearbyClimbableWall(player))
        {
            return;
        }

        Vec3 velocity = player.getDeltaMovement();
        double y;

        if (isPressingIntoWall(player))
        {
            y = Math.max(velocity.y, CLIMB_UP_SPEED);
        }
        else if (player.isShiftKeyDown())
        {
            // Ladder-like: sneaking holds the position instead of sliding down.
            y = Math.max(velocity.y, 0.0D);
        }
        else
        {
            y = Math.max(velocity.y, MAX_WALL_SLIDE_SPEED);
        }

        player.setDeltaMovement(velocity.x, y, velocity.z);
        player.resetFallDistance();
    }

    /**
     * @return true when a solid block face is within a few centimetres of one of the four horizontal sides of
     *         the body. It only reports "yes or no", the climb never needs to know which side it is.
     */
    public static boolean hasNearbyClimbableWall(Player player)
    {
        AABB box = player.getBoundingBox();

        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            AABB probe = box.move(direction.getStepX() * WALL_CONTACT_REACH, 0.0D,
                    direction.getStepZ() * WALL_CONTACT_REACH);

            if (!player.level().noCollision(player, probe))
            {
                return true;
            }
        }

        return false;
    }

    /** @return true when that player may use the wall climb at all right now. */
    private static boolean canClimb(Player player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player))
        {
            return false;
        }

        if (player.isPassenger() || player.isSleeping() || player.isFallFlying() || player.isSwimming())
        {
            return false;
        }

        if (player.getAbilities().flying || player.isInWater())
        {
            return false;
        }

        // Wall contact itself is checked by the caller, because "released W but still hanging next to the
        // wall" must keep working.
        return true;
    }

    /**
     * @return true when the player is really pushing into a solid wall, not just standing next to one. The
     *         intent comes from the movement input on the client and from the actual horizontal movement on
     *         the server, so both sides reach the same answer.
     */
    private static boolean isPressingIntoWall(Player player)
    {
        Vec3 intent = horizontalIntent(player);

        if (intent == null)
        {
            return false;
        }

        Vec3 offset = intent.normalize().scale(WALL_PROBE_DISTANCE);
        return !player.level().noCollision(player, player.getBoundingBox().move(offset));
    }

    /** @return the horizontal world direction the player wants to move in, null when there is no intent. */
    private static Vec3 horizontalIntent(Player player)
    {
        double forward = player.zza;
        double side = player.xxa;
        double input = Math.abs(forward) + Math.abs(side);

        if (input >= MIN_MOVE_INTENT)
        {
            float yRot = player.getYRot() * ((float) Math.PI / 180F);
            float sin = net.minecraft.util.Mth.sin(yRot);
            float cos = net.minecraft.util.Mth.cos(yRot);
            Vec3 local = new Vec3(side, 0.0D, forward);
            return new Vec3(local.x * cos - local.z * sin, 0.0D, local.z * cos + local.x * sin);
        }

        // No input information (typical on the server): fall back to the horizontal movement itself.
        Vec3 motion = player.getDeltaMovement();
        return motion.horizontalDistanceSqr() > MIN_MOVE_INTENT * MIN_MOVE_INTENT
                ? new Vec3(motion.x, 0.0D, motion.z)
                : null;
    }
}
