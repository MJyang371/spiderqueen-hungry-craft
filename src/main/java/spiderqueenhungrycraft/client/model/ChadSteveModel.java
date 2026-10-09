package spiderqueenhungrycraft.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.client.animation.ChadSteveAnimations;
import spiderqueenhungrycraft.entity.ChadSteveEntity;

/**
 * The Chad Steve rig (Phase 9A-GAMEPASS1).
 *
 * <p>The geometry, the pivots and the rest pose are exactly the ones of the source Blockbench model, copied bone
 * by bone, so the entity in the game looks like the Blockbench preview and nothing is scaled twice: the model is
 * already drawn at full size, the renderer adds no scale at all.
 *
 * <p>The texture layout is the interesting part. The Blockbench file paints every cube face with its own
 * rectangle of the 64x64 skin, and those rectangles are exactly the vanilla Steve ones (head at 0/0, torso at
 * 16/16, right arm at 40/16, left arm at 32/48, right leg at 0/16, left leg at 16/48, the hammer in the two flat
 * colour patches at 0/32 and 0/40). A Minecraft {@code CubeListBuilder} can only express the vanilla box layout,
 * so every cube keeps <b>its true Blockbench size</b> and gets a texture scale that maps the box layout back onto
 * those rectangles. Without that scale the exporter would place, for example, the torso at 0/0 and the hammer
 * would sample a 94x48 rectangle that does not exist - which is exactly what a naive Blockbench java export
 * produces.
 *
 * <p>Because the animation values of the file are written as offsets on top of the rest pose (that is what
 * Blockbench does), and because the vanilla animation targets of this class are additive as well
 * ({@code ModelPart::offsetRotation} and {@code ModelPart::offsetPos}), the animations of
 * {@link ChadSteveAnimations} can be played unchanged.
 */
@OnlyIn(Dist.CLIENT)
public class ChadSteveModel extends HierarchicalModel<ChadSteveEntity>
{
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "chad_steve"), "main");

    private static final int TEXTURE_WIDTH = 64;
    private static final int TEXTURE_HEIGHT = 64;

    /** The torso leans forward by 15 degrees in the source model. */
    private static final float BODY_LEAN_RADIANS = 0.2618F;

    /** Head, arms and legs carry the opposite 15 degrees in the source model, which keeps them straight. */
    private static final float COUNTER_LEAN_RADIANS = -0.2618F;

    private static final float DEGREES_TO_RADIANS = (float) Math.PI / 180.0F;

    /** The walk cycle of the file covers 1.6 seconds; the client speeds it up with the real walking speed. */
    private static final float WALK_SPEED_SCALE = 5.5F;
    private static final float WALK_SPEED_SCALE_MIN = 0.8F;
    private static final float WALK_SPEED_SCALE_MAX = 1.6F;

    /** How far the head follows its look direction, exactly like a vanilla humanoid head. */
    private static final float HEAD_LOOK_SCALE = 1.0F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final ModelPart hammerAnchor;
    private final ModelPart hammerHandle;
    private final ModelPart hammerHead;

    public ChadSteveModel(ModelPart bakedRoot)
    {
        this.root = bakedRoot.getChild("root");
        this.body = this.root.getChild("body");
        this.head = this.body.getChild("head");
        this.leftArm = this.body.getChild("left_arm");
        this.rightArm = this.body.getChild("right_arm");
        this.leftLeg = this.body.getChild("left_leg");
        this.rightLeg = this.body.getChild("right_leg");
        this.hammerAnchor = this.leftArm.getChild("hammer_anchor");
        this.hammerHandle = this.hammerAnchor.getChild("hammer_handle");
        this.hammerHead = this.hammerHandle.getChild("hammer_head");
    }

    /**
     * Builds the rig once. Every comment names the Blockbench pivot and the skin rectangle the cube samples, so
     * the two can be compared side by side.
     */
    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition meshRoot = mesh.getRoot();

        // The Blockbench root group sits on model y = 24, which is exactly the ground line of a living model.
        PartDefinition root = meshRoot.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // body: pivot (0, 18, 0), lean -15 degrees, cube 18 x 30 x 9, torso layout of the skin at 16/16.
        PartDefinition body = root.addOrReplaceChild("body",
                CubeListBuilder.create().texOffs(36, 41).addBox(-9.0F, -30.0F, -4.5F, 18.0F, 30.0F, 9.0F,
                        CubeDeformation.NONE, 2.25F, 2.5F),
                PartPose.offsetAndRotation(0.0F, -18.0F, 0.0F, BODY_LEAN_RADIANS, 0.0F, 0.0F));

        // head: an 11.5 cube on the vanilla 8x8x8 head layout, so one single scale of 1.4375 fits all six faces.
        body.addOrReplaceChild("head",
                CubeListBuilder.create().texOffs(0, 0).addBox(-5.75F, -6.5F, -9.75F, 11.5F, 11.5F, 11.5F,
                        CubeDeformation.NONE, 1.4375F, 1.4375F),
                PartPose.offsetAndRotation(0.0F, -30.0F, 0.0F, COUNTER_LEAN_RADIANS, 0.0F, 0.0F));

        // left arm: 9 x 29 x 9 on the vanilla 4x12x4 arm layout at 32/48, the hammer hangs from its hand.
        PartDefinition leftArm = body.addOrReplaceChild("left_arm",
                CubeListBuilder.create().texOffs(72, 117).addBox(-4.5F, -3.6F, -4.5F, 9.0F, 29.0F, 9.0F,
                        CubeDeformation.NONE, 2.25F, 2.4166667F),
                PartPose.offsetAndRotation(13.5F, -25.0F, 0.0F, COUNTER_LEAN_RADIANS, 0.0F, 0.0F));

        // The anchor is the hand: the hammer children follow the arm through this chain, nothing else.
        PartDefinition hammerAnchor = leftArm.addOrReplaceChild("hammer_anchor", CubeListBuilder.create(),
                PartPose.offset(2.0F, 21.0F, 0.0F));

        // Handle and head of the hammer are flat colour patches of the skin (brown at 0/32, grey at 0/40), so the
        // whole box layout is squeezed into one such 16x8 patch with a texture scale of 8.
        PartDefinition hammerHandle = hammerAnchor.addOrReplaceChild("hammer_handle",
                CubeListBuilder.create().texOffs(16, 264).addBox(-1.5F, -2.0F, -33.0F, 3.0F, 4.0F, 44.0F,
                        CubeDeformation.NONE, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        hammerHandle.addOrReplaceChild("hammer_head",
                CubeListBuilder.create().texOffs(16, 328).addBox(-4.5F, -9.0F, -4.5F, 9.0F, 17.0F, 9.0F,
                        CubeDeformation.NONE, 8.0F, 8.0F),
                PartPose.offset(0.0F, 0.0F, -25.5F));

        // right arm: the Blockbench pivot is one pixel lower than the left one, that asymmetry is kept untouched.
        body.addOrReplaceChild("right_arm",
                CubeListBuilder.create().texOffs(90, 39).addBox(-4.5F, -3.6F, -4.5F, 9.0F, 29.0F, 9.0F,
                        CubeDeformation.NONE, 2.25F, 2.4166667F),
                PartPose.offsetAndRotation(-13.5F, -26.0F, 0.0F, COUNTER_LEAN_RADIANS, 0.0F, 0.0F));

        // legs: the feet end on model y = 24, the ground line, so he stands on the floor by construction.
        body.addOrReplaceChild("left_leg",
                CubeListBuilder.create().texOffs(36, 69).addBox(-4.5F, 0.0F, -4.5F, 9.0F, 18.0F, 9.0F,
                        CubeDeformation.NONE, 2.25F, 1.5F),
                PartPose.offsetAndRotation(4.5F, 0.0F, 0.0F, COUNTER_LEAN_RADIANS, 0.0F, 0.0F));

        body.addOrReplaceChild("right_leg",
                CubeListBuilder.create().texOffs(0, 21).addBox(-4.5F, 0.0F, -4.5F, 9.0F, 18.0F, 9.0F,
                        CubeDeformation.NONE, 2.25F, 1.5F),
                PartPose.offsetAndRotation(-4.5F, 0.0F, 0.0F, COUNTER_LEAN_RADIANS, 0.0F, 0.0F));

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * Plays the three animations of the file. Priority is hammer_slam over walk over idle, and the head keeps
     * following its look direction on top of whatever the animation does.
     */
    @Override
    public void setupAnim(ChadSteveEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        // Start from the rest pose every frame: the animation targets are additive, so a leftover pose of the
        // previous frame would otherwise pile up.
        this.root().getAllParts().forEach(ModelPart::resetPose);

        this.animate(entity.idleAnimationState, ChadSteveAnimations.IDLE, ageInTicks);
        this.animate(entity.walkAnimationState, ChadSteveAnimations.WALK, ageInTicks, walkSpeedScale(entity));
        this.animate(entity.slamAnimationState, ChadSteveAnimations.HAMMER_SLAM, ageInTicks);

        // The head look goes on top of the animation, exactly like a vanilla humanoid head.
        this.head.yRot += netHeadYaw * DEGREES_TO_RADIANS * HEAD_LOOK_SCALE;
        this.head.xRot += headPitch * DEGREES_TO_RADIANS * HEAD_LOOK_SCALE;
    }

    /**
     * The walk cycle of the file is a fixed 1.6 second loop. Scaling its playback with the real walking speed
     * keeps the legs roughly in step with the ground instead of letting him skate.
     */
    private static float walkSpeedScale(ChadSteveEntity entity)
    {
        float speed = (float) entity.getDeltaMovement().horizontalDistance();
        return Mth.clamp(speed * WALK_SPEED_SCALE, WALK_SPEED_SCALE_MIN, WALK_SPEED_SCALE_MAX);
    }

    @Override
    public ModelPart root()
    {
        return this.root;
    }
}
