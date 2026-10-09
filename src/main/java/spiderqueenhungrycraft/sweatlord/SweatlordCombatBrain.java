package spiderqueenhungrycraft.sweatlord;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.HumanCombatantTargeting;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.skill.MeleeSkills;
import spiderqueenhungrycraft.sweatlord.skill.RangedSkills;
import spiderqueenhungrycraft.sweatlord.skill.SupportSkills;
import spiderqueenhungrycraft.sweatlord.skill.WaterBucketClutch;

/**
 * The fight of a Sweatlord Steve (Sweatlord round).
 *
 * <p>The brain is deliberately thin: it senses, it picks the strategy and it decides which skill gets the body. Every
 * bow shot, egg burst, pearl jump, apple, shield reaction, sword swing and axe hit is a {@link SweatlordSkill} with its
 * own cooldown and its own lifetime, so the high level state stays small enough to read in a log line.
 *
 * <p><b>Priority, highest first.</b>
 *
 * <ol>
 * <li>lethal environment safety ({@link EnvironmentSafety}) - nothing he does may walk him into lava or off a cliff,</li>
 * <li>an incoming projectile ({@link ProjectileDefenseInterrupt}) - a real threat preempts whatever is running,</li>
 * <li>the retreat at low health ({@link SupportSkills.RetreatHealSkill}),</li>
 * <li>the first enchanted golden apple of the encounter ({@link SupportSkills.EnchantedGapplePrebuffSkill}),</li>
 * <li>the normal attack skills, by their own priority,</li>
 * <li>and the ordinary approach or repositioning when nothing is ready.</li>
 * </ol>
 *
 * <p><b>The encounter.</b> A short loss of sight, a wall between them or a switch between ranged and melee never ends
 * an encounter: it only ends when the enemy is gone for {@link #ENCOUNTER_TIMEOUT_TICKS} ticks (or dead with nobody
 * else in sight). The enchanted golden apple is used once per encounter and the flag is only cleared when the
 * encounter really ends, which is what makes "one god apple per fight, and a new one in the next fight" true instead
 * of "one per state change".
 */
public final class SweatlordCombatBrain
{
    /** How long the enemy may be out of sight before the encounter is over (12 seconds). */
    public static final int ENCOUNTER_TIMEOUT_TICKS = 240;

    /** Below this fraction of his health he retreats, heals once and comes back. */
    public static final float RETREAT_HEALTH_RATIO = 0.32F;

    /** Above this fraction he considers himself fit for the next round. */
    public static final float REENGAGE_HEALTH_RATIO = 0.70F;

    /** How far an enemy is looked for. */
    public static final double TARGET_SEARCH_RANGE = 32.0D;

    /** The priorities of the skills, highest first. */
    public static final int PRIORITY_RETREAT = 90;
    public static final int PRIORITY_PREBUFF = 80;
    public static final int PRIORITY_SHIELD_BREAK = 70;
    public static final int PRIORITY_PEARL = 60;
    public static final int PRIORITY_BOW = 50;
    public static final int PRIORITY_TRIDENT = 45;
    public static final int PRIORITY_EGG = 40;
    public static final int PRIORITY_SWORD = 30;

    /**
     * The two bucket emergencies, above every attack of the kit and even above the retreat: a fall that would kill him
     * and a body that is burning are more urgent than any of them. The fall outranks the fire, because a burning body
     * still has seconds and a body at terminal velocity does not.
     */
    public static final int PRIORITY_MLG_CLUTCH = 110;
    public static final int PRIORITY_FIRE_CLUTCH = 100;

    /** One debug line per state change is enough; this switch gates everything else. */
    public static final boolean DEBUG = Boolean.getBoolean("spiderqueenhungrycraft.sweatlord.debug");

    private final SweatlordSteveEntity mob;
    private final List<SweatlordSkill> skills = new ArrayList<>();

    /**
     * The bucket emergencies, asked before everything else - even before the projectile guard.
     *
     * <p>They are ordinary {@link SweatlordSkill}s and not a second kind of thing: they carry a cooldown, they are
     * logged, they can take the body over and they are stopped like any other action. The only difference is the moment
     * they are asked, because a fall that will kill him does not wait for a bow draw to finish.
     */
    private final List<SweatlordSkill> emergencies = new ArrayList<>();

    /**
     * The instant projectile guard of this body: not a skill, an overlay (see {@link InstantProjectileGuard}). It is
     * asked before every attack and every skill of the kit, it owns nothing but a shield and a turn, and it can never
     * hold the body for more than {@link InstantProjectileGuard#MAX_HOLD_TICKS} ticks.
     */
    private final InstantProjectileGuard guard = new InstantProjectileGuard();

    /**
     * The ranged rounds of this body and the adaptation they carry: three full rounds in a row that never reached the
     * enemy, and he stops shooting at it and closes the gap with a pearl instead.
     */
    private final RangedAttackRoundTracker rangedRounds = new RangedAttackRoundTracker();

    /** The pearl jump of this brain, kept as a field because the adaptation has to call it directly. */
    private final SupportSkills.OffensivePearlSkill offensivePearl;

    private SweatlordCombatState state = SweatlordCombatState.IDLE;
    private String lastSyncedSkill = "none";
    private boolean lastSyncedEncounter;
    private boolean lastSyncedGapple;
    private boolean lastSyncedThreat;
    private String lastSyncedPearlMode = "none";

    @Nullable
    private SweatlordSkill active;

    private boolean encounter;
    private long lastEnemySeenTick;
    private boolean enchantedGappleUsedThisEncounter;
    private boolean retreatAppleEaten;
    private boolean retreatFinished;

    /** The pearl mode of the brain right now, only for the debug state. */
    private String pearlMode = "none";

    /** The summary of the last refused pearl plan, so a debug command can answer "why no pearl". */
    private String lastPearlRejection = "none";

    /** The tick the enemy scan of this brain belongs to, and what it found, so one tick never scans twice. */
    private long scannedTick = Long.MIN_VALUE;

    @Nullable
    private LivingEntity scannedEnemy;

    /** The same caching for the projectile scan of {@link #incomingShot()}: one look at the world per tick. */
    private long threatScanTick = Long.MIN_VALUE;
    private boolean threatScanFound;

    public SweatlordCombatBrain(SweatlordSteveEntity mob)
    {
        this.mob = mob;

        // The order here is only the order of the debug dump: the arbitration itself sorts by priority.
        this.skills.add(new SupportSkills.RetreatHealSkill(this));
        this.skills.add(new SupportSkills.EnchantedGapplePrebuffSkill(this));
        this.skills.add(new MeleeSkills.AxeShieldBreakSkill());
        this.offensivePearl = new SupportSkills.OffensivePearlSkill(this);
        this.skills.add(this.offensivePearl);
        this.skills.add(new RangedSkills.BowAttackSkill());
        this.skills.add(new RangedSkills.TridentAttackSkill());
        this.skills.add(new RangedSkills.EggBurstSkill());
        this.skills.add(new MeleeSkills.SwordMeleeSkill());

        // The emergencies, highest danger first: the fall outranks the fire, because a burning body still has seconds
        // and a body at terminal velocity does not.
        this.emergencies.add(new WaterBucketClutch.MlgClutchSkill());
        this.emergencies.add(new WaterBucketClutch.ExtinguishFireSkill());
    }

    /**
     * @return true while the brain wants the body: an enemy, an encounter, a running action, a threat - or a fall or a
     *         fire that would kill him even when nobody is around to watch.
     *
     *         <p><b>The lonely fire.</b> Burning belongs on this list exactly like the fatal fall does, and for the same
     *         reason: this brain is only ticked while its fight goal runs, and the fight goal only starts while the brain
     *         already wants the body. A fire that nobody was fighting was therefore never even looked at - the body
     *         burned down with a water bucket on its hip, and the water only ever came out while he happened to be in a
     *         fight at the same time. The bucket has to answer the fire itself, and not the fight the fire started in.
     */
    public boolean wantsControl()
    {
        return this.active != null || this.guard.isActive() || this.encounter || this.mob.getTarget() != null
                || this.mob.isUsingItem() || this.hasEnemyInReach() || this.isFallingFatally() || this.mob.isOnFire()
                || this.incomingShot();
    }

    /**
     * @return true when a real projectile is on a collision course with this body right now.
     *
     * <p><b>The wake up call of the reflex.</b> This brain is only ticked while its fight goal runs, and the goal only
     * starts while the brain already wants the body. A shot that is already in the air with nobody around to fight
     * would therefore never be looked at, and the instant projectile guard would never be asked - the same deadlock the
     * fire of a lonely body had. A projectile on a collision course is a reason of its own, and the scan is cached for
     * the tick so the goal question and the guard share one look at the world.
     */
    private boolean incomingShot()
    {
        long now = this.mob.level().getGameTime();

        if (this.threatScanTick != now)
        {
            this.threatScanTick = now;
            this.threatScanFound = ProjectileThreatDetector.worst(this.mob) != null;
        }

        return this.threatScanFound;
    }

    /**
     * @return true when the fall distance is already past the point where a landing starts to hurt.
     *
     * <p>It is one tick of arithmetic and no world query on purpose: this is only the wake up call of the brain, which
     * is not ticked at all while nothing else is going on. The real decision is made by the clutch itself, with the full
     * prediction of the landing.
     */
    private boolean isFallingFatally()
    {
        return !this.mob.onGround() && this.mob.getDeltaMovement().y < 0.0D
                && this.mob.fallDistance >= WaterBucketClutch.MIN_FALL_DAMAGE + 2.0F;
    }

    /**
     * @return true while a legal enemy really stands in reach right now.
     *
     * <p><b>The fix of the targeting deadlock.</b> This brain is the only thing that ever looks for an enemy, and this
     * brain is only ticked by its fight goal - which is only started while the brain already wants the body. A body
     * whose only reason to want the body was the enemy it had not found yet could therefore stand next to a Spider
     * Queen for ever and never look up; the failing test of the fix round reports exactly that, with the goal of the
     * body never running once. Asking whether an enemy is in reach is the missing reason, and the answer is cached for
     * the tick so the goal question and the decision that follows it share one scan.
     */
    public boolean hasEnemyInReach()
    {
        return this.enemyInReach(this.mob.level().getGameTime()) != null;
    }

    /** @return the nearest legal enemy right now, scanned at most once per tick. */
    @Nullable
    private LivingEntity enemyInReach(long now)
    {
        if (now != this.scannedTick)
        {
            this.scannedTick = now;
            this.scannedEnemy = this.findEnemy();
        }

        return this.scannedEnemy;
    }

    /** Called once when the goal takes over. */
    public void onGoalStart()
    {
        this.mob.loadout().equipCombatGrip();
    }

    /** Called when the goal gives the body back: nothing of the fight survives into the next one. */
    public void onGoalStop()
    {
        this.stopActive();
        this.guard.stop(this.mob);
        this.mob.stopUsingItem();
        this.mob.getNavigation().stop();
        this.setState(SweatlordCombatState.IDLE, "goal stopped");
    }

    /** One tick of the whole fight. */
    public void tick()
    {
        long now = this.mob.level().getGameTime();
        this.tickCooldowns();
        this.sense(now);

        CombatContext context = CombatContext.capture(this.mob);
        this.updateEncounter(context, now);

        // 1) The most immediately lethal danger owns the body before anything else on this list: a blocked arrow is
        //    survivable, a lethal fall and a burning body are not. The two bucket emergencies are asked here and not
        //    inside the arbitration, so they can take the body from a running draw or combo without a single flag.
        //
        //    They are asked before the environment safety of step 2 as well, and that order is the whole difference
        //    between a body that waters its own landing and a body that jumps off a cliff to get away from a fire: the
        //    environment rule only ever walks him somewhere, which extinguishes nothing and slows no fall down, while
        //    the bucket answers the danger itself. A round that had these two swapped had a burning Sweatlord walking in
        //    circles until he died, with the water bucket in his hand the whole time.
        if (this.emergencyTick(context, now))
        {
            this.syncDebug();
            return;
        }

        // 2) Lethal environment safety: no skill may walk him into lava, fire or off a cliff.
        if (this.handleEnvironmentSafety(context))
        {
            this.syncDebug();
            return;
        }

        // 3) A real projectile on a collision course owns the shield, and only for as long as it is really coming: the
        //    instant projectile guard is an overlay, never a skill, and it can not hold the body for more than a moment.
        if (this.guard.tick(this.mob))
        {
            this.stopActive();

            // <b>After</b> the skill was stopped and not before: the stop of a skill cleans up the use state of the body
            // (a bow draw is dropped, a shield of its own is put away), and an arrow that lands in exactly this tick would
            // therefore meet a body without a shield in its hand and go straight through the reflex window.
            this.guard.hold(this.mob);
            this.syncDebug();
            return;
        }

        // 4) The adaptation of a fight that can not be shot: three full ranged rounds in a row that never reached the
        //    enemy, and the pearl goes off instead of a fourth round that would miss as well.
        if (this.forcePearlGapClose(context, now))
        {
            this.syncDebug();
            return;
        }

        // 5) The whole attack side, by priority.
        this.arbitrate(context, now);
        this.syncDebug();
    }

    /**
     * The adaptation step of {@link #tick()}, asked directly: a test or a debug call can hand it a context and see
     * whether the body is really taken by the panic pearl, which is the same call the tick makes.
     *
     * @return true when the body was given to the forced pearl.
     */
    public boolean forcePearlGapClose(CombatContext context)
    {
        return this.forcePearlGapClose(context, this.mob.level().getGameTime());
    }

    /**
     * The "enough of this" moment of the ranged fight: a target this body has not been able to hit with
     * {@link RangedAttackRoundTracker#MISSES_FOR_FORCED_PEARL} full rounds in a row is closed with a pearl instead of
     * being shot at again.
     *
     * <p>It is a combat adaptation and not a normal skill choice, so it neither waits for the ranged skills to be
     * picked at random nor for the long normal cooldown of the pearl: the miss streak is what starts it. What it does
     * <b>not</b> do is cheat the world: the landing spot goes through the same {@code SafePearlPlanner} as every other
     * pearl of this body, so lava, a void, a cliff or a blocked landing refuses the jump - and a refused jump is a
     * graceful failure that only walks into the enemy and comes back later, never a body that stands still throwing
     * pearls.
     *
     * @return true when the body was given to the forced pearl.
     */
    private boolean forcePearlGapClose(CombatContext context, long now)
    {
        if (!this.rangedRounds.wantsForcedPearl(context))
        {
            return false;
        }

        if (this.active == this.offensivePearl)
        {
            return false;
        }

        // Never interrupt a body that is holding it for a reason a player would recognise (a pearl in the air).
        if (this.active != null && this.active.holdsBody(this.mob))
        {
            return false;
        }

        this.offensivePearl.force();
        this.offensivePearl.clearCooldown();
        this.stopActive();
        this.startSkill(this.offensivePearl, context, now);

        // One revenge pearl per streak: whatever happens to the jump, the three misses that started it are spent, so a
        // refused landing can not turn into a pearl every tick.
        this.rangedRounds.consumeMissStreak();
        this.log("forced pearl after " + RangedAttackRoundTracker.MISSES_FOR_FORCED_PEARL
                + " missed ranged rounds: " + this.rangedRounds.describe());

        return true;
    }

    /**
     * @return true while a bucket emergency owns the body.
     *
     * <p>It is asked before the projectile guard and before every attack, because the most immediately lethal danger
     * wins: an arrow can be blocked, a five block fall can not. The emergencies are ordinary skills of this brain, so
     * nothing here duplicates their cooldown, their logging or their stop - only the moment they are asked differs, and
     * a running emergency is ticked by exactly the same rules as any other action.
     */
    private boolean emergencyTick(CombatContext context, long now)
    {
        if (this.active != null && this.emergencies.contains(this.active))
        {
            this.active.tick(this.mob, context);

            if (this.active.shouldContinue(this.mob, context))
            {
                return true;
            }

            this.stopActive();
            return false;
        }

        SweatlordSkill best = null;

        for (SweatlordSkill skill : this.emergencies)
        {
            if (!skill.ready() || !skill.canStart(this.mob, context))
            {
                continue;
            }

            if (best == null || skill.priority() > best.priority())
            {
                best = skill;
            }
        }

        if (best == null)
        {
            return false;
        }

        // Whatever is running is dropped: this is the one moment where the body is not negotiable.
        this.stopActive();
        this.startSkill(best, context, now);

        return true;
    }

    /** Acquires and validates the enemy through the shared faction rules. */
    private void sense(long now)
    {
        LivingEntity target = this.mob.getTarget();

        if (target != null && (target.isRemoved() || !target.isAlive()
                || this.mob.distanceTo(target) > FOLLOW_RANGE_MARGIN
                || !CombatContext.isValidEnemy(this.mob, target)))
        {
            this.mob.setTarget(null);
            target = null;
        }

        if (target == null)
        {
            LivingEntity found = this.enemyInReach(now);

            if (found != null)
            {
                this.mob.setTarget(found);
                this.mob.setLastHurtByMob(found);
                target = found;
            }
        }

        if (target != null)
        {
            this.lastEnemySeenTick = now;
        }
    }

    /** A little more than the search range, so an enemy that steps back is not dropped every other tick. */
    private static final double FOLLOW_RANGE_MARGIN = TARGET_SEARCH_RANGE * 1.5D;

    @Nullable
    private LivingEntity findEnemy()
    {
        LivingEntity nearest = null;
        double best = TARGET_SEARCH_RANGE * TARGET_SEARCH_RANGE;

        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(LivingEntity.class,
                this.mob.getBoundingBox().inflate(TARGET_SEARCH_RANGE)))
        {
            if (!CombatContext.isValidEnemy(this.mob, candidate))
            {
                continue;
            }

            // The pack rules of the human units apply to him exactly as they do to the others: a body on its way back
            // to its post starts no new fight, and an armed human is not pulled away from the enemy he is committed to.
            if (!HumanCombatantTargeting.mayPickBandTarget(this.mob, candidate))
            {
                continue;
            }

            double distance = this.mob.distanceToSqr(candidate);

            if (distance < best)
            {
                best = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    /**
     * Starts and ends an encounter.
     *
     * <p>An encounter outlives a lost line of sight, a wall in the way and every switch between ranged and melee: it
     * only ends when the enemy has really been gone for {@link #ENCOUNTER_TIMEOUT_TICKS} ticks. That is what makes the
     * "one enchanted golden apple per fight" rule mean something - the flag is cleared here and nowhere else.
     */
    private void updateEncounter(CombatContext context, long now)
    {
        if (context.hasTarget())
        {
            if (!this.encounter)
            {
                this.encounter = true;
                this.enchantedGappleUsedThisEncounter = false;
                this.retreatAppleEaten = false;
                this.retreatFinished = false;
                this.log("encounter started against " + context.summary());
            }

            return;
        }

        if (this.encounter && now - this.lastEnemySeenTick >= ENCOUNTER_TIMEOUT_TICKS)
        {
            this.encounter = false;
            this.enchantedGappleUsedThisEncounter = false;
            this.retreatAppleEaten = false;
            this.retreatFinished = false;
            // The fight is over: the miss streak of the last enemy says nothing about the next one, and the rounds that
            // are still in the air belong to a fight nobody is watching any more.
            this.rangedRounds.reset();
            this.log("encounter ended after " + ENCOUNTER_TIMEOUT_TICKS + " ticks without an enemy");
            this.setState(SweatlordCombatState.IDLE, "encounter ended");
        }
    }

    /**
     * @return true when the body was taken over because the ground is lethal: he steps off the lava or away from the
     *         rim and does nothing else this tick.
     */
    private boolean handleEnvironmentSafety(CombatContext context)
    {
        if (!context.inDangerousFluid() && !context.badGroundAhead())
        {
            return false;
        }

        this.stopActive();

        for (net.minecraft.core.BlockPos candidate : EnvironmentSafety.ringCandidates(this.mob.level(),
                this.mob.position(), 2.0D, 6.0D))
        {
            this.mob.getNavigation().moveTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D, 1.2D);
            this.mob.markTransition("environment: stepping to safe ground " + candidate);
            this.log("environment safety: stepping to " + candidate);
            return true;
        }

        // Nothing safe in reach: at least do not walk further into it.
        this.mob.getNavigation().stop();
        this.mob.markTransition("environment: no safe step, holding still");
        return true;
    }

    private void tickCooldowns()
    {
        // <b>Every</b> skill of the brain, and the emergencies are skills like any other: a cooldown that is never
        // counted down is a bucket that works exactly once - the first fire of his life was answered and every later
        // one was not, because the fire clutch sat at "20 ticks left" for ever. The two lists are walked separately so
        // this per tick bookkeeping allocates nothing.
        for (SweatlordSkill emergency : this.emergencies)
        {
            emergency.tickCooldown();
        }

        for (SweatlordSkill skill : this.skills)
        {
            skill.tickCooldown();
        }
    }

    /**
     * Gives the body to the running skill for as long as it wants it, and otherwise to the highest priority skill that
     * can start right now.
     *
     * <p>A running attack is never preempted by another attack on purpose: every skill of this round finishes what it
     * started (a draw, a burst, a pearl, a swing), and only the two safety rules above may cut in. That is what keeps
     * the body out of half finished states.
     */
    private void arbitrate(CombatContext context, long now)
    {
        if (this.active != null)
        {
            // One action may take the body away from a running one, and it is asked before the running action ticks:
            // the read of a raised shield has to be able to cut into a sword fight, or it can never happen at all.
            SweatlordSkill takeOver = this.takeOver(context);

            if (takeOver != null)
            {
                this.stopActive();
                this.startSkill(takeOver, context, now);
                return;
            }

            this.active.tick(this.mob, context);

            if (this.active.shouldContinue(this.mob, context))
            {
                return;
            }

            this.stopActive();
        }

        SweatlordSkill best = null;

        for (SweatlordSkill skill : this.skills)
        {
            if (!skill.ready() || !skill.canStart(this.mob, context))
            {
                continue;
            }

            if (best == null || skill.priority() > best.priority())
            {
                best = skill;
            }
        }

        if (best != null)
        {
            this.startSkill(best, context, now);
            return;
        }

        this.defaultBehaviour(context, now);
    }

    private void startSkill(SweatlordSkill skill, CombatContext context, long now)
    {
        this.active = skill;
        skill.start(this.mob, context);
        this.mob.markTransition("skill " + skill.name() + " started: " + context.summary());
        this.log("skill " + skill.name() + " started against " + context.summary());
        this.setState(skill.state(), "skill " + skill.name());
    }

    /**
     * @return the ready action that may take the body from the running one, or null when none of them may.
     *
     * <p>The rule itself lives on the action ({@link SweatlordSkill#mayTakeOver}), so a future action can state the
     * same thing without this method ever learning its name.
     */
    @Nullable
    private SweatlordSkill takeOver(CombatContext context)
    {
        SweatlordSkill best = null;

        for (SweatlordSkill skill : this.skills)
        {
            if (skill == this.active || !skill.ready() || !skill.mayTakeOver(this.active)
                    || !skill.canStart(this.mob, context))
            {
                continue;
            }

            if (best == null || skill.priority() > best.priority())
            {
                best = skill;
            }
        }

        return best;
    }

    private void stopActive()
    {
        if (this.active != null)
        {
            SweatlordSkill finished = this.active;
            this.active = null;
            finished.stop(this.mob);
            this.log("skill " + finished.name() + " stopped");
        }
    }

    /**
     * What he does when no skill is ready and no cooldown allows a shot: close the distance, keep the ranged band, or
     * simply hold and face the enemy. This is the only place where the brain moves the body on its own.
     */
    private void defaultBehaviour(CombatContext context, long now)
    {
        if (!context.hasTarget())
        {
            this.setState(SweatlordCombatState.IDLE, "no enemy");
            this.mob.getNavigation().stop();
            return;
        }

        LivingEntity target = context.target();
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (context.distance() <= CombatContext.MELEE_DISTANCE)
        {
            this.setState(SweatlordCombatState.MELEE_COMBAT, "closing in for melee");
            this.mob.getNavigation().moveTo(target, 1.2D);
            return;
        }

        this.setState(SweatlordCombatState.RANGED_COMBAT, "keeping the ranged band");

        if (context.distance() > CombatContext.BOW_MAX_DISTANCE)
        {
            this.mob.getNavigation().moveTo(target, 1.1D);
        }
        else if (context.distance() < CombatContext.BOW_MIN_DISTANCE)
        {
            Vec3 away = this.mob.position().subtract(target.position());

            if (away.lengthSqr() > 1.0E-6D)
            {
                Vec3 step = this.mob.position().add(away.normalize().scale(3.0D));
                this.mob.getNavigation().moveTo(step.x, step.y, step.z, 1.1D);
            }
        }
        else
        {
            this.mob.getNavigation().stop();
        }
    }

    private void setState(SweatlordCombatState next, String reason)
    {
        if (this.state != next)
        {
            this.log("state " + this.state.id() + " -> " + next.id() + " (" + reason + ")");
        }

        this.state = next;
        this.mob.markTransition(reason);
    }

    /** Publishes the debug picture to the entity, and only when something about it really changed. */
    private void syncDebug()
    {
        String skill = this.active == null ? (this.guard.isActive() ? this.guard.name() : "none")
                : this.active.name();

        if (skill.equals(this.lastSyncedSkill) && this.encounter == this.lastSyncedEncounter
                && this.enchantedGappleUsedThisEncounter == this.lastSyncedGapple
                && this.guard.isActive() == this.lastSyncedThreat && this.pearlMode.equals(this.lastSyncedPearlMode))
        {
            return;
        }

        this.lastSyncedSkill = skill;
        this.lastSyncedEncounter = this.encounter;
        this.lastSyncedGapple = this.enchantedGappleUsedThisEncounter;
        this.lastSyncedThreat = this.guard.isActive();

        this.mob.syncDebugState(this.state, skill, this.encounter, this.enchantedGappleUsedThisEncounter,
                this.guard.isActive(), this.pearlMode);
    }

    /** One line per state change, skill start and stop, and per refused plan - never one per tick. */
    public void log(String message)
    {
        if (DEBUG || this.mob.level() instanceof net.minecraft.server.level.ServerLevel)
        {
            SpiderQueenHungryCraft.LOGGER.debug("[Sweatlord/{}] {}", this.mob.getStringUUID().substring(0, 8), message);
        }
    }

    /** @return every cooldown of every skill in one line, for the debug state. */
    public String describeCooldowns()
    {
        StringBuilder text = new StringBuilder();

        for (SweatlordSkill skill : this.allSkills())
        {
            if (text.length() > 0)
            {
                text.append(", ");
            }

            text.append(skill.describe(this.mob));
        }

        return text.toString();
    }

    /** @return the attacks and the bucket emergencies of this brain, in the order they are asked. */
    private List<SweatlordSkill> allSkills()
    {
        List<SweatlordSkill> all = new ArrayList<>(this.emergencies);
        all.addAll(this.skills);

        return all;
    }

    public SweatlordCombatState state()
    {
        return this.state;
    }

    @Nullable
    public SweatlordSkill activeSkill()
    {
        return this.active;
    }

    public boolean isEncounterActive()
    {
        return this.encounter;
    }

    public boolean hasUsedEnchantedGappleThisEncounter()
    {
        return this.enchantedGappleUsedThisEncounter;
    }

    /** Marks the enchanted golden apple of this encounter as used: it never happens twice per encounter. */
    public void markEnchantedGappleUsed()
    {
        this.enchantedGappleUsedThisEncounter = true;
        this.log("enchanted golden apple of this encounter used");
    }

    public boolean isRetreatAppleEaten()
    {
        return this.retreatAppleEaten;
    }

    public void markRetreatAppleEaten()
    {
        this.retreatAppleEaten = true;
    }

    public boolean isRetreatOver()
    {
        return this.retreatFinished;
    }

    public void markRetreatOver()
    {
        this.retreatFinished = true;
        this.log("retreat over, back into the fight");
    }

    /** The pearl mode of the brain right now, only for the debug state. */
    public void recordPearlMode(String mode)
    {
        this.pearlMode = mode;
    }

    public String pearlMode()
    {
        return this.pearlMode;
    }

    /** Remembers why the last pearl plan was refused, so the debug command can answer that question directly. */
    public void recordPearlRejection(String summary)
    {
        this.lastPearlRejection = summary;
    }

    public String lastPearlRejection()
    {
        return this.lastPearlRejection;
    }

    public InstantProjectileGuard instantGuard()
    {
        return this.guard;
    }

    /** @return the ranged rounds and the miss streak of this body, which the adaptation is decided from. */
    public RangedAttackRoundTracker rangedRounds()
    {
        return this.rangedRounds;
    }

    public SweatlordSteveEntity mob()
    {
        return this.mob;
    }

    /** @return the skill of that class from the list, so a test or a debug call can read its cooldown. */
    @Nullable
    public SweatlordSkill skillNamed(String name)
    {
        // Including the bucket emergencies: they are skills like any other, and a lookup that silently skips them would
        // report "no such skill" for the two actions this class owns most of.
        for (SweatlordSkill skill : this.allSkills())
        {
            if (skill.name().equals(name))
            {
                return skill;
            }
        }

        return null;
    }

    public List<SweatlordSkill> skills()
    {
        return java.util.Collections.unmodifiableList(this.skills);
    }
}
