package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * What the brood eats (Brood Feeding round).
 *
 * <p>Every spider of the queen - the imp, the brood spider, the skeleton spider, the infested Steve and every species a
 * later round may add - is fed by the hand of its queen: a piece of <b>meat</b> in her hand is health for the spider, and
 * the health it gives is exactly the hunger that food would have fed to a player. One piece of the stack is eaten per
 * bite.
 *
 * <p><b>The rule is the food of the game and not a list of ids.</b> A stack counts as food of the brood when the game
 * itself says that it is food and that it is <b>meat</b> ({@link FoodProperties#isMeat()}), so every kind of meat of this
 * mod, of the game and of any other mod that marks its food as meat is fed without a single line here - rotten flesh
 * included, which the game marks as meat as well, and so are all the raw and cooked cuts of the game. Fish is
 * deliberately not part of it: the game does not mark fish as meat and a spider of the queen eats meat.
 *
 * <p><b>The tag is the door for everything else.</b> {@link #BROOD_FOOD} lists what is food of the brood on top of the
 * meat of the game. Rotten flesh is in it so the rule can be read in one file, and a pack or another mod may drop any
 * food of its own into it - a spider that also eats fish, or a modded piece of meat whose author never marked it as meat
 * - by editing one json file under {@code data/<namespace>/tags/items/brood_food.json}.
 */
public final class BroodFood
{
    /**
     * Everything the brood eats on top of the meat of the game.
     *
     * <p>It is a tag and not a list in code on purpose: the food a spider of the queen accepts has to be open to the
     * packs of a world and to the mods beside this one, and a tag is the one mechanism of the game that both of them can
     * write.
     */
    public static final TagKey<Item> BROOD_FOOD = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "brood_food"));

    private BroodFood()
    {
    }

    /**
     * @param stack  the stack in the hand of the queen
     * @param eater  the spider that would eat it, only used to resolve the food of the stack
     * @return the health that stack would give that spider, 0 when the brood does not eat it at all
     */
    public static int nutritionOf(ItemStack stack, @Nullable LivingEntity eater)
    {
        if (stack.isEmpty())
        {
            return 0;
        }

        FoodProperties food = stack.getFoodProperties(eater);

        if (food == null || food.getNutrition() <= 0)
        {
            return 0;
        }

        return food.isMeat() || stack.is(BROOD_FOOD) ? food.getNutrition() : 0;
    }
}
