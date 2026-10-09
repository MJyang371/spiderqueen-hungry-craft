package spiderqueenhungrycraft.faction;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.world.QueenEntityTags;

/**
 * Every entity type tag of the faction system (Phase HF1-VISIBLE part D and part Q, extended by the faction split
 * pass).
 *
 * <p>Faction membership is data driven on purpose: a pack author adds or removes an id in one of these json files and
 * the faction of that entity changes, without a single line of java. The only entity that is never decided by a tag
 * is the player, because one entity type can not tell a Spider Queen from a normal human - that is what the queen
 * capability is for.
 *
 * <p>The priority when an entity type matches several tags is:
 * {@code no_faction} &gt; {@code queen_faction} &gt; {@code human_faction} &gt; {@code village_faction} &gt;
 * {@code illager_faction} &gt; {@code nether_fortress_faction} &gt; {@code piglin_faction} &gt;
 * {@code monster_faction} &gt; nothing ({@code NONE}).
 *
 * <p>A {@code Monster} that no tag names is {@code NONE}: the nether ecologies, the illagers, the bosses, the
 * guardians and the phantoms are deliberately kept out of the overworld monster faction, so that a fight between
 * them and the queen stays a matter of their own vanilla AI.
 */
public final class FactionTags
{
    /** Entities that stand with the humans: Chad Steve today, the human units later. */
    public static final TagKey<EntityType<?>> HUMAN_FACTION = create("human_faction");

    /** Entities that stand with the villages: villagers, iron golems, wandering traders, modded guards. */
    public static final TagKey<EntityType<?>> VILLAGE_FACTION = create("village_faction");

    /**
     * The fighting part of a village. Only these get the faction goal that hunts the queen, so an ordinary villager
     * never turns into a soldier. Shares the tag with the older village hostility code on purpose: one tag, one list.
     */
    public static final TagKey<EntityType<?>> VILLAGE_DEFENDERS = QueenEntityTags.VILLAGE_DEFENDERS;

    /**
     * The ordinary monsters of the overworld. It is deliberately not "every {@code Monster}": the nether ecologies,
     * the illagers and the special enemies have their own tag or none at all.
     */
    public static final TagKey<EntityType<?>> MONSTER_FACTION = create("monster_faction");

    /** The piglin ecology of the nether: piglins, piglin brutes, zombified piglins, hoglins, zoglins. */
    public static final TagKey<EntityType<?>> PIGLIN_FACTION = create("piglin_faction");

    /** The illager faction: the raiders of the overworld. */
    public static final TagKey<EntityType<?>> ILLAGER_FACTION = create("illager_faction");

    /** The nether fortress ecology: blazes, ghasts, wither skeletons, magma cubes. */
    public static final TagKey<EntityType<?>> NETHER_FORTRESS_FACTION = create("nether_fortress_faction");

    /** Entities on the side of the queen that are not the queen player herself. Reserved for the brood. */
    public static final TagKey<EntityType<?>> QUEEN_FACTION = create("queen_faction");

    /**
     * The hard exclusion. An entity type in here has no faction at all, whatever else it matches, and therefore never
     * takes part in any faction logic.
     */
    public static final TagKey<EntityType<?>> NO_FACTION = create("no_faction");

    private FactionTags()
    {
    }

    private static TagKey<EntityType<?>> create(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, name));
    }
}
