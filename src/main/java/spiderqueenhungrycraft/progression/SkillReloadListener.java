package spiderqueenhungrycraft.progression;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Reads the skill nodes of the world (Skill Progression round).
 *
 * <p><b>One file is one node.</b> Every file under {@code data/<namespace>/progression_skills/} becomes a node, named
 * after its own path - {@code progression_skills/queen_stance.json} is {@code spiderqueenhungrycraft:queen_stance} -
 * so the identity in a save file is exactly the name a pack author writes and never a number that shifts when a file
 * is inserted.
 *
 * <p><b>A broken file is skipped, never fatal.</b> A missing name, an icon that does not exist, a prerequisite that is
 * not an identity or a level below one is warned about and left out; every other node of the same reload is still
 * loaded. An empty result is a valid tree: a world whose pack ships nothing simply has no skill to buy, and every
 * ability this round adds is locked.
 *
 * <p>A prerequisite that no file declares is <b>warned about but kept</b>: the node then spells out something that can
 * not be bought yet, which is visible in the interface and in the log, and is a much better failure than silently
 * dropping a requirement a designer wrote down.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SkillReloadListener extends SimpleJsonResourceReloadListener
{
    /** The data pack folder the nodes live in. */
    public static final String DIRECTORY = "progression_skills";

    private static final Gson GSON = new Gson();

    public SkillReloadListener()
    {
        super(GSON, DIRECTORY);
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event)
    {
        event.addListener(new SkillReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler)
    {
        List<SkillNodeDefinition> loaded = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet())
        {
            SkillNodeDefinition node = read(file.getKey(), file.getValue());

            if (node != null)
            {
                loaded.add(node);
            }
        }

        if (loaded.isEmpty())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Skills] no usable {} node was found, every added ability stays locked",
                    DIRECTORY);
            SkillNodes.reset();
            return;
        }

        warnAboutMissingParents(loaded);
        SkillNodes.set(loaded);
    }

    /** @return the node of that file, or null when it is not usable - which was warned about. */
    private static SkillNodeDefinition read(ResourceLocation id, JsonElement element)
    {
        if (element == null || !element.isJsonObject())
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Skills] {} is not a json object, skipped", id);
            return null;
        }

        try
        {
            JsonObject root = element.getAsJsonObject();
            SkillNodeDefinition node = SkillNodeDefinition.parse(id, root);

            if (!BuiltInRegistries.ITEM.containsKey(node.icon()))
            {
                SpiderQueenHungryCraft.LOGGER.warn("[Skills] {} names an icon that does not exist ({}), skipped", id,
                        node.icon());
                return null;
            }

            return node;
        }
        catch (RuntimeException e)
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Skills] {} could not be read ({}), skipped", id, e.getMessage());
            return null;
        }
    }

    /**
     * Warns about prerequisites that no file of this world declares.
     *
     * <p>Nothing is removed: the node keeps the requirement it was given, so the interface shows why it can not be
     * bought and the log says which file is missing. It is deliberately a warning and not a refusal, because the same
     * pack may add the missing node in a later version.
     */
    private static void warnAboutMissingParents(List<SkillNodeDefinition> nodes)
    {
        Set<ResourceLocation> known = new HashSet<>();

        for (SkillNodeDefinition node : nodes)
        {
            known.add(node.id());
        }

        for (SkillNodeDefinition node : nodes)
        {
            for (ResourceLocation parent : node.parents())
            {
                if (!known.contains(parent))
                {
                    SpiderQueenHungryCraft.LOGGER.warn("[Skills] {} needs {}, which no {} file declares", node.id(),
                            parent, DIRECTORY);
                }
            }
        }
    }
}
