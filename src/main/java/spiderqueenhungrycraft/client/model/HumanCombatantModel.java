package spiderqueenhungrycraft.client.model;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;

/**
 * The model of the standard human combatant (Phase HF0A-GAMEPASS1; the swing was replaced by the vanilla one in the
 * animation round).
 *
 * <p>It is the vanilla humanoid model on the parts of the vanilla player model, so armour, the sword, the bow, the
 * shield and the golden apple are drawn by the vanilla layers exactly like they are on a player - without a single
 * new texture or model file of this mod.
 *
 * <p>Two poses are added here, and both of them are the vanilla poses of the game: the draw of a bow and the raised
 * shield. The melee swing is <b>not</b> added - it is the ordinary attack animation of the game (animation round). An
 * older round drew a swing of its own from a synced counter, and that was wrong twice: it replaced the swing of the
 * game with a cut of this mod's own shape, and it moved a hard coded arm instead of the arm the game itself picks from
 * {@code swingingArm} and {@code getMainArm} - which is how a body that carries a shield in its offhand ended up
 * looking like it attacks with the shield hand. The vanilla path is intact and is what runs here now:
 * {@code LivingEntityRenderer} hands the swing in as {@code attackTime}, {@code HumanoidModel.setupAttackAnimation}
 * turns it into the arm motion of the game, and {@link HumanCombatantEntity#swingMainHand()} is what puts that swing on
 * every client.
 *
 * <p>Both poses are asked of the entity from <b>synced state only</b> ({@link HumanCombatantEntity#isDrawingBow()} and
 * {@link HumanCombatantEntity#isShieldGuardUp()}), because the two copies the game keeps of "what is being used" do
 * not arrive together: the flag is synced, the local {@code useItem} copy is not.
 */
public class HumanCombatantModel extends HumanoidModel<HumanCombatantEntity>
{
    public HumanCombatantModel(ModelPart root)
    {
        super(root);
    }

    @Override
    public void setupAnim(HumanCombatantEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch)
    {
        this.rightArmPose = ArmPose.EMPTY;
        this.leftArmPose = ArmPose.EMPTY;

        if (entity.isDrawingBow())
        {
            // The pose of a player who is drawing a bow. The decision is the one of the entity (see
            // HumanCombatantEntity#isDrawingBow): it is made from the synced using flag, the used hand and the main
            // hand item, so it survives the moment a human switches the bow into his hand while starting to use it,
            // which is exactly where the draw animation used to get lost on the client.
            this.rightArmPose = ArmPose.BOW_AND_ARROW;
        }
        else if (entity.isShieldGuardUp())
        {
            // The shield is in the offhand, so the offhand arm carries the block pose. The question is the synced one
            // of the entity and not LivingEntity#isBlocking: that one reads the unsynced copy of the used item and only
            // answers after five ticks of use, so a client that received the "using" flag before the shield itself
            // answers no while the shield is really up.
            if (entity.getMainArm() == HumanoidArm.RIGHT)
            {
                this.leftArmPose = ArmPose.BLOCK;
            }
            else
            {
                this.rightArmPose = ArmPose.BLOCK;
            }
        }

        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}