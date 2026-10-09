package spiderqueenhungrycraft.ai;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Items;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderTemporaryCommand;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.hostility.HumanBandState;

/**
 * The one fight goal of a standard human combatant (Phase HF0A-GAMEPASS1).
 *
 * <p>It owns the movement and the look of the mob while a target exists and hands everything else to
 * {@link HumanCombatBrain}. There is exactly one such goal on purpose: several vanilla goals would fight over the
 * same controls, and the combatant would look indecisive instead of human.
 */
public class HumanCombatGoal extends Goal
{
    /** The fight of this body: the questions about its gear, its hands and its health. */
    private final HumanoidFighter mob;

    /** The body itself, for the parts of a mob the fight itself never asks about (its target, its age, its memory). */
    private final PathfinderMob body;

    /**
     * @param body the body this fight belongs to. It has to be a {@link HumanoidFighter} - a human combatant or an
     *             infested Steve - because the fight of this mod is written against that interface and nothing else, so
     *             one and the same fight drives every human body of the mod.
     */
    public HumanCombatGoal(PathfinderMob body)
    {
        if (!(body instanceof HumanoidFighter fighter))
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
        return this.mayRunTheFight();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.mayRunTheFight();
    }

    /**
     * @return true when the fight of this body may run right now. The leash of the pack aggro round is asked first: a
     *         soldier who was dragged out of his post ends the fight, drops everything of it and walks back.
     */
    private boolean mayRunTheFight()
    {
        if (this.isBeyondTheLeash() && !HumanBandState.isReturning(this.body))
        {
            this.disengageBecauseOfTheLeash();
            return false;
        }

        // A move order of the queen ends a fight in progress as well, exactly as it does for every spider of the brood.
        if (this.isHeldByAnOrder())
        {
            return false;
        }

        return this.hasValidTarget() || this.hasReaction() || this.isEating();
    }

    /**
     * @return true while this human was dragged so far from his post that the fight is over for him. The leash is the
     *         one rule that makes two archers a nuisance instead of shepherds: whoever runs away can not pull a whole
     *         group of soldiers across the map, he can only pull them to the edge of their own ground.
     *
     *         <p>A human without a post, without the pack rules or with a leash of zero is never beyond it, so this
     *         round changes nothing for a pack that switches it off.
     */
    private boolean isBeyondTheLeash()
    {
        if (!HungryCraftConfig.humanBandAggro())
        {
            return false;
        }

        double leash = HungryCraftConfig.humanBandLeashRadius();

        if (leash <= 0.0D)
        {
            return false;
        }

        BlockPos anchor = HumanBandState.anchor(this.body);

        if (anchor == null)
        {
            return false;
        }

        return this.body.distanceToSqr(anchor.getX() + 0.5D, anchor.getY() + 0.5D, anchor.getZ() + 0.5D) > leash * leash;
    }

    /** Ends the fight of a human who was dragged out of his post: nothing of it survives, and he walks back home. */
    private void disengageBecauseOfTheLeash()
    {
        if (HumanCombatBrain.DEBUG && this.body.getTarget() != null)
        {
            SpiderQueenHungryCraft.LOGGER.info("[Band/{}] leash broken, leaving {} and walking back to the post",
                    this.body.getStringUUID(), this.body.getTarget().getName().getString());
        }

        this.body.setTarget(null);
        HumanBandState.releaseLock(this.body);
        HumanBandState.markPeace(this.body, this.body.level().getGameTime(), HungryCraftConfig.humanBandPeaceTicks());
        HumanBandState.setReturning(this.body, true);
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

        if (HumanCombatBrain.DEBUG && target != null)
        {
            // The gear is logged with the target on purpose: it makes an "is he even a bowman?" question answerable
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

        // The fight is over, so the commitment goes with it: the lock is released and a short peace starts, during
        // which only a remembered attacker may pull this human into the next one.
        HumanBandState.releaseLock(this.body);
        HumanBandState.markPeace(this.body, this.body.level().getGameTime(), HungryCraftConfig.humanBandPeaceTicks());
    }

    /**
     * The target has to be alive and still a legal victim of <b>this body</b> ({@link HumanoidFighter#mayFight}): the
     * rule of a human for a human, the rule of the brood for a soldier of the queen. The fight itself never decides who
     * may be attacked, so one shared fight can serve two kinds of soldier without ever arguing about the enemy.
     */
    private boolean hasValidTarget()
    {
        LivingEntity target = this.body.getTarget();
        return target != null && this.mob.mayFight(target);
    }

    /**
     * @return true while he is in the middle of a golden apple. Such a bite is always finished, a fight that ended
     *         in between must not leave him stuck in an eating state.
     */
    private boolean isEating()
    {
        return this.mob.brain().state() == HumanCombatState.EAT_GOLDEN_APPLE && this.body.isUsingItem()
                && this.body.getUseItem().is(Items.GOLDEN_APPLE);
    }

    /**
     * @return true while he was hit recently by somebody he may not attack: the shield reaction of this round. The
     *         vanilla revenge memory is used for it, so nothing of this lives longer than the vanilla one.
     */
    private boolean hasReaction()
    {
        LivingEntity source = this.body.getLastHurtByMob();

        if (source == null || !source.isAlive())
        {
            return false;
        }

        return this.body.tickCount - this.body.getLastHurtByMobTimestamp() < HumanCombatBrain.REACTION_TICKS;
    }
}
