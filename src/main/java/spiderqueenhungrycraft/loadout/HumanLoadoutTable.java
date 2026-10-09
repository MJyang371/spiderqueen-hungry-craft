package spiderqueenhungrycraft.loadout;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The parsed loadout table of Phase HF0C.
 *
 * <p>Every number of this table lives in the data file, none of it in java: the budgets of the three tiers, the empty
 * slot chances, the item pools with their scores and per tier weights, and the drop rules. Java only knows what the
 * fields mean and how to roll with them.
 */
public final class HumanLoadoutTable
{
    /** The budgets and empty slot chances of one tier of the table. */
    public record TierData(int armorMin, int armorMax, int weaponMin, int weaponMax, int medicineMin, int medicineMax,
            float armorEmpty, float meleeEmpty, float bowEmpty, float shieldEmpty)
    {
    }

    /** How the equipment of a prepared human leaves its body. */
    public record DropRules(float equipmentDropChance, float medicineDropChance, boolean affectedByLooting,
            boolean dropEnchantments, float durabilityMin, float durabilityMax)
    {
    }

    /** The four armour slots in the order the table lists them. */
    private static final List<EquipmentSlot> ARMOR_ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private final String source;
    private final Map<HumanTier, TierData> tiers;
    private final List<HumanLoadoutEntry> armor;
    private final List<HumanLoadoutEntry> shield;
    private final List<HumanLoadoutEntry> melee;
    private final List<HumanLoadoutEntry> bow;
    private final List<HumanLoadoutEntry> medicine;
    private final DropRules drops;

    private HumanLoadoutTable(String source, Map<HumanTier, TierData> tiers, List<HumanLoadoutEntry> armor,
            List<HumanLoadoutEntry> shield, List<HumanLoadoutEntry> melee, List<HumanLoadoutEntry> bow,
            List<HumanLoadoutEntry> medicine, DropRules drops)
    {
        this.source = source;
        this.tiers = tiers;
        this.armor = armor;
        this.shield = shield;
        this.melee = melee;
        this.bow = bow;
        this.medicine = medicine;
        this.drops = drops;
    }

    /** @return the resource the table was read from, used by the log lines. */
    public String source()
    {
        return this.source;
    }

    public DropRules dropRules()
    {
        return this.drops;
    }

    /** @return true when this table has the tiers and the armour pool it needs to be worth using. */
    public boolean isUsable()
    {
        return !this.tiers.isEmpty() && !this.armor.isEmpty();
    }

    /**
     * Reads one loadout file.
     *
     * <p>A row whose item id is unknown to the game is skipped with one log line instead of failing the whole file,
     * so a pack author who writes a modded item by hand gets a readable message and not a broken world.
     */
    public static HumanLoadoutTable parse(JsonObject root, String source)
    {
        Map<HumanTier, TierData> tiers = new EnumMap<>(HumanTier.class);
        JsonObject tiersJson = root.has("tiers") ? root.getAsJsonObject("tiers") : null;

        for (HumanTier tier : HumanTier.values())
        {
            JsonObject node = tiersJson == null ? null : tiersJson.getAsJsonObject(tier.id());

            if (node == null)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Loadout] {} has no tier {}, that tier gets nothing", source,
                        tier.id());
                continue;
            }

            JsonObject empty = node.has("empty_slot_chance") ? node.getAsJsonObject("empty_slot_chance") : null;

            tiers.put(tier, new TierData(
                    rangeMin(node, "armor_budget", 0), rangeMax(node, "armor_budget", 0),
                    rangeMin(node, "weapon_budget", 0), rangeMax(node, "weapon_budget", 0),
                    rangeMin(node, "medicine_budget", 0), rangeMax(node, "medicine_budget", 0),
                    empty == null ? 0.0F : floatAt(empty, "armor", 0.0F),
                    empty == null ? 0.0F : floatAt(empty, "melee_weapon", 0.0F),
                    empty == null ? 0.0F : floatAt(empty, "bow", 0.0F),
                    empty == null ? 0.0F : floatAt(empty, "shield", 0.0F)));
        }

        JsonObject items = root.has("items") ? root.getAsJsonObject("items") : new JsonObject();
        JsonObject dropJson = root.has("drop_rules") ? root.getAsJsonObject("drop_rules") : new JsonObject();
        JsonObject durability = dropJson.has("dropped_durability_remaining_percent")
                ? dropJson.getAsJsonObject("dropped_durability_remaining_percent")
                : new JsonObject();

        DropRules drops = new DropRules(
                floatAt(dropJson, "equipment_drop_chance", 0.0F),
                floatAt(dropJson, "medicine_drop_chance", 0.0F),
                booleanAt(dropJson, "affected_by_looting", false),
                booleanAt(dropJson, "drop_enchantments", false),
                floatAt(durability, "min", 0.10F),
                floatAt(durability, "max", 0.45F));

        return new HumanLoadoutTable(source, tiers,
                parseEntries(items.getAsJsonArray("armor"), true, source),
                parseEntries(items.getAsJsonArray("shield"), false, source),
                parseEntries(items.getAsJsonArray("melee_weapon"), false, source),
                parseEntries(items.getAsJsonArray("bow"), false, source),
                parseEntries(items.getAsJsonArray("medicine"), false, source),
                drops);
    }

    private static List<HumanLoadoutEntry> parseEntries(JsonArray array, boolean withSlot, String source)
    {
        List<HumanLoadoutEntry> entries = new ArrayList<>();

        if (array == null)
        {
            return entries;
        }

        for (JsonElement element : array)
        {
            if (!element.isJsonObject())
            {
                continue;
            }

            JsonObject node = element.getAsJsonObject();
            String rawId = stringAt(node, "id", "");
            ResourceLocation id = ResourceLocation.tryParse(rawId);

            if (id == null || !BuiltInRegistries.ITEM.containsKey(id))
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Loadout] {} lists an unknown item {}, that row is skipped", source,
                        rawId);
                continue;
            }

            Item item = BuiltInRegistries.ITEM.get(id);
            HumanLoadoutEntry entry = new HumanLoadoutEntry(item, withSlot ? stringAt(node, "slot", "") : "",
                    intAt(node, "score", 0), intAt(node, "max_count", 1));

            if (node.has("weight"))
            {
                JsonObject weights = node.getAsJsonObject("weight");

                for (HumanTier tier : HumanTier.values())
                {
                    entry.setWeight(tier, intAt(weights, tier.weightKey(), 0));
                }
            }

            entries.add(entry);
        }

        return entries;
    }

    /**
     * Rolls the loadout of one freshly spawned prepared human.
     *
     * <p>The three pools are completely independent: armour and shield spend the armour budget, the blade and the bow
     * spend the weapon budget, and the golden apples spend the medicine budget. Nothing is ever taken from a pool to
     * pay for another one, and unspent budget is simply left unspent - nothing is forced onto a human just to fill a
     * slot.
     */
    public HumanLoadout roll(HumanTier tier, RandomSource random)
    {
        TierData data = this.tiers.get(tier);

        if (data == null)
        {
            return HumanLoadout.empty();
        }

        int armorBudget = rollBudget(data.armorMin(), data.armorMax(), random);
        int weaponBudget = rollBudget(data.weaponMin(), data.weaponMax(), random);
        int medicineBudget = rollBudget(data.medicineMin(), data.medicineMax(), random);

        // Armour: the slot order is shuffled, every slot may stay empty, every slot holds at most one piece.
        Map<EquipmentSlot, ItemStack> armor = new EnumMap<>(EquipmentSlot.class);
        List<EquipmentSlot> slots = new ArrayList<>(ARMOR_ORDER);

        // A plain Fisher-Yates on the random source of the entity, so the slot order differs from human to human.
        for (int i = slots.size() - 1; i > 0; --i)
        {
            int j = random.nextInt(i + 1);
            EquipmentSlot swap = slots.get(i);
            slots.set(i, slots.get(j));
            slots.set(j, swap);
        }

        int armorRemaining = armorBudget;

        for (EquipmentSlot slot : slots)
        {
            if (random.nextFloat() < data.armorEmpty())
            {
                continue;
            }

            HumanLoadoutEntry entry = pick(this.armor, tier, armorRemaining, slotName(slot), random);

            if (entry != null)
            {
                armor.put(slot, new ItemStack(entry.item()));
                armorRemaining -= entry.score();
            }
        }

        // Shield: the same armour pool, at most one.
        ItemStack shield = ItemStack.EMPTY;

        if (random.nextFloat() >= data.shieldEmpty())
        {
            HumanLoadoutEntry entry = pick(this.shield, tier, armorRemaining, null, random);

            if (entry != null)
            {
                shield = new ItemStack(entry.item());
                armorRemaining -= entry.score();
            }
        }

        // Blade first, then the bow, both from the weapon pool, at most one of each.
        ItemStack melee = ItemStack.EMPTY;
        int weaponRemaining = weaponBudget;

        if (random.nextFloat() >= data.meleeEmpty())
        {
            HumanLoadoutEntry entry = pick(this.melee, tier, weaponRemaining, null, random);

            if (entry != null)
            {
                melee = new ItemStack(entry.item());
                weaponRemaining -= entry.score();
            }
        }

        ItemStack bow = ItemStack.EMPTY;

        if (random.nextFloat() >= data.bowEmpty())
        {
            HumanLoadoutEntry entry = pick(this.bow, tier, weaponRemaining, null, random);

            if (entry != null)
            {
                bow = new ItemStack(entry.item());
                weaponRemaining -= entry.score();
            }
        }

        // Medicine: golden apples only in v0.1, each one costs its score, capped by the max count of the table.
        int apples = 0;
        HumanLoadoutEntry medicine = pick(this.medicine, tier, medicineBudget, null, random);

        if (medicine != null && medicine.score() > 0)
        {
            apples = Math.min(medicine.maxCount(), medicineBudget / medicine.score());
        }

        return new HumanLoadout(armor, shield, melee, bow, apples);
    }

    /** @return a weighted random entry of that pool that fits the budget and the slot, or null when none does. */
    private static HumanLoadoutEntry pick(List<HumanLoadoutEntry> pool, HumanTier tier, int budget, String wantedSlot,
            RandomSource random)
    {
        int total = 0;

        for (HumanLoadoutEntry entry : pool)
        {
            if (entry.fits(tier, budget, wantedSlot))
            {
                total += entry.weight(tier);
            }
        }

        if (total <= 0)
        {
            return null;
        }

        int roll = random.nextInt(total);

        for (HumanLoadoutEntry entry : pool)
        {
            if (!entry.fits(tier, budget, wantedSlot))
            {
                continue;
            }

            roll -= entry.weight(tier);

            if (roll < 0)
            {
                return entry;
            }
        }

        return null;
    }

    /** @return a random budget of that range, both ends included. */
    private static int rollBudget(int min, int max, RandomSource random)
    {
        return max <= min ? min : min + random.nextInt(max - min + 1);
    }

    private static String slotName(EquipmentSlot slot)
    {
        switch (slot)
        {
            case HEAD:
                return "head";
            case CHEST:
                return "chest";
            case LEGS:
                return "legs";
            case FEET:
                return "feet";
            default:
                return "";
        }
    }

    private static int rangeMin(JsonObject node, String key, int fallback)
    {
        return node.has(key) ? intAt(node.getAsJsonObject(key), "min", fallback) : fallback;
    }

    private static int rangeMax(JsonObject node, String key, int fallback)
    {
        return node.has(key) ? intAt(node.getAsJsonObject(key), "max", fallback) : fallback;
    }

    private static int intAt(JsonObject node, String key, int fallback)
    {
        return node.has(key) ? node.get(key).getAsInt() : fallback;
    }

    private static float floatAt(JsonObject node, String key, float fallback)
    {
        return node.has(key) ? node.get(key).getAsFloat() : fallback;
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
