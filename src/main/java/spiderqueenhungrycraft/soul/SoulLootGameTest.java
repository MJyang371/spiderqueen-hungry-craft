package spiderqueenhungrycraft.soul;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The proof of the soul containers (Soul Consumable round): the weights roll a count of zero to four, a real chest of
 * the world really pays, the whole vanilla ladder of structures lands on the soul the design asks for, and the three
 * optional compatibility groups land on the family of the structure they are a version of - with nothing at all when
 * the mod behind an alias is not installed.
 *
 * <p><b>The resolver is pure, and that is what makes this provable.</b> Every mapping is asked through the same entry
 * point the loot modifier uses, with nothing but {@link ResourceLocation}s - so a family can be proven without a
 * chest, a structure or a mod, and a name of a mod that is not even installed is just a string that matches its rule.
 *
 * <p><b>One promise is asked of the world itself.</b> A real chest is placed, given a real vanilla loot table and
 * filled by the server, so the whole chain - the global loot modifier, the container check, the rule book and the one
 * appended stack - runs exactly as it will in a player's world.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SoulLootGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 60;

    /** Where the chests of these tests stand: on the floor block of the test structure. */
    private static final BlockPos GROUND = new BlockPos(2, 1, 2);

    /** The soul the benevolent family pays, by the id the design gave it. */
    private static final String BENEVOLENT = "spiderqueen_hungrycraft:benevolent_soul";

    /** The soul the nameless traveler family pays. */
    private static final String NAMELESS = "spiderqueen_hungrycraft:nameless_traveler_soul";

    /** The soul a thief is worth. */
    private static final String THIEF = "spiderqueen_hungrycraft:thief_soul";

    /** The soul a scoundrel is worth. */
    private static final String SCOUNDREL = "spiderqueen_hungrycraft:scoundrel_soul";

    /** The soul an executioner is worth. */
    private static final String EXECUTIONER = "spiderqueen_hungrycraft:executioner_soul";

    /** The soul a tyrant is worth. */
    private static final String TYRANT = "spiderqueen_hungrycraft:tyrant_soul";

    /** The soul a hero is worth. */
    private static final String HERO = "spiderqueen_hungrycraft:hero_soul";

    private SoulLootGameTest()
    {
    }

    // ------------------------------------------------------------------ TEST G: the dice

    /**
     * <b>TEST G: the weights always roll a count of zero to four, and they roll the whole range.</b>
     *
     * <p>The random source is seeded, so this test cannot flake: the same world always sees the same four thousand
     * counts. The weights themselves are read from the rule book and only have to be legal - one entry per count, none
     * of them negative, and not all of them zero.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void theweightsalwaysrollzerotofour(GameTestHelper helper)
    {
        List<Integer> weights = SoulLootRules.countWeights();

        expect(helper, "there is one weight per possible count", weights.size() == SoulLootRules.MAX_COUNT + 1);

        int total = 0;

        for (int weight : weights)
        {
            expect(helper, "no weight is negative", weight >= 0);
            total += weight;
        }

        expect(helper, "at least one count can happen", total > 0);

        RandomSource random = RandomSource.create(20261008L);
        int paid = 0;
        long sum = 0L;
        int rolls = 4000;

        for (int roll = 0; roll < rolls; roll++)
        {
            int count = SoulLootRules.rollCount(random);

            expect(helper, "a count is never below zero", count >= 0);
            expect(helper, "a count is never above four", count <= SoulLootRules.MAX_COUNT);

            if (count > 0)
            {
                paid++;
            }

            sum += count;
        }

        double average = (double) sum / (double) rolls;

        expect(helper, "the average count of a container is the one the weights describe", average > 1.5D && average < 2.5D);
        expect(helper, "almost every container holds something", paid > rolls * 9 / 10);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST H and TEST O: the world itself

    /**
     * <b>TEST H: the modifier appends at most one stack, and never more than four pieces.</b>
     *
     * <p>A chest is placed, the vanilla loot table of a monster room is asked for forty times with forty different
     * seeds, and what is inspected is the loot the global loot modifiers hand over - so the whole chain runs for real:
     * the modifier, the container check, the rule book and the one appended stack.
     *
     * <p><b>What is asserted is what is injected, not what a container looks like afterwards.</b> Once a container is
     * filled, vanilla spreads its own loot by itself: {@code LootTable#fill} splits every stack of two or more over the
     * free slots (a stack of five diamonds is spread the very same way), so a chest that is not full may well show two
     * slots of one soul. That happens after this mod is done, it happens to the loot of every mod, and it can never
     * push another mod's loot out: the splitting only ever uses slots that are still empty.
     *
     * <p>The chest is also what proves that the family of a monster room is reached through its loot table alone - a
     * monster room is a chest set into a wall and not a registered structure, so nothing but the table can say what it
     * is. The soul it pays is asked of the rule book and not written down here, so a pack may move a family to another
     * soul without breaking this test.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void arealchestpaysatonestack(GameTestHelper helper)
    {
        BlockPos pos = chestPosition(helper);
        int paid = 0;

        for (long seed = 1L; seed <= 40L; seed++)
        {
            ObjectArrayList<ItemStack> loot = lootOf(helper, pos, BuiltInLootTables.SIMPLE_DUNGEON, seed);
            int stacks = 0;
            int pieces = 0;
            ItemStack soul = ItemStack.EMPTY;

            for (ItemStack stack : loot)
            {
                if (stack.getItem() instanceof SoulItem)
                {
                    stacks++;
                    pieces += stack.getCount();
                    soul = stack;
                }
            }

            expect(helper, "the modifier never appends two stacks of soul", stacks <= 1);
            expect(helper, "the modifier never appends more than four pieces", pieces <= SoulLootRules.MAX_COUNT);

            if (stacks == 1)
            {
                paid++;

                expect(helper, "a dungeon chest pays the soul the rule book gives that family",
                        name(soul).equals(SoulLootRules.soulOf(SoulFamily.DUNGEON).map(ResourceLocation::toString)
                                .orElse("")));
            }
        }

        if (chanceOfSomething() > 0)
        {
            expect(helper, "a dungeon chest really pays sometimes", paid > 0);
        }

        helper.succeed();
    }

    /**
     * <b>TEST O and TEST P: a container that no rule knows keeps its own loot.</b>
     *
     * <p>An igloo is deliberately not a family of this round, and its loot is asked for twenty times over a real chest:
     * not one piece of soul may appear. The same is then asked of the resolver - a structure and a table that no rule
     * mentions resolve to nothing - and of the one table that is refused on purpose: the dispenser of a jungle temple,
     * which sits inside a structure that does pay.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void acontainerthatnobodyknowsgetsnothing(GameTestHelper helper)
    {
        BlockPos pos = chestPosition(helper);

        for (long seed = 1L; seed <= 20L; seed++)
        {
            for (ItemStack stack : lootOf(helper, pos, BuiltInLootTables.IGLOO_CHEST, seed))
            {
                expect(helper, "an igloo chest never holds a soul", !(stack.getItem() instanceof SoulItem));
            }
        }

        expect(helper, "a structure and a table that no rule knows resolve to nothing",
                SoulLootRules.resolve(List.of(id("example:an_unknown_structure")), List.of(),
                        id("example:chests/an_unknown_chest")).isEmpty());

        expect(helper, "the dispenser of a jungle temple is refused although the temple pays",
                SoulLootRules.isIgnored(id("minecraft:chests/jungle_temple_dispenser")));
        expect(helper, "the jungle temple of the game is a family of this round",
                SoulLootRules.resolve(List.of(id("minecraft:jungle_pyramid")), List.of(),
                        id("minecraft:chests/jungle_temple")).isPresent());

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST I: the whole vanilla ladder

    /**
     * <b>TEST I: every structure of the game lands on the soul the design gives it.</b>
     *
     * <p>Both halves of a container are exercised: the structures that own the chest, and the loot tables that a chest
     * with no structure at all can be known by. The sub types of the design are here too - a village smith, a shipwreck
     * treasure and a bastion treasure are worth more than the family they belong to.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void everyvanillastructurelandsonthesoulofthedesign(GameTestHelper helper)
    {
        // Village: the ordinary houses pay a benevolent soul, the three smiths pay a nameless traveler.
        expects(helper, null, "minecraft:chests/village/village_plains_house", SoulFamily.VILLAGE, BENEVOLENT);
        expects(helper, null, "minecraft:chests/village/village_butcher", SoulFamily.VILLAGE, BENEVOLENT);
        expects(helper, null, "minecraft:chests/village/village_weaponsmith", SoulFamily.VILLAGE, NAMELESS);
        expects(helper, null, "minecraft:chests/village/village_armorer", SoulFamily.VILLAGE, NAMELESS);
        expects(helper, null, "minecraft:chests/village/village_toolsmith", SoulFamily.VILLAGE, NAMELESS);

        // The monster room has no structure at all - only its loot table says what it is - and the mineshaft has one.
        expects(helper, null, "minecraft:chests/simple_dungeon", SoulFamily.DUNGEON, BENEVOLENT);
        expects(helper, "minecraft:mineshaft", "minecraft:chests/abandoned_mineshaft", SoulFamily.MINESHAFT, BENEVOLENT);
        expects(helper, "minecraft:mineshaft_mesa", "minecraft:chests/abandoned_mineshaft", SoulFamily.MINESHAFT,
                BENEVOLENT);

        // A shipwreck pays a traveler for its supply and its map, and a thief for its treasure.
        expects(helper, "minecraft:shipwreck", "minecraft:chests/shipwreck_supply", SoulFamily.SHIPWRECK, NAMELESS);
        expects(helper, "minecraft:shipwreck_beached", "minecraft:chests/shipwreck_map", SoulFamily.SHIPWRECK, NAMELESS);
        expects(helper, "minecraft:shipwreck", "minecraft:chests/shipwreck_treasure", SoulFamily.SHIPWRECK, THIEF);

        // The ruined portal, the buried treasure and the two pyramids.
        expects(helper, "minecraft:ruined_portal", "minecraft:chests/ruined_portal", SoulFamily.RUINED_PORTAL, NAMELESS);
        expects(helper, "minecraft:buried_treasure", "minecraft:chests/buried_treasure", SoulFamily.BURIED_TREASURE,
                THIEF);
        expects(helper, "minecraft:desert_pyramid", "minecraft:chests/desert_pyramid", SoulFamily.DESERT_PYRAMID, THIEF);
        expects(helper, "minecraft:jungle_pyramid", "minecraft:chests/jungle_temple", SoulFamily.JUNGLE_TEMPLE, THIEF);

        // The outpost, the stronghold, the mansion and the fortress.
        expects(helper, "minecraft:pillager_outpost", "minecraft:chests/pillager_outpost", SoulFamily.PILLAGER_OUTPOST,
                SCOUNDREL);
        expects(helper, "minecraft:stronghold", "minecraft:chests/stronghold_library", SoulFamily.STRONGHOLD, SCOUNDREL);
        expects(helper, "minecraft:stronghold", "minecraft:chests/stronghold_crossing", SoulFamily.STRONGHOLD, SCOUNDREL);
        expects(helper, "minecraft:mansion", "minecraft:chests/woodland_mansion", SoulFamily.WOODLAND_MANSION,
                EXECUTIONER);
        expects(helper, "minecraft:fortress", "minecraft:chests/nether_bridge", SoulFamily.NETHER_FORTRESS, EXECUTIONER);

        // The bastion: the three ordinary rooms are an executioner, the treasure room is a tyrant.
        expects(helper, "minecraft:bastion_remnant", "minecraft:chests/bastion_other", SoulFamily.BASTION, EXECUTIONER);
        expects(helper, "minecraft:bastion_remnant", "minecraft:chests/bastion_bridge", SoulFamily.BASTION, EXECUTIONER);
        expects(helper, "minecraft:bastion_remnant", "minecraft:chests/bastion_hoglin_stable", SoulFamily.BASTION,
                EXECUTIONER);
        expects(helper, "minecraft:bastion_remnant", "minecraft:chests/bastion_treasure", SoulFamily.BASTION, TYRANT);

        // The ancient city, including its ice box, and the end city.
        expects(helper, "minecraft:ancient_city", "minecraft:chests/ancient_city", SoulFamily.ANCIENT_CITY, TYRANT);
        expects(helper, "minecraft:ancient_city", "minecraft:chests/ancient_city_ice_box", SoulFamily.ANCIENT_CITY,
                TYRANT);
        expects(helper, "minecraft:end_city", "minecraft:chests/end_city_treasure", SoulFamily.END_CITY, HERO);

        // A structure tag is an alias of its own: every village of the game, present and future, is in the tag.
        expect(helper, "the village tag claims a chest that no file names",
                SoulLootRules.resolve(List.of(), List.of(id("minecraft:village")), null)
                        .map(SoulLootRules.Plan::family).orElse(null) == SoulFamily.VILLAGE);
        expect(helper, "the mineshaft tag claims a mineshaft of a mod this mod has never heard of",
                SoulLootRules.resolve(List.of(), List.of(id("minecraft:mineshaft")), null)
                        .map(SoulLootRules.Plan::family).orElse(null) == SoulFamily.MINESHAFT);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST J and TEST K: the family wins

    /**
     * <b>TEST J: a container that borrowed a foreign loot table keeps the family of its structure.</b>
     *
     * <p>A village that used the library table of a stronghold - exactly what a village rewrite can do - is still only
     * a village, and a chest of it may never pay the soul of a stronghold.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void thestructurewinsovertheloftable(GameTestHelper helper)
    {
        expects(helper, "minecraft:village_plains", "minecraft:chests/stronghold_library", SoulFamily.VILLAGE, BENEVOLENT);
        expects(helper, "minecraft:village_plains", "minecraft:chests/pillager_outpost", SoulFamily.VILLAGE, BENEVOLENT);
        expects(helper, "minecraft:village_taiga", "minecraft:chests/nether_bridge", SoulFamily.VILLAGE, BENEVOLENT);

        // And the other way round: a stronghold that used a table of a village is still a stronghold.
        expects(helper, "minecraft:stronghold", "minecraft:chests/village/village_plains_house", SoulFamily.STRONGHOLD,
                SCOUNDREL);

        helper.succeed();
    }

    /**
     * <b>TEST K: a sub type of the very family the structure is still sharpens the soul.</b>
     *
     * <p>The rule that the structure wins must not swallow the sub types of the design: the smith of a village is a
     * village and pays a traveler, and the treasure of a shipwreck is a shipwreck and pays a thief.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void asubtypeofthefamilystillsharpens(GameTestHelper helper)
    {
        expects(helper, "minecraft:village_plains", "minecraft:chests/village/village_weaponsmith", SoulFamily.VILLAGE,
                NAMELESS);
        expects(helper, "minecraft:shipwreck", "minecraft:chests/shipwreck_treasure", SoulFamily.SHIPWRECK, THIEF);
        expects(helper, "minecraft:bastion_remnant", "minecraft:chests/bastion_treasure", SoulFamily.BASTION, TYRANT);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST L: Repurposed Structures

    /**
     * <b>TEST L: the variants of Repurposed Structures land on the family they are a version of.</b>
     *
     * <p>The ids below are the ones the jar of the mod really declares, and every one of them is matched even though
     * that mod is not installed here at all - the rule book of this mod carries the names, and a world without the mod
     * simply never sees them. A jungle fortress stays a nether fortress, an ocean city stays an end city and an end
     * mineshaft stays a mineshaft, because a variant is what its structure is and not where it stands.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void thevariantsofarepurposedstructurelandontheirfamily(GameTestHelper helper)
    {
        family(helper, "repurposed_structures:village_birch", SoulFamily.VILLAGE);
        family(helper, "repurposed_structures:mineshaft_end", SoulFamily.MINESHAFT);
        family(helper, "repurposed_structures:mineshaft_nether", SoulFamily.MINESHAFT);
        family(helper, "repurposed_structures:fortress_jungle", SoulFamily.NETHER_FORTRESS);
        family(helper, "repurposed_structures:bastion_underground", SoulFamily.BASTION);
        family(helper, "repurposed_structures:pyramid_jungle", SoulFamily.DESERT_PYRAMID);
        family(helper, "repurposed_structures:temple_ocean", SoulFamily.JUNGLE_TEMPLE);
        family(helper, "repurposed_structures:outpost_nether_brick", SoulFamily.PILLAGER_OUTPOST);
        family(helper, "repurposed_structures:mansion_jungle", SoulFamily.WOODLAND_MANSION);
        family(helper, "repurposed_structures:ancient_city_ocean", SoulFamily.ANCIENT_CITY);
        family(helper, "repurposed_structures:stronghold_end", SoulFamily.STRONGHOLD);
        family(helper, "repurposed_structures:ruined_portal_end", SoulFamily.RUINED_PORTAL);
        family(helper, "repurposed_structures:shipwreck_end", SoulFamily.SHIPWRECK);
        family(helper, "repurposed_structures:city_overworld", SoulFamily.END_CITY);

        // A shipwreck of that mod keeps the sub types of a shipwreck, and a bastion of it keeps the treasure.
        expects(helper, "repurposed_structures:shipwreck_end", "repurposed_structures:chests/shipwrecks/end/supply",
                SoulFamily.SHIPWRECK, NAMELESS);
        expects(helper, "repurposed_structures:shipwreck_end", "repurposed_structures:chests/shipwrecks/end/treasure",
                SoulFamily.SHIPWRECK, THIEF);
        expects(helper, "repurposed_structures:bastion_underground",
                "repurposed_structures:chests/bastions/underground/treasure", SoulFamily.BASTION, TYRANT);

        // And the structures of that mod that this round deliberately leaves alone stay without a soul.
        none(helper, "repurposed_structures:monument_jungle");
        none(helper, "repurposed_structures:witch_hut_oak");
        none(helper, "repurposed_structures:igloo_grassy");
        none(helper, "repurposed_structures:ruins_land_cold");

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST M, TEST N and TEST P: the YUNG replacements

    /**
     * <b>TEST M: the replacements of YUNG land on the structure they replace.</b>
     *
     * <p>Each of these is the structure that the mod rebuilt, so each one is what it replaces: better dungeons are
     * dungeons, better mineshafts are mineshafts, better strongholds are strongholds. The two mods of that family that
     * this round deliberately does not pay - the ocean monument and the witch hut - are asked about as well, and they
     * must stay empty.
     *
     * <p><b>TEST N is answered by every line above:</b> not one of these mods is loaded in this run, and their names are
     * matched as plain identities, so nothing can be missing at class loading time and nothing has to be installed for
     * the rules of a world to be read.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_loot", timeoutTicks = TIMEOUT_TICKS)
    public static void thereplacementsofayungmodlandontheirfamily(GameTestHelper helper)
    {
        family(helper, "betterdungeons:small_dungeon", SoulFamily.DUNGEON);
        family(helper, "betterdungeons:zombie_dungeon", SoulFamily.DUNGEON);
        family(helper, "betterdeserttemples:desert_temple", SoulFamily.DESERT_PYRAMID);
        family(helper, "betterjungletemples:jungle_temple", SoulFamily.JUNGLE_TEMPLE);
        family(helper, "bettermineshafts:mineshaft_oak", SoulFamily.MINESHAFT);
        family(helper, "bettermineshafts:mineshaft_lush", SoulFamily.MINESHAFT);
        family(helper, "betterstrongholds:stronghold", SoulFamily.STRONGHOLD);
        family(helper, "betterfortresses:fortress", SoulFamily.NETHER_FORTRESS);

        // TEST P: a rebuilt monument and a rebuilt swamp hut are deliberately left without a soul of this round.
        none(helper, "betteroceanmonuments:ocean_monument");
        none(helper, "betterwitchhuts:witch_hut");
        none(helper, "betterwitchhuts:witch_circle");

        // An identity of a mod that does not exist anywhere is just an identity: it matches no rule and resolves to
        // nothing, and asking about it can never fail.
        expect(helper, "a name of a mod that is not installed resolves to nothing",
                SoulLootRules.resolve(List.of(id("a_mod_that_does_not_exist:fortress")),
                        List.of(id("a_mod_that_does_not_exist:some_tag")), id("a_mod_that_does_not_exist:chests/loot"))
                        .isEmpty());

        helper.succeed();
    }

    // ------------------------------------------------------------------ the helpers of this class

    /**
     * Asks the resolver what one container is worth and holds it to both halves of the answer.
     *
     * <p>The soul is compared by the identity the design gave it, because the ladder of this round is the design; the
     * family is compared as a family, because a family is what a structure is.
     */
    private static void expects(GameTestHelper helper, @Nullable String structure, String table, SoulFamily family,
            String soul)
    {
        List<ResourceLocation> structures = structure == null ? List.of() : List.of(id(structure));
        Optional<SoulLootRules.Plan> plan = SoulLootRules.resolve(structures, List.of(), id(table));

        expect(helper, table + " belongs to " + family.id(), plan.isPresent() && plan.get().family() == family);
        expect(helper, table + " pays " + soul,
                plan.isPresent() && plan.get().soul().toString().equals(soul)
                        && BuiltInRegistries.ITEM.containsKey(plan.get().soul()));
    }

    /** Asks the resolver what a container of that structure alone is worth, and holds the family of the answer. */
    private static void family(GameTestHelper helper, String structure, SoulFamily family)
    {
        Optional<SoulLootRules.Plan> plan = SoulLootRules.resolve(List.of(id(structure)), List.of(), null);

        expect(helper, structure + " belongs to " + family.id(), plan.isPresent() && plan.get().family() == family);
    }

    /** Holds that a structure this round deliberately leaves alone really resolves to nothing. */
    private static void none(GameTestHelper helper, String structure)
    {
        expect(helper, structure + " is deliberately left without a soul",
                SoulLootRules.resolve(List.of(id(structure)), List.of(), null).isEmpty());
    }

    /**
     * Places the chest of these tests on the floor block of the test structure and returns its absolute position.
     *
     * <p>The chest is not filled by itself here - the loot of a table is asked for directly - so the one thing under
     * test is what this mod hands out, and not the way vanilla spreads a stack over the free slots afterwards.
     */
    private static BlockPos chestPosition(GameTestHelper helper)
    {
        // The helper places a block by the relative position of the test structure, the level is asked by the absolute
        // one - the same pair every other test of the mod uses.
        helper.setBlock(GROUND, Blocks.CHEST);

        return helper.absolutePos(GROUND);
    }

    /**
     * @return the loot of that table for one container at that position, exactly as the global loot modifiers hand it
     *         over - the container is not filled, so the list is what this mod injected.
     */
    private static ObjectArrayList<ItemStack> lootOf(GameTestHelper helper, BlockPos pos, ResourceLocation table,
            long seed)
    {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);

        return level.getServer().getLootData().getLootTable(table).getRandomItems(params, seed);
    }

    /** @return the identity that name is, which has to be a legal one. */
    private static ResourceLocation id(String name)
    {
        ResourceLocation id = ResourceLocation.tryParse(name);

        if (id == null)
        {
            throw new IllegalArgumentException("not an identity: " + name);
        }

        return id;
    }

    /** @return the name of that stack, written the way the rule book writes identities. */
    private static String name(ItemStack stack)
    {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** @return the whole weight of a container that is not the chance of an empty one. */
    private static int chanceOfSomething()
    {
        List<Integer> weights = SoulLootRules.countWeights();
        int chance = 0;

        for (int count = 1; count < weights.size(); count++)
        {
            chance += weights.get(count);
        }

        return chance;
    }

    /** Fails the test when a promise is not kept, with the sentence that says which promise it was. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            throw new AssertionError(what);
        }
    }
}
