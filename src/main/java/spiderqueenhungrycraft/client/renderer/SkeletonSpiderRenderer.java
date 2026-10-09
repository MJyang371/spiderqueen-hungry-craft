package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SkeletonSpiderEntity;

/**
 * The renderer of the skeleton spider (Special Spiders round I).
 *
 * <p>The very same vanilla spider model every other spider of the brood uses, in the cool bone white of a digested
 * skeleton, with the glowing blue eyes of the brood drawn by the emissive eyes layer (see
 * {@link SpiderMinionEyesLayer}) - so the eyes stay bright in the dark instead of merely being light pixels. No new
 * model and no new size: it is a spider first.
 */
public class SkeletonSpiderRenderer extends MobRenderer<SkeletonSpiderEntity, SpiderModel<SkeletonSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/skeleton_spider.png");

    /** The blue eyes of this species, drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/skeleton_spider_eyes.png");

    public SkeletonSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(SkeletonSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(SkeletonSpiderEntity entity)
    {
        return TEXTURE;
    }
}
