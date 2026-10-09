package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SkeletonSpiderEntity;

/**
 * The shooting of the skeleton spider (Special Spiders round I, accuracy pass).
 *
 * <p>It is the ranged attack goal of the game in the shape this spider needs: walk towards the victim while it is out of
 * range, <b>stand still and shoot</b> once it is inside, fire one shot every attack interval, and never fire through a
 * wall.
 *
 * <p><b>Why not the vanilla goal.</b> The ranged attack goal of the game asks the senses of the mob whether it can see
 * its victim, and that vision check of a creature ({@code Sensing#hasLineOfSight} on top of
 * {@code LivingEntity#hasLineOfSight}) stops answering beyond about eleven blocks. For a skeleton that is fine - it has
 * a bow, a step and a wide field of view - but for this spider it would quietly turn its twenty block range into a lie:
 * it would keep closing in until it stood in the face of its victim, shooting badly on the move, because as far as its
 * senses were concerned the victim was never visible. This goal therefore checks the shooting line itself, with the
 * ordinary block clip of the game over the real flight line and no distance limit.
 *
 * <p>Everything else is deliberately the behaviour of a skeleton: the same walk-then-stop rhythm, the same five ticks of
 * a settled aim before the first arrow, the same look control, and a shot only when the victim is really in range and
 * really visible.
 */
public class SpiderRangedAttackGoal extends Goal
{
    /** How many ticks of a clear line of fire are needed before this spider shoots at all. */
    private static final int SETTLE_TICKS = 5;

    /**
     * The closest this spider ever walks towards a victim it already has in shooting range.
     *
     * <p>Six blocks is well outside the reach of any melee attacker, and inside it the spider stands and shoots instead
     * of pressing on. It is the guarantee behind the whole goal: even if the ground, a block or a quirk of the line
     * check kept telling the spider that it cannot shoot from where it is, it still never ends up in the face of its
     * victim. A ranged spider does not brawl.
     */
    private static final double HOLD_DISTANCE = 6.0D;
    private static final double HOLD_DISTANCE_SQR = HOLD_DISTANCE * HOLD_DISTANCE;

    private final SkeletonSpiderEntity spider;
    private final double speedModifier;
    private final int attackIntervalTicks;
    private final float attackRadius;
    private final double attackRadiusSqr;

    private int attackTicks = -1;
    private int clearTicks;

    public SpiderRangedAttackGoal(SkeletonSpiderEntity spider, double speedModifier, int attackIntervalTicks,
            float attackRadius)
    {
        this.spider = spider;
        this.speedModifier = speedModifier;
        this.attackIntervalTicks = attackIntervalTicks;
        this.attackRadius = attackRadius;
        this.attackRadiusSqr = (double) attackRadius * (double) attackRadius;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return this.spider.getTarget() != null && !hasMoveOrder();
    }

    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.spider.getTarget();

        return target != null && target.isAlive() && !hasMoveOrder();
    }

    /**
     * @return true when the queen gave this spider a place to walk to (Brood command: the scout move)
     *
     * <p>Shooting while walking to a spot she picked is what made a spider that could not kill its victim - an enderman
     * that teleports away from every trident, for one - look like it had stopped listening: it held the move flag, kept
     * the fight and the new order never took effect. An order of the queen now wins over a fight, which is the same rule
     * the rest of the brood already lives by.
     */
    private boolean hasMoveOrder()
    {
        return this.spider.getTemporaryCommand() == spiderqueenhungrycraft.brood.SpiderTemporaryCommand.MOVE_TO;
    }

    @Override
    public void start()
    {
        this.attackTicks = 0;
        this.clearTicks = 0;
    }

    @Override
    public void stop()
    {
        this.spider.getNavigation().stop();
        this.attackTicks = -1;
        this.clearTicks = 0;
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.spider.getTarget();

        if (target == null)
        {
            return;
        }

        double distanceSqr = this.spider.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean clear = hasClearShot(target);

        if (clear)
        {
            ++this.clearTicks;
        }
        else
        {
            this.clearTicks = 0;
        }

        // Standing still is decided as soon as this spider may shoot from where it is: the victim is in range and the
        // line to it is free. Nothing else is waited for, because every step taken in the meantime is a step closer to
        // the melee this spider is supposed to avoid.
        boolean inRange = distanceSqr <= this.attackRadiusSqr;
        boolean canShootFromHere = clear && inRange;

        // Firing itself waits for the aim to settle for a moment, like the bow of a skeleton does.
        boolean mayShoot = canShootFromHere && this.clearTicks >= SETTLE_TICKS;

        // The last line of defence against a spider that walks into its victim: inside this distance it never takes
        // another step towards a victim it could already shoot at. If the line happens to be blocked there, it holds its
        // ground and waits for the first clear moment - like a skeleton whose bow is blocked, and never like a brawler.
        boolean holdGround = canShootFromHere || (inRange && distanceSqr <= HOLD_DISTANCE_SQR);

        if (holdGround)
        {
            // In range with a clear line: stop walking and shoot from where the spider stands. Shooting on the move is
            // what spoils the aim of a spider this low to the ground.
            this.spider.getNavigation().stop();
        }
        else
        {
            // Out of range, or the victim is behind cover: only then does it walk, and it walks all the way to where it
            // can really shoot from - not into melee.
            this.spider.getNavigation().moveTo(target, this.speedModifier);
        }

        this.spider.getLookControl().setLookAt(target, 30.0F, 30.0F);

        // Exactly one shot per attack interval: the timer of the next shot starts with the shot itself, so thirty
        // ticks mean thirty ticks. While the timer stands at zero and the shot is not possible yet (a victim in cover),
        // it simply stays there and takes the very first moment it can - no arrow is ever thrown into a wall.
        if (this.attackTicks > 0)
        {
            --this.attackTicks;
        }

        if (this.attackTicks <= 0 && mayShoot)
        {
            float distanceFactor = Mth.clamp((float) Math.sqrt(distanceSqr) / this.attackRadius, 0.1F, 1.0F);

            this.spider.performRangedAttack(target, distanceFactor);
            this.attackTicks = this.attackIntervalTicks;
        }
    }

    /**
     * @return true when a projectile would really reach that creature: the line is drawn from the mouth of the spider to
     *         the very point the shots of this spider are aimed at, and it must be free of blocks.
     *
     * <p>The aim point is the same one {@link SkeletonSpiderEntity#performRangedAttack} uses, so this check and the
     * arrow always agree on where "the victim" is. There is no distance limit in it - the range of the spider is decided
     * by its attack radius alone.
     */
    private boolean hasClearShot(LivingEntity target)
    {
        Vec3 from = new Vec3(this.spider.getX(), this.spider.getEyeY(), this.spider.getZ());
        Vec3 to = new Vec3(target.getX(), target.getY(1.0D / 3.0D), target.getZ());
        BlockHitResult hit = this.spider.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this.spider));

        return hit.getType() == HitResult.Type.MISS;
    }
}
