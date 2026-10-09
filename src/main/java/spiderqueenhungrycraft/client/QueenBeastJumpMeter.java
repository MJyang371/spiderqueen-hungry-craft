package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The charged jump meter of the beast (Phase 8C).
 *
 * <p>It borrows the operation language of a horse, not a horse itself: the very same vanilla icon sheet, the
 * very same bar, the very same place where the experience bar would be, which is hidden while transformed. No
 * entity is created, no vehicle is faked, nothing is mounted.
 *
 * <p>The bar only exists while the jump key of a transformed beast is really held, so there is no permanent
 * extra HUD element: press, watch it fill, release, and it is gone.
 */
@OnlyIn(Dist.CLIENT)
public final class QueenBeastJumpMeter implements IGuiOverlay
{
    /** The vanilla icon sheet the horse jump bar is drawn from. */
    private static final ResourceLocation ICONS = ResourceLocation.withDefaultNamespace("textures/gui/icons.png");

    /** Width of the empty and the filled bar, exactly like the vanilla jump meter. */
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 5;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || !QueenBeastState.isTransformed(player) || !QueenBeastJumpClient.isCharging())
        {
            return;
        }

        int x = width / 2 - 91;
        int y = height - 32 + 3;
        int filled = (int) (QueenBeastJumpClient.meterFraction() * (BAR_WIDTH + 1));

        graphics.blit(ICONS, x, y, 0, 84, BAR_WIDTH, BAR_HEIGHT);

        if (filled > 0)
        {
            graphics.blit(ICONS, x, y, 0, 89, Math.min(filled, BAR_WIDTH), BAR_HEIGHT);
        }
    }
}
