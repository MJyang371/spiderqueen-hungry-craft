package spiderqueenhungrycraft.world;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.Registries;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Entity type tags that drive the monster neutrality. Pack authors can add entries to either tag
 * without touching code.
 */
public final class QueenEntityTags
{
    /** Entity types that never attack the Spider Queen on their own. */
    public static final TagKey<EntityType<?>> NEUTRAL_TO_SPIDER_QUEEN = create("neutral_to_spider_queen");

    /** Entity types that stay hostile to the Spider Queen, even if their category would suggest otherwise. */
    public static final TagKey<EntityType<?>> ALWAYS_HOSTILE_TO_SPIDER_QUEEN = create("always_hostile_to_spider_queen");

    /**
     * Village guardians that protect villages from the Spider Queen. Entries for third party mods are
     * optional, so the mod works fine without Guard Villagers or Iron's Spells installed.
     */
    public static final TagKey<EntityType<?>> VILLAGE_DEFENDERS = create("village_defenders");

    private QueenEntityTags()
    {
    }

    private static TagKey<EntityType<?>> create(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, name));
    }
}
