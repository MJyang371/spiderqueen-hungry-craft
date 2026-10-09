package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.StraySpiderEntity;

/**
 * The renderer of the stray spider (Stray and Husk Spider round).
 *
 * <p>The very same vanilla spider model every other spider of the brood uses, in the pale cold blue of a digested
 * stray, with eyes that glow a cold white - drawn by the emissive eyes layer ({@link SpiderMinionEyesLayer}), so they
 * stay bright in the dark instead of merely being light pixels. No new model and no new size.
 */
public class StraySpiderRenderer extends MobRenderer<StraySpiderEntity, SpiderModel<StraySpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/stray_spider.png");

    /** The white eyes of this species, drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/stray_spider_eyes.png");

    public StraySpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(StraySpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(StraySpiderEntity entity)
    {
        return TEXTURE;
    }
}
