package spiderqueenhungrycraft.compat.iceandfire;

import java.util.List;
import java.util.UUID;

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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSelection;
import spiderqueenhungrycraft.brood.ImpSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract.Order;
import spiderqueenhungrycraft.compat.iceandfire.ai.NecromancyTargetGoal;
import spiderqueenhungrycraft.faction.QueenOwnerProtection;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The half of the Necromancy IV connection that needs no Ice and Fire at all: the contract as the unit of a queen's
 * sceptre (Necromancy IV round, the sceptre half).
 *
 * <p>It makes the same promise {@link IceAndFireCompatGameTest} makes for the probe: what can be proven in a workspace
 * without the other mod is the part that is <b>pure vanilla</b>, and that part is deliberately the whole command layer -
 * the contract of a dragon, the orders on it, the selection of a queen and the friendly rules around it. Only the goals
 * that really move a dragon need Ice and Fire, and those are proven on the external test instance.
 *
 * <p>What it proves, one promise at a time:
 *
 * <ul>
 * <li>an order survives the round trip through the persistent data of an entity,</li>
 * <li>a command of the sceptre reaches the contract dragons a queen <b>selected</b> and nobody else,</li>
 * <li>a dragon under the contract of somebody else never answers her,</li>
 * <li>the queen herself, a spider of her brood and another dragon of her own contract are never the victim of an
 * attack order, and</li>
 * <li>the colony layer treats a contract dragon as a creature of the queen who holds it, in both directions.</li>
 * </ul>
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class NecromancyScepterGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Ten minutes, the length the formal skill uses. */
    private static final int CONTRACT_TICKS = 12000;

    private NecromancyScepterGameTest()
    {
    }

    /**
     * <b>An order is ordinary persistent data of the dragon.</b>
     *
     * <p>The order of a contract dragon is what the goals of the compatibility read every tick, so it has to survive
     * exactly like the contract itself does: a write, a read, and the replacement of one order by the next - which is
     * what makes a new command outrank an old one, and what a chunk unload and a relog can not lose.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void theorderroundtripsonacontract(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Cow dragon = dragon(helper, 0, 0);
        long now = level.getGameTime();
        UUID owner = UUID.randomUUID();
        BlockPos target = helper.absolutePos(new BlockPos(8, 1, 8));

        NecromancyContract.started(owner, now, CONTRACT_TICKS, false).write(dragon);

        expect(helper, "a fresh contract holds the owner", NecromancyService.isContractOwner(dragon, owner));
        expect(helper, "with the deadline of the round",
                NecromancyContract.of(dragon).orElseThrow().remaining(now) == CONTRACT_TICKS);
        expect(helper, "and it is idle", NecromancyOrders.orderOf(dragon) == Order.NONE);

        NecromancyContract.of(dragon).orElseThrow().withOrder(Order.MOVE, target, null).write(dragon);
        NecromancyContract read = NecromancyContract.of(dragon).orElseThrow();

        expect(helper, "a move order is written", read.order() == Order.MOVE);
        expect(helper, "with the point it was given", target.equals(read.position()));
        expect(helper, "and the middle of that point is what a goal walks to",
                read.positionCentre() != null && read.positionCentre().x == target.getX() + 0.5D);

        UUID victim = UUID.randomUUID();
        NecromancyContract.of(dragon).orElseThrow().withOrder(Order.ATTACK, null, victim).write(dragon);
        read = NecromancyContract.of(dragon).orElseThrow();

        expect(helper, "the next order replaces the old one", read.order() == Order.ATTACK);
        expect(helper, "and leaves no point of the old one behind", read.position() == null);
        expect(helper, "with the victim of the attack order", victim.equals(read.target()));

        NecromancyOrders.writePhase(dragon, NecromancyContract.Phase.BREATHING);
        expect(helper, "a bombardment keeps its phase",
                NecromancyContract.of(dragon).orElseThrow().phase() == NecromancyContract.Phase.BREATHING);

        NecromancyOrders.clearOrder(dragon);
        read = NecromancyContract.of(dragon).orElseThrow();

        expect(helper, "clearing the order leaves the contract itself alone", read.order() == Order.NONE);
        expect(helper, "and keeps the owner", owner.equals(read.owner()));
        expect(helper, "and the deadline", read.remaining(now) == CONTRACT_TICKS);

        helper.succeed();
    }

    /**
     * <b>A contract dragon keeps the standing orders of a spider</b> (this round's brood stances).
     *
     * <p>The very follow, hold, patrol and free attack of a minion, written into the contract: the anchor a post stands
     * on, the anchor a patrol keeps when it steps on to patrol, and a follower that carries none at all.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void thestancesofacontractroundtrip(GameTestHelper helper)
    {
        Cow dragon = dragon(helper, 2, 2);
        contract(dragon, UUID.randomUUID());

        expect(helper, "a fresh contract is a follower", NecromancyOrders.stanceOf(dragon) == SpiderOrderMode.FOLLOW);

        BlockPos post = helper.absolutePos(new BlockPos(5, 1, 5));
        NecromancyOrders.setStance(dragon, SpiderOrderMode.HOLD, post);

        expect(helper, "a post is written", NecromancyOrders.stanceOf(dragon) == SpiderOrderMode.HOLD);
        expect(helper, "with the place it stands on",
                post.equals(NecromancyContract.of(dragon).orElseThrow().anchor()));

        NecromancyOrders.setStance(dragon, SpiderOrderMode.PATROL, post);

        expect(helper, "a patrol keeps that post", NecromancyOrders.stanceOf(dragon) == SpiderOrderMode.PATROL);
        expect(helper, "and its anchor is unchanged",
                post.equals(NecromancyContract.of(dragon).orElseThrow().anchor()));

        NecromancyOrders.setStance(dragon, SpiderOrderMode.FOLLOW, post);

        expect(helper, "a follower carries no anchor at all",
                NecromancyContract.of(dragon).orElseThrow().anchor() == null);
        expect(helper, "and the free hunt is the stance that hunts everything",
                SpiderOrderMode.FREE_ATTACK.huntsFreely() && !SpiderOrderMode.PATROL.huntsFreely());

        helper.succeed();
    }

    /**
     * <b>An idle contract is not a crash</b> (the regression test of the crash of 2026-10-09).
     *
     * <p>The targeting goal of a contract dragon is asked on every tick of the animal, and most of those ticks have
     * nothing to fight: the answer is null. This test runs the goal on a real mob with a real contract and no prey at
     * all, which is exactly the situation that produced a `NullPointerException` in a live world - the goal compared the
     * wanted target with `equals` before it had asked whether there was one.
     *
     * <p>It is a pure vanilla test: the goal, the contract and the mob are ordinary game objects, so it runs in this
     * workspace, where Ice and Fire is not installed.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void anidlecontractgoalisnotacrash(GameTestHelper helper)
    {
        Cow cow = dragon(helper, 3, 1);
        NecromancyTargetGoal goal = new NecromancyTargetGoal(cow);

        expect(helper, "a mob without a contract is not governed by the goal", !goal.canUse());
        goal.tick();

        expect(helper, "and a tick without a contract picks nothing", cow.getTarget() == null);

        contract(cow, UUID.randomUUID());

        expect(helper, "with a contract the goal takes its turn", goal.canUse());
        expect(helper, "and it would keep it", goal.canContinueToUse());

        // The crash itself: a contract with no prey, ticked twice - and then the free hunt, which searches the whole
        // neighbourhood. (The shared world of a game test run is never empty, so the free hunt is only asked to be
        // polite, not to be idle: whatever it picks must not be a friend of its contract.)
        goal.tick();
        goal.tick();

        expect(helper, "an idle contract keeps no target and never throws", cow.getTarget() == null);

        NecromancyOrders.setStance(cow, SpiderOrderMode.FREE_ATTACK, cow.blockPosition());
        goal.tick();

        expect(helper, "the free hunt never picks a friend of the contract",
                cow.getTarget() == null || !NecromancyService.isFriendlyToContract(cow, cow.getTarget()));

        cow.setTarget(null);
        helper.succeed();
    }

    /**
     * <b>A command reaches the contract dragons a queen selected, and nobody else.</b>
     *
     * <p>It is the whole sceptre half of this round in one test: the selection of the queen decides which unit answers,
     * a command without a selection reaches nobody, the order is written on the contract of the dragon that answered,
     * and the contract of somebody else is never touched - the exact rules the spider commands of the brood follow.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void theselectedcontractdragonanswers(GameTestHelper helper)
    {
        ServerPlayer queen = queen(helper, "necromancy_scepter_queen");
        ServerPlayer stranger = queen(helper, "necromancy_scepter_stranger");
        Cow dragon = dragon(helper, 1, 1);
        Cow foreign = dragon(helper, 3, 3);
        BlockPos target = helper.absolutePos(new BlockPos(12, 1, 12));

        contract(dragon, queen.getUUID());
        contract(foreign, stranger.getUUID());

        expect(helper, "the queen holds the contract of her dragon", NecromancyOrders.isContractDragonOf(queen, dragon));
        expect(helper, "but not the one of somebody else", !NecromancyOrders.isContractDragonOf(queen, foreign));
        expect(helper, "and the stranger does not hold hers", !NecromancyOrders.isContractDragonOf(stranger, dragon));
        expect(helper, "a command without a selection reaches nobody", NecromancyOrders.moveTo(queen, target) == 0);

        select(queen, dragon);

        expect(helper, "the selection echo carries the dragon",
                NecromancyOrders.selectedEntityIds(queen).contains(dragon.getId()));
        expect(helper, "the move order is accepted by one dragon", NecromancyOrders.moveTo(queen, target) == 1);
        expect(helper, "and written on its contract", NecromancyOrders.orderOf(dragon) == Order.MOVE
                && target.equals(NecromancyContract.of(dragon).orElseThrow().position()));
        expect(helper, "while the dragon of the stranger is untouched", NecromancyOrders.orderOf(foreign) == Order.NONE);

        ArmorStand victim = victim(helper, 6, 6);

        expect(helper, "the attack order replaces the move order", NecromancyOrders.attack(queen, victim) == 1);
        expect(helper, "and names that victim",
                victim.getUUID().equals(NecromancyContract.of(dragon).orElseThrow().target()));
        expect(helper, "the victim of the order is resolved again from the contract",
                NecromancyOrders.orderedVictim(dragon).orElse(null) == victim);
        expect(helper, "the queen herself is never a victim", NecromancyOrders.attack(queen, queen) == 0);
        expect(helper, "and neither is the dragon itself", NecromancyOrders.attack(queen, dragon) == 0);
        expect(helper, "and neither is a spider of her brood", NecromancyOrders.attack(queen, spider(helper, queen)) == 0);
        expect(helper, "and no refused order was written", NecromancyOrders.orderOf(dragon) == Order.ATTACK);

        QueenDataCapability.get(queen).ifPresent(data -> data.broodSelection().clear());

        expect(helper, "a selection that was given up commands nobody again",
                NecromancyOrders.moveTo(queen, target) == 0);

        helper.succeed();
    }

    /**
     * <b>A contract dragon belongs to the colony of the queen who holds it.</b>
     *
     * <p>It is the one line that makes the whole friendly protection work on the spider side without a second rule
     * anywhere: the colony layer answers the contract owner for a dragon under contract, so a spider of that queen may
     * not pick the skeleton as a victim and the skeleton may not pick the spider - the very rule
     * {@code QueenOwnerProtection} already applies to every creature of a brood.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void thecontractdragonbelongstothequeen(GameTestHelper helper)
    {
        ServerPlayer queen = queen(helper, "necromancy_colony_queen");
        ServerPlayer stranger = queen(helper, "necromancy_colony_stranger");
        Cow dragon = dragon(helper, 1, 3);
        Cow sibling = dragon(helper, 3, 1);
        SpiderMinionEntity spider = spider(helper, queen);

        contract(dragon, queen.getUUID());
        contract(sibling, queen.getUUID());

        expect(helper, "the colony of the dragon is the queen who holds it",
                queen.getUUID().equals(QueenOwnerProtection.ownerOf(dragon)));
        expect(helper, "a spider of that queen may not attack it", !QueenOwnerProtection.mayAttack(spider, dragon));
        expect(helper, "and it may not attack that spider", !QueenOwnerProtection.mayAttack(dragon, spider));

        expect(helper, "the queen herself is a friend of the contract",
                NecromancyService.isFriendlyToContract(dragon, queen));
        expect(helper, "another dragon of her own contract is one as well",
                NecromancyService.isFriendlyToContract(dragon, sibling));
        expect(helper, "a stranger is not", !NecromancyService.isFriendlyToContract(dragon, stranger));
        expect(helper, "a creature without any contract is not either",
                !NecromancyService.isFriendlyToContract(dragon, victim(helper, 5, 5)));
        expect(helper, "and a dragon without a contract is nobody's ally",
                !NecromancyService.isFriendlyToContract(dragon(helper, 7, 7), dragon));

        helper.succeed();
    }

    /**
     * <b>The column above a point decides the shape of a bombardment</b> (this round's breath fix).
     *
     * <p>Twenty five blocks of free air above the point mean a dragon may hover over it and breathe straight down; a
     * single block in that column means it has to come near on the ground and breathe from there. The rule is asked of
     * the world alone, so it is proven here, in a workspace without Ice and Fire - and it is proven on a real level,
     * because "is this air" is a question only the game can answer.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void theskyabovethepointdecidesthebombardment(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos point = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos column = new BlockPos(6, 2, 6);

        // The column is cut open first, because this rule is about the column and not about the test world: whatever a
        // flat template happens to have above that point, the 25 blocks a bombardment asks for are cleared here - and
        // they are cleared one by one, which is also the proof that the question is about every single block of them.
        for (int up = 0; up < NecromancyOrders.SKY_HEIGHT; up++)
        {
            helper.setBlock(column.above(up), net.minecraft.world.level.block.Blocks.AIR);
        }

        expect(helper, "a column of free air is a sky a bombardment may fly in",
                NecromancyOrders.skyOpen(level, point));

        helper.setBlock(column.above(NecromancyOrders.SKY_HEIGHT - 1),
                net.minecraft.world.level.block.Blocks.STONE);

        expect(helper, "one block at the very top of the column is a roof",
                !NecromancyOrders.skyOpen(level, point));

        helper.setBlock(column.above(NecromancyOrders.SKY_HEIGHT - 1), net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(column.above(4), net.minecraft.world.level.block.Blocks.STONE);

        expect(helper, "and so is one block in the middle of it", !NecromancyOrders.skyOpen(level, point));

        helper.setBlock(column.above(4), net.minecraft.world.level.block.Blocks.AIR);

        expect(helper, "taking the roof away opens the sky again", NecromancyOrders.skyOpen(level, point));

        helper.succeed();
    }

    /**
     * <b>The drag selection of the sceptre reaches the contract dragons</b> (this round's box fix).
     *
     * <p>Framing a swarm used to leave a skeleton out of the selection, and a unit that is not selected answers no key.
     * This test frames one contract dragon of the queen, one of somebody else and one with no contract at all, and asks
     * the server side query the box selection uses: exactly one of them may answer.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void theboxfindsthecontractdragons(GameTestHelper helper)
    {
        ServerPlayer queen = queen(helper, "necromancy_box_queen");
        ServerPlayer stranger = queen(helper, "necromancy_box_stranger");
        Cow mine = dragon(helper, 2, 2);
        Cow foreign = dragon(helper, 4, 4);
        Cow wild = dragon(helper, 6, 6);

        contract(mine, queen.getUUID());
        contract(foreign, stranger.getUUID());

        AABB box = new AABB(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(12, 6, 12)));
        List<Entity> found = NecromancyOrders.dragonsIn(queen, box);

        expect(helper, "the contract dragon of the queen is inside the box", found.contains(mine));
        expect(helper, "the dragon of somebody else is not", !found.contains(foreign));
        expect(helper, "and neither is a dragon without a contract", !found.contains(wild));
        expect(helper, "and the box holds exactly one of them", found.size() == 1);

        helper.succeed();
    }

    /**
     * <b>A stand-down is an interrupt, not a wish</b> (this round's HOLD fix).
     *
     * <p>The reported bug was "HOLD does nothing on a skeleton", and half of its cause was here, on the contract: a
     * post only wrote the <i>stance</i>, while the goal that holds a post refuses to run at all while an errand is still
     * on the contract. This test marches a dragon somewhere and then stands it down, and asks the contract what it
     * carries: the errand has to be gone, the stance has to be the post, and the post has to be the ground the dragon was
     * standing on.
     *
     * <p>What a game test <b>cannot</b> ask here is the other half - whether the body really lies down - because that is
     * a nest of Ice and Fire state (command, sitting pose, sleep ramp) and that mod is not installed in this workspace.
     * It is listed as unverified in the round's report.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void astanddowninterruptstheerrandfirst(GameTestHelper helper)
    {
        ServerPlayer queen = queen(helper, "necromancy_hold_queen");
        Cow dragon = dragon(helper, 2, 2);
        BlockPos post = dragon.blockPosition();

        contract(dragon, queen.getUUID());
        select(queen, dragon);

        NecromancyOrders.moveTo(queen, helper.absolutePos(new BlockPos(10, 1, 10)));
        expect(helper, "a march is on the contract first", NecromancyOrders.orderOf(dragon) == Order.MOVE);

        int held = NecromancyOrders.holdHere(queen);

        expect(helper, "the stand-down reached the dragon", held == 1);
        expect(helper, "and the errand is gone before anything else", NecromancyOrders.orderOf(dragon) == Order.NONE);
        expect(helper, "the new order is a post", NecromancyOrders.stanceOf(dragon) == SpiderOrderMode.HOLD);
        expect(helper, "and it stands where the dragon stood",
                post.equals(NecromancyContract.of(dragon).orElseThrow().anchor()));
        expect(helper, "the contract itself is untouched",
                queen.getUUID().equals(NecromancyContract.of(dragon).orElseThrow().owner()));

        helper.succeed();
    }

    /**
     * <b>A dragon of the queen is offered to the "everything I can see" key, and a dragon of somebody else is not</b>
     * (this round's selection fix, server half).
     *
     * <p>The key sends the entity ids a client believes are on its screen and the server decides which of them are
     * really hers - and for the dragons it takes that list from the <b>index</b> the contract itself maintains, which is
     * what this test writes next to the contract. The spider half of the same key is untouched and stays where it was.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void thevisiblekeyknowsonlyherown(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer queen = queen(helper, "necromancy_visible_queen");
        ServerPlayer stranger = queen(helper, "necromancy_visible_stranger");
        Cow mine = dragon(helper, 2, 2);
        Cow foreign = dragon(helper, 4, 4);

        contract(mine, queen.getUUID());
        NecromancyIndex.get(level).put(mine.getUUID(), level.getGameTime() + CONTRACT_TICKS);
        contract(foreign, stranger.getUUID());
        NecromancyIndex.get(level).put(foreign.getUUID(), level.getGameTime() + CONTRACT_TICKS);

        List<Entity> hers = NecromancyOrders.ownedLoaded(queen);

        expect(helper, "the queen's own contract dragon is offered", hers.contains(mine));
        expect(helper, "the dragon of somebody else is not", !hers.contains(foreign));
        expect(helper, "and she has exactly one of them here", hers.size() == 1);

        helper.succeed();
    }

    /**
     * <b>A box that only touches the edge of a dragon still selects it</b> (this round's drag-selection fix).
     *
     * <p>A skeleton is far bigger than the drag a queen draws over it, so the test builds the drag that used to fail: a
     * box that meets the animal's body while the <b>centre</b> of the animal stays outside it. The old rule ("is the
     * centre of the dragon inside the box") missed exactly that shape; the new rule asks whether the two bounding boxes
     * meet.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void aboxthattouchestheedgestillselectsadragon(GameTestHelper helper)
    {
        ServerPlayer queen = queen(helper, "necromancy_box_queen");
        Cow dragon = dragon(helper, 2, 2);

        contract(dragon, queen.getUUID());
        queen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HungryCraftItems.QUEEN_SCEPTER.get()));

        AABB body = dragon.getBoundingBox();
        Vec3 from = new Vec3(body.minX - 1.0D, body.minY, body.minZ - 1.0D);
        Vec3 to = new Vec3(body.minX - 0.2D, body.minY, body.getCenter().z);

        expect(helper, "the centre of the dragon is NOT inside that drag, which is the drag the old rule missed",
                !BroodSelection.selectionBox(from, to).contains(body.getCenter().x, body.getCenter().y,
                        body.getCenter().z));

        BroodSelection.selectBox(queen, from, to);

        List<UUID> selected = QueenDataCapability.get(queen)
                .map(data -> List.copyOf(data.broodSelection().ids())).orElse(List.of());

        expect(helper, "but the dragon is selected, because the two boxes meet", selected.contains(dragon.getUUID()));
        expect(helper, "and nothing else is", selected.size() == 1);

        helper.succeed();
    }

    /**
     * <b>The rally brings a contract dragon home, as a follower</b> (this round's "no reliable way to call it" fix).
     *
     * <p>The queen could always rally her skeletons - the command wheel calls the very same code - but a skeleton that is
     * marching somewhere is not waiting next to her, and the debug command of this round
     * ({@code /hungrycraft debug iaf recall}) is nothing but that same rally without the wheel. This test pins the
     * behaviour both of them rely on: the errand is dropped, the stance becomes FOLLOW, and the dragon really answered.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void aralliedskeletoncomeshomeasafollower(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer queen = queen(helper, "necromancy_rally_queen");
        Cow dragon = dragon(helper, 2, 2);

        contract(dragon, queen.getUUID());
        NecromancyIndex.get(level).put(dragon.getUUID(), level.getGameTime() + CONTRACT_TICKS);
        select(queen, dragon);

        NecromancyOrders.moveTo(queen, helper.absolutePos(new BlockPos(10, 1, 10)));
        expect(helper, "the skeleton is on an errand first", NecromancyOrders.orderOf(dragon) == Order.MOVE);

        int called = NecromancyOrders.rally(queen);

        expect(helper, "the rally reached the skeleton", called == 1);
        expect(helper, "and its errand is gone", NecromancyOrders.orderOf(dragon) == Order.NONE);
        expect(helper, "it is a follower now", NecromancyOrders.stanceOf(dragon) == SpiderOrderMode.FOLLOW);
        expect(helper, "and the contract is untouched",
                queen.getUUID().equals(NecromancyContract.of(dragon).orElseThrow().owner()));

        helper.succeed();
    }

    /**
     * <b>A dragon that was woken out of its bones belongs to the player who woke it</b> (the basic fix of this round).
     *
     * <p>The waking writes the UUID of the waker into the marker of the dragon (it always did), and this round taught
     * the ownership question to <b>read</b> it: {@code NecromancyService#ownerOf} answers with the contract when there is
     * one, and with the waker when there is not - so a skeleton that has just stood up is his in every sense the rest of
     * the feature can observe, long before the first contract is written on it.
     *
     * <p>The order of the two sources is part of the promise and is asserted here: a contract, once it exists, is the
     * answer (the dragon now works for the player who holds it), and an ended contract leaves no stale claim behind
     * because the corpse it makes clears the marker with it.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void adragonwokenoutofbonesbelongstotheplayerwhowokeit(GameTestHelper helper)
    {
        ServerPlayer waker = queen(helper, "necromancy_waker_queen");
        ServerPlayer stranger = queen(helper, "necromancy_waker_stranger");
        Cow dragon = dragon(helper, 1, 1);

        expect(helper, "before anybody wakes it, the dragon belongs to nobody",
                NecromancyService.ownerOf(dragon).isEmpty());
        expect(helper, "and it is nobody's ally either", !NecromancyService.isFriendlyToContract(dragon, waker));

        NecromancyMarker.mark(dragon, waker.getUUID(), 0, dragon.getHealth());

        expect(helper, "the waking makes the player who woke it the owner",
                NecromancyService.ownerOf(dragon).equals(java.util.Optional.of(waker.getUUID())));
        expect(helper, "so it belongs to him", NecromancyService.belongsTo(dragon, waker));
        expect(helper, "and to nobody else", !NecromancyService.belongsTo(dragon, stranger));
        expect(helper, "it has an owner at all", NecromancyService.hasNecromancyOwner(dragon));
        expect(helper, "and the waker is on its side", NecromancyService.isFriendlyToContract(dragon, waker));
        expect(helper, "while a stranger is not", !NecromancyService.isFriendlyToContract(dragon, stranger));

        contract(dragon, stranger.getUUID());

        expect(helper, "a contract outranks the waking", NecromancyService.belongsTo(dragon, stranger));
        expect(helper, "and the waker is not the owner any more", !NecromancyService.belongsTo(dragon, waker));

        NecromancyContract.clear(dragon);
        NecromancyMarker.clear(dragon);

        expect(helper, "and with no contract and no marker it is nobody's again",
                !NecromancyService.hasNecromancyOwner(dragon));

        helper.succeed();
    }

    /**
     * <b>The goal isolation is idempotent and reversible</b> (Necromancy IV round 7, the isolation).
     *
     * <p>This is the one promise of the isolation that can be proven in a workspace without Ice and Fire: the mechanism
     * of {@code NecromancyService#detachGoals} / {@code #restoreGoals} that removes a goal and later puts it back at its
     * original priority. The list of goals a real contract removes is just an argument to that mechanism, so what is
     * proven here is that the removal happens exactly once, that repeating it is a no-op, and that the restore returns
     * the exact goal that was taken. A HurtByTargetGoal stands in for any of the autonomous target goals.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void thegoalisolationisidempotentandreversible(GameTestHelper helper)
    {
        Cow cow = dragon(helper, 1, 1);
        cow.targetSelector.addGoal(3, new HurtByTargetGoal(cow));

        expect(helper, "the goal is installed", hasGoal(cow.targetSelector, HurtByTargetGoal.class));

        List<NecromancyService.RemovedGoal> removed = NecromancyService.detachGoals(cow.targetSelector,
                HurtByTargetGoal.class);

        expect(helper, "exactly one goal was detached", removed.size() == 1);
        expect(helper, "and it is gone now", !hasGoal(cow.targetSelector, HurtByTargetGoal.class));
        expect(helper, "a second detach is a no-op",
                NecromancyService.detachGoals(cow.targetSelector, HurtByTargetGoal.class).isEmpty());

        NecromancyService.restoreGoals(cow.targetSelector, removed);

        expect(helper, "and restoring puts the very same goal back", hasGoal(cow.targetSelector, HurtByTargetGoal.class));

        helper.succeed();
    }

    /** @return true when a goal of that kind is installed in that selector, for the isolation test above. */
    private static boolean hasGoal(GoalSelector selector, Class<? extends Goal> type)
    {
        for (WrappedGoal wrapped : selector.getAvailableGoals())
        {
            if (type.isInstance(wrapped.getGoal()))
            {
                return true;
            }
        }

        return false;
    }

    /** Writes the contract of one test dragon, with ten minutes on it and nothing else. */
    private static void contract(LivingEntity dragon, UUID owner)
    {
        NecromancyContract.started(owner, dragon.level().getGameTime(), CONTRACT_TICKS, false).write(dragon);
    }

    /** @return one cow of this test at that offset, added to the level so that a contract is resolved by its id. */
    private static Cow dragon(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        Cow cow = EntityType.COW.create(level);

        if (cow == null)
        {
            throw new IllegalStateException("the game refused to build a cow for the necromancy sceptre test");
        }

        BlockPos pos = helper.absolutePos(new BlockPos(offsetX, 1, offsetZ));
        cow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(cow);

        return cow;
    }

    /** @return the quiet victim of the attack test: an armour stand, which no other test has an opinion about. */
    private static ArmorStand victim(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        ArmorStand stand = EntityType.ARMOR_STAND.create(level);

        if (stand == null)
        {
            throw new IllegalStateException("the game refused to build an armour stand for the necromancy sceptre test");
        }

        BlockPos pos = helper.absolutePos(new BlockPos(offsetX, 1, offsetZ));
        stand.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(stand);

        return stand;
    }

    /** @return one fake queen of this test, deliberately never put into the level (see the other brood tests). */
    private static ServerPlayer queen(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);

        return player;
    }

    /** @return one spider of that queen, which is the creature a contract dragon must never fight. */
    private static SpiderMinionEntity spider(GameTestHelper helper, ServerPlayer queen)
    {
        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(helper.getLevel());

        if (spider == null)
        {
            throw new IllegalStateException("the game refused to build an imp spider for the necromancy sceptre test");
        }

        spider.setOwnerUUID(queen.getUUID());

        return spider;
    }

    /** Selects that dragon for that queen, exactly the way the sceptre writes a selection. */
    private static void select(ServerPlayer queen, LivingEntity dragon)
    {
        QueenDataCapability.get(queen).ifPresent(data -> data.broodSelection().selectOnly(List.of(dragon.getUUID())));
    }

    /** Fails the test on the promise that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("NECROMANCY-IV: " + what);
        }
    }
}
