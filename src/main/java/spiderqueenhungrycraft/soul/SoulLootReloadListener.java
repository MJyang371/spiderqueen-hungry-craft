package spiderqueenhungrycraft.soul;

import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Reads the rule book of the soul containers (Soul Consumable round) from {@code data/<namespace>/soul_loot/}.
 *
 * <p><b>Every file of the folder is a piece of the same book.</b> A file may hold the weights, the families, the loot
 * tables, the refused tables or any mix of them, and the files are merged in the order of their names, so the file
 * shipped by this mod and the file a pack adds for a structure mod of its own are read together. That is how the
 * compatibility of a mod that is not installed can still be part of this mod's data - the aliases are ids, and an id
 * of a mod nobody has simply never matches.
 *
 * <p>The rules are replaced on every server start and every {@code /reload}, because a datapack may change them at any
 * time and a world must never keep filling its chests by a rule that was taken away.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SoulLootReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the rule book lives in. */
    public static final String DIRECTORY = "soul_loot";

    private static final Gson GSON = new Gson();

    public SoulLootReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new SoulLootReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        if (files.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[SoulLoot] no {} file was found, no container holds a soul", DIRECTORY);
            SoulLootRules.reset();
            return;
        }

        SoulLootRules.applyFiles(files);
    }
}
