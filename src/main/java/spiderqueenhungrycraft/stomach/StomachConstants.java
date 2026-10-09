package spiderqueenhungrycraft.stomach;

/**
 * Every tuning number of the stomach system in one place (Phase 8E1-GATE2A).
 *
 * <p>The whole behaviour of a stomach core is described by these constants: how healthy it is, how fast it heals,
 * when it digests and how large its attack surface is. Nothing in the code below uses a magic number of its own,
 * so the feel of the stomach is changed here and only here.
 */
public final class StomachConstants
{
    /** Health of a stomach core. Deliberately absurd: the wall is broken by forwarding, not by killing it. */
    public static final float CORE_MAX_HEALTH = 9999.0F;

    /** The core heals itself back to full health this often. */
    public static final int CORE_REGEN_INTERVAL_TICKS = 20;

    /** A loaded cell makes sure its core exists this often. */
    public static final int CORE_ENSURE_INTERVAL_TICKS = 20;

    /** How often the core hurts everything that is inside its cell. */
    public static final int DIGESTIVE_PULSE_INTERVAL_TICKS = 40;

    /** Raw physical damage of one digestive pulse, before any armor of the victim. */
    public static final float DIGESTIVE_PULSE_DAMAGE = 4.0F;

    /** How often the core points the mobs of its cell at itself. */
    public static final int CORE_TAUNT_INTERVAL_TICKS = 10;

    /** Attack surface of a core: it covers the whole cavity of one cell. */
    public static final float CORE_WIDTH = 10.0F;
    public static final float CORE_HEIGHT = 10.0F;

    /** Knockback resistance of a core: it is nailed to its cell. */
    public static final double CORE_KNOCKBACK_RESISTANCE = 1.0D;

    /** Volume and pitch of the fleshy hit of a core. */
    public static final float CORE_HIT_SOUND_VOLUME = 1.1F;
    public static final float CORE_HIT_SOUND_PITCH = 0.85F;

    /** How charged a player attack has to be before it counts as a hit on the wall. */
    public static final float WALL_ATTACK_MIN_CHARGE = 0.9F;

    private StomachConstants()
    {
    }
}
