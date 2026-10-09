package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodBodyBrain;
import spiderqueenhungrycraft.brood.BroodBodyFighter;
import spiderqueenhungrycraft.brood.BroodBodyState;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderTemporaryCommand;

/**
 * The one fight goal of a brood body - the infested Steve (Infested Steve round, split round).
 *
 * <p>It owns the movement and the look of the body while a target exists and hands everything else to
 * {@link BroodBodyBrain}, this body's own brain. There is exactly one such goal on purpose: several vanilla goals would
 * fight over the same controls, and the body would look indecisive instead of driven.
 *
 * <p>It is the brood's own goal, not a human one: an order of the sceptre ends a fight in progress, which is the rule
 * every soldier of the brood lives by, and the fight it drives asks {@link BroodBodyFighter#mayFight} - the rule of the
 * brood - before it ever swings at anything.
 */
public class BroodBodyCombatGoal extends Goal
{
    /** The fight of this body: the questions about its gear, its hands and its health. */
    private final BroodBodyFighter mob;

    /** The body itself, for the parts of a mob the fight itself never asks about (its target, its age, its memory). */
    private final PathfinderMob body;

    /**
     * @param body the body this fight belongs to. It has to be a {@link BroodBodyFighter} - the infested Steve is one -
     *             because this fight is written against that seam and nothing else, so a second brood body could take
     *             this very fight over without a line of it being copied again.
     */
    public BroodBodyCombatGoal(PathfinderMob body)
    {
        if (!(body instanceof BroodBodyFighter fighter))
        {
            throw new IllegalArgumentException("not a humanoid fighter: " + body);
        }

        this.body = body;
        this.mob = fighter;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (this.isHeldByAnOrder())
        {
            return false;
        }

        return this.hasValidTarget() || this.hasReaction() || this.isEating();
    }

    @Override
    public boolean canContinueToUse()
    {
        // A move order of the queen ends a fight in progress as well, exactly as it does for every spider of the brood.
        if (this.isHeldByAnOrder())
        {
            return false;
        }

        return this.hasValidTarget() || this.hasReaction() || this.isEating();
    }

    /**
     * @return true while the queen has ordered this body to walk somewhere (Infested Steve round). An order of the
     *         sceptre wins over a fight - the very rule the ranged goal of the spiders lives by, and the reason a soldier
     *         of the queen never ignores an order in order to look for a fight.
     */
    private boolean isHeldByAnOrder()
    {
        return this.body instanceof SpiderMinionEntity spider
                && spider.getTemporaryCommand() == SpiderTemporaryCommand.MOVE_TO;
    }

    /**
     * The whole fight runs every game tick. A goal that only ticks on the vanilla throttle would make every counter
     * of the brain run at half speed, which is exactly the bug the hammer of Chad Steve was fixed for in Phase 9B2.
     */
    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void start()
    {
        this.body.getNavigation().stop();
        this.mob.brain().reset(this.mob);

        LivingEntity target = this.body.getTarget();

        if (BroodBodyBrain.DEBUG && target != null)
        {
            // The gear is logged with the target on purpose: it makes an "is it even a bowman?" question answerable
            // from the log alone.
            SpiderQueenHungryCraft.LOGGER.info("[Combatant/{}] acquired target {} ({}), bow={}, blade={}, shield={}, apples={}",
                    this.body.getStringUUID(), target.getName().getString(), target.getType().toShortString(),
                    this.mob.hasBow(), this.mob.hasMeleeWeapon(), this.mob.hasShield(),
                    this.mob.goldenAppleCount());
        }
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.body.getTarget();

        if (target != null)
        {
            this.mob.brain().tick(this.mob, target);
            return;
        }

        if (this.isEating())
        {
            // A bite that is already running is finished, even when the fight ended in between.
            this.mob.brain().tickEatingOnly(this.mob);
            return;
        }

        LivingEntity source = this.body.getLastHurtByMob();

        if (source != null)
        {
            // No target, but somebody just hit him: a guarded reaction, never a swing.
            this.mob.brain().tickReaction(this.mob, source);
        }
    }

    @Override
    public void stop()
    {
        this.mob.brain().reset(this.mob);
        this.body.getNavigation().stop();
    }

    /**
     * The target has to be alive and still a legal victim of <b>this body</b> ({@link BroodBodyFighter#mayFight}): the
     * rule of a human for a human, the rule of the brood for a soldier of the queen. The fight itself never decides who
     * may be attacked, so one shared fight can serve two kinds of soldier without ever arguing about the enemy.
     */
    private boolean hasValidTarget()
    {
        LivingEntity target = this.body.getTarget();
        return target != null && this.mob.mayFight(target);
    }

    /**
     * @return true while it is in the middle of a golden apple. Such a bite is always finished, a fight that ended
     *         in between must not leave him stuck in an eating state.
     */
    private boolean isEating()
    {
        return this.mob.brain().state() == BroodBodyState.EAT_GOLDEN_APPLE && this.body.isUsingItem()
                && this.body.getUseItem().is(Items.GOLDEN_APPLE);
    }

    /**
     * @return true while it was hit recently by somebody it may not attack: the shield reaction of this round. The
     *         vanilla revenge memory is used for it, so nothing of this lives longer than the vanilla one.
     */
    private boolean hasReaction()
    {
        LivingEntity source = this.body.getLastHurtByMob();

        if (source == null || !source.isAlive())
        {
            return false;
        }

        return this.body.tickCount - this.body.getLastHurtByMobTimestamp() < BroodBodyBrain.REACTION_TICKS;
    }
}
