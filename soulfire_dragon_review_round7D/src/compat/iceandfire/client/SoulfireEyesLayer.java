package spiderqueenhungrycraft.compat.iceandfire.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * Full-bright cyan soul eyes attached to Ice and Fire's actual animated Head bone.
 * No dependency on EntityDragonBase or I&F eye animation/AI.
 * Calibration values are deliberately isolated: tune them to the 1.20.1 fire skeleton's eye sockets.
 */
@OnlyIn(Dist.CLIENT)
public final class SoulfireEyesLayer extends RenderLayer<SoulfireDragonEntity, SoulfireIafTabulaModel>
{
    private static final ResourceLocation EYE_TEX = new ResourceLocation(
            "spiderqueen_hungrycraft", "textures/entity/soulfire_eye_glow.png");

    // Calibrated against the real geometry of firedragon_ground.tbl, read out on a live client (round 7G). The numbers
    // below are in model units (1/16 block) as they come out of the .tbl, and Citadel's translateAndRotate divides the
    // bone pivots by sixteen, so the space this layer draws in is measured in blocks - which is why the eye sizes and
    // offsets here are a sixteenth of the pixel values:
    //   Head      pivot=(0.0, 0.1, -5.5) , box x -2.0..2.0 , y -2.2..1.8 , z -4.0..0.0   -> x +-0.125 , y -0.1375..0.1125 , z -0.25..0
    //   HeadFront pivot=(0.0, -0.63, -3.8) , box x -1.5..1.5 , y -1.5..1.5 , z -5.0..0.0 -> the snout reaches z = -0.3125
    // The neck chain runs towards -z, so -z is forward. The eyes sit on the sides of the skull, a little behind the
    // snout, in the upper half of the head, and just OUTSIDE the side surface (the head is +-0.125 wide), because a quad
    // drawn inside the head box would be hidden by the head itself. The first version of this layer used numbers that
    // put them in front of and below the head instead.
    private static final float EYE_X = 0.13F;
    private static final float EYE_Y = 0.055F;
    private static final float EYE_Z = -0.19F;
    private static final float EYE_SIZE = 0.03F;

    public SoulfireEyesLayer(RenderLayerParent<SoulfireDragonEntity, SoulfireIafTabulaModel> parent)
    {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, SoulfireDragonEntity dragon,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        TabulaModel tabula = this.getParentModel().tabula();
        if (tabula == null)
        {
            return;
        }
        AdvancedModelBox head = tabula.getCube("Head");
        if (head == null)
        {
            return;
        }

        List<AdvancedModelBox> chain = new ArrayList<>();
        for (AdvancedModelBox cursor = head; cursor != null; cursor = cursor.getParent())
        {
            chain.add(cursor);
        }
        Collections.reverse(chain);
        pose.pushPose();
        for (AdvancedModelBox box : chain)
        {
            box.translateAndRotate(pose);
        }

        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(EYE_TEX));
        this.drawEye(pose, consumer, -EYE_X);
        this.drawEye(pose, consumer, EYE_X);
        pose.popPose();
    }

    private void drawEye(PoseStack pose, VertexConsumer consumer, float x)
    {
        // The current pose matrix is already transformed into the animated Head bone's local space.
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        float size = EYE_SIZE;
        this.vertex(consumer, matrix, normal, x - size, EYE_Y - size, EYE_Z, 0, 1);
        this.vertex(consumer, matrix, normal, x + size, EYE_Y - size, EYE_Z, 1, 1);
        this.vertex(consumer, matrix, normal, x + size, EYE_Y + size, EYE_Z, 1, 0);
        this.vertex(consumer, matrix, normal, x - size, EYE_Y + size, EYE_Z, 0, 0);
    }

    private void vertex(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal,
            float x, float y, float z, float u, float v)
    {
        buffer.vertex(matrix, x, y, z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(normal, 0.0F, 0.0F, -1.0F).endVertex();
    }
}
