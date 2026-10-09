package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.attack.ShieldBlockFeedback;
import spiderqueenhungrycraft.attack.ShieldProjectileInteraction;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The Instant Projectile Guard of the Sweatlord (PvP round): the reflex that is his and nobody else's.
 *
 * <p><b>The bug it replaces.</b> The guard of the last round went up while an enemy still <i>held</i> a shot - a drawn
 * bow, a charged crossbow, a throwable in the hand - because the game only lets a shield block after five ticks of use
 * and a shot from close range therefore can not be answered once it is in the air. That reasoning was right and the
 * rule was still wrong: a player who draws a bow and never releases it parked him behind a shield for as long as they
 * held the string, which is a bow that controls a body without shooting. <b>That behaviour is gone.</b> This guard only
 * ever reacts to a <b>real projectile in the world</b> that is on a collision course with his own body box, whatever the
 * enemy is holding.
 *
 * <p><b>What makes it instant.</b> Three things happen in the <b>same tick</b> the threat is confirmed, and none of them
 * waits for the wind up of the game:
 *
 * <ol>
 * <li>the body <b>snaps around</b> - yaw, body yaw and head yaw are written directly, not with a look control that
 * needs several ticks to come about, so a shot from behind is answered with a turn a client can see,</li>
 * <li>a <b>real shield</b> goes into the offhand and the real use state is started, so the renderer, the vanilla
 * pipeline and every other shield rule see a body that really holds a shield up,</li>
 * <li>and the <b>reflex window</b> opens: for the next few ticks this body's shield counts as ready even though the
 * five ticks of {@code LivingEntity#isBlocking} have not passed. That permission lives in
 * {@link SweatlordSteveEntity#isDamageSourceBlocked} and in {@link #allowsInstantBlock}, so it is <b>this body and
 * projectiles only</b> - a normal human, a player, a chad and every other living being keep the vanilla wind up exactly
 * as it is.</li>
 * </ol>
 *
 * <p><b>And it is an overlay, never a lock.</b> The guard is not a skill: it never waits for one to finish, it never
 * holds the shield for longer than {@link #MAX_HOLD_TICKS}, and it drops it {@link #POST_BLOCK_VISUAL_HOLD} ticks after
 * the last threat. Movement is not stopped either - the body keeps the path it had, which is why a projectile reflex
 * looks like a step and a turn and not like a body pinned in place.
 *
 * <p><b>What it covers.</b> Every {@code Projectile} in the world is looked at, by its own position and velocity and
 * its own gravity ({@link ProjectileThreatDetector}): arrows, snowballs, eggs, thrown tridents and the web shots of
 * this mod. Nothing here asks what the shooter is holding.
 *
 * <p><b>What the game can not block for him.</b> The shield path of {@code LivingEntity#hurt} begins with
 * {@code damage > 0}, so an egg, a snowball or a web shot can never enter it. For a damaging shot (an arrow, a thrown
 * trident) this guard does nothing but open the window: the game blocks the hit, takes the shield durability and
 * broadcasts the vanilla feedback. For a harmless one the guard itself answers the impact and broadcasts exactly one
 * feedback through {@link ShieldBlockFeedback}.
 */
public final class InstantProjectileGuard
{
    /** The name this guard reports to the debug state and the tests. */
    public static final String NAME = "projectile_defense";

    /**
     * How many ticks before the estimated impact the shield really goes up.
     *
     * <p>It is a visual budget and not a blocking one - the block is instant - so it is short: four ticks are enough for
     * the turn, the shield and the tick order of a server, and short enough that a shot that is still twenty blocks away
     * never raises anything.
     */
    public static final int REACTION_LEAD_TICKS = 4;

    /**
     * How long the guard stays up after the last threat, which is the visible tail of a block: the client sees a
     * shield that came up, took the hit and went down again, instead of a flicker of one tick.
     */
    public static final int POST_BLOCK_VISUAL_HOLD = 6;

    /**
     * How long after the last confirmed threat an instant block is still allowed.
     *
     * <p>One tick more than the visible hold, because a projectile and this body are two entities of the same server
     * tick and either of them may be ticked first: a shot that was confirmed a moment ago must still find the window
     * open when its own hurt call arrives.
     */
    public static final int REFLEX_GRACE_TICKS = POST_BLOCK_VISUAL_HOLD + 1;

    /** The shortest hold the tests and the debug output speak of, which is the visible tail of a reaction. */
    public static final int MIN_HOLD_TICKS = POST_BLOCK_VISUAL_HOLD;

    /**
     * How close a harmless shot has to be before the guard swats it out of the air.
     *
     * <p>It is answered <b>before</b> it arrives and not when it touches the shield, because a snowball crosses a block
     * and a half in a tick: by the time it is next to the body it is inside the same tick that removes it, and the tick
     * order of a server decides whether the projectile or this body is ticked first. The prediction of the detector is
     * what makes the answer deterministic instead of a coin flip.
     */
    public static final int SWAT_TICKS = 2;

    /** The longest the guard may own the shield in one stretch, whatever the world throws at him: even under a stream of
     * projectiles the body is given back this often, so a reflex can never become the permanent state the bow lock was. */
    public static final int MAX_HOLD_TICKS = 24;

    private boolean active;
    private int heldTicks;
    private int quietTicks;

    /** The tick a threat was last confirmed, which is what the reflex permission is measured against. */
    private long lastThreatTick = Long.MIN_VALUE;

    private int blocked;
    private int lastBlockTick = -1;

    /** The point the body is turned into: the projectile itself while it is coming, its last position afterwards. */
    @Nullable
    private Vec3 lastPoint;

    /** @return the stable name of this guard, for the debug state. */
    public String name()
    {
        return NAME;
    }

    /** @return true while the guard is up and owns the shield. */
    public boolean isActive()
    {
        return this.active;
    }

    /** @return how many harmless impacts this guard answered itself (an egg, a snowball, a web shot). */
    public int blockedProjectiles()
    {
        return this.blocked;
    }

    /** @return the tick of the last projectile this guard answered itself, or -1. */
    public int lastBlockTick()
    {
        return this.lastBlockTick;
    }

    /** @return one short line for the debug state. */
    public String describe()
    {
        return NAME + (this.active ? "(up " + this.heldTicks + ")" : "(idle " + this.blocked + ")");
    }

    /**
     * One tick of the reflex, asked by the brain before every attack and every skill of the kit.
     *
     * @return true while the guard owns the shield of the body for this tick.
     */
    public boolean tick(SweatlordSteveEntity mob)
    {
        ProjectileThreatDetector.Threat threat = ProjectileThreatDetector.worst(mob);
        long now = mob.level().getGameTime();

        if (threat != null && threat.ticksToImpact() <= REACTION_LEAD_TICKS)
        {
            this.lastThreatTick = now;
            this.quietTicks = 0;
            this.lastPoint = threat.projectile().position();

            if (!this.active)
            {
                this.active = true;
                this.heldTicks = 0;
                mob.brain().log(NAME + ": guard up, a shot arrives in " + threat.ticksToImpact() + " ticks");
            }
        }
        else if (this.active && this.quietTicks < POST_BLOCK_VISUAL_HOLD)
        {
            // The tail of a reaction: nothing is coming any more, but the shield stays visible for a moment so a client
            // sees a block and not a flicker.
            ++this.quietTicks;
        }
        else
        {
            return this.release(mob);
        }

        // The snap turn and the real shield, both inside this single tick.
        if (this.lastPoint != null)
        {
            PlayerLikeActions.faceTowards(mob, this.lastPoint);
        }

        PlayerLikeActions.raiseShield(mob);

        // Everything the game can not block for him: an egg, a snowball, a web shot.
        this.answerIncoming(mob);

        ++this.heldTicks;

        if (this.heldTicks >= MAX_HOLD_TICKS)
        {
            mob.brain().log(NAME + ": guard held for the maximum of " + MAX_HOLD_TICKS + " ticks");
            return this.release(mob);
        }

        return this.active;
    }

    /**
     * Answers every shot the game itself can not block, before it lands and one feedback per impact.
     *
     * <p>A damaging shot is left alone on purpose: the hurt pipeline of the game blocks it (this body's reflex window
     * makes the wind up unnecessary), takes the shield durability and broadcasts the vanilla feedback. Doing anything
     * here as well would be a second sound for one block, which is the one thing this round must never produce.
     */
    private void answerIncoming(SweatlordSteveEntity mob)
    {
        if (!PlayerLikeActions.holdsShieldInUse(mob))
        {
            return;
        }

        for (ProjectileThreatDetector.Threat threat : ProjectileThreatDetector.scan(mob))
        {
            if (threat.ticksToImpact() <= SWAT_TICKS && threat.projectile() instanceof Projectile projectile)
            {
                this.answer(mob, projectile);
            }
        }

        // And everything that is already touching him, for a shot the prediction can not see coming.
        for (Projectile projectile : ProjectileThreatDetector.inContact(mob))
        {
            this.answer(mob, projectile);
        }
    }

    /** Answers one shot the game can not block: one feedback for it, and the shot is out of the world. */
    private void answer(SweatlordSteveEntity mob, Projectile projectile)
    {
        if (projectile.isRemoved() || projectile.getOwner() == mob || theGameBlocksItItself(projectile)
                || !ShieldProjectileInteraction.approachesFromTheFront(mob, projectile))
        {
            return;
        }

        if (ShieldBlockFeedback.feedbackOnce(mob))
        {
            ++this.blocked;
            this.lastBlockTick = mob.tickCount;
            mob.brain().log(NAME + ": blocked a " + projectile.getType().toShortString() + " with the shield");
        }

        // The shot is over: an egg, a snowball and a web shot carry no damage, so there is nothing else to do.
        projectile.discard();
    }

    /**
     * @return true when the shield path of the game itself can block that projectile, which needs something to block:
     *         {@code LivingEntity#hurt} only asks the shield when the damage is above zero.
     */
    private static boolean theGameBlocksItItself(Projectile projectile)
    {
        return projectile instanceof AbstractArrow || projectile instanceof ThrownTrident;
    }

    /**
     * @return true when a hit of that source may be blocked by the reflex of this body although the five ticks of a
     *         shield wind up have not passed.
     *
     * <p>Three conditions, all of them narrow on purpose: the window has to be open (a real projectile was confirmed a
     * moment ago), a real shield has to be in use right now, and the hit has to come from a projectile that is not his
     * own and arrives at the half of the world he is facing. A melee swing is not covered - it keeps the wind up of the
     * game like every other body.
     */
    public boolean allowsInstantBlock(SweatlordSteveEntity mob, DamageSource source)
    {
        if (!this.active || mob.level().isClientSide())
        {
            return false;
        }

        if (mob.level().getGameTime() - this.lastThreatTick > REFLEX_GRACE_TICKS)
        {
            return false;
        }

        if (!PlayerLikeActions.holdsShieldInUse(mob))
        {
            return false;
        }

        Entity direct = source.getDirectEntity();

        if (!(direct instanceof Projectile projectile) || projectile.getOwner() == mob)
        {
            return false;
        }

        return ShieldProjectileInteraction.approachesFromTheFront(mob, projectile);
    }

    /**
     * Re-asserts the shield of the guard after something else cleaned up the use state of the body.
     *
     * <p>The stop of a skill drops the item the body was using, which is correct for that skill and wrong for a reflex
     * that is still answering a shot: the brain calls this right after it stopped the running skill, so the shield of
     * the guard is really in the hand of the body on the tick the shot arrives.
     */
    public void hold(SweatlordSteveEntity mob)
    {
        if (!this.active)
        {
            return;
        }

        if (this.lastPoint != null)
        {
            PlayerLikeActions.faceTowards(mob, this.lastPoint);
        }

        PlayerLikeActions.raiseShield(mob);
    }

    /** Ends the reaction once nothing is coming any more. */
    private boolean release(SweatlordSteveEntity mob)
    {
        if (!this.active)
        {
            return false;
        }

        this.active = false;
        this.quietTicks = 0;
        this.heldTicks = 0;
        this.lastPoint = null;
        PlayerLikeActions.stopUsing(mob);
        mob.loadout().equipCombatGrip();
        mob.brain().log(NAME + ": released");

        return false;
    }

    /** Called when the goal gives the body back. */
    public void stop(SweatlordSteveEntity mob)
    {
        if (this.active)
        {
            this.active = false;
            this.quietTicks = 0;
            this.heldTicks = 0;
            this.lastPoint = null;
            this.lastThreatTick = Long.MIN_VALUE;
            PlayerLikeActions.stopUsing(mob);
        }
    }
}
