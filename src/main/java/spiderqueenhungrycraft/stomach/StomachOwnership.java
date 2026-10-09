package spiderqueenhungrycraft.stomach;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The one place that answers who owns a cell and whether a core really is that cell (Phase 8E1-GATE2B).
 *
 * <p>The identity of a stomach is its owner UUID and nothing else: no nearest player, no nearest core and no
 * distance ranking. A core may only act for its owner when the whole chain is consistent - the saved data knows
 * that owner under exactly that cell index, the cell index belongs back to that owner, and the core is the one
 * canonical core of that cell. Anything less is refused, so two stomachs can never be confused with each other.
 */
public final class StomachOwnership
{
    private StomachOwnership()
    {
    }

    /** @return the cell of that owner when one was allocated, without allocating a new one. */
    public static Optional<StomachCell> allocatedCellOf(MinecraftServer server, UUID owner)
    {
        return server == null ? Optional.empty() : StomachSavedData.get(server).existingCell(owner);
    }

    /**
     * @return the canonical cell of that core, or an empty result when even one link of the identity chain is
     *         broken. The core must be the one and only core of that cell.
     */
    public static Optional<StomachCell> canonicalCellOf(ServerLevel level, StomachCoreEntity core)
    {
        if (level == null || core == null || core.isRemoved() || !core.isAlive())
        {
            return Optional.empty();
        }

        UUID owner = core.ownerUUID();
        int index = core.cellIndex();

        if (owner == null || index < 0)
        {
            return Optional.empty();
        }

        StomachSavedData data = StomachSavedData.get(level.getServer());
        Optional<StomachCell> allocated = data.existingCell(owner);

        if (allocated.isEmpty() || allocated.get().index() != index)
        {
            return Optional.empty();
        }

        if (!Optional.of(owner).equals(data.ownerForIndex(index)))
        {
            return Optional.empty();
        }

        StomachCell cell = new StomachCell(owner, index, StomachCellGeometry.centerFor(index));

        // A duplicate or foreign core is never the canonical one, whatever its own fields claim.
        return StomachCellManager.coreOf(level, cell).filter(found -> found == core).map(found -> cell);
    }

    /** @return the online player that owns that cell, or null while the owner is away. */
    @Nullable
    public static ServerPlayer onlineOwnerOf(MinecraftServer server, StomachCell cell)
    {
        return server == null ? null : server.getPlayerList().getPlayer(cell.owner());
    }

    /**
     * @return the player that has to feel the damage of that core: only a canonical core of a cell whose owner is
     *         online, alive and not spectating. Everything else is nobody, never somebody else.
     */
    public static Optional<ServerPlayer> damageTargetOf(ServerLevel level, StomachCoreEntity core)
    {
        ServerPlayer owner = canonicalCellOf(level, core)
                .map(cell -> onlineOwnerOf(level.getServer(), cell)).orElse(null);

        if (owner == null || owner.isRemoved() || !owner.isAlive() || owner.isSpectator())
        {
            return Optional.empty();
        }

        return Optional.of(owner);
    }
}
