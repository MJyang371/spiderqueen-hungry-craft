package spiderqueenhungrycraft.soul;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.ImpSpiderEntity;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of the soul system (Soul Foundation V1): experience in, souls out on death, one remnant per
 * player at most, and a collection that can never happen twice or by the wrong hand.
 *
 * <p>Every promise is asked through the very entry points the server uses - {@code Player#giveExperiencePoints},
 * {@link SoulDeathDrop#onPlayerDeath}, {@link SoulRemnantEntity#tryClaim} and the real command of the mod - so
 * nothing here imitates the system; it drives it.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SoulSystemGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the players and the remnants of these tests stand: on the floor block of the test structure. */
    private static final BlockPos GROUND = new BlockPos(2, 1, 2);

    private SoulSystemGameTest()
    {
    }

    // ------------------------------------------------------------------ TEST A and TEST B: experience

    /**
     * <b>TEST A: a gain of experience pays souls.</b> Ten experience are a hundred souls, and the next gain adds its
     * own share on top.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void againofexperiencepaystensoulsperpoint(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_xp_queen");

        expect(helper, "a player who never gained experience owns no souls", Souls.getSouls(queen) == 0L);

        queen.giveExperiencePoints(10);
        expect(helper, "ten experience are one hundred souls", Souls.getSouls(queen) == 100L);

        queen.giveExperiencePoints(7);
        expect(helper, "and the next gain adds exactly its own share", Souls.getSouls(queen) == 170L);

        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST B: experience that is spent never takes a soul.</b>
     *
     * <p>Both directions of the change are asked: the event of the question with a negative amount, and the real call
     * the game itself makes when an anvil or a repair spends experience.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void spentexperienceneverspendsouls(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_xp_loss_queen");
        queen.giveExperiencePoints(5);

        long before = Souls.getSouls(queen);

        expect(helper, "the gain before the spend really happened", before == 50L);

        // The very event the rule is about, with the negative amount a spend carries.
        MinecraftForge.EVENT_BUS.post(new PlayerXpEvent.XpChange(queen, -3));
        expect(helper, "a negative change of experience takes no soul", Souls.getSouls(queen) == before);

        // And the real path of a spend, which is what an anvil and a repair of the game take.
        queen.giveExperiencePoints(-3);
        expect(helper, "the real spend path of the game takes no soul either", Souls.getSouls(queen) == before);

        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST C, D and E: the death

    /** <b>TEST C: a death empties the player and leaves one remnant with the souls of that life.</b> */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void adeathleavesaremnantwiththesoulsofthelife(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_death_queen");
        Souls.setSouls(queen, 1000L);

        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantData.ActiveRemnant record = SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID());
        SoulRemnantEntity remnant = newestRemnant(helper, queen);

        expect(helper, "the death emptied the player", Souls.getSouls(queen) == 0L);
        expect(helper, "the death left exactly one active record", record != null);
        expect(helper, "with the souls of that life in it", record != null && record.souls() == 1000L);
        expect(helper, "and a remnant entity really stands in the world", remnant != null);

        if (remnant != null && record != null)
        {
            expect(helper, "the remnant belongs to the player who died", queen.getUUID().equals(remnant.ownerUUID()));
            expect(helper, "the remnant carries the souls of that life", remnant.storedSouls() == 1000L);
            expect(helper, "and the remnant is the active record of its owner",
                    remnant.isStillTheRecordOfItsOwner());
            expect(helper, "the record names the token of the remnant", record.token().equals(remnant.remnantToken()));
        }

        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST D: a second death destroys the first remnant, wherever it stands.</b>
     *
     * <p>One thousand souls were left behind and never collected; the player then earns two hundred and dies again -
     * the thousand are gone for good and the new remnant holds the two hundred. The old entity, which was never
     * loaded again, discovers on its next tick that it is obsolete and removes itself.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void aseconddeathdestroyestheoldremnant(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_second_death_queen");
        Souls.setSouls(queen, 1000L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantEntity old = newestRemnant(helper, queen);

        expect(helper, "the first death left a remnant", old != null);

        Souls.setSouls(queen, 200L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantData.ActiveRemnant record = SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID());

        expect(helper, "the active record of the player is the new life", record != null && record.souls() == 200L);
        expect(helper, "the old thousand were not handed back to the player", Souls.getSouls(queen) == 0L);
        expect(helper, "and the player owns exactly one remnant", countRemnants(helper, queen) == 2);

        if (old != null)
        {
            expect(helper, "the old remnant is no longer the record of its player", !old.isStillTheRecordOfItsOwner());
            old.tick();
            expect(helper, "and it removes itself on its next tick", old.isRemoved());
        }

        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST E: a death with nothing left behind destroys the old remnant and creates nothing.</b>
     *
     * <p>The player never collected the remnant of the last life and then died again with zero souls. The old one is
     * invalidated all the same, and no empty remnant appears out of it.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void adeathwithnosoulscreatesnoremmnant(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_empty_death_queen");
        Souls.setSouls(queen, 1000L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantEntity old = newestRemnant(helper, queen);

        expect(helper, "the first death left a remnant", old != null);

        Souls.setSouls(queen, 0L);
        SoulDeathDrop.onPlayerDeath(queen);

        expect(helper, "the old remnant lost its record",
                SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID()) == null);
        expect(helper, "and no second, empty remnant was created", countRemnants(helper, queen) == 1);

        if (old != null)
        {
            expect(helper, "the old remnant knows that it is stale", !old.isStillTheRecordOfItsOwner());
        }

        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST F, G and H: more than one player

    /** <b>TEST F: two players leave two independent remnants, and neither replaces the other.</b> */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void twoplayersleavetwoindependentrecords(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer steve = fakePlayer(helper, "soul_steve");
        ServerPlayer alex = fakePlayer(helper, "soul_alex");

        Souls.setSouls(steve, 500L);
        SoulDeathDrop.onPlayerDeath(steve);
        Souls.setSouls(alex, 700L);
        SoulDeathDrop.onPlayerDeath(alex);

        SoulRemnantData data = SoulRemnantData.get(serverOf(helper));
        SoulRemnantData.ActiveRemnant steveRecord = data.activeFor(steve.getUUID());
        SoulRemnantData.ActiveRemnant alexRecord = data.activeFor(alex.getUUID());

        expect(helper, "Steve has his own record", steveRecord != null && steveRecord.souls() == 500L);
        expect(helper, "Alex has his own record", alexRecord != null && alexRecord.souls() == 700L);
        expect(helper, "the two records name two different remnants", steveRecord != null && alexRecord != null
                && !steveRecord.token().equals(alexRecord.token()));
        expect(helper, "and each of them owns exactly one remnant",
                countRemnants(helper, steve) == 1 && countRemnants(helper, alex) == 1);

        reset(helper);
        helper.succeed();
    }

    /** <b>TEST G: nobody collects the remnant of somebody else.</b> */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void nobodycollectssomebodyelsesremnant(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer steve = fakePlayer(helper, "soul_steve_owner");
        ServerPlayer alex = fakePlayer(helper, "soul_alex_stranger");

        Souls.setSouls(steve, 500L);
        SoulDeathDrop.onPlayerDeath(steve);

        SoulRemnantEntity remnant = newestRemnant(helper, steve);

        expect(helper, "the remnant of Steve exists", remnant != null);
        expect(helper, "and the stranger owns nothing", Souls.getSouls(alex) == 0L);

        if (remnant != null)
        {
            expect(helper, "Alex can not collect the remnant of Steve", !remnant.tryClaimByPlayer(alex));
            expect(helper, "the souls of Steve stayed in the remnant", Souls.getSouls(steve) == 0L);
            expect(helper, "the remnant still is the record of its owner", remnant.isStillTheRecordOfItsOwner());
        }

        reset(helper);
        helper.succeed();
    }

    /** <b>TEST H: the owner collects their own remnant, is paid once, and the remnant is gone.</b> */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void theownercollectshisownremnant(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer steve = fakePlayer(helper, "soul_collector");
        Souls.setSouls(steve, 820L);
        SoulDeathDrop.onPlayerDeath(steve);

        SoulRemnantEntity remnant = newestRemnant(helper, steve);

        expect(helper, "the remnant of Steve exists", remnant != null);

        if (remnant != null)
        {
            expect(helper, "the owner really collected the remnant", remnant.tryClaimByPlayer(steve));
            expect(helper, "the souls are back on the player", Souls.getSouls(steve) == 820L);
            expect(helper, "the record is gone",
                    SoulRemnantData.get(serverOf(helper)).activeFor(steve.getUUID()) == null);
            expect(helper, "the entity left the world", remnant.isRemoved());

            // A second hand in the same tick, or a second look at the same remnant, can never pay twice.
            expect(helper, "a second collection is refused", !remnant.tryClaimByPlayer(steve));
            expect(helper, "and it changes nothing at all", Souls.getSouls(steve) == 820L);
        }

        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST I and TEST J: spiders of the owner

    /**
     * <b>TEST I: a spider of the living owner carries the souls home for her.</b>
     *
     * <p>The spider is a real imp of the brood with the real ownership of the system, and the plain entity of
     * somebody else is refused before anything is paid. The one thing this test can not use is the online lookup of
     * the server - a game test has no player in the player list - so the remnant of the test answers that single
     * question itself, see {@link TestRemnant}.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void aspiderofthelivingqueencollectstheremnant(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_spider_queen");
        Souls.setSouls(queen, 400L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantData.ActiveRemnant record = SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID());

        expect(helper, "the queen left a remnant", record != null);

        ImpSpiderEntity ownSpider = spider(helper, queen.getUUID());
        ImpSpiderEntity ownerlessSpider = spider(helper, null);

        expect(helper, "a spider of the queen knows its queen", ownSpider.isOwnedBy(queen.getUUID()));
        expect(helper, "a spider without an owner belongs to nobody", !ownerlessSpider.isOwnedBy(queen.getUUID()));

        if (record != null)
        {
            expect(helper, "a spider that is not of the queen can not collect it",
                    !testRemnant(helper, queen, record, queen).tryClaimBySpider(ownerlessSpider));
            expect(helper, "and nothing was paid for that attempt", Souls.getSouls(queen) == 0L);

            expect(helper, "a spider of the living queen collects the remnant for her",
                    testRemnant(helper, queen, record, queen).tryClaimBySpider(ownSpider));
            expect(helper, "and the queen really received the souls", Souls.getSouls(queen) == 400L);
        }

        ownSpider.discard();
        ownerlessSpider.discard();
        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST J: a spider of a dead queen brings nothing back.</b>
     *
     * <p>The queen is dead and her spider stands right on the remnant of the life that just ended: nothing may be
     * paid. And the same is true for a queen the server simply can not reach, which is the answer the real lookup of
     * the system gives for a player who is not online.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void aspiderofthedeadqueencollectsnothing(GameTestHelper helper)
    {
        reset(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_dead_queen");
        Souls.setSouls(queen, 600L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantData.ActiveRemnant record = SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID());

        expect(helper, "the queen left a remnant", record != null);

        ImpSpiderEntity ownSpider = spider(helper, queen.getUUID());
        SoulRemnantEntity plainRemnant = newestRemnant(helper, queen);

        queen.setHealth(0.0F);
        expect(helper, "the queen really counts as dead", !queen.isAlive());

        if (record != null)
        {
            expect(helper, "a spider of the dead queen can not collect the remnant",
                    !testRemnant(helper, queen, record, queen).tryClaimBySpider(ownSpider));
            expect(helper, "the record survived that attempt",
                    SoulRemnantData.get(serverOf(helper)).activeFor(queen.getUUID()) != null);
            expect(helper, "and no soul was paid", Souls.getSouls(queen) == 0L);
        }

        if (plainRemnant != null)
        {
            expect(helper, "a queen the server can not reach has no online player at all",
                    plainRemnant.resolveOwner() == null);
            expect(helper, "so her spider collects nothing through the real lookup either",
                    !plainRemnant.tryClaimBySpider(ownSpider));
        }

        ownSpider.discard();
        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST K and TEST L: the world rule

    /**
     * <b>TEST K: {@code keepSpiderQueenSouls} spares the souls of a death.</b>
     *
     * <p>With the rule off a death costs the souls and leaves a remnant; with the rule on the same death is free,
     * creates nothing, and leaves the remnant of an earlier death exactly as it was.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void theworldrulekeepssouls(GameTestHelper helper)
    {
        reset(helper);
        MinecraftServer server = serverOf(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_rule_queen");
        GameRules.BooleanValue keepSouls = server.getGameRules().getRule(SoulRules.keepSoulsRule());

        expect(helper, "the rule of the soul system is really registered", keepSouls != null);

        if (keepSouls == null)
        {
            reset(helper);
            helper.succeed();
            return;
        }

        expect(helper, "and it is off by default, so a death costs souls", !keepSouls.get());

        Souls.setSouls(queen, 1000L);
        SoulDeathDrop.onPlayerDeath(queen);

        expect(helper, "with the rule off a death leaves a remnant", newestRemnant(helper, queen) != null);

        SoulRemnantData data = SoulRemnantData.get(server);
        SoulRemnantData.ActiveRemnant before = data.activeFor(queen.getUUID());
        keepSouls.set(true, server);

        try
        {
            Souls.setSouls(queen, 250L);
            SoulDeathDrop.onPlayerDeath(queen);

            SoulRemnantData.ActiveRemnant after = data.activeFor(queen.getUUID());

            expect(helper, "the souls of the player survive a death in such a world", Souls.getSouls(queen) == 250L);
            expect(helper, "no remnant is created for that death", countRemnants(helper, queen) == 1);
            expect(helper, "and the remnant of the earlier death is not replaced",
                    before != null && after != null && before.token().equals(after.token()));
            expect(helper, "with its souls untouched", after != null && after.souls() == 1000L);
        }
        finally
        {
            keepSouls.set(false, server);
        }

        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST L: {@code keepInventory} says nothing about souls.</b>
     *
     * <p>Items kept, souls lost: the two rules are read in two different places, and the one of the game can never
     * decide the one of this mod.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void keepingtheinventorystillcostssouls(GameTestHelper helper)
    {
        reset(helper);
        MinecraftServer server = serverOf(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_keepinventory_queen");
        GameRules.BooleanValue keepInventory = server.getGameRules().getRule(GameRules.RULE_KEEPINVENTORY);
        GameRules.BooleanValue keepSouls = server.getGameRules().getRule(SoulRules.keepSoulsRule());

        if (keepSouls == null)
        {
            reset(helper);
            helper.succeed();
            return;
        }

        keepInventory.set(true, server);
        keepSouls.set(false, server);

        try
        {
            Souls.setSouls(queen, 300L);
            SoulDeathDrop.onPlayerDeath(queen);

            expect(helper, "the souls are lost even though the items are kept", Souls.getSouls(queen) == 0L);
            expect(helper, "and they really became a remnant",
                    SoulRemnantData.get(server).activeFor(queen.getUUID()) != null);
            expect(helper, "the items rule never turned the soul rule on", !keepSouls.get());
        }
        finally
        {
            keepInventory.set(false, server);
        }

        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST M and TEST N: cleanup and reload

    /**
     * <b>TEST M: the administrator command forgets the registry and removes the remnants that are loaded.</b>
     *
     * <p>It is the real command of the mod, dispatched through the real command dispatcher of the server, not a copy
     * of its logic.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void thecleanupcommandforgetseverything(GameTestHelper helper)
    {
        reset(helper);
        MinecraftServer server = serverOf(helper);
        ServerPlayer steve = fakePlayer(helper, "soul_command_steve");
        ServerPlayer alex = fakePlayer(helper, "soul_command_alex");

        Souls.setSouls(steve, 500L);
        SoulDeathDrop.onPlayerDeath(steve);
        Souls.setSouls(alex, 700L);
        SoulDeathDrop.onPlayerDeath(alex);

        SoulRemnantData data = SoulRemnantData.get(server);

        expect(helper, "two players left two records", data.size() == 2);
        expect(helper, "and two remnants really stand in the world",
                countRemnants(helper, steve) == 1 && countRemnants(helper, alex) == 1);

        int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                "hungrycraft souls clear_remnants");

        expect(helper, "the command reported work", result > 0);
        expect(helper, "the registry is empty afterwards", data.size() == 0);
        expect(helper, "and the loaded remnants are gone",
                countRemnants(helper, steve) == 0 && countRemnants(helper, alex) == 0);

        reset(helper);
        helper.succeed();
    }

    /**
     * <b>TEST N: a remnant that was reloaded from the save file knows when it is stale.</b>
     *
     * <p>The remnant is written to a tag and read back into a fresh instance, exactly as a chunk that was unloaded
     * and loaded again would do it. Its owner, its identity and its souls survive that trip, and the moment its
     * record is gone it removes itself on its very first tick - which is what makes a remnant whose record was
     * replaced while its chunk was not loaded disappear on its own.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_foundation", timeoutTicks = TIMEOUT_TICKS)
    public static void areloadedremnantcleansitselfup(GameTestHelper helper)
    {
        reset(helper);
        MinecraftServer server = serverOf(helper);
        ServerPlayer queen = fakePlayer(helper, "soul_reload_queen");
        Souls.setSouls(queen, 1000L);
        SoulDeathDrop.onPlayerDeath(queen);

        SoulRemnantEntity loaded = newestRemnant(helper, queen);

        expect(helper, "the death left a remnant", loaded != null);

        if (loaded == null)
        {
            reset(helper);
            helper.succeed();
            return;
        }

        CompoundTag saved = loaded.saveWithoutId(new CompoundTag());
        SoulRemnantEntity reloaded = new SoulRemnantEntity(HungryCraftEntities.SOUL_REMNANT.get(), helper.getLevel());
        reloaded.load(saved);

        expect(helper, "the reloaded remnant knows its owner", queen.getUUID().equals(reloaded.ownerUUID()));
        expect(helper, "the reloaded remnant knows its own identity",
                loaded.remnantToken() != null && loaded.remnantToken().equals(reloaded.remnantToken()));
        expect(helper, "the reloaded remnant still holds the souls", reloaded.storedSouls() == 1000L);
        expect(helper, "and it is still the record of its owner while that record exists",
                reloaded.isStillTheRecordOfItsOwner());

        // The record is dropped, exactly as a later death of the same player (or an administrator) drops it.
        SoulRemnantData.get(server).remove(queen.getUUID());

        expect(helper, "the reloaded remnant is stale now", !reloaded.isStillTheRecordOfItsOwner());
        reloaded.tick();
        expect(helper, "and it removes itself on its first tick", reloaded.isRemoved());

        reset(helper);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /**
     * One remnant of a test that answers the online lookup of its owner itself.
     *
     * <p>A game test can not put a player into the player list of the server - the fake players of the mod are
     * deliberately never part of any level - so this subclass replaces that single lookup and nothing else.
     * {@code tryClaimBySpider} itself, the ownership of the spider, the transaction of the record and the payout are
     * the real code of the system.
     */
    private static final class TestRemnant extends SoulRemnantEntity
    {
        /** Who the online lookup of this remnant answers with; null means "not online at all". */
        @Nullable
        private ServerPlayer answer;

        private TestRemnant(Level level)
        {
            super(HungryCraftEntities.SOUL_REMNANT.get(), level);
        }

        @Nullable
        @Override
        protected ServerPlayer resolveOwner()
        {
            return this.answer;
        }
    }

    /** @return a remnant that reports that player as its online owner, carrying the souls of that record. */
    private static TestRemnant testRemnant(GameTestHelper helper, ServerPlayer owner,
            SoulRemnantData.ActiveRemnant record, @Nullable ServerPlayer answer)
    {
        TestRemnant remnant = new TestRemnant(helper.getLevel());

        remnant.setRemnant(owner.getUUID(), record.token(), record.souls());
        remnant.moveTo(owner.getX(), owner.getY(), owner.getZ());
        remnant.answer = answer;

        return remnant;
    }

    /** @return the server of the test world, the one a game test always has. */
    private static MinecraftServer serverOf(GameTestHelper helper)
    {
        return helper.getLevel().getServer();
    }

    /** @return one fake player of this test, deliberately never put into the level (see the other tests of the mod). */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(GROUND);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);

        return player;
    }

    /** @return one spider of the brood that belongs to that owner and really stands in the test world. */
    private static ImpSpiderEntity spider(GameTestHelper helper, @Nullable UUID owner)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(GROUND);
        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

        if (spider == null)
        {
            throw new IllegalStateException("the game refused to build an imp spider of the soul test");
        }

        spider.setOwnerUUID(owner);
        spider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(spider);

        return spider;
    }

    /**
     * Forgets every soul remnant of the server, at the start and at the end of every test of this class.
     *
     * <p>The tests of one batch run one after another, so a clean slate here is a clean slate for the next case as
     * well - and no case is ever judged on the records of another one.
     */
    private static void reset(GameTestHelper helper)
    {
        SoulCleanup.clearAll(serverOf(helper));
    }

    /** @return the newest remnant of that player that stands in the world, or null when there is none. */
    @Nullable
    private static SoulRemnantEntity newestRemnant(GameTestHelper helper, ServerPlayer owner)
    {
        List<SoulRemnantEntity> found = remnantsOf(helper, owner);

        return found.isEmpty() ? null : found.get(found.size() - 1);
    }

    /** @return how many remnants that player has in the world right now. */
    private static int countRemnants(GameTestHelper helper, ServerPlayer owner)
    {
        return remnantsOf(helper, owner).size();
    }

    private static List<SoulRemnantEntity> remnantsOf(GameTestHelper helper, ServerPlayer owner)
    {
        List<SoulRemnantEntity> found = new ArrayList<>();

        for (Entity entity : helper.getLevel().getAllEntities())
        {
            if (entity instanceof SoulRemnantEntity remnant && owner.getUUID().equals(remnant.ownerUUID()))
            {
                found.add(remnant);
            }
        }

        return found;
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("SOUL-FOUNDATION: " + what);
        }
    }
}
