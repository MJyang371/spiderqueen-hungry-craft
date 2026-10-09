package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.cocoon.CocoonEntity;

/**
 * Renders every cocoon with the same model, only scaled by the size that was captured. The prey
 * itself is never rendered, a cocoon is a closed bundle of silk.
 */
public class CocoonRenderer extends EntityRenderer<CocoonEntity>
{
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/cocoon.png");

    /** How far a cocoon leans while it is being packed up. */
    private static final float WOBBLE_DEGREES = 8.0F;

    private static final float WOBBLE_DURATION = 10.0F;

    /**
     * The colour a cocoon is drawn with while a spider egg incubates inside it: a slight green, so the
     * player sees at a glance that this cocoon is not a meal any more.
     */
    private static final float INCUBATING_RED = 0.70F;
    private static final float INCUBATING_GREEN = 1.00F;
    private static final float INCUBATING_BLUE = 0.70F;

    private final CocoonModel model;

    public CocoonRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new CocoonModel(context.bakeLayer(CocoonModel.LAYER_LOCATION));
        this.shadowRadius = 0.4F;
    }

    @Override
    public void render(CocoonEntity entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer, int packedLight)
    {
        pose.pushPose();

        float wobble = entity.getWobbleTicks() + partialTick;
        if (wobble > 0.0F)
        {
            // A short shake after every hit, purely visual, the creature inside is untouched.
            float decay = Mth.clamp(wobble / WOBBLE_DURATION, 0.0F, 1.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(wobble * 1.8F) * WOBBLE_DEGREES * decay));
        }

        pose.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        pose.scale(entity.getVisualWidth(), entity.getVisualHeight(), entity.getVisualDepth());

        // A cocoon that carries a spider egg is tinted slightly green, because it must never be confused
        // with a cocoon full of prey.
        boolean incubating = entity.hasSpiderEgg();
        float red = incubating ? INCUBATING_RED : 1.0F;
        float green = incubating ? INCUBATING_GREEN : 1.0F;
        float blue = incubating ? INCUBATING_BLUE : 1.0F;

        this.model.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, red, green, blue, 1.0F);

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer, packedLight);
    }

    /** The name of the prey is shown above the cocoon, so the player knows what is inside. */
    @Override
    protected boolean shouldShowName(CocoonEntity entity)
    {
        return entity.hasCustomName();
    }

    @Override
    public ResourceLocation getTextureLocation(CocoonEntity entity)
    {
        return TEXTURE;
    }
}
