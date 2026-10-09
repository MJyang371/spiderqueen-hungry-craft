package spiderqueenhungrycraft.hook;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The two data tags of the spider hook (feature SPIDER-HOOK): the handle a pack author uses to say what his own
 * creatures do when a web catches them. Both are empty in the mod itself, so nothing of the mod has to be
 * marked and a pack adds its own beings without touching a single line of code.
 *
 * <p>{@link #HOOK_IMMUNE} marks a being the web may catch but never move: the hook drags its thrower to that
 * being instead. It is the tag for anything that cannot be shoved around, the same rule the hook applies to a
 * being that is far too heavy for its silk.
 *
 * <p>{@link #HOOK_HEAVY} marks a being that always counts as heavy, whatever its health is, so the thrower is
 * dragged to it. A pack that has a huge creature with little health uses this to make it pull like a boss.
 */
public final class HookEntityTags
{
    /** Caught, but never dragged: the thrower is pulled to it instead. */
    public static final TagKey<EntityType<?>> HOOK_IMMUNE = create("hook_immune");

    /** Always heavy, whatever its health is: the thrower is pulled to it. */
    public static final TagKey<EntityType<?>> HOOK_HEAVY = create("hook_heavy");

    private HookEntityTags()
    {
    }

    private static TagKey<EntityType<?>> create(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE,
                new ResourceLocation(SpiderQueenHungryCraft.MODID, name));
    }
}
