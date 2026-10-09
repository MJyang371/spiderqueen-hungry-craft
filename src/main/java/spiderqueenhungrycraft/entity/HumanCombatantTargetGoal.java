package spiderqueenhungrycraft.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

/**
 * The target rule of the standard human combatant (Phase HF0A-GAMEPASS1): a Spider Queen player, and nothing else
 * unless the debug switch of this round turns the monster hunting on.
 *
 * <p>It is a thin extension of the vanilla {@link NearestAttackableTargetGoal}, so the search box, the follow range
 * and the "nearest wins" selection behave exactly like everywhere else in the game.
 */
public class HumanCombatantTargetGoal extends NearestAttackableTargetGoal<Player>
{
    private final HumanCombatantEntity combatant;

    public HumanCombatantTargetGoal(HumanCombatantEntity mob)
    {
        super(mob, Player.class, 10, false, false,
                candidate -> HumanCombatantTargeting.isHostileQueen(candidate)
                        && HumanCombatantTargeting.mayPickBandTarget(mob, candidate));
        this.combatant = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * The rule is checked again on every tick the goal runs, so a queen who clears her flag, turns creative or dies
     * is dropped at once instead of being chased forever.
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.combatant.getTarget();

        if (target == null || !HumanCombatantTargeting.isHostileQueen(target))
        {
            return false;
        }

        return super.canContinueToUse();
    }
}
