package spiderqueenhungrycraft.web;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Entity type tags of the web features. Datapack friendly on purpose: modded spiders or immune mobs
 * are added through tags, never through hardcoded names or class references.
 */
public final class QueenWebEntityTags
{
    /** Spider like creatures, immune to webs and never capturable. */
    public static final TagKey<EntityType<?>> SPIDER_LIKE = create("spider_like");

    /** Anything else that ignores webs. Includes spider_like through the datapack. */
    public static final TagKey<EntityType<?>> WEB_IMMUNE = create("web_immune");

    /** Entities that can never be captured, for example bosses. */
    public static final TagKey<EntityType<?>> UNCAPTURABLE = create("uncapturable");

    /**
     * Opt in tag for living entities that should be capturable even though they do not extend Mob.
     * Meant for a few modded cases only, so it is empty by default.
     */
    public static final TagKey<EntityType<?>> FORCE_CAPTURABLE = create("force_capturable");

    private QueenWebEntityTags()
    {
    }

    private static TagKey<EntityType<?>> create(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, name));
    }
}
