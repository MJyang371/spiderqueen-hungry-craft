package spiderqueenhungrycraft.entity;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.hostility.HumanBandState;

/**
 * The post of a human unit (human pack aggro round): where he fights from, and the walk back to it.
 *
 * <p>Two jobs, and they are the same job. A human who has just been <b>dragged out of his post</b> (see the leash in
 * {@code HumanCombatGoal}) walks back to it. A human who is standing <b>in peace</b> far outside the leash of his old
 * post takes a new one right where he stands, so a human who strolls a hundred blocks away does not keep a post he
 * can never fight from again.
 *
 * <p>The two can never fight each other: while he has a target, has a band lock or is walking home, no new post is
 * taken, so the "walk away in peace" branch can not creep outward inside a fight. That is the whole leak protection of
 * the leash, and it is a single rule instead of a timer to maintain.
 *
 * <p>Without the pack rules (or with a leash of zero) this goal does nothing at all, and the human behaves exactly as
 * he did before this round.
 */
public class HumanPostGoal extends Goal
{
    /** Close enough: the walk is over at two and a half blocks from the post. */
    private static final double ARRIVE_DISTANCE_SQR = 6.25D;

    private static final double WALK_SPEED = 0.9D;

    /** How often the walk back picks a new path. */
    private static final int REPATH_TICKS = 20;

    /** A walk home that can not be finished is given up after this many ticks, so nobody walks forever. */
    private static final int GIVE_UP_TICKS = 400;

    private final PathfinderMob body;

    private int ticks;

    public HumanPostGoal(PathfinderMob body)
    {
        this.body = body;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return HumanBandState.isReturning(this.body) || this.takesUpThePost();
    }

    /**
     * @return true while this human stands in peace far outside the leash of his old post, which is the moment a new
     *         post is taken.
     */
    private boolean takesUpThePost()
    {
        if (!HungryCraftConfig.humanBandAggro() || HungryCraftConfig.humanBandLeashRadius() <= 0.0D)
        {
            return false;
        }

        long now = this.body.level().getGameTime();

        if (this.body.getTarget() != null || HumanBandState.lockedTarget(this.body, now) != null
                || HumanBandState.isReturning(this.body))
        {
            return false;
        }

        BlockPos anchor = HumanBandState.anchor(this.body);

        if (anchor == null)
        {
            return true;
        }

        double leash = HungryCraftConfig.humanBandLeashRadius();

        return this.body.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D) > leash * leash;
    }

    @Override
    public void start()
    {
        this.ticks = 0;

        if (!HumanBandState.isReturning(this.body))
        {
            HumanBandState.rememberAnchor(this.body);
            return;
        }

        this.walkHome();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.ticks >= GIVE_UP_TICKS || !HumanBandState.isReturning(this.body))
        {
            return false;
        }

        BlockPos anchor = HumanBandState.anchor(this.body);

        return anchor != null
                && this.body.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D)
                        > ARRIVE_DISTANCE_SQR;
    }

    @Override
    public void tick()
    {
        ++this.ticks;

        if (this.ticks % REPATH_TICKS == 0)
        {
            this.walkHome();
        }
    }

    @Override
    public void stop()
    {
        HumanBandState.setReturning(this.body, false);
        this.body.getNavigation().stop();
    }

    private void walkHome()
    {
        BlockPos anchor = HumanBandState.anchor(this.body);

        if (anchor != null)
        {
            this.body.getNavigation().moveTo(anchor.getX() + 0.5D, anchor.getY(), anchor.getZ() + 0.5D, WALK_SPEED);
        }
    }
}
