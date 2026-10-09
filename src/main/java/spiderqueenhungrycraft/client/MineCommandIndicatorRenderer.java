package spiderqueenhungrycraft.client;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

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
 * Draws the little mine mark of the queen sceptre (Spider Trigger round follow-up).
 *
 * <p>It is the picture of {@link MineCommandIndicator}: a small icon floating over every mine the crosshair could fire
 * right now, drawn as a sprite that <b>always turns to the queen</b>, so she can see at a glance that her aim is on a
 * trap instead of on a plain block - and how many traps it covers.
 *
 * <p><b>It is drawn through everything.</b> The mark of a mine that is hiding under a floor is worth nothing if the
 * floor hides the mark as well, and getting that right takes <b>both</b> halves of the trick:
 *
 * <ul>
 * <li>the render type has to be one whose own depth state is a <b>no-op</b> - {@code RenderType.textSeeThrough}, the
 * type a spectator's name tag is drawn with. Its shard is built from {@code gl 519} ({@code GL_ALWAYS}), and the shard
 * of the game only ever <i>enables</i> a comparison: for 519 it does nothing at all, so it leaves the depth test the
 * way it finds it. A {@code entityTranslucent} can not be used for this - it carries a {@code LEQUAL} depth test in
 * its own state and puts it back on every time the batch is flushed,</li>
 * <li>and the depth test is then switched off <b>by hand</b> around the drawing. Doing only this and expecting the
 * render type to leave it alone is what broke the first attempt: the {@code LEQUAL} of the terrain was still in force
 * when the batch was drawn, and the mine block covered its own mark.</li>
 * </ul>
 *
 * <p>Its vertex format is position, colour, texture and lightmap - no overlay and no normal - which is why the
 * vertices below are written with exactly those four elements and no more.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MineCommandIndicatorRenderer
{
    /** The icon, sixteen by sixteen pixels, drawn by the art script of the mod. */
    private static final ResourceLocation MARK_TEXTURE = ResourceLocation
            .fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "textures/misc/mine_marker.png");

    /**
     * The render type of the icon: the see through type of the game, whose own depth state is a no-op, so the depth
     * test this renderer switches off by hand around the drawing stays off while the batch is flushed.
     */
    private static final RenderType MARK_TYPE = RenderType.textSeeThrough(MARK_TEXTURE);

    /** How wide the sprite is: small, because it marks a block and must not hide it. */
    private static final float MARK_SIZE = 0.6F;

    /** How high it floats above the middle of the mine it marks: just over the block, never inside it. */
    private static final float MARK_HEIGHT = 1.3F;

    /** The breathing of the sprite, so the eye finds it without being shouted at. */
    private static final float PULSE_AMPLITUDE = 0.09F;
    private static final float PULSE_SPEED = 0.45F;

    /** Full daylight, so the mark is readable at midnight as well. */
    private static final int FULL_BRIGHT = 0xF000F0;

    private static boolean filteringSet;

    private MineCommandIndicatorRenderer()
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
        List<BlockPos> mines = MineCommandIndicator.minesToMark(minecraft.level);

        if (mines.isEmpty() || minecraft.level == null)
        {
            return;
        }

        softenFiltering(minecraft);

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(MARK_TYPE);

        // The one half of the trick that no render type of the game can do: the shard of "no depth test" is a no-op,
        // so the test has to be switched off here and it stays off through the flush of MARK_TYPE (whose own depth
        // shard does nothing). Without this line the icon is tested against the terrain and the trap covers its mark.
        RenderSystem.disableDepthTest();

        float pulse = 1.0F
                + PULSE_AMPLITUDE * Mth.sin((minecraft.level.getGameTime() + event.getPartialTick()) * PULSE_SPEED);
        float size = MARK_SIZE * pulse;

        for (BlockPos mine : mines)
        {
            poseStack.pushPose();
            poseStack.translate(mine.getX() + 0.5D - camera.x, mine.getY() + MARK_HEIGHT - camera.y,
                    mine.getZ() + 0.5D - camera.z);
            poseStack.mulPose(event.getCamera().rotation());
            poseStack.scale(size, size, size);

            Matrix4f matrix = poseStack.last().pose();
            facing(consumer, matrix);
            facingBackwards(consumer, matrix);

            poseStack.popPose();
        }

        minecraft.renderBuffers().bufferSource().endBatch(MARK_TYPE);
        RenderSystem.enableDepthTest();
    }

    /** The sprite that faces the queen: the pose stack is already turned to her camera, so this is the screen plane. */
    private static void facing(VertexConsumer consumer, Matrix4f matrix)
    {
        float[][] corners = { { -0.5F, 0.5F, 0.0F, 0.0F }, { 0.5F, 0.5F, 1.0F, 0.0F }, { 0.5F, -0.5F, 1.0F, 1.0F },
                { -0.5F, -0.5F, 0.0F, 1.0F } };

        for (float[] corner : corners)
        {
            vertex(consumer, matrix, corner[0], corner[1], corner[2], corner[3]);
        }
    }

    /** The same sprite with the other winding, so it is drawn even where a render type culls back faces. */
    private static void facingBackwards(VertexConsumer consumer, Matrix4f matrix)
    {
        float[][] corners = { { -0.5F, -0.5F, 0.0F, 1.0F }, { 0.5F, -0.5F, 1.0F, 1.0F }, { 0.5F, 0.5F, 1.0F, 0.0F },
                { -0.5F, 0.5F, 0.0F, 0.0F } };

        for (float[] corner : corners)
        {
            vertex(consumer, matrix, corner[0], corner[1], corner[2], corner[3]);
        }
    }

    /**
     * One vertex of a mark quad: full bright, always visible, and exactly the four elements the see through format
     * of the game carries - position, colour, texture and lightmap. Nothing else may be written to it.
     */
    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v)
    {
        consumer.vertex(matrix, x, y, 0.0F)
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
        minecraft.getTextureManager().getTexture(MARK_TEXTURE).setFilter(false, false);
    }
}
