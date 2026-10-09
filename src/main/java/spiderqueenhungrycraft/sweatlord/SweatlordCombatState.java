package spiderqueenhungrycraft.sweatlord;

/**
 * The few high level states of the Sweatlord fight (Sweatlord round).
 *
 * <p>This is deliberately a small list. Everything that has to happen inside a state - the draw of a bow, a burst of
 * eggs, a pearl in flight, a shield raised against an arrow - is a {@link SweatlordSkill} or an interrupt, never a
 * state of its own. A state only answers "what kind of thing is he doing", which is what the debug output and the
 * tests read, and which is what makes a stuck entity recognisable at a glance.
 *
 * <p>The intended graph is:
 *
 * <pre>
 * IDLE ──enemy found──&gt; PREBUFF ──gapple eaten──&gt; RANGED_COMBAT ⇄ MELEE_COMBAT
 *   ^                        │                          │
 *   │                        └──(no enemy)──────────────┘
 *   │                                                   │
 *   └──enemy gone──────────────────────────────────────┘
 *                            low HP
 *                               ↓
 *                         RETREAT_HEAL ──healed──&gt; RANGED_COMBAT / MELEE_COMBAT
 * </pre>
 *
 * <p>Outside of that graph sit two things that are not states on purpose: the lethal environment safety of
 * {@link EnvironmentSafety} (it only pushes him away from lava, a cliff or the void, it never becomes "the thing he
 * is doing") and the {@link InstantProjectileGuard} of this body (a real projectile on a collision course owns the
 * shield for a moment, and never the body: it is an overlay and not a skill).
 */
public enum SweatlordCombatState
{
    /** No valid enemy: the ordinary mob goals walk him around. */
    IDLE("idle"),

    /** The first enchanted golden apple of this encounter is being eaten, with the real item use. */
    PREBUFF("prebuff"),

    /** Far from the enemy: bow, eggs and trident, with a pearl jump when it is worth it. */
    RANGED_COMBAT("ranged_combat"),

    /** Inside melee reach: sword work, jump crits and the axe when the enemy really raises a shield. */
    MELEE_COMBAT("melee_combat"),

    /** Low health: an escape pearl, one plain golden apple, then back into the fight. */
    RETREAT_HEAL("retreat_heal"),

    /**
     * A lethal emergency: falling to his death, or burning. This one may take the body away from anything, including a
     * running draw or a sword combo, because it is the most immediately fatal thing on the field.
     */
    EMERGENCY("emergency");

    private final String id;

    SweatlordCombatState(String id)
    {
        this.id = id;
    }

    /** @return the stable lower case id, used by the debug output and the synced debug state of the entity. */
    public String id()
    {
        return this.id;
    }
}
