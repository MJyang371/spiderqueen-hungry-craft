package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The creative tabs of the mod (Phase 8E3A-GATE1).
 *
 * <p>There is one tab for the whole mod and everything official belongs in it. It is filled from a single
 * list, so adding another official item later is one line in one place, never a second tab and never a hard
 * coded pile of entries.
 *
 * <p>Right now the tab holds the spider bed and the day vision potion family (Phase 8E4).
 */
public final class HungryCraftCreativeTabs
{
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, SpiderQueenHungryCraft.MODID);

    /** The single tab of the mod, {@code spiderqueen_hungrycraft:main}. */
    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_MODE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.spiderqueen_hungrycraft.main"))
                    // The bed itself is the icon of the tab, so the tab is recognisable before it is opened.
                    .icon(() -> new ItemStack(HungryCraftItems.SPIDER_BED.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(HungryCraftItems.SPIDER_BED.get());

                        // The territory core (Phase LAIR-CORE-1) is official content like the bed, so it sits right
                        // next to it at the top of the tab.
                        output.accept(HungryCraftItems.TERRITORY_CORE.get());

                        // The day vision family, in the order the design asks for: drinkable, extended and
                        // then the splash and lingering forms of both (Phase 8E4).
                        HungryCraftPotions.family().forEach(output::accept);

                        // Chad Steve (Phase 9A-GAMEPASS1) is appended after the existing official content, the
                        // spider bed and the day vision family stay exactly where they were.
                        output.accept(HungryCraftItems.CHAD_STEVE_SPAWN_EGG.get());

                        // The three tiered humans of Phase HF0C. The old Human Combatant egg is deliberately not in
                        // this list any more: the three tiers are the test entrance, the old egg is only kept
                        // registered so old worlds and old saves keep working.
                        output.accept(HungryCraftItems.LOW_PREPARED_HUMAN_SPAWN_EGG.get());
                        output.accept(HungryCraftItems.MID_PREPARED_HUMAN_SPAWN_EGG.get());
                        output.accept(HungryCraftItems.HIGH_PREPARED_HUMAN_SPAWN_EGG.get());

                        // The second transcendent human (Sweatlord round) sits next to the ordinary humans: he is the
                        // same kind of test entrance, only a heavier one.
                        output.accept(HungryCraftItems.SWEATLORD_STEVE_SPAWN_EGG.get());

                        // The five spider hooks (feature SPIDER-HOOK), in quality order: plain, iron, gold,
                        // diamond and netherite. They are official weapons like the bed and the core, so they
                        // belong in this one tab and nowhere else.
                        output.accept(HungryCraftItems.SPIDER_HOOK.get());
                        output.accept(HungryCraftItems.IRON_SPIDER_HOOK.get());
                        output.accept(HungryCraftItems.GOLDEN_SPIDER_HOOK.get());
                        output.accept(HungryCraftItems.DIAMOND_SPIDER_HOOK.get());
                        output.accept(HungryCraftItems.NETHERITE_SPIDER_HOOK.get());

                        // The breeding pass: the spider egg and the two spawn eggs of the brood. The spawn
                        // eggs are test entrances only, and the spider carrier is deliberately not here at
                        // all - a carrier only ever comes into existence by picking a spider up.
                        output.accept(HungryCraftItems.SPIDER_EGG.get());
                        output.accept(HungryCraftItems.IMP_SPIDER_SPAWN_EGG.get());
                        output.accept(HungryCraftItems.BROOD_SPIDER_SPAWN_EGG.get());
                        output.accept(HungryCraftItems.ZOMBIE_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.SKELETON_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.CHRONO_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.STRAY_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.HUSK_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.DROWNED_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.CREEPER_SPIDER_SPAWN_EGG.get());
        output.accept(HungryCraftItems.INFESTED_STEVE_SPAWN_EGG.get());

                        // The Spider Mine and Egg Arrow round: the spider mine (the block the queen fires at)
                        // and the spider egg arrow (the clutch a bow loads from the off hand). Both are official
                        // content of the brood, so they close the tab beside the sceptre.
                        output.accept(HungryCraftItems.SPIDER_MINE.get());
                        output.accept(HungryCraftItems.SPIDER_EGG_ARROW.get());

                        // The Spider Trigger round: the same mine with a fuse, and the plate that watches a lair and
                        // hides as any block of the game.
                        output.accept(HungryCraftItems.DELAYED_SPIDER_MINE.get());
                        output.accept(HungryCraftItems.SPIDER_TRIGGER.get());

                        // The Queen Scepter (Brood Command AI) closes the tab: it is the tool of this round.
                        output.accept(HungryCraftItems.QUEEN_SCEPTER.get());

                        // The two debug entrances of the necromancy feature (Necromancy IV round): a bone dragon that is
                        // still a corpse, and one that is awake and belongs to the player who placed it. They stand
                        // right next to the tool they are commanded with, and they are the only way to get a skeleton
                        // without a world that happens to have one lying in it.
                        output.accept(HungryCraftItems.DORMANT_BONE_DRAGON.get());
                        output.accept(HungryCraftItems.AWAKENED_BONE_DRAGON.get());

                        // The seven souls of the Soul Consumable round are drawn in ascending worth, low to high:
                        // the benevolent, the nameless traveler, the thief, the scoundrel, the executioner, the tyrant
                        // and the hero. The order comes from the registration itself, so a soul that is ever added or
                        // re-priced is drawn in its place without a second list to maintain.
                        HungryCraftItems.soulsInWorthOrder().forEach(soul -> output.accept(soul.get()));
                    })
                    .build());

    private HungryCraftCreativeTabs()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        CREATIVE_MODE_TABS.register(modEventBus);
    }
}
