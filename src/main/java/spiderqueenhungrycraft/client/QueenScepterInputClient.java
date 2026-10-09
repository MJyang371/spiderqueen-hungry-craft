package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The item interaction side of the queen sceptre (Queen Scepter command patch, reworked by the Queen Staff input
 * timing round).
 *
 * <p>A right click with the sceptre in hand is a <b>command</b>, not an interaction: the game's own handling of that
 * click - and with it the ordinary interaction reach - is switched off here. That is what makes sixty four blocks
 * possible at all, and it is also what keeps a single click from running two command paths at once when the crosshair
 * happens to point at something close.
 *
 * <p>What this class deliberately <b>no longer</b> does is send anything. The press itself means nothing any more: it
 * is measured by {@link QueenStaffInputHandler}, and only the release decides whether it was a click or a hold. This
 * class therefore only keeps the default behaviour of the game away from the sceptre.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenScepterInputClient
{
    private QueenScepterInputClient()
    {
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!event.isUseItem())
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null)
        {
            return;
        }

        InteractionHand hand = event.getHand();
        ItemStack held = player.getItemInHand(hand);

        if (!held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
        {
            return;
        }

        // The sceptre owns this click: no block, no creature and no chair of the game may react to it instead. The
        // command itself is not asked for here - the release of this same button does that, once, in the input handler.
        event.setCanceled(true);
    }
}
