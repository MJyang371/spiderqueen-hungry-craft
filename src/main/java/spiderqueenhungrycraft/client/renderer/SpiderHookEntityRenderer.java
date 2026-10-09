package spiderqueenhungrycraft.client.renderer;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.hook.HookOrigin;
import spiderqueenhungrycraft.hook.SpiderHookEntity;

/**
 * Draws the web head of a spider hook and the silk that ties it to the rod (feature SPIDER-HOOK).
 *
 * <p>The web head itself is one sheet of the vanilla cobweb, always turned towards the camera - the way a
 * thrown thing like a snowball or an ender pearl is drawn - and it looks the same in every state of the web:
 * while it flies, while it hangs on a block and while it holds a creature. The cobweb <b>block</b> model is
 * deliberately not used: its many thin threads flicker into a shimmering grid at this size and read as a block
 * dragged through the world instead of a web that was thrown.
 *
 * <p>The silk is one white line from {@link HookOrigin} - the tip of the rod in the hand of the thrower - to
 * the middle of the web. It is computed fresh every frame, so it follows the rod while the player walks, jumps,
 * swings and turns, and it follows a caught creature wherever it runs: this is the same way the vanilla fishing
 * rod draws its line, down to the sag of the thread. There is deliberately no line entity and no second model;
 * the silk only exists while it is drawn.
 */
public class SpiderHookEntityRenderer extends EntityRenderer<SpiderHookEntity>
{
    /** The sheet the web head is drawn from: the very cobweb the world knows, only as a sprite. */
    private static final ResourceLocation COBWEB_SPRITE = new ResourceLocation("minecraft:block/cobweb");

    /** How big that sheet is drawn, as a share of one block. */
    private static final float WEB_SCALE = 0.55F;

    /** Half of the sheet, before the scale: the quad is one block wide and centred on the web. */
    private static final float SHEET_HALF = 0.5F;

    /** How many pieces the silk is cut into. More pieces means a smoother sag. */
    private static final int SILK_SEGMENTS = 16;

    /** The silk is white, as the design asks, and never transparent. */
    private static final int SILK_RED = 255;
    private static final int SILK_GREEN = 255;
    private static final int SILK_BLUE = 255;
    private static final int SILK_ALPHA = 255;

    public SpiderHookEntityRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public void render(SpiderHookEntity hook, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        this.renderWebSheet(poseStack, buffer, packedLight);
        this.renderSilk(hook, partialTick, poseStack, buffer);

        super.render(hook, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /**
     * The web head is never culled, and this is the whole point of the silk: the thread is drawn from the hand of
     * its thrower, so it has to stay visible even when the little web head itself is not - behind the player, off
     * to the side of the screen or far away. Vanilla culls an entity by distance and by the frustum of the camera
     * before it ever asks its renderer, which would take the whole silk away together with the head, and a rod
     * that is held would show no thread at all.
     *
     * <p>What costs nothing is worth nothing: there is one web per thrower, so asking this renderer a little more
     * often is nothing at all, and the geometry that lies outside the screen is thrown away by the graphics card
     * exactly as any other geometry that is not seen.
     */
    @Override
    public boolean shouldRender(SpiderHookEntity hook, Frustum frustum, double cameraX, double cameraY,
            double cameraZ)
    {
        return true;
    }

    /**
     * The web head is one sheet of the vanilla cobweb, always turned towards the camera. It is the same sheet in
     * every state of the web - flying, hanging on a block, holding a creature - because a web is a web, and the
     * silk already tells the player where the web is tied to.
     */
    private void renderWebSheet(PoseStack poseStack, MultiBufferSource buffer, int packedLight)
    {
        Camera camera = this.entityRenderDispatcher.camera;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(COBWEB_SPRITE);

        poseStack.pushPose();
        // The sheet lies in the local XY plane, so these two turns bring that plane in front of the camera. They
        // are the very turns the vanilla name tag uses, so the front of the sheet always faces the viewer.
        poseStack.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
        poseStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
        poseStack.scale(WEB_SCALE, WEB_SCALE, WEB_SCALE);

        // No culling: a billboard must be seen from the side it was turned to, never be eaten by a back face.
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        PoseStack.Pose pose = poseStack.last();

        sheetVertex(consumer, pose, -SHEET_HALF, -SHEET_HALF, sprite.getU1(), sprite.getV1(), packedLight);
        sheetVertex(consumer, pose, -SHEET_HALF, SHEET_HALF, sprite.getU1(), sprite.getV0(), packedLight);
        sheetVertex(consumer, pose, SHEET_HALF, SHEET_HALF, sprite.getU0(), sprite.getV0(), packedLight);
        sheetVertex(consumer, pose, SHEET_HALF, -SHEET_HALF, sprite.getU0(), sprite.getV1(), packedLight);

        poseStack.popPose();
    }

    /**
     * One corner of the web sheet. The face of the quad looks back at the camera, which is why the normal points
     * the way it does, and the texture runs from the left edge of the sprite on the left of the screen.
     */
    private static void sheetVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v,
            int packedLight)
    {
        consumer.vertex(pose.pose(), x, y, 0.0F)
                .color(SILK_RED, SILK_GREEN, SILK_BLUE, SILK_ALPHA)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(pose.normal(), 0.0F, 0.0F, -1.0F)
                .endVertex();
    }

    /**
     * The silk, from the tip of the rod to the web. The pose stack already stands on the interpolated
     * position of the web, so the thread is drawn in coordinates relative to the web itself - the very
     * trick the vanilla fishing rod uses.
     */
    private void renderSilk(SpiderHookEntity hook, float partialTick, PoseStack poseStack, MultiBufferSource buffer)
    {
        Player owner = hook.getOwningPlayer();

        if (owner == null)
        {
            return;
        }

        Vec3 tip = HookOrigin.rodTip(owner, partialTick, this.firstPersonViewOffset(owner));
        float dx = (float) (tip.x - Mth.lerp(partialTick, hook.xo, hook.getX()));
        float dy = (float) (tip.y - Mth.lerp(partialTick, hook.yo, hook.getY()));
        float dz = (float) (tip.z - Mth.lerp(partialTick, hook.zo, hook.getZ()));

        VertexConsumer consumer = buffer.getBuffer(RenderType.lineStrip());
        PoseStack.Pose pose = poseStack.last();

        for (int segment = 0; segment <= SILK_SEGMENTS; segment++)
        {
            silkVertex(dx, dy, dz, consumer, pose, (float) segment / SILK_SEGMENTS,
                    (float) (segment + 1) / SILK_SEGMENTS);
        }
    }

    /**
     * @return the point on the camera plane where the player sees his own rod tip, or null for anybody who
     *         is looked at from the outside. Only the camera of the player himself knows where his own rod
     *         tip is on the screen, so only he gets that point; for everybody else {@link HookOrigin}
     *         computes the tip from the body, exactly like the vanilla rod line does.
     */
    @Nullable
    private Vec3 firstPersonViewOffset(Player owner)
    {
        Minecraft minecraft = Minecraft.getInstance();

        if (!minecraft.options.getCameraType().isFirstPerson() || owner != minecraft.player)
        {
            return null;
        }

        double fovScale = HookOrigin.FOV_SCALE / (double) minecraft.options.fov().get().intValue();

        return this.entityRenderDispatcher.camera.getNearPlane()
                .getPointOnPlane((float) HookOrigin.rodSide(owner) * HookOrigin.firstPersonSide(),
                        HookOrigin.firstPersonDown())
                .scale(fovScale);
    }

    /**
     * One point of the silk. It is the shape the vanilla fishing rod line has: the thread leaves the rod tip
     * a little above the hand and hangs between the two ends, so even a taut web still looks like silk. How
     * far above the tip the thread is drawn is a configuration value, so it can be set to nothing.
     */
    private static void silkVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose,
            float from, float to)
    {
        float startX = x * from;
        float startY = y * (from * from + from) * 0.5F + HookOrigin.tipLift();
        float startZ = z * from;
        float stepX = x * to - startX;
        float stepY = y * (to * to + to) * 0.5F + HookOrigin.tipLift() - startY;
        float stepZ = z * to - startZ;
        float length = Mth.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);

        if (length > 1.0E-5F)
        {
            stepX /= length;
            stepY /= length;
            stepZ /= length;
        }

        consumer.vertex(pose.pose(), startX, startY, startZ)
                .color(SILK_RED, SILK_GREEN, SILK_BLUE, SILK_ALPHA)
                .normal(pose.normal(), stepX, stepY, stepZ)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(SpiderHookEntity hook)
    {
        // A block model is not drawn from an entity texture: it is drawn from the block atlas, which is the
        // very sheet the vanilla cobweb is baked into.
        return InventoryMenu.BLOCK_ATLAS;
    }
}
