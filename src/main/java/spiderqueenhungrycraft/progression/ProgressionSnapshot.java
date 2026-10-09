package spiderqueenhungrycraft.progression;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import spiderqueenhungrycraft.soul.Souls;

/**
 * Everything the growth interface of one client needs to draw itself (Progression Foundation round).
 *
 * <p>It is built on the server, from the definition and the shop the <b>server</b> holds, and it is what a client
 * draws. A client never reads {@code data/.../progression} or {@code data/.../soul_shop} of its own copy of the
 * world to decide what a level costs or what a product is: two data packs of the same world may say two different
 * things, and only one of them is being enforced.
 *
 * <p>It is a snapshot and not a subscription: it is sent when the interface is opened and again after every accepted
 * request, so a {@code /reload} that changed the numbers is picked up the next time anything really happened - and
 * never once per frame.
 *
 * @param corePos            the core this interface belongs to; the client sends it back with every request
 * @param queenLevel         the Queen Level of the player
 * @param souls              the souls of the player, as a fallback for a client that has not been told otherwise
 * @param definition         the numbers the server enforces
 * @param shop               the products that are for sale
 * @param skills             the skill nodes of the tree, as the interface draws them
 * @param legendaryEnabled   whether the legendary system exists at all (false this round: the tab is locked)
 * @param legendaryEligible  whether the player has met the conditions of the future legendary round
 */
public record ProgressionSnapshot(BlockPos corePos, int queenLevel, long souls, ProgressionDefinition definition,
        List<ShopEntryView> shop, List<SkillNodeView> skills, boolean legendaryEnabled, boolean legendaryEligible)
{
    /**
     * One product of the soul shop as the interface shows it: its identity, the item it hands over, how many pieces
     * and what it costs. The client resolves the item id against its own registry, so a product of an item this
     * client does not know simply draws as nothing.
     */
    public record ShopEntryView(ResourceLocation id, ResourceLocation itemId, int count, long price)
    {
    }

    /**
     * One node of the tree as the interface shows it: its identity, the rung it hangs on, the side and lane it stands
     * on, what it needs and what it costs.
     *
     * <p>It carries the <b>keys</b> of its name and its effect and never the words themselves, so both are translated
     * on the client and a pack only has to write the keys down once. Whether the node is owned, available or locked is
     * deliberately not in here: that is decided from the level and the bought skills, and the client asks the very same
     * rule the server enforces.
     */
    public record SkillNodeView(ResourceLocation id, int requiredLevel, long soulCost, int anchorLevel,
            SkillNodeDefinition.Side side, int lane, List<ResourceLocation> parents, ResourceLocation icon,
            String nameKey, String descriptionKey, SkillNodeDefinition.InputHint inputHint)
    {
    }

    /** @return the view of one node, which is the whole of what the drawing needs. */
    private static SkillNodeView viewOf(SkillNodeDefinition node)
    {
        return new SkillNodeView(node.id(), node.requiredLevel(), node.soulCost(), node.anchorLevel(), node.side(),
                node.lane(), node.parents(), node.icon(), node.nameKey(), node.descriptionKey(), node.inputHint());
    }

    /** Builds the snapshot of that player standing at that core, from the state of the server. */
    public static ProgressionSnapshot of(ServerPlayer player, BlockPos corePos)
    {
        ProgressionDefinition definition = ProgressionDefinitions.current();
        List<ShopEntryView> shop = new ArrayList<>();

        for (ShopEntry entry : SoulShop.enabledEntries())
        {
            // A product with a requirement is simply not in the snapshot of a player who does not own the skill: for
            // her the product does not exist at all, it is not greyed out and not locked. The server refuses it as
            // well, so a client that asks for it anyway changes nothing (Skill Progression round).
            if (!entry.visibleTo(player))
            {
                continue;
            }

            shop.add(new ShopEntryView(entry.id(), BuiltInRegistries.ITEM.getKey(entry.item()), entry.count(),
                    entry.price()));
        }

        List<SkillNodeView> skills = new ArrayList<>();

        for (SkillNodeDefinition node : SkillNodes.enabledDefinitions())
        {
            skills.add(viewOf(node));
        }

        return new ProgressionSnapshot(corePos.immutable(), QueenLevels.getLevel(player), Souls.getSouls(player),
                definition, List.copyOf(shop), List.copyOf(skills), LegendaryProgression.isSystemEnabled(),
                LegendaryProgression.isEligible(player));
    }

    /** @return how many souls one step to that level costs under the definition of this snapshot. */
    public long costToReach(int targetLevel)
    {
        return this.definition.costToReach(targetLevel);
    }
}
