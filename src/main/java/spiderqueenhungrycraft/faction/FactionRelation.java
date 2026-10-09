package spiderqueenhungrycraft.faction;

/**
 * How two factions stand towards each other (Phase HF1-VISIBLE).
 *
 * <p>The first three values are the design language a pack author sees. {@link #PASS_THROUGH} is an internal safety
 * valve and never a design decision: it means "the faction system has no opinion here, keep the vanilla behaviour
 * exactly as it is". It is used for the inside of the monster faction, so a skeleton that hits another skeleton can
 * still start a fight, and for every pair that involves {@link FactionId#NONE}.
 */
public enum FactionRelation
{
    /** Same side: the faction logic never starts a fight. */
    FRIENDLY("friendly"),

    /** Peace by default: no faction fight unless one of them was attacked by the other. */
    NEUTRAL("neutral"),

    /** A faction fight on sight. */
    HOSTILE("hostile"),

    /** Not a relation at all: vanilla and third party AI stay completely untouched. */
    PASS_THROUGH("pass_through");

    private final String id;

    FactionRelation(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }
}
