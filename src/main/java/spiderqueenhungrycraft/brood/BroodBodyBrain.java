package spiderqueenhungrycraft.brood;

import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The fight of a brood body: the infested Steve's own combat brain (Infested Steve round, split round).
 *
 * <p>It is one small state machine that is ticked by {@link BroodBodyCombatGoal} and owns everything a fight needs: the
 * walking, the facing, the hand that holds the blade, the bow and the golden apple, the shield in the offhand and the
 * attacks themselves.
 *
 * <p><b>It is a copy, and that is the point of the split round.</b> Until then this body fought with the brain of the
 * human combatant, and the two systems are now kept apart: the mechanics of a fight are still recognisably the same code
 * on both sides - that is what "the behaviour may be similar" means - but this is the file a change for a brood body
 * belongs in, and the human fight answers only to its own owner. Whatever only a soldier of the queen does (obeying an
 * order over a fight, fighting only what the brood is at war with) stands on this side of the line.
 *
 * <p>Every distance and every duration of this round sits in the constants below, so the feel of the fight can be tuned
 * in one place.
 */
public class BroodBodyBrain
{
    /** Inside this band it shoots: closer than {@link #BOW_MIN_DISTANCE} the bow is put away. */
    public static final double BOW_MIN_DISTANCE = 6.0D;

    /** Further away than this it closes the gap first. */
    public static final double BOW_MAX_DISTANCE = 18.0D;

    /** Closer than this the shield comes up and it backs away. */
    public static final double TOO_CLOSE_DISTANCE = 4.0D;

    /** Within reach of the iron sword. */
    public static final double MELEE_DISTANCE = 2.4D;

    /** A hit at the end of a windup only lands inside this reach, so nothing is ever hit through a wall or from afar. */
    public static final double MELEE_HIT_REACH = MELEE_DISTANCE + 0.6D;

    /** How far it wants to pull back while retreating to eat. */
    public static final double RETREAT_TARGET_DISTANCE = 10.0D;

    /** Below this share of its health it pulls back and eats a golden apple. */
    public static final float LOW_HEALTH_RATIO = 0.35F;

    /** Below this share it eats immediately, without opening the distance first. */
    public static final float CRITICAL_HEALTH_RATIO = 0.20F;

    /** A full player style draw. The arrow only leaves the bow after these ticks. */
    public static final int BOW_DRAW_TICKS = 20;

    /** How long it waits after a shot before drawing again. */
    public static final int BOW_COOLDOWN_TICKS = 30;

    /** 1.0 is a fully drawn bow, which is what a player would shoot. */
    public static final float BOW_POWER = 1.0F;

    /** A small spread, so the bow is strong but not a laser. */
    public static final float BOW_INACCURACY = 1.0F;

    /** The shield stays up at least this long, so the guard is visible and the block window is real. */
    public static final int SHIELD_MIN_TICKS = 15;

    /** The shield is lowered again after this long, so it never guards forever and never stops attacking. */
    public static final int SHIELD_MAX_TICKS = 35;

    /** After a guard it keeps the shield down for a moment, so it is not raised and lowered every other tick. */
    public static final int SHIELD_COOLDOWN_TICKS = 30;

    /**
     * One sword swing: the arm swings first, the damage lands {@link #MELEE_HIT_TICK} ticks later, and the next swing
     * waits for the cooldown. That is the vanilla melee rhythm of a mob, just slow enough to be readable.
     */
    public static final int MELEE_WINDUP_TICKS = 6;

    /** The tick of the swing on which the damage is resolved, six ticks after the visible swing started. */
    public static final int MELEE_HIT_TICK = 6;

    /** One sword swing per second, close to the cooldown of a player with an iron sword. */
    public static final int MELEE_COOLDOWN_TICKS = 20;

    /**
     * A draw that can not be finished because the target is behind cover is given up after this many ticks instead of
     * standing there with the bow up forever.
     */
    public static final int BOW_MAX_HOLD_TICKS = 60;

    /**
     * A retreat that can not open the distance - a wall behind it, a corner - is given up after this many ticks and
     * it eats where it stands instead of walking into the wall forever.
     */
    public static final int RETREAT_MAX_TICKS = 60;

    /** Temporary diagnostic switch of this phase: one log line per state change, never one per tick. */
    public static final boolean DEBUG = true;

    /** How long it waits after a golden apple before the next one, so three apples are not one big heal. */
    public static final int HEAL_COOLDOWN_TICKS = 200;

    /** The vanilla use duration of a golden apple, which is how long one bite takes. */
    public static final int GOLDEN_APPLE_USE_TICKS = 32;

    /**
     * Anti flicker of this round: a state that matters is held for at least this many ticks before anything else is
     * allowed, so a drawn bow is not dropped after one tick and a shield is not raised and lowered every tick. The
     * exceptions are a nearly dead combatant and a path that can not be walked, see {@link #isLocked}.
     */
    public static final int DECISION_LOCK_TICKS = 10;

    /** How often a running state picks a new path. */
    public static final int REPATH_INTERVAL_TICKS = 10;

    /** Every this many ticks it swaps the strafing direction, so it does not orbit one way forever. */
    public static final int STRAFE_SWITCH_TICKS = 40;

    public static final double APPROACH_SPEED = 1.0D;
    public static final double MELEE_CHASE_SPEED = 1.05D;
    public static final double RETREAT_SPEED = 1.15D;
    public static final double STRAFE_SPEED = 0.8D;

    /**
     * How fast the body itself turns to keep the enemy in front, in degrees per tick. The head alone is not enough,
     * see {@link #faceTarget}.
     */
    public static final float TURN_TO_TARGET_SPEED = 30.0F;

    /**
     * The step of a guard who is being pushed: negative is backwards. The move control adds a quarter of the walking
     * speed to it, which is the slow, deliberate push back of somebody holding a shield up - that factor can not be
     * tuned, and it is exactly the feel this state wants.
     */
    public static final float GUARD_BACK_STEP = -1.0F;

    /** A slight lean of that step to one side, so a wall, a step or a fence does not pin a guard down. */
    public static final float GUARD_BACK_LEAN = 0.35F;

    /** The step of a guard who is not being pushed: it holds the line and shuffles sideways instead of walking in. */
    public static final float GUARD_SIDE_STEP = 0.6F;

    /**
     * How long a hit that it is not allowed to answer keeps its shield up. The vanilla revenge memory lasts about
     * the same time, so a reaction never outlives the vanilla one.
     */
    public static final int REACTION_TICKS = 60;

    private BroodBodyState state = BroodBodyState.IDLE;
    private int stateTicks;
    private int bowDrawTicks;
    private int bowCooldownTicks;
    private int meleeCooldownTicks;
    private int healCooldownTicks;
    private int strafeSign = 1;
    private int eatTicks;
    private int meleeWindupTicks;
    private int shieldCooldownTicks;
    private int stuckLogTicks;

    /** @return what the combatant is doing right now. */
    public BroodBodyState state()
    {
        return this.state;
    }

    /** @return how many ticks the current state has been running, used for debug output and repathing. */
    public int stateTicks()
    {
        return this.stateTicks;
    }

    /**
     * Runs one tick of the fight. The goal only calls this while a valid target exists.
     *
     * <p>The state is derived from the situation first and then executed, so the state can never disagree with what
     * a distance or a health value says.
     */
    public void tick(BroodBodyFighter mob, LivingEntity target)
    {
        this.tickCooldowns();

        BroodBodyState next = this.decide(mob, target);

        if (next != this.state && this.isLocked(mob))
        {
            next = this.state;
        }

        if (next != this.state)
        {
            this.enter(mob, next);
        }
        else
        {
            ++this.stateTicks;
        }

        this.run(mob, target);
    }

    /**
     * @return true while the current state is still protected by the decision lock: a state that matters is held for
     *         {@link #DECISION_LOCK_TICKS} ticks. A nearly dead combatant and a path that can not be walked break the
     *         lock, everything else has to wait its ten ticks.
     */
    private boolean isLocked(BroodBodyFighter mob)
    {
        if (this.stateTicks >= DECISION_LOCK_TICKS || !isImportantState(this.state))
        {
            return false;
        }

        if (mob.body().getHealth() / mob.body().getMaxHealth() <= CRITICAL_HEALTH_RATIO)
        {
            return false;
        }

        return !mob.body().getNavigation().isStuck();
    }

    /** @return true for the states a person would also finish before doing something else. */
    public static boolean isImportantState(BroodBodyState state)
    {
        return state == BroodBodyState.DRAW_BOW || state == BroodBodyState.SHIELD_GUARD
                || state == BroodBodyState.MELEE_ATTACK || state == BroodBodyState.RETREAT
                || state == BroodBodyState.EAT_GOLDEN_APPLE;
    }

    /**
     * The reaction to a hit by somebody it is not allowed to fight back against: the shield comes up and it gives
     * ground, but it never swings. That is what happens when a normal player, a villager or a monster hits it while
     * the monster hunting of this round is switched off.
     */
    public void tickReaction(BroodBodyFighter mob, LivingEntity attacker)
    {
        this.tickCooldowns();

        // A reaction never fakes a piece of gear: without a shield it steps aside and keeps facing the attacker
        // instead of raising an empty hand (Phase HF0C fix). A shield that an axe just smashed counts as no shield.
        BroodBodyState next = mob.canGuard() ? BroodBodyState.SHIELD_GUARD : BroodBodyState.REPOSITION;

        if (this.state != next)
        {
            this.enter(mob, next);
        }
        else
        {
            ++this.stateTicks;
        }

        if (next == BroodBodyState.SHIELD_GUARD)
        {
            this.runGuard(mob, attacker);
        }
        else
        {
            this.runReposition(mob, attacker);
        }
    }

    /** Called when the fight ends: nothing of the fight survives into the next one. */
    public void reset(BroodBodyFighter mob)
    {
        this.stopUsingItem(mob);
        mob.equipMeleeWeapon();
        mob.body().getNavigation().stop();
        this.state = BroodBodyState.IDLE;
        this.stateTicks = 0;
        this.bowDrawTicks = 0;
        this.bowCooldownTicks = 0;
        this.meleeCooldownTicks = 0;
        this.healCooldownTicks = 0;
        this.shieldCooldownTicks = 0;
        this.meleeWindupTicks = 0;
        this.eatTicks = 0;
        this.strafeSign = 1;
    }

    private void tickCooldowns()
    {
        if (this.bowCooldownTicks > 0)
        {
            --this.bowCooldownTicks;
        }

        if (this.meleeCooldownTicks > 0)
        {
            --this.meleeCooldownTicks;
        }

        if (this.healCooldownTicks > 0)
        {
            --this.healCooldownTicks;
        }

        if (this.shieldCooldownTicks > 0)
        {
            --this.shieldCooldownTicks;
        }

        if (this.stuckLogTicks > 0)
        {
            --this.stuckLogTicks;
        }
    }

    /** One diagnostic line per event, never one per tick, see {@link #DEBUG}. */
    private void log(BroodBodyFighter mob, String message)
    {
        if (DEBUG)
        {
            SpiderQueenHungryCraft.LOGGER.info("[BroodBody/{}] {}", mob.body().getStringUUID(), message);
        }
    }

    /**
     * Decides what to do next.
     *
     * <p>Order of this round: eating, then healing, then the fight distance band.
     */
    private BroodBodyState decide(BroodBodyFighter mob, LivingEntity target)
    {
        double distance = mob.body().distanceTo(target);

        // The bite is finished when the vanilla use logic of the item stops the use.
        if (this.state == BroodBodyState.EAT_GOLDEN_APPLE)
        {
            return mob.body().isUsingItem() ? BroodBodyState.EAT_GOLDEN_APPLE : this.combatState(mob, target, distance);
        }

        float health = mob.body().getHealth() / mob.body().getMaxHealth();

        if (health <= LOW_HEALTH_RATIO && this.healCooldownTicks <= 0 && mob.goldenAppleCount() > 0)
        {
            // At a critical health, or when it could not open the distance at all, it eats where it stands.
            if (health <= CRITICAL_HEALTH_RATIO || distance >= RETREAT_TARGET_DISTANCE
                    || (this.state == BroodBodyState.RETREAT && this.stateTicks >= RETREAT_MAX_TICKS))
            {
                return BroodBodyState.EAT_GOLDEN_APPLE;
            }

            return BroodBodyState.RETREAT;
        }

        return this.combatState(mob, target, distance);
    }

    /**
     * The distance bands of this round, from the melee reach up to the far approach.
     *
     * <p>Line of sight is part of every band: a target behind a wall is walked around, never shot at and never
     * guarded, which is what keeps it from standing in front of a wall with the bow up.
     */
    private BroodBodyState combatState(BroodBodyFighter mob, LivingEntity target, double distance)
    {
        boolean visible = mob.body().hasLineOfSight(target);

        // A running draw is committed to until it is finished, the target walked into it, cover broke the shot, or
        // the draw has been held for too long.
        if (this.state == BroodBodyState.DRAW_BOW && distance > TOO_CLOSE_DISTANCE && visible
                && this.stateTicks < BOW_MAX_HOLD_TICKS)
        {
            return BroodBodyState.DRAW_BOW;
        }

        if (distance <= MELEE_DISTANCE)
        {
            // A bowman without a blade does not close in, it steps back and keeps shooting.
            return mob.canFightInMelee() ? BroodBodyState.MELEE_ATTACK : BroodBodyState.REPOSITION;
        }

        if (distance < BOW_MIN_DISTANCE)
        {
            // Everything below the bow range: the shield while it is ready and has not just been used, otherwise the
            // blade. A bowman without a blade keeps backing off instead, and never parks in this band.
            boolean guardReady = mob.canGuard() && this.shieldCooldownTicks <= 0
                    && (this.state != BroodBodyState.SHIELD_GUARD || this.stateTicks < SHIELD_MAX_TICKS);

            if (guardReady)
            {
                return BroodBodyState.SHIELD_GUARD;
            }

            return mob.canFightInMelee() ? BroodBodyState.MELEE_ATTACK : BroodBodyState.REPOSITION;
        }

        if (distance > BOW_MAX_DISTANCE)
        {
            // Too far for anything: close the gap, whatever it is carrying.
            return BroodBodyState.APPROACH;
        }

        if (mob.hasBow() && visible)
        {
            // Only a body that really owns a bow shoots. While the bow cools down it keeps the distance instead of
            // walking into the target.
            return this.bowCooldownTicks <= 0 ? BroodBodyState.DRAW_BOW : BroodBodyState.REPOSITION;
        }

        // No bow in its hands, or no shot at all: it walks in and fights with whatever it has. This is what keeps a
        // melee body from standing across the field and staring at a monster (Phase HF0C fix).
        return BroodBodyState.APPROACH;
    }

    /** Prepares the hands and the timers of the state that is entered. */
    private void enter(BroodBodyFighter mob, BroodBodyState next)
    {
        // Central safety net of Phase HF0C: a state whose gear is simply not there is never entered at all. No body
        // ever raises an empty hand as a shield, draws a bow it does not own or bites into an apple it does not have.
        if (next == BroodBodyState.SHIELD_GUARD && !mob.canGuard())
        {
            next = BroodBodyState.APPROACH;
        }
        else if (next == BroodBodyState.DRAW_BOW && !mob.hasBow())
        {
            next = BroodBodyState.APPROACH;
        }
        else if (next == BroodBodyState.EAT_GOLDEN_APPLE && mob.goldenAppleCount() <= 0)
        {
            next = BroodBodyState.APPROACH;
        }

        this.leave(mob, this.state);
        this.state = next;
        this.stateTicks = 0;

        switch (next)
        {
            case DRAW_BOW:
                this.bowDrawTicks = 0;
                mob.equipBow();
                break;
            case MELEE_ATTACK:
            case SHIELD_GUARD:
            case RETREAT:
                this.meleeWindupTicks = 0;
                mob.equipMeleeWeapon();
                break;
            case REPOSITION:
                mob.equipBow();
                this.strafeSign = -this.strafeSign;
                break;
            case EAT_GOLDEN_APPLE:
                // The stock is checked before the state is entered, see the guard at the top of this method.
                this.eatTicks = 0;
                this.beginEating(mob);
                break;
            default:
                break;
        }

        this.log(mob, "state -> " + next.id());
    }

    /** Cleans up what the state that is left behind was holding. */
    private void leave(BroodBodyFighter mob, BroodBodyState previous)
    {
        switch (previous)
        {
            case DRAW_BOW:
            case SHIELD_GUARD:
            case RETREAT:
                this.stopUsingItem(mob);
                break;
            default:
                break;
        }

        if (previous == BroodBodyState.SHIELD_GUARD)
        {
            // A guard is always followed by a pause, so the shield is not raised and lowered every other tick.
            this.shieldCooldownTicks = SHIELD_COOLDOWN_TICKS;
        }
    }

    /** Executes one tick of the current state. */
    private void run(BroodBodyFighter mob, LivingEntity target)
    {
        switch (this.state)
        {
            case IDLE:
                mob.body().getNavigation().stop();
                break;
            case APPROACH:
                this.lookAt(mob, target);
                this.chase(mob, target, APPROACH_SPEED);
                break;
            case DRAW_BOW:
                this.runDrawBow(mob, target);
                break;
            case SHIELD_GUARD:
                this.runGuard(mob, target);
                break;
            case RETREAT:
                this.runRetreat(mob, target);
                break;
            case EAT_GOLDEN_APPLE:
                this.runEating(mob, target);
                break;
            case MELEE_ATTACK:
                this.runMelee(mob, target);
                break;
            case REPOSITION:
                this.runReposition(mob, target);
                break;
            default:
                // RELEASE_BOW lives exactly one tick: the arrow already left the bow when the state was entered.
                break;
        }
    }

    /**
     * Draws the bow like a player: the bow goes into the hand, the arm is raised by the vanilla model, the draw is
     * held for {@link #BOW_DRAW_TICKS} ticks and only then is the arrow released.
     */
    private void runDrawBow(BroodBodyFighter mob, LivingEntity target)
    {
        if (!mob.hasBow())
        {
            // Nothing to shoot with at all: back to the walk, the next decision picks a reachable state.
            this.enter(mob, BroodBodyState.APPROACH);
            return;
        }

        // No line of sight means no shot: it walks instead of aiming at a wall (Phase HF0B, part G).
        if (!mob.body().hasLineOfSight(target))
        {
            this.enter(mob, BroodBodyState.APPROACH);
            return;
        }

        mob.body().getNavigation().stop();
        this.lookAt(mob, target);

        if (!mob.body().isUsingItem() || mob.body().getUsedItemHand() != InteractionHand.MAIN_HAND || !mob.body().getUseItem().is(Items.BOW))
        {
            // Starting the use again every tick would restart the draw forever, so it is only started once.
            if (!mob.body().isUsingItem())
            {
                mob.equipBow();
                mob.body().startUsingItem(InteractionHand.MAIN_HAND);
                this.bowDrawTicks = 0;
            }

            return;
        }

        ++this.bowDrawTicks;

        if (this.bowDrawTicks < BOW_DRAW_TICKS)
        {
            return;
        }

        this.releaseArrow(mob, target);
        mob.body().stopUsingItem();
        this.bowCooldownTicks = BOW_COOLDOWN_TICKS;
        this.log(mob, "bow released at " + Math.round(mob.body().distanceTo(target)) + " blocks");
        this.enter(mob, BroodBodyState.RELEASE_BOW);
    }

    /**
     * Fires a real vanilla arrow. Nothing here deals damage directly: the arrow flies, lands and hurts by itself.
     *
     * <p>No arrow is ever consumed, which is the test convenience of this round, and the arrow is not pickable up
     * again, because it is not loot from a mob but the ammunition of an infinite quiver.
     */
    private void releaseArrow(BroodBodyFighter mob, LivingEntity target)
    {
        AbstractArrow arrow = mob.newArrow();
        arrow.setOwner(mob.asLivingEntity());

        Vec3 eye = new Vec3(mob.body().getX(), mob.body().getEyeY(), mob.body().getZ());
        Vec3 aim = new Vec3(target.getX(), target.getY() + (double) target.getBbHeight() * 0.5D, target.getZ());
        Vec3 direction = aim.subtract(eye);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);

        // A player lifts the crosshair a little over a distant target, this is the same idea in one line.
        double lift = horizontal * 0.05D;

        arrow.shoot(direction.x, direction.y + lift, direction.z, BOW_POWER * 3.0F, BOW_INACCURACY);
        // A fully drawn bow arrow crits in vanilla, and so does this one.
        arrow.setCritArrow(true);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

        mob.body().level().addFreshEntity(arrow);
    }

    /**
     * The shield is up, it faces the target with the whole body and gives ground.
     *
     * <p>The block itself is left to the game: a mob that is using a shield in the offhand blocks, loses shield
     * durability on a block and only ever blocks what comes at its front, so this needs no damage event of its own.
     * What this state does own is that front, and that is why it moves without a path - see {@link #faceTarget} and
     * {@link #shieldStep}. Without them a guard walks away along a path, turns its body to the way it walks and blocks
     * nothing at all ("防了个寂寞").
     */
    private void runGuard(BroodBodyFighter mob, LivingEntity target)
    {
        if (!mob.canGuard())
        {
            // An axe smashed the shield it was holding (see attack/ShieldBreak): there is no guard without a shield,
            // so the state ends right here and the next decision picks a fight it can still win. REPOSITION is the
            // safe way out of both a fight and a mere reaction: it swings at nobody and keeps facing the attacker.
            this.enter(mob, BroodBodyState.REPOSITION);
            return;
        }

        this.faceTarget(mob, target);
        mob.equipMeleeWeapon();

        if (!mob.body().isUsingItem())
        {
            mob.body().startUsingItem(InteractionHand.OFF_HAND);
        }

        if (this.stateTicks % STRAFE_SWITCH_TICKS == 0)
        {
            this.strafeSign = -this.strafeSign;
        }

        if (this.stateTicks >= SHIELD_MAX_TICKS)
        {
            // The guard has served its purpose, the distance bands decide what comes next.
            return;
        }

        if (mob.body().distanceTo(target) < TOO_CLOSE_DISTANCE + 2.0D)
        {
            // Too close for comfort: it steps back with the shield up, leaning a little to one side so a wall, a low
            // step or a fence does not pin it down.
            this.shieldStep(mob, GUARD_BACK_STEP, GUARD_BACK_LEAN * (float) this.strafeSign);
        }
        else
        {
            // At a comfortable distance it holds the line and shuffles sideways instead of walking into the enemy.
            this.shieldStep(mob, 0.0F, GUARD_SIDE_STEP * (float) this.strafeSign);
        }
    }

    /**
     * Pulling back to about ten blocks with the shield up, so a melee attacker has to chase it.
     *
     * <p>With a shield that retreat is a backwards walk, not a run: the whole point of the raised shield is that its
     * front still faces the attacker, and turning around would throw that away (see {@link #faceTarget}). It is
     * therefore slower than a run, which is why the decision gives the retreat up after {@link #RETREAT_MAX_TICKS} and
     * eats where it stands - with the shield up, which is what a body with a shield should do.
     */
    private void runRetreat(BroodBodyFighter mob, LivingEntity target)
    {
        mob.equipMeleeWeapon();

        if (mob.canGuard())
        {
            this.faceTarget(mob, target);

            if (!mob.body().isUsingItem())
            {
                mob.body().startUsingItem(InteractionHand.OFF_HAND);
            }

            this.shieldStep(mob, GUARD_BACK_STEP, GUARD_BACK_LEAN * (float) this.strafeSign);
            return;
        }

        // No shield (or a smashed one): nothing has to stay in front of it, and turning around is the fastest way
        // to open the distance.
        this.lookAt(mob, target);
        this.backOff(mob, target, RETREAT_SPEED);
    }

    /**
     * The iron sword swings on a cooldown, the shield is put down while it attacks.
     *
     * <p>A swing is two parts: the visible arm swing starts right away, the damage lands {@link #MELEE_HIT_TICK}
     * ticks later and only if the target is still in reach and in sight. That is the readable melee rhythm of a mob,
     * and it is what makes it look like it is actually hitting something.
     */
    private void runMelee(BroodBodyFighter mob, LivingEntity target)
    {
        this.lookAt(mob, target);
        mob.equipMeleeWeapon();

        if (mob.body().isUsingItem() && mob.body().getUseItem().is(Items.SHIELD))
        {
            mob.body().stopUsingItem();
        }

        // A swing that is running is always finished, the damage of it sits on the windup.
        if (this.meleeWindupTicks > 0)
        {
            ++this.meleeWindupTicks;

            if (this.meleeWindupTicks < MELEE_HIT_TICK)
            {
                return;
            }

            this.meleeWindupTicks = 0;
            this.resolveMeleeHit(mob, target);
            return;
        }

        // Out of reach: close in, and keep the path alive so a moving target is actually followed.
        if (mob.body().distanceTo(target) > MELEE_DISTANCE)
        {
            this.chase(mob, target, MELEE_CHASE_SPEED);
            return;
        }

        mob.body().getNavigation().stop();

        if (this.meleeCooldownTicks > 0)
        {
            return;
        }

        // The visible swing goes out first, the damage follows on the windup tick.
        mob.swingMainHand();
        this.meleeWindupTicks = 1;
        this.meleeCooldownTicks = MELEE_COOLDOWN_TICKS;
        this.log(mob, "melee swing at " + target.getName().getString());
    }

    /** The real hit, through the vanilla damage pipeline of the game, never as a client side effect. */
    private void resolveMeleeHit(BroodBodyFighter mob, LivingEntity target)
    {
        if (target.isRemoved() || !target.isAlive())
        {
            return;
        }

        if (mob.body().distanceTo(target) > MELEE_HIT_REACH || !mob.body().hasLineOfSight(target))
        {
            // The target stepped out of the swing, which is exactly what a real swing should do.
            return;
        }

        mob.performMeleeAttack(target);
    }

    /**
     * The middle band while the bow cools down: it holds the line, keeps the bow ready and strafes sideways, so a
     * player cannot simply walk it down in a straight line.
     */
    private void runReposition(BroodBodyFighter mob, LivingEntity target)
    {
        this.lookAt(mob, target);
        mob.equipBow();

        if (this.stateTicks % STRAFE_SWITCH_TICKS == 0)
        {
            this.strafeSign = -this.strafeSign;
        }

        if (!mob.canFightInMelee() && mob.body().distanceTo(target) < BOW_MIN_DISTANCE)
        {
            // No blade and the target is inside its bow range: open the distance instead of dancing around it.
            this.backOff(mob, target, STRAFE_SPEED);
            return;
        }

        this.strafe(mob, target, STRAFE_SPEED);
    }

    /** Eating happens standing still: the vanilla item use plays the eat and applies the effects. */
    private void runEating(BroodBodyFighter mob, LivingEntity target)
    {
        mob.body().getNavigation().stop();
        this.lookAt(mob, target);
        this.finishEating(mob);
    }

    /**
     * Closes a bite the vanilla way.
     *
     * <p>The game's own item use runs for {@link #GOLDEN_APPLE_USE_TICKS} ticks and does everything by itself: the
     * bite animation, the vanilla golden apple effects and the shrinking of the apple. A mob is not a player, so if
     * that use does not close on its own, the very same vanilla call is made here - {@code finishUsingItem} on the
     * stack - which applies exactly the same effects and consumes exactly the same apple. No health is ever set by
     * hand, there is no {@code setHealth} anywhere in this class.
     */
    private void finishEating(BroodBodyFighter mob)
    {
        ++this.eatTicks;

        if (mob.body().isUsingItem())
        {
            if (this.eatTicks < GOLDEN_APPLE_USE_TICKS)
            {
                return;
            }

            mob.body().stopUsingItem();
        }

        ItemStack apple = mob.body().getMainHandItem();

        if (apple.is(Items.GOLDEN_APPLE))
        {
            // The 1.20.1 equivalent of the end of a vanilla bite: effects, sound and the shrinking of the stack.
            ItemStack rest = apple.finishUsingItem(mob.body().level(), mob.asLivingEntity());
            mob.body().setItemSlot(EquipmentSlot.MAINHAND, rest);
        }

        mob.equipMeleeWeapon();
    }

    /**
     * Finishes a bite that is already running even when the fight ended in between, so an interrupted fight can not
     * leave the combatant stuck in an eating state that nothing ticks any more.
     */
    public void tickEatingOnly(BroodBodyFighter mob)
    {
        this.tickCooldowns();

        if (this.state != BroodBodyState.EAT_GOLDEN_APPLE)
        {
            this.enter(mob, BroodBodyState.EAT_GOLDEN_APPLE);
        }
        else
        {
            ++this.stateTicks;
        }

        mob.body().getNavigation().stop();
        this.finishEating(mob);
    }

    /** Takes one golden apple out of the stock, puts it into the hand and starts the bite. */
    private boolean beginEating(BroodBodyFighter mob)
    {
        if (!mob.consumeGoldenApple())
        {
            // Nothing left to eat: a body without medicine never pretends to heal.
            return false;
        }

        mob.body().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE));
        mob.body().startUsingItem(InteractionHand.MAIN_HAND);
        this.healCooldownTicks = HEAL_COOLDOWN_TICKS;
        return true;
    }

    private void stopUsingItem(BroodBodyFighter mob)
    {
        if (mob.body().isUsingItem())
        {
            mob.body().stopUsingItem();
        }
    }

    private void lookAt(BroodBodyFighter mob, LivingEntity target)
    {
        mob.body().getLookControl().setLookAt(target, 30.0F, 30.0F);
    }

    /**
     * Turns the whole body towards the target, not only the head.
     *
     * <p>This is the difference between a shield that works and a shield that does not. A block is judged by the view
     * vector of the body ({@code LivingEntity#isDamageSourceBlocked} compares it with the direction the hit comes
     * from), and that vector is the head yaw - while the engine keeps the head of a walking mob no further than
     * {@code getMaxHeadYRot()} (75 degrees) away from the body yaw ({@code BodyRotationControl}). A body that follows
     * a path away from its enemy therefore turns its body towards the way it walks and can look back over its shoulder
     * at best: the hit then counts as coming from the side, the shield blocks nothing, and the axe always lands. That
     * is the "防了个寂寞" this round fixes - whoever gives ground with a shield up has to turn with the body and step
     * backwards, which is what a person with a shield does as well.
     *
     * <p>The head is still asked to look at the target on top of this, so the pitch of the shield is right too.
     */
    private void faceTarget(BroodBodyFighter mob, LivingEntity target)
    {
        this.lookAt(mob, target);

        Vec3 toTarget = target.position().subtract(mob.body().position());

        if (toTarget.horizontalDistanceSqr() < 1.0E-4D)
        {
            return;
        }

        LivingEntity body = mob.asLivingEntity();
        float wanted = (float) (Mth.atan2(toTarget.z, toTarget.x) * (180.0D / Math.PI)) - 90.0F;
        body.setYRot(Mth.rotateIfNecessary(body.getYRot(), wanted, TURN_TO_TARGET_SPEED));
        body.setYHeadRot(body.getYRot());
    }

    /**
     * Steps with the front towards the target, without ever turning the body away.
     *
     * <p>This is the one movement of the fight that may not use the navigation: a path turns the body towards wherever
     * it leads, see {@link #faceTarget}. The move control in strafe mode is the tool for the opposite - it walks
     * relative to the way the body already faces and never turns the body itself - and it does so at a quarter of the
     * walking speed, which is the slow, deliberate push back of somebody holding a shield up. It also refuses a step
     * that is not walkable, so a guard never walks off a cliff or into lava while giving ground.
     *
     * @param forwards 1.0 is a full step forward, -1.0 a full step backwards, 0.0 a pure sidestep.
     * @param side how far the step leans to one side, the sign picks the side.
     */
    private void shieldStep(BroodBodyFighter mob, float forwards, float side)
    {
        // A path of an earlier state writes to the move control as well and would overrule the step below.
        mob.body().getNavigation().stop();
        mob.body().getMoveControl().strafe(forwards, side);
    }

    private boolean shouldRepath()
    {
        return this.stateTicks % REPATH_INTERVAL_TICKS == 0;
    }

    private void walkTowards(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        mob.body().getNavigation().moveTo(target, speed);
    }

    /**
     * Walks towards the target and keeps the path alive (Phase HF0B, part D): a fresh path is requested when the old
     * one ran out, when the repath interval has passed, and whenever it is stuck against something. Without this it
     * would stand where the target used to be instead of walking after it.
     */
    private void chase(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        PathNavigation navigation = mob.body().getNavigation();

        if (navigation.isStuck() || navigation.isDone() || this.shouldRepath())
        {
            this.repath(mob, target, speed);
        }
    }

    /** The same for backing off, so a retreat through a doorway is not abandoned after the first path. */
    private void backOff(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        PathNavigation navigation = mob.body().getNavigation();

        if (navigation.isStuck() || navigation.isDone() || this.shouldRepath())
        {
            this.walkAway(mob, target, speed);
        }
    }

    /** The same for sidestepping, so the strafe never becomes standing still. */
    private void strafe(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        PathNavigation navigation = mob.body().getNavigation();

        if (navigation.isStuck() || navigation.isDone() || this.shouldRepath())
        {
            this.walkSideways(mob, target, speed);
        }
    }

    /** Requests one path and reports the case in which no path exists at all, throttled to two seconds. */
    private void repath(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        boolean found = mob.body().getNavigation().moveTo(target, speed);

        if (!found && this.stuckLogTicks <= 0)
        {
            this.stuckLogTicks = 40;
            this.log(mob, "no path to " + target.getName().getString() + ", retrying");
        }
    }

    /** Walks away from the target, about {@link #RETREAT_TARGET_DISTANCE} blocks. */
    private void walkAway(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        Vec3 away = mob.body().position().subtract(target.position());

        if (away.lengthSqr() < 1.0E-4D)
        {
            away = new Vec3(mob.body().getRandom().nextDouble() - 0.5D, 0.0D, mob.body().getRandom().nextDouble() - 0.5D);
        }

        Vec3 point = mob.body().position().add(away.normalize().scale(RETREAT_TARGET_DISTANCE));
        mob.body().getNavigation().moveTo(point.x, point.y, point.z, speed);
    }

    /** Steps sideways around the target, the direction flips every {@link #STRAFE_SWITCH_TICKS} ticks. */
    private void walkSideways(BroodBodyFighter mob, LivingEntity target, double speed)
    {
        Vec3 toTarget = target.position().subtract(mob.body().position());
        Vec3 side = new Vec3(-toTarget.z, 0.0D, toTarget.x);

        if (side.lengthSqr() < 1.0E-4D)
        {
            return;
        }

        Vec3 point = mob.body().position().add(side.normalize().scale(3.0D * (double) this.strafeSign));
        mob.body().getNavigation().moveTo(point.x, point.y, point.z, speed);
    }
}
