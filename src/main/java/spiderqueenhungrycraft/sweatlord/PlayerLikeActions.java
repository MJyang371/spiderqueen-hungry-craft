package spiderqueenhungrycraft.sweatlord;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.sweatlord.MovingTargetBallistics.ProjectileProfile;

/**
 * The player-like primitives of the Sweatlord (Sweatlord round).
 *
 * <p>Everything here is the server side version of something a player does, and every one of them uses the real game
 * path: a real item use state ({@code startUsingItem}), a real vanilla projectile, a real melee hit with the vanilla
 * crit rule and the vanilla knockback. Nothing in this class keeps a boolean of its own, which is exactly why the
 * renderer, the vanilla damage pipeline, the vanilla shield logic and the web shield rule of this mod all see the same
 * body doing the same thing.
 *
 * <p>The crit rule is the vanilla one for a player (the same rule the reference {@code AttackTiming} uses): falling,
 * off the ground, not climbing, not in water, not blind, not riding.
 */
public final class PlayerLikeActions
{
    /** The launch speed of a fully drawn arrow. The owning profile is the single source of truth for it. */
    public static final float ARROW_SPEED = (float) MovingTargetBallistics.ProjectileProfile.BOW.launchSpeed();

    /**
     * The inaccuracy of an arrow, an egg and a trident of this round: zero. The intercept of every shot is solved
     * ({@link MovingTargetBallistics#solve}), so the projectile leaves exactly on the trajectory that reaches the body
     * - and a random spread on top of a solved intercept is nothing but a miss with extra steps. He is a transcendent
     * player-like combatant, not a vanilla mob with a scatter gun.
     */
    public static final float SOLVED_INACCURACY = 0.0F;

    /** The reach a player has when they use a bucket, which is what limits the water clutch. */
    public static final double BUCKET_REACH = 4.5D;

    /** How long a full draw is held before the arrow leaves. */
    public static final int BOW_DRAW_TICKS = 20;

    /** The vanilla crit multiplier of a player melee hit. */
    public static final float CRIT_MULTIPLIER = 1.5F;

    private PlayerLikeActions()
    {
    }

    /** @return true when a swing right now would be a vanilla critical hit. */
    public static boolean isCritWindow(SweatlordSteveEntity mob)
    {
        return mob.getDeltaMovement().y < 0.0D && !mob.onGround() && !mob.onClimbable() && !mob.isInWater()
                && !mob.hasEffect(MobEffects.BLINDNESS) && !mob.isPassenger();
    }

    /** @return true when the target is inside the real reach of his weapon and can be seen. */
    public static boolean canMelee(SweatlordSteveEntity mob, LivingEntity target)
    {
        double reach = mob.attackReach() + (target.getBbWidth() + mob.getBbWidth()) * 0.5D;

        return mob.distanceTo(target) <= reach && mob.hasLineOfSight(target);
    }

    /**
     * One melee swing on the real game path: the damage of the attribute plus the weapon bonus, the vanilla crit
     * multiplier while he is falling, the vanilla knockback, the swing animation and the hurt memory.
     *
     * @return true when the target was really hurt by this swing.
     */
    public static boolean meleeAttack(SweatlordSteveEntity mob, LivingEntity target)
    {
        float damage = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) + mob.heldWeaponDamageBonus();
        boolean crit = isCritWindow(mob);

        if (crit)
        {
            damage *= CRIT_MULTIPLIER;
        }

        boolean hurt = target.hurt(mob.damageSources().mobAttack(mob), damage);

        if (hurt)
        {
            float yaw = mob.getYRot() * ((float) Math.PI / 180.0F);
            target.knockback(0.4D, Math.sin(yaw), -Math.cos(yaw));
            mob.setLastHurtMob(target);
            mob.recordCrit(crit);

            if (crit)
            {
                playCritFeedback(mob, target);
            }
        }

        mob.swingMainHand();

        return hurt;
    }

    /** The crit sparks a player's critical hit produces, so a jump attack is visible and not just extra damage. */
    public static void playCritFeedback(SweatlordSteveEntity mob, LivingEntity target)
    {
        if (mob.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 8, 0.25D, 0.25D, 0.25D, 0.05D);
        }
    }

    /** Starts using the item in the main hand, the real vanilla state a player gets from holding the use key. */
    public static boolean startUsingMainHand(SweatlordSteveEntity mob)
    {
        if (mob.isUsingItem())
        {
            return false;
        }

        mob.startUsingItem(InteractionHand.MAIN_HAND);

        return mob.isUsingItem();
    }

    /** Starts using the item in the offhand, which is how a shield is really held up. */
    public static boolean startUsingOffhand(SweatlordSteveEntity mob)
    {
        if (mob.isUsingItem())
        {
            return false;
        }

        mob.startUsingItem(InteractionHand.OFF_HAND);

        return mob.isUsingItem();
    }

    /**
     * Raises the guard <b>now</b>: a real shield in the offhand, the real use state up, and whatever else he was
     * using is dropped - and never a settling tick in between.
     *
     * <p>The reason is the budget of the reaction: the game only lets a shield block after five ticks of use
     * ({@code LivingEntity#isBlocking}), and an arrow of this mod crosses three blocks in a tick. Every tick spent
     * waiting here is therefore a tick the shot gets for free, which is exactly why a reaction that waited for
     * anything reads as "he never raises the shield at all".
     */
    public static boolean raiseShield(SweatlordSteveEntity mob)
    {
        if (!mob.getOffhandItem().is(Items.SHIELD))
        {
            mob.setItemSlot(EquipmentSlot.OFFHAND, VirtualLoadout.stackOf(Items.SHIELD));
        }

        if (!mob.isUsingItem() || !mob.getUseItem().is(Items.SHIELD))
        {
            // A running use state - a bow draw, an apple - has to end first: the game never switches the use state of
            // a body that is already using something.
            stopUsing(mob);
            mob.startUsingItem(InteractionHand.OFF_HAND);
        }

        return mob.isUsingItem();
    }

    /** Ends the use state, whatever it was. */
    public static void stopUsing(SweatlordSteveEntity mob)
    {
        if (mob.isUsingItem())
        {
            mob.stopUsingItem();
        }
    }

    /** Turns the whole body towards a point, which is what makes a raised shield really cover the front. */
    public static void faceTowards(SweatlordSteveEntity mob, Vec3 point)
    {
        mob.getLookControl().setLookAt(point.x, point.y + 0.4D, point.z, 30.0F, 30.0F);
        double dx = point.x - mob.getX();
        double dz = point.z - mob.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
    }

    /** Stops the pathing, used while an action owns the body. */
    public static void brake(SweatlordSteveEntity mob)
    {
        mob.getNavigation().stop();
    }

    /**
     * @return a real arrow of this mod, launched on the solved intercept of {@link MovingTargetBallistics} so that it
     *         really reaches that body - moving or not.
     */
    public static Arrow shootArrow(SweatlordSteveEntity mob, LivingEntity target)
    {
        Vec3 velocity = solvedVelocity(mob, target, ProjectileProfile.BOW);

        Arrow arrow = new Arrow(mob.level(), mob);
        arrow.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), SOLVED_INACCURACY);
        arrow.setCritArrow(true);
        mob.level().addFreshEntity(arrow);
        MovingTargetBallistics.attach(mob, arrow, target, ProjectileProfile.BOW);

        return arrow;
    }

    /**
     * @return the launch velocity of the solved intercept of that profile, or a straight shot of the full speed as the
     *         one honest fallback when no arc of that profile can reach the body at all (an enemy so far away that the
     *         item can not cross the distance).
     */
    private static Vec3 solvedVelocity(SweatlordSteveEntity mob, LivingEntity target, ProjectileProfile profile)
    {
        Vec3 from = ProjectileBallistics.eyeOf(mob);
        AABB box = target.getBoundingBox();
        Vec3 velocity = MovingTargetBallistics.solveAim(from, box.getCenter(), target.getDeltaMovement(), box, profile);

        if (velocity != null)
        {
            return velocity;
        }

        Vec3 aim = ProjectileBallistics.aimPointOf(target);
        Vec3 direction = aim.subtract(from);

        return direction.lengthSqr() < 1.0E-6D ? mob.getViewVector(1.0F).scale(profile.launchSpeed())
                : direction.normalize().scale(profile.launchSpeed());
    }

    /**
     * @return a real egg, thrown on the solved intercept at that body.
     *
     * <p>No random spread: he is a transcendent player-like combatant and not a vanilla mob, and a spread on top of a
     * solved intercept is nothing but a miss with extra steps.
     */
    public static ThrownEgg throwEgg(SweatlordSteveEntity mob, LivingEntity target)
    {
        Vec3 velocity = solvedVelocity(mob, target, ProjectileProfile.EGG);
        ThrownEgg egg = new ThrownEgg(mob.level(), mob);
        egg.setItem(new ItemStack(Items.EGG));
        egg.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), SOLVED_INACCURACY);
        mob.level().addFreshEntity(egg);
        MovingTargetBallistics.attach(mob, egg, target, ProjectileProfile.EGG);

        return egg;
    }

    /** @return a real trident, thrown on the solved intercept at that body. */
    public static ThrownTrident throwTrident(SweatlordSteveEntity mob, LivingEntity target)
    {
        Vec3 velocity = solvedVelocity(mob, target, ProjectileProfile.TRIDENT);
        ThrownTrident trident = new ThrownTrident(mob.level(), mob, new ItemStack(Items.TRIDENT));
        trident.shoot(velocity.x, velocity.y, velocity.z, (float) velocity.length(), SOLVED_INACCURACY);
        mob.level().addFreshEntity(trident);
        MovingTargetBallistics.attach(mob, trident, target, ProjectileProfile.TRIDENT);

        return trident;
    }

    /** @return a real ender pearl with exactly that velocity, which is the solved throw of the pearl planner. */
    public static ThrownEnderpearl throwPearl(SweatlordSteveEntity mob, Vec3 velocity)
    {
        ThrownEnderpearl pearl = new ThrownEnderpearl(mob.level(), mob);
        pearl.setDeltaMovement(velocity);
        mob.level().addFreshEntity(pearl);

        return pearl;
    }

    // -------------------------------------------------------------------------------------------------------------
    // The bucket, as a player uses it
    // -------------------------------------------------------------------------------------------------------------

    /**
     * @return true when a water source may legally be poured at that spot from that position, which is the same
     *         question a player's bucket asks the world: a loaded chunk, no dimension where water would evaporate, a
     *         spot inside the reach of a player, and a block that can be replaced by a fluid.
     */
    public static boolean canPlaceWater(Level level, BlockPos pos, Vec3 from)
    {
        if (!canWaterExist(level))
        {
            return false;
        }

        ServerLevel server = (ServerLevel) level;

        if (!server.isLoaded(pos) || !server.hasChunkAt(pos))
        {
            return false;
        }

        if (from.distanceTo(Vec3.atCenterOf(pos)) > BUCKET_REACH)
        {
            return false;
        }

        return server.getBlockState(pos).canBeReplaced();
    }

    /**
     * @return true when that body is really holding a shield up right now, <b>without</b> the five tick wind up of the
     *         game.
     *
     * <p>The real question of a shield is {@code LivingEntity#isBlocking}, and it only answers after five ticks of use.
     * That rule is untouched for every body in the game; this method is the raw material of the one exception of this
     * round - the instant projectile guard of the Sweatlord, whose visual shield is a real shield and whose reflex may
     * answer a projectile on the tick it came up.
     */
    public static boolean holdsShieldInUse(LivingEntity body)
    {
        return body.isUsingItem() && body.getUseItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /** @return true when water can exist in that world at all: a dimension as hot as the nether evaporates it. */
    public static boolean canWaterExist(Level level)
    {
        return level instanceof ServerLevel server && !server.dimensionType().ultraWarm();
    }

    /** @return true when a real water source is really standing at that spot after this call. */
    public static boolean placeWaterAt(Level level, BlockPos pos)
    {
        if (!(level instanceof ServerLevel server) || !server.isLoaded(pos))
        {
            return false;
        }

        server.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());

        return server.getBlockState(pos).is(Blocks.WATER);
    }

    /** @return true when that spot really held a water source and is empty air now. */
    public static boolean pickUpWaterAt(Level level, BlockPos pos, Vec3 from)
    {
        if (!(level instanceof ServerLevel server) || !server.isLoaded(pos))
        {
            return false;
        }

        // A player has to be able to reach the source they are scooping up, and so does he.
        if (from.distanceTo(Vec3.atCenterOf(pos)) > BUCKET_REACH)
        {
            return false;
        }

        BlockState state = server.getBlockState(pos);

        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource())
        {
            return false;
        }

        server.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());

        return server.getBlockState(pos).isAir();
    }
}
