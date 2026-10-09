package spiderqueenhungrycraft.sweatlord.skill;

import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.CombatContext;
import spiderqueenhungrycraft.sweatlord.MovingTargetBallistics;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;
import spiderqueenhungrycraft.sweatlord.ProjectileBallistics;
import spiderqueenhungrycraft.sweatlord.RangedAttackRoundTracker;
import spiderqueenhungrycraft.sweatlord.RangedCombatLocomotion;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatBrain;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatState;
import spiderqueenhungrycraft.sweatlord.SweatlordSkill;

/**
 * The ranged half of the Sweatlord kit (Sweatlord round): bow, trident and the egg burst.
 *
 * <p>All three of them throw real vanilla projectiles from a real hand, and all three of them are gated by the bow
 * band of {@link CombatContext} - a bow is never a melee weapon, so none of them starts inside
 * {@link CombatContext#BOW_MIN_DISTANCE}. Each one carries its own cooldown, so nothing here can ever become a
 * machine gun.
 */
public final class RangedSkills
{
    private RangedSkills()
    {
    }

    /**
     * The bow: a real draw with the real use state, and a real arrow with a basic lead prediction.
     *
     * <p>The draw is the same 20 ticks a player needs, and the shot only leaves when the draw has really been held -
     * the arrow is spawned by this skill and never by the game, because a mob's bow does not release on its own.
     */
    public static final class BowAttackSkill extends SweatlordSkill
    {
        /** The spread of an arrow of this mod, the same the ordinary human's bow uses. */
        private static final float INACCURACY = 1.0F;

        private int drawTicks;
        private boolean released;
        private double aimLead;

        /** The ranged round this draw belongs to: one arrow, one round (see {@link RangedAttackRoundTracker}). */
        private int round;
        private final RangedCombatLocomotion locomotion = new RangedCombatLocomotion();

        @Override
        public String name()
        {
            return "bow_attack";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.RANGED_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_BOW;
        }

        @Override
        public int cooldownTicks()
        {
            return 30;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            // A bow is never used as a club: inside the minimum distance he closes in instead.
            return context.distance() >= CombatContext.BOW_MIN_DISTANCE;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.drawTicks = 0;
            this.released = false;
            this.aimLead = 0.0D;
            this.round = mob.brain().rangedRounds().beginRound(context.target(), RangedAttackRoundTracker.Kind.BOW, 1,
                    mob.level().getGameTime());
            PlayerLikeActions.brake(mob);
            mob.loadout().equipBow();
            PlayerLikeActions.startUsingMainHand(mob);
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            LivingEntity target = context.target();

            if (target == null)
            {
                return;
            }

            // He draws while he walks: a player does not stop moving to shoot, and the shot is solved from the moving
            // body on the very tick it leaves (see MovingTargetBallistics).
            this.locomotion.tick(mob, context, RangedCombatLocomotion.Band.BOW);

            if (!mob.isDrawingBow())
            {
                // The use state was lost (a shield, an interrupter): start the draw again from zero rather than
                // shooting without a draw.
                mob.loadout().equipBow();
                PlayerLikeActions.startUsingMainHand(mob);
                this.drawTicks = 0;
                return;
            }

            ++this.drawTicks;

            if (this.drawTicks < PlayerLikeActions.BOW_DRAW_TICKS)
            {
                return;
            }

            this.aimLead = aimLeadBlocks(mob, target);
            mob.brain().rangedRounds().registerProjectile(this.round, PlayerLikeActions.shootArrow(mob, target));
            PlayerLikeActions.stopUsing(mob);
            this.released = true;
        }

        /**
         * @return how far ahead of the body the solved intercept lies, for the debug output and the tests: a shot at a
         *         strafing enemy has to be aimed where it will be, and this is that distance in blocks.
         */
        private static double aimLeadBlocks(SweatlordSteveEntity mob, LivingEntity target)
        {
            MovingTargetBallistics.Solution solution = MovingTargetBallistics.solve(ProjectileBallistics.eyeOf(mob),
                    target.getBoundingBox().getCenter(), target.getDeltaMovement(), target.getBoundingBox(),
                    MovingTargetBallistics.ProjectileProfile.BOW);

            return solution == null ? 0.0D
                    : solution.aimPoint().distanceTo(target.getBoundingBox().getCenter());
        }

        /** @return the movement of this skill, for the debug output and the ranged locomotion test. */
        public RangedCombatLocomotion locomotion()
        {
            return this.locomotion;
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            if (this.released)
            {
                return false;
            }

            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            return context.distance() >= CombatContext.BOW_MIN_DISTANCE * 0.6D;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            PlayerLikeActions.stopUsing(mob);
            mob.loadout().equipCombatGrip();
        }

        /** @return how far ahead of the target the last shot was aimed, for the debug output. */
        public double lastLeadBlocks()
        {
            return this.aimLead;
        }

        @Override
        public boolean holdsBody(SweatlordSteveEntity mob)
        {
            // A draw in progress is finished: an interrupted draw is a shot that never happened.
            return mob.isDrawingBow();
        }
    }

    /** The trident: a real thrown trident, on its own cooldown, with the gear restored afterwards. */
    public static final class TridentAttackSkill extends SweatlordSkill
    {
        private static final int THROW_WINDUP_TICKS = 6;
        private static final int HOLD_TICKS = 20;

        private int ticks;
        private boolean thrown;

        /** The ranged round of this throw: one trident, one round. */
        private int round;
        private final RangedCombatLocomotion locomotion = new RangedCombatLocomotion();

        @Override
        public String name()
        {
            return "trident_attack";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.RANGED_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_TRIDENT;
        }

        @Override
        public int cooldownTicks()
        {
            return 90;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            return context.distance() >= CombatContext.BOW_MIN_DISTANCE;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.ticks = 0;
            this.thrown = false;
            this.round = mob.brain().rangedRounds().beginRound(context.target(), RangedAttackRoundTracker.Kind.TRIDENT, 1,
                    mob.level().getGameTime());
            PlayerLikeActions.brake(mob);
            mob.loadout().equipTrident();
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            LivingEntity target = context.target();

            if (target == null)
            {
                return;
            }

            ++this.ticks;
            this.locomotion.tick(mob, context, RangedCombatLocomotion.Band.TRIDENT);

            if (this.thrown || this.ticks < THROW_WINDUP_TICKS)
            {
                return;
            }

            mob.brain().rangedRounds().registerProjectile(this.round, PlayerLikeActions.throwTrident(mob, target));
            mob.swingMainHand();
            this.thrown = true;
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            return this.ticks < HOLD_TICKS && context.hasTarget();
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            mob.loadout().equipCombatGrip();
        }

        /** @return the movement of this skill, for the debug output and the ranged locomotion test. */
        public RangedCombatLocomotion locomotion()
        {
            return this.locomotion;
        }
    }

    /**
     * The egg burst: five to seven eggs, one every other tick.
     *
     * <p>A burst and never a machine gun: the eggs leave the hand spread over ten to fourteen ticks, they carry no
     * spread of their own and each one is thrown on its own solved intercept, and the skill then goes on a long
     * cooldown. Nothing here throws seven eggs inside one tick.
     */
    public static final class EggBurstSkill extends SweatlordSkill
    {
        /** The smallest and largest burst of this round. */
        public static final int MIN_EGGS = 5;
        public static final int MAX_EGGS = 7;

        /** How many ticks between two eggs of one burst. */
        private static final int TICKS_BETWEEN_EGGS = 2;

        /** The movement of this skill: an egg wants to be thrown from the short band of the kit. */
        private final RangedCombatLocomotion locomotion = new RangedCombatLocomotion();

        private int eggs;
        private int thrown;
        private int ticks;

        /**
         * The ranged round of this burst: <b>one burst is one round</b>, however many eggs it throws, so a single egg
         * that reached the enemy makes the whole burst a hit (see {@link RangedAttackRoundTracker}).
         */
        private int round;

        @Override
        public String name()
        {
            return "egg_burst";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.RANGED_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_EGG;
        }

        @Override
        public int cooldownTicks()
        {
            return 120;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            return context.distance() >= CombatContext.BOW_MIN_DISTANCE;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.eggs = MIN_EGGS + mob.getRandom().nextInt(MAX_EGGS - MIN_EGGS + 1);
            this.thrown = 0;
            this.ticks = 0;
            this.round = mob.brain().rangedRounds().beginRound(context.target(), RangedAttackRoundTracker.Kind.EGG_BURST,
                    this.eggs, mob.level().getGameTime());
            PlayerLikeActions.brake(mob);
            mob.loadout().equipEggs();
            mob.brain().log("egg burst: " + this.eggs + " eggs");
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            LivingEntity target = context.target();

            if (target == null)
            {
                return;
            }

            ++this.ticks;
            this.locomotion.tick(mob, context, RangedCombatLocomotion.Band.EGG);

            if (this.ticks % TICKS_BETWEEN_EGGS != 0 || this.thrown >= this.eggs)
            {
                return;
            }

            mob.brain().rangedRounds().registerProjectile(this.round, PlayerLikeActions.throwEgg(mob, target));
            ++this.thrown;
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            return this.thrown < this.eggs && context.hasTarget();
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            mob.loadout().equipCombatGrip();
        }

        /** @return how many eggs of the last burst were really thrown, for the debug output and the tests. */
        public int thrownEggs()
        {
            return this.thrown;
        }

        /** @return the movement of this skill, for the debug output and the ranged locomotion test. */
        public RangedCombatLocomotion locomotion()
        {
            return this.locomotion;
        }
    }
}
