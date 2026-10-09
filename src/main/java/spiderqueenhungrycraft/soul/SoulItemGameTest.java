package spiderqueenhungrycraft.soul;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The proof of the seven lumps of soul (Soul Consumable round): they exist, they are worth exactly what they say, a
 * right click pays and costs one item, the creative hand keeps its item, and no recipe of the game can make one.
 *
 * <p>Every promise is asked through the very entry point a player uses - {@code ItemStack#use} on a real item of a real
 * player in a real level - so nothing here imitates the item, and the worth is read from the registration and never
 * from a second list that could drift away from it.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SoulItemGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the players of these tests stand: on the floor block of the test structure. */
    private static final BlockPos GROUND = new BlockPos(2, 1, 2);

    /** The worth of the seven souls, in the order they are registered and drawn in: low to high. */
    private static final long[] WORTH = { 100L, 150L, 250L, 500L, 800L, 1000L, 3000L };

    private SoulItemGameTest()
    {
    }

    /**
     * <b>TEST A and TEST B: the seven souls exist and are worth what they say.</b>
     *
     * <p>The same list the creative tab draws is walked here, so a soul that is added, removed or re-priced can never
     * pass this test while the interface shows something else.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_items", timeoutTicks = TIMEOUT_TICKS)
    public static void thesevensoulsexistandareworthwhattheysay(GameTestHelper helper)
    {
        List<RegistryObject<Item>> souls = HungryCraftItems.soulsInWorthOrder();

        expect(helper, "there are seven souls", souls.size() == WORTH.length);

        for (int index = 0; index < souls.size(); index++)
        {
            ItemStack stack = new ItemStack(souls.get(index).get());

            expect(helper, "soul " + index + " is an item of the game", !stack.isEmpty());
            expect(helper, "soul " + index + " is registered under its own name",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(SpiderQueenHungryCraft.MODID));
            expect(helper, "soul " + index + " is a soul item", stack.getItem() instanceof SoulItem);
            expect(helper, "soul " + index + " is worth " + WORTH[index] + " souls",
                    ((SoulItem) stack.getItem()).soulValue() == WORTH[index]);

            // Sixty four is the stack the round asks for: a chest may hold up to four, and a player may gather more.
            expect(helper, "soul " + index + " stacks to sixty four", stack.getMaxStackSize() == 64);
        }

        helper.succeed();
    }

    /**
     * <b>TEST C and TEST D: one right click pays the worth and costs exactly one piece.</b>
     *
     * <p>A stack of sixty four is used twice: the balance grows by the worth twice, and the stack loses one piece each
     * time - never a stack, never nothing.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_items", timeoutTicks = TIMEOUT_TICKS)
    public static void oneclickpaysandtakesonepiece(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "soul_item_survival");
        ItemStack stack = new ItemStack(HungryCraftItems.HERO_SOUL.get(), 64);
        long worth = ((SoulItem) stack.getItem()).soulValue();

        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Souls.setSouls(player, 0L);

        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        expect(helper, "one click pays the worth of a hero soul", Souls.getSouls(player) == worth);
        expect(helper, "one click costs one piece of a stack of sixty four", stack.getCount() == 63);

        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        expect(helper, "the second click pays again", Souls.getSouls(player) == worth * 2L);
        expect(helper, "the second click costs one piece again", stack.getCount() == 62);

        helper.succeed();
    }

    /**
     * <b>TEST E: the creative hand keeps its soul.</b>
     *
     * <p>Like every consumable of the game, a soul used in creative pays and never shrinks - the click still counts,
     * the item stays.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_items", timeoutTicks = TIMEOUT_TICKS)
    public static void thecreativehandkeepsitssoul(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "soul_item_creative");
        ItemStack stack = new ItemStack(HungryCraftItems.TYRANT_SOUL.get(), 5);
        long worth = ((SoulItem) stack.getItem()).soulValue();

        // The game mode and not the ability flag: a server player answers isCreative() from its game mode, which is
        // also how the game itself hands a creative hand its free items.
        player.setGameMode(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Souls.setSouls(player, 0L);

        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        expect(helper, "a creative click still pays", Souls.getSouls(player) == worth);
        expect(helper, "a creative click keeps every piece", stack.getCount() == 5);

        helper.succeed();
    }

    /**
     * <b>TEST F: no recipe of the game makes a soul.</b>
     *
     * <p>The seven souls are meant to be found - in a chest, never at a crafting table, a furnace, an anvil, a
     * stonecutter or a smithing table. Every recipe of the loaded packs is walked, so a recipe that another mod or a
     * datapack adds is covered as well.
     */
    @GameTest(template = "humanspawn_flat", batch = "soul_items", timeoutTicks = TIMEOUT_TICKS)
    public static void norecipeevermakesasoul(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();

        for (Recipe<?> recipe : level.getRecipeManager().getRecipes())
        {
            Item result = recipe.getResultItem(level.registryAccess()).getItem();

            expect(helper, "no recipe makes " + BuiltInRegistries.ITEM.getKey(result),
                    !(result instanceof SoulItem));
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------ the helpers of this class

    /** @return one fake player of this test, deliberately never put into the level (see the other tests of the mod). */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(GROUND);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);

        return player;
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
