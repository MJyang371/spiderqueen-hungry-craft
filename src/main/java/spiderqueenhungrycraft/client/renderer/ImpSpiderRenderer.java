package spiderqueenhungrycraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.ImpSpiderEntity;

/**
 * The renderer of the imp spider (this round's breeding pass).
 *
 * <p>It reuses the vanilla spider model and the vanilla spider animations, only drawn at half size - which
 * is what makes the imp a small spider without a single new model or a Blockbench file. The blue glowing
 * eyes come from the shared {@link SpiderMinionEyesLayer eyes layer} of the brood.
 */
public class ImpSpiderRenderer extends MobRenderer<ImpSpiderEntity, SpiderModel<ImpSpiderEntity>>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/imp_spider.png");

    /** Half in every direction: the model of the game at the size of the hitbox of the imp. */
    public static final float MODEL_SCALE = 0.5F;

    public ImpSpiderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SpiderModel<>(context.bakeLayer(ModelLayers.SPIDER)), 0.4F);
        this.addLayer(new SpiderMinionEyesLayer<>(this));
    }

    @Override
    protected void scale(ImpSpiderEntity entity, PoseStack pose, float partialTick)
    {
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
    }

    /** A spider on a ceiling is drawn flipped, exactly like the vanilla spider renderer does it. */
    @Override
    protected float getFlipDegrees(ImpSpiderEntity entity)
    {
        return 180.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(ImpSpiderEntity entity)
    {
        return TEXTURE;
    }
}
