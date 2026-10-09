package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import spiderqueenhungrycraft.web.WebElement;
import spiderqueenhungrycraft.web.WebShotProjectile;

/**
 * Renders the web shot in the colour of its element (Phase 7A-POLISH-1).
 *
 * <p>The ordinary shot delegates to the vanilla thrown item renderer and therefore keeps its exact look. An
 * elemental shot is drawn through a very light buffer wrapper that tints every vertex with the element colour
 * and, for the fire element, forces the full bright light map: the shot then looks hot even in the dark while
 * emitting no real light. No new texture, model, render type or graphics framework is involved.
 */
public class WebShotRenderer extends EntityRenderer<WebShotProjectile>
{
    private final ThrownItemRenderer<WebShotProjectile> plainRenderer;
    private final ThrownItemRenderer<WebShotProjectile> brightRenderer;

    public WebShotRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.plainRenderer = new ThrownItemRenderer<>(context);
        // The bright variant raises the block light part of the entity, the wrapper below forces the light map.
        this.brightRenderer = new ThrownItemRenderer<>(context, 1.0F, true);
    }

    @Override
    public void render(WebShotProjectile shot, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        WebElement element = shot.element();

        if (!element.isElemental())
        {
            this.plainRenderer.render(shot, entityYaw, partialTick, poseStack, buffer, packedLight);
            return;
        }

        ThrownItemRenderer<WebShotProjectile> renderer = element.isEmissive() ? this.brightRenderer
                : this.plainRenderer;

        renderer.render(shot, entityYaw, partialTick, poseStack,
                new TintedBufferSource(buffer, element.tintColor(), element.isEmissive()), packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(WebShotProjectile shot)
    {
        return this.plainRenderer.getTextureLocation(shot);
    }

    /** Wraps the buffer source of one shot, so every vertex of that shot carries the colour of its element. */
    public static final class TintedBufferSource implements MultiBufferSource
    {
        private final MultiBufferSource source;
        private final int tintColor;
        private final boolean fullBright;

        public TintedBufferSource(MultiBufferSource source, int tintColor, boolean fullBright)
        {
            this.source = source;
            this.tintColor = tintColor;
            this.fullBright = fullBright;
        }

        @Override
        public VertexConsumer getBuffer(RenderType renderType)
        {
            return new TintedVertexConsumer(this.source.getBuffer(renderType), this.tintColor, this.fullBright);
        }
    }

    /**
     * Tints every vertex with an element colour and can force the full bright light map. Public so the
     * development probe can check the numbers without a graphics device.
     */
    public static final class TintedVertexConsumer extends VertexConsumerWrapper
    {
        private final int tintColor;
        private final boolean fullBright;

        public TintedVertexConsumer(VertexConsumer parent, int tintColor, boolean fullBright)
        {
            super(parent);
            this.tintColor = tintColor;
            this.fullBright = fullBright;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha)
        {
            this.parent.color(tint(red, this.tintColor >> 16), tint(green, this.tintColor >> 8),
                    tint(blue, this.tintColor), alpha);
            return this;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha)
        {
            this.parent.defaultColor(tint(red, this.tintColor >> 16), tint(green, this.tintColor >> 8),
                    tint(blue, this.tintColor), alpha);
        }

        @Override
        public VertexConsumer uv2(int u, int v)
        {
            if (this.fullBright)
            {
                this.parent.uv2(LightTexture.FULL_BRIGHT);
                return this;
            }

            this.parent.uv2(u, v);
            return this;
        }

        /** @return one tinted colour channel of a vertex. */
        private static int tint(int value, int color)
        {
            return Mth.clamp(value * (color & 0xFF) / 255, 0, 255);
        }
    }
}
