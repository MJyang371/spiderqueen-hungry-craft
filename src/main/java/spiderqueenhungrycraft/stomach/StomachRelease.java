package spiderqueenhungrycraft.stomach;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * What happens to a stomach when its owner dies (Phase 8E1-GATE2B).
 *
 * <p>A stomach cannot outlive its queen: whatever was inside her is released where she died, in every dimension and
 * for every possible cause of death. The rule is never broken by the way she died - a mob, another player, lava,
 * the void, a command or her own digestion all close the cell the same way.
 *
 * <p>The one exception is a queen who dies inside her own stomach: there is no outside to release to, so the cell
 * is emptied instead - creatures and items are gone, guests are sent home, and the queen herself is left to the
 * ordinary respawn of the game.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachRelease
{
    /** Owners whose death was already handled; cleared again when they respawn. */
    private static final Set<UUID> RELEASED = new HashSet<>();

    /** The spreading of released things around the place of death, in blocks. */
    private static final double SPREAD_RADIUS = 1.5D;

    private StomachRelease()
    {
    }

    /** Forgets the death of that owner, so their next death releases their stomach again. */
    public static void onRespawn(UUID owner)
    {
        RELEASED.remove(owner);
    }

    /** A respawned queen owns a stomach again, so her next death is worth a release as well. */
    /** The queen leaves the server: her stomach is opened before she is gone, so nobody stays captured. */
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer owner)
        {
            onOwnerLogout(owner);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        onRespawn(event.getEntity().getUUID());
    }

    /**
     * The safety net of a queen who leaves the server (Phase 8E2A).
     *
     * <p>A stomach cannot be a prison: as long as a swallowed player can log out while somebody else is inside
     * her, the owner leaving the game has to open the cell before they are gone. Everything inside is released
     * at the place the queen stands - or at the anchor she last stood at while she was outside, when she logs out
     * inside her own stomach - and the cell is closed afterwards.
     *
     * <p>This is a safety release, not a vomit: no animation, no llama spit, no fluid. Nobody is watching it,
     * and the only promise that matters is that no guest stays behind in an unloaded world.
     */
    public static void onOwnerLogout(ServerPlayer owner)
    {
        if (owner == null || owner.getServer() == null)
        {
            return;
        }

        StomachCell cell = StomachOwnership.allocatedCellOf(owner.getServer(), owner.getUUID()).orElse(null);
        ServerLevel stomach = StomachCellManager.stomachLevel(owner.getServer());

        if (cell == null || stomach == null || !StomachCellManager.isLoaded(cell)
                || StomachContents.isEmpty(stomach, cell))
        {
            return;
        }

        StomachSavedData.Anchor anchor = StomachSavedData.get(owner.getServer()).anchorOf(owner.getUUID())
                .orElse(null);
        ServerLevel destination = anchor == null ? null : owner.getServer().getLevel(anchor.dimension());

        if (destination == null)
        {
            destination = owner.getServer().overworld();
            anchor = null;
        }

        // A queen who logs out inside her own stomach has no place of her own to spit into, so the last place
        // she really stood outside is used instead.
        double x = anchor != null && owner.level().dimension().equals(StomachCellManager.DIMENSION)
                ? anchor.x() : owner.getX();
        double y = anchor != null && owner.level().dimension().equals(StomachCellManager.DIMENSION)
                ? anchor.y() : owner.getY();
        double z = anchor != null && owner.level().dimension().equals(StomachCellManager.DIMENSION)
                ? anchor.z() : owner.getZ();

        releaseInto(destination, stomach, cell, BlockPos.containing(x, y, z), owner.getYRot(), owner.getXRot());
        StomachCellManager.closeCell(stomach, cell);

        SpiderQueenHungryCraft.LOGGER.info("The stomach of {} was released because its owner logged out",
                owner.getUUID());
    }

    /** Releases the stomach of a dying owner, exactly once per death. */
    public static void onOwnerDeath(LivingEntity victim)
    {
        if (!(victim instanceof ServerPlayer owner) || !(owner.level() instanceof ServerLevel deathLevel)
                || !RELEASED.add(owner.getUUID()))
        {
            return;
        }

        StomachCell cell = StomachOwnership.allocatedCellOf(owner.getServer(), owner.getUUID()).orElse(null);
        ServerLevel stomach = StomachCellManager.stomachLevel(owner.getServer());

        if (cell == null || stomach == null)
        {
            return;
        }

        // The place of death, exactly as the queen left it.
        BlockPos anchor = BlockPos.containing(owner.getX(), owner.getY(), owner.getZ());

        if (deathLevel.dimension().equals(StomachCellManager.DIMENSION)
                && cell.contains(owner.getX(), owner.getY(), owner.getZ()))
        {
            emptyOwnCell(stomach, cell, owner);
        }
        else
        {
            releaseInto(deathLevel, stomach, cell, anchor, owner.getYRot(), owner.getXRot());
        }

        StomachCellManager.closeCell(stomach, cell);
    }

    /** Moves everything of that cell to the place where its owner died. */
    private static void releaseInto(ServerLevel deathLevel, ServerLevel stomach, StomachCell cell, BlockPos anchor,
            float yaw, float pitch)
    {
        int index = 0;

        for (Entity entity : contentsOf(stomach, cell))
        {
            double[] offset = spread(index++);
            double x = anchor.getX() + 0.5D + offset[0];
            double y = anchor.getY() + offset[1];
            double z = anchor.getZ() + 0.5D + offset[2];

            if (entity instanceof ServerPlayer player)
            {
                StomachTeleport.movePlayer(player, deathLevel, x, y, z, yaw, pitch);
            }
            else
            {
                StomachTeleport.moveAny(entity, deathLevel, x, y, z, yaw);
            }
        }
    }

    /** The emergency case: the queen died inside her own stomach, so there is nothing to release to. */
    private static void emptyOwnCell(ServerLevel stomach, StomachCell cell, ServerPlayer owner)
    {
        for (Entity entity : contentsOf(stomach, cell))
        {
            if (entity instanceof ServerPlayer guest)
            {
                StomachCellManager.leave(guest);
            }
            else
            {
                entity.discard();
            }
        }

        // The queen herself is never touched: the ordinary respawn of the game takes her.
    }

    /** @return everything that is inside that cell, the core itself excluded. */
    private static List<Entity> contentsOf(ServerLevel stomach, StomachCell cell)
    {
        AABB box = cellBox(cell);
        List<Entity> found = new ArrayList<>();

        for (Entity entity : stomach.getAllEntities())
        {
            if (entity instanceof StomachCoreEntity || entity.isRemoved())
            {
                continue;
            }

            if (box.intersects(entity.getBoundingBox()) && cell.contains(entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ()))
            {
                found.add(entity);
            }
        }

        return found;
    }

    private static AABB cellBox(StomachCell cell)
    {
        BlockPos center = cell.center();
        double radius = StomachCellGeometry.OUTER_RADIUS + 1.0D;

        return new AABB(center.getX() + 0.5D - radius, center.getY() + 0.5D - radius,
                center.getZ() + 0.5D - radius, center.getX() + 0.5D + radius, center.getY() + 0.5D + radius,
                center.getZ() + 0.5D + radius);
    }

    /** @return a deterministic place around the anchor, so a dozen things do not end up in one block. */
    private static double[] spread(int index)
    {
        if (index == 0)
        {
            return new double[] { 0.0D, 0.0D, 0.0D };
        }

        double angle = index * 2.399963229728653D;
        double radius = SPREAD_RADIUS * Math.sqrt((double) (index % 9) / 9.0D);

        return new double[] { Math.cos(angle) * radius, 0.2D * (index % 3), Math.sin(angle) * radius };
    }
}
