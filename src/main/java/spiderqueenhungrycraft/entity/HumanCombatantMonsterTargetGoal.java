package spiderqueenhungrycraft.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

/**
 * The monster hunt of the standard human combatant (Phase HF0B).
 *
 * <p>It is the second target rule of a combatant, right behind the queen rule: every living entity in reach is offered
 * to {@link HumanCombatantTargeting#isHostileMonster}, which reads the {@code monster_faction} tag first and only
 * falls back to the vanilla monster marker for types that no tag mentions.
 *
 * <p>The search class is {@link LivingEntity}, because a monster faction can hold any entity type and the search has
 * to be able to see them all - the rule below filters, not the class filter. Should this ever stop finding targets,
 * narrowing it to {@code Monster.class} is the fallback the phase allows.
 */
public class HumanCombatantMonsterTargetGoal extends NearestAttackableTargetGoal<LivingEntity>
{
    private final HumanCombatantEntity combatant;

    public HumanCombatantMonsterTargetGoal(HumanCombatantEntity mob)
    {
        super(mob, LivingEntity.class, 10, false, false,
                candidate -> (HumanCombatantTargeting.isHostileMonster(mob, candidate)
                        || HumanCombatantTargeting.isProvoked(mob, candidate))
                        && HumanCombatantTargeting.mayPickBandTarget(mob, candidate));
        this.combatant = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * A target is checked again on every tick the goal runs, so a monster that died, was taken out of the tag or was
     * replaced by a queen is dropped at once.
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.combatant.getTarget();

        if (target == null)
        {
            return false;
        }

        if (!HumanCombatantTargeting.isHostileMonster(this.combatant, target)
                && !HumanCombatantTargeting.isHostileQueen(target))
        {
            return false;
        }

        return super.canContinueToUse();
    }
}
