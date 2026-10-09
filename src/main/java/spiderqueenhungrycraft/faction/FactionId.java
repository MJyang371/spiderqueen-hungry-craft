package spiderqueenhungrycraft.faction;

import javax.annotation.Nullable;

/**
 * The factions of the Hungry Craft faction system v0.2 (Phase HF1-VISIBLE, split into the ecology factions in the
 * faction split pass).
 *
 * <p>Biology decides what a creature <b>is</b>. The faction decides which side it <b>stands on</b>. The AI decides
 * what it <b>wants to do right now</b>. This enum is the middle one and only the middle one: a {@code Monster} is
 * not automatically the {@code MONSTER} faction, a villager is not automatically {@code HUMAN}, and a player is not
 * automatically on the queen's side - that depends on the queen flag of that player.
 *
 * <p><b>{@code MONSTER} is not "everything that extends {@code Monster}".</b> It is only the faction of the
 * <b>ordinary overworld monsters</b> (zombies, skeletons, spiders, creepers, slimes, endermen, silverfish, ...).
 * The nether, the illagers, the bosses, the guardians and the phantoms have their own faction or none at all, and
 * any {@code Monster} that no faction tag names at all is {@link #NONE}.
 */
public enum FactionId
{
    /** Spider Queens: a player whose queen flag is set, and later brood and queen guard entities. */
    QUEEN("queen"),

    /** Humans: a player whose queen flag is cleared, Chad Steve, and later the human units. */
    HUMAN("human"),

    /** Villages: villagers, iron golems, wandering traders and modded guards. */
    VILLAGE("village"),

    /**
     * The ordinary monsters of the overworld, driven by the {@code monster_faction} entity type tag. It is not a
     * marker for the {@code Monster} class - see the class documentation.
     */
    MONSTER("monster"),

    /**
     * The piglin ecology of the nether: piglins, piglin brutes, zombified piglins, hoglins and zoglins. Deliberately
     * neutral towards the queen and towards humans: this mod never starts a fight for them, their own vanilla AI
     * decides everything as it always did.
     */
    PIGLIN("piglin"),

    /** The illager faction (the raiders): witches, pillagers, vindicators, evokers, illusioners, ravagers and vexes. */
    ILLAGER("illager"),

    /** The nether fortress ecology: blazes, ghasts, wither skeletons and magma cubes. */
    NETHER_FORTRESS("nether_fortress"),

    /** Everything that deliberately has no faction and therefore never takes part in faction targeting. */
    NONE("none");

    private final String id;

    FactionId(String id)
    {
        this.id = id;
    }

    /** @return the stable lower case id, used by debug output. */
    public String id()
    {
        return this.id;
    }

    /** @return the faction with that id, or {@link #NONE} when it is unknown. */
    public static FactionId byId(@Nullable String id)
    {
        if (id != null)
        {
            for (FactionId faction : values())
            {
                if (faction.id.equals(id))
                {
                    return faction;
                }
            }
        }

        return NONE;
    }
}
