package spiderqueenhungrycraft.progression;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The skill nodes of the world (Skill Progression round): the one holder a data pack fills and every rule reads.
 *
 * <p>One file is one node, read by {@link SkillReloadListener} from {@code data/<namespace>/progression_skills/} and
 * named after its own path, so a pack adds a node by adding a file and removes one by deleting it. Nothing in the
 * drawing code knows what "visceral enhancement" is: the interface builds itself from what was read, exactly like the
 * shop does.
 *
 * <p><b>The completion state of a player is asked here and nowhere else.</b> {@link #isUnlocked} is the single
 * question an ability asks, and {@link #allRequiredForLegendUnlocked} is the whole condition of the future legendary
 * round - it is deliberately not "the list is empty": a pack may switch a node off, and a node may declare that it
 * does not count, and neither may make a queen who did everything she can do ineligible.
 */
public final class SkillNodes
{
    /** Nothing is part of the game until the data pack said otherwise. */
    private static volatile List<SkillNodeDefinition> definitions = List.of();

    /** The same list, already filtered: the drawing and every rule ask for this one, never for a stale copy. */
    private static volatile List<SkillNodeDefinition> enabled = List.of();

    private SkillNodes()
    {
    }

    /** @return every node of this world, enabled or not, in the order they were read. */
    public static List<SkillNodeDefinition> definitions()
    {
        return definitions;
    }

    /** @return the nodes that are really part of the game, which are the ones a rule may ask about. */
    public static List<SkillNodeDefinition> enabledDefinitions()
    {
        return enabled;
    }

    /** @return the node of that identity, or empty when the world has none of that name. */
    public static Optional<SkillNodeDefinition> find(ResourceLocation id)
    {
        for (SkillNodeDefinition node : definitions)
        {
            if (node.id().equals(id))
            {
                return Optional.of(node);
            }
        }

        return Optional.empty();
    }

    /**
     * @return true when that player has unlocked that node.
     *
     *         <p>It is one line on purpose: the permission of every ability lives in {@link QueenSkills}, and this is
     *         only the name the rules of the tree use for the same question.
     */
    public static boolean isUnlocked(Player player, ResourceLocation nodeId)
    {
        return QueenSkills.has(player, nodeId);
    }

    /**
     * The completion condition of the future legendary round: <b>every enabled node that is required for the
     * legend</b> is unlocked.
     *
     * @return true when nothing that counts is left locked.
     */
    public static boolean allRequiredForLegendUnlocked(Player player)
    {
        for (SkillNodeDefinition node : enabled)
        {
            if (node.requiredForLegend() && !isUnlocked(player, node.id()))
            {
                return false;
            }
        }

        return true;
    }

    /** Replaces the whole tree, used by the reload listener. */
    public static void set(List<SkillNodeDefinition> loaded)
    {
        definitions = List.copyOf(loaded);
        enabled = definitions.stream().filter(SkillNodeDefinition::enabled).toList();

        SpiderQueenHungryCraft.LOGGER.info("[Skills] {} nodes loaded, {} of them enabled", definitions.size(),
                enabled.size());
    }

    /** Empties the tree, used when a world has no node files at all: every added ability is locked. */
    public static void reset()
    {
        set(List.of());
    }
}
