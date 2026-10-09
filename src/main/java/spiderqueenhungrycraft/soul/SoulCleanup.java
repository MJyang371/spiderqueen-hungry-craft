package spiderqueenhungrycraft.soul;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * The administrator's emergency brake of the soul system (Soul Foundation V1).
 *
 * <p>{@code /kill @e} is not enough for a remnant: it only ever reaches entities in chunks that are loaded, and it
 * knows nothing about the registry that decides which remnant is real. That registry is the actual state, so the
 * cleanup empties it first and then removes whatever of its entities happens to be loaded right now.
 *
 * <p>Nothing is loaded to find the rest: a remnant in a chunk nobody is standing in simply discovers, the next time
 * that chunk ticks, that it is no longer anybody's record, and removes itself (see
 * {@link SoulRemnantEntity#isStillTheRecordOfItsOwner()}).
 */
public final class SoulCleanup
{
    private SoulCleanup()
    {
    }

    /**
     * What one cleanup really did, so the command can report it and a test can assert it.
     *
     * @param records  how many active records were forgotten
     * @param entities how many loaded remnants were removed from the world
     */
    public record Result(int records, int entities)
    {
    }

    /**
     * Forgets every remnant of every player and removes every loaded remnant entity of the server.
     *
     * @return the two counts, never null.
     */
    public static Result clearAll(MinecraftServer server)
    {
        int records = SoulRemnantData.get(server).clearAll();
        int entities = 0;

        for (ServerLevel level : server.getAllLevels())
        {
            // The entities are collected first and removed afterwards: removing one of them while walking the
            // entity list of a level would be a change of the list that is being read.
            List<SoulRemnantEntity> loaded = new ArrayList<>();

            for (Entity entity : level.getAllEntities())
            {
                if (entity instanceof SoulRemnantEntity remnant)
                {
                    loaded.add(remnant);
                }
            }

            for (SoulRemnantEntity remnant : loaded)
            {
                remnant.discard();
                entities++;
            }
        }

        return new Result(records, entities);
    }
}
