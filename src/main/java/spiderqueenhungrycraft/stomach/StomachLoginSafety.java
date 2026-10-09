package spiderqueenhungrycraft.stomach;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * What happens when somebody logs in inside a stomach (Phase 8E2A).
 *
 * <p>A swallowed guest has to be able to leave the game and come back. Logging in inside the stomach of an online
 * queen whose cell is intact therefore keeps them exactly where they are: the cell is woken up again, the wall is
 * rebuilt and the core is back, so the guest simply continues being digested.
 *
 * <p>Everything else is a rescue. A login inside the stomach dimension without a valid cell - the owner was
 * deleted, the cell index belongs to nobody, the owner is offline - must never drop a player into the void or
 * leave them in an unloaded pocket world, so they are moved to their own respawn point, or to the world spawn of
 * the overworld when they never slept anywhere.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachLoginSafety
{
    private StomachLoginSafety()
    {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            ensureSafeLogin(player);
        }
    }

    /**
     * Brings a logging in player in line with the stomach they are standing in.
     *
     * @return true when the player stays inside a stomach, false when they were rescued to an ordinary world
     */
    public static boolean ensureSafeLogin(ServerPlayer player)
    {
        if (player == null || player.isRemoved() || !(player.level() instanceof ServerLevel level)
                || !level.dimension().equals(StomachCellManager.DIMENSION))
        {
            return false;
        }

        MinecraftServer server = player.getServer();
        StomachCell cell = server == null ? null
                : StomachCellManager.resolveCellAt(level, player.position()).orElse(null);
        ServerPlayer owner = cell == null ? null : StomachOwnership.onlineOwnerOf(server, cell);

        // A valid cell of an online queen: the guest belongs here, so the cell is brought back to life instead
        // of being taken apart around them. The owner herself is allowed here too, that is the debug visit.
        if (owner != null && player.isAlive())
        {
            StomachCellManager.loadCell(level, cell);
            player.fallDistance = 0.0F;
            return true;
        }

        rescue(player);
        return false;
    }

    /** Moves a player who has no business in that stomach to their own respawn location. */
    private static void rescue(ServerPlayer player)
    {
        MinecraftServer server = player.getServer();

        if (server == null)
        {
            return;
        }

        BlockPos respawn = player.getRespawnPosition();
        ServerLevel target = respawn == null ? null : server.getLevel(player.getRespawnDimension());
        BlockPos landing = target == null ? null : StomachTeleport.findSafeSpot(target, respawn, null);

        if (target == null || landing == null)
        {
            target = server.overworld();
            landing = StomachTeleport.findSafeSpot(target, target.getSharedSpawnPos(),
                    target.getSharedSpawnPos());
        }

        if (target == null || landing == null)
        {
            SpiderQueenHungryCraft.LOGGER.error("No rescue destination for {} was found", player.getUUID());
            return;
        }

        StomachTeleport.movePlayer(player, target, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;

        SpiderQueenHungryCraft.LOGGER.info("{} was rescued from an orphaned stomach cell", player.getUUID());
    }
}
