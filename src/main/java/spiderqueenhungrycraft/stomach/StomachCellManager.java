package spiderqueenhungrycraft.stomach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The runtime half of the stomach system (Phase 8E1).
 *
 * <p>Every cell lives in the one shared stomach dimension, the difference is only the cell centre. A cell is
 * loaded while it really holds something: as long as a living entity is inside it, the cell chunk is held by a
 * releasable ticket of this mod. Nobody inside, no ticket - and the cell blocks stay in the save file, so they do
 * not have to be built again.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachCellManager
{
    /** The one dimension that holds every stomach cell. */
    public static final ResourceKey<Level> DIMENSION =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(SpiderQueenHungryCraft.MODID, "stomach"));

    /**
     * The ticket that keeps an occupied cell loaded. It is a runtime only ticket: it is not written to disk, so a
     * server crash can never leave a stomach permanently force loaded.
     */
    private static final TicketType<ChunkPos> CELL_TICKET =
            TicketType.create("spiderqueen_hungrycraft:stomach_cell", Comparator.comparingLong(ChunkPos::toLong));

    /** Ticket radius in chunks, so the cell chunk is fully loaded and entity ticking. */
    private static final int TICKET_DISTANCE = 2;

    /** How often an occupied cell compares itself with its template. */
    private static final int INTEGRITY_INTERVAL_TICKS = 20;

    /** How often an occupied cell emits its sparse ambient particles. */
    private static final int PARTICLE_INTERVAL_TICKS = 10;

    /** Below this Y an entity in the stomach is falling into the void and is rescued. */
    private static final double VOID_RESCUE_Y = -20.0D;

    /** A freshly requested cell is never released within this many ticks, so a teleport cannot race the ticket. */
    private static final int RELEASE_GRACE_TICKS = 60;

    /** Cell index by loaded chunk key: the cells that currently hold a ticket. */
    private static final Map<Long, Integer> LOADED_CELLS = new HashMap<>();

    /** Server tick at which a cell was loaded, used by the release grace period. */
    private static final Map<Long, Long> LOADED_AT = new HashMap<>();

    private static long serverTick;

    private StomachCellManager()
    {
    }

    /** @return the stomach level of that server, or null while the server has no such level. */
    public static ServerLevel stomachLevel(MinecraftServer server)
    {
        return server == null ? null : server.getLevel(DIMENSION);
    }

    /** @return the cell of that owner, allocating a persistent cell when there is none yet. */
    public static StomachCell cellFor(MinecraftServer server, UUID owner)
    {
        return StomachSavedData.get(server).cellFor(owner);
    }

    /** @return the cell of that owner when one was ever allocated. */
    public static Optional<StomachCell> existingCell(MinecraftServer server, UUID owner)
    {
        return StomachSavedData.get(server).existingCell(owner);
    }

    /**
     * Resolves the cell a position inside the stomach belongs to.
     *
     * <p>Cell centres live on a fixed grid, so the candidate index is pure arithmetic and the result only has to
     * be checked against the cell radius and the persistent allocation table. This works for every code path -
     * digestion, death nutrition, the core - without them ever touching coordinates on their own.
     */
    public static Optional<StomachCell> resolveCellAt(ServerLevel level, Vec3 position)
    {
        if (level == null || !level.dimension().equals(DIMENSION))
        {
            return Optional.empty();
        }

        int index = indexAt(position.x, position.z);
        Optional<UUID> owner = StomachSavedData.get(level.getServer()).ownerForIndex(index);

        if (owner.isEmpty())
        {
            return Optional.empty();
        }

        StomachCell cell = new StomachCell(owner.get(), index, StomachCellGeometry.centerFor(index));

        return cell.contains(position.x, position.y, position.z) ? Optional.of(cell) : Optional.empty();
    }

    /** Same as {@link #resolveCellAt} but for an entity, which is the ordinary entry point of every system. */
    public static Optional<StomachCell> resolveCell(Entity entity)
    {
        if (!(entity.level() instanceof ServerLevel level))
        {
            return Optional.empty();
        }

        return resolveCellAt(level, entity.position());
    }

    /** @return the allocation index of the cell that contains that horizontal position. */
    private static int indexAt(double x, double z)
    {
        int column = (int) Math.round((x - 8.0D) / StomachCellGeometry.CELL_SPACING);
        int row = (int) Math.round((z - 8.0D) / StomachCellGeometry.CELL_SPACING);

        return row * 16 + column;
    }

    // ------------------------------------------------------------------ tickets

    /** Keeps that cell loaded and makes sure its template is intact. */
    public static void loadCell(ServerLevel level, StomachCell cell)
    {
        ChunkPos pos = cell.chunkPos();
        long key = pos.toLong();

        if (!LOADED_CELLS.containsKey(key))
        {
            LOADED_CELLS.put(key, cell.index());
            level.getChunkSource().addRegionTicket(CELL_TICKET, pos, TICKET_DISTANCE, pos);
        }

        LOADED_AT.put(key, serverTick);
        level.getChunk(pos.x, pos.z);
        ensureBuilt(level, cell);

        // The wall of the cell is born with the cell, so an occupied cell is never without its core.
        ensureCore(level, cell);
    }

    /** Drops the ticket of that cell and removes its core; the cell blocks themselves stay in the save file. */
    public static void unloadCell(ServerLevel level, StomachCell cell)
    {
        // A core is not an occupant, so it would linger forever in a closed cell. It dies with its cell instead,
        // which is also why a core can never keep a cell loaded.
        for (StomachCoreEntity core : level.getEntitiesOfClass(StomachCoreEntity.class, cellBox(cell)))
        {
            core.discard();
        }

        ChunkPos pos = cell.chunkPos();
        long key = pos.toLong();

        if (LOADED_CELLS.remove(key) != null)
        {
            level.getChunkSource().removeRegionTicket(CELL_TICKET, pos, TICKET_DISTANCE, pos);
        }

        LOADED_AT.remove(key);
    }

    /** @return true when that cell currently holds a ticket. */
    public static boolean isLoaded(StomachCell cell)
    {
        return LOADED_CELLS.containsKey(cell.chunkPos().toLong());
    }

    /** @return how many cells are currently kept loaded; used by the debug output. */
    public static int loadedCellCount()
    {
        return LOADED_CELLS.size();
    }

    /** Builds the cell when its template is missing, and repairs it when it was damaged. */
    public static void ensureBuilt(ServerLevel level, StomachCell cell)
    {
        BlockPos center = cell.center();
        boolean intact = level.getBlockState(center).isAir()
                && level.getBlockState(center.offset(0, StomachCellGeometry.OUTER_RADIUS, 0))
                        .is(spiderqueenhungrycraft.registry.HungryCraftBlocks.STOMACH_WALL.get())
                && level.getBlockState(center.offset(0, -StomachCellGeometry.INNER_RADIUS + 1, 0)).getFluidState()
                        .getType() == net.minecraft.world.level.material.Fluids.WATER;

        if (!intact)
        {
            StomachCellGeometry.build(level, center);
        }
    }

    /** Compares a cell with its template and restores every block that does not match. */
    public static int repairCell(ServerLevel level, StomachCell cell)
    {
        return StomachCellGeometry.repair(level, cell.center());
    }

    // ------------------------------------------------------------------ occupants

    /**
     * @return every living entity that really is inside that cell.
     *
     * <p>The core of the cell is deliberately not an occupant: it is the wall, not something inside the stomach.
     * Without that filter a core would keep its own cell loaded forever, count as food and be punished by itself.
     */
    public static List<LivingEntity> occupants(ServerLevel level, StomachCell cell)
    {
        List<LivingEntity> found = new ArrayList<>();

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, cellBox(cell)))
        {
            if (entity instanceof StomachCoreEntity)
            {
                continue;
            }

            double middle = entity.getY() + entity.getBbHeight() * 0.5D;

            if (cell.contains(entity.getX(), middle, entity.getZ()))
            {
                found.add(entity);
            }
        }

        return found;
    }

    // ------------------------------------------------------------------ core

    /** @return the core of that cell, when it currently exists and is healthy. */
    public static Optional<StomachCoreEntity> coreOf(ServerLevel level, StomachCell cell)
    {
        for (StomachCoreEntity core : level.getEntitiesOfClass(StomachCoreEntity.class, cellBox(cell)))
        {
            if (!core.isRemoved() && core.isAlive() && core.belongsTo(cell))
            {
                return Optional.of(core);
            }
        }

        return Optional.empty();
    }

    /**
     * Makes sure that a loaded cell owns exactly one living core.
     *
     * <p>The core is created when it is missing - which also covers a core that died or was removed by a command -
     * it is re-anchored when it exists, and every duplicate or foreign core inside the same cell is removed. A
     * core is never an occupant, so this method can never keep a cell loaded.
     */
    public static void ensureCore(ServerLevel level, StomachCell cell)
    {
        StomachCoreEntity valid = null;

        for (StomachCoreEntity core : level.getEntitiesOfClass(StomachCoreEntity.class, cellBox(cell)))
        {
            if (core.isRemoved() || !core.isAlive() || !core.belongsTo(cell) || valid != null)
            {
                core.discard();
                continue;
            }

            valid = core;
        }

        if (valid == null)
        {
            level.addFreshEntity(StomachCoreEntity.forCell(level, cell));
        }
        else
        {
            valid.placeAtCell(cell);
        }
    }

    /** @return the search box of one cell, one block wider than its wall shell. */
    private static AABB cellBox(StomachCell cell)
    {
        BlockPos center = cell.center();
        double radius = StomachCellGeometry.OUTER_RADIUS + 1.0D;

        return new AABB(center.getX() + 0.5D - radius, center.getY() + 0.5D - radius,
                center.getZ() + 0.5D - radius, center.getX() + 0.5D + radius, center.getY() + 0.5D + radius,
                center.getZ() + 0.5D + radius);
    }

    // ------------------------------------------------------------------ debug travel

    /**
     * Sends a player into the stomach of another player (or their own).
     *
     * <p>A visitor is an ordinary occupant: no immunity, no special case. The cell is built, kept loaded and the
     * visitor is placed in the middle of the cavity, above the water.
     *
     * @return false when the stomach level is not available
     */
    public static boolean visit(ServerPlayer actor, UUID owner)
    {
        MinecraftServer server = actor.getServer();
        ServerLevel level = stomachLevel(server);

        if (level == null)
        {
            return false;
        }

        StomachCell cell = cellFor(server, owner);
        loadCell(level, cell);
        BlockPos spawn = cell.spawnPos();

        StomachTeleport.movePlayer(actor, level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                actor.getYRot(), actor.getXRot());

        return true;
    }

    /**
     * Sends a player back to the safe position they had before they entered a stomach.
     *
     * @return false when the player is not inside a stomach
     */
    public static boolean leave(ServerPlayer actor)
    {
        if (!actor.level().dimension().equals(DIMENSION))
        {
            return false;
        }

        MinecraftServer server = actor.getServer();
        StomachSavedData.Anchor anchor = StomachSavedData.get(server).anchorOf(actor.getUUID()).orElse(null);
        ServerLevel target = anchor == null ? null : server.getLevel(anchor.dimension());
        BlockPos landing = target == null ? null : StomachTeleport.findSafeSpot(target, anchor.blockPos(), null);

        if (target == null || landing == null)
        {
            target = server.overworld();
            landing = target.getSharedSpawnPos();
        }

        StomachTeleport.movePlayer(actor, target, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                actor.getYRot(), actor.getXRot());

        return true;
    }

    // ------------------------------------------------------------------ server tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();

        if (server == null)
        {
            return;
        }

        serverTick++;

        if (serverTick % 20L == 0L)
        {
            trackAnchors(server);
            rescueVoid(server);
        }

        if (serverTick % 10L != 0L)
        {
            return;
        }

        ServerLevel level = stomachLevel(server);

        if (level == null)
        {
            LOADED_CELLS.clear();
            LOADED_AT.clear();
            return;
        }

        StomachSavedData data = StomachSavedData.get(server);

        for (long key : new ArrayList<>(LOADED_CELLS.keySet()))
        {
            Integer index = LOADED_CELLS.get(key);

            if (index == null)
            {
                continue;
            }

            Optional<UUID> owner = data.ownerForIndex(index);

            if (owner.isEmpty())
            {
                LOADED_CELLS.remove(key);
                LOADED_AT.remove(key);
                continue;
            }

            tickCell(level, new StomachCell(owner.get(), index, StomachCellGeometry.centerFor(index)));
        }
    }

    /**
     * @return everything that counts as the content of that cell: the living guests, the items and the
     *         experience orbs inside it. The core is never content.
     */
    public static List<Entity> contents(ServerLevel level, StomachCell cell)
    {
        return StomachContents.collect(level, cell);
    }

    /** Closes a cell completely: its core, its ticket and the status panel of its owner, all at once. */
    public static void closeCell(ServerLevel level, StomachCell cell)
    {
        unloadCell(level, cell);
        StomachHudBroadcast.clear(level.getServer(), cell.owner());
    }

    /**
     * The lifecycle of one loaded cell: it is kept only while it really holds a living entity, and while it is
     * populated it repairs itself and emits its sparse ambience.
     */
    private static void tickCell(ServerLevel level, StomachCell cell)
    {
        StomachHudBroadcast.send(level, cell);

        long key = cell.chunkPos().toLong();

        if (StomachContents.isEmpty(level, cell))
        {
            if (serverTick - LOADED_AT.getOrDefault(key, serverTick) > RELEASE_GRACE_TICKS)
            {
                unloadCell(level, cell);
            }

            return;
        }

        if (serverTick % INTEGRITY_INTERVAL_TICKS == 0L)
        {
            repairCell(level, cell);
        }

        if (serverTick % PARTICLE_INTERVAL_TICKS == 0L)
        {
            StomachParticles.ambient(level, cell);
        }

        // The wall has to be alive as long as the cell is: a core that was killed, removed or never created
        // is back within a second, and duplicates are cleaned up on the way.
        if (serverTick % StomachConstants.CORE_ENSURE_INTERVAL_TICKS == 0L)
        {
            ensureCore(level, cell);
        }
    }

    /** Remembers where every online player stands while they are not inside a stomach. */
    private static void trackAnchors(MinecraftServer server)
    {
        StomachSavedData data = StomachSavedData.get(server);

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (!player.level().dimension().equals(DIMENSION))
            {
                data.updateAnchor(player.getUUID(), player.level().dimension(), player.getX(), player.getY(),
                        player.getZ(), player.getYRot(), player.getXRot());
            }
        }
    }

    /** Sends anything that falls out of a cell back to the overworld instead of losing it in the void. */
    private static void rescueVoid(MinecraftServer server)
    {
        ServerLevel level = stomachLevel(server);

        if (level == null)
        {
            return;
        }

        List<LivingEntity> falling = new ArrayList<>();

        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof LivingEntity living && living.getY() < VOID_RESCUE_Y && resolveCell(living).isEmpty())
            {
                falling.add(living);
            }
        }

        for (LivingEntity living : falling)
        {
            ServerLevel overworld = server.overworld();
            BlockPos spawn = overworld.getSharedSpawnPos();
            StomachTeleport.move(living, overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    living.getYRot());
        }
    }
}
