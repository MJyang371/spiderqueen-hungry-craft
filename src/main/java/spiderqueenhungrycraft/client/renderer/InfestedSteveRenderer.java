package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.client.model.InfestedSteveModel;

/**
 * The renderer of the infested Steve (Infested Steve round).
 *
 * <p>He is drawn on the parts of the <b>vanilla player model</b> ({@link ModelLayers#PLAYER}), exactly the way the
 * standard human combatant is drawn, plus the two vanilla layers that make equipment visible: the humanoid armour layer
 * for the armour of a digested human and the item in hand layer for the sword, the bow, the shield and the golden apple.
 * No model of this mod is duplicated for him and no vanilla texture is touched.
 *
 * <p>What is deliberately different from a human is the skin: a human wears one of the vanilla default skins, this
 * creature wears its own texture - a colourised body of a Steve, so a player can tell a soldier of the queen from an
 * ordinary human at one glance.
 */
public class InfestedSteveRenderer extends LivingEntityRenderer<InfestedSteveEntity, InfestedSteveModel>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/infested_steve.png");

    public InfestedSteveRenderer(EntityRendererProvider.Context context)
    {
        super(context, new InfestedSteveModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /** The one texture of this creature, the same on every client and for every body of it. */
    @Override
    public ResourceLocation getTextureLocation(InfestedSteveEntity entity)
    {
        return TEXTURE;
    }
}
