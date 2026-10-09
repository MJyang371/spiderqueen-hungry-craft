package spiderqueenhungrycraft.progression;

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
 * Reads the progression definition of the world (Progression Foundation round).
 *
 * <p>Every file under {@code data/<namespace>/progression/} is read on world start and on {@code /reload}. The file
 * of this mod, {@code spiderqueenhungrycraft:main}, is preferred; when it is not there, the first file that was found
 * is used, so a pack author can ship their own line under their own namespace.
 *
 * <p><b>A broken file never breaks a world.</b> Anything that is not a json object, a number that is not a whole
 * number and a value out of range are all warned about, and the definition that was in force stays exactly as it is.
 * When no file is found at all, the official numbers of this round are used and that is logged as well.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class ProgressionReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the definition lives in. */
    public static final String DIRECTORY = "progression";

    /** The definition of this mod, the one a world is meant to use. */
    public static final ResourceLocation MAIN_ID =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "main");

    private static final Gson GSON = new Gson();

    public ProgressionReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new ProgressionReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        if (files.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Progression] no {} file was found, using the official numbers",
                    DIRECTORY);
            ProgressionDefinitions.reset();
            return;
        }

        ResourceLocation chosen = files.containsKey(MAIN_ID) ? MAIN_ID : files.keySet().iterator().next();
        JsonElement element = files.get(chosen);

        if (element == null || !element.isJsonObject())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Progression] {} is not a json object, keeping the previous"
                    + " definition", chosen);
            return;
        }

        try
        {
            JsonObject root = element.getAsJsonObject();
            ProgressionDefinitions.set(ProgressionDefinition.parse(root));
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Progression] {} could not be read ({}), keeping the previous"
                    + " definition", chosen, e.getMessage());
        }
    }
}
