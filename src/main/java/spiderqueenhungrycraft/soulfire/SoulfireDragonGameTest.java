package spiderqueenhungrycraft.soulfire;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of round 7C: the soul fire dragon is one unit, it is commanded like a spider of the brood, and the
 * newest command always wins (written after a load failure that only a real run could have caught).
 *
 * <p>Everything here is asked through the very entry points the sceptre uses ({@link SoulfireOrders}), so a test can
 * never pass on a path the game does not take. None of it needs Ice and Fire: the unit is pure vanilla, and only its
 * renderer is optional - which is exactly why these tests can run in a bare workspace.
 *
 * <p>It only runs in a game test run ({@code runGameTestServer} or {@code /test}).
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SoulfireDragonGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the unit of these tests stands: on the floor block of the test structure. */
    private static final BlockPos DRAGON_POS = new BlockPos(2, 1, 2);

    /** Where a scout move of these tests points: the far corner of the same floor. */
    private static final BlockPos DESTINATION = new BlockPos(6, 1, 6);

    private SoulfireDragonGameTest()
    {
    }

    private static void expect(GameTestHelper helper, String what, boolean ok)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }

    /** One unit, one registry entry, and a vanilla parent: the claim the whole round rests on. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void theunitofthisroundisregisteredandhasavanillaparent(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = HungryCraftEntities.SOULFIRE_DRAGON.get().create(helper.getLevel());

        expect(helper, "the entity type of the soul fire dragon exists and can be built", dragon != null);
        expect(helper, "and it is registered under the id of round 7C",
                EntityType.getKey(HungryCraftEntities.SOULFIRE_DRAGON.get()).toString()
                        .equals("spiderqueen_hungrycraft:soulfire_dragon"));
        expect(helper, "its parent is the vanilla tamable animal, so it owes nothing to the other mod",
                dragon.getClass().getSuperclass() == TamableAnimal.class);
        expect(helper, "and it starts as a follower of its queen without a contract",
                dragon.getOrderMode() == SpiderOrderMode.FOLLOW && !dragon.hasContract());
        helper.succeed();
    }

    /** The rule the round asks for by name: a new command is obeyed at once and the old one is gone. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void anewcommanddropstheonebeforeit(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        int first = dragon.getCommandVersion();

        dragon.setMoveTo(DESTINATION, 7);
        int second = dragon.getCommandVersion();

        expect(helper, "the scout move is written on the unit", dragon.getMoveTarget() != null);
        expect(helper, "the move order is the newest command", second > first);

        dragon.breatheAt(DESTINATION);

        expect(helper, "the skill of the ability key replaces the errand", dragon.getMoveTarget() == null);
        expect(helper, "and the unit carries a breath order now", dragon.hasBreathOrder());
        expect(helper, "which is a newer command again", dragon.getCommandVersion() > second);

        dragon.setMoveTo(DESTINATION, 8);

        expect(helper, "and a later move stops the breath again", !dragon.hasBreathOrder());
        helper.succeed();
    }

    /** The orders of the sceptre, which are the orders of the brood: follow, hold, patrol, free attack, follow. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void theorderkeywalkswiththesamefourordersasthebrood(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);

        dragon.cycleOrder();
        expect(helper, "a follower steps to hold", dragon.getOrderMode() == SpiderOrderMode.HOLD);
        expect(helper, "and takes the block it stands on as its post", dragon.getAnchor() != null);

        dragon.cycleOrder();
        expect(helper, "hold steps to patrol", dragon.getOrderMode() == SpiderOrderMode.PATROL);
        expect(helper, "which keeps the post", dragon.getAnchor() != null);

        dragon.cycleOrder();
        expect(helper, "patrol steps to the hunt", dragon.getOrderMode() == SpiderOrderMode.FREE_ATTACK);

        dragon.cycleOrder();
        expect(helper, "and the hunt steps back to follow",
                dragon.getOrderMode() == SpiderOrderMode.FOLLOW && dragon.getAnchor() == null);
        helper.succeed();
    }

    /** A friend is never a victim, and a right click does nothing at all (round: no riding, no grabbing). */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void afriendofthequeenisfusedandtherightclickisdeaf(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        SoulfireDragonEntity other = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), new BlockPos(3, 1, 3));

        queen.moveTo(dragon.getX(), dragon.getY(), dragon.getZ());
        dragon.beginContract(queen.getUUID(), helper.getLevel().getGameTime() + 12_000);

        // The second unit belongs to the very same queen, which is what makes the two of them friends: a soul fire
        // dragon without a contract is nobody's friend, and this test asks about the rule of the brood, not about a
        // wild unit.
        other.beginContract(queen.getUUID(), helper.getLevel().getGameTime() + 12_000);

        expect(helper, "the queen is a friend of her own unit", SoulfireTargeting.isFriendOf(dragon, queen));
        expect(helper, "and so is another unit of the same queen", SoulfireTargeting.isFriendOf(dragon, other));
        expect(helper, "the order to attack the queen is refused", !dragon.setForcedTarget(queen));
        expect(helper, "the order to attack the other unit is refused", !dragon.setForcedTarget(other));
        expect(helper, "and neither of them was written as a victim", dragon.getForcedTarget() == null);

        InteractionResult clicked = dragon.mobInteract(queen, InteractionHand.MAIN_HAND);

        expect(helper, "a right click on the unit is passed on and means nothing", clicked == InteractionResult.PASS);

        // Nothing of what a right click could do happened: no rider got on, the unit kept its queen, and the click did
        // not touch its standing order. (Whether it moved is deliberately not asked: a unit under contract is a
        // follower, so walking towards its queen is the one thing it is supposed to do.)
        expect(helper, "and nobody climbed onto it, because it has no seat", dragon.getPassengers().isEmpty());
        expect(helper, "and it still answers to the queen who raised it", dragon.isOwnedBy(queen));
        helper.succeed();
    }

    /**
     * The rule the skill follows before it chooses where to spit: the column over the point decides.
     *
     * <p>This is the half of the skill that can be proven without a client: whether the {@value
     * SoulfireDragonEntity#SKY_HEIGHT} blocks over a point are open air, which is what makes the unit hover over it,
     * or covered, which makes it come down and stand beside it instead.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void theskillhoversonlywhentheskyoverthepointisopen(GameTestHelper helper)
    {
        BlockPos point = helper.absolutePos(new BlockPos(4, 2, 4));

        // A clear column first, whatever the test structure brought with it.
        for (int up = 1; up <= SoulfireDragonEntity.SKY_HEIGHT; up++)
        {
            helper.getLevel().setBlockAndUpdate(point.above(up), Blocks.AIR.defaultBlockState());
        }

        expect(helper, "with an open sky the unit flies over the point and hovers",
                SoulfireOrders.skyOpen(helper.getLevel(), point));

        helper.getLevel().setBlockAndUpdate(point.above(SoulfireDragonEntity.SKY_HEIGHT),
                Blocks.STONE.defaultBlockState());

        expect(helper, "with one block on the top of that column it comes down and stands instead",
                !SoulfireOrders.skyOpen(helper.getLevel(), point));

        helper.getLevel().setBlockAndUpdate(point.above(SoulfireDragonEntity.SKY_HEIGHT),
                Blocks.AIR.defaultBlockState());
        helper.succeed();
    }
}
