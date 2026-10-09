package spiderqueenhungrycraft.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;

/**
 * The one rule a natural spawner has to ask (Phase LAIR-CORE-1).
 *
 * <p>This is the public entry point of the territory core, and it is meant to stay the only one: the surface patrol
 * of the humans, the vanilla spawner and every later natural spawner of this mod ask
 * {@link #isNaturalSpawnBlocked(ServerLevel, BlockPos)} before they place anything, so "inside a territory" can never
 * mean two different things.
 *
 * <p>It only ever answers for <b>natural</b> spawns. Anything a player or a script does - a command, a spawn egg,
 * breeding, a monster spawner, a structure, a later brood - is not affected, see
 * {@link #isNaturalSpawnType(MobSpawnType)}.
 *
 * <p><b>Both roads of this mod end here.</b> The vanilla spawner asks through {@code MobSpawnEvent.PositionCheck},
 * which {@link TerritoryCoreSpawnEvents} handles. The surface patrol of the humans is placed by this mod itself, so no
 * such event ever fires for it, and that path therefore asks {@link #isNaturalSpawnBlocked} directly inside
 * {@link spiderqueenhungrycraft.human.spawn.HumanSpawnValidator#rejectNaturalSpawnReason} - the single place where a
 * natural human spawn is judged. Without that second road a lair core was invisible to the humans of the surface.
 */
public final class TerritoryCoreSpawnRules
{
    private TerritoryCoreSpawnRules()
    {
    }

    /** @return true when a natural spawn at that position is inside the protection of a territory core. */
    public static boolean isNaturalSpawnBlocked(ServerLevel level, BlockPos pos)
    {
        return TerritoryCoreIndex.isProtected(level, pos);
    }

    /**
     * @return true only for the spawn reasons that count as natural.
     *
     * <p>Blocked: {@link MobSpawnType#NATURAL}, the spawn reason of every ordinary spawn cycle.
     * <br>Allowed on purpose: {@code COMMAND} (a summon), {@code SPAWN_EGG}, {@code BREEDING} (animals and
     * villagers), {@code SPAWNER} (a monster spawner), {@code STRUCTURE} (a village garrison, a structure), {@code
     * MOB_SUMMONED} (a queen, a brood, a script), {@code TRIGGERED} (a raid, a patrol), {@code EVENT} and {@code
     * CHUNK_GENERATION} (world generation, which happens before a player can place a core anyway). None of those are
     * natural spawns, so a territory core never touches them.
     */
    public static boolean isNaturalSpawnType(MobSpawnType type)
    {
        return type == MobSpawnType.NATURAL;
    }
}
