package spiderqueenhungrycraft.compat.iceandfire;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Keeps the client mirror of the necromancy markers honest (Necromancy IV prototype 2).
 *
 * <p>One handler, and it exists because an entity id is only meaningful inside one connection: a new session hands out
 * the same numbers again, and a mirror that outlived its world could therefore make a completely unrelated dragon look
 * like a skeleton.
 *
 * <p>Leaving a world is the moment that matters, and it covers both endings this client can have: quitting to the
 * title screen and quitting the game. The server fills the mirror again by itself, because it tells every player who
 * starts tracking one of its woken dragons ({@link IceAndFireCompatEvents}).
 *
 * <p>It is annotated client only, so a dedicated server never registers - and never loads - this class.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class ClientNecromancyEvents
{
    private ClientNecromancyEvents()
    {
    }

    /** Forgets every dragon this client knew about, because the connection they belonged to is gone. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        ClientNecromancyState.clear("disconnect");
    }
}
