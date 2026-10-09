package spiderqueenhungrycraft.compat.iceandfire.ai;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;

/**
 * The two errands a dragon under a contract can be sent on: a march, and a bombardment (Necromancy IV round).
 *
 * <p><b>Why a goal.</b> Ice and Fire keeps a dragon moving with goals of its own ({@code DragonAIWander} at priority
 * 7, {@code DragonAIAttackMelee} at 5) and with a flight manager that decides on every tick whether the animal should
 * walk, take off or hover. A command that simply called {@code getNavigation().moveTo} every tick from the outside
 * would be fighting all of that for control, so this goal does what the escort goal of that mod does: it owns the
 * <b>move flag</b> at priority 3 - higher than the wander - and hands Ice and Fire a destination. From there the
 * native movement, the native path finder, the native flight decision and the native animation do the walking, exactly
 * as they do for a dragon that escorts its owner.
 *
 * <p><b>The phases of a bombardment</b> are the three that were already reserved on the contract:
 * {@code TRAVELLING} (fly to the point above the target), {@code AIMING} (hover, turn the head, take a breath) and
 * {@code BREATHING} (breathe, tick by tick, through the native breath of the dragon). The stage gate of the round -
 * a dragon younger than stage 3 does not breathe - is enforced here, where the real stage of an Ice and Fire dragon is
 * known, and the queen is told with the line that was reserved for it.
 *
 * <p>This file names no Ice and Fire type: the dragon specific half of it (hovering, the breath, the stage, the
 * breath sound) is asked through {@link IceAndFireSupport}, which is the facade of the compatibility.
 */
public final class NecromancyMoveGoal extends Goal
{
    /** How fast the dragon is sent after its destination: the speed the escort goal of Ice and Fire uses as well. */
    private static final double SPEED = 1.0D;

    /** How close a dragon has to be to a spot before it counts as arrived. */
    private static final double ARRIVE_DISTANCE = 6.0D;

    /** How often the destination is handed to the navigation again, in ticks. */
    private static final int REPATH_TICKS = 20;

    /** How long one errand may take before it is dropped, so a dragon can not try forever: one minute. */
    private static final int GIVE_UP_TICKS = 20 * 60;

    /** How long the dragon takes aim before it breathes, in ticks. */
    private static final int AIM_TICKS = 20;

    /** The muzzle sound of a native breath is played every fifth tick, like the native loop does. */
    private static final int BREATH_SOUND_TICKS = 5;

    /**
     * How long the goal watches a destination before it stops trusting the native navigation, in ticks (Necromancy IV
     * round, the marching fix).
     *
     * <p>A dragon that has not moved {@value #STUCK_PROGRESS} blocks in two seconds is not walking anywhere - it is
     * standing behind a hill, inside a ravine, on a leaf or in a doorway its own path finder can not solve - and that
     * is the one case in which the straight line of {@link IceAndFireSupport#straightLine} takes over.
     */
    private static final int STUCK_TICKS = 40;

    /** And how far it has to move in that time to count as on its way. */
    private static final double STUCK_PROGRESS = 2.0D;

    /** For how many ticks the straight line is flown before the native navigation is trusted again. */
    private static final int STRAIGHT_TICKS = 40;

    /** How far a bombardment stands away from a point it can not hover over, at the most. */
    private static final double GROUND_STANDOFF = 6.0D;

    /** How far below a point a place to stand under it is looked for, in blocks. */
    private static final int SPOT_SEARCH = 8;

    /** How many places are tried per ring before a bombardment gives up and breathes from where it stands. */
    private static final int SPOT_ATTEMPTS = 8;

    /** How often the sky above a bombardment is looked at again, in ticks. */
    private static final int SKY_RECHECK_TICKS = 20;

    private final Mob minion;
    private int repath;
    private int elapsed;
    private int breathTicks;
    private int watchedTicks;
    private int straightTicks;
    private int skyTicks;
    private boolean openSky;
    private Vec3 watched;

    public NecromancyMoveGoal(Mob minion)
    {
        this.minion = minion;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return errand() != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return errand() != null;
    }

    @Override
    public void start()
    {
        this.repath = 0;
        this.elapsed = 0;
        this.breathTicks = 0;
        this.watched = null;
        this.watchedTicks = 0;
        this.straightTicks = 0;
        this.skyTicks = 0;
        this.openSky = false;

        // An errand always means "on your feet": a skeleton that was lying on its post is stood up before it is sent
        // anywhere, because the mod derives "may this animal move" from that very command.
        IceAndFireSupport.setSitting(this.minion, false);
    }

    @Override
    public void stop()
    {
        // The path is dropped, so a dragon that is given a new order, that loses its contract or whose errand simply
        // ends stops walking on the spot instead of finishing a path nobody asked for any more. The flight state is
        // deliberately left alone: whether an animal of Ice and Fire lands, hovers or keeps flying is its own business
        // and its own logic decides it, exactly as it does for every other dragon of the mod.
        this.minion.getNavigation().stop();
        IceAndFireSupport.stopBreath(this.minion);
    }

    /** @return the errand of that dragon, or null when it has none (which also ends the goal). */
    private NecromancyContract errand()
    {
        NecromancyContract.Order order = NecromancyOrders.orderOf(this.minion);

        if (order != NecromancyContract.Order.MOVE && order != NecromancyContract.Order.BOMBARD)
        {
            return null;
        }

        if (!this.minion.isAlive() || this.minion.isRemoved())
        {
            return null;
        }

        if (this.minion.isVehicle())
        {
            // The queen is on its back: the errand is <b>frozen</b> for as long as she rides it and picks up again the
            // moment she gets off, because the errand stays on the contract. A ridden dragon is steered by its rider -
            // that is what the riding hook of this round establishes - so an AI that kept walking it would be fighting
            // her hands on the controls.
            return null;
        }

        NecromancyContract contract = NecromancyOrders.contractOf(this.minion).orElse(null);

        return contract != null && contract.position() != null ? contract : null;
    }

    @Override
    public void tick()
    {
        NecromancyContract contract = errand();

        if (contract == null || contract.position() == null)
        {
            return;
        }

        Vec3 centre = Vec3.atCenterOf(contract.position());

        if (contract.order() == NecromancyContract.Order.MOVE)
        {
            NecromancyDiagnostics.stateChanged(this.minion,
                    () -> "MARCH: " + IceAndFireSupport.describeState(this.minion));
            marchTo(centre);

            return;
        }

        NecromancyDiagnostics.stateChanged(this.minion,
                () -> "BOMBARD " + contract.phase() + ": " + IceAndFireSupport.describeState(this.minion));
        bombard(contract, centre);
    }

    /**
     * One tick of a march: the destination is handed over - to the native navigation while the dragon is on its way,
     * and to the straight line of the round when it is not walking anywhere - and the errand ends the moment it is
     * reached.
     */
    private void marchTo(Vec3 centre)
    {
        if (arrived(centre, ARRIVE_DISTANCE))
        {
            IceAndFireSupport.holdStill(this.minion);
            NecromancyOrders.clearOrder(this.minion);

            return;
        }

        if (++this.elapsed > GIVE_UP_TICKS)
        {
            // A destination that can not be reached - a wall, a hole, another dimension - is dropped instead of being
            // tried forever: the dragon stands still again and the queen can send it somewhere else.
            NecromancyOrders.clearOrder(this.minion);

            return;
        }

        sendTo(centre);
    }

    /**
     * Hands one destination to the dragon: {@link IceAndFireSupport#approach} decides between the walk and the flight,
     * and {@link IceAndFireSupport#straightLine} is called whenever the native navigation has been making no progress
     * at all.
     *
     * <p>It is deliberately a switch and not a mixture: every tick exactly one of the two is in charge, the navigation
     * for at least {@value #REPATH_TICKS} ticks and the straight line for exactly {@value #STRAIGHT_TICKS}, so a goal
     * is never fighting its own two hands for the same entity.
     */
    private void sendTo(Vec3 destination)
    {
        if (this.straightTicks > 0)
        {
            this.straightTicks--;
            IceAndFireSupport.straightLine(this.minion, destination, SPEED);

            return;
        }

        watchProgress();

        if (this.repath-- <= 0)
        {
            IceAndFireSupport.approach(this.minion, destination, SPEED);
            this.repath = REPATH_TICKS;
        }
    }

    /**
     * Watches whether the dragon is getting anywhere, and asks for the straight line when it plainly is not: the spot
     * it stood on {@value #STUCK_TICKS} ticks ago is compared with the spot it stands on now.
     */
    private void watchProgress()
    {
        Vec3 now = this.minion.position();

        if (this.watched == null || this.watched.distanceToSqr(now) > STUCK_PROGRESS * STUCK_PROGRESS)
        {
            this.watched = now;
            this.watchedTicks = 0;

            return;
        }

        if (++this.watchedTicks > STUCK_TICKS)
        {
            this.watched = null;
            this.watchedTicks = 0;
            this.straightTicks = STRAIGHT_TICKS;
        }
    }

    /** One tick of a bombardment, in the phase the contract carries. */
    private void bombard(NecromancyContract contract, Vec3 centre)
    {
        switch (contract.phase())
        {
            case TRAVELLING:
                travelForBombardment(centre);
                break;

            case AIMING:
                aim(centre);
                break;

            case BREATHING:
                breathe(contract, centre);
                break;
        }
    }

    /**
     * The way there, in the mode the point itself decides (Necromancy IV round, the bombardment fix).
     *
     * <p>The round asked for two shapes of the same attack, and which one is used is a fact about the world above the
     * point and about nothing else:
     *
     * <ul>
     * <li><b>open sky</b> - {@value NecromancyOrders#SKY_HEIGHT} blocks of air above it - the dragon flies to a point
     * {@value NecromancyOrders#BOMBARD_HEIGHT} blocks above the target, hovers there and breathes straight down,</li>
     * <li><b>a roof</b> - anything at all in that column - it walks and lands as close to the point as it can stand,
     * stays on its feet and breathes from the ground, which is the only way a skeleton can burn something under an
     * overhang.</li>
     * </ul>
     *
     * <p>Both modes end in the same aiming phase, and both are travelled with the native navigation of the mod (with
     * the straight line of {@link #sendTo} as the fallback), so nothing about the flight of that mod is invented here.
     */
    private void travelForBombardment(Vec3 centre)
    {
        if (!IceAndFireSupport.canBreathe(this.minion))
        {
            // Too young to breathe: the order is dropped here, where the real stage of the dragon is known.
            IceAndFireSupport.warnTooYoungToBreathe(this.minion);
            NecromancyOrders.clearOrder(this.minion);

            return;
        }

        if (this.skyTicks-- <= 0)
        {
            // The sky is looked at once a second rather than every tick: it is a question about twenty five blocks of
            // the world, and a queen who digs a roof over a bombardment while it is on its way is answered by the
            // second tick of it, not by the first.
            this.openSky = NecromancyOrders.skyOpen(this.minion.level(), BlockPos.containing(centre));
            this.skyTicks = SKY_RECHECK_TICKS;
        }

        Vec3 post = this.openSky
                ? centre.add(0.0D, NecromancyOrders.BOMBARD_HEIGHT, 0.0D)
                : groundPost(centre);

        if (this.openSky ? arrived(post, ARRIVE_DISTANCE) : standingAt(post))
        {
            if (this.openSky)
            {
                IceAndFireSupport.hover(this.minion);
            }

            IceAndFireSupport.holdStill(this.minion);
            NecromancyOrders.writePhase(this.minion, NecromancyContract.Phase.AIMING);
            this.elapsed = 0;

            return;
        }

        if (++this.elapsed > GIVE_UP_TICKS)
        {
            NecromancyOrders.clearOrder(this.minion);

            return;
        }

        sendTo(post);
    }

    /**
     * @return where a bombardment stands when the point it was sent at has a roof over it: on the ground, as close to
     *         that point as a dragon can put its feet - the ring is walked outwards so that a point in the middle of a
     *         cave is approached from the cave floor and not from the wall.
     */
    private Vec3 groundPost(Vec3 centre)
    {
        Vec3 here = standingSpot(centre);

        if (here != null)
        {
            return here;
        }

        for (double radius = 2.0D; radius <= GROUND_STANDOFF; radius += 2.0D)
        {
            for (int attempt = 0; attempt < SPOT_ATTEMPTS; attempt++)
            {
                double angle = this.minion.getRandom().nextDouble() * Math.PI * 2.0D;
                Vec3 candidate = standingSpot(
                        centre.add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius));

                if (candidate != null)
                {
                    return candidate;
                }
            }
        }

        return centre;
    }

    /** @return true when the dragon really stands on the ground at (or beside) that spot. */
    private boolean standingAt(Vec3 post)
    {
        return this.minion.onGround()
                && this.minion.position().distanceToSqr(post) <= GROUND_STANDOFF * GROUND_STANDOFF;
    }

    /** @return the first spot at that point a dragon could stand on: free air with a floor under it, or null. */
    @Nullable
    private Vec3 standingSpot(Vec3 point)
    {
        BlockPos from = BlockPos.containing(point);

        for (int depth = 0; depth <= SPOT_SEARCH; depth++)
        {
            BlockPos candidate = from.below(depth);

            if (this.minion.level().isEmptyBlock(candidate) && this.minion.level().isEmptyBlock(candidate.above())
                    && !this.minion.level().isEmptyBlock(candidate.below()))
            {
                return Vec3.atBottomCenterOf(candidate);
            }
        }

        return null;
    }

    /**
     * A moment of standing or hovering still and turning the head, which is what gives the breath its muzzle.
     *
     * <p>The order the mode of the bombardment has decided is kept: a skeleton with open sky above it hovers, one under
     * a roof keeps its feet on the ground, and both of them are held in place by {@link IceAndFireSupport#holdStill}
     * rather than walking into their own aim.
     */
    private void aim(Vec3 centre)
    {
        if (this.openSky)
        {
            IceAndFireSupport.hover(this.minion);
        }

        IceAndFireSupport.holdStill(this.minion);
        this.minion.getLookControl().setLookAt(centre.x, centre.y, centre.z, 30.0F, 30.0F);

        if (++this.elapsed > AIM_TICKS)
        {
            NecromancyOrders.writePhase(this.minion, NecromancyContract.Phase.BREATHING);
            this.breathTicks = 0;
        }
    }

    /** The stream itself: the native breath of the dragon, at the point a queen chose, tick after tick. */
    private void breathe(NecromancyContract contract, Vec3 centre)
    {
        if (this.openSky)
        {
            IceAndFireSupport.hover(this.minion);
        }

        // Breathing is a still business: a dragon that walked while it spat fire would drag its own stream across the
        // countryside, and the round asks for a muzzle that stays where it was aimed.
        IceAndFireSupport.holdStill(this.minion);
        this.minion.getLookControl().setLookAt(centre.x, centre.y, centre.z, 30.0F, 30.0F);

        if (this.breathTicks % BREATH_SOUND_TICKS == 0)
        {
            IceAndFireSupport.breathSound(this.minion);
        }

        this.breathTicks++;

        if (!IceAndFireSupport.breatheAt(this.minion, centre))
        {
            // The dragon lost the ability to breathe mid bombardment (a gate of Ice and Fire, a changed config): the
            // order is dropped instead of being retried forever.
            IceAndFireSupport.stopBreath(this.minion);
            NecromancyOrders.clearOrder(this.minion);
        }
    }

    /** @return true when that point is close enough to count as reached. */
    private boolean arrived(Vec3 point, double distance)
    {
        return this.minion.position().distanceToSqr(point) <= distance * distance;
    }
}
