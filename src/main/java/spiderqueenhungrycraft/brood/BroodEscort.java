package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.ITeleporter;
import spiderqueenhungrycraft.brood.goal.SpiderFollowOwnerGoal;
import spiderqueenhungrycraft.player.FollowRoster;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * Escort relocation: the followers of a queen who travelled far come along (Brood Selection + Escort Relocation round).
 *
 * <p>A spider can only teleport itself while its chunk is loaded and ticking. When the queen is moved a thousand blocks
 * by a command, or steps through a portal, her escort may be left in a chunk that unloads a moment later - and a spider
 * that does not tick can never notice that it is lost. That is what this class is for: the server, which watches the
 * queen and already knows where each follower was last seen (see {@link FollowRoster}), moves them itself.
 *
 * <p>Only followers travel. A guarding or patrolling spider stays exactly where it was told to stand, whatever the queen
 * does - the whole point of a garrison is that it does not wander off with her.
 *
 * <p>Nothing is ever duplicated: a follower that is still loaded is moved as it is, and one from an unloaded chunk is
 * found again by loading its last known chunk for a moment (with a ticket that is always released again). If a spider
 * cannot be found even then, its roster entry is dropped - a replacement is never spawned.
 */
public final class BroodEscort
{
    /** A queen who moved further than this in a single tick, or changed dimension, is travelling rather than walking. */
    public static final double LONG_RELOCATION_THRESHOLD = 64.0D;

    /** The ticket level of the temporary load: small and short lived, and released in the same breath. */
    private static final int RECOVERY_TICKET_LEVEL = 2;

    /** How many spots are tried around the queen before she is used as the last resort. */
    private static final int PLACEMENT_ATTEMPTS = 12;

    private BroodEscort()
    {
    }

    /**
     * @return true when that queen travelled rather than walked since she was last seen: she changed dimension, or she
     *         is further than {@link #LONG_RELOCATION_THRESHOLD} blocks away from where she stood a tick ago.
     *
     * <p>Walking, sprinting, falling and knockback never move a player that far in one tick, so this only fires for a
     * command teleport, a portal or a mod that moved her with its own API.
     */
    public static boolean isLongRelocation(@Nullable ResourceKey<Level> previousDimension, double previousX,
            double previousY, double previousZ, ServerPlayer queen)
    {
        if (previousDimension == null || !previousDimension.equals(queen.level().dimension()))
        {
            return true;
        }

        double dx = previousX - queen.getX();
        double dy = previousY - queen.getY();
        double dz = previousZ - queen.getZ();

        return dx * dx + dy * dy + dz * dz >= LONG_RELOCATION_THRESHOLD * LONG_RELOCATION_THRESHOLD;
    }

    /**
     * Moves the whole escort of that queen to wherever she is now.
     *
     * <p>It is a strategic move and not a battle order: the errand and the victim of every follower are dropped first,
     * while the order itself stays FOLLOW. A queen who travels wants her escort with her, not a spider that is still
     * chasing something a thousand blocks away.
     */
    public static int relocate(ServerPlayer queen)
    {
        return relocate(queen, queen.level() instanceof ServerLevel level ? level : null);
    }

    /**
     * The same move into an explicit destination, which is the world the queen moved into.
     *
     * <p>It exists so the whole crossing can be exercised without a real player entity travelling, and it keeps the rule
     * of the class: only followers are moved, everything else about a spider is kept.
     */
    public static int relocate(ServerPlayer queen, @Nullable ServerLevel destination)
    {
        if (destination == null || queen.getServer() == null)
        {
            return 0;
        }

        FollowRoster roster = QueenDataCapability.get(queen).map(data -> data.followRoster()).orElse(null);

        if (roster == null || roster.size() == 0)
        {
            return 0;
        }

        Map<UUID, FollowRoster.Entry> followers = new LinkedHashMap<>();

        for (FollowRoster.Entry entry : roster.entries())
        {
            followers.put(entry.spider(), entry);
        }

        Map<Long, Recovery> recoveries = new LinkedHashMap<>();
        List<UUID> stale = new ArrayList<>();
        int moved = 0;

        try
        {
            for (FollowRoster.Entry entry : followers.values())
            {
                SpiderMinionEntity spider = loadFor(queen, entry, recoveries);

                if (spider == null)
                {
                    // A spider that cannot be found anywhere is not replaced: its entry is simply forgotten.
                    stale.add(entry.spider());
                    continue;
                }

                if (moveToQueen(spider, queen, destination))
                {
                    moved++;
                }
            }
        }
        finally
        {
            for (Recovery recovery : recoveries.values())
            {
                recovery.release();
            }
        }

        for (UUID spider : stale)
        {
            roster.dropStale(spider);
        }

        return moved;
    }

    /**
     * @return the follower of that roster entry, ready to be moved, or null when it cannot be found at all.
     *
     * <p>A loaded spider is used as it is. When it is not loaded, its last known chunk is loaded for the duration of
     * this relocation - with a region ticket that is released again at the very end of the move, so the world never ends
     * up with a permanently forced chunk. One chunk is loaded once, however many spiders it holds.
     */
    @Nullable
    private static SpiderMinionEntity loadFor(ServerPlayer queen, FollowRoster.Entry entry, Map<Long, Recovery> recoveries)
    {
        ResourceKey<Level> dimension = FollowRoster.dimensionKey(entry.dimension());
        ServerLevel level = dimension == null || queen.getServer() == null ? null : queen.getServer().getLevel(dimension);

        if (level == null)
        {
            return null;
        }

        SpiderMinionEntity loaded = follower(level.getEntity(entry.spider()), queen);

        if (loaded != null)
        {
            return loaded;
        }

        ChunkPos chunk = entry.chunkPos();

        if (chunk == null)
        {
            return null;
        }

        long key = chunk.toLong() ^ ((long) level.dimension().location().hashCode() << 32);

        if (!recoveries.containsKey(key))
        {
            recoveries.put(key, new Recovery(level, chunk));
        }

        // The chunk is loaded, so the spider inside it is created and can be found by its UUID.
        level.getChunk(chunk.x, chunk.z);

        return follower(level.getEntity(entry.spider()), queen);
    }

    /** @return that entity as a follower of this queen, or null when it is something else or no longer hers. */
    @Nullable
    private static SpiderMinionEntity follower(@Nullable Entity entity, ServerPlayer queen)
    {
        return entity instanceof SpiderMinionEntity spider && spider.isOwnedBy(queen)
                && spider.getOrderMode() == SpiderOrderMode.FOLLOW ? spider : null;
    }

    /**
     * @return true when that follower really arrived next to its queen.
     *
     * <p>In the very same world it is a safe teleport, decided by the one placement rule the brood has (the follow goal
     * itself). Into another world the game's own dimension change is used, which moves the entity and keeps everything
     * about it - UUID, life, name, owner, ability rest - and never creates a second spider.
     */
    private static boolean moveToQueen(SpiderMinionEntity spider, ServerPlayer queen, ServerLevel destination)
    {
        // A travelling queen wants her escort with her, so the errand of every follower ends here.
        spider.clearTemporaryCommand();
        spider.getNavigation().stop();
        spider.setTarget(null);

        if (spider.level() != destination)
        {
            BlockPos spot = placement(destination, queen);
            Entity moved = spider.changeDimension(destination, new EscortTeleporter(spot, queen));

            if (moved instanceof SpiderMinionEntity relocated)
            {
                relocated.clearTemporaryCommand();
                relocated.setOrderMode(SpiderOrderMode.FOLLOW);

                return true;
            }

            return false;
        }

        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++)
        {
            BlockPos spot = SpiderFollowOwnerGoal.findSafeSpot(destination, queen.blockPosition());

            if (spot != null && spider.randomTeleport(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, true))
            {
                return true;
            }
        }

        return false;
    }

    /** @return a safe spot near the queen in that level, or her own block when the world offers nowhere else. */
    private static BlockPos placement(ServerLevel level, ServerPlayer queen)
    {
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++)
        {
            BlockPos spot = SpiderFollowOwnerGoal.findSafeSpot(level, queen.blockPosition());

            if (spot != null)
            {
                return spot;
            }
        }

        return queen.blockPosition();
    }

    /** One chunk that was loaded for this relocation, and released again in the end. */
    private record Recovery(ServerLevel level, ChunkPos chunk)
    {
        private Recovery
        {
            level.getChunkSource().addRegionTicket(TicketType.UNKNOWN, chunk, RECOVERY_TICKET_LEVEL, chunk);
        }

        private void release()
        {
            this.level.getChunkSource().removeRegionTicket(TicketType.UNKNOWN, this.chunk, RECOVERY_TICKET_LEVEL,
                    this.chunk);
        }
    }

    /** Places a relocated spider where the escort decided, instead of at a portal the game would build for it. */
    private static final class EscortTeleporter implements ITeleporter
    {
        private final BlockPos spot;
        private final ServerPlayer queen;

        private EscortTeleporter(BlockPos spot, ServerPlayer queen)
        {
            this.spot = spot;
            this.queen = queen;
        }

        @Override
        public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw,
                Function<Boolean, Entity> repositionEntity)
        {
            Entity moved = repositionEntity.apply(false);

            if (this.spot != null)
            {
                moved.moveTo(this.spot.getX() + 0.5D, this.spot.getY(), this.spot.getZ() + 0.5D, yaw, moved.getXRot());
            }
            else
            {
                moved.moveTo(this.queen.getX(), this.queen.getY(), this.queen.getZ(), yaw, moved.getXRot());
            }

            return moved;
        }
    }
}
