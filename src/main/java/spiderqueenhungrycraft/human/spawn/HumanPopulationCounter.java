package spiderqueenhungrycraft.human.spawn;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.human.HumanEntityTypes;

    /**
     * Counts the <b>surface population</b> of this mod inside a box: the ordinary humans the surface patrol is about.
     *
     * <p>The two questions a human can be asked are deliberately separate (Sweatlord round):
     *
     * <ul>
     * <li>{@link #countInBox} - "how many ordinary humans are here", which is the number the ring target, the per
     * chunk cap and the local cap of {@code HumanSurfaceSpawner} are made of. A transcendent human (Chad Steve,
     * Sweatlord Steve) is <b>not</b> part of it: it must never take a slot away from the ordinary survivors.</li>
     * <li>{@link #countHumanUnitsInBox} - "how many humans of this mod are here at all", including the transcendent
     * ones. This is the pack/faction question and it is only used for debug output and tests.</li>
     * </ul>
     *
     * <p>Both count {@link Mob}s only: a player is not a "naturally existing humanoid monster", even when he is a
     * human.
     */
    public final class HumanPopulationCounter
    {
        private HumanPopulationCounter()
        {
        }

        /** @return how many ordinary humans stand inside that box right now, i.e. its surface population. */
        public static int countInBox(ServerLevel level, AABB box)
        {
            return level.getEntitiesOfClass(Mob.class, box, HumanEntityTypes::countsTowardSurfacePopulation).size();
        }

        /** @return how many human units of this mod stand inside that box, transcendent ones included. */
        public static int countHumanUnitsInBox(ServerLevel level, AABB box)
        {
            return level.getEntitiesOfClass(Mob.class, box, HumanEntityTypes::isHumanMob).size();
        }

        /** @return how many ordinary humans stand inside that chunk right now. */
        public static int countInChunk(ServerLevel level, ChunkPos chunk)
        {
            return countInBox(level, chunkBox(level, chunk));
        }

        /** @return how many ordinary humans stand inside that sphere of blocks around a point. */
        public static int countInRadius(ServerLevel level, double x, double y, double z, double radius)
        {
            return countInBox(level, new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
        }

        /** @return how many human units of this mod stand inside that sphere, transcendent ones included. */
        public static int countHumanUnitsInRadius(ServerLevel level, double x, double y, double z, double radius)
        {
            return countHumanUnitsInBox(level,
                    new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius));
        }

        private static AABB chunkBox(ServerLevel level, ChunkPos chunk)
        {
            return new AABB(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1.0D, level.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1.0D);
        }
    }
