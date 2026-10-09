package spiderqueenhungrycraft.client.renderer;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.client.model.SweatlordSteveModel;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The renderer of Sweatlord Steve (Sweatlord round).
 *
 * <p>The same structure as the ordinary human's renderer: the vanilla player model with a vanilla default skin plus
 * the two vanilla layers that make the gear visible (the humanoid armour layer and the item in hand layer), so the
 * sword, the axe, the bow, the shield and the apple in his hands are drawn by the game itself. No texture of this mod
 * was added and none was copied.
 *
 * <p>The skin is one of the nine <b>wide</b> default skins, chosen per body on the server and synced to every client
 * through the skin variant of the entity. An out of range index falls back to the first skin, so a broken save can
 * never crash a client here.
 */
public class SweatlordSteveRenderer
        extends LivingEntityRenderer<SweatlordSteveEntity, SweatlordSteveModel>
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

    public SweatlordSteveRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SweatlordSteveModel(context.bakeLayer(ModelLayers.PLAYER)), 0.5F);

        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));

        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    /** @return how many skins this renderer knows, used by the client smoke test. */
    public static int skinCount()
    {
        return WIDE_SKINS.length;
    }

    /** @return the texture of the synced skin variant of that body, with a safe fallback for a broken index. */
    @Override
    public ResourceLocation getTextureLocation(SweatlordSteveEntity entity)
    {
        int variant = entity.skinVariant();

        if (variant < 0 || variant >= WIDE_SKINS.length)
        {
            variant = 0;
        }

        return WIDE_SKINS[variant];
    }
}
