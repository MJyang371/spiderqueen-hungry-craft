package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;

/**
 * The invisible renderer of a stomach core (Phase 8E1-GATE2A).
 *
 * <p>A core is the wall itself, not a creature: no model, no texture, no shadow, no name tag. A plain
 * {@link EntityRenderer} with an empty render method and its default shadow radius of zero is exactly that, and
 * it keeps the whole thing free of any new asset.
 */
public class StomachCoreRenderer extends EntityRenderer<StomachCoreEntity>
{
    public StomachCoreRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(StomachCoreEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        // Nothing is drawn: the wall is felt, not seen.
    }

    /** Never used, because nothing of a core is ever drawn. */
    @Override
    public ResourceLocation getTextureLocation(StomachCoreEntity entity)
    {
        return null;
    }
}
