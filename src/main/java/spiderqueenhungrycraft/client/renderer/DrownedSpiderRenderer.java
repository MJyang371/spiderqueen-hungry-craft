package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.DrownedSpiderEntity;

/**
 * The renderer of the drowned spider (Drowned Spider round).
 *
 * <p>No new model, exactly like every other spider of the brood: the vanilla spider model, in the drowned cyan of the
 * creature it was bred from - a cold blue that sits between blue and green but leans clearly towards the blue - with
 * eyes that glow blue through the emissive eyes layer of the brood ({@link SpiderMinionEyesLayer}), so they stay bright
 * in the dark.
 */
public class DrownedSpiderRenderer extends MobRenderer<DrownedSpiderEntity, SpiderModel<DrownedSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/drowned_spider.png");

    /** The blue eyes of this species, drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/drowned_spider_eyes.png");

    public DrownedSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(DrownedSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(DrownedSpiderEntity entity)
    {
        return TEXTURE;
    }
}
