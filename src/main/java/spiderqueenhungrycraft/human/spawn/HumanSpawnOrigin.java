package spiderqueenhungrycraft.human.spawn;

import javax.annotation.Nullable;

/**
 * Where a human came into the world (Phase HF0D).
 *
 * <p>It is saved in the nbt of the entity, so later rounds can decide from it whether a human may despawn, whether it
 * counts towards the population caps and whether it should appear in debug output.
 */
public enum HumanSpawnOrigin
{
    /** The surface patrol spawner of this mod put him there. */
    NATURAL_SURFACE("natural_surface"),

    /** The one time garrison of a village put him there. */
    VILLAGE_GARRISON("village_garrison"),

    /** A spawn egg was used. */
    SPAWN_EGG("spawn_egg"),

    /** A command, for example {@code /summon}, placed him. */
    COMMAND("command"),

    /** Nothing is known about him, for example an entity from an older save file. */
    UNKNOWN("unknown");

    private final String id;

    HumanSpawnOrigin(String id)
    {
        this.id = id;
    }

    public String id()
    {
        return this.id;
    }

    /** @return the origin with that id, or {@link #UNKNOWN} for anything that is not known. */
    public static HumanSpawnOrigin byId(@Nullable String id)
    {
        if (id != null)
        {
            for (HumanSpawnOrigin origin : values())
            {
                if (origin.id.equals(id))
                {
                    return origin;
                }
            }
        }

        return UNKNOWN;
    }
}
