package spiderqueenhungrycraft.beast;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastTuningPacket;

/**
 * The datapack wiring of the beast tuning (Phase 8B-POLISH-2).
 *
 * <p>{@link AddReloadListenerEvent} adds the parser to the server resource reload, so the mod ships a default
 * tuning file and a modpack datapack can override it. {@link OnDatapackSyncEvent} is fired by Forge both when
 * a player logs in and when the whole server reloads, which is exactly the moment the clients have to learn
 * the new camera values.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBeastTuningEvents
{
    private QueenBeastTuningEvents()
    {
    }

    /** Registers the parser of {@code data/<namespace>/queen_beast/*.json} for server start and every /reload. */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new QueenBeastTuningReloadListener());
    }

    /** Sends the freshly loaded values to the clients view of the shake, at login and after every /reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event)
    {
        S2CBeastTuningPacket packet = S2CBeastTuningPacket.ofCurrent();

        // A single player on login, or every player of the server on a reload.
        for (ServerPlayer player : event.getPlayers())
        {
            HungryCraftNetwork.sendToPlayer(packet, player);
        }
    }
}
