package spiderqueenhungrycraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.network.C2SSpiderBedLeaveHibernationPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;

/**
 * The screen of a Spider Bed Hibernation (Phase 8E3 patch).
 *
 * <p>It is the closest a spider bed may get to the vanilla bed screen without being a bed: the world stays
 * visible, the view fades into the dark and there is exactly one button, the vanilla leave bed button, with
 * the vanilla words in the vanilla place. It is deliberately not the vanilla in bed screen and it never puts
 * the player into the vanilla sleeping state.
 *
 * <p>Closing the screen asks the server to get up, exactly like the vanilla button does; the screen stays
 * open until the server answers with the end of the session, so a client can never decide on its own that a
 * rest is over.
 */
@OnlyIn(Dist.CLIENT)
public class SpiderBedHibernationScreen extends Screen
{
    public SpiderBedHibernationScreen()
    {
        super(Component.translatable("block.spiderqueen_hungrycraft.spider_bed"));
    }

    @Override
    protected void init()
    {
        super.init();

        // The very same words in the very same place as the vanilla bed screen.
        this.addRenderableWidget(Button.builder(Component.translatable("multiplayer.stopSleeping"), button -> leave())
                .bounds(this.width / 2 - 100, this.height - 40, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        // The world stays visible underneath, only the fade of the hibernation is drawn over it.
        SpiderBedHibernationClientState.renderFade(graphics, this.width, this.height);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** Escape only asks to get up, it never closes the screen on its own. */
    @Override
    public void onClose()
    {
        leave();
    }

    @Override
    public boolean isPauseScreen()
    {
        return false;
    }

    private void leave()
    {
        HungryCraftNetwork.sendToServer(new C2SSpiderBedLeaveHibernationPacket());
    }
}
