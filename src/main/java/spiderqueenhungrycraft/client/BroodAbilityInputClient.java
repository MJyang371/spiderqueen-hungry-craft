package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.C2SBroodAbilityCastPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * Client side of the brood ability key (Brood Ability framework).
 *
 * <p>One physical press is one request and nothing else. The key is read with {@code consumeClick}, so holding it down
 * does not matter at all - a player who keeps the finger on the key does not send a packet per tick. Whether the press
 * means anything is decided by the server, together with the cooldown of every spider.
 *
 * <p>The one thing this client checks is whether the request is even worth sending: without the queen sceptre in a hand
 * the key is not the key of the brood, so no packet is sent at all.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class BroodAbilityInputClient
{
    private BroodAbilityInputClient()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        while (HungryCraftKeyMappings.BROOD_ABILITY.consumeClick())
        {
            sendCast();
        }
    }

    /**
     * Asks the server to cast the ability of the selected spiders. The command wheel of the sceptre calls this very
     * method (Command Wheel round), so the key and the wheel are one and the same request, and the sceptre check is
     * asked once, here.
     */
    static void sendCast()
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.getConnection() == null || minecraft.screen != null || !holdsScepter(player))
        {
            return;
        }

        HungryCraftNetwork.sendToServer(new C2SBroodAbilityCastPacket());
    }

    /** @return true when the queen sceptre is in one of the hands of that player, the voice of the brood. */
    private static boolean holdsScepter(LocalPlayer player)
    {
        for (ItemStack stack : player.getHandSlots())
        {
            if (stack.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }
}
