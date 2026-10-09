package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The soul bar of the local player (Soul Foundation V1): a long Minecraft style frame in the bottom left corner of
 * the screen, with the icon of a soul at its left end and the balance of the player at its right end.
 *
 * <p>It is a pure client drawing of a server value, and it is deliberately flat: a dark plate in a grey pixel frame,
 * one icon and one number - the way the game frames its own small readouts. The frame keeps its length for every
 * ordinary balance, so the bar never twitches as the number changes; only a balance too long for the frame widens
 * it, see {@link SoulHudLayout#panelWidthFor(int)}.
 *
 * <p>The bottom right corner is taken by the stomach panel, which is why this one is anchored bottom left, and it is
 * drawn <b>below</b> the chat panel: a line of chat is worth more than a bar that will still be there afterwards.
 */
@OnlyIn(Dist.CLIENT)
public final class SoulHud implements IGuiOverlay
{
    /** The icon of a soul at the left end of the bar: the art of this mod, shipped as its own small texture. */
    private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/gui/soul_icon.png");

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();

        // The bar is part of the world view: a hidden HUD, an open screen (an inventory, a pause menu) and the
        // moment before a player exists show nothing at all.
        if (minecraft.player == null || minecraft.options.hideGui || minecraft.screen != null)
        {
            return;
        }

        Font font = gui.getFont();
        String text = Long.toString(SoulClientState.drawableSouls());
        int textWidth = font.width(text);
        int panelWidth = SoulHudLayout.panelWidthFor(textWidth);
        int panelHeight = SoulHudLayout.panelHeight();

        // The whole bar is laid out in its own units and then scaled down as one piece (see SoulHudLayout.SCALE), so
        // the frame, the icon and the number keep their proportions exactly. The corner of the screen is the parent
        // space, which is why the bar is translated by its position and only then scaled.
        graphics.pose().pushPose();
        graphics.pose().translate(SoulHudLayout.OFFSET_X,
                height - SoulHudLayout.OFFSET_Y - SoulHudLayout.screenHeight(), 0.0F);
        graphics.pose().scale(SoulHudLayout.SCALE, SoulHudLayout.SCALE, 1.0F);

        drawPanel(graphics, 0, 0, panelWidth, panelHeight);

        // The icon of a soul at the left end of the bar, one to one with the pixels of its art.
        graphics.blit(ICON, SoulHudLayout.PADDING, SoulHudLayout.PADDING, SoulHudLayout.ICON_SIZE,
                SoulHudLayout.ICON_SIZE, 0.0F, 0.0F, SoulHudLayout.ICON_SIZE, SoulHudLayout.ICON_SIZE,
                SoulHudLayout.ICON_SIZE, SoulHudLayout.ICON_SIZE);

        // The balance at the right end of the bar, centred on the icon: as the number grows it grows towards the
        // middle of the frame, and it never reaches past the right edge.
        int textY = (panelHeight - SoulHudLayout.FONT_LINE_HEIGHT + 1) / 2;

        graphics.drawString(font, text, panelWidth - SoulHudLayout.PADDING - textWidth, textY,
                SoulHudLayout.COLOR_TEXT, true);

        graphics.pose().popPose();
    }

    /** The dark plate and the grey bevel of the bar, in the flat style of the game's own small boxes. */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height)
    {
        int frame = SoulHudLayout.FRAME;

        graphics.fill(x, y, x + width, y + height, SoulHudLayout.COLOR_BACKGROUND);

        // Light from the top left, dark at the bottom right: the bevel the game gives its own frames.
        graphics.fill(x, y, x + width, y + frame, SoulHudLayout.COLOR_FRAME_LIGHT);
        graphics.fill(x, y, x + frame, y + height, SoulHudLayout.COLOR_FRAME_LIGHT);
        graphics.fill(x, y + height - frame, x + width, y + height, SoulHudLayout.COLOR_FRAME_DARK);
        graphics.fill(x + width - frame, y, x + width, y + height, SoulHudLayout.COLOR_FRAME_DARK);
    }
}
