package spiderqueenhungrycraft.human.spawn;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.human.HumanEntityTypes;
import spiderqueenhungrycraft.human.spawn.HumanSpawnConfig.SurfaceRules;
import spiderqueenhungrycraft.human.spawn.HumanSpawnConfig.TierWeight;

/**
 * The surface patrol of the humans (Phase HF0D).
 *
 * <p>It never touches the vanilla biome spawn lists. Instead it runs on its own clock: one attempt every
 * {@code attempt_interval_ticks} around a player, with a higher chance during the day than during the night. An
 * attempt looks at the ring between the minimum and the maximum distance around that player, and when the ring is
 * thinner than the population target of that time of day it picks a chunk in it, checks the chunk and the local
 * population caps, rolls the tier by the weights of the rules and then looks for a spot that passes
 * {@link HumanSpawnValidator}.
 *
 * <p>The target is what keeps the ring evenly filled instead of merely "sometimes lucky": a nearly empty ring lets
 * every attempt through, a full one turns them all away, and because every chunk carries at most
 * {@code per_chunk_human_cap} humans the survivors end up one per chunk rather than bunched up in the few chunks the
 * dice happened to like.
 *
 * <p>Nothing here runs per tick and nothing scans the whole world: one interval, a handful of players, a few tries.
 */
public final class HumanSurfaceSpawner
{
    /** How many spots inside the chosen chunk are tried before the attempt is given up. */
    private static final int POSITION_TRIES = 8;

    /**
     * How many chunks one attempt of one player may try before the attempt is over.
     *
     * <p>A chunk that is already occupied - the cap of the rules is one human per chunk - is not a reason to abort
     * the whole tick: without that retry, a busy ring would swallow most attempts into one crowded chunk and the
     * humans would gather in the few chunks the dice kept picking. With it, every attempt lands in a free chunk and
     * the humans spread out over the ring, which is what the phase asks for.
     *
     * <p>The tries also have to be enough to close the ring once the population target is nearly reached: with a ring
     * of about seventy usable chunks and a target of a few dozen humans, six picks still find one of the free chunks
     * with a probability of well over nine in ten, while four would start to waste more and more attempts.
     */
    private static final int CHUNK_TRIES = 6;

    /** Tier entries that are no ordinary human, warned about once each so a rules file can not fail silently. */
    private static final Set<EntityType<?>> WARNED_NOT_ORDINARY = ConcurrentHashMap.newKeySet();

    private HumanSurfaceSpawner()
    {
    }

    /** Called for every server level tick; the rules decide whether this tick is an attempt at all. */
    public static void tick(ServerLevel level, HumanSpawnerHooks.SurfaceHooks hooks)
    {
        hooks.onSurfaceSpawnerCalled();

        HumanSpawnConfig config = HumanSpawnRules.config();

        if (config == null)
        {
            hooks.onRulesNullSkip();
            return;
        }

        if (!config.surfaceEnabled())
        {
            hooks.onSurfaceDisabledSkip();
            return;
        }

        SurfaceRules rules = config.surface();

        if (!HumanSpawnValidator.isAllowedDimension(level, rules))
        {
            hooks.onDimensionReject();
            return;
        }

        if (level.getGameTime() % Math.max(1, rules.attemptIntervalTicks()) != 0L)
        {
            hooks.onTickIntervalSkip();
            return;
        }

        recordPopulation(level, rules);

        for (ServerPlayer player : level.players())
        {
            hooks.onPlayerLoopEntries();

            if (player.isSpectator())
            {
                hooks.onSpectatorSkip();
                continue;
            }

            attempt(level, player, rules, hooks);
        }
    }

    /**
     * Counts the humans that stand in the ring of the rules around every player and hands the number to
     * {@link HumanSpawnEvents}.
     *
     * <p>The count is the answer to \"does this player see humans or not\", which is the experience the rules of this
     * phase tune: the tier weights pick one tier per attempt, so a weighted average is not the same as the population
     * a player walks through. It is taken once per tick, costs one box query per player and replaces the per attempt
     * local cap query.
     */
    public static void recordPopulation(ServerLevel level, SurfaceRules rules)
    {
        int total = 0;

        for (ServerPlayer player : level.players())
        {
            if (!player.isSpectator())
            {
                total += countInRing(level, player, rules);
            }
        }

        HumanSpawnEvents.recordPopulation(total);
    }

    /**
     * @return how many humans stand in the ring between the minimum and the maximum distance around that player.
     *
     * <p>Boxes are square while the ring is round, so the box only has to be the outer square of the ring: every
     * human inside the circle is inside the box, and the few extra ones the corners add are subtracted again by
     * {@link #isWithinRing}. That check makes the box no longer an approximation - the corners are really dropped,
     * the cost is that their humans are counted as lying in the ring and pull the number up a little.
     */
    private static int countInRing(ServerLevel level, ServerPlayer player, SurfaceRules rules)
    {
        double max = Math.max(rules.minDistance(), rules.maxDistance());
        AABB box = new AABB(player.getX() - max, level.getMinBuildHeight(), player.getZ() - max,
                player.getX() + max, level.getMaxBuildHeight(), player.getZ() + max);

        return level.getEntitiesOfClass(Mob.class, box,
                human -> HumanEntityTypes.countsTowardSurfacePopulation(human)
                        && isWithinRing(player, human.blockPosition(), rules)).size();
    }

    /** The flat distance test that both the candidate positions and the population ring use. */
    private static boolean isWithinRing(ServerPlayer player, BlockPos pos, SurfaceRules rules)
    {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        double flatDistance = Math.sqrt(dx * dx + dz * dz);

        return flatDistance >= rules.minDistance() && flatDistance <= rules.maxDistance();
    }

    /** Proxy for backward compatibility with test seams that call the old signature. */
    public static void tick(ServerLevel level)
    {
        tick(level, HumanSpawnerHooks.forSurface());
    }

    private static void attempt(ServerLevel level, ServerPlayer player, SurfaceRules rules, HumanSpawnerHooks.SurfaceHooks hooks)
    {
        boolean day = level.isDay();
        float chance = day ? rules.dayChance() : rules.nightChance();

        if (level.random.nextFloat() >= chance)
        {
            hooks.onChanceReject();
            return;
        }

        // The density of the world, and not the dice, decides whether another human is wanted here. The count is the
        // very number the experience talks about ("how many survivors stand around me"), so the rules can ask for a
        // full day and a thin night without depending on how fast the humans of the ring happen to despawn.
        int target = day ? rules.dayTarget() : rules.nightTarget();

        if (target > 0 && countInRing(level, player, rules) >= target)
        {
            hooks.onTargetReachedSkip();
            return;
        }

        for (int tried = 0; tried < CHUNK_TRIES; ++tried)
        {
            ChunkPos chunk = randomChunkAround(level, player, rules);

            if (chunk == null)
            {
                hooks.onChunkNotLoadedSkip();
                return;
            }

            if (!level.getChunkSource().hasChunk(chunk.x, chunk.z))
            {
                // A chunk that is not loaded yet says nothing about the ring, so it is not a reason to end the attempt:
                // the try is handed to another chunk of the same ring. Ending the attempt here threw away most of the
                // attempts of a player whose loaded area is smaller than the ring, which is one more way the survivors
                // ended up rarer than the rules asked for.
                hooks.onChunkNotLoadedSkip();
                continue;
            }

            if (HumanPopulationCounter.countInChunk(level, chunk) >= Math.max(1, rules.perChunkCap()))
            {
                hooks.onPerChunkCapReject();
                continue;
            }

            EntityType<?> type = pickTier(level, rules);

            if (type == null)
            {
                hooks.onTypePickNull();
                return;
            }

            if (spawnInChunk(level, player, rules, hooks, chunk, type))
            {
                return;
            }
        }
    }

    /**
     * Tries to place one human inside that chunk.
     *
     * @return true when a human really joined the level; only then is the attempt of this tick over. A local
     *         population cap that is already reached is the one rejection that ends the attempt for good, because the
     *         humans around that player are dense enough already.
     */
    private static boolean spawnInChunk(ServerLevel level, ServerPlayer player, SurfaceRules rules,
            HumanSpawnerHooks.SurfaceHooks hooks, ChunkPos chunk, EntityType<?> type)
    {
        for (int i = 0; i < POSITION_TRIES; ++i)
        {
            hooks.onPositionTested();

            BlockPos pos = surfacePosition(level, chunk);

            if (!isWithinPlayerRange(player, pos, rules))
            {
                hooks.onRingReject();
                continue;
            }

            // The natural spawn gate of this phase: a peaceful world and a protected territory are asked before the
            // position itself, because neither of them is a property of the position. The territory core of Phase
            // LAIR-CORE-1 answers through MobSpawnEvent.PositionCheck for the vanilla spawner, but this spawner places
            // its humans itself and never fires that event, so it has to ask the rule itself - without this line a
            // core of a lair was simply not seen by the surface patrol.
            HumanSpawnValidator.Reason refusal = HumanSpawnValidator.rejectNaturalSpawnReason(level, pos, type, rules);

            if (refusal != null)
            {
                hooks.onValidatorReject(refusal);
                continue;
            }

            if (rules.localCap().enabled() && HumanPopulationCounter.countInRadius(level, pos.getX() + 0.5D, (double) pos.getY(), pos.getZ() + 0.5D, rules.localCap().radius()) >= rules.localCap().maxHumans())
            {
                hooks.onLocalCapReject();
                return true;
            }

            Mob spawned = HumanSpawnFactory.spawn(type, level, pos, MobSpawnType.NATURAL, HumanSpawnOrigin.NATURAL_SURFACE);

            if (spawned != null)
            {
                SpiderQueenHungryCraft.LOGGER.debug("[Spawn] surface {} at {} {} {} (day={})", EntityType.getKey(type), pos.getX(), pos.getY(), pos.getZ(), level.isDay());
                hooks.onSpawnSuccess();
                return true;
            }

            hooks.onSpawnFailed();
        }

        return false;
    }

    /** @return a random chunk in the ring around the player, or null when the rules describe no ring at all. */
    private static ChunkPos randomChunkAround(ServerLevel level, ServerPlayer player, SurfaceRules rules)
    {
        double min = Math.max(0.0D, rules.minDistance());
        double max = Math.max(min, rules.maxDistance());
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double distance = min + level.random.nextDouble() * (max - min);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);

        // The ring is measured in blocks, a ChunkPos is measured in chunks: without that conversion every attempt
        // would aim sixteen times too far away - into chunks that are not even loaded - and no human would ever be
        // placed, no matter how high the chances are.
        return new ChunkPos(x >> 4, z >> 4);
    }

    /** @return the first open spot of that chunk, taken from the heightmap so trees and caves are skipped. */
    private static BlockPos surfacePosition(ServerLevel level, ChunkPos chunk)
    {
        int x = chunk.getMinBlockX() + level.random.nextInt(16);
        int z = chunk.getMinBlockZ() + level.random.nextInt(16);

        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(x, level.getMinBuildHeight(), z));
    }

    /** The ring is measured flat, so a hill under the player can not push a candidate out of it. */
    private static boolean isWithinPlayerRange(ServerPlayer player, BlockPos pos, SurfaceRules rules)
    {
        return isWithinRing(player, pos, rules);
    }

    /** @return the tier to spawn, rolled by the weights of the rules, or null when none is allowed at all. */
    private static EntityType<?> pickTier(ServerLevel level, SurfaceRules rules)
    {
        List<TierWeight> weights = rules.tierWeights();
        int total = 0;

        for (TierWeight entry : weights)
        {
            if (isAllowed(entry, rules))
            {
                total += entry.weight();
            }
        }

        if (total <= 0)
        {
            return null;
        }

        int roll = level.random.nextInt(total);

        for (TierWeight entry : weights)
        {
            if (!isAllowed(entry, rules))
            {
                continue;
            }

            roll -= entry.weight();

            if (roll < 0)
            {
                return entry.entity();
            }
        }

        return null;
    }

    /**
     * A forbidden entity is never spawned naturally, no matter what weight the table gives it - and neither is a
     * special human (human tidy round).
     *
     * <p>Only an {@link spiderqueenhungrycraft.human.HumanArchetype#STANDARD ordinary human} may be placed by this
     * spawner. The shipped rules list the three prepared humans by hand anyway, so nothing about the current world
     * changes; this gate is what keeps a special human - Chad Steve, a future transcendent - out of the surface
     * patrol even when a pack author writes one into the tier table. A type that is skipped this way is reported
     * once, because a silently ignored line in a configuration file is the worst kind of bug.
     */
    private static boolean isAllowed(TierWeight entry, SurfaceRules rules)
    {
        if (entry.weight() <= 0 || rules.forbiddenEntities().contains(entry.entity()))
        {
            return false;
        }

        if (!HumanEntityTypes.isOrdinaryHumanType(entry.entity()))
        {
            if (WARNED_NOT_ORDINARY.add(entry.entity()))
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Spawn] {} is listed as a surface tier but is no ordinary human, "
                        + "this spawner never places it", EntityType.getKey(entry.entity()));
            }

            return false;
        }

        return true;
    }
}
