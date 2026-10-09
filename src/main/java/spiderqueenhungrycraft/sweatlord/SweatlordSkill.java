package spiderqueenhungrycraft.sweatlord;

import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * One reusable piece of the Sweatlord fight (Sweatlord round).
 *
 * <p>The brain never implements a bow, a burst of eggs, a pearl or an axe by itself: it only decides which skill gets
 * the body right now. A skill owns one real action - its own hand switches, its own use state, its own projectile and
 * its own cooldown - and answers the five questions of this class. That split is what keeps the high level state
 * machine small ({@link SweatlordCombatState}) and what makes every single action testable on its own.
 *
 * <p>Cooldown, priority and the "hold the body" lock live here so no skill can forget them.
 */
public abstract class SweatlordSkill
{
    private int cooldown;

    /** @return the stable name of this skill, used by the debug output, the tests and the synced debug state. */
    public abstract String name();

    /** @return the high level state this skill is a part of, which is what the debug state and the tests read. */
    public abstract SweatlordCombatState state();

    /** @return the priority of this skill: the highest ready skill that can start wins. */
    public abstract int priority();

    /** @return how long this skill stays on cooldown after it ran, in ticks. */
    public abstract int cooldownTicks();

    /** @return true when the current situation is the one this skill is for. */
    public abstract boolean canStart(SweatlordSteveEntity mob, CombatContext context);

    /** Takes the body: real hand switches, real use state, real projectile. Called once. */
    public abstract void start(SweatlordSteveEntity mob, CombatContext context);

    /** One tick of the running skill. */
    public abstract void tick(SweatlordSteveEntity mob, CombatContext context);

    /** @return false the moment the action is over; the brain then stops it and picks the next one. */
    public abstract boolean shouldContinue(SweatlordSteveEntity mob, CombatContext context);

    /** Releases everything the action held: use state, navigation, aim. Called exactly once per start. */
    public abstract void stop(SweatlordSteveEntity mob);

    /**
     * @return true while this action has to keep the body even when a higher priority skill becomes possible, because
     *         letting go in the middle would leave the world in a half finished state (a draw without a shot, a pearl
     *         in the air). The brain still lets the projectile defense interrupt end it - that is a safety rule and
     *         never negotiable.
     */
    public boolean holdsBody(SweatlordSteveEntity mob)
    {
        return false;
    }

    /**
     * @return true when this action may take the body away from an action that is already running.
     *
     * <p>The default is no: every action of this round finishes what it started. The one documented exception is the
     * read of a raised shield ({@code MeleeSkills.AxeShieldBreakSkill}), because that answer is worthless if it has to
     * wait for a sword combo that never ends while the enemy keeps his shield up.
     */
    public boolean mayTakeOver(SweatlordSkill running)
    {
        return false;
    }

    /** @return the cooldown of this skill right now, for the debug dump. */
    public final int cooldown()
    {
        return this.cooldown;
    }

    /** @return true when this skill may run right now. */
    public final boolean ready()
    {
        return this.cooldown <= 0;
    }

    /** One tick of bookkeeping, called by the brain for every skill, running or not. */
    public final void tickCooldown()
    {
        if (this.cooldown > 0)
        {
            --this.cooldown;
        }
    }

    /** Puts this skill on its own cooldown, called by the skill when the action really happened. */
    protected final void beginCooldown()
    {
        this.cooldown = this.cooldownTicks();
    }

    /** Puts this skill on an explicit cooldown, for the cases where the situation deserves more than the normal one. */
    protected final void setCooldown(int ticks)
    {
        this.cooldown = Math.max(0, ticks);
    }

    /** Clears the cooldown, used by the encounter reset and by the tests. */
    public final void clearCooldown()
    {
        this.cooldown = 0;
    }

    /** @return one short line about this skill for the debug dump. */
    public String describe(SweatlordSteveEntity mob)
    {
        return this.name() + (this.cooldown > 0 ? "(cd " + this.cooldown + ")" : "");
    }
}
