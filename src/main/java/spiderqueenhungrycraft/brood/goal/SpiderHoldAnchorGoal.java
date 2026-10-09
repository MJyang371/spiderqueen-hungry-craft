package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * The hold goal of a spider minion (Brood Command AI): a guarding spider walks back to its anchor whenever it strayed
 * too far, and otherwise does nothing at all - it is not a frozen statue, it still fights and it still looks around.
 *
 * <p>The spider does not have to stand on the exact block: an area of about two blocks around the anchor counts as
 * "at my post", so a dozen guards do not try to squeeze into one collision box.
 *
 * <p><b>A post is not a leash.</b> While the sceptre has given this spider a scout move, that order owns its feet
 * (Brood Command Group round): a garrison that a queen picked and sent somewhere walks there and stands there, and only
 * when the errand is over - it arrived and the queen gives a new command, calls it home or the destination is gone - does
 * the post pull again. Without that line a moved garrison would turn around the moment it arrived.
 */
public class SpiderHoldAnchorGoal extends Goal
{
    /** A spider inside this radius of its anchor is at its post. */
    public static final double HOLD_RADIUS = 2.0D;

    private final SpiderMinionEntity spider;

    private int recalcTicks;

    public SpiderHoldAnchorGoal(SpiderMinionEntity spider)
    {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return this.spider.getOrderMode() == SpiderOrderMode.HOLD && this.spider.getMoveAnchor() == null
                && distanceToAnchor() > HOLD_RADIUS * HOLD_RADIUS;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.spider.getOrderMode() == SpiderOrderMode.HOLD && this.spider.getMoveAnchor() == null
                && distanceToAnchor() > HOLD_RADIUS * HOLD_RADIUS;
    }

    @Override
    public void start()
    {
        this.recalcTicks = 0;
    }

    @Override
    public void stop()
    {
        this.spider.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        BlockPos anchor = this.spider.getOrderAnchor();

        if (anchor == null || this.recalcTicks-- > 0)
        {
            return;
        }

        this.recalcTicks = this.adjustedTickDelay(10);
        this.spider.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, 1.0D);
    }

    /** @return the squared distance to the anchor, or 0 when there is no anchor in this level. */
    private double distanceToAnchor()
    {
        BlockPos anchor = this.spider.getOrderAnchor();

        return anchor == null ? 0.0D : this.spider.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);
    }
}
