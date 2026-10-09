package spiderqueenhungrycraft.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;

/**
 * The model of the infested Steve (Infested Steve round; the poses were made synced in the animation round).
 *
 * <p>It is the model of the game itself on the parts of the vanilla player model - the same parts the standard human
 * combatant is drawn on - so armour, the sword, the bow, the shield and the golden apple appear through the vanilla
 * layers and not a single new model file or model texture exists for this creature.
 *
 * <p>Only the poses a fight needs are added on top of the walk of the game, and every one of them is the vanilla pose
 * read from the body: the draw of a bow, the raised shield and the ITEM pose of an apple in the hand. The sword swing
 * is the ordinary attack animation of the game, unchanged.
 *
 * <p>The two poses of the gear ask the entity ({@link InfestedSteveEntity#isDrawingBow()} and
 * {@link InfestedSteveEntity#isShieldGuardUp()}) and never the unsynced copies themselves (animation round).
 * {@code getUseItem()} is the copy the game keeps for itself: it is filled on the server and only ever guessed on a
 * client from whatever the hand held when the synced "using" flag arrived, which is why a body that drew a bow could
 * be rendered with a sword in its hand and no draw at all.
 */
public class InfestedSteveModel extends HumanoidModel<InfestedSteveEntity>
{
    public InfestedSteveModel(ModelPart root)
    {
        super(root);
    }

    @Override
    public void setupAnim(InfestedSteveEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        this.rightArmPose = ArmPose.EMPTY;
        this.leftArmPose = ArmPose.EMPTY;

        if (entity.isDrawingBow())
        {
            // The pose of a body that is drawing a bow.
            this.rightArmPose = ArmPose.BOW_AND_ARROW;
        }
        else if (entity.isShieldGuardUp())
        {
            // The shield is in the offhand, so the offhand arm carries the block pose.
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
            // An apple in the hand: the ITEM pose is what a player shows while eating one.
            if (entity.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND)
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