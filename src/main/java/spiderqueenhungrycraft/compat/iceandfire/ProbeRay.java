package spiderqueenhungrycraft.compat.iceandfire;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The long range probe ray of the Ice and Fire debug commands (Necromancy IV prototype 1).
 *
 * <p>It is the same idea as the command ray of the queen sceptre ({@code QueenCommandRay}): the eyes of the player, the
 * view vector, {@link #RANGE} blocks, and the nearest thing that is met wins. A creature behind a wall is never picked,
 * because the creature search stops at the distance of the block hit.
 *
 * <p><b>It knows nothing about Ice and Fire.</b> It answers with an ordinary {@link Entity} of the game, so the facade
 * can use it in a workspace where that mod does not exist at all, and the inspect command can say "that is not an Ice
 * and Fire dragon" about anything else the crosshair happens to point at.
 */
public final class ProbeRay
{
    /** How far a debug probe reaches: sixty four blocks, the distance the round asks for. */
    public static final double RANGE = 64.0D;

    private ProbeRay()
    {
    }

    /**
     * @return the creature the crosshair of that player points at, or null when it points at a block or at nothing.
     *
     *         <p>Only pickable, non spectator living beings are candidates. Ice and Fire's own corpse is one of them:
     *         a dragon that is dead in Ice and Fire's sense overrides {@code isAlive()} and answers true while it is
     *         still in the world, which is exactly why it can be pointed at and woken up again.
     */
    @Nullable
    public static Entity entityUnderCrosshair(Player player, double range)
    {
        Level level = player.level();
        Vec3 eyes = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F);
        Vec3 end = eyes.add(direction.scale(range));

        // The creature search stops at the wall the ray hit first, so nothing behind it can ever be chosen.
        BlockHitResult blockHit = level.clip(
                new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS ? range
                : eyes.distanceTo(blockHit.getLocation());

        AABB search = player.getBoundingBox().expandTowards(direction.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyes, end, search,
                candidate -> isProbeTarget(candidate, player), blockDistance * blockDistance);

        return entityHit == null ? null : entityHit.getEntity();
    }

    /** @return true when that being may be pointed at all: a living, pickable creature that is not the player. */
    private static boolean isProbeTarget(Entity candidate, Player player)
    {
        return candidate instanceof LivingEntity && candidate != player && candidate.isAlive()
                && candidate.isPickable() && !candidate.isSpectator();
    }
}
