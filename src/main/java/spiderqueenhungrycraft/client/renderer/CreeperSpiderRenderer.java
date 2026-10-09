package spiderqueenhungrycraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.CreeperSpiderEntity;

/**
 * The renderer of the creeper spider (Phase X).
 *
 * <p>The very same vanilla spider model as every other spider of the brood, in the green of a creeper, with the shared
 * glowing eyes of the brood. No new model, no Blockbench file and no new animation file is added for this spider.
 *
 * <p>What is added is the <b>fuse</b>, drawn the way the game draws the fuse of a creeper and with nothing but the
 * vanilla renderer API:
 * <ul>
 * <li>the model <b>swells</b>: it grows around its own feet, from its ordinary size to a quarter bigger just before the
 * blast, exactly the way the eye reads a creeper that is about to go off,</li>
 * <li>and it <b>blinks white</b>: slowly in the middle of the fuse and fast at its end, which is the flash of a primed
 * creeper of the game.</li>
 * </ul>
 *
 * <p>No texture of its own is needed for the blinking: the white overlay of the vanilla renderer is used, so this is a
 * matter of two overridden methods and of no new art at all.
 */
public class CreeperSpiderRenderer extends MobRenderer<CreeperSpiderEntity, SpiderModel<CreeperSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/creeper_spider.png");

    /** The shared glowing eyes of the brood, drawn by the eyes layer every spider of this mod wears. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/spider_minion_eyes.png");

    /** How much bigger the model gets over the whole fuse: a quarter, the swell of a creeper. */
    private static final float SWELL_MAX = 0.25F;

    /** Up to this progress the fuse is silent to the eye: nothing blinks in the first second. */
    private static final float FUSE_CALM_UNTIL = 1.0F / 3.0F;

    /** From this progress on the blinking is fast: the last second before the blast. */
    private static final float FUSE_FAST_FROM = 2.0F / 3.0F;

    public CreeperSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(CreeperSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(CreeperSpiderEntity entity)
    {
        return TEXTURE;
    }

    /**
     * The ordinary spider drawing, with the swell of a burning fuse around it.
     *
     * <p>The scaling happens before the model is drawn and around the origin of the entity - its feet - so the spider
     * grows out of the ground it stands on instead of floating into the air while it charges.
     */
    @Override
    public void render(CreeperSpiderEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        float progress = entity.fuseProgress(partialTick);

        if (progress <= 0.0F)
        {
            super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);

            return;
        }

        float swell = 1.0F + SWELL_MAX * progress;
        poseStack.pushPose();
        poseStack.scale(swell, swell, swell);
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }

    /**
     * The blinking of the fuse, read from the same countdown the server counts: nothing in the first second, a slow
     * wink in the second one and a fast flash in the last one. It is the white overlay of the vanilla renderer, so no
     * second texture and no glow layer is involved.
     */
    @Override
    protected float getWhiteOverlayProgress(CreeperSpiderEntity entity, float partialTick)
    {
        float progress = entity.fuseProgress(partialTick);

        if (progress <= 0.0F)
        {
            return 0.0F;
        }

        int fuse = entity.getFuse();

        if (progress < FUSE_CALM_UNTIL)
        {
            return 0.0F;
        }

        if (progress < FUSE_FAST_FROM)
        {
            return fuse % 20 < 10 ? 0.45F : 0.0F;
        }

        return fuse % 6 < 3 ? 0.8F : 0.0F;
    }
}
