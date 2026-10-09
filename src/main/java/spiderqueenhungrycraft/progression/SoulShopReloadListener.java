package spiderqueenhungrycraft.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Reads the products of the soul shop (Progression Foundation round).
 *
 * <p><b>One file is one product.</b> Every file under {@code data/<namespace>/soul_shop/} becomes an entry, named
 * after its own path, so a pack adds a product by adding a file and removes one by deleting it. The shop of this
 * round ships exactly one file, and the interface builds itself from what was read - nothing in the drawing code
 * knows what a spider egg is.
 *
 * <p><b>A broken product is skipped, never fatal.</b> A missing item, a count below one or a negative price is
 * warned about and left out; every other product of the same reload is still loaded. An empty result is a valid
 * shop - an empty shop simply sells nothing.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SoulShopReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the products live in. */
    public static final String DIRECTORY = "soul_shop";

    /** The bounds a product has to stay inside, so no file can hand out an absurd stack or an absurd price. */
    public static final int MAX_COUNT = 6400;
    public static final long MAX_PRICE = 1_000_000_000L;

    private static final Gson GSON = new Gson();

    public SoulShopReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new SoulShopReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        List<ShopEntry> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet())
        {
            ShopEntry entry = read(file.getKey(), file.getValue());

            if (entry != null)
            {
                loaded.add(entry);
            }
        }

        if (loaded.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] no usable {} product was found, the shop stays empty",
                    DIRECTORY);
            SoulShop.reset();
            return;
        }

        SoulShop.set(loaded);
    }

    /** @return the product of that file, or null when it is not usable - which was warned about. */
    private static ShopEntry read(ResourceLocation id, JsonElement element)
    {
        if (element == null || !element.isJsonObject())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} is not a json object, skipped", id);
            return null;
        }

        try
        {
            JsonObject root = element.getAsJsonObject();
            String itemName = string(root, "item");
            ResourceLocation itemId = ResourceLocation.tryParse(itemName);
            double countValue = number(root, "count", 1.0D);
            double priceValue = number(root, "price", 0.0D);
            boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();

            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId))
            {
                SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} names an item that does not exist ({}), skipped",
                        id, itemName);
                return null;
            }

            Item item = BuiltInRegistries.ITEM.get(itemId);

            if (item == null || item == Items.AIR)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} resolves to air, skipped", id);
                return null;
            }

            if (countValue != Math.floor(countValue) || countValue <= 0.0D || countValue > MAX_COUNT)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} has an unusable count ({}), skipped", id,
                        countValue);
                return null;
            }

            if (priceValue != Math.floor(priceValue) || priceValue < 0.0D || priceValue > MAX_PRICE)
            {
                SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} has an unusable price ({}), skipped", id,
                        priceValue);
                return null;
            }

            return new ShopEntry(id, item, (int)countValue, (long)priceValue, enabled, readSkill(root));
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[SoulShop] {} could not be read ({}), skipped", id, e.getMessage());
            return null;
        }
    }

    /**
     * @return the skill that product hangs off, or null when it is for everybody.
     *
     *         <p>A requirement that is not an identity is refused as a whole instead of being dropped: a product whose
     *         gate could not be read would otherwise be on sale for everybody, which is the one failure mode of this
     *         field that must not exist (Skill Progression round).
     *
     * @throws IllegalArgumentException when the value is there but is not an identity.
     */
    private static ResourceLocation readSkill(JsonObject root)
    {
        String name = string(root, "requires_skill");

        if (name.isBlank())
        {
            return null;
        }

        ResourceLocation skill = ResourceLocation.tryParse(name);

        if (skill == null)
        {
            throw new IllegalArgumentException("requires_skill must be a namespaced identity (" + name + ")");
        }

        return skill;
    }

    /** @return the text of that key, or an empty string when it is absent. */
    private static String string(JsonObject root, String key)
    {
        JsonElement element = root.get(key);

        return element == null || !element.isJsonPrimitive() ? "" : element.getAsString();
    }

    /** @return the number of that key, the fallback when it is absent, or {@code NaN} when it is not a number. */
    private static double number(JsonObject root, String key, double fallback)
    {
        JsonElement element = root.get(key);

        if (element == null || element.isJsonNull())
        {
            return fallback;
        }

        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsDouble()
                : Double.NaN;
    }
}
