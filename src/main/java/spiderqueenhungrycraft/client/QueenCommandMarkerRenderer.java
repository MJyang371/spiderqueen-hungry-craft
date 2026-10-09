package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Draws the command marker of the queen sceptre (Queen Scepter command patch).
 *
 * <p>The marker is drawn <b>only for the queen who gave the order</b>: the server sends the confirmation of a scout
 * move to nobody but her, and this class is a picture her own client paints in her own world - it is not an entity, not
 * a block and not a particle, so no other player has anything to see and nothing is ever synchronised to anyone else.
 *
 * <p>Two drawings belong to one marker:
 * <ul>
 * <li>a <b>footprint</b> lying on the ground of the clicked block, which says exactly where the order points,</li>
 * <li>a <b>sprite that always turns to the queen</b>: the pose stack is turned to the camera before it is drawn, so the
 * indicator of the user faces her from every angle instead of being a flat picture that disappears when she looks at it
 * from the side. It floats above the footprint and breathes (a slow pulse), so the eye finds it at once.</li>
 * </ul>
 *
 * <p>Both are full bright and drawn <b>through</b> grass, trees and walls, so the mark a queen just put down is never
 * swallowed by the terrain the order points at: a command cursor one can not see is a command cursor one can not use.
 * That takes two things at once, and both of them are needed - see the note in {@link MineCommandIndicatorRenderer}:
 * the render type is the see through type of the game (whose own depth state is a no-op, so the flush of the batch can
 * not put a comparison back on), and the depth test is <b>also</b> switched off by hand around the drawing, because
 * that shard of the game never disables the test itself. The texture is taken as it is (nearest pixel, no blurring),
 * and the footprint lies a hair above the block it was placed on, so it never fights with the ground underneath it.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenCommandMarkerRenderer
{
    /** The indicator drawing of the user: thirty two by thirty two pixels, used exactly as drawn. */
    private static final ResourceLocation MARKER_TEXTURE = ResourceLocation
            .fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "textures/misc/queen_command_marker.png");

    /** How wide the footprint on the ground is: one and a quarter blocks. */
    public static final float MARKER_SIZE = 1.25F;

    /** How wide the sprite that faces the queen is: bigger than the footprint, so it is readable from far away. */
    public static final float BILLBOARD_SIZE = 1.6F;

    /** How high that sprite floats above the clicked block, like a pin stuck into the ground. */
    private static final float BILLBOARD_HEIGHT = 0.95F;

    /** The breathing of the sprite: it grows and shrinks a little, which no eye misses. */
    private static final float PULSE_AMPLITUDE = 0.09F;
    private static final float PULSE_SPEED = 0.35F;

    /** A hair above the block, so the marker and the ground never fight over the same pixels. */
    private static final float LIFT = 0.03F;

    /** Full daylight, so the marker is readable at midnight as well. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private static boolean filteringSet;

    private QueenCommandMarkerRenderer()
    {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event)
    {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        BlockPos marker = QueenScepterVisuals.markerToDraw(minecraft.level);

        if (marker == null)
        {
            return;
        }

        softenFiltering(minecraft);

        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        RenderType type = RenderType.textSeeThrough(MARKER_TEXTURE);
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(type);

        // Through grass, dirt and walls. The type itself carries a no-op depth state - the shard of "no depth test" of
        // the game does nothing at all - so the test has to be switched off by hand here and stays off through the
        // flush of that type. A plain entityTranslucent could not be used: it puts its own LEQUAL back on the flush.
        RenderSystem.disableDepthTest();

        // The footprint on the ground: it shows exactly which block the order points at.
        poseStack.pushPose();
        poseStack.translate(marker.getX() + 0.5D - cameraPosition.x, marker.getY() + LIFT - cameraPosition.y,
                marker.getZ() + 0.5D - cameraPosition.z);
        Matrix4f footprint = poseStack.last().pose();
        ground(consumer, footprint, true);
        ground(consumer, footprint, false);
        poseStack.popPose();

        // The sprite above it, turned to the queen herself: a marker she can read from every angle she stands in.
        float pulse = 1.0F + PULSE_AMPLITUDE
                * Mth.sin((minecraft.level.getGameTime() + event.getPartialTick()) * PULSE_SPEED);
        float sprite = BILLBOARD_SIZE * pulse;
        poseStack.pushPose();
        poseStack.translate(marker.getX() + 0.5D - cameraPosition.x,
                marker.getY() + LIFT + BILLBOARD_HEIGHT - cameraPosition.y, marker.getZ() + 0.5D - cameraPosition.z);
        poseStack.mulPose(event.getCamera().rotation());
        poseStack.scale(sprite, sprite, sprite);
        Matrix4f billboard = poseStack.last().pose();
        facing(consumer, billboard);
        facingBackwards(consumer, billboard);
        poseStack.popPose();

        minecraft.renderBuffers().bufferSource().endBatch(type);
        RenderSystem.enableDepthTest();
    }

    /** Draws the footprint, facing up or down, so it can be seen from above and from below. */
    private static void ground(VertexConsumer consumer, Matrix4f matrix, boolean up)
    {
        float half = MARKER_SIZE / 2.0F;
        float[][] corners = up ? new float[][] { { -half, -half }, { half, -half }, { half, half }, { -half, half } }
                : new float[][] { { -half, half }, { half, half }, { half, -half }, { -half, -half } };

        for (float[] corner : corners)
        {
            vertex(consumer, matrix, corner[0], 0.0F, corner[1], corner[0] / MARKER_SIZE + 0.5F,
                    corner[1] / MARKER_SIZE + 0.5F, 0.0F, up ? 1.0F : -1.0F, 0.0F);
        }
    }

    /**
     * Draws the sprite that faces the queen: the pose stack is already turned to her camera, so this is the screen plane
     * - x to the right, y upwards - and the drawing is one block wide, which the scale of the pose stack then enlarges.
     */
    private static void facing(VertexConsumer consumer, Matrix4f matrix)
    {
        // The texture is stored with its top first, so v runs from zero at the top edge to one at the bottom edge.
        float[][] corners = { { -0.5F, 0.5F, 0.0F, 0.0F }, { 0.5F, 0.5F, 1.0F, 0.0F }, { 0.5F, -0.5F, 1.0F, 1.0F },
                { -0.5F, -0.5F, 0.0F, 1.0F } };

        for (float[] corner : corners)
        {
            vertex(consumer, matrix, corner[0], corner[1], 0.0F, corner[2], corner[3], 0.0F, 0.0F, 1.0F);
        }
    }

    /** The same sprite with the other winding, so it is drawn even where a render type of the game culls back faces. */
    private static void facingBackwards(VertexConsumer consumer, Matrix4f matrix)
    {
        float[][] corners = { { -0.5F, -0.5F, 0.0F, 1.0F }, { 0.5F, -0.5F, 1.0F, 1.0F }, { 0.5F, 0.5F, 1.0F, 0.0F },
                { -0.5F, 0.5F, 0.0F, 0.0F } };

        for (float[] corner : corners)
        {
            vertex(consumer, matrix, corner[0], corner[1], 0.0F, corner[2], corner[3], 0.0F, 0.0F, -1.0F);
        }
    }

    /**
     * One vertex of a marker quad: full bright and exactly the elements the see through format of the game carries -
     * position, colour, texture and lightmap. The normal of a corner is kept as a parameter because it says which way
     * the quad faces for a reader, but it is deliberately not written: that format has no normal element.
     */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v,
            float normalX, float normalY, float normalZ)
    {
        consumer.vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .uv2(FULL_BRIGHT)
                .endVertex();
    }

    /** The drawing is pixel art: it is asked to be sampled without blurring and without mipmaps, once. */
    private static void softenFiltering(Minecraft minecraft)
    {
        if (filteringSet)
        {
            return;
        }

        filteringSet = true;
        minecraft.getTextureManager().getTexture(MARKER_TEXTURE).setFilter(false, false);
    }
}
