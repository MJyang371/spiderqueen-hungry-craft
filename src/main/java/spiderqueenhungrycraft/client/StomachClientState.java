package spiderqueenhungrycraft.client;

import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.S2CStomachStatusPacket;

/**
 * The client cache of the stomach status (Phase 8E1-GATE2B).
 *
 * <p>The client never looks into the stomach dimension on its own: it only remembers what the server told it. A
 * packet that belongs to somebody else is dropped, a packet that is older than the state it already has is
 * dropped, and a state that is not refreshed within the lifetime is forgotten, so the panel can never become a
 * ghost of a stomach that is long gone.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class StomachClientState
{
    /** How long a snapshot is trusted without a new one. */
    public static final int TTL_TICKS = 40;

    private static UUID owner;
    private static int cellIndex = -1;
    private static int sequence = -1;
    private static boolean loaded;

    private static List<S2CStomachStatusPacket.Occupant> occupants = List.of();
    private static int lastUpdate;
    private static int clientTick;

    private StomachClientState()
    {
    }

    /** Accepts the status of a cell, if it is mine and newer than what I already know. */
    public static void accept(S2CStomachStatusPacket packet)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || !packet.owner().equals(player.getUUID()) || packet.sequence() <= sequence)
        {
            return;
        }

        owner = packet.owner();
        cellIndex = packet.cellIndex();
        sequence = packet.sequence();
        loaded = packet.loaded();
        occupants = List.copyOf(packet.occupants());
        lastUpdate = clientTick;
    }

    /** @return the guests to draw, or an empty list while the panel has to stay hidden. */
    public static List<S2CStomachStatusPacket.Occupant> visibleOccupants()
    {
        if (!isFresh() || !loaded || occupants.isEmpty())
        {
            return List.of();
        }

        return occupants;
    }

    /** @return true while the last snapshot is recent enough to be trusted. */
    public static boolean isFresh()
    {
        return owner != null && clientTick - lastUpdate <= TTL_TICKS;
    }

    public static int cellIndex()
    {
        return cellIndex;
    }

    public static int sequence()
    {
        return sequence;
    }

    public static void clear()
    {
        owner = null;
        cellIndex = -1;
        sequence = -1;
        loaded = false;
        occupants = List.of();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        clientTick++;

        if (Minecraft.getInstance().player == null || !isFresh())
        {
            clear();
        }
    }
}
