package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * The command ray of the queen sceptre (Queen Scepter command patch).
 *
 * <p>The sceptre is a long range command tool, so nothing about it uses the ordinary interaction reach of the game: the
 * ray starts at the eyes of the queen, goes {@link #RANGE} blocks along her view and reports whatever it meets first -
 * a creature, a block or nothing.
 *
 * <p><b>Nearest hit wins.</b> The creature search is limited to the distance of the block hit, so a creature behind a
 * wall is never picked: if the wall is closer, the wall is the answer. The same helper answers on the client (for the
 * crosshair preview) and on the server (which never trusts the client and simply casts again), so both sides always
 * mean the same thing by "what the queen is pointing at".
 */
public final class QueenCommandRay
{
    /** How far the sceptre commands: 64 blocks, exactly as far as the ray itself. */
    public static final double RANGE = 64.0D;

    /** What the ray met. */
    public enum Kind
    {
        /** Nothing within range. */
        NONE,
        /** A creature. */
        ENTITY,
        /** A block face. */
        BLOCK
    }

    /** The answer of one cast: what was hit, where and how far away it was. */
    public record Hit(Kind kind, @Nullable LivingEntity entity, @Nullable BlockPos blockPos, @Nullable Vec3 location,
            @Nullable Direction face, double distance)
    {
        /** @return the ray that found nothing. */
        public static Hit none()
        {
            return new Hit(Kind.NONE, null, null, null, null, RANGE);
        }

        /** @return true when a creature was hit. */
        public boolean hasEntity()
        {
            return this.kind == Kind.ENTITY && this.entity != null;
        }

        /** @return true when a block face was hit, but no creature in front of it. */
        public boolean hasBlock()
        {
            return this.kind == Kind.BLOCK && this.blockPos != null;
        }
    }

    private QueenCommandRay()
    {
    }

    /**
     * Casts the command ray of that player.
     *
     * <p>It is safe on both sides: the client uses it for the hover preview, the server uses it as the only truth about
     * what a command means.
     */
    public static Hit cast(Player player)
    {
        Level level = player.level();
        Vec3 eyes = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F);
        Vec3 end = eyes.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(
                new ClipContext(eyes, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS ? RANGE
                : eyes.distanceTo(blockHit.getLocation());

        // The creature search stops at the wall the ray hit first, so nothing behind it can ever be chosen.
        AABB search = player.getBoundingBox().expandTowards(direction.scale(RANGE)).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, eyes, end, search,
                candidate -> isCommandTarget(candidate, player), blockDistance * blockDistance);

        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity living)
        {
            return new Hit(Kind.ENTITY, living, null, null, null, eyes.distanceTo(entityHit.getLocation()));
        }

        if (blockHit.getType() != HitResult.Type.MISS)
        {
            return new Hit(Kind.BLOCK, null, blockHit.getBlockPos(), blockHit.getLocation(), blockHit.getDirection(),
                    blockDistance);
        }

        return Hit.none();
    }

    /**
     * @return the point the crosshair of that player indicates right now: where its ray met the world, the middle of
     *         the creature it met, or a point far along the line of sight when it met nothing at all.
     *
     * <p>It is the very ray every command of the sceptre is decided with, so a client and a server always mean the same
     * point by "where the queen is pointing" - which is what lets the client send the point of its press and be
     * understood, and what lets the server cast the point of the release itself instead of believing one.
     */
    public static Vec3 cursorPoint(Player player)
    {
        return cursorPoint(player, cast(player));
    }

    /** The same answer for a ray that was already cast, so nothing casts twice for one question. */
    public static Vec3 cursorPoint(Player player, Hit hit)
    {
        if (hit.hasEntity() && hit.entity() != null)
        {
            LivingEntity entity = hit.entity();

            return new Vec3(entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ());
        }

        if (hit.location() != null)
        {
            return hit.location();
        }

        // Nothing was met at all: the point of the crosshair is the far end of its own ray, so an aim into the sky is
        // still a position and not a hole in the data.
        return player.getEyePosition().add(player.getViewVector(1.0F).scale(RANGE));
    }

    /** @return true when that creature may be pointed at all: a living, pickable creature that is not a spectator. */
    private static boolean isCommandTarget(Entity candidate, Player queen)
    {
        return candidate instanceof LivingEntity && candidate != queen && candidate.isAlive()
                && candidate.isPickable() && !candidate.isSpectator();
    }
}
