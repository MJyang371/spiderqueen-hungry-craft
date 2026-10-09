package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderMinionTargeting;

/**
 * The self defence of a spider minion (Brood Command AI): a spider that was hit hits back, whatever its order is.
 *
 * <p>No faction relation is needed for this: somebody attacked the spider, so the spider is allowed to answer. The
 * owner protection still comes first, which is what keeps a stray hit of the queen (or of a sibling spider) from
 * starting a fight inside her own brood.
 */
public class SpiderSelfDefenseGoal extends Goal
{
    private final SpiderMinionEntity spider;

    public SpiderSelfDefenseGoal(SpiderMinionEntity spider)
    {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (this.spider.getForcedTarget() != null)
        {
            return false;
        }

        LivingEntity attacker = this.attacker();

        return attacker != null && this.spider.getTarget() != attacker;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.spider.getForcedTarget() == null && this.spider.getTarget() == this.attacker()
                && this.spider.getTarget() != null && this.spider.getTarget().isAlive();
    }

    @Override
    public void start()
    {
        this.spider.setTarget(this.attacker());
    }

    /** @return the last creature that hurt this spider, when it may be attacked. */
    @Nullable
    private LivingEntity attacker()
    {
        LivingEntity attacker = this.spider.getLastHurtByMob();

        return SpiderMinionTargeting.isAttackable(this.spider, attacker) ? attacker : null;
    }
}
