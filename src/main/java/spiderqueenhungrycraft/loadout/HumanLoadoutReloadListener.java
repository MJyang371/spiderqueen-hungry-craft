package spiderqueenhungrycraft.loadout;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Reads the human loadout tables of the data pack (Phase HF0C).
 *
 * <p>Every file under {@code data/<namespace>/human_loadouts/} is read on world start and on {@code /reload}. The file
 * of this mod, {@code spiderqueenhungrycraft:default}, is preferred; when it is not there, the first file that was
 * found is used, so a pack author can ship their own table under their own namespace.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class HumanLoadoutReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the tables live in. */
    public static final String DIRECTORY = "human_loadouts";

    /** The table of this mod, the one the prepared humans are meant to use. */
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "default");

    private static final Gson GSON = new Gson();

    public HumanLoadoutReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new HumanLoadoutReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        if (files.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Loadout] no {} file was found, the prepared humans keep their "
                    + "built-in equipment", DIRECTORY);
            return;
        }

        ResourceLocation chosen = files.containsKey(DEFAULT_ID) ? DEFAULT_ID : files.keySet().iterator().next();
        JsonElement element = files.get(chosen);

        if (element == null || !element.isJsonObject())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Loadout] {} is not a json object, keeping the previous table", chosen);
            return;
        }

        try
        {
            JsonObject root = element.getAsJsonObject();
            HumanLoadoutTable parsed = HumanLoadoutTable.parse(root, chosen.toString());

            if (parsed.isUsable())
            {
                HumanLoadoutManager.setTable(parsed);
            }
            else
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Loadout] {} has no usable tiers or armour, keeping the previous "
                        + "table", chosen);
            }
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Loadout] {} could not be read ({}), keeping the previous table",
                    chosen, e.getMessage());
        }
    }
}
