package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.soul.SoulRemnantEntity;

/**
 * The drawing of a soul remnant (Soul Foundation V1): the texture of a soul on two crossed vertical planes.
 *
 * <p><b>It is not a billboard.</b> The planes stand in the world at fixed angles - one at forty five degrees, one at
 * a hundred and thirty five - so the remnant reads as a small X from the front, as a small X from above and as a
 * thin line only from the two directions where a crossed pair always is one. The planes never turn towards the
 * camera, which is exactly why a queen who walks around her remnant sees a thing standing in the world instead of a
 * picture following her.
 *
 * <p><b>It glows in the dark.</b> The whole remnant is drawn with the emissive render type of the game - the very
 * shader family the eyes of an enderman are drawn with - so the darkness of a cave, the night of a world and the
 * blindness of the queen change nothing about the visibility of the light. No light block is placed and no light
 * level is changed: this is a drawing, not a lamp.
 */
@OnlyIn(Dist.CLIENT)
public final class SoulRemnantRenderer extends EntityRenderer<SoulRemnantEntity>
{
    /** The texture of one soul: the two crossed planes below are painted with this single square. */
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SpiderQueenHungryCraft.MODID, "textures/entity/soul_remnant.png");

    /**
     * The emissive render type: translucency for the soft edge of the texture, and no lighting at all, so the
     * remnant keeps its colour in a pitch black world.
     */
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    /** Half of the edge of one plane, in blocks: the whole X is this wide and twice as tall. */
    private static final float HALF_PLANE = 0.5F;

    /** One plane at forty five and one at a hundred and thirty five degrees: the two of them form the X. */
    private static final float PLANE_ANGLE_A = (float)Math.toRadians(45.0D);
    private static final float PLANE_ANGLE_B = (float)Math.toRadians(135.0D);

    /** The very slight floating of the remnant, so a still world still shows that it is alive. */
    private static final double BOB_HEIGHT = 0.06D;
    private static final double BOB_RADIANS_PER_TICK = 0.055D;

    public SoulRemnantRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulRemnantEntity entity)
    {
        return TEXTURE;
    }

    @Override
    public void render(SoulRemnantEntity entity, float entityYaw, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight)
    {
        double bob = Math.sin((entity.tickCount + partialTick) * BOB_RADIANS_PER_TICK) * BOB_HEIGHT;

        pose.pushPose();
        pose.translate(0.0D, entity.getBbHeight() * 0.5D + bob, 0.0D);

        VertexConsumer consumer = buffers.getBuffer(RENDER_TYPE);
        Matrix4f matrix = pose.last().pose();

        drawPlane(consumer, matrix, PLANE_ANGLE_A);
        drawPlane(consumer, matrix, PLANE_ANGLE_B);

        pose.popPose();
    }

    /** One vertical plane through the centre, crossing the other one at right angles. */
    private static void drawPlane(VertexConsumer consumer, Matrix4f matrix, float angle)
    {
        float rightX = Mth.cos(angle) * HALF_PLANE;
        float rightZ = Mth.sin(angle) * HALF_PLANE;
        float leftX = -rightX;
        float leftZ = -rightZ;

        // The plane is drawn twice with opposite winding, so it is visible from both of its sides whatever the
        // back face culling of the render type does.
        vertex(consumer, matrix, leftX, -HALF_PLANE, leftZ, 0.0F, 1.0F);
        vertex(consumer, matrix, rightX, -HALF_PLANE, rightZ, 1.0F, 1.0F);
        vertex(consumer, matrix, rightX, HALF_PLANE, rightZ, 1.0F, 0.0F);
        vertex(consumer, matrix, leftX, HALF_PLANE, leftZ, 0.0F, 0.0F);

        vertex(consumer, matrix, leftX, HALF_PLANE, leftZ, 0.0F, 0.0F);
        vertex(consumer, matrix, rightX, HALF_PLANE, rightZ, 1.0F, 0.0F);
        vertex(consumer, matrix, rightX, -HALF_PLANE, rightZ, 1.0F, 1.0F);
        vertex(consumer, matrix, leftX, -HALF_PLANE, leftZ, 0.0F, 1.0F);
    }

    /** One corner of a plane: full brightness and no lighting on purpose, see the class comment. */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v)
    {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 1.0F, 0.0F)
                .endVertex();
    }
}
