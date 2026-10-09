package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.HuskSpiderEntity;

/**
 * The renderer of the husk spider (Stray and Husk Spider round).
 *
 * <p>The very same vanilla spider model every other spider of the brood uses, in the dry sandy brown of a digested
 * husk, with the blue eyes of the undead brood drawn by the emissive eyes layer ({@link SpiderMinionEyesLayer}). It is
 * clearly apart from the greenish zombie spider it is a cousin of, and apart from every grey spider of the hive.
 */
public class HuskSpiderRenderer extends MobRenderer<HuskSpiderEntity, SpiderModel<HuskSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/husk_spider.png");

    /** The blue eyes of this species, drawn by the glowing eyes layer of the brood. */
    private static final ResourceLocation EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/husk_spider_eyes.png");

    public HuskSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.8F);
        this.addLayer(new SpiderMinionEyesLayer<>(this, EYES));
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(HuskSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(HuskSpiderEntity entity)
    {
        return TEXTURE;
    }
}
