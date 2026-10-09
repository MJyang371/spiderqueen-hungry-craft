package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderTemporaryCommand;

/**
 * The scout move of the queen sceptre (Queen Scepter command patch): walk to the temporary move anchor, then stand by.
 *
 * <p>It is deliberately <b>not</b> a hold order. The spider remains a follower - its order is untouched - it is simply
 * busy with a place a bit further away than a leash would allow, and it stays interested in that place (a fight it was
 * pulled into ends with a walk back to the anchor) until the command is replaced, the queen calls it home or the
 * destination stops being loaded.
 */
public class SpiderMoveToGoal extends Goal
{
    /** A spider this close to its move anchor counts as arrived. */
    public static final double ARRIVE_RADIUS = 2.5D;

    private final SpiderMinionEntity spider;

    private int recalcTicks;

    public SpiderMoveToGoal(SpiderMinionEntity spider)
    {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return this.spider.getTemporaryCommand() == SpiderTemporaryCommand.MOVE_TO
                && this.spider.getMoveAnchor() != null && distanceToAnchor() > ARRIVE_RADIUS * ARRIVE_RADIUS;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.spider.getTemporaryCommand() == SpiderTemporaryCommand.MOVE_TO
                && this.spider.getMoveAnchor() != null && distanceToAnchor() > ARRIVE_RADIUS * ARRIVE_RADIUS;
    }

    @Override
    public void start()
    {
        this.recalcTicks = 0;
    }

    @Override
    public void stop()
    {
        // The walk is over: on the way home and after a fight the path is recalculated fresh, and arriving for the
        // first time is reported once, so the command marker of the queen can disappear.
        this.spider.getNavigation().stop();

        if (this.spider.getTemporaryCommand() == SpiderTemporaryCommand.MOVE_TO && distanceToAnchor() <= ARRIVE_RADIUS * ARRIVE_RADIUS)
        {
            this.spider.reportMoveArrival();
        }
    }

    @Override
    public void tick()
    {
        BlockPos anchor = this.spider.getMoveAnchor();

        if (anchor == null || this.recalcTicks-- > 0)
        {
            return;
        }

        this.recalcTicks = this.adjustedTickDelay(10);
        this.spider.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.0D);
    }

    /** @return the squared distance to the move anchor, or 0 when there is none in this level. */
    private double distanceToAnchor()
    {
        BlockPos anchor = this.spider.getMoveAnchor();

        return anchor == null ? 0.0D
                : this.spider.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
    }
}
