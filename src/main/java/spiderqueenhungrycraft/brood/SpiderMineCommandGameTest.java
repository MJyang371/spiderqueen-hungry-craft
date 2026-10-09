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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.C2SQueenScepterUsePacket;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The runtime proof of the mine command of the sceptre (Spider Trigger round).
 *
 * <p>Every test here is synchronous: it puts a fake queen on the floor of the test structure, gives her a sceptre, puts
 * one of her own spiders into her selection, and then calls the very method the network handler calls for a click
 * ({@link QueenScepterCommands#use}). Nothing is stubbed, nothing is reached into: the same ray is cast, the same
 * selection is resolved and the same commands are issued as in the game.
 *
 * <p>What it proves is the one promise of this round that can be broken silently: <b>a click that is not near a mine
 * must command exactly what it commanded before this round existed</b>. Two tests walk the two commands a click can
 * be - the scout move of a block and the attack order of a creature - with no mine anywhere near, and both of them
 * assert that the spider really got its order. The other three tests pin the new rule down from its other side: a mine
 * inside the ball of a click takes the click, the mine that fires is the nearest one, and a click that <b>started</b>
 * elsewhere is not a mine command at all even when it ends right on top of one.
 *
 * <p>The delayed mine is used for all of them, and for a reason: it can be lit and then thrown away again, so these
 * tests can look at the whole path of the command without a blast rearranging the structure underneath them.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SpiderMineCommandGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty and a slow run can not hide behind a timeout. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the fake queen stands: on the floor block, with air above it. */
    private static final BlockPos QUEEN_POS = new BlockPos(2, 1, 2);

    /** Where her one spider stands: one block to the side of every ray this class casts. */
    private static final BlockPos SPIDER_POS = new BlockPos(3, 1, 2);

    /** The floor block the queen aims at when she looks straight down. */
    private static final BlockPos FLOOR_POS = new BlockPos(2, 0, 2);

    /** The floor block one step to the south, which is where a mine that is not aimed at directly is placed. */
    private static final BlockPos SIDE_POS = new BlockPos(2, 0, 3);

    /** Where the victim of the attack test stands: straight ahead of a queen who looks south. */
    private static final BlockPos VICTIM_POS = new BlockPos(2, 1, 3);

    private SpiderMineCommandGameTest()
    {
    }

    /**
     * <b>No mine, a block under the crosshair: the spider marches.</b>
     *
     * <p>It is the plain promise of the round: the mine command may never eat a scout move it has nothing to do with.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_command_march", timeoutTicks = TIMEOUT_TICKS)
    public static void scepterclickwithoutaminestillmarches(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, 0.0F, 90.0F);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        click(queen, QueenCommandRay.cursorPoint(queen));

        expect(helper, "a click on a block without any mine still marches the selected spider",
                spider.getMoveAnchor() != null);

        discard(spider);
        helper.succeed();
    }

    /**
     * <b>No mine, a creature under the crosshair: the spider is sent at it.</b>
     *
     * <p>The second command a click can be, and the second one the mine command must leave alone.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_command_attack", timeoutTicks = TIMEOUT_TICKS)
    public static void scepterclickwithoutaminestillattacks(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, 0.0F, 0.0F);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        ArmorStand victim = placeVictim(helper);
        click(queen, QueenCommandRay.cursorPoint(queen));

        expect(helper, "the ray of the queen really met the victim", victim != null);
        expect(helper, "a click on a creature without any mine still orders the attack",
                victim != null && spider.getForcedTarget() == victim);

        discard(victim);
        discard(spider);
        helper.succeed();
    }

    /**
     * <b>A mine inside the ball of the click: the click fires it and means nothing else.</b>
     *
     * <p>The mine is the floor block the queen is aiming at, so both ends of the click live inside it. What is asserted
     * is the whole promise of the round in one place: the block left the world, its fuse is lit, and the spider got
     * <b>no</b> order - the mine command took the march that click would otherwise have been.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_command_fire", timeoutTicks = TIMEOUT_TICKS)
    public static void scepterclickonaminefiresitandcommandsnothing(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, 0.0F, 90.0F);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        helper.setBlock(FLOOR_POS, HungryCraftBlocks.DELAYED_SPIDER_MINE.get());
        click(queen, QueenCommandRay.cursorPoint(queen));

        PrimedSpiderMineEntity primed = primed(helper);

        expect(helper, "the mine of the click left the world",
                !(helper.getBlockState(FLOOR_POS).getBlock() instanceof DelayedSpiderMineBlock));
        expect(helper, "the fuse of the mine is lit", primed != null);
        expect(helper, "the mine command took the march order of that click", spider.getMoveAnchor() == null);

        discard(spider);
        discard(primed);
        helper.succeed();
    }

    /**
     * <b>A click that started somewhere else is not a mine command, even when it ends right on top of one.</b>
     *
     * <p>The press point of this click is three blocks away from the mine, so the mine does not answer it and the click
     * stays the scout move it would always have been - the mine is still standing afterwards.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_command_press", timeoutTicks = TIMEOUT_TICKS)
    public static void scepterclickthatstartedelsewhereisnotamine(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, 0.0F, 90.0F);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        helper.setBlock(SIDE_POS, HungryCraftBlocks.DELAYED_SPIDER_MINE.get());

        // The press of this click happened far away from the mine, while the release aims right next to it.
        Vec3 press = queen.getEyePosition().add(0.0D, -3.0D, -6.0D);
        click(queen, press);

        expect(helper, "a click that started outside the mine still marches the spider",
                spider.getMoveAnchor() != null);
        expect(helper, "the mine of that click was not fired",
                helper.getBlockState(SIDE_POS).getBlock() instanceof DelayedSpiderMineBlock);

        discard(spider);
        helper.succeed();
    }

    /**
     * <b>Two mines in range: the nearest one fires.</b>
     *
     * <p>The tie breaker of the rule cannot be observed without two mines at exactly the same distance, so this test
     * only pins down the part that always has one answer: the mine closest to the point of the release goes off, and the
     * one further away keeps standing.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_mine_command_nearest", timeoutTicks = TIMEOUT_TICKS)
    public static void scepterclickfiresonlythenearestmine(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, 0.0F, 90.0F);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        helper.setBlock(FLOOR_POS, HungryCraftBlocks.DELAYED_SPIDER_MINE.get());
        helper.setBlock(SIDE_POS, HungryCraftBlocks.DELAYED_SPIDER_MINE.get());

        click(queen, QueenCommandRay.cursorPoint(queen));

        PrimedSpiderMineEntity primed = primed(helper);

        expect(helper, "the nearest mine of the click is the one whose fuse is lit", primed != null);
        expect(helper, "the mine that was further away kept standing",
                helper.getBlockState(SIDE_POS).getBlock() instanceof DelayedSpiderMineBlock);

        discard(spider);
        discard(primed);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return a fake queen of this mod, standing on the floor, sceptre in hand and looking that way. */
    private static ServerPlayer placeQueen(GameTestHelper helper, float yaw, float pitch)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(QUEEN_POS);
        ServerPlayer queen = FakePlayerFactory.get(level,
                new GameProfile(UUID.randomUUID(), "spider_mine_command_test"));
        queen.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, yaw, pitch);
        queen.setGameMode(GameType.SURVIVAL);
        queen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HungryCraftItems.QUEEN_SCEPTER.get()));

        // The queen is deliberately NOT put into the level. These tests run in the same world as every other test of
        // the mod, and a stray player standing in a test structure is an answer every other test's AI would suddenly
        // have to consider - the mine command test has no business changing what a human or a sweatlord targets.
        // Everything it needs of her lives on the entity itself: her eyes, her aim, her uuid, her queen data.
        return queen;
    }

    /** @return one imp spider of that queen, standing beside her and out of every ray these tests cast. */
    private static ImpSpiderEntity placeSpider(GameTestHelper helper, ServerPlayer queen)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(SPIDER_POS);
        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

        if (spider == null)
        {
            throw new IllegalStateException("the game refused to build the imp spider of the mine command test");
        }

        spider.setOwnerUUID(queen.getUUID());
        spider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(spider);

        return spider;
    }

    /**
     * @return the victim of the attack test: an armour stand straight ahead of a queen who looks south.
     *
     * <p>An armour stand and not a zombie, for the same reason the queen is not put into the world: this test only
     * needs a creature the sceptre may be ordered at, and a quiet one that no other test has an opinion about is the
     * one that can not change what else happens in the shared world of a game test run.
     */
    @Nullable
    private static ArmorStand placeVictim(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(VICTIM_POS);
        ArmorStand victim = EntityType.ARMOR_STAND.create(level);

        if (victim == null)
        {
            return null;
        }

        victim.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(victim);

        return victim;
    }

    /** The selection a queen really commands: written into her queen data, exactly as a box selection would write it. */
    private static void select(ServerPlayer queen, SpiderMinionEntity spider)
    {
        QueenDataCapability.get(queen)
                .ifPresent(data -> data.broodSelection().selectOnly(List.of(spider.getUUID())));
    }

    /**
     * One click of the sceptre, with the press point of the gesture it is meant to be.
     *
     * <p>It calls the very method the network handler calls, so the ray, the mine search and every command behind them
     * are the ones of the game.
     */
    private static void click(ServerPlayer queen, @Nullable Vec3 pressPoint)
    {
        QueenScepterCommands.use(queen, InteractionHand.MAIN_HAND, false,
                C2SQueenScepterUsePacket.Action.CLICK, pressPoint);
    }

    /** @return the primed delayed mine inside the test area, or null while no fuse is lit. */
    @Nullable
    private static PrimedSpiderMineEntity primed(GameTestHelper helper)
    {
        List<PrimedSpiderMineEntity> found = helper.getLevel().getEntitiesOfClass(PrimedSpiderMineEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16.0D));

        return found.isEmpty() ? null : found.get(0);
    }

    /** Takes one of the helper entities of these tests out of the world again, which they share with every other test. */
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
            helper.fail("SPIDER-MINE-COMMAND: " + what);
        }
    }
}
