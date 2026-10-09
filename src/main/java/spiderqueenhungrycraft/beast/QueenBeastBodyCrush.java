package spiderqueenhungrycraft.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The body crush of the Queen Beast form (Phase 8D): what the four times enlarged body pushes aside while it
 * simply walks.
 *
 * <p>A giant spider should not be stopped by a tree, a hedge or a window, so the soft obstacles of the
 * {@code block_breaking.body_crush} rule give way. Stone, deepslate, a whole house and everything else above
 * that rule still blocks the way: tearing those down is the job of the charge, never of a walk.
 *
 * <p>The scan is deliberately tiny. Only the volume the body really swept since the previous tick is checked,
 * plus a very small prediction in the direction of the movement, so an obstacle is caught a moment before the
 * ordinary collision clip would stop the beast on it, and only a handful of blocks are touched per tick.
 * Nothing is dropped by default, so a walk through a forest never litters the ground with item entities.
 */
public final class QueenBeastBodyCrush
{
    private QueenBeastBodyCrush()
    {
    }

    /** One server tick of a walking beast. */
    public static void tick(ServerPlayer player, QueenBeastState state)
    {
        AABB body = player.getBoundingBox();
        AABB last = state.getLastBody();
        state.setLastBody(body);

        // A charge already destroys everything it runs through, so the crush stands aside while it runs.
        if (state.isChargeActive())
        {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();

        // The corridor of this step: where the body really moved since the previous tick, plus a very small
        // prediction in front of it, because the vanilla collision stops a walking player slightly before the
        // body would overlap the block. The vertical part is zero, so a step down a hill never eats the ground.
        Vec3 movement = last == null
                ? Vec3.ZERO
                : new Vec3(body.minX - last.minX, 0.0D, body.minZ - last.minZ);
        Vec3 forward = QueenBeastJump.horizontalForward(player)
                .scale(QueenBeastConstants.BEAST_BODY_CRUSH_LOOKAHEAD);
        Vec3 corridor = movement.add(forward.x, 0.0D, forward.z);

        QueenBeastMovementCorridor.destroy(level, player, body, corridor,
                QueenBeastConstants.BEAST_CORRIDOR_MARGIN_HORIZONTAL,
                QueenBeastConstants.BEAST_CORRIDOR_MARGIN_TOP, QueenBeastTuning.RuleProfile.BODY_CRUSH,
                QueenBeastConstants.BEAST_BODY_CRUSH_MAX_BLOCKS_PER_TICK);
    }
}
