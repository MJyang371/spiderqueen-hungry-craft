package spiderqueenhungrycraft.entity;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The complete attack behaviour of Chad Steve (Phase 9A-GAMEPASS1, rewritten in Phase 9B).
 *
 * <p>A vanilla {@code MeleeAttackGoal} damages the moment it swings and on its own cooldown, which would make a
 * player lose health while the hammer is still in the air. This goal replaces it completely and is the only path
 * that can ever damage anything:
 *
 * <pre>
 * idle / chasing  -&gt;  target inside strike range  -&gt;  start slam (swing tick 0)
 *   -&gt;  swing tick 20: 3x3x2 area + dust + anvil sound, damage once
 *   -&gt;  swing tick 40: swing over, short recovery  -&gt;  chasing / wandering
 * </pre>
 *
 * <p>Two things were wrong in the first version and are fixed here:
 * <ol>
 * <li>The goal could never chase. It only started when the target was already inside the strike range, and
 * because a goal is only ticked while it runs, the chase code below it was dead. A goal that can not run can not
 * walk either, so Chad Steve just stood there. Walking into range is now part of {@code canUse()}.</li>
 * <li>The client animation and the server timer were started with different reference points. Now the server
 * starts the swing with tick 0 in the same tick it sets the synced state, and the client starts the animation at
 * that same tick, so the impact frame of the animation (1.0 second) and the damage tick (20) are the same
 * moment.</li>
 * </ol>
 *
 * <p>The damage is dealt on <b>exactly one</b> tick, {@link ChadSteveEntity#HAMMER_DAMAGE_TICK}. Before it
 * nothing happens, after it nothing happens, and a boolean guard makes a second hit of the same swing impossible.
 *
 * <p><b>Phase 9B2 fix:</b> the two reports of "the hammer already hit the ground, the damage comes a good second
 * later" both came from one missing override. {@link #requiresUpdateEveryTick()} was not overridden, so the whole
 * swing counter ran on half of the game ticks and reached its impact tick after about two seconds instead of one -
 * the animation was always right, the server timer was slow. See the javadoc of that method.
 *
 * <p><b>Phase 9B3 fix:</b> the report "a monster that hits him from the side or from behind is never hit back, he
 * keeps swinging forward" had the same kind of single cause on the server side. Vanilla never turns a standing mob:
 * the look control only moves the head, the body follows the walk direction and the entity yaw is written by the
 * movement control alone - and the slam area of this mod is measured in exactly that entity yaw. A victim standing
 * behind or beside him therefore triggered a swing (only the distance was asked, never the direction) whose 3x4x5
 * box pointed where he had last walked. Two things are new: {@link #faceTarget()} pivots him toward his victim on
 * the spot, and a swing only starts once the slam box really covers the target, with a sixty tick safety valve for
 * victims the box can never cover (a monster below a ledge, for example).
 */
public class ChadSteveHammerAttackGoal extends Goal
{
    /** Walking speed while chasing, relative to the movement speed attribute. */
    private static final double CHASE_SPEED = 1.0D;

    /** The swing is started once the target is this close, so the hammer has room to land. */
    private static final double STRIKE_RANGE = 3.2D;

    /**
     * The stomach core is the whole cell, not a creature standing in front of him (Phase 9C, part H). The cavity of
     * a cell has a radius of five blocks, so every position inside the cell is within this reach: the wall is hit
     * from wherever he happens to stand, which is exactly what the core logic intends. No line of sight, no pickable
     * target and no collision box is required for it.
     */
    private static final double CORE_STRIKE_RANGE = 6.0D;

    /**
     * While chasing, a target beyond this distance is given up. It is deliberately far, the real range limit is
     * the FOLLOW_RANGE attribute of the entity and the vanilla target goal.
     */
    private static final double CHASE_ABORT_RANGE = 40.0D;

    /** A short break after the swing, so a new slam can not start in the same breath. */
    private static final int RECOVERY_TICKS = 10;

    /**
     * How far he may turn toward his victim in one tick while he stands and fights (Phase 9B3). Twelve degrees per
     * tick means a full about face takes a third of a second, which reads as a heavy pivot instead of a snap.
     */
    private static final float TURN_SPEED_DEGREES_PER_TICK = 12.0F;

    /**
     * Safety valve of the same fix: when a victim sits inside strike range but can never enter the slam box - a
     * monster two blocks below a ledge is the classic case - he still swings after this many ticks of turning, so
     * he can never end up frozen in front of a target he is unable to reach with the hammer.
     */
    private static final int TURN_THEN_SWING_ANYWAY_TICKS = 60;

    private final ChadSteveEntity mob;

    /** Ticks spent turning on the spot while the victim was inside strike range but outside the slam box. */
    private int turningTicks;

    /** True from the first tick of a swing until the recovery of that swing is over. */
    private boolean swinging;

    /** Ticks since the swing started, {@code 0} on the first tick of the swing. */
    private int swingTick;

    /**
     * Guard for the single hit of one swing (Phase 9B2, part E). It is cleared when a swing starts and set once
     * the damage tick has fired, so no other code path - not the recovery, not the end of the animation and not a
     * restart of the goal - can ever deal a second hit for the same swing.
     */
    private boolean hasDealtDamageThisSwing;

    /** The tick on which the next swing may start. */
    private int nextAttackTick;

    public ChadSteveHammerAttackGoal(ChadSteveEntity mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /**
     * Runs as long as there is a valid target inside the chase radius, whether or not it is already in strike
     * range. This is what makes him walk up to his victim instead of waiting for the victim to walk into him.
     */
    @Override
    public boolean canUse()
    {
        LivingEntity target = this.mob.getTarget();

        if (!this.mob.isAttackableTarget(target))
        {
            return false;
        }

        if (this.mob.tickCount < this.nextAttackTick)
        {
            return false;
        }

        return this.mob.distanceToSqr(target) <= CHASE_ABORT_RANGE * CHASE_ABORT_RANGE;
    }

    @Override
    public boolean canContinueToUse()
    {
        // A swing that has started always runs to its end, which keeps the impact timing honest even when the target
        // dies or runs away in the middle of it. The swing is guaranteed to end, because tick() advances it without
        // ever looking at the target first.
        if (this.swinging)
        {
            return true;
        }

        LivingEntity target = this.mob.getTarget();

        if (!this.mob.isAttackableTarget(target))
        {
            return false;
        }

        return this.mob.distanceToSqr(target) <= CHASE_ABORT_RANGE * CHASE_ABORT_RANGE;
    }

    @Override
    public void start()
    {
        this.swinging = false;
        this.swingTick = 0;
        this.hasDealtDamageThisSwing = false;
        this.turningTicks = 0;
    }

    /**
     * <b>This one method is the actual fix of the "damage comes a second after the hammer landed" bug.</b>
     *
     * <p>{@code Goal.requiresUpdateEveryTick()} defaults to {@code false}, and {@code Mob.serverAiStep()} uses the
     * vanilla throttle: on the odd ticks it only calls {@code tickRunningGoals(false)}, which ticks <b>nothing
     * but</b> the goals that opted in here, and only on the even ticks does the full {@code GoalSelector.tick()}
     * run. A swing counter that lives inside {@code tick()} therefore advanced on half of the game ticks, so the
     * impact arrived after roughly two seconds instead of one - exactly the delay that was reported twice, and the
     * reason the hammer seemed to land long before the damage. The vanilla {@code MeleeAttackGoal} overrides this
     * method for the same reason: attack timing needs every single tick.
     */
    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void stop()
    {
        this.swinging = false;
        this.swingTick = 0;
        this.hasDealtDamageThisSwing = false;
        this.turningTicks = 0;
        // Never leave an attack state behind: this clears the synced swing state, so every client stops the
        // animation and falls back to walk or idle.
        this.mob.serverStopSlam();
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        // A running swing is advanced first, before anything looks at the target. It must be able to finish even
        // when the target died or was dropped in the middle of the two second animation.
        //
        // This is the fix of a real freeze: with the target check in front, a target that vanished during the swing
        // stopped the counter for good, while {@link #canContinueToUse()} kept the goal alive because it was still
        // swinging. The goal then held Goal.Flag.MOVE and Goal.Flag.LOOK forever, the synced slam state was never
        // cleared, and Chad Steve stood in place with walk and idle suppressed - which players reported as "he lost
        // his AI". A web shot healed it only because the provocation of that shot set a new target
        // (QueenProvocation.provoke), which made this very early return stop happening.
        if (this.swinging)
        {
            // Keep the hammer pointed at the victim while the swing winds up: the damage tick uses the facing of
            // that very moment, so a target that circles him during the wind up is still hit. After the damage tick
            // the facing is left alone, so the recovery cannot drag his body around.
            if (this.swingTick < ChadSteveEntity.HAMMER_DAMAGE_TICK)
            {
                this.faceTarget();
            }

            this.tickSwing();
            return;
        }

        LivingEntity target = this.mob.getTarget();

        if (target == null)
        {
            this.turningTicks = 0;
            return;
        }

        this.faceTarget();

        // The stomach core is reached from anywhere inside its cell, everything else has to be walked up to first.
        double strikeRange = this.mob.isCellCore(target) ? CORE_STRIKE_RANGE : STRIKE_RANGE;

        if (this.mob.distanceToSqr(target) > strikeRange * strikeRange)
        {
            // Walk up to the target. The walking animation is played by the client from the real movement, the
            // slam always wins once it has started. The path direction turns his body, so nothing is turned here.
            this.turningTicks = 0;
            this.mob.getNavigation().moveTo(target, CHASE_SPEED);
            return;
        }

        // Close enough - but a swing is only worth starting when the hammer would really land on the victim. This is
        // the fix of the report that a monster behind or beside him was hammered at forever and never hit: the slam
        // area is measured in his own facing, so he pivots on the spot until that area covers the target and only
        // then swings. The core of a stomach is the whole cell and is hit from anywhere, so it skips the question.
        if (!this.mob.isCellCore(target) && !this.mob.isTargetInsideHammerArea(target)
                && this.turningTicks < TURN_THEN_SWING_ANYWAY_TICKS)
        {
            this.mob.getNavigation().stop();
            ++this.turningTicks;
            return;
        }

        this.beginSwing();
    }

    /**
     * Points him at his victim: the head through the vanilla look control, and - while he is standing, because the
     * navigation owns the body yaw while he walks - his whole facing through
     * {@link ChadSteveEntity#faceTowards(double, double, float)} (Phase 9B3).
     *
     * <p>This one method is what closes the reported bug: the look control only ever moves the head, the slam area
     * is measured in the entity yaw, and vanilla writes that yaw only while a mob actually walks. A target that
     * stood behind or beside him therefore kept the hammer aimed wherever he had last walked.
     */
    private void faceTarget()
    {
        LivingEntity target = this.mob.getTarget();

        if (target == null || !target.isAlive())
        {
            return;
        }

        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (!this.mob.getNavigation().isDone())
        {
            // Walking already turns his body toward the path; fighting the movement control would only shake him.
            return;
        }

        this.mob.faceTowards(target.getX(), target.getZ(), TURN_SPEED_DEGREES_PER_TICK);
    }

    /**
     * Starts the swing. The synced state and the animation on every client begin in this very tick with swing
     * tick 0, so nothing can shift the animation against the server timer.
     */
    private void beginSwing()
    {
        this.swinging = true;
        this.swingTick = 0;
        this.hasDealtDamageThisSwing = false;
        this.turningTicks = 0;
        this.mob.getNavigation().stop();
        this.mob.serverStartSlam();

        if (ChadSteveEntity.DEBUG_SLAM_TIMING)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[ChadSteve#{}] start slam at swingTick=0 (damage tick {}, impact frame {})", this.mob.getId(),
                    ChadSteveEntity.HAMMER_DAMAGE_TICK, ChadSteveEntity.HAMMER_IMPACT_TICK);
        }
    }

    /**
     * Advances the running swing by one tick. This method is now guaranteed to run on every game tick of the swing
     * (see {@link #requiresUpdateEveryTick()}), so swing tick N is N ticks after the swing started and the counters
     * line up with the animation the clients play.
     *
     * <p>Damage and feedback are two separate moments on purpose: the damage sits on
     * {@link ChadSteveEntity#HAMMER_DAMAGE_TICK} (which is the impact frame while the lead is zero) and the dust and
     * the anvil landing sit on {@link ChadSteveEntity#HAMMER_FEEDBACK_TICK}. Nothing else in this goal can hurt
     * anything.
     */
    private void tickSwing()
    {
        this.swingTick++;

        // Mirror the counter first, so the synced state and the log lines of this tick all read the same number.
        this.mob.serverSetSlamTick(this.swingTick);

        if (this.swingTick == ChadSteveEntity.HAMMER_FEEDBACK_TICK)
        {
            this.mob.playImpactFeedback();
        }

        if (this.swingTick == ChadSteveEntity.HAMMER_DAMAGE_TICK && !this.hasDealtDamageThisSwing)
        {
            // The one and only hit of this swing: the 3x4x5 area in front of him. The boolean guard makes a
            // second hit of the same swing impossible, whatever else happens to this goal.
            this.hasDealtDamageThisSwing = true;
            this.mob.performHammerDamage();
        }

        if (this.swingTick > ChadSteveEntity.HAMMER_ANIMATION_DURATION_TICKS)
        {
            this.swinging = false;
            this.swingTick = 0;
            this.mob.serverStopSlam();
            this.nextAttackTick = this.mob.tickCount + RECOVERY_TICKS;

            if (ChadSteveEntity.DEBUG_SLAM_TIMING)
            {
                SpiderQueenHungryCraft.LOGGER.info("[ChadSteve#{}] slam over after {} ticks", this.mob.getId(),
                        ChadSteveEntity.HAMMER_ANIMATION_DURATION_TICKS);
            }
        }
    }
}
