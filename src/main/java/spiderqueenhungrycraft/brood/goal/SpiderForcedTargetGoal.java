package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderMinionTargeting;

/**
 * The direct order of the queen sceptre (Brood Command AI).
 *
 * <p>This is the strongest target rule a spider has: while the forced target is valid, the spider chases it and no
 * other goal gets a say. It is deliberately <b>not</b> a fourth order - the long term order of the spider (follow,
 * hold, patrol) is untouched by an attack order, so the spider returns to exactly what it was doing the moment the
 * command is over.
 */
public class SpiderForcedTargetGoal extends Goal
{
    private final SpiderMinionEntity spider;

    public SpiderForcedTargetGoal(SpiderMinionEntity spider)
    {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity forced = this.validForcedTarget();

        return forced != null && this.spider.getTarget() != forced;
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity forced = this.validForcedTarget();

        return forced != null && this.spider.getTarget() == forced;
    }

    @Override
    public void start()
    {
        this.spider.setTarget(this.validForcedTarget());
    }

    /** @return the ordered target while it may still be attacked, else null (and the order is dropped). */
    @Nullable
    private LivingEntity validForcedTarget()
    {
        LivingEntity forced = this.spider.getForcedTarget();

        if (forced == null)
        {
            return null;
        }

        if (!SpiderMinionTargeting.isAttackable(this.spider, forced))
        {
            this.spider.clearForcedTarget();

            return null;
        }

        return forced;
    }
}
