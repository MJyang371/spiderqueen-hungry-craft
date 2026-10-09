package spiderqueenhungrycraft.progression;

import net.minecraft.server.level.ServerPlayer;

/**
 * The boundary of the legendary progression (Progression Foundation round): the entry point a later round implements,
 * and a locked door until then.
 *
 * <p><b>Legendary growth is a different system.</b> The main line ends at
 * {@link ProgressionDefinition#maxLevel()} - it is not extended to 201 and beyond. What the future round adds is a
 * second sink and a second kind of growth that hangs off the <b>completion</b> of the first one, and it never shares
 * a field, a price table or a rule with the Queen Level.
 *
 * <p>The eligibility below is the whole condition of that future round, already in its final shape, and it is
 * computed honestly - but {@link #SYSTEM_ENABLED} is false, so nothing can be offered, bought or spent, and this
 * round touches no attribute of anybody.
 *
 * <p><b>Where the future round will go (recorded here, implemented nowhere):</b> a legendary sink spends souls on
 * permanent, small improvements of the base body - base armour, maximum health, base attack damage and a hunger
 * capacity - and it must do it through {@code AttributeModifier}s of a stable, well named kind rather than by writing
 * into the base value of a vanilla attribute. The hunger capacity is the one of those that can not be done like the
 * others: vanilla hunger is a fixed twenty, so it needs a design of its own and is deliberately not touched here or
 * by any other round of this phase.
 */
public final class LegendaryProgression
{
    /** False until the round that really implements legendary growth. It is the switch that locks the tab. */
    public static final boolean SYSTEM_ENABLED = false;

    private LegendaryProgression()
    {
    }

    /** @return true when the legendary system of this world exists at all. */
    public static boolean isSystemEnabled()
    {
        return SYSTEM_ENABLED;
    }

    /**
     * The completion condition of the future round: the main line is finished <b>and</b> everything that counts among
     * the skill nodes is unlocked.
     *
     * @return true when that player has done everything the ordinary growth system can ask of her.
     */
    public static boolean isEligible(ServerPlayer player)
    {
        ProgressionDefinition definition = ProgressionDefinitions.current();

        return QueenLevels.hasReached(player, definition.maxLevel())
                && SkillNodes.allRequiredForLegendUnlocked(player);
    }

    /**
     * @return true when a legendary purchase may be offered to that player right now.
     *
     *         <p>Never true in this round: the door is locked by {@link #SYSTEM_ENABLED}, whatever the conditions
     *         say. This is the method the interface asks, so the day the system is switched on there is exactly one
     *         place to change.
     */
    public static boolean canOffer(ServerPlayer player)
    {
        return SYSTEM_ENABLED && isEligible(player);
    }
}
