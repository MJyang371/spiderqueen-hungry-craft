package spiderqueenhungrycraft.brood;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of the brood feeding rule: a piece of <b>meat</b> in the hand of the spider's own queen is
 * health for the spider, one bite costs exactly one piece of the stack, and the health it gives is exactly the hunger
 * that food would have fed to a player.
 *
 * <p>Every promise is asked through the very entry point the game uses, {@link SpiderMinionEntity#mobInteract}, with a
 * real item stack in the hand of a real (fake) player: nothing here reimplements the rule or reads a private field.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class BroodFeedingGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** The hunger of rotten flesh, and therefore the health one bite of it gives. */
    private static final int ROTTEN_FLESH_NUTRITION = 4;

    /** The hunger of a cooked cut of beef, and therefore the health one bite of it gives. */
    private static final int COOKED_BEEF_NUTRITION = 8;

    private BroodFeedingGameTest()
    {
    }

    /**
     * <b>The queen feeds her own spider with meat, and the meal costs one piece.</b>
     *
     * <p>Rotten flesh (the meat the request names by hand) and a cooked cut of beef: each heals by exactly its own
     * hunger, one piece of the stack leaves the hand per bite, and the same stack can be emptied bite by bite.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void thequeenfeedsherownspiderwithmeat(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_queen");
        ImpSpiderEntity imp = spider(helper, HungryCraftEntities.IMP_SPIDER, queen.getUUID());

        // Half of the ten health of an imp, so four points of healing can never reach the ceiling and be lost.
        imp.setHealth(5.0F);

        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 2);
        queen.setItemInHand(InteractionHand.MAIN_HAND, flesh);

        InteractionResult result = imp.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "feeding is an interaction the game accepts", result.consumesAction());
        expect(helper, "rotten flesh heals the four hunger it would have fed a player", imp.getHealth() == 9.0F);
        expect(helper, "a bite costs exactly one piece of the stack", flesh.getCount() == 1);

        // The same hand, the same stack, one bite later: the rule holds again and the last piece is gone.
        imp.setHealth(5.0F);
        imp.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "the next bite repeats the same rule", imp.getHealth() == 9.0F);
        expect(helper, "the last piece of the stack leaves the hand", flesh.isEmpty());

        // A cooked cut is food of the brood as well, with the hunger of the cut itself.
        BroodSpiderEntity brood = spider(helper, HungryCraftEntities.BROOD_SPIDER, queen.getUUID());
        brood.setHealth(5.0F);

        ItemStack steak = new ItemStack(Items.COOKED_BEEF, 1);
        queen.setItemInHand(InteractionHand.MAIN_HAND, steak);
        brood.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "a cooked cut heals its own hunger (eight)", brood.getHealth() == 13.0F);
        expect(helper, "the cooked cut is spent as well", steak.isEmpty());

        helper.succeed();
    }

    /**
     * <b>Only the real owner may feed.</b>
     *
     * <p>A stranger does not heal the spider of somebody else and does not spend one piece on it - not even a stranger
     * in <b>creative</b> mode, whose carrying right is deliberately wider than the right to feed. And a spider that
     * belongs to nobody at all is fed by nobody either.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void onlytherealownermayfeed(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_owner");
        ServerPlayer stranger = fakePlayer(helper, "brood_feeding_stranger");
        stranger.setGameMode(GameType.CREATIVE);

        ImpSpiderEntity spider = spider(helper, HungryCraftEntities.IMP_SPIDER, queen.getUUID());
        spider.setHealth(5.0F);

        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 3);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, flesh);

        spider.mobInteract(stranger, InteractionHand.MAIN_HAND);

        expect(helper, "a stranger never heals a spider that is not theirs, not even in creative",
                spider.getHealth() == 5.0F);
        expect(helper, "a stranger never spends a piece of meat on somebody else's spider",
                flesh.getCount() == 3);

        // The same hand, but the spider has no queen at all: there is nobody it would eat from.
        ImpSpiderEntity ownerless = spider(helper, HungryCraftEntities.IMP_SPIDER, null);
        ownerless.setHealth(5.0F);
        queen.setItemInHand(InteractionHand.MAIN_HAND, flesh);

        ownerless.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "a spider without a queen is fed by nobody", ownerless.getHealth() == 5.0F);
        expect(helper, "and nobody spends food on it either", flesh.getCount() == 3);

        helper.succeed();
    }

    /**
     * <b>A spider that is not hurt eats nothing and keeps the food.</b>
     *
     * <p>The meal would be wasted, so the hand of the queen is spared: the health does not change and the piece stays
     * where it is.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void afullspiderkeepsthefood(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_full_queen");
        ImpSpiderEntity spider = spider(helper, HungryCraftEntities.IMP_SPIDER, queen.getUUID());
        spider.setHealth(spider.getMaxHealth());

        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 1);
        queen.setItemInHand(InteractionHand.MAIN_HAND, flesh);

        spider.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "an unhurt spider heals nothing", spider.getHealth() == spider.getMaxHealth());
        expect(helper, "an unhurt spider keeps the piece of its queen", flesh.getCount() == 1);

        helper.succeed();
    }

    /**
     * <b>Only meat is food of the brood.</b>
     *
     * <p>The rule is the food of the game (see {@link BroodFood}): bread is food, but it is not meat, so it is neither
     * eaten nor paid for, and a stone is not food at all.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void nonmeatisnotfoodofthebrood(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_bread_queen");
        ImpSpiderEntity spider = spider(helper, HungryCraftEntities.IMP_SPIDER, queen.getUUID());
        spider.setHealth(5.0F);

        ItemStack bread = new ItemStack(Items.BREAD, 2);
        queen.setItemInHand(InteractionHand.MAIN_HAND, bread);

        spider.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "bread heals nothing", spider.getHealth() == 5.0F);
        expect(helper, "bread is not spent", bread.getCount() == 2);

        // The numbers themselves, read through the very rule the interaction asks.
        expect(helper, "rotten flesh is read as the four hunger of the game",
                BroodFood.nutritionOf(new ItemStack(Items.ROTTEN_FLESH), spider) == ROTTEN_FLESH_NUTRITION);
        expect(helper, "a cooked cut is read as its own eight hunger",
                BroodFood.nutritionOf(new ItemStack(Items.COOKED_BEEF), spider) == COOKED_BEEF_NUTRITION);
        expect(helper, "bread is food, but not meat, and therefore not food of the brood",
                BroodFood.nutritionOf(new ItemStack(Items.BREAD), spider) == 0);
        expect(helper, "a stone is not food at all",
                BroodFood.nutritionOf(new ItemStack(Items.STONE), spider) == 0);
        expect(helper, "an empty hand is not food either",
                BroodFood.nutritionOf(ItemStack.EMPTY, spider) == 0);

        helper.succeed();
    }

    /**
     * <b>The bad brother is fed by the very same rule.</b>
     *
     * <p>The infested Steve is not a spider by shape, but it is one of the brood, so the meat of its queen is health
     * for it exactly like for an imp - the base class carries the rule, not a list of species.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void thebadbrotheriseedbythesamerule(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_steve_queen");
        InfestedSteveEntity steve = spider(helper, HungryCraftEntities.INFESTED_STEVE, queen.getUUID());
        steve.setHealth(5.0F);

        ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 1);
        queen.setItemInHand(InteractionHand.MAIN_HAND, flesh);

        steve.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "the bad brother heals by the hunger of the meat", steve.getHealth() == 9.0F);
        expect(helper, "and the piece is spent", flesh.isEmpty());

        helper.succeed();
    }

    /**
     * <b>Every species of the brood knows the rule.</b>
     *
     * <p>One bite per registered spider of the queen, asked of the entity types themselves: a species a later round
     * adds to the brood inherits the base class and is covered by this test the moment it is registered here.
     */
    @GameTest(template = "humanspawn_flat", batch = "brood_feeding", timeoutTicks = TIMEOUT_TICKS)
    public static void everyspeciesofthebroodknowstherule(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "brood_feeding_all_queen");

        List<EntityType<? extends SpiderMinionEntity>> species = List.of(
                HungryCraftEntities.IMP_SPIDER.get(),
                HungryCraftEntities.BROOD_SPIDER.get(),
                HungryCraftEntities.ZOMBIE_SPIDER.get(),
                HungryCraftEntities.SKELETON_SPIDER.get(),
                HungryCraftEntities.CHRONO_SPIDER.get(),
                HungryCraftEntities.STRAY_SPIDER.get(),
                HungryCraftEntities.HUSK_SPIDER.get(),
                HungryCraftEntities.DROWNED_SPIDER.get(),
                HungryCraftEntities.CREEPER_SPIDER.get(),
                HungryCraftEntities.INFESTED_STEVE.get());

        for (EntityType<? extends SpiderMinionEntity> type : species)
        {
            SpiderMinionEntity spider = build(type, queen.getUUID(), helper);
            spider.setHealth(5.0F);

            ItemStack flesh = new ItemStack(Items.ROTTEN_FLESH, 1);
            queen.setItemInHand(InteractionHand.MAIN_HAND, flesh);

            spider.mobInteract(queen, InteractionHand.MAIN_HAND);

            expect(helper, EntityType.getKey(type) + " heals by the hunger of the meat",
                    spider.getHealth() == 9.0F);
            expect(helper, EntityType.getKey(type) + " pays one piece for that meal", flesh.isEmpty());
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return one fake player of this test, deliberately never put into the level (see the other brood tests). */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);

        return player;
    }

    /** @return one spider of that species and queen, refused loudly when the game will not build it. */
    private static <T extends SpiderMinionEntity> T spider(GameTestHelper helper,
            net.minecraftforge.registries.RegistryObject<EntityType<T>> type, @Nullable UUID owner)
    {
        return build(type.get(), owner, helper);
    }

    /** @return one spider of that type and owner, refused loudly when the game will not build it. */
    private static <T extends SpiderMinionEntity> T build(EntityType<T> type, @Nullable UUID owner,
            GameTestHelper helper)
    {
        T spider = type.create(helper.getLevel());

        if (spider == null)
        {
            throw new IllegalStateException("the game refused to build " + EntityType.getKey(type));
        }

        spider.setOwnerUUID(owner);

        return spider;
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("BROOD-FEEDING: " + what);
        }
    }
}
