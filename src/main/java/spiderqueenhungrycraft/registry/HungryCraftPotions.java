package spiderqueenhungrycraft.registry;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The potions of the mod (Phase 8E4).
 *
 * <p>There are exactly two of them: the potion of day vision and its extended form. Nothing else is
 * registered here, and above all no blindness potion: the day vision never corrupts into anything.
 *
 * <p>The two durations are the vanilla night vision ones, so a day vision potion behaves exactly like the
 * potion it is the mirror of: three minutes normally, eight minutes with redstone.
 *
 * <p>Both potions are ordinary vanilla potion entries, so every vanilla form works for them for free: a
 * splash potion with gunpowder, a lingering potion with dragon's breath, and all of them show up in the
 * vanilla food and drinks tab because vanilla itself fills that tab from the whole potion registry.
 */
public final class HungryCraftPotions
{
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, SpiderQueenHungryCraft.MODID);

    /** Three minutes of daylight vision, like a vanilla potion of night vision. */
    public static final int NORMAL_DURATION_TICKS = 3600;

    /** Eight minutes of daylight vision, like a vanilla extended potion of night vision. */
    public static final int LONG_DURATION_TICKS = 9600;

    /** {@code spiderqueen_hungrycraft:day_vision}. */
    public static final RegistryObject<Potion> DAY_VISION = POTIONS.register("day_vision",
            () -> new Potion(new MobEffectInstance(HungryCraftEffects.DAY_VISION.get(), NORMAL_DURATION_TICKS)));

    /**
     * {@code spiderqueen_hungrycraft:long_day_vision}.
     *
     * <p>It carries no explicit base name on purpose, unlike the vanilla long potions: that way the extended
     * potion gets a translation key of its own and can be called "Long Potion of Day Vision" instead of
     * sharing the name of the normal one.
     */
    public static final RegistryObject<Potion> LONG_DAY_VISION = POTIONS.register("long_day_vision",
            () -> new Potion(new MobEffectInstance(HungryCraftEffects.DAY_VISION.get(), LONG_DURATION_TICKS)));

    private HungryCraftPotions()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        POTIONS.register(modEventBus);
    }

    /**
     * @return one bottle of that potion in that container, with the vanilla potion tag on it, so the vanilla
     *         name, the vanilla tooltip and the vanilla bottle colour all work untouched
     */
    public static ItemStack bottle(Item container, RegistryObject<Potion> potion)
    {
        return PotionUtils.setPotion(new ItemStack(container), potion.get());
    }

    /**
     * The whole day vision family in the order it is shown in the creative tab: drinkable, extended, then
     * the splash and the lingering forms of both. It is built lazily because the potions only exist once the
     * registries are filled.
     */
    public static List<ItemStack> family()
    {
        return List.of(
                bottle(Items.POTION, DAY_VISION),
                bottle(Items.POTION, LONG_DAY_VISION),
                bottle(Items.SPLASH_POTION, DAY_VISION),
                bottle(Items.SPLASH_POTION, LONG_DAY_VISION),
                bottle(Items.LINGERING_POTION, DAY_VISION),
                bottle(Items.LINGERING_POTION, LONG_DAY_VISION));
    }
}
