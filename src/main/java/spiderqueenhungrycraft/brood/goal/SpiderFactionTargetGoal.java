package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderMinionTargeting;

/**
 * The targeting of a spider minion (Brood Command AI).
 *
 * <p>It is a thin extension of the vanilla {@link NearestAttackableTargetGoal} - so reach, follow range and "nearest
 * wins" behave exactly like everywhere else in the game - whose only condition is
 * {@link SpiderMinionTargeting#isEnemy}. Which creatures that means is decided by the order of the spider and by the
 * faction system, not by this class: an ordinary spider hunts the enemies of the queen side, and a spider on the free
 * attack order hunts everything that is not of its own side.
 */
public class SpiderFactionTargetGoal extends NearestAttackableTargetGoal<LivingEntity>
{
    private final SpiderMinionEntity spider;

    public SpiderFactionTargetGoal(SpiderMinionEntity spider)
    {
        super(spider, LivingEntity.class, 10, false, false,
                candidate -> SpiderMinionTargeting.isEnemy(spider, candidate));
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * The rule is asked again on every tick the goal runs, so a target that stops being hostile (it was taken out of
     * its faction tag, a queen flag changed, it turned creative) - or a spider that was just switched to the free attack
     * order, or off it - is answered with the rule of that very moment instead of the one it was picked under.
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.spider.getTarget();

        return target != null && SpiderMinionTargeting.isEnemy(this.spider, target) && super.canContinueToUse();
    }
}
