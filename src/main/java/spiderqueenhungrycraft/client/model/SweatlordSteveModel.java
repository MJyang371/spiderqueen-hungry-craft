package spiderqueenhungrycraft.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The model of Sweatlord Steve (Sweatlord round; the swing was replaced by the vanilla one in the animation round).
 *
 * <p>He is drawn on the parts of the vanilla player model with the vanilla default skins, exactly like the ordinary
 * human, so armour, the sword, the axe, the bow, the shield and the apple are drawn by the vanilla layers with no new
 * texture and no new model file of this mod.
 *
 * <p>Two poses are added on top of the walk of the game, and both are the vanilla ones: the draw of a bow and the
 * raised shield. The melee swing is the ordinary attack animation of the game (animation round) - the private, synced
 * sword swing of the earlier round is gone, together with the two things that were wrong about it: it replaced the
 * swing of the game with a cut of this mod's own shape, and it moved a hard coded arm instead of the arm the game picks
 * from {@code swingingArm} and {@code getMainArm}. Because the Sweatlord carries his shield in the offhand, that made
 * a body whose sword is in the main hand look as if it attacked with the shield hand.
 *
 * <p>Both poses are asked of the entity from <b>synced state only</b> ({@link SweatlordSteveEntity#isDrawingBow()} and
 * {@link SweatlordSteveEntity#isShieldGuardUp()}), so they survive a client that received the "using" flag before the
 * equipment it belongs to - which is the one ordering in which a client can not see the item at all.
 */
public class SweatlordSteveModel extends HumanoidModel<SweatlordSteveEntity>
{
    public SweatlordSteveModel(ModelPart root)
    {
        super(root);
    }

    @Override
    public void setupAnim(SweatlordSteveEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        this.rightArmPose = ArmPose.EMPTY;
        this.leftArmPose = ArmPose.EMPTY;

        if (entity.isDrawingBow())
        {
            // The pose of a player who is drawing a bow.
            this.rightArmPose = ArmPose.BOW_AND_ARROW;
        }
        else if (entity.isShieldGuardUp())
        {
            // The shield is in the offhand, so the offhand arm carries the block pose. Asked of the entity, because
            // LivingEntity#isBlocking reads the unsynced copy of the used item and only answers after five ticks.
            if (entity.getMainArm() == HumanoidArm.RIGHT)
            {
                this.leftArmPose = ArmPose.BLOCK;
            }
            else
            {
                this.rightArmPose = ArmPose.BLOCK;
            }
        }
        else if (entity.isUsingItem())
        {
            // An apple, an egg or a trident in the hand: the ITEM pose is what a player shows while using one. The
            // hand is the synced one the game uses for the same question.
            if (entity.getUsedItemHand() == InteractionHand.MAIN_HAND)
            {
                this.rightArmPose = ArmPose.ITEM;
            }
            else
            {
                this.leftArmPose = ArmPose.ITEM;
            }
        }

        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}