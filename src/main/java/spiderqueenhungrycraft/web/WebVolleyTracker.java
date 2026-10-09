package spiderqueenhungrycraft.web;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.world.entity.LivingEntity;

/**
 * The tracker of a beast web volley (Phase 8A).
 *
 * <p>A beast volley is five real, independent projectiles, so damage, Webbed stacks, capture rolls and
 * elemental effects may happen five times on the same victim. The only thing that must not happen five times
 * is the pack aggro scan, because five projectiles landing next to each other would search the very same
 * neighbourhood five times for nothing.
 *
 * <p>That is the single purpose of this class: it remembers which victims already provoked the pack in one
 * volley, and nothing else. There is no "this target was hit" flag, no damage bookkeeping and no NBT. A volley
 * lives for {@link #LIFETIME_TICKS} ticks and is dropped afterwards, so the map can never grow without bound.
 *
 * <p>Server side and main thread only: it is only ever touched from the server side projectile hits and the
 * provocation, never from a client.
 */
public final class WebVolleyTracker
{
    /** Value for everything that is not part of a volley, such as the ordinary single shot. */
    public static final int NO_VOLLEY = -1;

    /** How long a volley stays in the tracker. Well above any flight time of a projectile. */
    private static final int LIFETIME_TICKS = 60;

    private static final Map<Integer, Volley> VOLLEYS = new HashMap<>();

    private static int nextVolleyId = 1;

    private WebVolleyTracker()
    {
    }

    /**
     * Opens a new volley.
     *
     * @return the id every projectile of that volley carries
     */
    public static int startVolley(long gameTime)
    {
        purge(gameTime);

        int id = nextVolleyId++;

        if (nextVolleyId <= 0)
        {
            nextVolleyId = 1;
        }

        VOLLEYS.put(id, new Volley(gameTime + LIFETIME_TICKS));
        return id;
    }

    /**
     * Marks that victim as provoked for that volley.
     *
     * @return true when the pack scan still has to run, false when this volley already provoked that victim.
     *         A missing volley (an expired or an unknown id) answers true, so an alert is never lost just
     *         because the tracker forgot about it.
     */
    public static boolean markProvocation(int volleyId, LivingEntity victim)
    {
        if (volleyId == NO_VOLLEY)
        {
            return true;
        }

        Volley volley = VOLLEYS.get(volleyId);

        if (volley == null)
        {
            return true;
        }

        return volley.provokedVictims().add(victim.getUUID());
    }

    private static void purge(long gameTime)
    {
        VOLLEYS.entrySet().removeIf(entry -> entry.getValue().expiresAt() < gameTime);
    }

    /** One salvo: when it expires and which victims already woke the pack up. */
    private static final class Volley
    {
        private final long expiresAt;
        private final Set<UUID> provokedVictims = new HashSet<>();

        private Volley(long expiresAt)
        {
            this.expiresAt = expiresAt;
        }

        private long expiresAt()
        {
            return this.expiresAt;
        }

        private Set<UUID> provokedVictims()
        {
            return this.provokedVictims;
        }
    }
}
