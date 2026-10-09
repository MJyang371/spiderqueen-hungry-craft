package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * The fade of a Spider Bed Hibernation when no hibernation screen is open (Phase 8E3 patch).
 *
 * <p>The screen normally draws the fade itself, so this overlay only covers the moment in between: a session
 * that started while another screen was open, or a client that closed the screen by other means. It never
 * draws on top of the hibernation screen, so the fade can never be applied twice.
 */
@OnlyIn(Dist.CLIENT)
public final class SpiderBedHibernationOverlay implements IGuiOverlay
{
    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        if (!SpiderBedHibernationClientState.isActive() || Minecraft.getInstance().screen != null)
        {
            return;
        }

        SpiderBedHibernationClientState.renderFade(graphics, width, height);
    }
}
