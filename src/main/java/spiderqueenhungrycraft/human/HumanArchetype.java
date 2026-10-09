package spiderqueenhungrycraft.human;

/**
 * What kind of human a mob of this mod is (human tidy round).
 *
 * <p>It answers one question and only one: <b>is this an ordinary human or a special one</b>. It deliberately does
 * not answer the other questions that used to be squeezed into the same check, because those are separate rules with
 * separate owners:
 *
 * <ul>
 * <li>which side it stands on is a {@link spiderqueenhungrycraft.faction.FactionId}, decided by the faction tags,</li>
 * <li>whether the surface spawner may place it is a spawn rule (the tier table of the spawn rules file) plus this
 * archetype,</li>
 * <li>whether it occupies the ordinary human population is a population rule
 * ({@link spiderqueenhungrycraft.human.spawn.HumanPopulationCounter}),</li>
 * <li>and who owns its faction targeting is {@link spiderqueenhungrycraft.faction.OwnFactionTargeting}.</li>
 * </ul>
 */
public enum HumanArchetype
{
    /** An ordinary human: a prepared human of one of the three tiers. The surface patrol spawns these. */
    STANDARD("standard", true),

    /** A special human: unique gear, own AI, own drops. Chad Steve today, more of them later. */
    TRANSCENDENT("transcendent", false);

    private final String id;
    private final boolean countsTowardSurfacePopulation;

    HumanArchetype(String id, boolean countsTowardSurfacePopulation)
    {
        this.id = id;
        this.countsTowardSurfacePopulation = countsTowardSurfacePopulation;
    }

    /** @return the stable lower case id, used by debug output. */
    public String id()
    {
        return this.id;
    }

    /**
     * @return true when a human of this archetype is one of the humans the surface population is about.
     *
     * <p>Only ordinary humans count towards that population (human tidy round, made explicit in the Sweatlord round):
     * a transcendent human (Chad Steve, Sweatlord Steve) stands on the human side, is a human unit for the pack rules
     * and is counted as one, but it must never eat a slot of the ring a player's ordinary survivors are spawned in.
     * Otherwise a single special human standing in the ring would quietly lower how many ordinary Steves the world
     * spawns.
     */
    public boolean countsTowardSurfacePopulation()
    {
        return this.countsTowardSurfacePopulation;
    }
}
