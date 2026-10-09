package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSpiderEntity;

/**
 * The renderer of the ordinary brood spider (this round's breeding pass).
 *
 * <p>The vanilla spider model at full size, in the grey of the brood with the blue glowing eyes of the
 * {@link SpiderMinionEyesLayer}. The two spiders of the mod only differ in their scale and their texture,
 * so one model from the game serves both.
 */
public class BroodSpiderRenderer extends MobRenderer<BroodSpiderEntity, SpiderModel<BroodSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/brood_spider.png");

    public BroodSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(BroodSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(BroodSpiderEntity entity)
    {
        return TEXTURE;
    }
}
