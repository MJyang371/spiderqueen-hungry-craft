package spiderqueenhungrycraft.sweatlord.skill;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.CombatContext;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;
import spiderqueenhungrycraft.sweatlord.SafePearlPlanner;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatBrain;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatState;
import spiderqueenhungrycraft.sweatlord.SweatlordSkill;
import spiderqueenhungrycraft.sweatlord.VirtualLoadout;

/**
 * The support half of the Sweatlord kit (Sweatlord round): the opening god apple, the pearl jump and the retreat.
 *
 * <p>All three of them are ordinary {@link SweatlordSkill}s with the same contract as an attack: a priority, a
 * cooldown, a real action and an end. They are grouped in one file because they share the pearl planner and the
 * encounter bookkeeping of the brain, not because they are one action.
 */
public final class SupportSkills
{
    private SupportSkills()
    {
    }

    /**
     * The opening play of an encounter: one real enchanted golden apple, eaten with the vanilla item use.
     *
     * <p>It runs at most once per encounter (the brain owns that flag), it is skipped entirely while a golden apple
     * buff is still on him, and it never fakes the effect with {@code addEffect}: the apple goes into the main hand,
     * the use state starts, and the buffs are the game's own reward for finishing the bite.
     */
    public static final class EnchantedGapplePrebuffSkill extends SweatlordSkill
    {
        /** How long the bite may take before the skill gives up (the vanilla eat is 32 ticks). */
        private static final int BITE_TIMEOUT_TICKS = 80;

        private final SweatlordCombatBrain brain;
        private int biteTicks;

        public EnchantedGapplePrebuffSkill(SweatlordCombatBrain brain)
        {
            this.brain = brain;
        }

        @Override
        public String name()
        {
            return "enchanted_gapple_prebuff";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.PREBUFF;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_PREBUFF;
        }

        @Override
        public int cooldownTicks()
        {
            return 20;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!this.brain.isEncounterActive() || this.brain.hasUsedEnchantedGappleThisEncounter())
            {
                return false;
            }

            // Never again while a golden apple buff is still running: that covers the case where the flag of the
            // encounter was lost for any reason and is the second guard against an endless apple loop.
            return !context.appleBuffActive();
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.biteTicks = 0;
            PlayerLikeActions.stopUsing(mob);
            mob.loadout().equipGoldenApple(true);
            PlayerLikeActions.startUsingMainHand(mob);
            this.brain.log("prebuff: eating an enchanted golden apple");
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            ++this.biteTicks;
            PlayerLikeActions.brake(mob);

            if (context.hasTarget())
            {
                PlayerLikeActions.faceTowards(mob, context.target().position());
            }

            if (!mob.isUsingItem() && mob.getMainHandItem().isEmpty())
            {
                // The apple was finished and the game consumed it: the buff is on him now.
                this.brain.markEnchantedGappleUsed();
            }
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!this.brain.hasUsedEnchantedGappleThisEncounter() && mob.isUsingItem()
                    && mob.getMainHandItem().is(Items.ENCHANTED_GOLDEN_APPLE))
            {
                return this.biteTicks < BITE_TIMEOUT_TICKS;
            }

            // The bite is over (or was interrupted): the buff is either on him or the apple is gone, either way the
            // encounter flag is set and the skill is done.
            if (mob.hasEffect(net.minecraft.world.effect.MobEffects.ABSORPTION)
                    || mob.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION))
            {
                this.brain.markEnchantedGappleUsed();
            }

            return false;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            PlayerLikeActions.stopUsing(mob);
            this.beginCooldown();
            mob.loadout().equipCombatGrip();
        }

        @Override
        public boolean holdsBody(SweatlordSteveEntity mob)
        {
            // A bite that is running is finished: the fight can wait 32 ticks for it.
            return mob.isUsingItem();
        }
    }

    /**
     * The pearl jump: the one ability that makes him a player-like fighter instead of a walking target.
     *
     * <p>Three rules are non-negotiable here (the round asks for exactly these):
     *
     * <ol>
     * <li>the destination is never the enemy's own position but a spot the {@link SafePearlPlanner} chose and scored,</li>
     * <li>nothing is ever teleported by hand: a real pearl is thrown and the game teleports him when it lands,</li>
     * <li>and the skill is only over once the body has <b>really</b> arrived somewhere new - a pearl in the air is not
     * an arrival, and the brain may not switch to melee before the teleport happened.</li>
     * </ol>
     */
    public static final class OffensivePearlSkill extends SweatlordSkill
    {
        /** How long the whole jump may take before the skill gives up. */
        private static final int JUMP_TIMEOUT_TICKS = 100;

        /** A refused plan pauses the skill for longer than a normal jump: the search must never run every tick. */
        private static final int REFUSED_COOLDOWN_TICKS = 200;

        /** How far the body has to have moved for the jump to count as arrived. */
        private static final double ARRIVED_DISTANCE = 2.0D;

        private final SweatlordCombatBrain brain;

        @Nullable
        private SafePearlPlanner.Plan plan;

        @Nullable
        private ThrownEnderpearl pearl;

        private Vec3 startPosition = Vec3.ZERO;
        private int ticks;
        private boolean arrived;
        private boolean refused;

        /**
         * True when the brain called {@link #force()}: this jump is the answer to a ranged plan that clearly failed
         * (three missed rounds in a row) and not a normal choice of the kit. The landing safety is untouched by it.
         */
        private boolean forced;

        public OffensivePearlSkill(SweatlordCombatBrain brain)
        {
            this.brain = brain;
        }

        /**
         * Marks the next jump as the adaptation of the brain: "enough of this, I am coming over there".
         *
         * <p>It is set by {@code SweatlordCombatBrain#forcePearlGapClose} right before the skill is started, and it is
         * cleared when the jump ends. Nothing else about the jump changes: the same planner refuses the same lava, the
         * same void and the same blocked landing spot, so a panic pearl is still not a suicidal one.
         */
        public void force()
        {
            this.forced = true;
        }

        /** @return true when this jump is the adaptation of a failed ranged plan, for the debug output and the tests. */
        public boolean isForced()
        {
            return this.forced;
        }

        @Override
        public String name()
        {
            return "offensive_pearl";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.RANGED_COMBAT;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_PEARL;
        }

        @Override
        public int cooldownTicks()
        {
            return 80;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (!context.hasTarget() || !this.brain.isEncounterActive())
            {
                return false;
            }

            // A pearl jump is a way to get into the fight, never something to do at a target that is already close - and
            // never something to do instead of shooting: a pearl lobs the body over cover and costs the whole body for
            // seconds, so it is the answer to a fight this body can not shoot its way into, which is an enemy without a
            // line of sight or one beyond the reach of the bow. The first rule of this skill was "any enemy between five
            // and thirty-six blocks", and it made the ranged kit invisible: in the aim scenario of this round the pearl
            // took the body again and again for seven hundred ticks and the bow, the eggs and the trident never fired a
            // single shot. Every pearl of the other scenarios of the round (the ones against creepers and monsters at
            // twenty blocks) was thrown without a line of sight and keeps working under this rule.
            if (context.lineOfSight() && context.distance() <= CombatContext.BOW_MAX_DISTANCE)
            {
                return false;
            }

            return context.distance() > CombatContext.BOW_MIN_DISTANCE
                    && context.distance() < CombatContext.BOW_MAX_DISTANCE * 1.5D;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.ticks = 0;
            this.arrived = false;
            this.refused = false;
            this.pearl = null;
            this.startPosition = mob.position();
            this.brain.recordPearlMode(this.forced ? "forced: planning" : "planning");

            SafePearlPlanner.Result result = SafePearlPlanner.plan(mob, context.target(),
                    SafePearlPlanner.Mode.OFFENSIVE);
            this.plan = result.plan();

            if (this.plan == null)
            {
                this.refused = true;
                this.brain.recordPearlMode("refused");
                this.brain.recordPearlRejection("offensive: " + result.summary());
                this.brain.log("offensive pearl refused: " + result.summary());
                return;
            }

            this.brain.log("offensive pearl accepted: " + result.summary());
            PlayerLikeActions.brake(mob);
            mob.loadout().equipEnderPearl();
            this.pearl = PlayerLikeActions.throwPearl(mob, this.plan.velocity());
            this.brain.recordPearlMode("thrown to " + this.plan.destination());
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            ++this.ticks;

            if (this.refused)
            {
                if (context.hasTarget())
                {
                    PlayerLikeActions.faceTowards(mob, context.target().position());
                }

                return;
            }

            if (mob.position().distanceTo(this.startPosition) > ARRIVED_DISTANCE)
            {
                this.arrived = true;
                this.brain.recordPearlMode("arrived at " + mob.blockPosition());
                this.brain.log("offensive pearl landed, the body is at " + mob.blockPosition());
                return;
            }

            if (this.pearl == null || this.pearl.isRemoved() || !this.pearl.isAlive())
            {
                // The pearl is gone but the body has not moved yet. The teleport happens on the impact tick, so one
                // more tick is allowed before this counts as a failed jump.
                this.brain.recordPearlMode("pearl gone, waiting for the teleport");
            }
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            return !this.refused && !this.arrived && this.ticks < JUMP_TIMEOUT_TICKS;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            // A refused plan means there is no safe landing spot in this world right now, so the next look at it is
            // worth a good deal less often than a normal jump: the cooldown says exactly that. The adaptation of the
            // brain is over either way - a forced jump that was refused is a graceful failure and not a reason to keep
            // throwing pearls.
            this.setCooldown(this.refused ? REFUSED_COOLDOWN_TICKS : this.cooldownTicks());
            this.brain.recordPearlMode(this.refused
                    ? (this.forced ? "forced: no safe landing" : "refused, cooling down")
                    : this.arrived ? "arrived" : "timed out");
            this.forced = false;
            mob.loadout().equipCombatGrip();
            this.plan = null;
            this.pearl = null;
        }

        @Override
        public boolean holdsBody(SweatlordSteveEntity mob)
        {
            // A pearl in the air is a body that is not there yet: nothing else may take it over.
            return !this.arrived;
        }
    }

    /**
     * The low health retreat: get out with a pearl, eat exactly one plain golden apple, come back.
     *
     * <p>The thresholds live in {@link SweatlordCombatBrain} as named constants, never as magic numbers in the middle
     * of a decision. The retreat has four phases and ends on its own:
     *
     * <ol>
     * <li>an escape pearl from the planner in {@link SafePearlPlanner.Mode#ESCAPE} - and when there is no safe place
     * to land, no pearl is thrown at all and he simply backs off behind his shield,</li>
     * <li>waiting for the body to really arrive,</li>
     * <li>exactly one plain golden apple, never a second,</li>
     * <li>and waiting until he is fit again ({@link SweatlordCombatBrain#REENGAGE_HEALTH_RATIO}) or until the retreat
     * has run long enough that waiting longer would only stall the fight.</li>
     * </ol>
     */
    public static final class RetreatHealSkill extends SweatlordSkill
    {
        /** How long the whole retreat may last before he goes back in whatever happens. */
        private static final int RETREAT_TIMEOUT_TICKS = 400;

        /** How far the body has to have moved for the escape pearl to count as an arrival. */
        private static final double ARRIVED_DISTANCE = 2.0D;

        private final SweatlordCombatBrain brain;

        @Nullable
        private SafePearlPlanner.Plan plan;

        @Nullable
        private ThrownEnderpearl pearl;

        private Vec3 startPosition = Vec3.ZERO;
        private Vec3 pearlStart = Vec3.ZERO;
        private int ticks;
        private int appleTicks;
        private boolean arrived = true;
        private boolean appleEaten;

        public RetreatHealSkill(SweatlordCombatBrain brain)
        {
            this.brain = brain;
        }

        @Override
        public String name()
        {
            return "retreat_heal";
        }

        @Override
        public SweatlordCombatState state()
        {
            return SweatlordCombatState.RETREAT_HEAL;
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_RETREAT;
        }

        @Override
        public int cooldownTicks()
        {
            return 60;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (this.brain.isRetreatOver())
            {
                return false;
            }

            return context.healthRatio() <= SweatlordCombatBrain.RETREAT_HEALTH_RATIO;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.ticks = 0;
            this.appleTicks = 0;
            this.appleEaten = this.brain.isRetreatAppleEaten();
            this.pearl = null;
            this.startPosition = mob.position();
            this.arrived = false;
            this.brain.recordPearlMode("escape planning");
            PlayerLikeActions.stopUsing(mob);

            SafePearlPlanner.Result result = SafePearlPlanner.plan(mob, context.target(),
                    SafePearlPlanner.Mode.ESCAPE);
            this.plan = result.plan();

            if (this.plan == null)
            {
                // No safe place to jump to: never throw the pearl anyway. He backs off and keeps the shield up.
                this.brain.recordPearlMode("escape refused");
                this.brain.recordPearlRejection("escape: " + result.summary());
                this.brain.log("escape pearl refused, backing off instead: " + result.summary());
                this.arrived = true;
                return;
            }

            this.brain.log("escape pearl accepted: " + result.summary());
            PlayerLikeActions.brake(mob);
            mob.loadout().equipEnderPearl();
            this.pearlStart = mob.position();
            this.pearl = PlayerLikeActions.throwPearl(mob, this.plan.velocity());
            this.brain.recordPearlMode("escaping to " + this.plan.destination());
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            ++this.ticks;

            // Phase 1: wait for the body to really arrive through the pearl.
            if (!this.arrived)
            {
                if (mob.position().distanceTo(this.startPosition) > ARRIVED_DISTANCE
                        || this.pearl == null || this.pearl.isRemoved() || !this.pearl.isAlive())
                {
                    this.arrived = true;
                    this.brain.recordPearlMode("arrived at " + mob.blockPosition());
                }

                return;
            }

            // Phase 2: exactly one plain golden apple.
            if (!this.appleEaten)
            {
                if (!mob.isUsingItem() || !mob.getMainHandItem().is(Items.GOLDEN_APPLE))
                {
                    mob.loadout().equipGoldenApple(false);
                    PlayerLikeActions.startUsingMainHand(mob);
                }

                PlayerLikeActions.brake(mob);
                ++this.appleTicks;

                if (context.hasTarget())
                {
                    PlayerLikeActions.faceTowards(mob, context.target().position());
                }

                if (mob.getMainHandItem().isEmpty()
                        || mob.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION))
                {
                    this.appleEaten = true;
                    this.brain.markRetreatAppleEaten();
                    PlayerLikeActions.stopUsing(mob);
                    mob.loadout().equipCombatGrip();
                }

                return;
            }

            // Phase 3: keep the distance behind a real shield until he is fit again.
            if (context.hasTarget())
            {
                PlayerLikeActions.faceTowards(mob, context.target().position());
                Vec3 away = mob.position().subtract(context.target().position());

                if (away.lengthSqr() > 1.0E-6D && context.distance() < 8.0D)
                {
                    Vec3 step = mob.position().add(away.normalize().scale(4.0D));
                    mob.getNavigation().moveTo(step.x, step.y, step.z, 1.2D);
                }
                else
                {
                    PlayerLikeActions.brake(mob);
                }
            }

            if (!mob.loadout().hasShield())
            {
                mob.loadout().equipOffhand(Items.SHIELD);
                return;
            }

            PlayerLikeActions.startUsingOffhand(mob);
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            if (this.ticks >= RETREAT_TIMEOUT_TICKS)
            {
                return false;
            }

            return context.healthRatio() < SweatlordCombatBrain.REENGAGE_HEALTH_RATIO;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            PlayerLikeActions.stopUsing(mob);
            mob.loadout().equipCombatGrip();
            this.brain.markRetreatOver();
            this.brain.recordPearlMode("retreat over");
            this.plan = null;
            this.pearl = null;
        }
    }
}
