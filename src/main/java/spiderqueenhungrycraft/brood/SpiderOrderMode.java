package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

/**
 * The long term order of a spider minion (Brood Command AI).
 *
 * <p>An order is <b>not</b> a combat state: fighting is a temporary override on top of the order (see
 * {@link SpiderMinionEntity#getForcedTarget()}), so a patrol spider that was sent at an enemy goes back to patrolling
 * its anchor the moment that enemy is gone. The order therefore only says where the spider belongs:
 * <ul>
 * <li>{@link #FOLLOW} - with its queen. The default of every spider a player created.</li>
 * <li>{@link #HOLD} - guarding one anchor position.</li>
 * <li>{@link #PATROL} - wandering around one anchor position.</li>
 * <li>{@link #FREE_ATTACK} - <b>hunting</b> around one anchor position: it walks the rounds of a patrol and it attacks
 * every living creature that does not stand on its own side, whether the faction matrix has an opinion about that
 * creature or not (see {@link SpiderMinionTargeting#isFreeAttackEnemy}).</li>
 * </ul>
 *
 * <p><b>The orders are the cycle of the order key</b> ({@link #next()}): follow, hold, patrol, free attack, follow.
 * A free attack order is left the moment a new command arrives (a scout move, an attack order, a rally, a post), so it
 * is a mode a queen switches on and off rather than a place her spiders stay in forever.
 */
public enum SpiderOrderMode
{
    /** Stay with the owner (vanilla pet feel). */
    FOLLOW("follow"),

    /** Guard the anchor: return to it whenever there is nothing to fight. */
    HOLD("hold"),

    /** Patrol around the anchor: wander inside a radius of it. */
    PATROL("patrol"),

    /** Hunt around the anchor: wander like a patrol and attack everything that is not of the own side. */
    FREE_ATTACK("free_attack");

    private final String id;

    SpiderOrderMode(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }

    /** @return the next order of the sceptre cycle: follow, hold, patrol, follow ... */
    public SpiderOrderMode next()
    {
        SpiderOrderMode[] modes = values();

        return modes[(this.ordinal() + 1) % modes.length];
    }

    /** @return true when this order stands on an anchor position. */
    public boolean needsAnchor()
    {
        return this != FOLLOW;
    }

    /**
     * @return true when this order walks its own rounds around its anchor instead of standing on it - a patrol
     *         wandering the neighbourhood, and a free attack hunting in it.
     *
     * <p>It is the one question the wandering goal of the brood asks, so a new hunting order can never end up standing
     * still like a guard.
     */
    public boolean roams()
    {
        return this == PATROL || this == FREE_ATTACK;
    }

    /** @return true when this order attacks whatever is not of the own side, and not only what the factions call an enemy. */
    public boolean huntsFreely()
    {
        return this == FREE_ATTACK;
    }

    /** @return the language key of the name of this order. */
    public String translationKey()
    {
        return "mode.spiderqueen_hungrycraft." + this.id;
    }

    /** @return the order with that id, {@link #FOLLOW} when the id is unknown (the safe default). */
    public static SpiderOrderMode byId(@Nullable String id)
    {
        if (id != null)
        {
            for (SpiderOrderMode mode : values())
            {
                if (mode.id.equals(id))
                {
                    return mode;
                }
            }
        }

        return FOLLOW;
    }
}
