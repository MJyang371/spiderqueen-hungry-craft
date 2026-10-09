package spiderqueenhungrycraft.sweatlord;

import java.util.EnumSet;

import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The one goal that drives the Sweatlord fight (Sweatlord round).
 *
 * <p>Exactly like the ordinary human's combat goal, one goal owns the movement and the look of the body while the
 * brain works, and nothing else decides anything: several vanilla goals would fight over the same controls and the
 * body would look indecisive instead of like a player.
 *
 * <p>The goal only starts when the brain really wants the body (an enemy, an encounter, a running action or a threat);
 * the rest of the time the ordinary stroll and look goals walk him around.
 */
public class SweatlordCombatGoal extends Goal
{
    private final SweatlordSteveEntity mob;

    public SweatlordCombatGoal(SweatlordSteveEntity mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return this.mob.brain().wantsControl();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.mob.brain().wantsControl();
    }

    @Override
    public void start()
    {
        this.mob.brain().onGoalStart();
    }

    @Override
    public void tick()
    {
        this.mob.brain().tick();
    }

    @Override
    public void stop()
    {
        this.mob.brain().onGoalStop();
    }
}
