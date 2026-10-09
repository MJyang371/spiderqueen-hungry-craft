package spiderqueenhungrycraft.client.model;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * 鐏电伀榫?(soul fire dragon): <b>the model of this mod's own unit</b>, built out of boxes in code (round 7D).
 *
 * <p><b>Why this class exists at all.</b> Round 7C drew the dragon with the Tabula model of the other mod
 * ({@code /assets/iceandfire/models/tabula/firedragon/firedragon_Ground.tbl}) and that is exactly what failed in the
 * field: the path was handed to Citadel without its {@code .tbl} suffix, the resource stream came back null, the
 * renderer swallowed the {@code NullPointerException} to protect the client and the unit was therefore drawn as
 * <b>nothing at all</b>. A unit of this mod may never again be invisible because an optional mod moved one file. This
 * model has no dependency of any kind: every cube, every pivot and every pose below is written here, the layer is baked
 * by the ordinary {@code EntityRenderersEvent.RegisterLayerDefinitions} of the game, and the texture ships in this
 * mod's own resources.
 *
 * <p><b>The shape.</b> A stage four skeleton dragon (the remains every bone dragon of the other mod leaves behind), at
 * the size of the hitbox of the entity (3.0 x 3.5 blocks): the ground line of the model is {@code y = 24}, the toes
 * stand on it, the body sits at knee height, the neck rises and reaches forward, the tail balances behind and the two
 * wings spread wider than the unit is long. The model's front is <b>-Z</b>, exactly like every vanilla living model,
 * which is what makes the ordinary yaw rotation of the renderer point it the way the unit looks.
 *
 * <p><b>The poses.</b> Standing, walking, flying (a wing beat), hovering (the same beat, slower) and sitting (the legs
 * folded under the body, the pose of a post). The jaw opens while the unit breathes soul fire
 * ({@link SoulfireDragonEntity#isBreathing()}), so the stream leaves the mouth the model shows.
 */
@OnlyIn(Dist.CLIENT)
public class SoulfireDragonModel extends HierarchicalModel<SoulfireDragonEntity>
{
    /** The layer of this model, baked once by the client setup of the game. */
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            new ResourceLocation(SpiderQueenHungryCraft.MODID, "soulfire_dragon"), "main");

    /** The texture layout. No cube of the model samples outside it. */
    private static final int TEXTURE_WIDTH = 128;
    private static final int TEXTURE_HEIGHT = 128;

    /** Where the bright soul fire patch of the texture starts: the eyes and the inside of the mouth. */
    private static final int GLOW_U = 96;
    private static final int GLOW_V = 0;

    /** Where the dim soul fire patch starts: the wing membranes and the tip of the tail. */
    private static final int MEMBRANE_U = 0;
    private static final int MEMBRANE_V = 96;

    /** How much of the look the head takes; the last joint of the neck carries the rest of it. */
    private static final float HEAD_LOOK_SHARE = 0.55F;

    /** How high the wing beats, in radians. */
    private static final float WING_BEAT = 0.80F;

    /** The rest angle of a folded wing, in radians. */
    private static final float WING_FOLDED = 1.05F;

    /** The rest angle of a spread wing, in radians. */
    private static final float WING_SPREAD = 0.25F;

    /** How far the jaw opens for a breath, in radians. */
    private static final float JAW_OPEN = 0.62F;

    /** How much the whole body drops when the unit sits down, in model units. */
    private static final float SIT_DROP = 5.0F;

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart chest;
    private final ModelPart neck;
    private final ModelPart neckTip;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart tail;
    private final ModelPart tailTip;
    private final ModelPart wingLeft;
    private final ModelPart wingRight;
    private final ModelPart wingMembraneLeft;
    private final ModelPart wingMembraneRight;
    private final ModelPart legFrontLeft;
    private final ModelPart legFrontRight;
    private final ModelPart legBackLeft;
    private final ModelPart legBackRight;

    public SoulfireDragonModel(ModelPart bakedRoot)
    {
        this.root = bakedRoot.getChild("root");
        this.body = this.root.getChild("body");
        this.chest = this.body.getChild("chest");
        this.neck = this.body.getChild("neck");
        this.neckTip = this.neck.getChild("neck_tip");
        this.head = this.neckTip.getChild("head");
        this.jaw = this.head.getChild("jaw");
        this.tail = this.body.getChild("tail");
        this.tailTip = this.tail.getChild("tail_tip");
        this.wingLeft = this.body.getChild("wing_left");
        this.wingRight = this.body.getChild("wing_right");
        this.wingMembraneLeft = this.wingLeft.getChild("membrane");
        this.wingMembraneRight = this.wingRight.getChild("membrane");
        this.legFrontLeft = this.body.getChild("leg_front_left");
        this.legFrontRight = this.body.getChild("leg_front_right");
        this.legBackLeft = this.body.getChild("leg_back_left");
        this.legBackRight = this.body.getChild("leg_back_right");
    }

    @Override
    public ModelPart root()
    {
        return this.root;
    }

    /**
     * Builds the bones once. Every pivot is written in the same units the game uses (1/16 of a block, y downwards)
     * and the ground line of the model is {@code y = 24}, which is where the toes of the four legs end.
     */
    public static LayerDefinition createBodyLayer()
    {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition meshRoot = mesh.getRoot();

        PartDefinition root = meshRoot.addOrReplaceChild("root", CubeListBuilder.create(),
                PartPose.offset(0.0F, 24.0F, 0.0F));

        // The body sits at knee height and runs from the hips to the shoulders.
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-6.0F, -6.0F, -14.0F, 12.0F, 12.0F, 26.0F),
                PartPose.offset(0.0F, -10.0F, 0.0F));

        // The chest is the bulk in front of the shoulders, where the wings and the front legs hang.
        body.addOrReplaceChild("chest", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-7.0F, -8.0F, -8.0F, 14.0F, 14.0F, 12.0F),
                PartPose.offset(0.0F, 2.0F, -9.0F));

        // The neck rises and reaches forward, and the head sits at its end.
        PartDefinition neck = body.addOrReplaceChild("neck", CubeListBuilder.create()
                .texOffs(0, 40).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 6.0F, 7.0F),
                PartPose.offset(0.0F, -3.0F, -14.0F));

        PartDefinition neckTip = neck.addOrReplaceChild("neck_tip", CubeListBuilder.create()
                .texOffs(40, 40).addBox(-3.0F, -3.0F, -7.0F, 6.0F, 6.0F, 7.0F),
                PartPose.offset(0.0F, 0.0F, -7.0F));

        // The skull of a dragon: long and low, with the horns of a stage four skeleton.
        PartDefinition head = neckTip.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 56).addBox(-4.0F, -4.0F, -11.0F, 8.0F, 8.0F, 11.0F),
                PartPose.offset(0.0F, -1.0F, -7.0F));

        head.addOrReplaceChild("jaw", CubeListBuilder.create()
                .texOffs(56, 68).addBox(-3.5F, 0.0F, -8.0F, 7.0F, 2.0F, 8.0F),
                PartPose.offset(0.0F, 3.0F, -2.0F));

        // The inside of the mouth and the two eyes sample the bright soul fire patch of the texture.
        head.addOrReplaceChild("mouth", CubeListBuilder.create()
                .texOffs(GLOW_U, GLOW_V).addBox(-3.0F, -1.0F, -5.0F, 6.0F, 2.0F, 5.0F),
                PartPose.offset(0.0F, 1.0F, -6.0F));

        head.addOrReplaceChild("eye_left", CubeListBuilder.create()
                .texOffs(GLOW_U, GLOW_V).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(-2.8F, -2.0F, -9.0F));

        head.addOrReplaceChild("eye_right", CubeListBuilder.create()
                .texOffs(GLOW_U, GLOW_V).addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(2.8F, -2.0F, -9.0F));

        head.addOrReplaceChild("horn_left", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(-2.0F, -4.0F, -8.0F));

        head.addOrReplaceChild("horn_right", CubeListBuilder.create()
                .texOffs(0, 80).addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offset(2.0F, -4.0F, -8.0F));

        // The tail balances behind; its tip samples the dim soul fire patch together with the wing membranes.
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(40, 56).addBox(-3.0F, -3.0F, 0.0F, 6.0F, 6.0F, 8.0F),
                PartPose.offset(0.0F, -2.0F, 12.0F));

        tail.addOrReplaceChild("tail_tip", CubeListBuilder.create()
                .texOffs(MEMBRANE_U, MEMBRANE_V).addBox(-2.0F, -2.0F, 0.0F, 4.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 1.0F, 8.0F));

        // The wings: a bone arm and a wide membrane, folded at rest and beating in the air.
        PartDefinition wingLeft = body.addOrReplaceChild("wing_left", CubeListBuilder.create()
                .texOffs(44, 24).addBox(-20.0F, -1.5F, -2.0F, 20.0F, 3.0F, 6.0F),
                PartPose.offset(-5.0F, -8.0F, -8.0F));

        wingLeft.addOrReplaceChild("membrane", CubeListBuilder.create()
                .texOffs(MEMBRANE_U, MEMBRANE_V).addBox(-22.0F, -0.5F, -13.0F, 22.0F, 1.0F, 26.0F),
                PartPose.offset(-20.0F, 0.0F, 0.0F));

        PartDefinition wingRight = body.addOrReplaceChild("wing_right", CubeListBuilder.create()
                .texOffs(44, 24).mirror().addBox(0.0F, -1.5F, -2.0F, 20.0F, 3.0F, 6.0F),
                PartPose.offset(5.0F, -8.0F, -8.0F));

        wingRight.addOrReplaceChild("membrane", CubeListBuilder.create()
                .texOffs(MEMBRANE_U, MEMBRANE_V).mirror().addBox(0.0F, -0.5F, -13.0F, 22.0F, 1.0F, 26.0F),
                PartPose.offset(20.0F, 0.0F, 0.0F));

        // The four legs. The toes end exactly on the ground line of the model, so the unit stands on its feet.
        body.addOrReplaceChild("leg_front_left", CubeListBuilder.create()
                .texOffs(16, 80).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offset(-4.0F, 2.0F, -10.0F));

        body.addOrReplaceChild("leg_front_right", CubeListBuilder.create()
                .texOffs(16, 80).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 8.0F, 4.0F),
                PartPose.offset(4.0F, 2.0F, -10.0F));

        body.addOrReplaceChild("leg_back_left", CubeListBuilder.create()
                .texOffs(16, 80).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offset(-4.0F, 2.0F, 9.0F));

        body.addOrReplaceChild("leg_back_right", CubeListBuilder.create()
                .texOffs(16, 80).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offset(4.0F, 2.0F, 9.0F));

        return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
    }

    /**
     * The pose of the moment, read from the unit and from nothing else.
     *
     * <p>Every part is reset first, so the pose of a frame can never leak into the next one: the wings fold or beat,
     * the legs walk or tuck, the tail balances, the neck carries part of the look of the queen and the jaw opens while
     * the soul fire runs.
     */
    @Override
    public void setupAnim(SoulfireDragonEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        this.root.getAllParts().forEach(ModelPart::resetPose);

        // The look: the queen points somewhere, and the head and the last joint of the neck follow her.
        this.head.yRot += netHeadYaw * Mth.DEG_TO_RAD * HEAD_LOOK_SHARE;
        this.head.xRot += headPitch * Mth.DEG_TO_RAD * HEAD_LOOK_SHARE;
        this.neckTip.yRot += netHeadYaw * Mth.DEG_TO_RAD * 0.22F;
        this.neckTip.xRot += headPitch * Mth.DEG_TO_RAD * 0.18F;

        boolean airborne = entity.isFlying() || entity.isHovering();
        float beat = Mth.cos(ageInTicks * (airborne ? 0.42F : 0.18F)) * WING_BEAT;

        if (airborne)
        {
            // In the air: the wings beat, the legs hang tucked and the neck reaches forward.
            this.body.xRot = 0.10F;
            this.neck.xRot = -0.18F;
            this.wingLeft.zRot = WING_SPREAD + beat;
            this.wingRight.zRot = -WING_SPREAD - beat;
            this.wingMembraneLeft.zRot = beat * 0.45F;
            this.wingMembraneRight.zRot = -beat * 0.45F;
            this.legFrontLeft.xRot = 1.25F;
            this.legFrontRight.xRot = 1.25F;
            this.legBackLeft.xRot = 1.25F;
            this.legBackRight.xRot = 1.25F;
            this.tail.xRot = -0.12F;
            this.tail.yRot = Mth.cos(ageInTicks * 0.12F) * 0.10F;
            this.tailTip.yRot = Mth.cos(ageInTicks * 0.12F + 0.6F) * 0.14F;
        }
        else
        {
            // On the ground: four legs walk, the wings are folded on the back and the tail sways with the stride.
            float stride = Mth.cos(limbSwing * 0.6662F) * 1.4F * limbSwingAmount;
            this.legFrontLeft.xRot = stride;
            this.legFrontRight.xRot = -stride;
            this.legBackLeft.xRot = -stride;
            this.legBackRight.xRot = stride;
            this.wingLeft.zRot = WING_FOLDED;
            this.wingRight.zRot = -WING_FOLDED;
            this.wingMembraneLeft.zRot = 0.35F;
            this.wingMembraneRight.zRot = -0.35F;
            this.tail.xRot = 0.06F + Mth.cos(limbSwing * 0.3F) * 0.05F * limbSwingAmount;
            this.tail.yRot = Mth.sin(limbSwing * 0.3F) * 0.18F * limbSwingAmount;
            this.tailTip.yRot = Mth.sin(limbSwing * 0.3F + 0.5F) * 0.22F * limbSwingAmount;
        }

        // The pose of a garrison: the body comes down onto legs folded under it, where it stands until it is called.
        if (entity.isSitting() && !airborne)
        {
            this.body.y += SIT_DROP;
            this.legFrontLeft.xRot = 1.35F;
            this.legFrontRight.xRot = 1.35F;
            this.legBackLeft.xRot = 1.45F;
            this.legBackRight.xRot = 1.45F;
            this.wingLeft.zRot = WING_FOLDED + 0.20F;
            this.wingRight.zRot = -WING_FOLDED - 0.20F;
        }

        // The breath: the jaw opens wide and the head tips down onto the point it was told to burn.
        if (entity.isBreathing())
        {
            this.jaw.xRot = JAW_OPEN;
            this.head.xRot += 0.16F;
        }
    }
}
