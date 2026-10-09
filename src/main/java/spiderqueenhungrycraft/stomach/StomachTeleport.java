package spiderqueenhungrycraft.stomach;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;

/**
 * Every cross dimension move of the stomach system goes through here (Phase 8E1).
 *
 * <p>Players are always moved with the vanilla player teleport, never discarded or rebuilt, so their inventory,
 * experience, UUID and connection stay untouched. Ordinary entities are moved with the vanilla
 * {@link Entity#changeDimension(ServerLevel, ITeleporter)} pipeline, which transfers state instead of copying it.
 */
public final class StomachTeleport
{
    /** Depth of the internal moves of this mod, which the protection events must ignore. */
    private static int internalMoves;

    private StomachTeleport()
    {
    }

    /** @return true while this mod is moving something itself, so the protection rules stand back. */
    public static boolean isInternalMove()
    {
        return internalMoves > 0;
    }

    /** Moves a player to a position in another dimension using the vanilla player teleport. */
    public static void movePlayer(ServerPlayer player, ServerLevel destination, double x, double y, double z,
            float yaw, float pitch)
    {
        internalMoves++;

        try
        {
            player.teleportTo(destination, x, y, z, yaw, pitch);
        }
        finally
        {
            internalMoves--;
        }
    }

    /** Moves any living entity, players included, to a position in another dimension. */
    public static void move(LivingEntity entity, ServerLevel destination, double x, double y, double z, float yaw)
    {
        internalMoves++;

        try
        {
            moveInternal(entity, destination, x, y, z, yaw);
        }
        finally
        {
            internalMoves--;
        }
    }

    private static void moveInternal(LivingEntity entity, ServerLevel destination, double x, double y, double z,
            float yaw)
    {
        if (entity instanceof ServerPlayer player)
        {
            player.teleportTo(destination, x, y, z, yaw, player.getXRot());
            return;
        }

        if (entity.level() == destination)
        {
            entity.moveTo(x, y, z, yaw, entity.getXRot());
            entity.setDeltaMovement(Vec3.ZERO);
            return;
        }

        entity.changeDimension(destination, new FixedTeleporter(x, y, z, yaw));
    }

    /** Moves anything - items, orbs, creatures - to a position in another dimension. */
    public static void moveAny(Entity entity, ServerLevel destination, double x, double y, double z, float yaw)
    {
        internalMoves++;

        try
        {
            if (entity.level() == destination)
            {
                entity.moveTo(x, y, z, yaw, entity.getXRot());
                entity.setDeltaMovement(Vec3.ZERO);
                return;
            }

            entity.changeDimension(destination, new FixedTeleporter(x, y, z, yaw));
        }
        finally
        {
            internalMoves--;
        }
    }

    /**
     * Searches a safe landing position around an anchor: two blocks of air with solid ground below.
     *
     * @param fallback the position used when no better spot is found
     */
    public static BlockPos findSafeSpot(ServerLevel level, BlockPos anchor, BlockPos fallback)
    {
        for (int radius = 0; radius <= 3; radius++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius)
                    {
                        continue;
                    }

                    for (int dy = 1; dy >= -2; dy--)
                    {
                        BlockPos candidate = anchor.offset(dx, dy, dz);

                        if (isSafeStand(level, candidate))
                        {
                            return candidate;
                        }
                    }
                }
            }
        }

        return fallback;
    }

    /** @return true when an entity of about two blocks height can stand there. */
    public static boolean isSafeStand(ServerLevel level, BlockPos pos)
    {
        BlockPos floor = pos.below();
        boolean ground = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();

        return ground && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    /** Places an ordinary entity at an exact spot after the vanilla dimension change. */
    private static final class FixedTeleporter implements ITeleporter
    {
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;

        private FixedTeleporter(double x, double y, double z, float yaw)
        {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        @Override
        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float rotation,
                Function<Boolean, Entity> repositionEntity)
        {
            Entity placed = repositionEntity.apply(false);
            placed.moveTo(x, y, z, yaw, placed.getXRot());
            placed.setDeltaMovement(Vec3.ZERO);

            return placed;
        }

        @Override
        public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld,
                Function<ServerLevel, PortalInfo> defaultPortalInfo)
        {
            return new PortalInfo(new Vec3(x, y, z), Vec3.ZERO, yaw, entity.getXRot());
        }
    }
}
