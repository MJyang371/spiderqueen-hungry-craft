package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.brood.SpiderEggArrow;

/**
 * The renderer of the spider egg arrow (Spider Mine and Egg Arrow round).
 *
 * <p>It is the ordinary arrow renderer of the game in a concrete class - the one of the game is abstract, because only
 * the texture of a species is missing from it - pointed at the texture of a clutch of eggs on an arrow shaft. So the
 * arrow is drawn with the model of the game, flying and tumbling exactly like a plain arrow, and the only thing this
 * class adds is the picture of what it is.
 */
public class SpiderEggArrowRenderer extends ArrowRenderer<SpiderEggArrow>
{
    /** The texture of this arrow, drawn by this mod. */
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "spiderqueen_hungrycraft", "textures/entity/projectiles/spider_egg_arrow.png");

    public SpiderEggArrowRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderEggArrow entity)
    {
        return TEXTURE;
    }
}
