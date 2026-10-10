package spiderqueenhungrycraft.compat.iceandfire.client;

import com.github.alexthe666.citadel.client.model.ITabulaModelAnimator;
import com.github.alexthe666.citadel.client.model.TabulaModel;
import com.github.alexthe666.iceandfire.client.model.animator.IceAndFireTabulaModelAnimator;
import com.github.alexthe666.iceandfire.client.model.util.DragonAnimationsLibrary;
import com.github.alexthe666.iceandfire.client.model.util.EnumDragonModelTypes;
import com.github.alexthe666.iceandfire.client.model.util.EnumDragonPoses;
import com.github.alexthe666.iceandfire.client.model.util.IEnumDragonModelTypes;
import com.github.alexthe666.iceandfire.client.model.util.IEnumDragonPoses;

import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * The animation adapter of the soul fire dragon (round 7C, born as the adapter of round 7B).
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

    private TabulaModel[] walkPoses;
    private TabulaModel[] flyPoses;
    private TabulaModel groundPose;
    private TabulaModel sittingPose;

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

    @Override
    public void setRotationAngles(TabulaModel model, SoulfireDragonEntity entity, float limbSwing,
            float limbSwingAmount, float ageInTicks, float rotationYaw, float rotationPitch, float scale)
    {
        model.resetToDefaultPose();
        this.ensurePoses();

        boolean flying = entity.isFlying() || entity.isHovering();
        TabulaModel pose = this.poseFor(entity, flying, limbSwing, limbSwingAmount, ageInTicks);

        if (pose != null && model.llibAnimator != null)
        {
            this.moveToPose(model, pose);
        }
    }

    /** @return the pose of the moment: a wing beat in the air, the walk cycle on the ground, the sit of a post. */
    private TabulaModel poseFor(SoulfireDragonEntity entity, boolean flying, float limbSwing, float limbSwingAmount,
            float ageInTicks)
    {
        if (flying && this.flyPoses != null && this.flyPoses.length > 0)
        {
            int index = (int) (ageInTicks / 4.0F) % this.flyPoses.length;

            return this.flyPoses[Math.abs(index)];
        }

        if (entity.isSitting() && this.sittingPose != null)
        {
            return this.sittingPose;
        }

        if (this.walkPoses != null && this.walkPoses.length > 0 && limbSwingAmount > 0.05F)
        {
            int index = (int) (limbSwing * 0.6662F / 1.5F) % this.walkPoses.length;

            return this.walkPoses[Math.abs(index)];
        }

        return this.groundPose;
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
