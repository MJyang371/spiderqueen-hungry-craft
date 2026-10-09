package spiderqueenhungrycraft.faction;

/**
 * Marks a mob that owns its faction targeting (human tidy round).
 *
 * <p>The faction system gives every mob that takes part in the war the one {@link FactionTargetGoal}, so no mob of
 * this mod has to write the rules of the war again (see {@code FactionEvents}). One mob may not receive that goal
 * though: a mob that already registers the very same goal in its own {@code registerGoals()} would end up with the
 * goal twice, and the second copy would fight the first one for the same target flag.
 *
 * <p>So this interface answers exactly one question - <b>who owns the targeting of this mob</b> - and it is
 * deliberately not a list of entity types. Before it existed, {@code FactionEvents} asked
 * {@code instanceof ChadSteveEntity}, which meant that every further special human would have had to be added to that
 * line as well. A new special human now simply implements this interface and is done:
 *
 * <ul>
 * <li>it registers its own {@link FactionTargetGoal} (or its own goals) in {@code registerGoals()}, and</li>
 * <li>the faction system never injects a second one into it.</li>
 * </ul>
 *
 * <p>An ordinary human does <b>not</b> implement this: it keeps the injected default goal and therefore stays under
 * the shared faction rules. The behaviour of every existing entity is exactly the same with this marker as it was
 * with the name of the one entity type it replaces.
 */
public interface OwnFactionTargeting
{
}
