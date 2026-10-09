package spiderqueenhungrycraft.compat.iceandfire.ai;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;

/**
 * Who a dragon under a contract is allowed to fight (Necromancy IV round).
 *
 * <p><b>Why a goal and not a line inside the dragon.</b> A contract dragon is, in the data of Ice and Fire, an
 * ordinary <i>wild</i> dragon: the contract deliberately never calls {@code tame} or {@code setOwnerUUID}, so the owner
 * of that record stays untouched - and that is exactly why the native targeting goals of that mod would happily pick
 * the queen who holds the contract, or her spiders. Instead of mixing into the other mod, this goal is installed on the
 * contract dragon alone and <b>holds the target flag of the entity</b> while the contract lives:
 *
 * <ul>
 * <li>a goal of the {@code TARGET} flag at priority {@code 0} is the first one asked every tick, and a running goal
 * keeps its flag, so neither {@code DragonAITarget} nor {@code DragonAITargetNonTamed} (priorities 6 and 5) can start
 * while this one runs - the wild hunting of the skeleton is switched off by taking its turn, not by deleting it,</li>
 * <li>the target of the dragon is written here, so the native attack AI of Ice and Fire
 * ({@code DragonAIAttackMelee} on the ground and {@code IafDragonLogic#updateDragonAttack} in the air, breath
 * included) does the fighting, with its own claws, bites, damage and configuration,</li>
 * <li>and every candidate passes {@link NecromancyService#isFriendlyToContract}, which is the one definition of a
 * friend of the contract: the queen herself, a spider of her brood and another dragon of her own contract are never
 * accepted - not by an order, and not by the hunt.</li>
 * </ul>
 *
 * <p>What is deliberately left is the rest of the animal: an ordered victim, whatever hurt the skeleton last, and the
 * nearest ordinary <b>hostile</b> creature of the game ({@link Enemy}) are all valid, so a skeleton on a payroll still
 * defends itself and still clears a cave - it simply never turns on the brood that pays it.
 *
 * <p>This file names no Ice and Fire type on purpose: it is installed by {@code IceAndFireBridge}, which is the only
 * class of this mod allowed to talk to that mod, and it works on the vanilla {@link Mob} surface alone.
 */
public final class NecromancyTargetGoal extends Goal
{
    /** How far a skeleton looks for the natural prey of the brood while it follows, holds or patrols. */
    private static final double HUNT_RANGE = 24.0D;

    /** And how far a skeleton looks while it is on the free hunt, which is a wider order than a post. */
    private static final double FREE_HUNT_RANGE = 32.0D;

    private final Mob minion;

    public NecromancyTargetGoal(Mob minion)
    {
        this.minion = minion;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        return stillUnderContract();
    }

    @Override
    public boolean canContinueToUse()
    {
        return stillUnderContract();
    }

    /** @return true while that dragon is awake and really holds a contract this mod wrote. */
    private boolean stillUnderContract()
    {
        return NecromancyService.isContractedDragon(this.minion) && this.minion.isAlive() && !this.minion.isRemoved();
    }

    @Override
    public void tick()
    {
        if (this.minion.isVehicle())
        {
            // The queen is on its back, so the skeleton fights only what she makes it fight: the target is cleared - the
            // native melee and breath goals of Ice and Fire then have nothing to aim at - while the <b>flag</b> this goal
            // holds is kept, so the wild hunting of that mod still can not pick her or her spiders. The moment she steps
            // off, the very next tick of this goal chooses a victim again, exactly as the contract says.
            if (this.minion.getTarget() != null)
            {
                this.minion.setTarget(null);
            }

            return;
        }

        LivingEntity wanted = wantedTarget();
        LivingEntity current = this.minion.getTarget();

        // Careful, and it is the one line of this goal that crashed a live world once: the wanted target is null most of
        // the time (a contract with nothing to fight), so it can never be the left hand of an equals call.
        if (wanted != null ? wanted.equals(current) : current == null)
        {
            return;
        }

        if (wanted != null)
        {
            // A dragon that was lying on its post gets up for a fight - and the mod derives "may this animal move" from
            // that very flag, so a sitting dragon that kept its pose could never chase anything.
            IceAndFireSupport.setSitting(this.minion, false);
        }

        this.minion.setTarget(wanted);
    }

    /** @return the creature the dragon should be fighting right now, or null when it should be fighting nobody. */
    @Nullable
    private LivingEntity wantedTarget()
    {
        NecromancyContract contract = NecromancyOrders.contractOf(this.minion).orElse(null);

        if (contract == null)
        {
            return null;
        }

        if (contract.order() == NecromancyContract.Order.ATTACK)
        {
            Optional<LivingEntity> victim = NecromancyOrders.orderedVictim(this.minion);

            if (victim.isEmpty() || !NecromancyHunting.isTarget(this.minion, victim.get())
                    || NecromancyHunting.isOwnSide(this.minion, victim.get()))
            {
                // The victim died, left the level or turned out to be a friend of the contract: the order is over, and it
                // is over on the contract itself, so a reload of the chunk does not bring it back.
                NecromancyOrders.clearOrder(this.minion);

                return null;
            }

            return victim.get();
        }

        SpiderOrderMode stance = contract.stance();
        LivingEntity current = this.minion.getTarget();

        if (current != null && isPrey(stance, current))
        {
            // A fight that is already running is finished, whoever started it, and a chase is never dropped because the
            // victim ran out of the range the search uses.
            return current;
        }

        LivingEntity byHurt = this.minion.getLastHurtByMob();

        if (NecromancyHunting.isTarget(this.minion, byHurt) && !NecromancyHunting.isOwnSide(this.minion, byHurt))
        {
            // Whoever hit the skeleton is answered, whatever its stance is: a garrison dragon that is being shot at is
            // allowed to shoot back even while a march is on the contract.
            return byHurt;
        }

        if (contract.order() != NecromancyContract.Order.NONE)
        {
            // A march and a bombardment are errands, not an invitation to hunt: the dragon keeps its eyes on the errand.
            return null;
        }

        return nearestPrey(stance);
    }

    /** @return true when that creature is prey under that stance, asked exactly the way the spiders ask it. */
    private boolean isPrey(SpiderOrderMode stance, LivingEntity candidate)
    {
        return stance.huntsFreely()
                ? NecromancyHunting.isFreeAttackPrey(this.minion, candidate)
                : NecromancyHunting.isNaturalPrey(this.minion, queen(), candidate);
    }

    /** @return the nearest prey of that stance, or null when there is none around. */
    @Nullable
    private LivingEntity nearestPrey(SpiderOrderMode stance)
    {
        double range = stance.huntsFreely() ? FREE_HUNT_RANGE : HUNT_RANGE;
        AABB area = this.minion.getBoundingBox().inflate(range);
        LivingEntity nearest = null;
        double closest = Double.MAX_VALUE;

        for (LivingEntity candidate : this.minion.level().getEntitiesOfClass(LivingEntity.class, area))
        {
            if (!isPrey(stance, candidate))
            {
                continue;
            }

            double distance = this.minion.distanceToSqr(candidate);

            if (distance < closest)
            {
                closest = distance;
                nearest = candidate;
            }
        }

        return nearest;
    }

    /** @return the queen who holds the contract of that dragon, or null when it is not under one. */
    @Nullable
    private Entity queen()
    {
        return NecromancyService.contractOwner(this.minion)
                .map(owner -> this.minion.level().getPlayerByUUID(owner)).orElse(null);
    }
}
