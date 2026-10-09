package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.client.model.ChadSteveModel;
import spiderqueenhungrycraft.entity.ChadSteveEntity;

/**
 * Draws Chad Steve with his own skin (Phase 9A-GAMEPASS1).
 *
 * <p>There is deliberately no {@code poseStack.scale} anywhere in here. The Blockbench model is already built at
 * the full size of a two times Steve, so the renderer only has to hand the baked model to the standard mob
 * renderer - scaling it here would make him twice as big a second time.
 *
 * <p>He is a normal living entity with a normal model. The player renderer, the player skin system and the whole
 * fake player machinery are not touched.
 */
@OnlyIn(Dist.CLIENT)
public class ChadSteveRenderer extends MobRenderer<ChadSteveEntity, ChadSteveModel>
{
    /** The skin that is painted in the Blockbench file, copied into the mod unchanged. */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/chad_steve.png");

    /** A broad shadow for a broad man. */
    private static final float SHADOW_RADIUS = 0.7F;

    public ChadSteveRenderer(EntityRendererProvider.Context context)
    {
        super(context, new ChadSteveModel(context.bakeLayer(ChadSteveModel.LAYER_LOCATION)), SHADOW_RADIUS);
    }

    @Override
    public ResourceLocation getTextureLocation(ChadSteveEntity entity)
    {
        return TEXTURE;
    }
}
