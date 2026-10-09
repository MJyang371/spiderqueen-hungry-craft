package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * The glowing blue eyes of every spider of the brood (this round's breeding pass).
 *
 * <p>It is the same trick the vanilla spider plays with its own eyes texture: the whole model is drawn a
 * second time, only the eye pixels of the texture, with the eyes render type - which ignores the light of
 * the world and therefore glows in the dark. The ordinary spiders share one eye texture; a species whose eyes
 * have a colour of their own (the zombie spider of the Brood Ability round) hands its own texture to the
 * second constructor.
 */
public class SpiderMinionEyesLayer<T extends SpiderMinionEntity, M extends SpiderModel<T>>
        extends EyesLayer<T, M>
{
    /** The light blue eyes of the ordinary spiders of the brood. */
    public static final ResourceLocation MINION_EYES = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/spider/spider_minion_eyes.png");

    private final RenderType eyes;

    public SpiderMinionEyesLayer(RenderLayerParent<T, M> parent)
    {
        this(parent, MINION_EYES);
    }

    public SpiderMinionEyesLayer(RenderLayerParent<T, M> parent, ResourceLocation eyesTexture)
    {
        super(parent);
        this.eyes = RenderType.eyes(eyesTexture);
    }

    @Override
    public RenderType renderType()
    {
        return this.eyes;
    }
}
