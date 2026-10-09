package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.brood.SpiderArrow;

/**
 * The renderer of the arrow of a skeleton spider (Special Spiders round I).
 *
 * <p>It is the vanilla arrow renderer of the game in a concrete class - the one of the game is abstract, because only
 * the texture of a species is missing from it - pointed at the ordinary arrow texture. So the arrow of a skeleton
 * spider is drawn exactly like the arrow of a skeleton, with no new texture, no new item and no new model.
 */
public class SpiderArrowRenderer extends ArrowRenderer<SpiderArrow>
{
    /** The arrow texture of the game itself. */
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft",
            "textures/entity/projectiles/arrow.png");

    public SpiderArrowRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderArrow entity)
    {
        return TEXTURE;
    }
}
