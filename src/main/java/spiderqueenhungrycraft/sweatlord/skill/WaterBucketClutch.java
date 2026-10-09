package spiderqueenhungrycraft.sweatlord.skill;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.CombatContext;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatBrain;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatState;
import spiderqueenhungrycraft.sweatlord.SweatlordSkill;

/**
 * The bucket emergencies of the Sweatlord (Sweatlord V1.1): burning, and falling to death.
 *
 * <p>Two ordinary skills live here - an emergency is not a new kind of thing, it is a skill with a very good reason -
 * and both are <b>interrupts</b>: the brain asks them before everything else, they may take the body away from a bow
 * draw, an egg burst, a trident throw or a sword combo, and they never wait for one of those to finish. The most
 * immediately lethal danger wins, which is why the fall outranks the fire.
 *
 * <p><b>What a bucket really is in this mod.</b> The water is a real {@code Blocks.WATER} source placed through
 * {@link PlayerLikeActions#placeWaterAt}, inside the real reach of a player and only where the world allows it - no
 * dimension that would evaporate it, no block a fluid can not replace, never more than one source, and only his own
 * source is ever taken back. Nothing is ever scooped out of a lake he happened to fall into.
 *
 * <p><b>And a bucket is never a lock.</b> That is the part that matters most in a fight: the loadout of this body is
 * {@link spiderqueenhungrycraft.sweatlord.VirtualLoadout virtual}, so the moment this action ends the water bucket is
 * <b>available again</b>, whatever happened to the block in the world - it flowed away, it was replaced, he was knocked
 * out of reach, the chunk changed, he landed too fast to take it back. There is no "has water" flag anywhere in this
 * class, so there is no state that could stay wrong, and a failed recovery is a supported outcome and not an error.
 */
public final class WaterBucketClutch
{
    /**
     * A fall that would deal less than this is not worth a bucket: the vanilla rule gives three blocks for free, and a
     * two or three block hop must never make him stop fighting to water the floor.
     */
    public static final float MIN_FALL_DAMAGE = 4.0F;

    /** How far below him the landing floor is looked for. Deeper than this is the void, where water would not help. */
    private static final int FLOOR_SCAN_DEPTH = 24;

    private WaterBucketClutch()
    {
    }

    /**
     * The machinery both emergencies share: plan a spot, pour the water at the moment the world allows it, try to take
     * the source back, and <b>always</b> give the body back.
     */
    private abstract static class AbstractWaterClutchSkill extends SweatlordSkill
    {
        /** How long the source is chased after the emergency is over before the attempt is given up. */
        private static final int MAX_RECOVERY_TICKS = 12;

        private int age;
        private int recoveryTicks;
        private boolean placed;
        private boolean recovered;

        /** The one source he placed himself, and the only block he is ever allowed to scoop up. */
        @Nullable
        private BlockPos waterSpot;

        /** @return the spot the water must be poured at right now, or null while the world does not allow it yet. */
        @Nullable
        protected abstract BlockPos plan(SweatlordSteveEntity mob);

        /** @return true while the emergency is still going on: still falling, still burning. */
        protected abstract boolean emergencyStillThere(SweatlordSteveEntity mob);

        /** @return the longest this emergency may hold the body before it gives up. */
        protected abstract int maxTicks();

        /**
         * @return true when the body may be held after the emergency is over, in the hope of scooping the source back.
         *         It is the difference between a body that finishes a clutch and a body that lingers in its own puddle:
         *         the scoop itself is a single tick of work, and anything after that is the body standing still for no
         *         reason a player would recognise.
         */
        protected boolean chasesTheSourceAfterwards()
        {
            return true;
        }

        @Override
        public final SweatlordCombatState state()
        {
            return SweatlordCombatState.EMERGENCY;
        }

        @Override
        public boolean holdsBody(SweatlordSteveEntity mob)
        {
            return true;
        }

        @Override
        public boolean mayTakeOver(SweatlordSkill running)
        {
            // An emergency does not negotiate: a running draw, burst, throw or combo is dropped.
            return true;
        }

        @Override
        public void start(SweatlordSteveEntity mob, CombatContext context)
        {
            this.age = 0;
            this.recoveryTicks = 0;
            this.placed = false;
            this.recovered = false;
            this.waterSpot = null;
            mob.loadout().equipWaterBucket();
            mob.brain().log(this.name() + ": bucket out");
        }

        @Override
        public void tick(SweatlordSteveEntity mob, CombatContext context)
        {
            ++this.age;

            if (this.placed)
            {
                this.tickRecovery(mob);
                return;
            }

            BlockPos spot = this.plan(mob);

            if (spot == null)
            {
                // Not yet: a falling body is simply not close enough to the ground for the reach of a player, and the
                // plan of the next tick will say so again. There is no "place it early" path anywhere in this class.
                return;
            }

            this.waterSpot = spot;
            mob.getLookControl().setLookAt(spot.getX() + 0.5D, spot.getY() + 0.5D, spot.getZ() + 0.5D, 30.0F, 30.0F);

            if (!PlayerLikeActions.placeWaterAt(mob.level(), spot))
            {
                return;
            }

            this.placed = true;
            mob.loadout().equipEmptyBucket();
            mob.brain().log(this.name() + ": water placed at " + spot);
        }

        /** The best effort recovery of his own source: it never holds the body and it never locks anything. */
        private void tickRecovery(SweatlordSteveEntity mob)
        {
            if (this.recovered || this.waterSpot == null || this.emergencyStillThere(mob))
            {
                return;
            }

            ++this.recoveryTicks;

            if (PlayerLikeActions.pickUpWaterAt(mob.level(), this.waterSpot, mob.getEyePosition()))
            {
                this.recovered = true;
                mob.brain().log(this.name() + ": the source was taken back");
            }
        }

        @Override
        public boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context)
        {
            if (this.age >= this.maxTicks())
            {
                return false;
            }

            if (!this.placed)
            {
                // The water is not out yet, so the emergency itself is the only reason to hold the body.
                return this.emergencyStillThere(mob);
            }

            if (this.recovered || this.recoveryTicks > MAX_RECOVERY_TICKS || !this.chasesTheSourceAfterwards())
            {
                return this.emergencyStillThere(mob);
            }

            return true;
        }

        @Override
        public void stop(SweatlordSteveEntity mob)
        {
            this.beginCooldown();
            this.waterSpot = null;
            this.placed = false;
            this.recovered = false;
            this.recoveryTicks = 0;
            mob.loadout().equipCombatGrip();
        }
    }

    /**
     * The MLG water clutch: a fall that would really hurt is answered with one real water source on the landing surface.
     *
     * <p><b>It is not triggered by a hop.</b> The fall has to be one the vanilla rule would punish
     * ({@link #MIN_FALL_DAMAGE} and up, which is a drop of seven blocks or more), so stepping off a rock never costs him
     * a bucket and never interrupts his fight.
     *
     * <p><b>It is not a cheat either.</b> The plan is the block <b>above</b> the first floor under him - the spot he is
     * going to land in - and it is only poured when that spot is inside the real reach of a player
     * ({@link PlayerLikeActions#BUCKET_REACH}). At the speed of a fall that is the last tick or two before the impact,
     * which is exactly the timing of the trick and the reason there is no "place it ten blocks early" path here.
     *
     * <p>And it really works because the game says so and not because this mod says so: {@code Entity#move} clips the
     * movement of a falling body with {@code ClipContext.Fluid.WATER} and resets the fall distance when the water is in
     * the way, so landing in his own source is a landing without fall damage.
     */
    public static final class MlgClutchSkill extends AbstractWaterClutchSkill
    {
        /** Long enough for the longest survivable fall, and still finite: the body is never parked in this action. */
        private static final int MAX_TICKS = 60;

        /** The water goes out when the landing is this many ticks away, which is the timing of the real trick. */
        private static final int PLACE_AT_TICKS = 2;

        /** The gravity of a living body of the game, and the drag its vertical motion is multiplied with. */
        private static final double LIVING_GRAVITY = 0.08D;
        private static final double LIVING_DRAG = 0.98D;

        @Override
        public String name()
        {
            return "mlg_water_clutch";
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_MLG_CLUTCH;
        }

        @Override
        public int cooldownTicks()
        {
            return 40;
        }

        @Override
        protected int maxTicks()
        {
            return MAX_TICKS;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            if (mob.onGround() || mob.isInWater() || mob.getDeltaMovement().y >= -0.1D)
            {
                return false;
            }

            if (predictedFallDamage(mob) < MIN_FALL_DAMAGE || !PlayerLikeActions.canWaterExist(mob.level()))
            {
                return false;
            }

            BlockPos spot = landingSpot(mob);

            return spot != null && mob.level().getBlockState(spot).canBeReplaced();
        }

        @Override
        protected boolean emergencyStillThere(SweatlordSteveEntity mob)
        {
            return !mob.onGround();
        }

        @Override
        @Nullable
        protected BlockPos plan(SweatlordSteveEntity mob)
        {
            // The timing of the trick, and the reason it is not a cheat: the water only goes out in the last ticks before
            // the landing, never ten blocks early, and only where a player could really pour it.
            if (ticksToLand(mob) > PLACE_AT_TICKS)
            {
                return null;
            }

            Vec3 eye = mob.getEyePosition();
            BlockPos landing = landingSpot(mob);

            // The landing spot itself when it is already inside the reach of a player (which it is on the last tick of a
            // slow fall), and otherwise the block under his feet - which is exactly where a player aims when they drop it
            // on the way down. The fall passes through it either way, which is what resets the fall distance.
            if (landing != null && PlayerLikeActions.canPlaceWater(mob.level(), landing, eye))
            {
                return landing;
            }

            BlockPos below = mob.blockPosition().below();

            return PlayerLikeActions.canPlaceWater(mob.level(), below, eye) ? below : null;
        }

        /**
         * @return an estimate of the ticks the fall still needs to reach the floor, counted with the gravity and the drag
         *         of a living body of the game ({@code 0.08} and {@code 0.98}). It is an estimate and it is used as one:
         *         it only decides when the water is worth pouring, and the placement itself is checked against the world.
         */
        private static int ticksToLand(SweatlordSteveEntity mob)
        {
            BlockPos floor = landingFloor(mob);

            if (floor == null)
            {
                return Integer.MAX_VALUE;
            }

            double top = floor.getY() + 1.0D;
            double y = mob.getY();
            double motion = mob.getDeltaMovement().y;

            for (int tick = 1; tick <= MAX_TICKS; ++tick)
            {
                motion = (motion - LIVING_GRAVITY) * LIVING_DRAG;
                y += motion;

                if (y <= top)
                {
                    return tick;
                }
            }

            return Integer.MAX_VALUE;
        }

        /** @return the block above the first floor under him, which is the spot he is going to land in. */
        @Nullable
        private static BlockPos landingSpot(SweatlordSteveEntity mob)
        {
            BlockPos floor = landingFloor(mob);

            return floor == null ? null : floor.above();
        }

        /** @return the first floor under him, or null when he is falling into the void. */
        @Nullable
        private static BlockPos landingFloor(SweatlordSteveEntity mob)
        {
            BlockPos base = mob.blockPosition();

            for (int dy = 1; dy <= FLOOR_SCAN_DEPTH; ++dy)
            {
                BlockPos below = base.below(dy);

                if (!mob.level().getBlockState(below).getCollisionShape(mob.level(), below).isEmpty())
                {
                    return below;
                }
            }

            return null;
        }

        /**
         * @return the damage the landing would deal right now, counted with the vanilla rule of {@code LivingEntity}:
         *         nothing up to three blocks, then one point per block, from the height he still has to fall.
         */
        private static float predictedFallDamage(SweatlordSteveEntity mob)
        {
            BlockPos floor = landingFloor(mob);

            if (floor == null)
            {
                return 0.0F;
            }

            double drop = mob.getY() - (floor.getY() + 1.0D);
            float fall = mob.fallDistance + (float) Math.max(0.0D, drop);

            return Math.max(0.0F, (float) Math.ceil(fall - 3.0F));
        }
    }

    /**
     * Being on fire: one source at his own feet, and the body back the moment the fire is out.
     *
     * <p>The fire is not put out by this mod. It is put out by the game, because a body standing in water is
     * extinguished by {@code Entity#baseTick} and {@code LivingEntity#tick} themselves - this skill only makes the water
     * exist where it has to exist, which is the block his feet are in.
     *
     * <p><b>And then he walks on.</b> Extinguishing is not a place to stay: the source is scooped back on the same tick
     * the fire dies, if he can still reach it at all, and the body is handed back in that very tick - so a fight is
     * resumed and a stroll is resumed instead of a body standing in a puddle it made itself.
     */
    public static final class ExtinguishFireSkill extends AbstractWaterClutchSkill
    {
        private static final int MAX_TICKS = 40;

        @Override
        public String name()
        {
            return "water_extinguish";
        }

        @Override
        public int priority()
        {
            return SweatlordCombatBrain.PRIORITY_FIRE_CLUTCH;
        }

        @Override
        public int cooldownTicks()
        {
            return 20;
        }

        @Override
        protected int maxTicks()
        {
            return MAX_TICKS;
        }

        @Override
        public boolean canStart(SweatlordSteveEntity mob, CombatContext context)
        {
            return mob.isOnFire() && !mob.isInWater() && this.plan(mob) != null;
        }

        @Override
        protected boolean emergencyStillThere(SweatlordSteveEntity mob)
        {
            return mob.isOnFire();
        }

        @Override
        protected boolean chasesTheSourceAfterwards()
        {
            // The fire is the emergency, and water was the answer to it: once the body is not burning any more the
            // clutch has nothing left to do. Chasing the source for a few more ticks would park him in his own puddle.
            return false;
        }

        @Override
        @Nullable
        protected BlockPos plan(SweatlordSteveEntity mob)
        {
            // The block his own feet are in: that is where the water has to go, and it is what a player does when they
            // dump a bucket on the ground they are standing on.
            BlockPos spot = mob.blockPosition();

            return PlayerLikeActions.canPlaceWater(mob.level(), spot, mob.getEyePosition()) ? spot : null;
        }
    }
}
