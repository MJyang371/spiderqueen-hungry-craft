package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.ZombieSpiderEntity;

/**
 * The renderer of the zombie spider (Brood Ability framework).
 *
 * <p>The very same vanilla spider model the other spiders of the brood use, in the dark green of a digested zombie with
 * eyes of a deep, dark blue - both clearly apart from the grey body and the light blue eyes of the ordinary spiders. No
 * new model is added for this spider.
 */
public class ZombieSpiderRenderer extends MobRenderer<ZombieSpiderEntity, SpiderModel<ZombieSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/zombie_spider.png");

    /** The deep blue eyes of this species, still drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/zombie_spider_eyes.png");

    public ZombieSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(ZombieSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ZombieSpiderEntity entity)
    {
        return TEXTURE;
    }
}
