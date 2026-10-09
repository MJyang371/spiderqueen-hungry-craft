package spiderqueenhungrycraft.brood;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Entity type tags that drive the behaviour of the brood (Brood Villager Neutrality pass).
 *
 * <p>These are deliberately <b>not</b> faction tags. A faction says which side a creature stands on and is read by
 * {@link spiderqueenhungrycraft.faction.FactionResolver}; nothing here ever changes one. A tag in this class only names
 * creatures that a rule of the spiders themselves singles out, so a pack author can rewrite the list in a json file
 * without touching the faction system at all.
 */
public final class BroodEntityTags
{
    /**
     * The civilians that a spider of the brood never picks on its own.
     *
     * <p>A villager stays a member of the village faction and the village stays an enemy of the queen side in the
     * faction matrix - this tag takes the villager out of the <b>natural</b> targeting of a spider only
     * ({@link SpiderMinionTargeting#isNaturalEnemy(SpiderMinionEntity, net.minecraft.world.entity.LivingEntity)}).
     * Every order survives it: the attack order of the sceptre, the help of a following spider while its queen fights a
     * villager, and the self defence of a spider that was hit.
     *
     * <p>It exists because a spider that kills a villager on its own breaks the machines of a redstone builder: a
     * villager breeder lives from its villagers staying alive, and a raid farm lives from the raiders reaching them.
     * Iron golems, guards and every other defender of a village are deliberately <b>not</b> in this list - a village
     * still defends itself, and a spider still answers a defender that swings at it.
     */
    public static final TagKey<EntityType<?>> VILLAGE_CIVILIANS = create("village_civilians");

    private BroodEntityTags()
    {
    }

    private static TagKey<EntityType<?>> create(String name)
    {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, name));
    }
}
