package spiderqueenhungrycraft.sweatlord.skill;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.attack.ShieldBreak;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.CombatContext;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatBrain;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatState;
import spiderqueenhungrycraft.sweatlord.SweatlordSkill;

/**
 * The melee half of the Sweatlord kit (Sweatlord round): the sword with player-like crits, and the axe against a
 * shield that is really up.
 *
 * <p>Both of them are real swings on the real game path ({@link PlayerLikeActions#meleeAttack}), and both of them are
 * gated by the same three questions the reference {@code TriggerBot} asks: reach, line of sight and the attack
 * cooldown. What is special about the axe is the rule it follows:
 *
 * <blockquote>
 * A shield that is detected is only ever the reason to <b>choose the axe for the next real melee hit</b>. Nothing is
 * disabled from a distance, nothing is disabled by a detection, and the shield is only smashed after a real axe hit
 * has really landed on a body that really blocked it - exactly the way the vanilla game does it for a player.
 * </blockquote>
 */
public final class MeleeSkills
{
    private MeleeSkills()
    {
    }

    /** The sword: chase into reach, wait for the cooldown, swing, and jump into a crit when that is safe. */
    public static final class SwordMeleeSkill extends SweatlordSkill
    {
        /** The vanilla attack cooldown of a player, in ticks. */
        public static final int ATTACK_COOLDOWN_TICKS = 12;

        /** How close the target has to be before a jump attack is worth it. */
        private static final double JUMP_ATTACK_DISTANCE = 3.5D;

        private int swingCooldown;
        private int windup;
        private boolean critSwing;

        @Override
        public String name()
        {
            return "sword_melee";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.MELEE_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_SWORD;
        }

        @Override
        public int cooldownTicks()
        {
            return 1;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            return context.distance() <= mob.attackReach() + 2.5D;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.swingCooldown = 0;
            this.windup = 0;
            this.critSwing = false;
            mob.loadout().equipSword();
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            LivingEntity target = context.target();

            if (target == null)
            {
                return;
            }

            mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (this.swingCooldown > 0)
            {
                --this.swingCooldown;
            }

            if (this.windup > 0)
            {
                // The damage of a swing lands on the windup tick, exactly like the ordinary human's brain does it, so
                // a player can read the swing instead of being hit by an invisible instant.
                if (--this.windup == 0 && PlayerLikeActions.canMelee(mob, target))
                {
                    PlayerLikeActions.meleeAttack(mob, target);
                }

                return;
            }

            if (!PlayerLikeActions.canMelee(mob, target))
            {
                mob.getNavigation().moveTo(target, 1.2D);
                return;
            }

            PlayerLikeActions.brake(mob);

            if (this.swingCooldown > 0)
            {
                return;
            }

            if (this.wantsJumpAttack(mob, context))
            {
                mob.getJumpControl().jump();
                this.critSwing = true;
            }

            this.windup = 2;
            this.swingCooldown = ATTACK_COOLDOWN_TICKS;
        }

        /**
         * @return true when a jump attack is safe right now, which is what the round asks for: the head has room, the
         *         ground ahead is not a cliff, the body stands on solid ground and the target is still close enough to
         *         be hit in the falling phase.
         */
        private boolean wantsJumpAttack(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!mob.onGround() || context.badGroundAhead() || context.distance() > JUMP_ATTACK_DISTANCE)
            {
                return false;
            }

            // The head has to be free, or the jump is a bonk instead of a crit.
            return mob.level().noCollision(mob, mob.getBoundingBox().move(0.0D, 1.2D, 0.0D));
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            return context.distance() <= mob.attackReach() + 4.0D;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            this.windup = 0;
            this.critSwing = false;
        }
    }

    /**
     * The axe against a raised shield: the one melee skill with a condition.
     *
     * <p>What it may do: when the enemy really holds a shield up <b>and</b> is inside the real melee reach <b>and</b>
     * can be seen <b>and</b> the cooldown is ready, switch to the diamond axe and land one real swing. If that swing
     * really hits a body that really blocked it, the shield is smashed with the vanilla rule of this mod
     * ({@link ShieldBreak#smash}), which is the same three-and-a-bit seconds a player loses.
     *
     * <p>What it may never do: disarm a shield from a distance, close a shield that was only detected, or call
     * {@code stopUsingItem} on the enemy during a decision. A detected shield is only ever the reason to bring the
     * axe; everything else happens after a real hit.
     */
    public static final class AxeShieldBreakSkill extends SweatlordSkill
    {
        private static final int SWING_WINDUP_TICKS = 2;

        private int windup;

        @Override
        public String name()
        {
            return "axe_shield_break";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.MELEE_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_SHIELD_BREAK;
        }

        @Override
        public int cooldownTicks()
        {
            return 24;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.targetShieldUp() || !context.hasTarget() || !context.lineOfSight())
            {
                return false;
            }

            // The axe is only ever chosen for a hit that can really land: no reach, no axe.
            return PlayerLikeActions.canMelee(mob, context.target());
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.windup = SWING_WINDUP_TICKS;
            PlayerLikeActions.brake(mob);
            mob.loadout().equipAxe();
            mob.brain().log("axe shield break: the target really holds a shield up in reach");
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            LivingEntity target = context.target();

            if (target == null)
            {
                return;
            }

            PlayerLikeActions.faceTowards(mob, target.position());

            if (this.windup > 0 && --this.windup == 0 && PlayerLikeActions.canMelee(mob, target))
            {
                boolean blocked = CombatContext.isReallyBlocking(target) && target.isDamageSourceBlocked(
                        mob.damageSources().mobAttack(mob));

                PlayerLikeActions.meleeAttack(mob, target);

                // The smash only happens after the real hit really landed on a body that really blocked it - the
                // vanilla rule of this mod, and the same window a player gets.
                if (blocked && target.isAlive())
                {
                    ShieldBreak.smash(target, mob);
                    mob.brain().log("axe shield break: the shield of the target was smashed");
                }
            }
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            return this.windup > 0 && context.hasTarget();
        }

        /**
         * The one action of this round that may interrupt a running one: the sword.
         *
         * <p>A sword combo holds the body for as long as the enemy stays inside seven blocks, and a shield can be
         * raised in the middle of that combo. Without this rule the axe would only ever be chosen while nothing runs -
         * which in a melee fight is never, so the read of the shield existed and never happened.
         */
        @Override
        public boolean mayTakeOver(SweatlordSkill running)
        {
            return running instanceof SwordMeleeSkill;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            this.windup = 0;
            mob.loadout().equipSword();
        }
    }
}
