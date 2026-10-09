package spiderqueenhungrycraft.brood;

/**
 * The <b>temporary</b> command of a spider minion (Queen Scepter command patch).
 *
 * <p>An order ({@link SpiderOrderMode}) says how a spider lives; a temporary command says what it was told to do a
 * moment ago. The two never mix: a forced attack or a scout move never changes the follow / hold / patrol order, so a
 * patrolling spider that was sent at an enemy goes back to patrolling the instant the enemy is gone.
 *
 * <p>A spider has exactly <b>one</b> of these at a time - a new command always replaces the old one, and there is no
 * queue: the navigation of the game follows one destination, and so does the sceptre.
 */
public enum SpiderTemporaryCommand
{
    /** No temporary command: the spider does what its order says. */
    NONE("none"),

    /** Sent to a scout position: walk there, then stand by until told something else. */
    MOVE_TO("move_to"),

    /** Told to attack one creature, whatever the order and the faction matrix say. */
    FORCED_ATTACK("forced_attack");

    private final String id;

    SpiderTemporaryCommand(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }

    /** @return true when this command is a fight rather than a place to stand. */
    public boolean isFight()
    {
        return this == FORCED_ATTACK;
    }
}
