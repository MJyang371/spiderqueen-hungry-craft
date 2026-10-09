package spiderqueenhungrycraft.beast;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.server.ServerLifecycleHooks;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Reads the datapack tuning of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>Every {@code data/<namespace>/queen_beast/*.json} of the current pack set is parsed on server start and
 * on every {@code /reload}, so a modpack can retune the armor, the bite volume and the bite camera impulse
 * without touching the mod. The result is stored in {@link QueenBeastTuning}, which the server logic reads.
 *
 * <p>The listener also finishes the reload on the live world: a beast that is already transformed gets the
 * freshly loaded armor right away, because the modifier is removed and re-added with its fixed id.
 */
public final class QueenBeastTuningReloadListener extends SimpleJsonResourceReloadListener
{
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public QueenBeastTuningReloadListener()
    {
        super(GSON, QueenBeastTuning.DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources,
            ProfilerFiller profiler)
    {
        int applied = QueenBeastTuning.load(files);

        if (applied == 0)
        {
            SpiderQueenHungryCraft.LOGGER.info("No beast tuning file found, using the built in defaults");
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.info("Applied {} beast tuning file(s)", applied);
        }

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();

        if (server != null)
        {
            QueenBeastTuning.reconcileTransformedBeasts(server);
        }
    }
}
