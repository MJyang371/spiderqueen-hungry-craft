package spiderqueenhungrycraft.human.spawn;

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
 * Reads the human spawn rules of the data pack (Phase HF0D).
 *
 * <p>Every file under {@code data/<namespace>/human_spawns/} is read on world start and on {@code /reload}. The file
 * of this mod, {@code spiderqueenhungrycraft:default}, is preferred; when it is not there, the first file that was
 * found is used, so a pack author can ship their own rules under their own namespace.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class HumanSpawnReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the spawn rules live in. */
    public static final String DIRECTORY = "human_spawns";

    /** The rules of this mod, the ones the humans of this mod spawn by. */
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "default");

    private static final Gson GSON = new Gson();

    public HumanSpawnReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new HumanSpawnReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        if (files.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Spawn] no {} file was found, no human spawns naturally", DIRECTORY);
            return;
        }

        ResourceLocation chosen = files.containsKey(DEFAULT_ID) ? DEFAULT_ID : files.keySet().iterator().next();
        JsonElement element = files.get(chosen);

        if (element == null || !element.isJsonObject())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Spawn] {} is not a json object, keeping the previous rules", chosen);
            return;
        }

        try
        {
            HumanSpawnRules.setConfig(HumanSpawnConfig.parse(element.getAsJsonObject(), chosen.toString()));
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Spawn] {} could not be read ({}), keeping the previous rules", chosen,
                    e.getMessage());
        }
    }
}
