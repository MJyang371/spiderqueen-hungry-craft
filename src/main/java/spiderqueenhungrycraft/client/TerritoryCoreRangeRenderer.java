package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Draws the protected range of a territory core (Phase LAIR-CORE-1).
 *
 * <p>The outline is the section aligned box of {@link spiderqueenhungrycraft.territory.TerritoryCoreIndex}, drawn as
 * wireframe in the world, at the very last stage of the level render, and with the depth test switched off on purpose:
 * a range that is hidden by the rock it protects would be useless, so it is visible through anything.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TerritoryCoreRangeRenderer
{
    /** The colour of the outline: the pale blue of spider silk in the dark. */
    private static final float RED = 0.45F;
    private static final float GREEN = 0.85F;
    private static final float BLUE = 1.0F;
    private static final float ALPHA = 1.0F;

    private TerritoryCoreRangeRenderer()
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
        AABB box = TerritoryCoreRangeClient.visibleBox(minecraft.level);

        if (box == null)
        {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        // Through walls: the protection does not care about walls either.
        RenderSystem.disableDepthTest();
        LevelRenderer.renderLineBox(poseStack, consumer, box, RED, GREEN, BLUE, ALPHA);
        RenderSystem.enableDepthTest();

        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }
}
