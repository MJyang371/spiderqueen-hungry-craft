package spiderqueenhungrycraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.world.entity.projectile.ThrownTrident;

/**
 * The renderer of the giant trident (Drowned Spider round).
 *
 * <p>It is the trident renderer of the game itself, in a class that does one thing: it draws that trident three times
 * as large. Nothing else about it changes - the same model, the same texture, the same spin and the same light.
 *
 * <p><b>Only the drawing is scaled.</b> The collision box of the projectile stays the size of a trident's, because a
 * bigger picture must never mean a bigger hit: an enemy has to be where the trident looks like it is, not three blocks
 * away from it.
 */
public class GiantSpiderTridentRenderer extends ThrownTridentRenderer
{
    /** How much larger than a trident of the game this one is drawn. */
    public static final float SCALE = 3.0F;

    public GiantSpiderTridentRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(ThrownTrident trident, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);
        super.render(trident, entityYaw, partialTick, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
