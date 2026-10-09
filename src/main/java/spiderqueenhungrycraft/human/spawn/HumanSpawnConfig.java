package spiderqueenhungrycraft.human.spawn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The parsed spawn rules of Phase HF0D.
 *
 * <p>Every number of this file lives in the data pack, none of it in java: the day and night chances, the distances,
 * the caps, the tier weights and the forbidden lists. Java only knows what the fields mean.
 */
public final class HumanSpawnConfig
{
    /** The local cap around a spawn attempt. */
    public record LocalCap(boolean enabled, double radius, int maxHumans)
    {
    }

    /** One entry of the tier weight table. */
    public record TierWeight(EntityType<?> entity, int weight)
    {
    }

    /**
     * Everything about the surface patrol spawner.
     *
     * <p>{@code dayTarget} and {@code nightTarget} are the population the spawner aims at inside the ring around one
     * player, and they are what tells the two times of day apart: while the ring holds fewer humans than the target of
     * that time of day an attempt may add one, and once the ring holds enough the attempt is dropped before a chunk is
     * even looked at. Without them the density of the world would be a side effect of the chance and of how fast the
     * survivors despawn, which is exactly what the players do not see; with them the day stays as full as the rules ask
     * and the night stays as thin as the rules ask, no matter how a world happens to be loaded.
     *
     * <p>A target of {@code 0} means "no target": the caps are then the only limit. That is what the spawn rules of the
     * game test use, so the test can prove the mechanics without being stopped by a number that describes an experience
     * and not the code.
     */
    public record SurfaceRules(boolean enabled, Set<ResourceLocation> dimensions, int attemptIntervalTicks,
            float dayChance, float nightChance, int dayTarget, int nightTarget, double minDistance, double maxDistance,
            int perChunkCap, LocalCap localCap, boolean surfaceOnly, boolean mustSeeSky, boolean fullBlockGround,
            List<TagKey<Block>> forbiddenGroundTags, List<TierWeight> tierWeights, Set<EntityType<?>> forbiddenEntities)
    {
    }

    /** Everything about the one time village garrison. */
    public record GarrisonRules(boolean enabled, boolean oncePerVillage, int highMin, int highMax, int chadCount,
            boolean onePerChunk, boolean chadFirst, boolean sameRulesAsSurface, int checkIntervalTicks,
            int chunksPerPlayer, double positionSearchRadius, int villagePoiRadius)
    {
    }

    private final String source;
    private final boolean enabled;
    private final SurfaceRules surface;
    private final GarrisonRules garrison;

    private HumanSpawnConfig(String source, boolean enabled, SurfaceRules surface, GarrisonRules garrison)
    {
        this.source = source;
        this.enabled = enabled;
        this.surface = surface;
        this.garrison = garrison;
    }

    /** @return the resource the rules were read from, used by the log lines. */
    public String source()
    {
        return this.source;
    }

    public boolean enabled()
    {
        return this.enabled;
    }

    public SurfaceRules surface()
    {
        return this.surface;
    }

    public GarrisonRules garrison()
    {
        return this.garrison;
    }

    /** @return true when the surface spawner may run at all. */
    public boolean surfaceEnabled()
    {
        return this.enabled && this.surface.enabled();
    }

    /** @return true when the garrison spawner may run at all. */
    public boolean garrisonEnabled()
    {
        return this.enabled && this.garrison.enabled();
    }

    /**
     * Reads one spawn rules file.
     *
     * <p>An entity or block id that the game does not know is skipped with one log line, so a pack author who writes a
     * modded id by hand gets a readable message instead of a broken world. A value of the wrong json type is a hard
     * error: it is logged with the field that is wrong, and the caller keeps the rules it had, because a half read
     * table of spawn weights would spawn a world nobody described.
     */
    public static HumanSpawnConfig parse(JsonObject root, String source)
    {
        try
        {
            return parseStrict(root, source);
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.error("[Spawn] {} is not a readable spawn rules file ({}), the previous "
                    + "rules stay in place", source, e.getMessage());
            throw e;
        }
    }

    private static HumanSpawnConfig parseStrict(JsonObject root, String source)
    {
        boolean enabled = booleanAt(root, "enabled", true);

        JsonObject surfaceJson = objectAt(root, "natural_surface");
        Set<ResourceLocation> dimensions = new HashSet<>();

        for (String id : stringList(surfaceJson, "dimensions"))
        {
            ResourceLocation location = ResourceLocation.tryParse(id);

            if (location != null)
            {
                dimensions.add(location);
            }
        }

        if (dimensions.isEmpty())
        {
            dimensions.add(Level.OVERWORLD.location());
        }

        JsonObject localCapJson = objectAt(surfaceJson, "local_radius_cap");
        LocalCap localCap = new LocalCap(booleanAt(localCapJson, "enabled", true),
                doubleAt(localCapJson, "radius", 48.0D), intAt(localCapJson, "max_humans", 3));

        SurfaceRules surface = new SurfaceRules(
                booleanAt(surfaceJson, "enabled", true),
                dimensions,
                intAt(surfaceJson, "attempt_interval_ticks", 600),
                floatAt(surfaceJson, "day_spawn_chance", 0.20F),
                floatAt(surfaceJson, "night_spawn_chance", 0.06F),
                intAt(surfaceJson, "day_target_population", 0),
                intAt(surfaceJson, "night_target_population", 0),
                doubleAt(surfaceJson, "min_distance_from_player", 24.0D),
                doubleAt(surfaceJson, "max_distance_from_player", 96.0D),
                intAt(surfaceJson, "per_chunk_human_cap", 1),
                localCap,
                booleanAt(surfaceJson, "surface_only", true),
                booleanAt(surfaceJson, "must_see_sky", true),
                booleanAt(surfaceJson, "ground_must_be_full_block", true),
                forbiddenGroundTags(surfaceJson, source),
                tierWeights(surfaceJson, source),
                forbiddenEntities(surfaceJson, source));

        JsonObject garrisonJson = objectAt(root, "village_garrison");
        JsonObject highCount = objectAt(garrisonJson, "high_prepared_human_count");

        GarrisonRules garrison = new GarrisonRules(
                booleanAt(garrisonJson, "enabled", true),
                booleanAt(garrisonJson, "once_per_village", true),
                intAt(highCount, "min", 2),
                intAt(highCount, "max", 3),
                intAt(garrisonJson, "chad_steve_count", 1),
                booleanAt(garrisonJson, "try_to_keep_one_human_per_chunk", true),
                booleanAt(garrisonJson, "spawn_chad_first", true),
                booleanAt(garrisonJson, "valid_position_rules_same_as_surface", true),
                intAt(garrisonJson, "check_interval_ticks", 100),
                intAt(garrisonJson, "chunks_checked_per_player", 4),
                doubleAt(garrisonJson, "position_search_radius", 32.0D),
                intAt(garrisonJson, "village_poi_radius", 48));

        return new HumanSpawnConfig(source, enabled, surface, garrison);
    }

    /**
     * @return the ground tags a spawn position may not stand on. The vanilla leaf tag is always part of the list, the
     *         file may add more, so a pack author can never accidentally spawn a human on top of a tree.
     */
    private static List<TagKey<Block>> forbiddenGroundTags(JsonObject node, String source)
    {
        List<TagKey<Block>> tags = new ArrayList<>();
        tags.add(BlockTags.LEAVES);

        for (String id : stringList(node, "forbidden_ground_block_tags"))
        {
            ResourceLocation location = ResourceLocation.tryParse(id);

            if (location == null)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Spawn] {} lists an invalid block tag {}, it is skipped", source,
                        id);
                continue;
            }

            TagKey<Block> tag = TagKey.create(Registries.BLOCK, location);

            if (!tags.contains(tag))
            {
                tags.add(tag);
            }
        }

        return tags;
    }

    private static List<TierWeight> tierWeights(JsonObject node, String source)
    {
        List<TierWeight> weights = new ArrayList<>();

        if (!node.has("tier_weights") || !node.get("tier_weights").isJsonArray())
        {
            return weights;
        }

        for (JsonElement element : node.getAsJsonArray("tier_weights"))
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject entry = element.getAsJsonObject();
            EntityType<?> type = entityType(stringAt(entry, "entity", ""), source);

            if (type != null)
            {
                weights.add(new TierWeight(type, intAt(entry, "weight", 0)));
            }
        }

        return weights;
    }

    private static Set<EntityType<?>> forbiddenEntities(JsonObject node, String source)
    {
        Set<EntityType<?>> types = new HashSet<>();

        for (String id : stringList(node, "forbidden_entities"))
        {
            EntityType<?> type = entityType(id, source);

            if (type != null)
            {
                types.add(type);
            }
        }

        return types;
    }

    private static EntityType<?> entityType(String id, String source)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);

        if (location == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(location))
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Spawn] {} lists an unknown entity {}, it is skipped", source, id);
            return null;
        }

        return BuiltInRegistries.ENTITY_TYPE.get(location);
    }

    private static JsonObject objectAt(JsonObject node, String key)
    {
        return node != null && node.has(key) && node.get(key).isJsonObject() ? node.getAsJsonObject(key)
                : new JsonObject();
    }

    private static List<String> stringList(JsonObject node, String key)
    {
        List<String> values = new ArrayList<>();

        if (node == null || !node.has(key) || !node.get(key).isJsonArray())
        {
            return values;
        }

        JsonArray array = node.getAsJsonArray(key);

        for (JsonElement element : array)
        {
            values.add(element.getAsString());
        }

        return values;
    }

    /** Reads a number that may be written as {@code 40} or as {@code 40.0}. */
    private static int intAt(JsonObject node, String key, int fallback)
    {
        return node.has(key) ? integer(node, key, node.get(key).getAsDouble()) : fallback;
    }

    /** Reads a number that may be written as {@code 0.55} or as {@code 0.55F}. */
    private static float floatAt(JsonObject node, String key, float fallback)
    {
        return node.has(key) ? (float) node.get(key).getAsDouble() : fallback;
    }

    private static double doubleAt(JsonObject node, String key, double fallback)
    {
        return node.has(key) ? node.get(key).getAsDouble() : fallback;
    }

    private static int integer(JsonObject node, String key, double value)
    {
        if (value != Math.rint(value))
        {
            throw new IllegalStateException(key + " is " + value + ", which is not a whole number");
        }

        return (int) value;
    }

    private static boolean booleanAt(JsonObject node, String key, boolean fallback)
    {
        return node.has(key) ? node.get(key).getAsBoolean() : fallback;
    }

    private static String stringAt(JsonObject node, String key, String fallback)
    {
        return node.has(key) ? node.get(key).getAsString() : fallback;
    }
}
