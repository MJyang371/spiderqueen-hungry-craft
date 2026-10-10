package spiderqueenhungrycraft.soulfire.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireTargeting;

/**
 * The only goal of a soul fire dragon that is allowed to look for an enemy (round 7C).
 *
 * <p>It answers exactly one question - <b>is this unit hunting right now, and is there anything in its hunting
 * ground?</b> - and it answers it only for the free attack order: with any other order standing, and while a breath
 * skill or a forced victim is running, the goal refuses to start and the unit looks at nothing at all. That is the
 * other half of "no autonomous hunting": the unit is not a monster with a target selector, it is a unit that hunts
 * when its queen tells it to hunt.
 *
 * <p>The victim it picks is the nearest creature the friendship rule allows ({@link SoulfireTargeting}), around the
 * anchor of the unit when it has one and around the unit itself when it does not - so a hunt ordered at a post never
 * chases a rabbit halfway across the world.
 */
public class SoulfireFreeAttackGoal extends TargetGoal
{
    /** How far around the hunting ground a victim is looked for, in blocks. */
    private static final double HUNT_RADIUS = 24.0D;

    private final SoulfireDragonEntity dragon;

    public SoulfireFreeAttackGoal(SoulfireDragonEntity dragon)
    {
        super(dragon, false);
        this.dragon = dragon;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (this.dragon.getOrderMode() != SpiderOrderMode.FREE_ATTACK)
        {
            return false;
        }

        if (this.dragon.hasBreathOrder() || this.dragon.getForcedTarget() != null)
        {
            // A temporary command outranks the hunt, and the hunt does not even begin while one runs.
            return false;
        }

        LivingEntity victim = this.nearestVictim();

        if (victim == null)
        {
            return false;
        }

        this.targetMob = victim;

        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity victim = this.dragon.getTarget();

        return this.dragon.getOrderMode() == SpiderOrderMode.FREE_ATTACK && !this.dragon.hasBreathOrder()
                && SoulfireTargeting.isHuntable(this.dragon, victim);
    }

    @Override
    public void stop()
    {
        this.dragon.setTarget(null);
        super.stop();
    }

    /** @return the nearest creature this unit may hunt, or null when its hunting ground is empty. */
    @Nullable
    private LivingEntity nearestVictim()
    {
        Vec3 centre = this.dragon.getAnchor() == null ? this.dragon.position()
                : Vec3.atBottomCenterOf(this.dragon.getAnchor());
        AABB ground = new AABB(centre, centre).inflate(HUNT_RADIUS);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;

        for (LivingEntity candidate : this.dragon.level().getEntitiesOfClass(LivingEntity.class, ground))
        {
            if (!SoulfireTargeting.isHuntable(this.dragon, candidate))
            {
                continue;
            }

            double distance = this.dragon.distanceToSqr(candidate);

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = candidate;
            }
        }

        return best;
    }
}
