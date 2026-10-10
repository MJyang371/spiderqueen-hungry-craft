package spiderqueenhungrycraft.compat.iceandfire.client;

import com.github.alexthe666.citadel.client.model.ITabulaModelAnimator;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.citadel.client.model.AdvancedModelBox;
import net.minecraft.util.Mth;
import com.github.alexthe666.iceandfire.client.model.animator.IceAndFireTabulaModelAnimator;
import com.github.alexthe666.iceandfire.client.model.util.DragonAnimationsLibrary;
import com.github.alexthe666.iceandfire.client.model.util.EnumDragonModelTypes;
import com.github.alexthe666.iceandfire.client.model.util.EnumDragonPoses;
import com.github.alexthe666.iceandfire.client.model.util.IEnumDragonModelTypes;
import com.github.alexthe666.iceandfire.client.model.util.IEnumDragonPoses;

import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * The animation adapter of the soul fire dragon (round 7F, restored pose adapter).
 *
 * <p><b>It is an adapter, not a copy.</b> Ice and Fire's own dragon animator is bound to {@code EntityDragonBase}
 * ({@code DragonTabulaModelAnimator<T extends EntityDragonBase>}) and can therefore never animate a unit of this mod,
 * but its <b>parent</b> {@link IceAndFireTabulaModelAnimator} is a plain, entity free class of public helpers
 * ({@code moveToPose}, {@code transitionTo}, {@code rotate}, {@code distance}). This class extends it and implements
 * Citadel's generic {@link ITabulaModelAnimator} for {@link SoulfireDragonEntity}, so the model geometry, the poses and
 * the interpolation helpers are Ice and Fire's, while the decision "which pose to show right now" is ours
 * (standing, walking, flying, sitting or breathing) and reads only this mod's own state.
 *
 * <p>The pose sets are fetched <b>lazily</b> from {@link DragonAnimationsLibrary} and every use is null guarded: the
 * library is filled by Ice and Fire's own client setup, and a frame that happens before that simply shows the default
 * pose instead of failing.
 */
public class SoulfireDragonAnimator extends IceAndFireTabulaModelAnimator
        implements ITabulaModelAnimator<SoulfireDragonEntity>
{
    /** The one model kind every soul fire dragon is drawn with, whatever remains it was raised from. */
    public static final IEnumDragonModelTypes MODEL_TYPE = EnumDragonModelTypes.FIRE_DRAGON_MODEL;

    /** Development probe switch: when on, the adapter reports every hundredth pose it is asked for. */
    private static final boolean RIG_DEBUG = Boolean.getBoolean("spiderqueen.soulfire.rig");
    private int rotationCalls;

    private TabulaModel[] walkPoses;
    private TabulaModel[] flyPoses;
    private TabulaModel groundPose;
    private TabulaModel sittingPose;
    private TabulaModel[] bitePoses;

    /** How much of a head turn each bone of the neck takes; the head takes what is left of it (round 7H). */
    private static final float NECK1_SHARE = 0.18F;
    private static final float NECK2_SHARE = 0.24F;
    private static final float NECK3_SHARE = 0.26F;

    /** The largest turn the animation shows, in degrees: a neck is not a swivel chair, and past about this much the head
     *  of the model starts to fold into its own shoulders. */
    private static final float MAX_YAW = 45.0F;
    private static final float MAX_PITCH = 25.0F;

    /** How fast the shown turn follows the real one, per frame: a look has to be a turn, not a jump. */
    private static final float FOLLOW = 0.22F;

    /** A shared renderer animates multiple dragons: never share smoothing state between different entities. */
    private final java.util.WeakHashMap<SoulfireDragonEntity, float[]> perDragonPose = new java.util.WeakHashMap<>();

    public SoulfireDragonAnimator()
    {
        this(MODEL_TYPE);
    }

    public SoulfireDragonAnimator(IEnumDragonModelTypes modelType)
    {
        // Exactly the wiring of Ice and Fire's own animators: the base model comes from the library the mod fills in
        // its client setup, which is the same model the renderer is handed.
        super(DragonAnimationsLibrary.getModel(EnumDragonPoses.GROUND_POSE, modelType));
    }

    /**
     * The I&F pose library stores FULL Tabula poses, not ready-to-run vanilla animation clips.
     * IceAndFireTabulaModelAnimator.moveToPose() constructs an animation keyframe delta;
     * calling it outside Citadel's active keyframe animation produces a static sculpture.
     * This renderer has no EntityDragonBase/IAnimatedEntity keyframe driver by design.
     * Instead, interpolate the REAL pose boxes each render frame, matching by their names.
     * There are NO custom wing cubes and NO dependence on the old 7D model.
     */
    @Override
    public void setRotationAngles(TabulaModel model, SoulfireDragonEntity entity, float limbSwing,
            float limbSwingAmount, float ageInTicks, float rotationYaw, float rotationPitch, float scale)
    {
        model.resetToDefaultPose();
        this.ensurePoses();

        if (RIG_DEBUG && ++this.rotationCalls % 1000 == 1)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7G] animator: setRotationAngles call {} flying={} sitting={} breathing={} limbAmount={} "
                            + "ground={} fly1={} walk1={} sittingPose={}",
                    this.rotationCalls, entity.isFlying(), entity.isSitting(), entity.isBreathing(), limbSwingAmount,
                    this.groundPose != null, this.flyPoses != null && this.flyPoses[0] != null,
                    this.walkPoses != null && this.walkPoses[0] != null, this.sittingPose != null);
        }

        if (entity.isLanding())
        {
            // I&F's hover/flight/ground postures. There is no invented LANDING constant here: the
            // normal I&F grounded pose is smoothly blended in during the final metres of descent.
            this.applyCycle(model, this.flyPoses, ageInTicks, 10.0F, 1.0F);
            TabulaModel hoverPose = this.get(EnumDragonPoses.HOVERING_POSE);
            this.applyPose(model, hoverPose, hoverPose, 0.0F, 0.35F);
            float landing = Mth.clamp(entity.landingAnimationProgress(), 0.0F, 1.0F);
            this.applyPose(model, this.groundPose, this.groundPose, 0.0F, landing);
        }
        else if (entity.isFlying() || entity.isHovering())
        {
            // Genuine I&F flight and hovering poses, not an independent mesh/wing animation.
            this.applyCycle(model, this.flyPoses, ageInTicks, 10.0F, 1.0F);
            if (entity.isHovering())
            {
                TabulaModel hoverPose = this.get(EnumDragonPoses.HOVERING_POSE);
                this.applyPose(model, hoverPose, hoverPose, 0.0F, 0.6F);
            }
            if (entity.getDeltaMovement().y < -0.20D)
            {
                TabulaModel divePose = this.get(EnumDragonPoses.DIVING_POSE);
                this.applyPose(model, divePose, divePose, 0.0F, 0.18F);
            }
        }
        else if (entity.isSitting())
        {
            this.applyPose(model, this.sittingPose, this.sittingPose, 0.0F, 1.0F);
        }
        else if (limbSwingAmount > 0.035F)
        {
            // 4 I&F walk poses. LimbSwing comes from the vanilla walk animation state.
            float phase = limbSwing * 0.16F;
            this.applyCycle(model, this.walkPoses, phase, 1.0F, Mth.clamp(limbSwingAmount * 3.0F, 0.0F, 1.0F));
        }
        else
        {
            this.applyPose(model, this.groundPose, this.groundPose, 0.0F, 1.0F);
        }

        // I&F stream-breath pose gives the jaws their original dragon-mouth gesture.
        if (entity.isBreathing())
        {
            TabulaModel stream = this.get(EnumDragonPoses.STREAM_BREATH);
            if (stream != null)
            {
                this.applyBreathingHead(model, stream);
            }
        }

        // During a turn the true original wing bones bank with the body, rather than flying sideways
        // with a camera-only yaw. Bank comes from the SERVER's bounded physical turn.
        if (entity.isFlying() || entity.isHovering() || entity.isLanding())
        {
            this.applyAirTurn(model, entity);
        }

        // Round 7H: the bite is an animation of the real jaws, played over the clock the server counts.
        if (entity.isBiting())
        {
            this.applyBite(model, entity.biteProgress(), entity.isFlying() || entity.isHovering() ? 0.6F : 0.85F);
        }

        // Round 7H: the neck and the head follow wherever the server says the unit is looking, and a unit with nothing to
        // do keeps breathing and swaying. Both are added on top of the pose above, so walking, flying, sitting and
        // breathing units turn their heads as well.
        this.applyLook(model, entity);

        if (isIdle(entity, limbSwingAmount))
        {
            this.applyIdle(model, ageInTicks);
        }
    }

    /** @return true while the unit is doing nothing that has an animation of its own. */
    private static boolean isIdle(SoulfireDragonEntity entity, float limbSwingAmount)
    {
        return !entity.isFlying() && !entity.isHovering() && !entity.isLanding() && !entity.isBiting()
                && !entity.isBreathing() && limbSwingAmount <= 0.035F;
    }

    /**
     * Turns the neck and the head towards the direction the unit is really looking (round 7H).
     *
     * <p>The three neck bones bend by ever larger shares of the turn and the head takes what is left, so the neck curves
     * instead of the head snapping around on a stiff column. The shown angle chases the real one instead of being set to
     * it, which is what makes a glance a turn and not a jump, and the whole turn is clamped well before the bones of the
     * model would start to fold into each other.
     */
    private void applyLook(TabulaModel model, SoulfireDragonEntity entity)
    {
        float targetYaw = Mth.clamp(entity.syncedHeadYaw(), -MAX_YAW, MAX_YAW);
        float targetPitch = Mth.clamp(entity.syncedHeadPitch(), -MAX_PITCH, MAX_PITCH);

        float[] shown = this.perDragonPose.computeIfAbsent(entity, unused -> new float[3]);
        // Breathing should aim steadily. In 7H an alternating lookControl target shook the neck every render frame.
        float response = entity.isBreathing() ? 0.10F : FOLLOW;
        shown[0] += Mth.wrapDegrees(targetYaw - shown[0]) * response;
        shown[1] += (targetPitch - shown[1]) * response;
        this.turn(model, "Neck1", shown[0] * NECK1_SHARE, shown[1] * NECK1_SHARE);
        this.turn(model, "Neck2", shown[0] * NECK2_SHARE, shown[1] * NECK2_SHARE);
        this.turn(model, "Neck3", shown[0] * NECK3_SHARE, shown[1] * NECK3_SHARE);
        this.turn(model, "Head", shown[0] * (1.0F - NECK1_SHARE - NECK2_SHARE - NECK3_SHARE),
                shown[1] * (1.0F - NECK1_SHARE - NECK2_SHARE - NECK3_SHARE));
    }

    /**
     * The breathing of a standing unit (round 7H): a slow sway of the neck and the head, a very small rotation of the
     * chest, and the tail swinging out one segment after the other. It is a picture only - no world position, no
     * navigation and no body yaw is touched anywhere in here.
     */
    /** Server-authoritative turn posture on the real I&F skeleton. No fake wings. */
    private void applyAirTurn(TabulaModel model, SoulfireDragonEntity entity)
    {
        float[] shown = this.perDragonPose.computeIfAbsent(entity, unused -> new float[3]);
        float desired = Mth.clamp(entity.syncedAirBank(), -14.0F, 14.0F);
        shown[2] += (desired - shown[2]) * 0.18F;
        AdvancedModelBox upper = model.getCube("BodyUpper");
        if (upper != null)
        {
            upper.rotateAngleZ += (float) Math.toRadians(shown[2] * 0.75F);
        }
        // Wings are the original I&F arm joints; small counter-asymmetric rotations give a real bank gesture.
        AdvancedModelBox left = model.getCube("armL1");
        AdvancedModelBox right = model.getCube("armR1");
        if (left != null) left.rotateAngleX += (float) Math.toRadians(shown[2] * 0.20F);
        if (right != null) right.rotateAngleX -= (float) Math.toRadians(shown[2] * 0.20F);
    }

    private void applyIdle(TabulaModel model, float ageInTicks)
    {
        float slow = ageInTicks * 0.045F;

        this.turn(model, "Neck2", Mth.sin(slow) * 2.5F, Mth.sin(slow * 0.7F) * 1.5F);
        this.turn(model, "Neck3", Mth.sin(slow + 0.6F) * 2.0F, Mth.sin(slow * 0.9F) * 1.2F);
        this.turn(model, "Head", Mth.sin(slow + 1.1F) * 1.5F, Mth.sin(slow * 0.8F + 0.4F) * 1.0F);
        this.turn(model, "BodyUpper", Mth.sin(ageInTicks * 0.06F) * 0.6F, Mth.sin(ageInTicks * 0.05F) * 0.5F);

        int tail = 0;

        for (AdvancedModelBox part : model.getCubes().values())
        {
            if (part.boxName != null && part.boxName.toLowerCase(java.util.Locale.ROOT).contains("tail"))
            {
                float phase = ageInTicks * 0.05F - tail * 0.7F;
                this.turn(model, part.boxName, Mth.sin(phase) * (1.8F + tail * 0.6F), 0.0F);
                tail++;
            }
        }
    }

    /**
     * The bite (round 7H): Ice and Fire's own bite poses, blended over the clock of the bite, so the jaws open, the head
     * shoots forward and the teeth close on the tick the damage falls. A pose that is missing from the jar is simply not
     * used; when none of them is there, the neck and the jaws do the work themselves, on the very same clock.
     */
    private void applyBite(TabulaModel model, float progress, float weight)
    {
        if (this.bitePoses == null)
        {
            this.bitePoses = new TabulaModel[] { this.get(EnumDragonPoses.BITE1), this.get(EnumDragonPoses.BITE2),
                    this.get(EnumDragonPoses.BITE3) };
        }

        int first = -1;
        int second = -1;

        for (int index = 0; index < this.bitePoses.length; index++)
        {
            if (this.bitePoses[index] != null)
            {
                if (first < 0)
                {
                    first = index;
                }

                second = index;
            }
        }

        if (first >= 0 && second > first)
        {
            float phase = Mth.clamp(progress, 0.0F, 1.0F) * (second - first);
            int from = Math.min(first + (int) phase, second);
            int to = Math.min(from + 1, second);
            TabulaModel one = this.bitePoses[from];
            TabulaModel other = this.bitePoses[to];

            if (one != null && other != null)
            {
                this.applyPose(model, one, other, phase - (int) phase, weight);

                return;
            }
        }

        // No bite pose in this jar: the head and the neck do the work themselves, still on the same clock.
        float snap = Mth.sin(Mth.clamp(progress, 0.0F, 1.0F) * (float) Math.PI);
        this.turn(model, "Neck2", 0.0F, snap * 6.0F);
        this.turn(model, "Neck3", 0.0F, snap * 5.0F);
        this.turn(model, "Head", 0.0F, snap * 9.0F);
        this.openJaws(model, snap);
    }

    /** Opens the lower jaw by that much (0 to 1), the one bone a bite can not do without. */
    private void openJaws(TabulaModel model, float amount)
    {
        AdvancedModelBox jaw = model.getCube("Jaw");

        if (jaw != null)
        {
            jaw.rotateAngleX += (float) Math.toRadians(amount * 12.0F);
        }
    }

    /** Adds a turn to one bone, in degrees, about the vertical and the sideways axis. */
    private void turn(TabulaModel model, String bone, float yawDegrees, float pitchDegrees)
    {
        AdvancedModelBox part = model.getCube(bone);

        if (part == null)
        {
            return;
        }

        part.rotateAngleY += (float) Math.toRadians(yawDegrees);
        part.rotateAngleX += (float) Math.toRadians(pitchDegrees);
    }

    private void applyBreathingHead(TabulaModel model, TabulaModel stream)
    {
        for (AdvancedModelBox part : model.getCubes().values())
        {
            String name = part.boxName.toLowerCase(java.util.Locale.ROOT);
            if (!(name.contains("head") || name.contains("jaw") || name.startsWith("neck")))
            {
                continue;
            }
            AdvancedModelBox pose = stream.getCubes().get(part.boxName);
            if (pose != null)
            {
                part.rotateAngleX = Mth.lerp(0.50F, part.rotateAngleX, pose.rotateAngleX);
                part.rotateAngleY = Mth.lerp(0.50F, part.rotateAngleY, pose.rotateAngleY);
                part.rotateAngleZ = Mth.lerp(0.50F, part.rotateAngleZ, pose.rotateAngleZ);
            }
        }
    }

    private void applyCycle(TabulaModel base, TabulaModel[] poses, float time, float ticksPerPose, float strength)
    {
        if (poses == null || poses.length == 0)
        {
            return;
        }
        // Missing poses should not freeze the whole model: degrade to GROUND.
        float phase = Math.max(0.0F, time / ticksPerPose);
        int first = (int) Math.floor(phase) % poses.length;
        int second = (first + 1) % poses.length;
        this.applyPose(base, poses[first], poses[second], phase - (float) Math.floor(phase), strength);
    }

    private void applyPose(TabulaModel target, TabulaModel first, TabulaModel second, float alpha, float weight)
    {
        if (first == null || second == null)
        {
            return;
        }
        // Mapping by stable I&F box names also works if some pose lacks one optional box.
        for (AdvancedModelBox part : target.getCubes().values())
        {
            AdvancedModelBox a = first.getCubes().get(part.boxName);
            AdvancedModelBox b = second.getCubes().get(part.boxName);
            if (a == null || b == null)
            {
                continue;
            }
            part.rotateAngleX = Mth.lerp(weight, part.rotateAngleX, Mth.lerp(alpha, a.rotateAngleX, b.rotateAngleX));
            part.rotateAngleY = Mth.lerp(weight, part.rotateAngleY, Mth.lerp(alpha, a.rotateAngleY, b.rotateAngleY));
            part.rotateAngleZ = Mth.lerp(weight, part.rotateAngleZ, Mth.lerp(alpha, a.rotateAngleZ, b.rotateAngleZ));
            part.rotationPointX = Mth.lerp(weight, part.rotationPointX, Mth.lerp(alpha, a.rotationPointX, b.rotationPointX));
            part.rotationPointY = Mth.lerp(weight, part.rotationPointY, Mth.lerp(alpha, a.rotationPointY, b.rotationPointY));
            part.rotationPointZ = Mth.lerp(weight, part.rotationPointZ, Mth.lerp(alpha, a.rotationPointZ, b.rotationPointZ));
        }
    }

    /** Reads the pose sets of this dragon kind out of Ice and Fire's library, once, and never fails when it is empty. */
    private void ensurePoses()
    {
        if (this.groundPose != null)
        {
            return;
        }

        this.groundPose = get(EnumDragonPoses.GROUND_POSE);
        this.sittingPose = get(EnumDragonPoses.SITTING_POSE);
        this.walkPoses = new TabulaModel[] { get(EnumDragonPoses.WALK1), get(EnumDragonPoses.WALK2),
                get(EnumDragonPoses.WALK3), get(EnumDragonPoses.WALK4) };
        this.flyPoses = new TabulaModel[] { get(EnumDragonPoses.FLIGHT1), get(EnumDragonPoses.FLIGHT2),
                get(EnumDragonPoses.FLIGHT3), get(EnumDragonPoses.FLIGHT4), get(EnumDragonPoses.FLIGHT5),
                get(EnumDragonPoses.FLIGHT6) };
    }

    private TabulaModel get(IEnumDragonPoses pose)
    {
        return DragonAnimationsLibrary.getModel(pose, MODEL_TYPE);
    }
}
