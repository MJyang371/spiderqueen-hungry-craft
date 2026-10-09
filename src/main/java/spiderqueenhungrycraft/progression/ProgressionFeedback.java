package spiderqueenhungrycraft.progression;

/**
 * The answer of the server to one request of the growth interface (Progression Foundation round).
 *
 * <p>It is a small code and not a piece of text: the server decides <b>what</b> happened, the client decides how to
 * say it, and the translation of the message the task asks for lives in the language file of the mod. A code also
 * keeps the denial path free of logs - a player who clicks a node they can not afford is an ordinary event, not an
 * error.
 */
public enum ProgressionFeedback
{
    /** The Queen Level was raised. */
    UPGRADE_OK,

    /** A skill node was bought and is now owned. */
    SKILL_OK,

    /** A product of the soul shop was bought and handed over. */
    PURCHASE_OK,

    /** The player does not hold enough souls. The one message both paths share. */
    INSUFFICIENT_SOULS,

    /** The node that was asked for is not the one step the player is allowed to take next. */
    NOT_THE_NEXT_LEVEL,

    /** The main line is finished for this player (or for this world), there is nothing left to raise. */
    ALREADY_MAX_LEVEL,

    /** The product does not exist (any more) in the shop of the server. */
    UNKNOWN_PRODUCT,

    /** The request did not come from a valid open core, or the player is not at it any more. */
    DENIED,

    /** The node that was asked for exists, but its level or one of its prerequisites is still missing. */
    SKILL_LOCKED,

    /** The player already owns that node, so nothing was charged a second time. */
    ALREADY_UNLOCKED,

    /** The node does not exist (any more), or a pack switched it off. */
    UNKNOWN_SKILL
}
