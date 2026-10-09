package spiderqueenhungrycraft.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.network.S2CStomachStatusPacket;

/**
 * The small status panel of a stomach (Phase 8E1-GATE2B).
 *
 * <p>It shows who is inside the stomach of the local player right now, with the name and the health of every
 * guest. It is a pure client drawing of a server snapshot: the client never asks the stomach dimension anything
 * itself. The panel only appears while the cell is really loaded and really holds somebody, and it disappears on
 * its own when the snapshots stop arriving.
 */
@OnlyIn(Dist.CLIENT)
public final class StomachHudOverlay implements IGuiOverlay
{
    private static final Component TITLE = Component.translatable("hud.spiderqueen_hungrycraft.stomach");

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.screen != null)
        {
            return;
        }

        List<S2CStomachStatusPacket.Occupant> occupants = StomachClientState.visibleOccupants();

        if (occupants.isEmpty())
        {
            return;
        }

        Font font = gui.getFont();
        int rows = Math.min(occupants.size(), StomachHudLayout.MAX_VISIBLE_ENTRIES);
        int extra = occupants.size() - rows;
        int panelHeight = StomachHudLayout.TITLE_HEIGHT + rows * StomachHudLayout.ENTRY_HEIGHT
                + (extra > 0 ? 10 : 0) + StomachHudLayout.PADDING;
        int x = width - StomachHudLayout.OFFSET_X - StomachHudLayout.PANEL_WIDTH;
        int y = height - StomachHudLayout.OFFSET_Y - panelHeight;

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(StomachHudLayout.SCALE, StomachHudLayout.SCALE, 1.0F);

        graphics.fill(0, 0, StomachHudLayout.PANEL_WIDTH, panelHeight, StomachHudLayout.background());
        graphics.drawString(font, TITLE, StomachHudLayout.PADDING, StomachHudLayout.PADDING,
                StomachHudLayout.COLOR_TITLE, true);

        int rowY = StomachHudLayout.TITLE_HEIGHT + StomachHudLayout.PADDING;

        for (int index = 0; index < rows; index++)
        {
            drawEntry(graphics, font, occupants.get(index), rowY);
            rowY += StomachHudLayout.ENTRY_HEIGHT;
        }

        if (extra > 0)
        {
            graphics.drawString(font, Component.translatable("hud.spiderqueen_hungrycraft.stomach.more", extra),
                    StomachHudLayout.PADDING, rowY, StomachHudLayout.COLOR_MORE, true);
        }

        graphics.pose().popPose();
    }

    /** One guest: its name over its health bar, with the numbers of that bar written next to it. */
    private static void drawEntry(GuiGraphics graphics, Font font, S2CStomachStatusPacket.Occupant occupant, int rowY)
    {
        String name = font.plainSubstrByWidth(occupant.name(), StomachHudLayout.BAR_WIDTH - 30);
        String health = Math.round(occupant.health()) + " / " + Math.round(occupant.maxHealth());

        graphics.drawString(font, name, StomachHudLayout.PADDING, rowY, StomachHudLayout.COLOR_TEXT, true);
        graphics.drawString(font, health, StomachHudLayout.PANEL_WIDTH - StomachHudLayout.PADDING
                - font.width(health), rowY, StomachHudLayout.COLOR_TEXT, true);

        int barY = rowY + 10;
        int barX = StomachHudLayout.PADDING;

        graphics.fill(barX, barY, barX + StomachHudLayout.BAR_WIDTH, barY + StomachHudLayout.BAR_HEIGHT,
                StomachHudLayout.COLOR_BAR_BACK);

        float ratio = occupant.maxHealth() <= 0.0F ? 0.0F
                : Math.min(1.0F, Math.max(0.0F, occupant.health() / occupant.maxHealth()));
        int filled = Math.round(StomachHudLayout.BAR_WIDTH * ratio);

        if (filled > 0)
        {
            graphics.fill(barX, barY, barX + filled, barY + StomachHudLayout.BAR_HEIGHT,
                    StomachHudLayout.COLOR_BAR_FILL);
        }
    }
}
