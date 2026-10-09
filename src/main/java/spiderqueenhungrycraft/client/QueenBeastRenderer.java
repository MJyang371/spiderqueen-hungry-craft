package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import spiderqueenhungrycraft.beast.QueenBeastConstants;

/**
 * The third person body of the Queen Beast form (Phase 8A).
 *
 * <p>It reuses the vanilla spider geometry and the vanilla spider texture, nothing is added to the mod: no new
 * model, no new texture, no GeckoLib, and above all no real spider entity that would only exist to be drawn.
 * The model is baked from the vanilla spider layer and drawn with the very same transform chain a living
 * renderer uses, only with the beast scale, so the legs really stand on the ground.
 *
 * <p>The animation is the vanilla spider animation - walking legs, a head that follows the look direction and
 * an idle pose - plus the code driven head lunge of a bite (phase 8B2). No new model and no new texture.
 */
public final class QueenBeastRenderer
{
    /** The vanilla spider texture. Referenced, never copied into the mod. */
    private static final ResourceLocation SPIDER_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/spider/spider.png");

    /** The ground offset of a living model, straight from the vanilla livng renderer. */
    private static final float MODEL_GROUND_TRANSLATION = -1.501F;

    /** The walking animation of an entity never exceeds one. */
    private static final float MAX_WALK_ANIMATION_SPEED = 1.0F;

    /**
     * The model parts the bite lunge moves, in the fixed order {@link LungePose} keeps them: the head, the
     * thorax, the abdomen and the two bracing front legs. Every other part, above all the walking legs, is
     * never touched by the animation.
     */
    private static final String[] LUNGE_PARTS = { "head", "body0", "body1", "right_front_leg", "left_front_leg" };

    private static final int HEAD = 0;
    private static final int BODY0 = 1;
    private static final int BODY1 = 2;
    private static final int RIGHT_FRONT_LEG = 3;
    private static final int LEFT_FRONT_LEG = 4;

    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    private static SpiderModel<AbstractClientPlayer> spiderModel;

    private QueenBeastRenderer()
    {
    }

    /** Draws that player as the giant spider. The pose stack is the one the player renderer was called with. */
    public static void render(AbstractClientPlayer player, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight)
    {
        SpiderModel<AbstractClientPlayer> model = spiderModel();

        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float headYaw = Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - bodyYaw;
        float headPitch = Mth.lerp(partialTick, player.xRotO, player.getXRot());
        float limbSwing = player.walkAnimation.position(partialTick);
        float limbSwingAmount = Math.min(player.walkAnimation.speed(partialTick), MAX_WALK_ANIMATION_SPEED);
        float lunge = QueenBeastBiteClient.lungeAmount(player.getId(), partialTick);

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));

        // The whole animal dips towards the ground at the peak of the bite. This axis is the world Y of the
        // rotated frame, so the dip never depends on where the spider happens to look.
        if (lunge != 0.0F)
        {
            poseStack.translate(0.0F, -lunge * QueenBeastConstants.BEAST_BITE_BODY_DIP, 0.0F);
        }

        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.scale(QueenBeastConstants.BEAST_RENDER_SCALE, QueenBeastConstants.BEAST_RENDER_SCALE,
                QueenBeastConstants.BEAST_RENDER_SCALE);
        poseStack.translate(0.0F, MODEL_GROUND_TRANSLATION, 0.0F);

        model.setupAnim(player, limbSwing, limbSwingAmount, player.tickCount + partialTick, headYaw, headPitch);

        // The lunge of a bite (8B2, strengthened by 8B-POLISH-1). The pose is taken back right after the draw
        // call, so it can never pile up frame over frame and the walking animation of the legs stays intact.
        LungePose pose = new LungePose(model);

        if (lunge != 0.0F)
        {
            applyLunge(pose, lunge);
        }

        model.renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityCutoutNoCull(SPIDER_TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        pose.restore();
        poseStack.popPose();
    }

    /**
     * Moves the head, the thorax, the abdomen and the bracing front legs for one frame of the bite.
     *
     * <p>In the model space of a spider the head faces -Z and +Y points down, so a lunge is a negative Z shift
     * plus a positive X rotation, and the body sinks with a positive Y shift.
     */
    private static void applyLunge(LungePose pose, float lunge)
    {
        ModelPart head = pose.part(HEAD);
        head.z -= lunge * QueenBeastConstants.BEAST_BITE_HEAD_LUNGE;
        head.xRot += radians(lunge * QueenBeastConstants.BEAST_BITE_HEAD_PITCH_DEGREES);

        float bodyForward = lunge * QueenBeastConstants.BEAST_BITE_BODY_LUNGE;
        float bodyDrop = lunge * QueenBeastConstants.BEAST_BITE_BODY_DROP;
        float bodyPitch = radians(lunge * QueenBeastConstants.BEAST_BITE_BODY_PITCH_DEGREES);

        ModelPart body0 = pose.part(BODY0);
        body0.z -= bodyForward;
        body0.y += bodyDrop;
        body0.xRot += bodyPitch;

        // The abdomen follows with a fraction only, which keeps the whole animal together instead of stretching.
        float follow = QueenBeastConstants.BEAST_BITE_ABDOMEN_FOLLOW;
        ModelPart body1 = pose.part(BODY1);
        body1.z -= bodyForward * follow;
        body1.y += bodyDrop * follow;
        body1.xRot += bodyPitch * follow;

        // The front legs spread a little as if the spider braces itself for the impact.
        float brace = radians(lunge * QueenBeastConstants.BEAST_BITE_LEG_BRACE_DEGREES);
        pose.part(RIGHT_FRONT_LEG).yRot -= brace;
        pose.part(LEFT_FRONT_LEG).yRot += brace;
    }

    private static float radians(float degrees)
    {
        return degrees * DEGREES_TO_RADIANS;
    }

    /**
     * A snapshot of the parts the bite poses, so the animation can be applied and taken back completely. The
     * model is shared by every beast on screen, so a leftover pose would otherwise leak into the next frame.
     */
    private static final class LungePose
    {
        private final ModelPart[] parts = new ModelPart[LUNGE_PARTS.length];
        private final float[][] saved = new float[LUNGE_PARTS.length][];

        private LungePose(SpiderModel<AbstractClientPlayer> model)
        {
            for (int index = 0; index < LUNGE_PARTS.length; index++)
            {
                ModelPart part = model.root().getChild(LUNGE_PARTS[index]);
                this.parts[index] = part;
                this.saved[index] = new float[] { part.x, part.y, part.z, part.xRot, part.yRot, part.zRot };
            }
        }

        private ModelPart part(int index)
        {
            return this.parts[index];
        }

        private void restore()
        {
            for (int index = 0; index < this.parts.length; index++)
            {
                ModelPart part = this.parts[index];
                float[] values = this.saved[index];

                part.x = values[0];
                part.y = values[1];
                part.z = values[2];
                part.xRot = values[3];
                part.yRot = values[4];
                part.zRot = values[5];
            }
        }
    }

    /** The vanilla spider model, baked once from the vanilla layer definition. */
    private static SpiderModel<AbstractClientPlayer> spiderModel()
    {
        if (spiderModel == null)
        {
            ModelPart root = Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.SPIDER);
            spiderModel = new SpiderModel<>(root);
        }

        return spiderModel;
    }
}
