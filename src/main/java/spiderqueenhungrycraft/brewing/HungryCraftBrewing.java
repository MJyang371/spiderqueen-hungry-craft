package spiderqueenhungrycraft.brewing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftPotions;

/**
 * The brewing recipes of the mod (Phase 8E4).
 *
 * <p>Exactly two mixes are added:
 * <ul>
 * <li>awkward potion + glow ink sac -&gt; potion of day vision,</li>
 * <li>potion of day vision + redstone -&gt; extended potion of day vision.</li>
 * </ul>
 *
 * <p>The gunpowder and dragon's breath steps are deliberately not added: vanilla already turns every potion
 * into its splash form and every splash potion into its lingering form, so adding them here again would only
 * be a second recipe for something that already works.
 *
 * <p>There is no fermented spider eye recipe and there never will be one in this version: the day vision
 * does not corrupt into a blindness potion.
 *
 * <p>Each mix matches its own container, its own potion and its own ingredient exactly, so the vanilla
 * brewing and the brewing of every other mod are untouched.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HungryCraftBrewing
{
    private HungryCraftBrewing()
    {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        // The brewing registry is a plain list, so it is filled after the registries are complete.
        event.enqueueWork(HungryCraftBrewing::registerRecipes);
    }

    private static void registerRecipes()
    {
        BrewingRecipeRegistry.addRecipe(new PotionMix(Items.POTION, Potions.AWKWARD, Items.GLOW_INK_SAC,
                Items.POTION, HungryCraftPotions.DAY_VISION.get()));

        BrewingRecipeRegistry.addRecipe(new PotionMix(Items.POTION, HungryCraftPotions.DAY_VISION.get(),
                Items.REDSTONE, Items.POTION, HungryCraftPotions.LONG_DAY_VISION.get()));
    }

    /**
     * One potion mix of the mod: a container holding a known potion, a plain ingredient item and the bottle
     * that comes out of it.
     *
     * <p>It is written by hand instead of using an {@code Ingredient} so that the input is matched exactly:
     * the container item and the potion on the tag, never "any potion" and never "any bottle".
     */
    private record PotionMix(Item inputContainer, Potion inputPotion, Item ingredient, Item outputContainer,
            Potion outputPotion) implements IBrewingRecipe
    {
        @Override
        public boolean isInput(ItemStack input)
        {
            return input.is(this.inputContainer) && PotionUtils.getPotion(input) == this.inputPotion;
        }

        @Override
        public boolean isIngredient(ItemStack ingredient)
        {
            return ingredient.is(this.ingredient);
        }

        @Override
        public ItemStack getOutput(ItemStack input, ItemStack ingredient)
        {
            if (!this.isInput(input) || !this.isIngredient(ingredient))
            {
                return ItemStack.EMPTY;
            }

            return PotionUtils.setPotion(new ItemStack(this.outputContainer), this.outputPotion);
        }
    }
}
