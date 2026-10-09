
package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The drawing half of the RTS selection (Brood RTS Mouse Selection round).
 *
 * <p>Two kinds of boxes, both of them edges only and both of them this client's own painting:
 *
 * <ul>
 * <li>the <b>drag box</b>, white and translucent, while the right button is held down: an axis aligned volume of the
 * same two points the release is about to send, so what the queen sees is exactly what she asks for,</li>
 * <li>the <b>selection highlight</b>, a calm green, for three seconds after a selection - on the spiders the drag
 * caught and on no others.</li>
 * </ul>
 *
 * <p>The pale blue hover of the crosshair and the warm box of an accepted attack order (see
 * {@link QueenScepterOutlineRenderer}) keep their colours and are drawn beside these two, so no colour ever means two
 * things at once. Nothing here is sent anywhere: another player sees neither the box nor the highlight.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenScepterSelectionBoxRenderer
{
    /** The drag box: white, and only its edges - never a filled block of colour on the ground. */
    private static final float DRAG_RED = 1.0F;
    private static final float DRAG_GREEN = 1.0F;
    private static final float DRAG_BLUE = 1.0F;
    private static final float DRAG_ALPHA = 0.65F;

    /** A selected spider: green, so a selection can never be mistaken for the blue hover of the crosshair. */
    private static final float SELECT_RED = 0.4F;
    private static final float SELECT_GREEN = 1.0F;
    private static final float SELECT_BLUE = 0.5F;
    private static final float SELECT_ALPHA = 1.0F;

    private static final double SELECT_INFLATE = 0.12D;

    private QueenScepterSelectionBoxRenderer()
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
        ClientLevel level = minecraft.level;
        AABB drag = BroodSelectionBoxClient.dragBox();
        boolean highlight = BroodSelectionBoxClient.highlightActive();

        if (level == null || (drag == null && !highlight))
        {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());

        if (drag != null)
        {
            LevelRenderer.renderLineBox(poseStack, consumer, drag, DRAG_RED, DRAG_GREEN, DRAG_BLUE, DRAG_ALPHA);
        }

        if (highlight)
        {
            float partialTick = event.getPartialTick();

            // Every entity of this very client whose id the server put into the selection echo: the spiders of the
            // brood and, since the Necromancy IV round, the contract dragons of Ice and Fire as well. Which ids those
            // are is decided on the server, so this drawing never invents a unit and never wrongs one.
            for (Entity entity : level.entitiesForRendering())
            {
                if (BroodSelectionBoxClient.isHighlighted(entity.getId()))
                {
                    LevelRenderer.renderLineBox(poseStack, consumer, boxOf(entity, partialTick),
                            SELECT_RED, SELECT_GREEN, SELECT_BLUE, SELECT_ALPHA);
                }
            }
        }

        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    /** @return the box of that spider at this very frame, moved to where its model is drawn right now. */
    private static AABB boxOf(Entity entity, float partialTick)
    {
        double dx = Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX();
        double dy = Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY();
        double dz = Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ();

        return entity.getBoundingBox().move(dx, dy, dz).inflate(SELECT_INFLATE);
    }
}
