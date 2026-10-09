package spiderqueenhungrycraft.compat.iceandfire.ai;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;

/**
 * Where a contract dragon belongs when it has no errand (Necromancy IV round, the brood stances on a skeleton).
 *
 * <p>It is the counterpart of the goals a spider minion walks its orders with, and it means the very same four things:
 *
 * <ul>
 * <li>{@link SpiderOrderMode#FOLLOW} - <b>with its queen</b>. It walks after her once she is more than
 * {@value #FOLLOW_START_DISTANCE} blocks away and stands still again within {@value #FOLLOW_STOP_DISTANCE}, and a queen
 * who got hopelessly far away pulls her skeleton to her with the same emergency teleport a pet spider uses. A dragon
 * that is close enough is left to its own devices on purpose, so the animal still wanders and still behaves like the
 * animal it is,</li>
 * <li>{@link SpiderOrderMode#HOLD} - <b>on its post</b>. It returns to the anchor whenever it left it, and once it is
 * there it <b>lies down</b>, which is the sit pose of the mod itself: the very pose a tamed dragon takes when its owner
 * tells it to stay. It gets up again for a fight and lies down again when the fight is over,</li>
 * <li>{@link SpiderOrderMode#PATROL} - <b>walking the rounds</b> around the anchor, attacking what a spider of that
 * queen would attack while it walks (see {@link NecromancyHunting}),</li>
 * <li>{@link SpiderOrderMode#FREE_ATTACK} - <b>hunting</b> in the neighbourhood of the anchor: it chases whatever its
 * hunting rule found and roams the rounds when there is nothing, exactly like a hunting spider.</li>
 * </ul>
 *
 * <p>Like the marching goal it owns the <b>move flag</b> of the entity while it runs, which is what keeps the wander of
 * Ice and Fire out of the way of a post or a hunt - and, like every goal of this compatibility, it names no Ice and
 * Fire type: the two dragon specific steps (the sit pose and the teleport) go through {@link IceAndFireSupport}.
 */
public final class NecromancyStanceGoal extends Goal
{
    /** How far a follower lets its queen walk before it follows her. */
    private static final double FOLLOW_START_DISTANCE = 16.0D;

    /** And how close it walks before it stops again. */
    private static final double FOLLOW_STOP_DISTANCE = 8.0D;

    /** Beyond this the walking is given up and the skeleton is pulled to its queen instead. */
    private static final double TELEPORT_DISTANCE = 48.0D;

    /** How far a holding dragon may stray from its post before it is called back to it. */
    private static final double HOLD_RADIUS = 6.0D;

    /**
     * How far from its post a holding dragon may answer a fight (Necromancy IV round).
     *
     * <p>Inside it the skeleton walks at its victim and comes home again afterwards, which is what the spiders of a
     * garrison do; beyond it the victim is let go of, because a post is a post.
     */
    private static final double LEASH = 24.0D;

    /** The rounds of a patrol, and of a hunt without a victim: how far from the anchor they reach. */
    private static final double ROAM_RADIUS = 12.0D;

    /** How often the destination is handed to the navigation again, in ticks. */
    private static final int REPATH_TICKS = 20;

    /** A roaming destination is picked when it was reached, or after this many ticks at the latest. */
    private static final int ROAM_TICKS = 120;

    /** How often a follower may try its emergency teleport at most, in ticks. */
    private static final int TELEPORT_COOLDOWN_TICKS = 40;

    /** How many places around a target are tried before one attempt is given up. */
    private static final int TELEPORT_ATTEMPTS = 12;

    private final Mob minion;
    private int repath;
    private int roamTicks;
    private int teleportCooldown;
    private Vec3 destination;

    public NecromancyStanceGoal(Mob minion)
    {
        this.minion = minion;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return wantsToMove();
    }

    @Override
    public boolean canContinueToUse()
    {
        return wantsToMove();
    }

    @Override
    public void start()
    {
        this.repath = 0;
        this.roamTicks = 0;
        this.destination = null;

        // Taking the turn means standing up: whatever the dragon was doing before - lying on a post of a stance that is
        // no longer the one on the contract, or sitting out a fight that is over - it is on its feet while this goal
        // runs. The pose is written by this goal when it holds a post (see #hold) and by nobody else.
        IceAndFireSupport.setSitting(this.minion, false);
    }

    @Override
    public void stop()
    {
        this.minion.getNavigation().stop();
        this.destination = null;

        // The pose of a post is deliberately <b>not</b> cleared here (Necromancy IV round, the HOLD fix). This goal
        // stops for a dozen reasons that have nothing to do with a post being given up - a follower that came close
        // enough, a hunter with nothing to chase, a fight that is over - and standing the dragon up on every one of them
        // was why a dragon that had just lain down on its post was on its feet again on the very next tick. Every real
        // change of order clears the pose where the order is written instead: NecromancyOrders#setStance when a post is
        // given up, the marching goal when an errand arrives, and the ending of a contract when the contract is over.
    }

    /** @return the stance of that dragon when it is theirs to walk, or null when an errand outranks it. */
    private SpiderOrderMode stanceState()
    {
        NecromancyContract contract = NecromancyOrders.contractOf(this.minion).orElse(null);

        if (contract == null || contract.order() != NecromancyContract.Order.NONE)
        {
            return null;
        }

        if (!this.minion.isAlive() || this.minion.isRemoved())
        {
            return null;
        }

        return contract.stance();
    }

    /** @return true when the stance really asks for a movement this tick (a follower that is close enough does not). */
    private boolean wantsToMove()
    {
        SpiderOrderMode stance = stanceState();

        if (stance == null)
        {
            return false;
        }

        if (this.minion.isVehicle())
        {
            // The queen is on its back: everything the contract would have the skeleton do - walking, roaming, hunting -
            // is hers to do with the reins for as long as she rides it, and the stance is frozen rather than given up
            // (it lives on the contract, so it is still there the moment she steps off).
            return false;
        }

        if (stance == SpiderOrderMode.HOLD)
        {
            // A post is kept at all times: the goal stays on watch even when the animal is standing right on it, which
            // is what holds the wander of the mod away from a garrison.
            return true;
        }

        if (stance == SpiderOrderMode.FOLLOW)
        {
            LivingEntity owner = owner();

            if (owner == null)
            {
                return false;
            }

            // Two reasons to follow, and the second one is round 4's answer to "sometimes it just will not come": the
            // queen is far enough to walk after her, or the skeleton can not see her from where it stands - which is
            // the case of a dragon that stopped five blocks away on the wrong side of a wall and then stood there,
            // waiting, while the order was "with your queen".
            // The third reason is round 6's: a flight the CONTRACT started is still in the air, and finishing it (which
            // means landing) is a job this goal has to stay awake for.
            return this.minion.distanceToSqr(owner) > FOLLOW_START_DISTANCE * FOLLOW_START_DISTANCE
                    || !this.minion.hasLineOfSight(owner)
                    || IceAndFireSupport.contractFlight(this.minion);
        }

        // A patrol walks its rounds and a hunt works its neighbourhood: both are busy by definition, and both chase a
        // victim of their own if there is one.
        return true;
    }

    @Override
    public void tick()
    {
        SpiderOrderMode stance = stanceState();

        if (stance == null)
        {
            return;
        }

        if (stance == SpiderOrderMode.FOLLOW)
        {
            follow();
        }
        else if (stance == SpiderOrderMode.HOLD)
        {
            hold();
        }
        else
        {
            roamOrChase();
        }
    }

    /** The follower: walk after the queen, and be pulled to her when walking can not catch up any more. */
    private void follow()
    {
        LivingEntity owner = owner();

        if (owner == null)
        {
            return;
        }

        IceAndFireSupport.setSitting(this.minion, false);

        if (this.minion.distanceToSqr(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE && this.teleportCooldown-- <= 0)
        {
            this.teleportCooldown = TELEPORT_COOLDOWN_TICKS;

            if (teleportNear(owner))
            {
                return;
            }
        }

        if (this.minion.distanceToSqr(owner) <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE
                && this.minion.hasLineOfSight(owner))
        {
            if (IceAndFireSupport.contractFlight(this.minion))
            {
                // Standing by its queen but still in the air, and it is the contract that put it there: finishing that
                // flight is not an order, it is the end of one (Necromancy IV round 6). The walk/fly steering of the
                // contract brings it down to the ground under her and closes the flight the moment it really stands -
                // which is what keeps a dragon from being left hovering next to its queen with nobody driving it, the
                // one state in which Ice and Fire's own logic and the contract can take turns putting it up and down.
                IceAndFireSupport.approach(this.minion, owner.position(), 1.0D);
                NecromancyDiagnostics.stateChanged(this.minion,
                        () -> "FOLLOW landing: " + IceAndFireSupport.describeState(this.minion));

                return;
            }

            this.minion.getNavigation().stop();

            return;
        }

        repathTowards(owner.position());
    }

    /** The garrison: land first, then come home, a fight answered from the post, and down on it when there is none. */
    private void hold()
    {
        BlockPos anchor = anchor();

        if (anchor == null)
        {
            return;
        }

        Vec3 post = Vec3.atBottomCenterOf(anchor);
        LivingEntity victim = this.minion.getTarget();

        if (!this.minion.onGround())
        {
            // A post is a place on the ground, so a skeleton in the air has not arrived at it yet however close its
            // coordinates are (Necromancy IV round 5). It is brought down by the very same walk/fly steering every
            // other destination uses, on its feet - and the sitting pose is written only once it really stands, because
            // the pose would not even ramp while the animal is flying or hovering: Ice and Fire raises sleepProgress
            // for a dragon that is neither.
            IceAndFireSupport.setSitting(this.minion, false);
            repathTowards(post);
            NecromancyDiagnostics.stateChanged(this.minion,
                    () -> "HOLD landing: " + IceAndFireSupport.describeState(this.minion));

            return;
        }

        if (victim != null && victim.isAlive() && withinLeash(anchor, victim))
        {
            // A fight is answered on the feet and from the post: the skeleton walks at the victim while it stays near
            // the post, and the native breath of the mod fires at whatever the target of the dragon is, walking or
            // standing. It is the shape a garrison of spiders has - chase, then come home - and a victim that runs
            // beyond the leash is let go of just below.
            IceAndFireSupport.setSitting(this.minion, false);
            repathTowards(victim.position());
            NecromancyDiagnostics.stateChanged(this.minion,
                    () -> "HOLD fighting: " + IceAndFireSupport.describeState(this.minion));

            return;
        }

        if (victim != null && victim.isAlive())
        {
            // Out of the leash: a post is a post, and a skeleton that followed its prey across the world would be a
            // hunter and not a garrison.
            this.minion.setTarget(null);
        }

        if (this.minion.position().distanceToSqr(post) > HOLD_RADIUS * HOLD_RADIUS)
        {
            IceAndFireSupport.setSitting(this.minion, false);
            repathTowards(post);
            NecromancyDiagnostics.stateChanged(this.minion,
                    () -> "HOLD walking home: " + IceAndFireSupport.describeState(this.minion));

            return;
        }

        this.minion.getNavigation().stop();

        // At the post and nothing to fight: down. It is the very pose the mod gives a dragon that was told to stay, and
        // it takes both halves of it to make it happen - the command and the pose itself (see
        // IceAndFireSupport#setSitting, which explains why one of them is not enough for a skeleton).
        IceAndFireSupport.setSitting(this.minion, true);
        NecromancyDiagnostics.stateChanged(this.minion,
                () -> "HOLD posted (lying down): " + IceAndFireSupport.describeState(this.minion));
    }

    /** @return true when both the dragon and that victim are inside the leash of that post. */
    private boolean withinLeash(BlockPos anchor, LivingEntity victim)
    {
        double x = anchor.getX() + 0.5D;
        double y = anchor.getY();
        double z = anchor.getZ() + 0.5D;

        return this.minion.distanceToSqr(x, y, z) <= LEASH * LEASH && victim.distanceToSqr(x, y, z) <= LEASH * LEASH;
    }

    /** A patrol on its rounds, and a hunt that chases what its targeting rule found. */
    private void roamOrChase()
    {
        IceAndFireSupport.setSitting(this.minion, false);

        LivingEntity victim = this.minion.getTarget();

        if (victim != null && victim.isAlive())
        {
            // A victim of the hunt, or of a patrol: the skeleton walks after it. The attack itself - the claws, the bite
            // and the breath - is the native AI of the mod, which picks the fight up as soon as the animal is close
            // enough.
            this.destination = null;
            repathTowards(victim.position());

            return;
        }

        Vec3 target = this.destination;

        if (target == null || this.minion.position().distanceToSqr(target) <= 4.0D || this.roamTicks-- <= 0)
        {
            target = roamDestination();
            this.destination = target;
            this.roamTicks = ROAM_TICKS;
        }

        if (target != null)
        {
            repathTowards(target);
        }
    }

    /** @return the next place of the rounds: a random spot in the neighbourhood of the anchor, or null. */
    @Nullable
    private Vec3 roamDestination()
    {
        BlockPos base = anchor() != null ? anchor() : this.minion.blockPosition();

        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++)
        {
            double angle = this.minion.getRandom().nextDouble() * Math.PI * 2.0D;
            double radius = ROAM_RADIUS * Math.sqrt(this.minion.getRandom().nextDouble());
            int x = base.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = base.getZ() + (int) Math.round(Math.sin(angle) * radius);
            int y = base.getY() + this.minion.getRandom().nextInt(5) - 2;

            if (this.minion.level().isEmptyBlock(new BlockPos(x, y, z)))
            {
                return new Vec3(x + 0.5D, y, z + 0.5D);
            }
        }

        return null;
    }

    /** Hands one destination to the native navigation of the dragon, at a sane rate. */
    private void repathTowards(Vec3 target)
    {
        if (this.repath-- > 0)
        {
            return;
        }

        this.repath = REPATH_TICKS;

        // The walk and the flight are one decision and it is not taken here: Necromancy IV round sends a skeleton that is
        // close by on foot and one that is far away into the air, which is why a follower that is thirty blocks away
        // arrives over the hill instead of walking into it.
        IceAndFireSupport.approach(this.minion, target, 1.0D);
    }

    /**
     * Pulls one dragon to a free spot beside its queen (see {@link NecromancyOrders#pullNear}), which is the same
     * emergency move the hard rally of the sceptre uses.
     *
     * @return true when the dragon really arrived
     */
    private boolean teleportNear(LivingEntity owner)
    {
        boolean arrived = NecromancyOrders.pullNear(this.minion, owner);

        if (arrived)
        {
            this.destination = null;
        }

        return arrived;
    }

    /** @return the queen who holds the contract of that dragon, or null when she is not in the world. */
    @Nullable
    private LivingEntity owner()
    {
        return NecromancyService.contractOwner(this.minion)
                .map(owner -> this.minion.level().getPlayerByUUID(owner)).orElse(null);
    }

    /** @return the post of that contract, or null when it carries none. */
    @Nullable
    private BlockPos anchor()
    {
        return NecromancyOrders.contractOf(this.minion).map(NecromancyContract::anchor).orElse(null);
    }
}
