package spiderqueenhungrycraft.brood.goal;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * The wandering goal of a spider minion (Brood Command AI): a spider whose order <b>roams</b>
 * ({@link SpiderOrderMode#roams()}) walks its rounds around its anchor instead of following its queen.
 *
 * <p>There is no fixed route and no waypoint list - every stroll simply picks a random reachable spot <b>towards</b>
 * the anchor, so a spider that chased something far away walks back into its area on its own, and a spider that is
 * already at home just potters about. The vanilla random stroll of a spider is replaced by this goal, which is the
 * same vanilla behaviour with one extra question: "is my order one that walks around a post?".
 *
 * <p>Two orders do that, and they differ only in what they attack while they walk it: a {@link SpiderOrderMode#PATROL
 * patrol} keeps to the enemies of the queen side, and a {@link SpiderOrderMode#FREE_ATTACK free attack} hunts
 * everything that is not of its own side. Neither of them is a statue: both keep the rounds this goal gives them.
 *
 * <p>A roaming spider that the sceptre has given a scout move stands still instead (Brood Command Group round): while
 * that order runs, the errand owns the feet of the spider, so a group that was sent to a spot stays there and picks its
 * round up again when the errand is over.
 */
public class SpiderPatrolGoal extends RandomStrollGoal
{
    /** The area a patrolling spider wanders in, around its anchor. */
    public static final double PATROL_RADIUS = 8.0D;

    private final SpiderMinionEntity spider;

    public SpiderPatrolGoal(SpiderMinionEntity spider)
    {
        super(spider, 1.0D, 60);
        this.spider = spider;
    }

    @Override
    public boolean canUse()
    {
        return this.spider.getOrderMode().roams() && this.spider.getOrderAnchor() != null
                && this.spider.getMoveAnchor() == null && super.canUse();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.spider.getOrderMode().roams() && this.spider.getMoveAnchor() == null
                && super.canContinueToUse();
    }

    /** A random spot inside the patrol area, always on land and always towards the anchor of the spider. */
    @Override
    @Nullable
    protected Vec3 getPosition()
    {
        BlockPos anchor = this.spider.getOrderAnchor();

        if (anchor == null)
        {
            return null;
        }

        Vec3 centre = new Vec3(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D);

        return LandRandomPos.getPosTowards(this.spider, (int) PATROL_RADIUS, 7, centre);
    }
}
