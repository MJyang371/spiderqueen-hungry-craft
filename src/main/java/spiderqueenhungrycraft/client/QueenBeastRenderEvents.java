package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The rendering of the Queen Beast form (Phase 8A).
 *
 * <p>While transformed the ordinary player renderer is cancelled and replaced by the giant spider, which
 * removes the player skin, every armor layer, the cape and the held item in one step, for the local player and
 * for every other player alike.
 *
 * <p>In first person the vanilla hand and the held item are hidden as well, because a beast has no human arms
 * and the camera sits inside the mouth of the spider, where a full model would only fill the screen.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastRenderEvents
{
    private QueenBeastRenderEvents()
    {
    }

    /** Replaces the whole player model, its layers and its held item with the beast body. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event)
    {
        Player player = event.getEntity();

        if (!QueenBeastState.isTransformed(player))
        {
            return;
        }

        event.setCanceled(true);

        if (player instanceof AbstractClientPlayer clientPlayer)
        {
            QueenBeastRenderer.render(clientPlayer, event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource(), event.getPackedLight());
        }
    }

    /** First person: no player arm and no held item model. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event)
    {
        LocalPlayer player = Minecraft.getInstance().player;

        if (player != null && QueenBeastState.isTransformed(player))
        {
            event.setCanceled(true);
        }
    }

    /** The same for the arm of the player renderer, which is used for example while holding a map. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event)
    {
        if (QueenBeastState.isTransformed(event.getPlayer()))
        {
            event.setCanceled(true);
        }
    }
}
