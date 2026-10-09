package spiderqueenhungrycraft.registry;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.QueenScepterItem;
import spiderqueenhungrycraft.brood.SpiderCarrierItem;
import spiderqueenhungrycraft.brood.SpiderEggArrowItem;
import spiderqueenhungrycraft.brood.SpiderSpawnEggItem;
import spiderqueenhungrycraft.cocoon.CocoonItem;
import spiderqueenhungrycraft.hook.SpiderHookItem;
import spiderqueenhungrycraft.hook.SpiderHookTier;
import spiderqueenhungrycraft.necromancy.NecromancyDebugItem;
import spiderqueenhungrycraft.soul.SoulItem;

/**
 * Items of the mod. The cocoon is capped to a stack size of one on purpose: one item always stands
 * for exactly one captured creature, so two cocoons must never merge.
 */
public final class HungryCraftItems
{
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, SpiderQueenHungryCraft.MODID);

    public static final RegistryObject<Item> COCOON = ITEMS.register("cocoon",
            () -> new CocoonItem(new Item.Properties().stacksTo(1)));

    /**
     * The spider bed as an item (Phase 8E3A-GATE1). It has a normal block item on purpose: a bed can be
     * taken out of the creative tab and placed on a floor, a wall or a ceiling like any other block, the
     * ritual is not the only way to get one.
     */
    public static final RegistryObject<Item> SPIDER_BED = ITEMS.register("spider_bed",
            () -> new BlockItem(HungryCraftBlocks.SPIDER_BED.get(), new Item.Properties()));

    /**
     * The territory core as an item (Phase LAIR-CORE-1). It is an ordinary block item: the core is crafted, carried
     * and placed like any other block, and breaking it gives it back.
     */
    public static final RegistryObject<Item> TERRITORY_CORE = ITEMS.register("territory_core",
            () -> new BlockItem(HungryCraftBlocks.TERRITORY_CORE.get(), new Item.Properties()));

    /** The Steve shirt cyan of his own skin. */
    private static final int SPAWN_EGG_SHIRT_CYAN = 0x00CCCC;

    /** The Steve pants blue of his own skin. */
    private static final int SPAWN_EGG_PANTS_BLUE = 0x3A3189;

    /**
     * Chad Steve spawn egg (Phase 9A-GAMEPASS1). It uses the vanilla spawn egg model and the vanilla spawn egg
     * texture, only the two tint colours are set, so no new art is needed for it.
     */
    public static final RegistryObject<Item> CHAD_STEVE_SPAWN_EGG = ITEMS.register("chad_steve_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.CHAD_STEVE, SPAWN_EGG_SHIRT_CYAN, SPAWN_EGG_PANTS_BLUE,
                    new Item.Properties()));

    /** The iron grey of the armour of the standard human combatant. */
    private static final int SPAWN_EGG_IRON_GREY = 0xD8D8D8;

    /** The leather brown of his belt, the second colour of the spawn egg. */
    private static final int SPAWN_EGG_LEATHER_BROWN = 0x6B4A2B;

    /** The pale stone grey of a low prepared human. */
    private static final int SPAWN_EGG_STONE_GREY = 0x8A8A8A;

    /** The gold of the apples a mid prepared human carries. */
    private static final int SPAWN_EGG_GOLD = 0xF0C000;

    /** The diamond cyan of a high prepared human. */
    private static final int SPAWN_EGG_DIAMOND_CYAN = 0x33E0E0;

    /** The dark grey of netherite, the second colour of the high prepared egg. */
    private static final int SPAWN_EGG_NETHERITE = 0x3B3B3B;

    /**
     * The three tiered humans of Phase HF0C, one spawn egg each. The old Human Combatant egg stays registered but is
     * gone from the creative tab, so the three tiers are the only test entrance of this round.
     */
    public static final RegistryObject<Item> LOW_PREPARED_HUMAN_SPAWN_EGG = ITEMS.register(
            "low_prepared_human_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.LOW_PREPARED_HUMAN, SPAWN_EGG_LEATHER_BROWN,
                    SPAWN_EGG_STONE_GREY, new Item.Properties()));

    public static final RegistryObject<Item> MID_PREPARED_HUMAN_SPAWN_EGG = ITEMS.register(
            "mid_prepared_human_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.MID_PREPARED_HUMAN, SPAWN_EGG_IRON_GREY, SPAWN_EGG_GOLD,
                    new Item.Properties()));

    public static final RegistryObject<Item> HIGH_PREPARED_HUMAN_SPAWN_EGG = ITEMS.register(
            "high_prepared_human_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.HIGH_PREPARED_HUMAN, SPAWN_EGG_DIAMOND_CYAN,
                    SPAWN_EGG_NETHERITE, new Item.Properties()));

    /**
     * Sweatlord Steve spawn egg (Sweatlord round).
     *
     * <p>The second transcendent human of this mod is a test subject like Chad Steve and the ordinary humans: no
     * spawn placement anywhere, so this egg and {@code /summon} are the only ways in. The tints are the diamond cyan
     * and the gold of the gear he uses - the same vanilla spawn egg model and texture, so no new art is needed.
     */
    public static final RegistryObject<Item> SWEATLORD_STEVE_SPAWN_EGG = ITEMS.register(
            "sweatlord_steve_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.SWEATLORD_STEVE, SPAWN_EGG_DIAMOND_CYAN, SPAWN_EGG_GOLD,
                    new Item.Properties()));

    /**
     * The standard human combatant spawn egg (Phase HF0A-GAMEPASS1). It uses the vanilla spawn egg model and the
     * vanilla spawn egg texture like the Chad Steve egg, only the two tint colours are set.
     */
    public static final RegistryObject<Item> HUMAN_COMBATANT_SPAWN_EGG = ITEMS.register("human_combatant_spawn_egg",
            () -> new ForgeSpawnEggItem(HungryCraftEntities.HUMAN_COMBATANT, SPAWN_EGG_IRON_GREY, SPAWN_EGG_LEATHER_BROWN,
                    new Item.Properties()));

    /**
     * The spider egg (this round's breeding pass). It is an ordinary block item: the egg is placed on the
     * ground with a right click, it hatches after ten seconds and it comes back as the very same item when
     * it is broken.
     */
    public static final RegistryObject<Item> SPIDER_EGG = ITEMS.register("spider_egg",
            () -> new BlockItem(HungryCraftBlocks.SPIDER_EGG.get(), new Item.Properties()));

    /**
     * The spider mine (Spider Mine and Egg Arrow round). It is an ordinary block item: the mine is crafted from a block
     * of TNT and eight spider eggs, placed with a right click, and it comes back as itself when it is broken by hand -
     * only the one it is fired never returns.
     */
    public static final RegistryObject<Item> SPIDER_MINE = ITEMS.register("spider_mine",
            () -> new BlockItem(HungryCraftBlocks.SPIDER_MINE.get(), new Item.Properties()));

    /**
     * The delayed spider mine (Spider Trigger round): a spider mine with a string as its fuse, which turns the instant
     * blast of the block into the four seconds of a block of TNT.
     */
    public static final RegistryObject<Item> DELAYED_SPIDER_MINE = ITEMS.register("delayed_spider_mine",
            () -> new BlockItem(HungryCraftBlocks.DELAYED_SPIDER_MINE.get(), new Item.Properties()));

    /**
     * The spider trigger (Spider Trigger round): the pressure plate that watches, reports and can be hidden as any
     * full block. It is an ordinary block item, so it is crafted, carried, placed and broken like any other block.
     */
    public static final RegistryObject<Item> SPIDER_TRIGGER = ITEMS.register("spider_trigger",
            () -> new BlockItem(HungryCraftBlocks.SPIDER_TRIGGER.get(), new Item.Properties()));

    /**
     * The spider egg arrow (Spider Mine and Egg Arrow round): an arrow with a clutch of eggs, shot from a bow while it
     * is carried in the off hand. Its item is what makes it real ammunition - see {@link SpiderEggArrowItem}.
     */
    public static final RegistryObject<Item> SPIDER_EGG_ARROW = ITEMS.register("spider_egg_arrow",
            () -> new SpiderEggArrowItem(new Item.Properties()));

    /**
     * The carrier of a collected spider (this round's breeding pass). Always a single item, because one
     * carrier stands for exactly one spider individual.
     */
    public static final RegistryObject<Item> SPIDER_CARRIER = ITEMS.register("spider_carrier",
            () -> new SpiderCarrierItem(new Item.Properties().stacksTo(1)));

    /**
     * The Queen Scepter (Brood Command AI): the command tool of a queen over her brood. A stack of one, and no
     * durability in this round - it is a command tool, not a weapon.
     */
    public static final RegistryObject<Item> QUEEN_SCEPTER = ITEMS.register("queen_scepter",
            () -> new QueenScepterItem(new Item.Properties().stacksTo(1)));

    /**
     * The <b>dormant bone dragon</b> of the necromancy feature (Necromancy IV round, the two debug entrances): a bone
     * dragon that has not been awakened, placed in front of the player who uses the item.
     *
     * <p>It is a creative tab entrance and nothing else: it is not craftable, it has no survival recipe, and it exists
     * because a corpse can not be tested without a way to put one in front of the tester. The dragon it builds is the
     * very corpse Ice and Fire's own world generation leaves in a desert, so a dragon woken out of it is an ordinary
     * bone dragon of that mod.
     */
    public static final RegistryObject<Item> DORMANT_BONE_DRAGON = ITEMS.register("dormant_bone_dragon",
            () -> new NecromancyDebugItem(false, new Item.Properties().stacksTo(1)));

    /**
     * The <b>awakened bone dragon</b> of the same feature: a bone dragon that is awake, that <b>belongs to the player
     * who placed it</b>, and that is therefore a unit of his brood - selectable with the sceptre, commandable, rideable -
     * for the ordinary ten minutes of a contract.
     *
     * <p>Like its dormant twin it is a debug entrance in the creative tab only, and it walks the same three steps the
     * skill walks: the corpse, the waking, and the contract. Nothing about it is a shortcut around a rule.
     */
    public static final RegistryObject<Item> AWAKENED_BONE_DRAGON = ITEMS.register("awakened_bone_dragon",
            () -> new NecromancyDebugItem(true, new Item.Properties().stacksTo(1)));

    /** The grey of the body of an imp spider, the first colour of its spawn egg. */
    private static final int SPAWN_EGG_IMP_GREY = 0x5A5A66;

    /** The grey of the body of an ordinary brood spider, a shade lighter than the imp. */
    private static final int SPAWN_EGG_BROOD_GREY = 0x76767F;

    /** The glowing blue of the eyes of the brood, the second colour of both spawn eggs. */
    private static final int SPAWN_EGG_MINION_EYES = 0x4FA8FF;

    /**
     * The two spawn eggs of the brood (this round's breeding pass). They are test entrances only: neither
     * has a survival recipe, and the brood spider egg really spawns the mod's own brood spider, never a
     * {@code minecraft:spider}. A spider that was spawned this way has no owner, so it can only be picked
     * up in creative.
     */
    public static final RegistryObject<Item> IMP_SPIDER_SPAWN_EGG = ITEMS.register("imp_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.IMP_SPIDER, SPAWN_EGG_IMP_GREY, SPAWN_EGG_MINION_EYES,
                    new Item.Properties()));

    public static final RegistryObject<Item> BROOD_SPIDER_SPAWN_EGG = ITEMS.register("brood_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.BROOD_SPIDER, SPAWN_EGG_BROOD_GREY,
                    SPAWN_EGG_MINION_EYES, new Item.Properties()));

    /**
     * The two colours of the spawn egg of a zombie spider (Special Spiders round I: repaired).
     *
     * <p>They are the colours <b>the game itself</b> writes into {@code Items.ZOMBIE_SPAWN_EGG} - the teal body and the
     * green of the vanilla zombie egg - not an estimate: the whole egg is drawn by the generic two colour spawn egg
     * model, so the pair of numbers is the entire look of it. The earlier values were a guess and made the egg look
     * like a different creature.
     */
    private static final int SPAWN_EGG_ZOMBIE_GREEN = 0x00AFAF;

    private static final int SPAWN_EGG_ZOMBIE_EYES = 0x799C65;

    /**
     * The spawn egg of the zombie spider (Brood Ability framework). Like the other eggs of the brood it is a test
     * entrance: the spider that appears belongs to the player who used the egg, and it follows by default.
     */
    public static final RegistryObject<Item> ZOMBIE_SPIDER_SPAWN_EGG = ITEMS.register("zombie_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.ZOMBIE_SPIDER, SPAWN_EGG_ZOMBIE_GREEN,
                    SPAWN_EGG_ZOMBIE_EYES, new Item.Properties()));

    /**
     * The two colours of the spawn egg of a skeleton spider: the light bone grey and the dark grey of the egg of a
     * vanilla skeleton, taken from {@code Items.SKELETON_SPAWN_EGG} of this exact version.
     */
    private static final int SPAWN_EGG_SKELETON_BONE = 0xC1C1C1;

    private static final int SPAWN_EGG_SKELETON_SHADE = 0x494949;

    /**
     * The two colours of the spawn egg of a chrono spider: the near black and the black of the egg of a vanilla
     * enderman, taken from {@code Items.ENDERMAN_SPAWN_EGG} of this exact version. The spider itself is dark as well,
     * so the egg of this species is the darkest one of the brood.
     */
    private static final int SPAWN_EGG_CHRONO_DARK = 0x161616;

    private static final int SPAWN_EGG_CHRONO_BLACK = 0x000000;

    /**
     * The spawn eggs of the two special spiders of this round (Special Spiders round I). Like every egg of the brood
     * they are test entrances: the spider that appears belongs to the player who used the egg and follows by default.
     */
    public static final RegistryObject<Item> SKELETON_SPIDER_SPAWN_EGG = ITEMS.register("skeleton_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.SKELETON_SPIDER, SPAWN_EGG_SKELETON_BONE,
                    SPAWN_EGG_SKELETON_SHADE, new Item.Properties()));

    public static final RegistryObject<Item> CHRONO_SPIDER_SPAWN_EGG = ITEMS.register("chrono_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.CHRONO_SPIDER, SPAWN_EGG_CHRONO_DARK,
                    SPAWN_EGG_CHRONO_BLACK, new Item.Properties()));

    /**
     * The two colours of the spawn egg of a stray spider: the greys of the egg of a vanilla stray, taken from
     * {@code Items.STRAY_SPAWN_EGG} of this exact version.
     */
    private static final int SPAWN_EGG_STRAY_GREY = 0x617677;

    private static final int SPAWN_EGG_STRAY_WHITE = 0xEAEAEA;

    /**
     * The two colours of the spawn egg of a husk spider: the sand and the pale desert tone of the egg of a vanilla husk,
     * taken from {@code Items.HUSK_SPAWN_EGG} of this exact version.
     */
    private static final int SPAWN_EGG_HUSK_SAND = 0x797061;

    private static final int SPAWN_EGG_HUSK_PALE = 0xE6CC94;

    /**
     * The spawn eggs of the two desert spiders of this round (Stray and Husk Spider round). Like every egg of the brood
     * they are test entrances: the spider that appears belongs to the player who used the egg and follows by default.
     */
    public static final RegistryObject<Item> STRAY_SPIDER_SPAWN_EGG = ITEMS.register("stray_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.STRAY_SPIDER, SPAWN_EGG_STRAY_GREY,
                    SPAWN_EGG_STRAY_WHITE, new Item.Properties()));

    public static final RegistryObject<Item> HUSK_SPIDER_SPAWN_EGG = ITEMS.register("husk_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.HUSK_SPIDER, SPAWN_EGG_HUSK_SAND,
                    SPAWN_EGG_HUSK_PALE, new Item.Properties()));

    /**
     * The two colours of the spawn egg of a drowned spider: the cold cyan and the green of the egg of a vanilla drowned,
     * taken from {@code Items.DROWNED_SPAWN_EGG} of this exact version.
     */
    private static final int SPAWN_EGG_DROWNED_CYAN = 0x8FF1D7;

    private static final int SPAWN_EGG_DROWNED_GREEN = 0x799C65;

    /**
     * The spawn egg of the drowned spider (Drowned Spider round). Like every egg of the brood it is a test entrance: the
     * spider that appears belongs to the player who used the egg and follows by default, and in creative mode it can be
     * taken straight from the tab of the mod.
     */
    public static final RegistryObject<Item> DROWNED_SPIDER_SPAWN_EGG = ITEMS.register("drowned_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.DROWNED_SPIDER, SPAWN_EGG_DROWNED_CYAN,
                    SPAWN_EGG_DROWNED_GREEN, new Item.Properties()));

    /**
     * The two colours of the spawn egg of a creeper spider: the green and the black of the egg of a vanilla creeper,
     * taken from {@code Items.CREEPER_SPAWN_EGG} of this exact version.
     */
    private static final int SPAWN_EGG_CREEPER_GREEN = 0x0DA70B;

    private static final int SPAWN_EGG_CREEPER_BLACK = 0x000000;

    /**
     * The spawn egg of the creeper spider (Phase X). Like every egg of the brood it is a test entrance: the spider that
     * appears belongs to the player who used the egg and follows by default, and in creative mode it is taken straight
     * from the tab of the mod.
     */
    public static final RegistryObject<Item> CREEPER_SPIDER_SPAWN_EGG = ITEMS.register("creeper_spider_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.CREEPER_SPIDER, SPAWN_EGG_CREEPER_GREEN,
                    SPAWN_EGG_CREEPER_BLACK, new Item.Properties()));

    /** The teal of the body of an infested Steve: the colour of the brood that drives it, the first colour of its egg. */
    private static final int SPAWN_EGG_INFESTED_TEAL = 0x2E8B8B;

    /** The dark red of the eyes of the brood inside him, the second colour of its spawn egg. */
    private static final int SPAWN_EGG_INFESTED_RED = 0x8C2020;

    /**
     * The spawn egg of the infested Steve (Infested Steve round). Like every egg of the brood it is a test entrance: the
     * soldier that appears belongs to the player who used the egg, follows by default - and it appears with empty hands,
     * because a fresh body has not looted anything yet.
     */
    public static final RegistryObject<Item> INFESTED_STEVE_SPAWN_EGG = ITEMS.register("infested_steve_spawn_egg",
            () -> new SpiderSpawnEggItem(HungryCraftEntities.INFESTED_STEVE, SPAWN_EGG_INFESTED_TEAL,
                    SPAWN_EGG_INFESTED_RED, new Item.Properties()));

    /**
     * The five spider hooks (feature SPIDER-HOOK), plain to netherite. They are weapons and not consumables:
     * one rod is one item that is held, so the stack size is one. What a quality changes lives in
     * {@link SpiderHookTier} - how hard the web drops, how many casts in a row the rod takes before it needs
     * its break, how far its silk reaches and how much wear a pull costs.
     */
    public static final RegistryObject<Item> SPIDER_HOOK = registerHook(SpiderHookTier.PLAIN);

    public static final RegistryObject<Item> IRON_SPIDER_HOOK = registerHook(SpiderHookTier.IRON);

    public static final RegistryObject<Item> GOLDEN_SPIDER_HOOK = registerHook(SpiderHookTier.GOLDEN);

    public static final RegistryObject<Item> DIAMOND_SPIDER_HOOK = registerHook(SpiderHookTier.DIAMOND);

    public static final RegistryObject<Item> NETHERITE_SPIDER_HOOK = registerHook(SpiderHookTier.NETHERITE);

    /**
     * A rod wears out like any other tool of the game, so it is registered with the durability of its quality.
     * That value is only the flag that makes the stack damageable at all: the number the rod really lives by
     * is read from the configuration through {@code SpiderHookItem}, so a pack author may move it.
     */
    private static RegistryObject<Item> registerHook(SpiderHookTier tier)
    {
        return ITEMS.register(tier.itemName(),
                () -> new SpiderHookItem(tier, new Item.Properties().stacksTo(1).durability(tier.baseDurability())));
    }

    // ------------------------------------------------------------------ the seven soul items (Soul Consumable round)

    /**
     * The seven lumps of soul of this round, from the benevolent up to the hero.
     *
     * <p>They are ordinary items of sixty four and nothing else: no durability, no recipe, no smelting and no smithing
     * anywhere in the mod, and no creature of the game ever drops one. The only way to own a soul is to find one, and
     * the only way to own its worth is to swallow it - one right click, one item, {@link SoulItem#soulValue()} souls.
     *
     * <p>The worth is written once, here, at registration: the item is the definition of its own value, so no file and
     * no client can name an amount the game did not put there.
     */
    public static final RegistryObject<Item> BENEVOLENT_SOUL = registerSoul("benevolent_soul", 100L);

    public static final RegistryObject<Item> NAMELESS_TRAVELER_SOUL = registerSoul("nameless_traveler_soul", 150L);

    public static final RegistryObject<Item> THIEF_SOUL = registerSoul("thief_soul", 250L);

    public static final RegistryObject<Item> SCOUNDREL_SOUL = registerSoul("scoundrel_soul", 500L);

    public static final RegistryObject<Item> EXECUTIONER_SOUL = registerSoul("executioner_soul", 800L);

    public static final RegistryObject<Item> TYRANT_SOUL = registerSoul("tyrant_soul", 1000L);

    public static final RegistryObject<Item> HERO_SOUL = registerSoul("hero_soul", 3000L);

    /** One soul item: a stack of sixty four, and the worth it pays when it is swallowed. */
    private static RegistryObject<Item> registerSoul(String name, long soulValue)
    {
        return ITEMS.register(name, () -> new SoulItem(soulValue, new Item.Properties().stacksTo(64)));
    }

    /**
     * The seven souls in ascending worth - the one order the creative tab draws them in and the one order the game
     * tests read them by. It is built from the registrations above and holds no second copy of anything.
     */
    public static List<RegistryObject<Item>> soulsInWorthOrder()
    {
        return List.of(BENEVOLENT_SOUL, NAMELESS_TRAVELER_SOUL, THIEF_SOUL, SCOUNDREL_SOUL, EXECUTIONER_SOUL, TYRANT_SOUL,
                HERO_SOUL);
    }

    private HungryCraftItems()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        ITEMS.register(modEventBus);
    }
}
