package spiderqueenhungrycraft.brood;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The runtime proof of the two answers a mine can give to fire (Spider Mine and Egg Arrow / Spider Trigger follow up
 * round).
 *
 * <p>Three promises of that round are pinned down here. A spider mine is <b>blast proof</b>: an explosion of the game
 * neither takes it out of the world nor lights it. A delayed spider mine is the other way round: a blast <b>lights its
 * fuse</b> - the mine leaves the world as the primed entity of this mod, with the short fuse of a chain of TNT and
 * without coming back as an item - and it carries its placer along, because the placer is what the spiders of that fuse
 * will belong to four seconds later. And both of them can be <b>lit by a hand of fire</b>, exactly like the block of
 * TNT of the game: flint and steel (or a fire charge) fires an immediate mine where it stands, and starts the fuse of a
 * delayed one - while every other right click still does nothing at all.
 *
 * <p>Everything is driven through the entry points the game itself uses - {@code BlockState#use} for the hand of fire
 * and {@code Level#explode} for the blast - so nothing here is a private shortcut that only a test can walk.
 *
 * <p>The blast of the tests is deliberately a small one (half of a creeper). What decides whether a mine is caught by
 * an explosion is not how strong that explosion was but whether the energy left after the resistance of the block is
 * still above zero, and a small blast stays inside the test structure instead of tearing the floor of the tests around
 * this one up. The blast of a real mine is four times as strong and walks the identical path.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SpiderMineIgnitionGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty and a slow run can not hide behind a timeout. */
    private static final int TIMEOUT_TICKS = 40;

    /** The mine of a test: the middle of the floor of the test structure, standing on it. */
    private static final BlockPos MINE_POS = new BlockPos(2, 1, 2);

    /** The power of the blast of a test: half of a creeper, and enough to settle what these tests ask. */
    private static final float BLAST_POWER = 2.0F;

    /** How far around the test structure the tests look for the things they made: just over its own five blocks. */
    private static final double AREA = 4.0D;

    private SpiderMineIgnitionGameTest()
    {
    }

    /**
     * <b>A blast of the world lights the fuse of a delayed mine.</b>
     *
     * <p>It is the chain reaction the round asks for: the blast takes the block - it is as easy to blow up as TNT and
     * says so - and the mine answers with its own primed entity, with the short fuse the game gives a block of TNT that
     * another explosion lit, with the placer of the mine on it, and with no mine item to pick up afterwards.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_ignition_blast", timeoutTicks = TIMEOUT_TICKS)
    public static void theblastofanexplosionlightsafuse(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        placeMine(helper, HungryCraftBlocks.DELAYED_SPIDER_MINE.get(), queen);

        blastAt(helper, MINE_POS);

        expect(helper, "the blast really did take the delayed mine out of the world",
                helper.getBlockState(MINE_POS).isAir());

        PrimedSpiderMineEntity primed = primed(helper);

        expect(helper, "a delayed mine that a blast caught is not simply gone: its fuse is lit", primed != null);
        expect(helper, "the placer of the mine travels with the fuse a blast lit",
                primed != null && queen.getUUID().equals(primed.getMineOwner()));
        expect(helper, "a fuse lit by a blast is the short chain fuse of TNT and not the four seconds of a trigger",
                primed != null && primed.getFuse() > 0 && primed.getFuse() < PrimedSpiderMineEntity.FUSE_TICKS);

        List<ItemEntity> dropped = items(helper);

        expect(helper, "the mine that became a primed entity does not also come back as an item" + describe(dropped),
                dropped.stream().noneMatch(item -> item.getItem().is(HungryCraftBlocks.DELAYED_SPIDER_MINE.get().asItem())));

        discard(primed);
        helper.succeed();
    }

    /**
     * <b>A blast does not take the spider mine, and does not light it either.</b>
     *
     * <p>The other half of the same rule, with the very same blast at the very same spot: the mine is standing there
     * afterwards with its memory intact and no fuse of it anywhere.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_ignition_resist", timeoutTicks = TIMEOUT_TICKS)
    public static void aspiderminesurvivesablast(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        placeMine(helper, HungryCraftBlocks.SPIDER_MINE.get(), queen);

        blastAt(helper, MINE_POS);

        expect(helper, "the spider mine is blast proof: the blast that takes a delayed mine leaves this one standing",
                helper.getBlockState(MINE_POS).is(HungryCraftBlocks.SPIDER_MINE.get()));
        expect(helper, "no explosion can light the spider mine: it has nothing to light", primed(helper) == null);
        expect(helper, "the mine that survived the blast still remembers its placer",
                helper.getLevel().getBlockEntity(helper.absolutePos(MINE_POS)) instanceof SpiderMineBlockEntity mine
                        && queen.getUUID().equals(mine.getOwner()));

        helper.succeed();
    }

    /**
     * <b>A hand of fire lights the fuse of a delayed mine.</b>
     *
     * <p>Flint and steel on the block, the way the game does it on TNT: the block becomes the primed entity with the
     * ordinary four seconds of a mine, and the tool is spent by one point.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_ignition_flint", timeoutTicks = TIMEOUT_TICKS)
    public static void flintandsteellightsafuse(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        placeMine(helper, HungryCraftBlocks.DELAYED_SPIDER_MINE.get(), queen);

        ItemStack lighter = lightWithFlintAndSteel(helper, queen);

        expect(helper, "the mine of the click left the world",
                !helper.getBlockState(MINE_POS).is(HungryCraftBlocks.DELAYED_SPIDER_MINE.get()));

        PrimedSpiderMineEntity primed = primed(helper);

        expect(helper, "a hand of fire lights the fuse of a delayed mine", primed != null);
        expect(helper, "a fuse lit by a hand is the ordinary four seconds of a delayed mine",
                primed != null && primed.getFuse() == PrimedSpiderMineEntity.FUSE_TICKS);
        expect(helper, "the flint and steel of that hand was spent by one point", lighter.getDamageValue() == 1);

        discard(primed);
        helper.succeed();
    }

    /**
     * <b>A hand of fire sets an immediate mine off where it stands.</b>
     *
     * <p>The same click on the other mine: this one has no fuse at all, so the fire is not a fuse either - the block
     * leaves the world in its own blast.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_ignition_immediate", timeoutTicks = TIMEOUT_TICKS)
    public static void flintandsteelfiresatonce(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        placeMine(helper, HungryCraftBlocks.SPIDER_MINE.get(), queen);

        ItemStack lighter = lightWithFlintAndSteel(helper, queen);

        expect(helper, "a hand of fire sets a spider mine off",
                !helper.getBlockState(MINE_POS).is(HungryCraftBlocks.SPIDER_MINE.get()));
        expect(helper, "an immediate mine has no fuse to light, so no fuse is lit", primed(helper) == null);
        expect(helper, "the flint and steel of that hand was spent by one point", lighter.getDamageValue() == 1);

        discardSpiders(helper);
        helper.succeed();
    }

    /**
     * <b>Everything else right clicks a mine and nothing happens.</b>
     *
     * <p>The block has no use of its own and still has none: a hand of fire is the only click a mine answers.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_ignition_empty_hand", timeoutTicks = TIMEOUT_TICKS)
    public static void anemptyhandsetsnothingoff(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        placeMine(helper, HungryCraftBlocks.SPIDER_MINE.get(), queen);

        BlockPos pos = helper.absolutePos(MINE_POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = helper.getLevel().getBlockState(pos).use(helper.getLevel(), queen,
                InteractionHand.MAIN_HAND, hit);

        expect(helper, "an empty hand is not a hand of fire, so the click is passed on", !result.consumesAction());
        expect(helper, "the mine of that click is still standing",
                helper.getBlockState(MINE_POS).is(HungryCraftBlocks.SPIDER_MINE.get()));

        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return a fake queen of this mod, standing beside the mine, with her own uuid and nothing in her hand. */
    private static ServerPlayer placeQueen(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer queen = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "spider_mine_fire_test"));
        queen.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        queen.setGameMode(GameType.SURVIVAL);
        queen.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        // The queen is deliberately NOT put into the level, for the reason the mine command test gives: these tests run
        // in the same world as every other test of the mod, and a stray player standing in a test structure is an answer
        // every other test's AI would suddenly have to consider. Everything needed of her lives on the entity itself.
        return queen;
    }

    /** Puts one mine of the queen down and writes her down as its placer, the way placing it by hand would. */
    private static void placeMine(GameTestHelper helper, Block block, ServerPlayer queen)
    {
        helper.setBlock(MINE_POS, block);

        if (helper.getLevel().getBlockEntity(helper.absolutePos(MINE_POS)) instanceof SpiderMineBlockEntity mine)
        {
            mine.setOwner(queen.getUUID());
        }
    }

    /** One blast of the game at that spot of the structure: the kind a creeper or a block of TNT leaves behind. */
    private static void blastAt(GameTestHelper helper, BlockPos relative)
    {
        BlockPos pos = helper.absolutePos(relative);

        helper.getLevel().explode(null, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, BLAST_POWER,
                Level.ExplosionInteraction.TNT);
    }

    /**
     * One click of a hand of fire on the mine of the test: flint and steel in the main hand of the queen and the very
     * use the game calls for a right click.
     *
     * @return the stack she used, so that a test can look at what that click cost her
     */
    private static ItemStack lightWithFlintAndSteel(GameTestHelper helper, ServerPlayer queen)
    {
        ItemStack lighter = new ItemStack(Items.FLINT_AND_STEEL);
        queen.setItemInHand(InteractionHand.MAIN_HAND, lighter);

        BlockPos pos = helper.absolutePos(MINE_POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        InteractionResult result = helper.getLevel().getBlockState(pos).use(helper.getLevel(), queen,
                InteractionHand.MAIN_HAND, hit);

        expect(helper, "a mine takes the click of a hand of fire", result.consumesAction());

        return lighter;
    }

    /** @return the primed delayed mine of this test, or null while no fuse of it is lit. */
    @Nullable
    private static PrimedSpiderMineEntity primed(GameTestHelper helper)
    {
        List<PrimedSpiderMineEntity> found = helper.getLevel().getEntitiesOfClass(PrimedSpiderMineEntity.class,
                area(helper));

        return found.isEmpty() ? null : found.get(0);
    }

    /** @return every mine item lying around in this test: there should never be one. */
    private static List<ItemEntity> items(GameTestHelper helper)
    {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(helper));
    }

    /** @return the box of this test structure and a little more, and never as far as the structure beside it. */
    private static AABB area(GameTestHelper helper)
    {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(AREA);
    }

    /** @return every item lying around in this test, as words for a failure message: what a blast left behind. */
    private static String describe(List<ItemEntity> items)
    {
        if (items.isEmpty())
        {
            return "";
        }

        StringBuilder text = new StringBuilder(" (the blast dropped: ");

        for (int index = 0; index < items.size(); index++)
        {
            ItemStack stack = items.get(index).getItem();

            if (index > 0)
            {
                text.append(", ");
            }

            text.append(stack.getCount()).append(' ').append(stack.getItem());
        }

        return text.append(')').toString();
    }

    /** Takes the spiders of a mine out of the world again, which these tests share with every other test. */
    private static void discardSpiders(GameTestHelper helper)
    {
        for (ImpSpiderEntity spider : helper.getLevel().getEntitiesOfClass(ImpSpiderEntity.class, area(helper)))
        {
            spider.discard();
        }
    }

    /** Takes one of the helper entities of these tests out of the world again. */
    private static void discard(@Nullable Entity entity)
    {
        if (entity != null)
        {
            entity.discard();
        }
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("SPIDER-MINE-IGNITION: " + what);
        }
    }
}
