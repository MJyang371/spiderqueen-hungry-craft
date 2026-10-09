package spiderqueenhungrycraft.progression;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.soul.SoulCleanup;
import spiderqueenhungrycraft.soul.SoulDeathDrop;
import spiderqueenhungrycraft.soul.SoulRules;
import spiderqueenhungrycraft.soul.Souls;

/**
 * The runtime proof of the growth foundation (Progression Foundation round): the Queen Level, the price of a level,
 * the two permanent values, and the two things a territory core can sell.
 *
 * <p>Every promise is asked through the very entry points the server uses - {@link ProgressionService#upgrade},
 * {@link SoulShopService#purchase}, {@link ProgressionDefinition#parse} and the death handler of the soul system - so
 * nothing here imitates the system; it drives it. A request of a player is always made through a real session at a
 * real core, exactly as a packet would, which is also how the tests of the missing context are written.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class ProgressionGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the core of a test stands, and where the player using it stands. */
    private static final BlockPos CORE_POS = new BlockPos(2, 1, 2);

    /** The product the data pack of this mod ships, and what it is supposed to cost. */
    private static final long SPIDER_EGG_PRICE = 100L;
    private static final int SPIDER_EGG_COUNT = 1;

    private ProgressionGameTest()
    {
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return one fake player of this test, deliberately never put into the level (see the other tests of the mod). */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CORE_POS);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 0.0F, 0.0F);

        return player;
    }

    /**
     * Puts a real territory core down and opens its interface for that player.
     *
     * @return the position of the core, which every request of these tests then names.
     */
    private static BlockPos openCore(GameTestHelper helper, ServerPlayer player)
    {
        helper.setBlock(CORE_POS, HungryCraftBlocks.TERRITORY_CORE.get());

        BlockPos pos = helper.absolutePos(CORE_POS);

        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        ProgressionSessions.open(player, pos);

        return pos;
    }

    /** Closes the interface of that player and puts the level back where the test found it. */
    private static void closeCore(GameTestHelper helper, ServerPlayer player)
    {
        ProgressionSessions.close(player.getUUID());
    }

    /** @return the definition of the official numbers of this mod, rebuilt from a json text. */
    private static ProgressionDefinition definition(String json)
    {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        return ProgressionDefinition.parse(root);
    }

    /** @return the server of the test world, the one a game test always has. */
    private static MinecraftServer server(GameTestHelper helper)
    {
        return helper.getLevel().getServer();
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("PROGRESSION: " + what);
        }
    }

    // ------------------------------------------------------------------ TEST A: the first level

    /** <b>TEST A: a new player is level one, and level one is the lowest level there is.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void anewplayerstartsatlevelone(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_new_player");

        expect(helper, "a player who never bought a level is level one", QueenLevels.getLevel(player) == 1);
        expect(helper, "and the lowest level of the system is one", QueenData.MIN_QUEEN_LEVEL == 1);
        expect(helper, "the capability never hands out a level below one",
                QueenDataCapability.get(player).map(data ->
                {
                    data.setQueenLevel(-5);
                    return data.getQueenLevel() == 1;
                }).orElse(false));

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST B and TEST C: the price of a level

    /**
     * <b>TEST B and TEST C: the ladder of the official numbers.</b>
     *
     * <p>Reaching level two costs the base price, every tier of twenty levels adds its own step, and no boundary is
     * off by one: level twenty is still the base price and level twenty one is already the next one.
     */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void theladderoftheofficialnumbersisright(GameTestHelper helper)
    {
        ProgressionDefinition official = ProgressionDefinitions.current();

        expect(helper, "the definition of this mod really was read from the data pack",
                official.maxLevel() == 200 && official.baseCost() == 250 && official.levelsPerTier() == 20
                        && official.tierIncrement() == 50);

        expect(helper, "reaching the first level costs nothing", official.costToReach(1) == 0L);
        expect(helper, "reaching level two costs the base price", official.costToReach(2) == 250L);

        long[][] boundaries = {
                { 20, 250 }, { 21, 300 }, { 40, 300 }, { 41, 350 }, { 60, 350 }, { 61, 400 }, { 80, 400 },
                { 81, 450 }, { 100, 450 }, { 101, 500 }, { 120, 500 }, { 121, 550 }, { 140, 550 }, { 141, 600 },
                { 160, 600 }, { 161, 650 }, { 180, 650 }, { 181, 700 }, { 200, 700 } };

        for (long[] boundary : boundaries)
        {
            int level = (int)boundary[0];
            long expected = boundary[1];

            expect(helper, "level " + level + " costs " + expected + " souls",
                    official.costToReach(level) == expected);
        }

        // And the price really is flat inside a tier, which is what makes the boundaries the only interesting places.
        for (int level = 2; level <= 20; level++)
        {
            expect(helper, "every level of the first tier costs the base price (level " + level + ")",
                    official.costToReach(level) == 250L);
        }

        helper.succeed();
    }

    /** <b>TEST D: the whole ladder from level two to level two hundred costs 94,750 souls.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thewholeladdercosts94750(GameTestHelper helper)
    {
        ProgressionDefinition official = ProgressionDefinitions.current();
        long total = 0L;

        for (int level = 2; level <= official.maxLevel(); level++)
        {
            total += official.costToReach(level);
        }

        expect(helper, "the sum of every step from level 2 to level " + official.maxLevel() + " is 94750, found "
                + total, total == 94750L);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST E and TEST F: paying for a level

    /** <b>TEST E: a player who can pay the price is raised by one level and loses exactly that price.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void anupgradechargesthepriceandraisesthelevel(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_upgrade_ok");
        BlockPos core = openCore(helper, player);

        Souls.setSouls(player, 1000L);
        QueenLevels.setLevel(player, 1);

        expect(helper, "the upgrade was accepted", ProgressionService.upgrade(player, core, 2)
                == ProgressionFeedback.UPGRADE_OK);
        expect(helper, "the price of the second level was taken", Souls.getSouls(player) == 750L);
        expect(helper, "and the level really went up", QueenLevels.getLevel(player) == 2);

        closeCore(helper, player);
        helper.succeed();
    }

    /** <b>TEST F: a player who can not pay keeps every soul and stays where they are.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void anupgradewithoutenoughsoulschangesnothing(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_upgrade_poor");
        BlockPos core = openCore(helper, player);

        Souls.setSouls(player, 249L);
        QueenLevels.setLevel(player, 1);

        expect(helper, "the upgrade was refused for the souls",
                ProgressionService.upgrade(player, core, 2) == ProgressionFeedback.INSUFFICIENT_SOULS);
        expect(helper, "not one soul was taken", Souls.getSouls(player) == 249L);
        expect(helper, "and the level did not move", QueenLevels.getLevel(player) == 1);

        closeCore(helper, player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST G and TEST H: the rules of the ladder

    /** <b>TEST G: a level can never be skipped, whatever a player can pay.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void alevelcannotbeskipped(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_skip");
        BlockPos core = openCore(helper, player);

        Souls.setSouls(player, 100_000L);
        QueenLevels.setLevel(player, 5);

        expect(helper, "asking for the level after the next one is refused",
                ProgressionService.upgrade(player, core, 7) == ProgressionFeedback.NOT_THE_NEXT_LEVEL);
        expect(helper, "asking for a level already reached is refused",
                ProgressionService.upgrade(player, core, 5) == ProgressionFeedback.NOT_THE_NEXT_LEVEL);
        expect(helper, "even a pile of souls was not touched", Souls.getSouls(player) == 100_000L);
        expect(helper, "and the level stayed where it was", QueenLevels.getLevel(player) == 5);

        closeCore(helper, player);
        helper.succeed();
    }

    /** <b>TEST H: the top of the ladder is the top: level two hundred can not buy a two hundred and first.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thetopoftheladdercannotbepassed(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_top");
        BlockPos core = openCore(helper, player);

        int top = ProgressionDefinitions.current().maxLevel();
        Souls.setSouls(player, 1_000_000L);
        QueenLevels.setLevel(player, top);

        expect(helper, "the level above the top is refused",
                ProgressionService.upgrade(player, core, top + 1) == ProgressionFeedback.ALREADY_MAX_LEVEL);
        expect(helper, "no soul was spent on the refused level", Souls.getSouls(player) == 1_000_000L);
        expect(helper, "and the level is still the top", QueenLevels.getLevel(player) == top);

        closeCore(helper, player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST I and TEST J: the level is permanent

    /** <b>TEST I: a death may take the souls of a life, but never the level of a queen.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void adeathnevertakesalevel(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_death");
        QueenLevels.setLevel(player, 42);
        Souls.setSouls(player, 1000L);

        SoulDeathDrop.onPlayerDeath(player);

        expect(helper, "the souls of the life really were dropped", Souls.getSouls(player) == 0L);
        expect(helper, "but the permanent level stayed", QueenLevels.getLevel(player) == 42);

        // And the level survives the trip through the save file, which is what a respawn and a restart both read.
        CompoundTag saved = QueenDataCapability.get(player).map(QueenData::serializeNBT).orElseGet(CompoundTag::new);
        QueenData reloaded = new QueenData();
        reloaded.deserializeNBT(saved);

        expect(helper, "the level is written to the save file and read back", reloaded.getQueenLevel() == 42);

        SoulCleanup.clearAll(server(helper));
        helper.succeed();
    }

    /** <b>TEST J: the world rule about souls says nothing about levels.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void theworldruledoesnotchangelevels(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_rule_level");
        MinecraftServer server = server(helper);
        GameRules.BooleanValue keepSouls = server.getGameRules().getRule(SoulRules.keepSoulsRule());

        QueenLevels.setLevel(player, 7);
        Souls.setSouls(player, 500L);
        keepSouls.set(true, server);

        try
        {
            SoulDeathDrop.onPlayerDeath(player);

            expect(helper, "a world that spares souls really spares them", Souls.getSouls(player) == 500L);
            expect(helper, "and the level is untouched by that rule as well", QueenLevels.getLevel(player) == 7);
        }
        finally
        {
            keepSouls.set(false, server);
        }

        SoulCleanup.clearAll(server);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST K, L and M: the shop

    /** <b>TEST K: the spider egg of the data pack costs a hundred souls and really arrives.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thespidereggcanbebought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_shop_ok");
        BlockPos core = openCore(helper, player);
        ShopEntry egg = spiderEggEntry();

        expect(helper, "the data pack really loaded a spider egg product", egg != null);

        // The egg of the shop belongs to the brood queen (Skill Progression round): the product is only on sale for a
        // queen who owns that node, which is what the whole of TEST G and TEST H of the skill test pin down.
        QueenSkills.unlock(player, QueenSkills.BROOD_QUEEN);

        if (egg != null)
        {
            expect(helper, "the product is the one this round ships",
                    egg.count() == SPIDER_EGG_COUNT && egg.price() == SPIDER_EGG_PRICE && egg.enabled());

            Souls.setSouls(player, SPIDER_EGG_PRICE);

            expect(helper, "the purchase was accepted",
                    SoulShopService.purchase(player, core, egg.id()) == ProgressionFeedback.PURCHASE_OK);
            expect(helper, "the price was taken", Souls.getSouls(player) == 0L);
            expect(helper, "and the player really holds one spider egg",
                    countInInventory(player, HungryCraftItems.SPIDER_EGG.get()) == 1);
        }

        closeCore(helper, player);
        helper.succeed();
    }

    /** <b>TEST L: one soul short means no purchase at all.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thespidereggcannotbeboughtwithtoofewsouls(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_shop_poor");
        BlockPos core = openCore(helper, player);
        ShopEntry egg = spiderEggEntry();

        expect(helper, "the data pack really loaded a spider egg product", egg != null);

        // The product requires the brood queen (Skill Progression round), so it is bought here by a queen who owns it:
        // what this test measures is the purse and nothing else.
        QueenSkills.unlock(player, QueenSkills.BROOD_QUEEN);

        if (egg != null)
        {
            Souls.setSouls(player, SPIDER_EGG_PRICE - 1L);

            expect(helper, "the purchase was refused for the souls",
                    SoulShopService.purchase(player, core, egg.id()) == ProgressionFeedback.INSUFFICIENT_SOULS);
            expect(helper, "not one soul was taken", Souls.getSouls(player) == SPIDER_EGG_PRICE - 1L);
            expect(helper, "and no egg appeared", countInInventory(player, HungryCraftItems.SPIDER_EGG.get()) == 0);
        }

        closeCore(helper, player);
        helper.succeed();
    }

    /**
     * <b>TEST M: a full inventory never stops a purchase - what does not fit lands at the feet.</b>
     *
     * <p>It is the whole point of the shop that a queen can turn souls into things before she walks into somewhere
     * dangerous, so the shop must never be a way to lose souls to a full bag.
     */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void afullinventorystillgetsitsproduct(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_shop_full");
        BlockPos core = openCore(helper, player);
        ShopEntry egg = spiderEggEntry();

        expect(helper, "the data pack really loaded a spider egg product", egg != null);

        // The product requires the brood queen (Skill Progression round): this test is about the bag, not the purse.
        QueenSkills.unlock(player, QueenSkills.BROOD_QUEEN);

        if (egg == null)
        {
            closeCore(helper, player);
            helper.succeed();
            return;
        }

        // Every slot of the inventory holds a full stack of stone, so nothing can be put away any more.
        for (int slot = 0; slot < player.getInventory().items.size(); slot++)
        {
            player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        }

        Souls.setSouls(player, SPIDER_EGG_PRICE);

        expect(helper, "the purchase was accepted although the bag is full",
                SoulShopService.purchase(player, core, egg.id()) == ProgressionFeedback.PURCHASE_OK);
        expect(helper, "the price was taken all the same", Souls.getSouls(player) == 0L);
        expect(helper, "and the egg really lies on the ground next to the player",
                countDroppedEggs(helper, player) == 1);

        closeCore(helper, player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST N: what the shop refuses

    /** <b>TEST N: a product that is not for sale is refused - one that never existed, and one that was switched off.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void aproductthatisnotforsaleisrefused(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_shop_stale");
        BlockPos core = openCore(helper, player);
        Souls.setSouls(player, 1000L);

        ResourceLocation unknown = ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID,
                "no_such_product");

        expect(helper, "a product that does not exist is refused",
                SoulShopService.purchase(player, core, unknown) == ProgressionFeedback.UNKNOWN_PRODUCT);

        // And a product that a reload switched off, while it is still in the list of the server.
        List<ShopEntry> before = SoulShop.entries();
        ResourceLocation disabled = ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID,
                "disabled_test");

        try
        {
            SoulShop.set(List.of(new ShopEntry(disabled, HungryCraftItems.SPIDER_EGG.get(), 1, 10L, false, null)));

            expect(helper, "a product that is switched off is refused",
                    SoulShopService.purchase(player, core, disabled) == ProgressionFeedback.UNKNOWN_PRODUCT);
        }
        finally
        {
            SoulShop.set(before);
        }

        expect(helper, "not one soul was spent on either of them", Souls.getSouls(player) == 1000L);

        closeCore(helper, player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST O and TEST P: the context of a request

    /**
     * <b>TEST O and TEST P: a request without a valid open core is refused, whether it asks for a level or a product.</b>
     *
     * <p>Three ways of not having a valid context, and all three of them answer the same way: an interface that was
     * never opened, one whose player walked away, and one whose core is gone. Nothing is spent in any of them.
     */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void arequestwithoutavalidsessionisrefused(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_no_session");
        BlockPos core = helper.absolutePos(CORE_POS);
        ResourceLocation product = ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "spider_egg");

        helper.setBlock(CORE_POS, HungryCraftBlocks.TERRITORY_CORE.get());
        Souls.setSouls(player, 10_000L);
        QueenLevels.setLevel(player, 1);

        expect(helper, "an upgrade without a core that was ever opened is refused",
                ProgressionService.upgrade(player, core, 2) == ProgressionFeedback.DENIED);
        expect(helper, "and a purchase without one as well",
                SoulShopService.purchase(player, core, product) == ProgressionFeedback.DENIED);
        expect(helper, "nothing was spent and nothing moved",
                Souls.getSouls(player) == 10_000L && QueenLevels.getLevel(player) == 1);

        // Walked away from the core she opened.
        ProgressionSessions.open(player, core);
        player.moveTo(core.getX() + 100.0D, core.getY() + 1.0D, core.getZ(), 0.0F, 0.0F);

        expect(helper, "an upgrade from far away is refused",
                ProgressionService.upgrade(player, core, 2) == ProgressionFeedback.DENIED);
        expect(helper, "and a purchase from far away as well",
                SoulShopService.purchase(player, core, product) == ProgressionFeedback.DENIED);
        expect(helper, "still nothing was spent", Souls.getSouls(player) == 10_000L);

        // Back at the core, but the core itself is gone.
        player.moveTo(core.getX() + 0.5D, core.getY() + 1.0D, core.getZ() + 0.5D, 0.0F, 0.0F);
        helper.setBlock(CORE_POS, Blocks.AIR);

        expect(helper, "an upgrade at a core that is gone is refused",
                ProgressionService.upgrade(player, core, 2) == ProgressionFeedback.DENIED);
        expect(helper, "and so is a purchase at it",
                SoulShopService.purchase(player, core, product) == ProgressionFeedback.DENIED);
        expect(helper, "and still no soul was spent", Souls.getSouls(player) == 10_000L);
        expect(helper, "and the level never moved", QueenLevels.getLevel(player) == 1);

        ProgressionSessions.close(player.getUUID());
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST Q and TEST R: the definition

    /**
     * <b>TEST Q: the definition is read, validated, and replaceable - which is exactly what a reload does.</b>
     *
     * <p>The file of this mod really being in force is the observable half of the reload listener; the other half is
     * asked of {@link ProgressionDefinition#parse} directly, so every promise about a broken file is pinned down
     * without having to break one.
     */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thedefinitionisreadandcanbereplaced(GameTestHelper helper)
    {
        ProgressionDefinition official = ProgressionDefinitions.current();

        expect(helper, "the shipped file of this mod is in force",
                official.maxLevel() == 200 && official.baseCost() == 250 && official.levelsPerTier() == 20
                        && official.tierIncrement() == 50);

        expect(helper, "a valid file is accepted",
                accepts("{\"max_level\":300,\"base_cost\":10,\"levels_per_tier\":5,\"tier_increment\":2}"));
        expect(helper, "and its numbers really are used",
                definition("{\"max_level\":300,\"base_cost\":10,\"levels_per_tier\":5,\"tier_increment\":2}")
                        .costToReach(2) == 10L);

        expect(helper, "a maximum below one is refused", !accepts("{\"max_level\":0}"));
        expect(helper, "a negative base cost is refused", !accepts("{\"base_cost\":-1}"));
        expect(helper, "a tier of zero levels is refused", !accepts("{\"levels_per_tier\":0}"));
        expect(helper, "a negative tier step is refused", !accepts("{\"tier_increment\":-5}"));
        expect(helper, "text where a number belongs is refused", !accepts("{\"max_level\":\"many\"}"));
        expect(helper, "a fractional number is refused", !accepts("{\"base_cost\":12.5}"));

        // A file that is not usable never reaches the rules of the world.
        ProgressionDefinitions.set(new ProgressionDefinition(0, 250, 20, 50));
        expect(helper, "the holder refuses an unusable definition",
                ProgressionDefinitions.current().maxLevel() == 200);

        // And a reload really changes the numbers, which is what the listener hands to this holder.
        ProgressionDefinitions
                .set(definition("{\"max_level\":250,\"base_cost\":100,\"levels_per_tier\":10,\"tier_increment\":25}"));
        expect(helper, "a new definition is in force once it is set",
                ProgressionDefinitions.current().maxLevel() == 250);
        expect(helper, "with the price of the new file", ProgressionDefinitions.current().costToReach(11) == 125L);

        ProgressionDefinitions.reset();
        expect(helper, "and the official numbers can be put back",
                ProgressionDefinitions.current().maxLevel() == 200);

        helper.succeed();
    }

    /** <b>TEST R: a world whose maximum is lowered never downgrades a player - it only stops the ladder there.</b> */
    @GameTest(template = "humanspawn_flat", batch = "progression_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void aloweredmaximumneverdowngradesaplayer(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "progression_lowered_max");
        BlockPos core = openCore(helper, player);
        int official = ProgressionDefinitions.current().maxLevel();

        QueenLevels.setLevel(player, official);
        Souls.setSouls(player, 1_000_000L);

        ProgressionDefinitions.set(new ProgressionDefinition(150, 250, 20, 50));

        try
        {
            expect(helper, "the world now ends at 150", ProgressionDefinitions.current().maxLevel() == 150);
            expect(helper, "but a level that was already earned is never taken away",
                    QueenLevels.getLevel(player) == official);
            expect(helper, "and no further level can be bought",
                    ProgressionService.upgrade(player, core, official + 1) == ProgressionFeedback.ALREADY_MAX_LEVEL);
            expect(helper, "with nothing spent on the refused level", Souls.getSouls(player) == 1_000_000L);

            // Below the new end the ladder still works: a lowered maximum stops it, it does not break it.
            QueenLevels.setLevel(player, 149);
            Souls.setSouls(player, 1000L);

            long price = ProgressionDefinitions.current().costToReach(150);

            expect(helper, "a level inside the new maximum is still buyable",
                    ProgressionService.upgrade(player, core, 150) == ProgressionFeedback.UPGRADE_OK);
            expect(helper, "and it was paid for", Souls.getSouls(player) == 1000L - price);
            expect(helper, "up to the new end", QueenLevels.getLevel(player) == 150);
        }
        finally
        {
            ProgressionDefinitions.reset();
        }

        closeCore(helper, player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the last pieces of furniture

    /** @return the product of this mod's data pack: one spider egg, or null when the pack did not load one. */
    @Nullable
    private static ShopEntry spiderEggEntry()
    {
        for (ShopEntry entry : SoulShop.entries())
        {
            if (entry.item() == HungryCraftItems.SPIDER_EGG.get())
            {
                return entry;
            }
        }

        return null;
    }

    /** @return how many pieces of that item the player carries, in any slot. */
    private static int countInInventory(ServerPlayer player, Item item)
    {
        int found = 0;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);

            if (stack.is(item))
            {
                found += stack.getCount();
            }
        }

        return found;
    }

    /** @return how many eggs lie on the ground around the player, which is where a full bag sends them. */
    private static int countDroppedEggs(GameTestHelper helper, ServerPlayer player)
    {
        int found = 0;

        for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(6.0D)))
        {
            if (entity.getItem().is(HungryCraftItems.SPIDER_EGG.get()))
            {
                found += entity.getItem().getCount();
            }
        }

        return found;
    }

    /** @return true when that json text is accepted as a definition. */
    private static boolean accepts(String json)
    {
        try
        {
            definition(json);
            return true;
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }
}
