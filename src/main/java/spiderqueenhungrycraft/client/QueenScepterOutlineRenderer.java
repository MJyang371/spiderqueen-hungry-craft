package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
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
 * The crosshair highlight of the queen sceptre (Queen Scepter command patch).
 *
 * <p>Two boxes, and both of them are drawings of this client only:
 * <ul>
 * <li>a pale blue box around whatever creature the crosshair points at right now - it appears the moment the
 * crosshair arrives and is gone the moment it leaves,</li>
 * <li>a warm box around the creature the server really accepted an attack order for, which stays for the four seconds
 * of {@link QueenScepterVisuals#ATTACK_CONFIRM_TICKS} and is deliberately bigger than the hover box, so the answer of
 * the server is never mistaken for a preview.</li>
 * </ul>
 *
 * <p>No glowing tag, no potion, no entity data: a queen sees her own aim, and nobody else sees anything at all.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenScepterOutlineRenderer
{
    /** The crosshair preview: the pale silk blue of the brood. */
    private static final float HOVER_RED = 0.55F;
    private static final float HOVER_GREEN = 0.85F;
    private static final float HOVER_BLUE = 1.0F;

    /** The accepted attack order: warm, so it never looks like the preview. */
    private static final float CONFIRM_RED = 1.0F;
    private static final float CONFIRM_GREEN = 0.45F;
    private static final float CONFIRM_BLUE = 0.15F;

    private static final float ALPHA = 1.0F;

    /** The confirmation box is drawn a little wider, so both of them stay readable at the same time. */
    private static final double HOVER_INFLATE = 0.06D;
    private static final double CONFIRM_INFLATE = 0.22D;

    private QueenScepterOutlineRenderer()
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
        Entity hovered = QueenScepterVisuals.hoveredTarget(minecraft.level);
        Entity confirmed = QueenScepterVisuals.confirmedTarget(minecraft.level);

        if (hovered == null && confirmed == null)
        {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        VertexConsumer consumer = minecraft.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        float partialTick = event.getPartialTick();

        if (confirmed != null)
        {
            LevelRenderer.renderLineBox(poseStack, consumer, boxOf(confirmed, partialTick, CONFIRM_INFLATE),
                    CONFIRM_RED, CONFIRM_GREEN, CONFIRM_BLUE, ALPHA);
        }

        if (hovered != null)
        {
            LevelRenderer.renderLineBox(poseStack, consumer, boxOf(hovered, partialTick, HOVER_INFLATE),
                    HOVER_RED, HOVER_GREEN, HOVER_BLUE, ALPHA);
        }

        minecraft.renderBuffers().bufferSource().endBatch(RenderType.lines());
        poseStack.popPose();
    }

    /** @return the box of that creature at this very frame, moved to where the model is drawn right now. */
    private static AABB boxOf(Entity entity, float partialTick, double inflate)
    {
        // A box on the tick position would lag a whole tick behind the model, so it is moved by the same interpolation
        // the game uses to draw the creature itself.
        double dx = Mth.lerp(partialTick, entity.xo, entity.getX()) - entity.getX();
        double dy = Mth.lerp(partialTick, entity.yo, entity.getY()) - entity.getY();
        double dz = Mth.lerp(partialTick, entity.zo, entity.getZ()) - entity.getZ();

        return entity.getBoundingBox().move(dx, dy, dz).inflate(inflate);
    }
}
