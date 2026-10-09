package spiderqueenhungrycraft.progression;

/**
 * What one skill node is for one player right now (Skill Progression round).
 *
 * <p>It is the three states the task names, and they are decided from two things only: whether the node is already
 * owned, and whether its conditions - the Queen Level and every prerequisite - are met. <b>Whether the player can
 * afford it is deliberately not part of this</b>: a node she can not pay for is still available, and clicking it is
 * what tells her that she is missing the souls. That is why this enum is about permission and not about a purse.
 *
 * <p>It is used on both sides: the server asks it to decide whether a request may be answered, the client asks it to
 * decide which frame a node gets. While a client computes it from the picture the server sent, an honest client and a
 * modified one are still answered by the same rules on the server.
 */
public enum SkillState
{
    /** The player has bought it. */
    UNLOCKED,

    /** Level and prerequisites are met: it can be bought, whatever the purse says. */
    AVAILABLE,

    /** The level or a prerequisite is missing: the node can not be bought at all. */
    LOCKED
}
