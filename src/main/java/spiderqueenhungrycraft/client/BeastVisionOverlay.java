package spiderqueenhungrycraft.client;

import java.io.InputStream;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The beast vision overlay (Phase 8B1): the spider presence over the first person view.
 *
 * <p>It is drawn only in first person and only while transformed, so the third person view never shows a spider
 * face. The artwork is never stretched: the overlay is always as tall as the current GUI, its width follows the
 * real pixel aspect ratio of the image and it is centered horizontally, which is recalculated every frame and
 * therefore survives any window resize.
 *
 * <p>The image keeps its own alpha and is not tinted. It is drawn below the hotbar, which also means below the
 * crosshair, health, armor and hunger, so none of them is ever covered.
 */
@OnlyIn(Dist.CLIENT)
public final class BeastVisionOverlay implements IGuiOverlay
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/gui/beast_vision_overlay.png");

    private int textureWidth;
    private int textureHeight;
    private boolean unavailable;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        // First person only: no spider face in the third person view, and none behind a menu either.
        if (!minecraft.options.getCameraType().isFirstPerson() || minecraft.screen != null)
        {
            return;
        }

        if (!loadTextureSize())
        {
            return;
        }

        int renderHeight = height;
        int renderWidth = Math.max(1, Math.round((float) renderHeight * (float) this.textureWidth
                / (float) this.textureHeight));
        int x = (width - renderWidth) / 2;

        graphics.blit(TEXTURE, x, 0, renderWidth, renderHeight, 0.0F, 0.0F, this.textureWidth, this.textureHeight,
                this.textureWidth, this.textureHeight);
    }

    /**
     * Reads the real pixel size of the artwork once, so the overlay can never be stretched and never depends on
     * a hardcoded resolution. A missing file only disables the overlay, it never breaks the HUD.
     */
    private boolean loadTextureSize()
    {
        if (this.textureWidth > 0 && this.textureHeight > 0)
        {
            return true;
        }

        if (this.unavailable)
        {
            return false;
        }

        try (InputStream stream = Minecraft.getInstance().getResourceManager().getResource(TEXTURE)
                .orElseThrow().open(); NativeImage image = NativeImage.read(stream))
        {
            this.textureWidth = image.getWidth();
            this.textureHeight = image.getHeight();
            return this.textureWidth > 0 && this.textureHeight > 0;
        }
        catch (Exception exception)
        {
            this.unavailable = true;
            SpiderQueenHungryCraft.LOGGER.warn("Beast vision overlay texture {} is missing or unreadable, the "
                    + "overlay is skipped.", TEXTURE, exception);
            return false;
        }
    }
}
