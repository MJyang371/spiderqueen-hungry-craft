package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.player.QueenSilk;

/**
 * The stored silk bar of a Spider Queen (feature SILK-CAPACITY): ten cobweb icons above the hearts, every
 * icon worth two points of silk.
 *
 * <p>The row belongs to the queen alone. It is drawn exactly when the vanilla health bar is drawn, which
 * already means a game mode in which the player can really be hurt: a creative queen sees neither hearts nor
 * webs, because every shot of hers is free, and a spectator sees nothing either.
 *
 * <p>The place is never written down as a number. The vanilla health bar puts its extra heart rows (extra
 * maximum health and absorption) above one another and the armor row one row above the topmost hearts, so
 * the very same arithmetic is repeated here from the real state of the player: the webs sit one row above
 * the armor, or above the hearts while she wears no armor. Other HUD elements therefore never collide with
 * the bar, however many rows the player really has.
 *
 * <p>No new texture is shipped: the icon is the vanilla cobweb block texture, cut in code the way a half
 * heart is, and an empty web is the same texture drawn dark.
 */
@OnlyIn(Dist.CLIENT)
public final class QueenSilkHud implements IGuiOverlay
{
    /** The vanilla cobweb block texture, used directly as the HUD icon. */
    private static final ResourceLocation COBWEB =
            ResourceLocation.withDefaultNamespace("textures/block/cobweb.png");

    /** The cobweb texture is a square of this size, everything below is cut out of it. */
    private static final int TEXTURE_SIZE = 16;

    /**
     * One web is eight pixels square and is drawn every eight pixels. Ten of them therefore cover exactly the
     * width of the ten hearts below them, without a gap and without the overlap the vanilla bars use for the
     * sprites that were designed for it: the cobweb texture belongs to a block, and a pixel of it covered by
     * the next icon would cut a strand off.
     */
    private static final int ICON_SIZE = 8;
    private static final int ICON_STEP = 8;

    /**
     * The left edge of the vanilla health bar, and the row height every HUD bar of the game uses. Both are
     * taken from the vanilla layout on purpose: the webs line up with the hearts without being pinned to a
     * fixed place on the screen.
     */
    private static final int BAR_LEFT = 91;
    private static final int ROW_HEIGHT = 10;

    /** The vanilla health bar starts this many pixels above the bottom of the screen. */
    private static final int BAR_BOTTOM = 39;

    /** One heart is two health points and one row of the bar holds ten of them. */
    private static final float HEALTH_PER_HEART = 2.0F;
    private static final float HEARTS_PER_ROW = 10.0F;

    /**
     * A half web keeps the left half of the texture in the left half of the icon, exactly the way a vanilla
     * half heart works: one cut, no second image.
     */
    private static final int HALF_ICON_WIDTH = ICON_SIZE / 2;
    private static final int HALF_ICON_U = TEXTURE_SIZE / 2;

    /** Darkness of an empty web: the vanilla style, where an empty icon is a dark version of a full one. */
    private static final float EMPTY_BRIGHTNESS = 0.25F;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // A survival bar is drawn under exactly the conditions of the vanilla health bar, so nothing of it
        // ever appears in creative, as a spectator, with the HUD hidden or for a player who is no queen.
        if (player == null || minecraft.options.hideGui || !gui.shouldDrawSurvivalElements()
                || !QueenSilk.clientIsQueen())
        {
            return;
        }

        int amount = QueenSilk.clientAmount();
        int x = width / 2 - BAR_LEFT;
        int y = silkRowY(player, height);

        for (int icon = 0; icon < QueenSilk.ICON_COUNT; icon++)
        {
            drawIcon(graphics, x + icon * ICON_STEP, y, amount - icon * QueenSilk.AMOUNT_PER_ICON);
        }

        // The empty web is drawn dimmed, so the shader colour is put back for whatever comes after this bar.
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * The row of webs above the health bar.
     *
     * <p>It is computed the way the vanilla health bar computes its own place for the armor row: the number
     * of heart rows follows the real maximum health and the absorption of the player, the rows are spaced by
     * the same shrinking distance, and the armor row sits one row above the topmost hearts. The webs take
     * that row while the player wears no armor, and move one row up while she does.
     */
    private static int silkRowY(LocalPlayer player, int height)
    {
        float shownHealth = Math.max(player.getMaxHealth(), (float)Mth.ceil(player.getHealth()));
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int healthRows = Math.max(1,
                Mth.ceil((shownHealth + (float)absorption) / HEALTH_PER_HEART / HEARTS_PER_ROW));
        int rowSpacing = Math.max(ROW_HEIGHT - (healthRows - 2), 3);
        int armorRowY = height - BAR_BOTTOM - (healthRows - 1) * rowSpacing - ROW_HEIGHT;

        return player.getArmorValue() > 0 ? armorRowY - ROW_HEIGHT : armorRowY;
    }

    /**
     * One web of the bar.
     *
     * <p>Two points of silk are a full web, one point is the left half of it and nothing is a dark, empty
     * web, so the bar reads at a glance like the health and hunger bars do. The half web is the same texture
     * cut in code, no second image is needed for it.
     */
    private static void drawIcon(GuiGraphics graphics, int x, int y, int silk)
    {
        boolean full = silk >= QueenSilk.AMOUNT_PER_ICON;
        boolean half = !full && silk > 0;

        if (!full)
        {
            graphics.setColor(EMPTY_BRIGHTNESS, EMPTY_BRIGHTNESS, EMPTY_BRIGHTNESS, 1.0F);
            graphics.blit(COBWEB, x, y, ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, TEXTURE_SIZE, TEXTURE_SIZE,
                    TEXTURE_SIZE, TEXTURE_SIZE);
        }

        if (full || half)
        {
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            int width = half ? HALF_ICON_WIDTH : ICON_SIZE;
            int u = half ? HALF_ICON_U : TEXTURE_SIZE;
            graphics.blit(COBWEB, x, y, width, ICON_SIZE, 0.0F, 0.0F, u, TEXTURE_SIZE, TEXTURE_SIZE,
                    TEXTURE_SIZE);
        }
    }
}
