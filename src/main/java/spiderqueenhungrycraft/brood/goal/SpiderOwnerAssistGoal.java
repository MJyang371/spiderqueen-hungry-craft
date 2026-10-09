package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderMinionTargeting;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * The two ways a spider of the brood helps its queen in a fight (Brood Command AI), the vanilla wolf idea
 * {@code OwnerHurtByTargetGoal} / {@code OwnerHurtTargetGoal} in one class: the spider attacks whoever hurt the owner,
 * or whoever the owner just attacked.
 *
 * <p><b>Only a following spider does this.</b> A guarding or patrolling spider does not abandon its post because its
 * queen punched a cow on the other side of the base - it only reacts once the queen herself is in danger, and only if
 * she is at war right next to it... which is exactly what "follow" means: those are the spiders that travel with her.
 *
 * <p>An order that ignores the faction matrix (a cow is nobody's enemy) still passes the owner protection first, so a
 * queen can never turn her own brood against itself.
 */
public class SpiderOwnerAssistGoal extends Goal
{
    /** Which of the vanilla pet goals this instance is. */
    public enum Assist
    {
        /** Attack whoever hurt the owner (vanilla {@code OwnerHurtByTargetGoal}). */
        HURT_BY_OWNER,

        /** Attack whoever the owner attacked (vanilla {@code OwnerHurtTargetGoal}). */
        HURT_TARGET
    }

    private final SpiderMinionEntity spider;
    private final Assist assist;

    public SpiderOwnerAssistGoal(SpiderMinionEntity spider, Assist assist)
    {
        this.spider = spider;
        this.assist = assist;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (this.spider.getOrderMode() != SpiderOrderMode.FOLLOW || this.spider.getForcedTarget() != null)
        {
            return false;
        }

        LivingEntity victim = this.assistedTarget();

        return victim != null && victim != this.spider.getTarget();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.spider.getOrderMode() == SpiderOrderMode.FOLLOW && this.spider.getForcedTarget() == null
                && this.spider.getTarget() == this.assistedTarget();
    }

    @Override
    public void start()
    {
        this.spider.setTarget(this.assistedTarget());
    }

    /** @return the queen of this spider, or null while she is not in this world. */
    @Nullable
    protected Player owner()
    {
        return this.spider.getOwnerPlayer();
    }

    /** @return the creature the queen is fighting with right now, or null when there is none. */
    @Nullable
    private LivingEntity assistedTarget()
    {
        Player owner = this.owner();

        if (owner == null)
        {
            return null;
        }

        LivingEntity candidate = this.assist == Assist.HURT_BY_OWNER ? owner.getLastHurtByMob() : owner.getLastHurtMob();

        return SpiderMinionTargeting.isAttackable(this.spider, candidate) ? candidate : null;
    }
}
