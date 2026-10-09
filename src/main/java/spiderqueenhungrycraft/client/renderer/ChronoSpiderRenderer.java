package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.ChronoSpiderEntity;

/**
 * The renderer of the chrono spider (Special Spiders round I).
 *
 * <p>The very same vanilla spider model every other spider of the brood uses, in the near black of deep space with
 * violet glowing eyes drawn by the emissive eyes layer (see {@link SpiderMinionEyesLayer}). The dark body also makes
 * the difference to the grey spiders of the brood obvious at a glance, which is the whole point of the colour scheme:
 * bone white, grey, deeper grey and black, from the skeleton to the chrono spider.
 */
public class ChronoSpiderRenderer extends MobRenderer<ChronoSpiderEntity, SpiderModel<ChronoSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/chrono_spider.png");

    /** The violet eyes of this species, drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/chrono_spider_eyes.png");

    public ChronoSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(ChronoSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ChronoSpiderEntity entity)
    {
        return TEXTURE;
    }
}
