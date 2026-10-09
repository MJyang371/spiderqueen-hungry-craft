package spiderqueenhungrycraft.human.spawn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.human.spawn.HumanSpawnConfig.GarrisonRules;
import spiderqueenhungrycraft.human.spawn.HumanSpawnConfig.SurfaceRules;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The one time garrison of a village (Phase HF0D).
 *
 * <p>There is no hook into world generation here, on purpose: that is brittle. Instead the overworld is watched at a
 * low frequency around each player, and a chunk that belongs to a village is looked up in
 * {@link HumanGarrisonSavedData}. A village that was never seen before gets its garrison once - a Chad Steve and two
 * to three high prepared humans - and is then marked, so neither the next tick nor the next reload can do it again.
 *
 * <p>A village is found by its structure first ({@code #minecraft:village}) and by its bell secondly: the meeting
 * point of a village is stored in the world, so even a village that existed long before this mod was installed is
 * found. The key of a village is its anchor quantized to a {@value #VILLAGE_CELL} block cell, so the same village
 * always has the same key no matter where the player stands when it is detected.
 */
public final class HumanVillageGarrisonSpawner
{
    /** The cell a village key is quantized to. */
    private static final int VILLAGE_CELL = 48;

    /** How many spots around the anchor are tried before the garrison takes what it got. */
    private static final int POSITION_TRIES = 32;

    private HumanVillageGarrisonSpawner()
    {
    }

    /** Called for every server level tick; the rules decide whether this tick checks anything. */
    public static void tick(ServerLevel level)
    {
        HumanSpawnConfig config = HumanSpawnRules.config();

        if (config == null || !config.garrisonEnabled())
        {
            return;
        }

        GarrisonRules rules = config.garrison();

        if (!HumanSpawnValidator.isAllowedDimension(level, config.surface()))
        {
            return;
        }

        if (level.getGameTime() % Math.max(1, rules.checkIntervalTicks()) != 0L)
        {
            return;
        }

        for (ServerPlayer player : level.players())
        {
            if (!player.isSpectator())
            {
                checkAround(level, player, rules);
            }
        }
    }

    private static void checkAround(ServerLevel level, ServerPlayer player, GarrisonRules rules)
    {
        ChunkPos centre = player.chunkPosition();
        checkChunk(level, centre, rules);

        for (int i = 1; i < Math.max(1, rules.chunksPerPlayer()); ++i)
        {
            int dx = level.random.nextInt(7) - 3;
            int dz = level.random.nextInt(7) - 3;
            checkChunk(level, new ChunkPos(centre.x + dx, centre.z + dz), rules);
        }
    }

    private static void checkChunk(ServerLevel level, ChunkPos chunk, GarrisonRules rules)
    {
        if (!level.getChunkSource().hasChunk(chunk.x, chunk.z))
        {
            return;
        }

        BlockPos probe = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                chunk.getMiddleBlockPosition(level.getMinBuildHeight()));
        String key = villageKey(level, probe, rules);

        if (key == null)
        {
            return;
        }

        HumanGarrisonSavedData data = HumanGarrisonSavedData.get(level);

        if (rules.oncePerVillage() && data.isProcessed(key))
        {
            return;
        }

        garrison(level, probe, key, rules, data);
    }

    /** @return the stable key of the village at that position, or null when there is no village there. */
    private static String villageKey(ServerLevel level, BlockPos pos, GarrisonRules rules)
    {
        BlockPos anchor = null;
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);

        if (start != null && start.isValid())
        {
            BoundingBox box = start.getBoundingBox();
            anchor = new BlockPos((box.minX() + box.maxX()) / 2, pos.getY(), (box.minZ() + box.maxZ()) / 2);
        }

        if (anchor == null)
        {
            Optional<BlockPos> meeting = level.getPoiManager().findClosest(holder -> holder.is(PoiTypes.MEETING), pos,
                    Math.max(1, rules.villagePoiRadius()), PoiManager.Occupancy.ANY);
            anchor = meeting.orElse(null);
        }

        if (anchor == null)
        {
            return null;
        }

        return "village@" + Math.floorDiv(anchor.getX(), VILLAGE_CELL) + "," + Math.floorDiv(anchor.getZ(), VILLAGE_CELL);
    }

    /**
     * Places the garrison of one village: Chad Steve first when the rules ask for it, then the high prepared humans.
     * When there are fewer free spots than unit slots, the important one is already placed and the rest is simply
     * skipped - the village is still marked, because a retry would only walk the same spots again and fill the log.
     */
    private static void garrison(ServerLevel level, BlockPos anchor, String key, GarrisonRules rules,
            HumanGarrisonSavedData data)
    {
        List<BlockPos> spots = findSpots(level, anchor, rules);
        int chadCount = Math.max(0, rules.chadCount());
        int highMin = Math.max(0, Math.min(rules.highMin(), rules.highMax()));
        int highMax = Math.max(highMin, rules.highMax());
        int highTarget = highMin + (highMax > highMin ? level.random.nextInt(highMax - highMin + 1) : 0);
        int chadSpawned = 0;
        int highSpawned = 0;
        int index = 0;

        if (rules.chadFirst() && chadCount > 0 && index < spots.size()
                && place(level, HungryCraftEntities.CHAD_STEVE.get(), spots.get(index)))
        {
            ++chadSpawned;
            ++index;
        }

        for (int i = 0; i < highTarget && index < spots.size(); ++i, ++index)
        {
            if (place(level, HungryCraftEntities.HIGH_PREPARED_HUMAN.get(), spots.get(index)))
            {
                ++highSpawned;
            }
        }

        if (!rules.chadFirst())
        {
            for (int i = 0; i < chadCount && index < spots.size(); ++i, ++index)
            {
                if (place(level, HungryCraftEntities.CHAD_STEVE.get(), spots.get(index)))
                {
                    ++chadSpawned;
                }
            }
        }

        if (rules.oncePerVillage())
        {
            data.markProcessed(key);
        }

        SpiderQueenHungryCraft.LOGGER.info(
                "[Spawn] village garrison {}: {} chad steve, {} high prepared human (spots={}, highTarget={})", key,
                chadSpawned, highSpawned, spots.size(), highTarget);
    }

    private static boolean place(ServerLevel level, EntityType<?> type, BlockPos pos)
    {
        return HumanSpawnFactory.spawn(type, level, pos, MobSpawnType.STRUCTURE,
                HumanSpawnOrigin.VILLAGE_GARRISON) != null;
    }

    /**
     * @return the free spots around the village anchor, one per chunk when the rules ask for it.
     *
     * <p>Every spot is validated with the size of Chad Steve, the biggest human of this mod, so a spot that passes is
     * certainly big enough for a prepared human as well.
     */
    private static List<BlockPos> findSpots(ServerLevel level, BlockPos anchor, GarrisonRules rules)
    {
        List<BlockPos> spots = new ArrayList<>();
        Set<Long> usedChunks = new HashSet<>();
        SurfaceRules surface = HumanSpawnRules.config() == null ? null : HumanSpawnRules.config().surface();
        double radius = Math.max(8.0D, rules.positionSearchRadius());

        for (int i = 0; i < POSITION_TRIES; ++i)
        {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = level.random.nextDouble() * radius;
            int x = anchor.getX() + Mth.floor(Math.cos(angle) * distance);
            int z = anchor.getZ() + Mth.floor(Math.sin(angle) * distance);
            BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, level.getMinBuildHeight(), z));

            if (rules.sameRulesAsSurface() && surface != null
                    && !HumanSpawnValidator.isValidSurfacePosition(level, pos, HungryCraftEntities.CHAD_STEVE.get(),
                            surface))
            {
                continue;
            }

            if (rules.onePerChunk() && !usedChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)))
            {
                continue;
            }

            spots.add(pos);
        }

        return spots;
    }
}
