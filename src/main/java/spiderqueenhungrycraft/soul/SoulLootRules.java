package spiderqueenhungrycraft.soul;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The rule book of the soul containers (Soul Consumable round): which family a container belongs to, which soul that
 * family is worth, and how many pieces a container may hold.
 *
 * <p><b>Nothing here knows a mod by name.</b> A rule is a list of {@link ResourceLocation}s - structure ids, structure
 * tags, loot table ids - and every mod that is not installed simply never matches: its ids are carried in the file,
 * compared as strings, and quietly do nothing. That is why YUNG, Repurposed Structures and Better Village can be
 * supported without one line of their code, without a compile dependency and without a hard dependency of any kind,
 * and why Hungry Craft starts, runs its game tests and fills vanilla chests exactly the same when none of them is
 * installed.
 *
 * <p><b>The family comes first, the loot table only refines.</b> A container inside a structure is worth what the
 * <i>structure</i> is - a village that borrowed the library loot table of a stronghold is still a village - and the
 * loot table is asked only twice: to name a sub type of that very family (a village smith, a shipwreck treasure, a
 * bastion treasure), and, when no structure owns the container at all, to name the family itself. That second use is
 * what covers the monster room of the game, which is a chest set into a wall and not a registered structure.
 *
 * <p><b>Overlapping structures.</b> When two structures both really cover the container, the family listed earlier in
 * the rule files wins. It is the only order the rule book has, so a pack that disagrees with a choice moves the family
 * up.
 *
 * <p><b>A broken file is skipped, never fatal.</b> A malformed entry is warned about and left out; every other entry of
 * the reload is still loaded, and a world with no usable rule at all simply keeps its loot untouched.
 */
public final class SoulLootRules
{
    /** The highest number a container may hold: the weights have one entry per possible count, zero to this. */
    public static final int MAX_COUNT = 4;

    /**
     * What a container is worth once the dice are rolled: a family, one soul item and how many of it.
     *
     * <p>A drop is never built with a count below one - an empty container is not a drop at all, it is the absence of
     * one - so nothing downstream has to wonder what a soul of zero pieces would mean.
     */
    public record Drop(SoulFamily family, ResourceLocation soul, int count)
    {
    }

    /** What a container is worth before the dice are rolled: the family and the one soul that family pays. */
    public record Plan(SoulFamily family, ResourceLocation soul)
    {
    }

    /** One family of the rule book: what it is worth and every structure and structure tag that claims it. */
    private record Family(SoulFamily family, ResourceLocation soul, Set<ResourceLocation> structures,
            Set<ResourceLocation> tags)
    {
        /** @return true when any of the structures of that container, or any tag it carries, is this family. */
        boolean claims(Collection<ResourceLocation> structureIds, Collection<ResourceLocation> tagIds)
        {
            for (ResourceLocation id : structureIds)
            {
                if (this.structures.contains(id))
                {
                    return true;
                }
            }

            for (ResourceLocation id : tagIds)
            {
                if (this.tags.contains(id))
                {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * One loot table of the rule book: the family it belongs to and, for a sub type, the soul that replaces the default.
     *
     * <p>A null soul means "the soul of the family" and never "no soul": a table without a soul of its own only ever
     * sharpens the count of a container, it never silences one.
     */
    private record Table(SoulFamily family, @Nullable ResourceLocation soul)
    {
    }

    /** The whole rule book, replaced as one piece on every reload so no reader ever sees it half written. */
    private static final class Book
    {
        /** Weights indexed by the count they give: entry zero is the chance of an empty container. */
        private final List<Integer> weights;

        /** In the order the files listed them: that order is the priority between overlapping families. */
        private final List<Family> families;

        /** Every loot table a family or a sub type is known by. */
        private final Map<ResourceLocation, Table> tables;

        /** The tables that never hold a soul, however rich the structure around them is. */
        private final Set<ResourceLocation> ignored;

        private Book(List<Integer> weights, List<Family> families, Map<ResourceLocation, Table> tables,
                Set<ResourceLocation> ignored)
        {
            this.weights = List.copyOf(weights);
            this.families = List.copyOf(families);
            this.tables = Map.copyOf(tables);
            this.ignored = Set.copyOf(ignored);
        }

        /** An empty rule book: no family, no table, no dice - every container of the world keeps its own loot. */
        private static Book empty()
        {
            return new Book(zeroWeights(), List.of(), Map.of(), Set.of());
        }

        /** @return the soul that family pays, or null when the rule book does not know that family at all. */
        @Nullable
        private ResourceLocation soulOf(SoulFamily family)
        {
            for (Family candidate : this.families)
            {
                if (candidate.family() == family)
                {
                    return candidate.soul();
                }
            }

            return null;
        }
    }

    private static volatile Book book = Book.empty();

    private SoulLootRules()
    {
    }

    // ------------------------------------------------------------------ reading a container

    /**
     * The one question the loot modifier asks: what is this container worth?
     *
     * <p>It is pure - no world, no registry, no dice - so the whole mapping can be proven by a game test without a
     * chest, and so a rule can never depend on anything but the ids it was handed.
     *
     * @param structureIds  the structures that really own the container, in any order
     * @param structureTags the tags those structures carry, in any order
     * @param lootTable     the loot table that is being filled
     * @return the family and the soul of that container, or empty when this mod has nothing to say about it
     */
    public static Optional<Plan> resolve(Collection<ResourceLocation> structureIds,
            Collection<ResourceLocation> structureTags, @Nullable ResourceLocation lootTable)
    {
        Book current = book;

        // A dispenser is not a treasure container. The rule book names the tables that are refused even when the
        // structure around them would pay, so the dispenser of a jungle temple is never touched.
        if (lootTable != null && current.ignored.contains(lootTable))
        {
            return Optional.empty();
        }

        SoulFamily family = null;

        for (Family candidate : current.families)
        {
            if (candidate.claims(structureIds, structureTags))
            {
                family = candidate.family();
                break;
            }
        }

        Table table = lootTable == null ? null : current.tables.get(lootTable);
        ResourceLocation soul = null;

        if (family == null)
        {
            // No structure owns this container, so the loot table is the only thing left that can name a family -
            // the monster room of the game and a chest a mod placed by hand both arrive here.
            if (table == null)
            {
                return Optional.empty();
            }

            family = table.family();
            soul = table.soul();
        }
        else if (table != null && table.family() == family)
        {
            // The structure decides the family; the table may only sharpen it from within - a village smith is a
            // village, and a village that borrowed the library table of a stronghold is still only a village.
            soul = table.soul();
        }

        if (soul == null)
        {
            soul = current.soulOf(family);
        }

        return soul == null ? Optional.empty() : Optional.of(new Plan(family, soul));
    }

    /**
     * Rolls how many pieces a container holds, from the weights of the rule book.
     *
     * <p>The random source is the one of the loot context and never a private generator: the dice of a world have to be
     * the dice of its loot generation, or a seed stops reproducing the world it describes.
     *
     * @return a count between zero and {@link #MAX_COUNT}; zero means the container keeps exactly what it generated.
     */
    public static int rollCount(RandomSource random)
    {
        List<Integer> weights = book.weights;
        int total = 0;

        for (int weight : weights)
        {
            total += weight;
        }

        if (total <= 0)
        {
            return 0;
        }

        int roll = random.nextInt(total);

        for (int count = 0; count < weights.size(); count++)
        {
            roll -= weights.get(count);

            if (roll < 0)
            {
                return count;
            }
        }

        return 0;
    }

    /**
     * The whole decision in one call: what this container is worth and how many pieces of it.
     *
     * <p>An empty optional is the answer for every container this mod has nothing to say about <b>and</b> for every
     * container whose dice came up zero, because the two mean the same thing to the loot: nothing is added.
     */
    public static Optional<Drop> roll(Collection<ResourceLocation> structureIds,
            Collection<ResourceLocation> structureTags, @Nullable ResourceLocation lootTable, RandomSource random)
    {
        Optional<Plan> plan = resolve(structureIds, structureTags, lootTable);

        if (plan.isEmpty())
        {
            return Optional.empty();
        }

        int count = rollCount(random);

        return count <= 0 ? Optional.empty() : Optional.of(new Drop(plan.get().family(), plan.get().soul(), count));
    }

    // ------------------------------------------------------------------ what a reader may ask

    /** @return the weights of the rule book, one per possible count, from zero to {@link #MAX_COUNT}. */
    public static List<Integer> countWeights()
    {
        return book.weights;
    }

    /** @return true when this loot table is refused even inside a family that would pay - a dispenser, for instance. */
    public static boolean isIgnored(ResourceLocation lootTable)
    {
        return book.ignored.contains(lootTable);
    }

    /** @return the family that claims that structure id, empty when no rule of this world knows it. */
    public static Optional<SoulFamily> familyOfStructure(ResourceLocation structureId)
    {
        for (Family family : book.families)
        {
            if (family.structures().contains(structureId))
            {
                return Optional.of(family.family());
            }
        }

        return Optional.empty();
    }

    /** @return the family that claims that structure tag, empty when no rule of this world knows it. */
    public static Optional<SoulFamily> familyOfTag(ResourceLocation tagId)
    {
        for (Family family : book.families)
        {
            if (family.tags().contains(tagId))
            {
                return Optional.of(family.family());
            }
        }

        return Optional.empty();
    }

    /** @return the soul that family pays, empty when the family is not part of the rule book. */
    public static Optional<ResourceLocation> soulOf(SoulFamily family)
    {
        return Optional.ofNullable(book.soulOf(family));
    }

    /** @return how many families the rule book declares. */
    public static int familyCount()
    {
        return book.families.size();
    }

    /** @return how many structure ids and structure tags the whole rule book knows, for the log line of a reload. */
    public static int structureAliasCount()
    {
        int total = 0;

        for (Family family : book.families)
        {
            total += family.structures().size() + family.tags().size();
        }

        return total;
    }

    /** @return how many loot tables the rule book knows, for the log line of a reload. */
    public static int tableCount()
    {
        return book.tables.size();
    }

    // ------------------------------------------------------------------ the reload and its reading

    /**
     * Reads every file of the folder, in the order of its name, and replaces the rule book.
     *
     * <p><b>Files are merged, never replaced wholesale</b>, so a pack adds an alias by adding a file that holds that
     * alias and does not have to repeat the rule book of this mod. Within one key the later file - the one whose name
     * sorts higher - wins, which turns an override into a file named after the one it overrides.
     */
    static void applyFiles(Map<ResourceLocation, JsonElement> files)
    {
        Map<ResourceLocation, Family> families = new LinkedHashMap<>();
        Map<ResourceLocation, Table> tables = new HashMap<>();
        Set<ResourceLocation> ignored = new HashSet<>();
        List<Integer> weights = zeroWeights();
        boolean weightsSeen = false;

        for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet())
        {
            try
            {
                JsonElement element = file.getValue();

                if (element == null || !element.isJsonObject())
                {
                    SpiderQueenHungryCraft.LOGGER.warn("[SoulLoot] {} is not a json object, skipped", file.getKey());
                    continue;
                }

                JsonObject root = element.getAsJsonObject();

                if (root.has("count_weights"))
                {
                    weights = readWeights(root.get("count_weights"), file.getKey());
                    weightsSeen = true;
                }

                readFamilies(root, file.getKey(), families);
                readTables(root, file.getKey(), tables);
                ignored.addAll(readIds(root.get("ignored_loot_tables")));
            }
            catch (RuntimeException e)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[SoulLoot] {} could not be read ({}), skipped", file.getKey(),
                        e.getMessage());
            }
        }

        if (!weightsSeen)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[SoulLoot] no file declares count_weights, no container holds a soul");
        }

        book = new Book(weights, List.copyOf(families.values()), tables, ignored);

        SpiderQueenHungryCraft.LOGGER.info("[SoulLoot] {} families, {} structure aliases, {} loot tables, {} refused tables",
                familyCount(), structureAliasCount(), tableCount(), ignored.size());
    }

    /** Puts the rule book back to empty. Used by a reload that found nothing usable, and by the game tests. */
    static void reset()
    {
        book = Book.empty();
    }

    private static List<Integer> zeroWeights()
    {
        List<Integer> zeros = new ArrayList<>(MAX_COUNT + 1);

        for (int count = 0; count <= MAX_COUNT; count++)
        {
            zeros.add(0);
        }

        return zeros;
    }

    /** @return the weights of that key, which has to hold one number per possible count and no negative one. */
    private static List<Integer> readWeights(JsonElement element, ResourceLocation file)
    {
        if (!element.isJsonArray())
        {
            throw new IllegalArgumentException("count_weights must be a list");
        }

        JsonArray array = element.getAsJsonArray();

        if (array.size() != MAX_COUNT + 1)
        {
            throw new IllegalArgumentException(
                    "count_weights must hold " + (MAX_COUNT + 1) + " weights, zero to " + MAX_COUNT);
        }

        List<Integer> weights = new ArrayList<>(array.size());

        for (int index = 0; index < array.size(); index++)
        {
            int weight = array.get(index).getAsInt();

            if (weight < 0)
            {
                throw new IllegalArgumentException("count_weights may not hold a negative weight");
            }

            weights.add(weight);
        }

        SpiderQueenHungryCraft.LOGGER.debug("[SoulLoot] {} sets the container weights {}", file, weights);
        return weights;
    }

    /**
     * Reads the families of one file into the map of the reload.
     *
     * <p>A family that a second file names again is <b>not</b> a second family: its aliases are added to the ones that
     * are already there and the soul of the later file wins. That is what lets a pack extend the village family with
     * the villages of another mod without touching the file of this mod.
     */
    private static void readFamilies(JsonObject root, ResourceLocation file, Map<ResourceLocation, Family> families)
    {
        JsonElement element = root.get("families");

        if (element == null || element.isJsonNull())
        {
            return;
        }

        if (!element.isJsonArray())
        {
            throw new IllegalArgumentException("families must be a list");
        }

        for (JsonElement entry : element.getAsJsonArray())
        {
            if (!entry.isJsonObject())
            {
                throw new IllegalArgumentException("every family must be an object");
            }

            JsonObject family = entry.getAsJsonObject();
            SoulFamily id = readFamily(family, file);
            ResourceLocation soul = readSoul(family, file);
            ResourceLocation key = familyKey(id);
            Family known = families.get(key);
            Set<ResourceLocation> structures = new LinkedHashSet<>();
            Set<ResourceLocation> tags = new LinkedHashSet<>();

            if (known != null)
            {
                structures.addAll(known.structures());
                tags.addAll(known.tags());
            }

            structures.addAll(readIds(family.get("structures")));
            tags.addAll(readIds(family.get("structure_tags")));

            families.put(key, new Family(id, soul, Set.copyOf(structures), Set.copyOf(tags)));
        }
    }

    /** Reads the loot tables of one file into the map of the reload, each of them over the entry it had before. */
    private static void readTables(JsonObject root, ResourceLocation file, Map<ResourceLocation, Table> tables)
    {
        JsonElement element = root.get("loot_tables");

        if (element == null || element.isJsonNull())
        {
            return;
        }

        if (!element.isJsonArray())
        {
            throw new IllegalArgumentException("loot_tables must be a list");
        }

        for (JsonElement entry : element.getAsJsonArray())
        {
            if (!entry.isJsonObject())
            {
                throw new IllegalArgumentException("every loot table must be an object");
            }

            JsonObject table = entry.getAsJsonObject();
            ResourceLocation id = readId(table, "table", file);
            SoulFamily family = readFamily(table, file);
            ResourceLocation soul = readSoulOrNull(table, file);

            tables.put(id, new Table(family, soul));
        }
    }

    /**
     * The key a family is stored under inside one reload: the family itself, so that however many files name the same
     * family they end up as one entry. It is never written to a file - a file names the family, not the key.
     */
    private static ResourceLocation familyKey(SoulFamily family)
    {
        return ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, family.id());
    }

    /** @return the family of that entry, which has to name one of the families this mod knows. */
    private static SoulFamily readFamily(JsonObject entry, ResourceLocation file)
    {
        String name = readText(entry, "family", file);

        return SoulFamily.byId(name.toLowerCase(Locale.ROOT).trim())
                .orElseThrow(() -> new IllegalArgumentException("family names one this mod does not know: " + name));
    }

    /** @return the soul of that entry, which a family always has to name. */
    private static ResourceLocation readSoul(JsonObject entry, ResourceLocation file)
    {
        ResourceLocation soul = readSoulOrNull(entry, file);

        if (soul == null)
        {
            throw new IllegalArgumentException("a family has to name the soul it pays");
        }

        return soul;
    }

    /**
     * @return the soul of that entry, or null when it names none.
     *
     *         <p>A soul has to be an item that really exists, so a rule that names a soul of a mod that is not
     *         installed is refused at load and can never hand the world an empty stack.
     */
    @Nullable
    private static ResourceLocation readSoulOrNull(JsonObject entry, ResourceLocation file)
    {
        JsonElement element = entry.get("soul");

        if (element == null || element.isJsonNull())
        {
            return null;
        }

        ResourceLocation soul = ResourceLocation.tryParse(element.getAsString());

        if (soul == null)
        {
            throw new IllegalArgumentException("soul must be the identity of an item");
        }

        if (!BuiltInRegistries.ITEM.containsKey(soul))
        {
            throw new IllegalArgumentException("soul names an item that does not exist: " + soul);
        }

        return soul;
    }

    /**
     * @return the identities of that key, empty when it is absent.
     *
     *         <p>An identity of a mod that is not installed is <b>not</b> an error: it is kept exactly as written and
     *         simply never matches anything in a world without that mod. That is the whole trick behind the optional
     *         compatibility of this round - the rules of another mod are data of this mod.
     */
    private static Set<ResourceLocation> readIds(@Nullable JsonElement element)
    {
        if (element == null || element.isJsonNull())
        {
            return Set.of();
        }

        if (!element.isJsonArray())
        {
            throw new IllegalArgumentException("aliases must be a list");
        }

        Set<ResourceLocation> ids = new LinkedHashSet<>();

        for (JsonElement entry : element.getAsJsonArray())
        {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());

            if (id == null)
            {
                throw new IllegalArgumentException("an alias is not an identity: " + entry);
            }

            ids.add(id);
        }

        return ids;
    }

    private static ResourceLocation readId(JsonObject entry, String key, ResourceLocation file)
    {
        ResourceLocation id = ResourceLocation.tryParse(readText(entry, key, file));

        if (id == null)
        {
            throw new IllegalArgumentException(key + " must be a namespaced identity");
        }

        return id;
    }

    private static String readText(JsonObject entry, String key, ResourceLocation file)
    {
        JsonElement element = entry.get(key);

        if (element == null || !element.isJsonPrimitive() || element.getAsString().isBlank())
        {
            throw new IllegalArgumentException(key + " is missing in " + file);
        }

        return element.getAsString();
    }
}
