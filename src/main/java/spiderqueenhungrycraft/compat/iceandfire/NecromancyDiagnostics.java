package spiderqueenhungrycraft.compat.iceandfire;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The diagnostics of the dragon compatibility (Necromancy IV round 5): a few lines per <b>event</b>, and never a line per
 * tick.
 *
 * <p><b>Why it exists.</b> Every bug of the last three rounds had the same shape: a native mechanism of Ice and Fire
 * that a skeleton under a contract never reaches, invisible except in the behaviour of the animal. Reading the code did
 * not find them; watching a <i>handful of well chosen lines</i> in a live log did - and one of those lines (a failed
 * Mixin application) had been sitting in the log of every launch, unread. This class is that lesson written down: when
 * the switch is on, the important steps of a skeleton's life say so in the log, once each.
 *
 * <p><b>What it never does.</b>
 *
 * <ul>
 * <li>it never writes on a tick boundary - a state line is written the first time the state is seen and then only when
 * it really <b>changes</b> ({@link #stateChanged}), which is what makes "it was hovering, then it landed" visible
 * without drowning the file,</li>
 * <li>it never touches the world: it formats a string and hands it to the logger,</li>
 * <li>and it is off unless {@code compat.necromancy_diagnostics} is true, so a normal game is exactly as quiet as it was
 * before this class existed.</li>
 * </ul>
 *
 * <p>Everything it writes carries {@link IceAndFireSupport#PROBE_LOG_PREFIX}, which is what makes the output of a
 * reproduction findable in one search of the log afterwards.
 */
public final class NecromancyDiagnostics
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The last state string written for one entity, so that only a real change is written again. */
    private static final Map<Object, String> LAST_STATE = new WeakHashMap<>();

    /** The keys {@link #once} has already written, for the facts that are worth one line in a whole session. */
    private static final Set<String> SAID_ONCE = ConcurrentHashMap.newKeySet();

    /**
     * Whether the one line about the riding hook has been written in this game. See
     * {@link #announceRiderHookApplied()}: it is deliberately <b>not</b> behind the diagnostics switch.
     */
    private static final AtomicBoolean RIDER_HOOK_ANNOUNCED = new AtomicBoolean();

    private NecromancyDiagnostics()
    {
    }

    /** @return true when the diagnostics are switched on in the configuration. */
    public static boolean enabled()
    {
        return HungryCraftConfig.necromancyDiagnostics();
    }

    /**
     * Writes one event, with the probe marker in front of it.
     */
    public static void log(String message)
    {
        if (enabled())
        {
            LOGGER.info("{} [diag] {}", IceAndFireSupport.PROBE_LOG_PREFIX, message);
        }
    }

    /**
     * The same line, for a message that costs something to build (round 6): the text is not put together at all while
     * the switch is off, so "off" really is free even on a path that is walked often.
     */
    public static void log(java.util.function.Supplier<String> message)
    {
        if (enabled())
        {
            log(message.get());
        }
    }

    /**
     * Writes one event exactly once per key, however often it is asked to.
     *
     * <p>It is the tool for the two facts that a log has to be able to state positively but that are reached on a hot
     * path: that the riding hook of the compatibility is <b>applied</b> in this game (the moment the game first asks a
     * dragon who controls it), and that it really handed a rider over. Both are worth one line each and neither is
     * worth a line per tick.
     */
    public static void once(String key, String message)
    {
        if (enabled() && SAID_ONCE.add(key))
        {
            LOGGER.info("{} [diag] {}", IceAndFireSupport.PROBE_LOG_PREFIX, message);
        }
    }

    /**
     * Writes one line exactly once per key, <b>whether or not</b> the diagnostics switch is on.
     *
     * <p>It is for the facts that prove an optional compatibility hook really runs in this game, exactly like
     * {@link #announceRiderHookApplied}: the autonomy gates of the necromancy round are applied by Mixin, and "applied"
     * is not "did intercept a contract dragon". A gate writes this line the first time it actually intercepts one, and
     * a gate that never fires writes nothing - so a missing line is itself the diagnosis the round asks for (the gate did
     * not match, or no contract dragon ever reached it).
     */
    public static void announce(String key, String message)
    {
        if (SAID_ONCE.add(key))
        {
            LOGGER.info("{} {}", IceAndFireSupport.PROBE_LOG_PREFIX, message);
        }
    }

    /**
     * Writes the one line that proves the riding hook of this compatibility is really applied in this game, and writes
     * it <b>whether or not</b> the diagnostics switch is on.
     *
     * <p>It is the only exception to "the diagnostics are off by default", and it is deliberate: this hook once failed
     * to apply in a live client and the evidence sat unread in the log for a whole session, because the line that
     * reported it was a Mixin warning in the middle of a startup - and there was no line at all that said "the hook is
     * fine". One line per launch, written from inside the injected method itself (so it can only appear if the
     * injection really happened), and free after the first call.
     */
    public static void announceRiderHookApplied()
    {
        if (RIDER_HOOK_ANNOUNCED.compareAndSet(false, true))
        {
            LOGGER.info("{} riding hook applied: EntityDragonBase#getControllingPassenger is answered by this mod too",
                    IceAndFireSupport.PROBE_LOG_PREFIX);
        }
    }

    /**
     * Writes one line about a piece of state that <b>this mod</b> just wrote, and names the code that wrote it.
     *
     * <p>It is the other half of {@link #stateChanged}: together they answer "who changed this" without a debugger. The
     * watcher ({@link #stateChanged}, driven from the contract clock) says what the state of a dragon is at every moment
     * it really changes; this method says which of our own decisions changed it. A change in the watcher that no
     * {@code wrote} line precedes is therefore a change made by <b>somebody else</b> - and in this compatibility that
     * "somebody else" is Ice and Fire's own AI, which is exactly what a control-contention hunt has to see.
     *
     * <p>For an entity the owner is keyed by identity, and the line carries the entity's UUID so it can be told from a
     * second skeleton in the same log.
     */
    public static void write(Object owner, String whatChanged, String source)
    {
        if (enabled())
        {
            String who = owner instanceof net.minecraft.world.entity.Entity entity
                    ? entity.getUUID().toString()
                    : String.valueOf(owner);

            LOGGER.info("{} [diag] wrote {} for {} ({})", IceAndFireSupport.PROBE_LOG_PREFIX, whatChanged, who, source);
        }
    }

    /**
     * Writes one line whenever the described state of that owner really changes.
     *
     * <p>It is the rate limiter of this class, and it is a stricter one than "every N ticks": a state that does not
     * change writes nothing at all, so a skeleton that hovers for five minutes costs one line and a skeleton that lands,
     * fights and takes off again costs three.
     *
     * @param owner the thing the state belongs to (an entity, or its UUID), used as the key of the memory
     * @param state what is true right now, in a form a human can read
     */
    public static void stateChanged(Object owner, String state)
    {
        if (!enabled())
        {
            return;
        }

        String previous = LAST_STATE.put(owner, state);

        if (!state.equals(previous))
        {
            LOGGER.info("{} [diag] {}", IceAndFireSupport.PROBE_LOG_PREFIX, state);
        }
    }

    /**
     * The same watcher, for a state line that costs something to build.
     *
     * <p>The description of a dragon of the compatibility reads its contract, its running goals, its navigator and its
     * move control, and building it on every tick of a goal would be a waste in a mod whose diagnostics are supposed to
     * be free while they are off. With this overload the supplier is not called at all unless the switch is on, so the
     * "off" case costs one boolean read - and the goals of `NecromancyMoveGoal` and `NecromancyStanceGoal` can stay as
     * talkative as the round wants them to be while they are on.
     *
     * @param owner the thing the state belongs to, used as the key of the memory
     * @param state built only when the diagnostics are on
     */
    public static void stateChanged(Object owner, java.util.function.Supplier<String> state)
    {
        if (enabled())
        {
            stateChanged(owner, state.get());
        }
    }

    /** Forgets the remembered state of one owner, so that its next state is written again whatever it is. */
    public static void forget(Object owner)
    {
        LAST_STATE.remove(owner);
    }
}
