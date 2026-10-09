package spiderqueenhungrycraft.client.animation;

import org.joml.Vector3f;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.entity.ChadSteveEntity;

/**
 * The three animations of the Chad Steve Blockbench file, ported one to one into the vanilla animation system
 * (Phase 9A-GAMEPASS1). No GeckoLib, no AzureLib, only {@link AnimationDefinition}, {@link AnimationChannel} and
 * {@link Keyframe}.
 *
 * <p><b>Timing.</b> {@code idle} is 2 seconds and loops, {@code walk} is 1.6 seconds and loops, {@code hammer_slam}
 * is 2 seconds and plays once. The {@code impact} marker of {@code hammer_slam} sits on 1.0 second, which is
 * {@link ChadSteveEntity#HAMMER_IMPACT_TICK} ticks and the only moment the server is allowed to deal damage.
 *
 * <p><b>Values.</b> Blockbench writes animation values as offsets on top of the rest pose, and the vanilla targets
 * used by this class are additive as well, so the numbers below are the raw Blockbench numbers. Only the axis
 * mapping is applied: the model space of a Minecraft entity mirrors the x axis (and the y axis) of the Blockbench
 * model space, so a Blockbench rotation (x, y, z) becomes (-x, -y, z) and a Blockbench offset (x, y, z) becomes
 * (-x, -y, z). That is exactly what the helpers below do, which keeps every single line comparable with the file.
 *
 * <p>Channels that carry nothing but zeros in the file (for example the two hammer bones in {@code idle} and
 * {@code walk}) are left out, they can not move anything.
 */
@OnlyIn(Dist.CLIENT)
public final class ChadSteveAnimations
{
    private static final AnimationChannel.Interpolation LINEAR = AnimationChannel.Interpolations.LINEAR;
    private static final AnimationChannel.Interpolation SMOOTH = AnimationChannel.Interpolations.CATMULLROM;

    /** The pose of an idle Chad Steve: a slow breath in the body, the head and both arms. Loops. */
    public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.0F).looping()
            .addAnimation("body", position(
                    posKey(0.0F, 0.0F, 0.0F, 0.0F, SMOOTH),
                    posKey(0.5F, 0.0F, 0.28F, 0.0F, SMOOTH),
                    posKey(0.9F, 0.0F, 0.45F, 0.0F, SMOOTH),
                    posKey(1.5F, 0.0F, 0.18F, 0.0F, SMOOTH),
                    posKey(2.0F, 0.0F, 0.0F, 0.0F, SMOOTH)))
            .addAnimation("head", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, SMOOTH),
                    rotKey(0.45F, -1.2F, 0.0F, 0.0F, SMOOTH),
                    rotKey(0.85F, -2.1F, 0.0F, 0.0F, SMOOTH),
                    rotKey(1.5F, -0.8F, 0.0F, 0.0F, SMOOTH),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, SMOOTH)))
            .addAnimation("head", position(
                    posKey(0.0F, 0.0F, 0.0F, 0.0F, SMOOTH),
                    posKey(0.45F, 0.0F, 0.2F, 0.0F, SMOOTH),
                    posKey(0.85F, 0.0F, 0.32F, 0.0F, SMOOTH),
                    posKey(1.5F, 0.0F, 0.1F, 0.0F, SMOOTH),
                    posKey(2.0F, 0.0F, 0.0F, 0.0F, SMOOTH)))
            .addAnimation("left_arm", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, SMOOTH),
                    rotKey(0.7F, 1.5F, 0.0F, 0.0F, SMOOTH),
                    rotKey(1.05F, 1.9F, 0.0F, 0.0F, SMOOTH),
                    rotKey(1.55F, 0.7F, 0.0F, 0.0F, SMOOTH),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, SMOOTH)))
            .addAnimation("right_arm", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, SMOOTH),
                    rotKey(0.7F, 1.8F, 0.0F, 0.9F, SMOOTH),
                    rotKey(1.05F, 3.0F, 0.0F, 1.2F, SMOOTH),
                    rotKey(1.55F, 1.0F, 0.0F, 0.4F, SMOOTH),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, SMOOTH)))
            .build();

    /**
     * The walk cycle of the file: both legs alternate, the free right arm swings with them, and the left arm only
     * makes a small compensation because it carries the hammer, so the hammer never swings around wildly. The
     * cycle loops seamlessly, every channel ends on the value it started with.
     */
    public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
            .addAnimation("right_leg", rotation(
                    rotKey(0.0F, 22.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.4F, -1.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, -18.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.2F, -1.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 22.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("left_leg", rotation(
                    rotKey(0.0F, -18.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.4F, 1.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, 22.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.2F, 1.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, -18.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("right_arm", rotation(
                    rotKey(0.0F, -14.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.4F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, 14.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.2F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, -14.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("left_arm", rotation(
                    rotKey(0.0F, 5.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.4F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, -5.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.2F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 5.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("body", rotation(
                    rotKey(0.0F, 0.0F, 2.0F, 0.0F, LINEAR),
                    rotKey(0.4F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, 0.0F, -2.0F, 0.0F, LINEAR),
                    rotKey(1.2F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 0.0F, 2.0F, 0.0F, LINEAR)))
            .addAnimation("body", position(
                    posKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    posKey(0.4F, 0.0F, 0.35F, 0.0F, LINEAR),
                    posKey(0.8F, 0.0F, 0.0F, 0.0F, LINEAR),
                    posKey(1.2F, 0.0F, 0.35F, 0.0F, LINEAR),
                    posKey(1.6F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("head", rotation(
                    rotKey(0.0F, 0.0F, -1.2F, 0.0F, LINEAR),
                    rotKey(0.4F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.8F, 0.0F, 1.2F, 0.0F, LINEAR),
                    rotKey(1.2F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 0.0F, -1.2F, 0.0F, LINEAR)))
            .build();

    /**
     * The hammer slam of the file: windup, hammer high above the head, a fast swing down and a recovery back into
     * the rest pose. It plays once and is never looped.
     *
     * <p>{@code impact} is on 1.0 second: that is the frame on which the hammer head reaches the ground, the frame
     * the server deals its single hit on ({@link ChadSteveEntity#HAMMER_IMPACT_TICK}) and the frame the whole
     * timing of {@code ChadSteveHammerAttackGoal} is built around.
     *
     * <p>One technical repair against the file: the position channel of {@code hammer_handle} stopped after its
     * key on 1.0 second, so the handle stayed three pixels down and five back after the animation. The closing key
     * on 2.0 seconds puts it back into the hand, which is what every other channel of this animation already does.
     */
    public static final AnimationDefinition HAMMER_SLAM = AnimationDefinition.Builder.withLength(2.0F)
            .addAnimation("body", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.75F, 25.2F, -3.6F, 0.0F, LINEAR),
                    rotKey(1.0F, -33.0F, -6.0F, 0.0F, LINEAR),
                    rotKey(1.6F, -4.0F, -1.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("body", position(
                    posKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    posKey(1.0F, 0.0F, 0.0F, -1.0F, LINEAR),
                    posKey(1.6F, 0.0F, 0.15F, 0.0F, LINEAR),
                    posKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("head", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.3F, -5.0F, -3.0F, 0.0F, LINEAR),
                    rotKey(0.75F, -19.0F, -6.0F, 0.0F, LINEAR),
                    rotKey(1.0F, 19.0F, 3.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 2.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("left_arm", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.75F, 120.3F, 0.0F, -1.8F, LINEAR),
                    rotKey(1.0F, 63.0F, 0.0F, -3.0F, LINEAR),
                    rotKey(1.6F, 2.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("right_arm", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.3F, -20.0F, 0.0F, 4.0F, LINEAR),
                    rotKey(0.75F, 72.5F, 0.0F, 10.0F, LINEAR),
                    rotKey(1.0F, 15.0F, 0.0F, 10.0F, LINEAR),
                    rotKey(1.6F, -5.0F, 0.0F, 2.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("left_leg", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.75F, -27.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.0F, 55.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 12.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("right_leg", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.75F, 22.8F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.0F, 13.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, 1.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("hammer_anchor", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.3F, -45.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.6F, -90.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.0F, -33.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.6F, -5.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("hammer_handle", rotation(
                    rotKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    rotKey(0.75F, 92.5F, 0.0F, 0.0F, LINEAR),
                    rotKey(1.0F, 1.07F, 0.0F, 0.0F, LINEAR),
                    rotKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .addAnimation("hammer_handle", position(
                    posKey(0.0F, 0.0F, 0.0F, 0.0F, LINEAR),
                    posKey(1.0F, 0.0F, -3.0F, -5.0F, LINEAR),
                    posKey(2.0F, 0.0F, 0.0F, 0.0F, LINEAR)))
            .build();

    private ChadSteveAnimations()
    {
    }

    private static AnimationChannel rotation(Keyframe... keyframes)
    {
        return new AnimationChannel(AnimationChannel.Targets.ROTATION, keyframes);
    }

    private static AnimationChannel position(Keyframe... keyframes)
    {
        return new AnimationChannel(AnimationChannel.Targets.POSITION, keyframes);
    }

    /** A Blockbench rotation offset (x, y, z) in degrees, mapped into the model space of the entity. */
    private static Keyframe rotKey(float time, float bbX, float bbY, float bbZ,
            AnimationChannel.Interpolation interpolation)
    {
        return new Keyframe(time, KeyframeAnimations.degreeVec(-bbX, -bbY, bbZ), interpolation);
    }

    /** A Blockbench position offset (x, y, z) in pixels, mapped into the model space of the entity. */
    private static Keyframe posKey(float time, float bbX, float bbY, float bbZ,
            AnimationChannel.Interpolation interpolation)
    {
        return new Keyframe(time, new Vector3f(-bbX, -bbY, bbZ), interpolation);
    }
}
