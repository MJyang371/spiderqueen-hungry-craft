package spiderqueenhungrycraft.brood;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Watches where every queen is and pulls her escort along when she travels (Brood Selection + Escort Relocation round).
 *
 * <p>The detection is deliberately a plain position and dimension comparison between two server ticks, and not an event:
 * a command teleport, a portal, a mod that moves a player with its own API and any future mechanism all end in the same
 * place - the queen is suddenly somewhere else. A single tick of movement beyond {@link BroodEscort#LONG_RELOCATION_THRESHOLD}
 * blocks, or any change of dimension, is treated as travel. Walking, sprinting and knockback never move anybody that far
 * in one tick.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BroodEscortEvents
{
    /** Where each queen stood at the end of the previous tick. */
    private static final Map<UUID, LastSeen> LAST_SEEN = new HashMap<>();

    private BroodEscortEvents()
    {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        MinecraftServer server = event.getServer();

        if (server == null)
        {
            return;
        }

        for (ServerPlayer queen : server.getPlayerList().getPlayers())
        {
            LastSeen previous = LAST_SEEN.get(queen.getUUID());
            LastSeen now = new LastSeen(queen.level().dimension(), queen.getX(), queen.getY(), queen.getZ());

            LAST_SEEN.put(queen.getUUID(), now);

            if (previous == null)
            {
                continue;
            }

            boolean changedDimension = !previous.dimension().equals(now.dimension());

            if (BroodEscort.isLongRelocation(previous.dimension(), previous.x(), previous.y(), previous.z(), queen))
            {
                BroodEscort.relocate(queen);
            }
        }

        forgetDeparted(server);
    }

    /** Drops the remembered position of players who are not in the world any more. */
    private static void forgetDeparted(MinecraftServer server)
    {
        Iterator<Map.Entry<UUID, LastSeen>> entries = LAST_SEEN.entrySet().iterator();

        while (entries.hasNext())
        {
            if (server.getPlayerList().getPlayer(entries.next().getKey()) == null)
            {
                entries.remove();
            }
        }
    }

    /** Where a queen stood, and in which world. */
    private record LastSeen(ResourceKey<Level> dimension, double x, double y, double z)
    {
    }
}
