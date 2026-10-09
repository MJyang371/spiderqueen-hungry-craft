package spiderqueenhungrycraft.human;

/**
 * Marks a human of this mod that is <b>not</b> an ordinary surface human (human tidy round).
 *
 * <p>A transcendent human stands on the human side like every other human - it is filed into {@code human_faction},
 * it is answered by {@link HumanEntityTypes#isHumanMob} as a human unit, it takes part in the shared faction rules
 * and in the pack rules of {@link spiderqueenhungrycraft.hostility.HumanProvocation} - but it is not one of the
 * ordinary mobs the world is populated with. Chad Steve is the first such human, and this interface is what makes
 * "special" a property of a type instead of a property of one hard coded class name:
 *
 * <ul>
 * <li>its archetype is {@link HumanArchetype#TRANSCENDENT},</li>
 * <li>the surface spawner refuses to place it even when a spawn rules file lists it as a tier
 * (see {@code HumanSurfaceSpawner} - the shipped rules list the ordinary humans by hand anyway, so nothing about
 * the current world changes),</li>
 * <li>and it keeps its own AI, its own attributes, its own drops and its own model, exactly as Chad Steve does.</li>
 * </ul>
 *
 * <p>A future special human therefore needs two things at most: this marker, and - only when it registers its own
 * faction goal - {@link spiderqueenhungrycraft.faction.OwnFactionTargeting}.
 */
public interface TranscendentHuman
{
}
