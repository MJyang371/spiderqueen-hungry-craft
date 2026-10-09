package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The minimal beast HUD (Phase 8B1).
 *
 * <p>A beast cannot use items, so the hotbar, the experience bar and the name of the selected item have no
 * business on screen while transformed. They are cancelled one by one on the vanilla overlay they belong to,
 * which leaves health, armor, hunger, air and the crosshair exactly where they were, and it never touches the
 * chat, the subtitles, boss bars, the debug screen, toasts or any other system message. It is not a global
 * "hide the GUI" switch.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastHud
{
    private QueenBeastHud()
    {
    }

    @SubscribeEvent
    public static void onRenderGuiOverlayPre(RenderGuiOverlayEvent.Pre event)
    {
        if (!isMinimalHud() || !isHiddenOverlay(event.getOverlay().id()))
        {
            return;
        }

        event.setCanceled(true);
    }

    private static boolean isHiddenOverlay(ResourceLocation id)
    {
        return VanillaGuiOverlay.HOTBAR.id().equals(id)
                || VanillaGuiOverlay.EXPERIENCE_BAR.id().equals(id)
                || VanillaGuiOverlay.ITEM_NAME.id().equals(id);
    }

    private static boolean isMinimalHud()
    {
        LocalPlayer player = Minecraft.getInstance().player;

        return player != null && QueenBeastState.isTransformed(player);
    }
}
