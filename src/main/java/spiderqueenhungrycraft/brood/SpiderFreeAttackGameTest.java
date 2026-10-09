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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.C2SBroodSelectionPacket;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;

/**
 * The runtime proof of the fourth order of the brood (free attack round): the hunt can be ordered, it attacks everything
 * that is not of the own side of the spider, and any new command takes it away again.
 *
 * <p>Three promises are pinned down here, and every one of them is asked through the entry points the game itself uses:
 * the order key goes through {@code BroodSelection.perform} (the very method the packet handler calls), the commands go
 * through {@link SpiderCommand}, and the rule of who is a victim is asked of
 * {@link SpiderMinionTargeting#isFreeAttackEnemy} directly - which is the method the target goal of a spider calls.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SpiderFreeAttackGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the spider of these tests stands: on the floor block of the test structure. */
    private static final BlockPos SPIDER_POS = new BlockPos(2, 1, 2);

    /** Where a scout move of these tests is pointed at: the far corner of the same floor. */
    private static final BlockPos DESTINATION = new BlockPos(4, 1, 4);

    private SpiderFreeAttackGameTest()
    {
    }

    /**
     * <b>The hunt is an order, and the next command takes it away.</b>
     *
     * <p>The hunt is asked for the way the order key asks for it, and then two commands are given - the attack order and
     * the scout move, the two that are not orders themselves - and each of them has to leave a follower behind.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_free_attack_order", timeoutTicks = TIMEOUT_TICKS)
    public static void thehuntcanbeorderedandistakenawaybythenextcommand(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        orderTheHunt(helper, queen);

        expect(helper, "the hunt key really puts the selected spider on the free attack order",
                spider.getOrderMode() == SpiderOrderMode.FREE_ATTACK);
        expect(helper, "a hunting spider is given the place it stands on as its hunting ground",
                spider.getOrderAnchor() != null);
        expect(helper, "a hunting spider never walks with its queen",
                spider.getOrderMode() != SpiderOrderMode.FOLLOW);

        // The attack order: a new command, and one that is not an order itself - so the hunt is over.
        ArmorStand victim = placeVictim(helper);
        SpiderCommand.attack(queen, victim);

        expect(helper, "an attack order really reached the spider", spider.getForcedTarget() == victim);
        expect(helper, "an attack order takes the hunt away from the spider it was given to",
                spider.getOrderMode() == SpiderOrderMode.FOLLOW);

        // And the scout move, the second command that is not an order of its own.
        spider.freeAttackHere();
        SpiderCommand.moveTo(queen, helper.absolutePos(DESTINATION), 1);

        expect(helper, "a scout move is accepted by a hunting spider", spider.getMoveAnchor() != null);
        expect(helper, "a scout move takes the hunt away as well", spider.getOrderMode() == SpiderOrderMode.FOLLOW);

        // The three commands that are orders of their own write that order, which is the end of the hunt as well.
        spider.freeAttackHere();
        SpiderCommand.holdHere(queen);

        expect(helper, "the stand-down key turns a hunting spider into a guard",
                spider.getOrderMode() == SpiderOrderMode.HOLD);

        spider.freeAttackHere();
        SpiderCommand.followHere(queen);

        expect(helper, "the soft rally turns a hunting spider into a follower",
                spider.getOrderMode() == SpiderOrderMode.FOLLOW);

        // Last, because the hard rally is the one command that drops the selection with it.
        spider.freeAttackHere();
        SpiderCommand.rally(queen);

        expect(helper, "the hard rally turns a hunting spider into a follower as well",
                spider.getOrderMode() == SpiderOrderMode.FOLLOW);

        discard(victim);
        discard(spider);
        helper.succeed();
    }

    /**
     * <b>The hunt spares its own side and takes everything else.</b>
     *
     * <p>One assertion per kind of creature, on the very rule the target goal of a spider asks
     * ({@link SpiderMinionTargeting#isFreeAttackEnemy}): a zombie that the faction matrix calls neutral, a villager that
     * nature never hands to a spider, the spiders of another queen and the pets of another player are victims; the queen
     * herself, the spiders of the same queen, what that queen has tamed, a creeper and the props of a queen (an armour
     * stand, her stomach core) are not - and the creeper is the one entry of that list that no faction of the game would
     * protect.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_free_attack_sides", timeoutTicks = TIMEOUT_TICKS)
    public static void thehuntsparesitsownside(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        ServerPlayer stranger = placeStranger(helper);
        ServerLevel level = helper.getLevel();

        ImpSpiderEntity spider = minion(level, queen.getUUID());
        spider.freeAttackHere();

        Zombie zombie = creature(EntityType.ZOMBIE.create(level));
        Villager villager = creature(EntityType.VILLAGER.create(level));
        Creeper creeper = creature(EntityType.CREEPER.create(level));
        Wolf pet = creature(EntityType.WOLF.create(level));
        Wolf strangerPet = creature(EntityType.WOLF.create(level));
        ArmorStand display = creature(EntityType.ARMOR_STAND.create(level));
        StomachCoreEntity core = creature(HungryCraftEntities.STOMACH_CORE.get().create(level));
        ImpSpiderEntity sibling = minion(level, queen.getUUID());
        ImpSpiderEntity strangerSpider = minion(level, stranger.getUUID());

        pet.setOwnerUUID(queen.getUUID());
        strangerPet.setOwnerUUID(stranger.getUUID());

        expect(helper, "the hunt attacks a zombie, which the faction matrix calls neutral towards the queen",
                SpiderMinionTargeting.isFreeAttackEnemy(spider, zombie));
        expect(helper, "the hunt attacks a villager, which nature never hands to a spider",
                SpiderMinionTargeting.isFreeAttackEnemy(spider, villager));
        expect(helper, "that villager is still no natural enemy of an ordinary spider",
                !SpiderMinionTargeting.isNaturalEnemy(spider, villager));
        expect(helper, "the hunt attacks the spiders of another queen",
                SpiderMinionTargeting.isFreeAttackEnemy(spider, strangerSpider));
        expect(helper, "the hunt attacks the pets of another player",
                SpiderMinionTargeting.isFreeAttackEnemy(spider, strangerPet));

        expect(helper, "the hunt never attacks the spiders of the same queen",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, sibling));
        expect(helper, "the hunt never attacks what the queen has tamed",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, pet));
        expect(helper, "the hunt never attacks a creeper",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, creeper));
        expect(helper, "the hunt never attacks the queen herself",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, queen));
        expect(helper, "the hunt never attacks a prop, such as the armour stand of the queen",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, display));
        expect(helper, "the hunt never attacks the stomach core of a queen, which no crosshair can even pick",
                !SpiderMinionTargeting.isFreeAttackEnemy(spider, core));

        // And the switch itself: the very same creatures are asked again with an ordinary order, where the zombie is
        // neutral and the villager is out of the question.
        spider.setOrderMode(SpiderOrderMode.FOLLOW);

        expect(helper, "an ordinary spider leaves the zombie alone", !SpiderMinionTargeting.isEnemy(spider, zombie));
        expect(helper, "an ordinary spider leaves the villager alone too",
                !SpiderMinionTargeting.isEnemy(spider, villager));
        expect(helper, "an ordinary spider leaves the pet of another player alone as well, because a wolf is not in any"
                + " faction at all", !SpiderMinionTargeting.isEnemy(spider, strangerPet));

        discard(zombie);
        discard(villager);
        discard(creeper);
        discard(pet);
        discard(strangerPet);
        discard(display);
        discard(core);
        discard(sibling);
        discard(strangerSpider);
        discard(spider);
        helper.succeed();
    }

    /**
     * <b>The order key walks the four orders now.</b>
     *
     * <p>One spider, four presses: follow, hold, patrol, free attack, follow. It also pins down the anchor rule of that
     * walk - the first press posts the spider where it stands, and the hunt keeps that post instead of inventing a new
     * one.
     */
    @GameTest(template = "humanspawn_flat", batch = "spider_free_attack_cycle", timeoutTicks = TIMEOUT_TICKS)
    public static void theordercyclewalksthefourorders(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);
        ImpSpiderEntity spider = placeSpider(helper, queen);
        select(queen, spider);

        expect(helper, "a fresh spider follows", spider.getOrderMode() == SpiderOrderMode.FOLLOW);

        expect(helper, "the first press of the order key posts the group",
                SpiderCommand.cycleOrder(queen) == SpiderOrderMode.HOLD);
        expect(helper, "the post of the group is the block the spider stands on", spider.getOrderAnchor() != null);

        expect(helper, "the second press makes the group patrol",
                SpiderCommand.cycleOrder(queen) == SpiderOrderMode.PATROL);

        expect(helper, "the third press switches the hunt on",
                SpiderCommand.cycleOrder(queen) == SpiderOrderMode.FREE_ATTACK);
        expect(helper, "the hunt keeps the post the group already had", spider.getOrderAnchor() != null);

        expect(helper, "the fourth press brings the group back to following",
                SpiderCommand.cycleOrder(queen) == SpiderOrderMode.FOLLOW);
        expect(helper, "a follower has no post any more", spider.getOrderAnchor() == null);

        discard(spider);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return a fake queen of this mod with her sceptre in hand: every command of these tests needs one. */
    private static ServerPlayer placeQueen(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "spider_free_attack_test");
        queen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HungryCraftItems.QUEEN_SCEPTER.get()));

        return queen;
    }

    /** @return a second player, who owns nothing of the queen and is therefore another colony entirely. */
    private static ServerPlayer placeStranger(GameTestHelper helper)
    {
        return fakePlayer(helper, "spider_free_attack_stranger");
    }

    /**
     * @return one fake player of this test.
     *
     * <p>Like the mine command test, the players are deliberately <b>not</b> put into the level: these tests run in the
     * same world as every other test of the mod, and a stray player standing in a test structure is an answer every
     * other test's AI would suddenly have to consider. Everything needed of them lives on the entity itself.
     */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);

        return player;
    }

    /** @return one spider of that queen, standing on the floor of the test structure and really in the world. */
    private static ImpSpiderEntity placeSpider(GameTestHelper helper, ServerPlayer queen)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(SPIDER_POS);
        ImpSpiderEntity spider = minion(level, queen.getUUID());

        spider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(spider);

        return spider;
    }

    /** @return one spider of that owner that is never put into the world: the rules of these tests only need it to exist. */
    private static ImpSpiderEntity minion(ServerLevel level, @Nullable UUID owner)
    {
        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

        if (spider == null)
        {
            throw new IllegalStateException("the game refused to build the imp spider of the free attack test");
        }

        spider.setOwnerUUID(owner);

        return spider;
    }

    /** @return one creature of this test, refused loudly when the game will not build it. */
    private static <T extends LivingEntity> T creature(@Nullable T built)
    {
        if (built == null)
        {
            throw new IllegalStateException("the game refused to build a creature of the free attack test");
        }

        return built;
    }

    /** @return the victim of the attack order test: an armour stand, which belongs to no colony at all. */
    private static ArmorStand placeVictim(GameTestHelper helper)
    {
        ArmorStand victim = creature(EntityType.ARMOR_STAND.create(helper.getLevel()));
        BlockPos pos = helper.absolutePos(DESTINATION);
        victim.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);

        return victim;
    }

    /** The selection a queen really commands: written into her queen data, exactly as a box selection would write it. */
    private static void select(ServerPlayer queen, SpiderMinionEntity spider)
    {
        QueenDataCapability.get(queen)
                .ifPresent(data -> data.broodSelection().selectOnly(List.of(spider.getUUID())));
    }

    /**
     * One press of the hunt key, through the very method the packet handler calls: the key only ever asks, and the
     * server answers with {@link BroodSelection}.
     */
    private static void orderTheHunt(GameTestHelper helper, ServerPlayer queen)
    {
        BroodSelection.perform(queen,
                new C2SBroodSelectionPacket(C2SBroodSelectionPacket.Action.FREE_ATTACK, new int[0]));
    }

    /** Takes one of the helper entities of these tests out of the world again, which it shares with every other test. */
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
            helper.fail("SPIDER-FREE-ATTACK: " + what);
        }
    }
}
