package spiderqueenhungrycraft.compat.iceandfire;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * What a <b>client</b> knows about the necromancy markers of the server (Necromancy IV prototype 2).
 *
 * <p>It is the client's copy of the answer of {@link NecromancyMarker}, and it has to exist because the marker
 * itself can not travel: Forge's persistent data is written and read from disk and is never part of any entity
 * packet. The server sends {@code S2CNecromancyStatePacket} when a dragon is woken or put back, and again to every
 * player who starts tracking a woken dragon, so this set is rebuilt by itself after a relog, a chunk reload or a
 * dimension change.
 *
 * <p><b>It holds entity ids, not entities.</b> An id is what a packet can carry and what a renderer has in its hand,
 * and a stale id costs a few bytes until the mirror is cleared - the mirror is emptied whenever a world is left, so
 * an id of an older session can never chance upon an unrelated dragon of a new one.
 *
 * <p>It is deliberately free of any client only type: a plain set, a logger. That keeps the class loadable (and
 * testable) on a dedicated server as well, which is exactly what the game tests of this compatibility do.
 */
public final class ClientNecromancyState
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many dragons one client mirrors before the set is dropped and rebuilt by the next packets. */
    private static final int MAX_TRACKED = 512;

    private static final Map<Integer, Mirror> REVIVED = new HashMap<>();

    private ClientNecromancyState()
    {
    }

    /**
     * What one client knows about one woken dragon.
     *
     * <p>{@code revived} is the marker every player is told about, because everybody sees the bones. The rest is told to
     * the <b>owner of the contract</b> alone - it is her sheet, not public knowledge - and it is what her inspect HUD
     * draws: whether the dragon is hers, which standing order it holds, which errand it is on, and whether it is old
     * enough to be sent on a bombardment.
     */
    public record Mirror(boolean revived, boolean mine, int stance, int order, boolean canBreathe)
    {
    }

    /** @return the state this client was told about that entity, or null when it was never told anything. */
    @Nullable
    public static Mirror of(int entityId)
    {
        return REVIVED.get(entityId);
    }

    /** @return true when the server said that HungryCraft has that entity woken up. */
    public static boolean isRevived(int entityId)
    {
        Mirror mirror = REVIVED.get(entityId);

        return mirror != null && mirror.revived();
    }

    /** @return how many dragons this client currently mirrors. */
    public static int size()
    {
        return REVIVED.size();
    }

    /** Writes the plain answer of the server about one entity, keeping whatever else was known about it. */
    public static void set(int entityId, boolean revived)
    {
        Mirror known = REVIVED.get(entityId);

        if (known != null)
        {
            set(entityId, revived, known.mine(), known.stance(), known.order(), known.canBreathe());

            return;
        }

        set(entityId, revived, false, 0, 0, false);
    }

    /** Writes the whole answer of the server about one entity, replacing whatever was known about it before. */
    public static void set(int entityId, boolean revived, boolean mine, int stance, int order, boolean canBreathe)
    {
        if (revived)
        {
            if (REVIVED.size() >= MAX_TRACKED && !REVIVED.containsKey(entityId))
            {
                LOGGER.warn("{} the client mirror is full ({}), dropping the whole set - the server will rebuild it",
                        IceAndFireSupport.PROBE_LOG_PREFIX, MAX_TRACKED);
                REVIVED.clear();
            }

            REVIVED.put(entityId, new Mirror(true, mine, stance, order, canBreathe));
        }
        else
        {
            REVIVED.remove(entityId);
        }

        LOGGER.info("{} client mirror: entity {} revived={} mine={} stance={} order={} now holds {} dragon(s)",
                IceAndFireSupport.PROBE_LOG_PREFIX, entityId, revived, mine, stance, order, REVIVED.size());
    }

    /**
     * Empties the mirror, which is what leaving a world means.
     *
     * <p>An entity id belongs to one connection: a new session hands out the same numbers again, and a mirror that
     * outlived its world could therefore make an unrelated dragon look like a skeleton. It is cleared on every
     * disconnect and on every unloaded level, and the server fills it again by itself.
     */
    public static void clear(String why)
    {
        if (REVIVED.isEmpty())
        {
            return;
        }

        LOGGER.info("{} client mirror cleared ({}), {} dragon(s) forgotten", IceAndFireSupport.PROBE_LOG_PREFIX, why,
                REVIVED.size());
        REVIVED.clear();
    }

    /** @return a copy of the mirror, for the inspection report of the debug commands. */
    public static Map<Integer, Boolean> snapshot()
    {
        Map<Integer, Boolean> copy = new HashMap<>();

        for (Map.Entry<Integer, Mirror> entry : REVIVED.entrySet())
        {
            copy.put(entry.getKey(), entry.getValue().revived());
        }

        return copy;
    }
}
