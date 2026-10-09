package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.client.model.HumanCombatantModel;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;

/**
 * The renderer of the standard human combatant (Phase HF0A-GAMEPASS1).
 *
 * <p>He is drawn on the parts of the vanilla player model with a vanilla default skin, plus the two vanilla layers
 * that make equipment visible: the humanoid armour layer for the iron armour and the item in hand layer for the
 * sword, the bow, the shield and the golden apple. No texture of this mod was copied for it and none was added.
 *
 * <p>The skin is one of the nine <b>wide</b> default skins of the vanilla game, chosen randomly per combatant on the
 * server and synced to every client. Only the wide model is used in this round, which is why the list below only
 * holds wide skins: a slim skin on a wide model would be stretched, and switching between the two player model
 * layers per entity needs a second model subscription, which is a later polish round. Every path is the vanilla
 * texture path of the game, exactly the ones {@code DefaultPlayerSkin} hands out itself.
 */
public class HumanCombatantRenderer extends LivingEntityRenderer<HumanCombatantEntity, HumanCombatantModel>
{
    /** The nine wide default skins, same order and same files as the vanilla default skin list. */
    private static final ResourceLocation[] WIDE_SKINS = {
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/alex.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/ari.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/efe.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/kai.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/makena.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/noor.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/sunny.png"),
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/zuri.png")
    };

    public HumanCombatantRenderer(EntityRendererProvider.Context context)
    {
        super(context, new HumanCombatantModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /** The default skin this combatant wears, straight from the synced variant of the entity. */
    @Override
    public ResourceLocation getTextureLocation(HumanCombatantEntity entity)
    {
        int variant = entity.skinVariant();

        if (variant < 0 || variant >= WIDE_SKINS.length)
        {
            variant = 0;
        }

        return WIDE_SKINS[variant];
    }
}
