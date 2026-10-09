package spiderqueenhungrycraft.ai;

/**
 * The states of the human combat brain (Phase HF0A-GAMEPASS1).
 *
 * <p>The state is derived from the situation on every tick and then executed, so it is a description of what the
 * combatant is doing right now and never a hidden flag two places can disagree about.
 */
public enum HumanCombatState
{
    /** Nothing to fight: the ordinary mob goals take over. */
    IDLE("idle"),

    /** The target is out of bow range, walking towards it with the sword in hand. */
    APPROACH("approach"),

    /** The bow is drawn, the combatant stands still and faces the target until the draw is complete. */
    DRAW_BOW("draw_bow"),

    /** The arrow of the current draw is released, the bow cooldown starts. */
    RELEASE_BOW("release_bow"),

    /** The target is too close: the shield is up, facing it, backing away or strafing at reduced speed. */
    SHIELD_GUARD("shield_guard"),

    /** Low on health with a golden apple left: pulling back to about ten blocks. */
    RETREAT("retreat"),

    /** The golden apple is being eaten, which the vanilla use logic of the item resolves. */
    EAT_GOLDEN_APPLE("eat_golden_apple"),

    /** Within melee reach: the iron sword swings on a cooldown. */
    MELEE_ATTACK("melee_attack"),

    /** In the middle band: holding the line, strafing, waiting for the bow cooldown to end. */
    REPOSITION("reposition");

    private final String id;

    HumanCombatState(String id)
    {
        this.id = id;
    }

    /** @return the stable lower case id, used by debug output of the combatant. */
    public String id()
    {
        return this.id;
    }
}
