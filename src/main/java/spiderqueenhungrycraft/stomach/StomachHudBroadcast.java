package spiderqueenhungrycraft.stomach;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CStomachStatusPacket;

/**
 * The status feed of the stomach system (Phase 8E1-GATE2B).
 *
 * <p>Every cell reports itself to its own owner and to nobody else: the client of the owner can never see the
 * stomach dimension, so the little status panel is fed from here. The sequence number grows with every snapshot, so
 * a packet that arrives late can never undo a newer state, and closing a cell clears the panel at once instead of
 * waiting for the next snapshot.
 */
public final class StomachHudBroadcast
{
    /** A snapshot is about half a second. */
    public static final int SNAPSHOT_INTERVAL_TICKS = 10;

    /** The most guests one snapshot may carry, so an extreme cell cannot build a huge packet. */
    public static final int MAX_OCCUPANTS = 64;

    private static final java.util.Map<UUID, Integer> SEQUENCES = new java.util.HashMap<>();

    private StomachHudBroadcast()
    {
    }

    /** Sends the current state of that cell to its owner. */
    public static void send(ServerLevel level, StomachCell cell)
    {
        ServerPlayer owner = StomachOwnership.onlineOwnerOf(level.getServer(), cell);

        if (owner == null)
        {
            return;
        }

        send(owner, cell, true, occupantsOf(level, cell));
    }

    /** Tells the owner of that cell that nothing is left of it. */
    public static void clear(MinecraftServer server, UUID owner)
    {
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(owner);

        if (player != null)
        {
            StomachCell cell = StomachOwnership.allocatedCellOf(server, owner).orElse(null);

            send(player, cell, false, List.of());
        }
    }

    private static void send(ServerPlayer owner, StomachCell cell, boolean loaded,
            List<S2CStomachStatusPacket.Occupant> occupants)
    {
        int sequence = SEQUENCES.merge(owner.getUUID(), 1, Integer::sum);

        HungryCraftNetwork.sendToPlayer(
                new S2CStomachStatusPacket(owner.getUUID(), cell == null ? -1 : cell.index(), sequence, loaded,
                        occupants),
                owner);
    }

    /** @return the guests of that cell, in a stable order, the core and every item excluded. */
    private static List<S2CStomachStatusPacket.Occupant> occupantsOf(ServerLevel level, StomachCell cell)
    {
        List<LivingEntity> guests = new ArrayList<>(StomachCellManager.occupants(level, cell));
        guests.sort(Comparator.comparing(entity -> entity.getUUID().toString()));

        List<S2CStomachStatusPacket.Occupant> occupants = new ArrayList<>();

        for (LivingEntity guest : guests)
        {
            if (occupants.size() >= MAX_OCCUPANTS)
            {
                break;
            }

            occupants.add(new S2CStomachStatusPacket.Occupant(guest.getUUID(),
                    String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(guest.getType())),
                    guest.getDisplayName().getString(), guest.getHealth(), guest.getMaxHealth()));
        }

        return occupants;
    }

    /** @return the name a status line shows for a component, which is already plain text. */
    public static String plain(Component component)
    {
        return component.getString();
    }
}
