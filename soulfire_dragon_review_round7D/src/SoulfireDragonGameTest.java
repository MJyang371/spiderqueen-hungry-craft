package spiderqueenhungrycraft.soulfire;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.player.QueenDataCapability;
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

    // ------------------------------------------------------------------------------------------------------------
    // Round 7E, stage A: survival and the safety of the remains
    // ------------------------------------------------------------------------------------------------------------

    /** 7E A1: a unit that comes down out of the sky keeps every heart it had. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 400)
    public static void aunitthatfallsoutoftheskykeepsallitshearts(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        float before = dragon.getHealth();
        double groundY = dragon.getY();
        BlockPos column = helper.absolutePos(DRAGON_POS);

        // A clear shaft first. The unit is three blocks wide, so a one block hole would leave it wedged in the roof of
        // the test structure - suffocating where it should be falling, which is not what this test is about.
        for (int up = 1; up <= 45; up++)
        {
            for (int dx = -2; dx <= 2; dx++)
            {
                for (int dz = -2; dz <= 2; dz++)
                {
                    helper.getLevel().setBlockAndUpdate(column.offset(dx, up, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }

        // The rule itself, asked directly: a fall of thirty blocks is answered with "no damage" and the counter is cleared.
        dragon.fallDistance = 30.0F;
        expect(helper, "the fall rule of the unit refuses to hurt it",
                !dragon.causeFallDamage(30.0F, 1.0F, dragon.damageSources().fall()));
        expect(helper, "and the fall counter was cleared with it", dragon.fallDistance == 0.0F);
        expect(helper, "so the rule alone left it untouched", dragon.getHealth() >= before - 0.01F);

        // And then the real thing: forty blocks of free fall through that shaft.
        dragon.teleportTo(dragon.getX(), groundY + 40.0D, dragon.getZ());
        dragon.setDeltaMovement(Vec3.ZERO);

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] % 40 == 0)
            {
                SpiderQueenHungryCraft.LOGGER.info("[7E fall] tick={} y={} groundY={} onGround={} noGravity={} removed={} "
                        + "health={} delta={}", ticks[0], dragon.getY(), groundY, dragon.onGround(), dragon.isNoGravity(),
                        dragon.isRemoved(), dragon.getHealth(), dragon.getDeltaMovement());
            }

            if (ticks[0] > 360)
            {
                helper.fail("the unit never came down: y=" + dragon.getY() + " groundY=" + groundY + " onGround="
                        + dragon.onGround() + " noGravity=" + dragon.isNoGravity() + " removed=" + dragon.isRemoved()
                        + " health=" + dragon.getHealth());
            }

            // The ground flag of the spawn survives the teleport for one tick, so the fall is only read once the unit
            // has really come down again.
            if (!dragon.onGround() || dragon.getY() > groundY + 1.0D)
            {
                return;
            }

            expect(helper, "the unit came down on the floor", !dragon.isRemoved());
            expect(helper, "and it really did fall: " + (int) (groundY + 40.0D - dragon.getY()) + " blocks",
                    groundY + 40.0D - dragon.getY() > 35.0D);
            expect(helper, "and it is not left floating either", !dragon.isNoGravity());
            expect(helper, "and the fall cost it nothing: " + dragon.getHealth() + " of " + before,
                    dragon.getHealth() >= before - 0.01F);

            helper.succeed();
        });
    }

    /** 7E A2: soul fire does not burn, and that is the only thing the unit is immune to. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void fireandlavacannotburnaunitofsoulfire(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        float before = dragon.getHealth();

        expect(helper, "the entity type of the unit is registered as fire immune",
                HungryCraftEntities.SOULFIRE_DRAGON.get().fireImmune());

        dragon.hurt(dragon.damageSources().inFire(), 6.0F);
        expect(helper, "standing in fire does not hurt it", dragon.getHealth() >= before - 0.01F);

        dragon.hurt(dragon.damageSources().lava(), 6.0F);
        expect(helper, "and lava does not hurt it", dragon.getHealth() >= before - 0.01F);

        // The control of this test: the immunity may not be a blanket invulnerability.
        dragon.hurt(dragon.damageSources().generic(), 6.0F);
        expect(helper, "but an ordinary blow still reaches it", dragon.getHealth() < before - 0.01F);
        helper.succeed();
    }

    /** 7E A3: a spawn that is refused leaves the remains exactly where they were. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void arefusedspawnleavestheremainsuntouched(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        SoulfireDragonEntity corpse = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), new BlockPos(3, 1, 3));
        SoulfireDragonEntity refused = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        expect(helper, "a unit can be built for the attempt", refused != null);

        SoulfireFaults.failNextSpawns(1);
        boolean handed = SoulfireRising.handOverCorpse(level, refused, corpse);

        expect(helper, "the spawn was refused", !handed);
        expect(helper, "the remains are still in the world", !corpse.isRemoved());
        expect(helper, "and the unit that could not rise never entered it", level.getEntity(refused.getUUID()) == null);
        expect(helper, "and it was cleaned up", refused.isRemoved());

        // The other half of the rule, without which the first half would prove nothing: an accepted spawn really does
        // hand the remains over.
        SoulfireFaults.clear();
        SoulfireDragonEntity risen = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        expect(helper, "a second unit can be built", risen != null);
        expect(helper, "the hand over succeeds", SoulfireRising.handOverCorpse(level, risen, corpse));
        expect(helper, "the remains are gone now", corpse.isRemoved());
        expect(helper, "and the unit took their place", level.getEntity(risen.getUUID()) != null);

        risen.discard();
        helper.succeed();
    }

    /** 7E A3: a restore that fails does not lose the identity of the remains. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void arefusedrestorekeepstheidentityoftheremains(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        UUID soul = dragon.getUUID();
        UUID original = UUID.randomUUID();
        CompoundTag remains = new CompoundTag();

        remains.putString("Id", "iceandfire:fire_dragon");
        remains.putInt("Age", 4);
        dragon.setOriginalBone(remains, original);
        dragon.setOrigin(SoulfireDragonEntity.Origin.FIRE);
        dragon.setStage(4);
        dragon.beginContract(UUID.randomUUID(), level.getGameTime() + 12_000);

        // Two failures are armed: the one at the end of the contract and the one of the retry that follows it. In a
        // workspace without the other mod the restore already fails at the gate before that, and the proof is the same.
        SoulfireFaults.failNextRestores(2);
        SoulfireRising.expire(dragon);

        SoulfireContracts.Contract record = SoulfireContracts.get(level).find(soul);

        expect(helper, "the unit is gone", dragon.isRemoved());
        expect(helper, "the contract is still owed to the world",
                record != null && record.state() == SoulfireContracts.State.PENDING);
        expect(helper, "the identity of the remains survived", record != null && original.equals(record.original()));
        expect(helper, "their kind and stage survived",
                record != null && "FIRE".equals(record.origin()) && record.stage() == 4);
        expect(helper, "and their whole saved tag survived",
                record != null && record.remains() != null && record.remains().getInt("Age") == 4);

        SoulfireRising.retryPending(level.getServer());

        SoulfireContracts.Contract after = SoulfireContracts.get(level).find(soul);

        expect(helper, "a failed retry keeps the record instead of dropping it",
                after != null && original.equals(after.original()) && after.remains() != null);

        SoulfireContracts.get(level).drop(soul);
        SoulfireFaults.clear();
        helper.succeed();
    }

    /** 7E A3: the death of a unit is a contract that is honoured, not a bone dragon that is lost. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void thedeathofaunithandstheremainsback(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        UUID soul = dragon.getUUID();
        UUID original = UUID.randomUUID();
        CompoundTag remains = new CompoundTag();

        remains.putString("Id", "iceandfire:ice_dragon");
        dragon.setOriginalBone(remains, original);
        dragon.setOrigin(SoulfireDragonEntity.Origin.ICE);
        dragon.setStage(5);
        dragon.beginContract(UUID.randomUUID(), level.getGameTime() + 12_000);

        SoulfireFaults.clear();
        dragon.kill();

        SoulfireContracts.Contract record = SoulfireContracts.get(level).find(soul);

        expect(helper, "the death wrote the contract into the ledger",
                record != null && record.state() == SoulfireContracts.State.PENDING);
        expect(helper, "with the identity of the remains", record != null && original.equals(record.original()));
        expect(helper, "and with what they were",
                record != null && "ICE".equals(record.origin()) && record.stage() == 5);

        SoulfireContracts.get(level).drop(soul);
        helper.succeed();
    }

    /**
     * The guard of the whole round: without the other mod nothing is raised, and <b>nothing is lost either</b>.
     *
     * <p>In a workspace without Ice and Fire this is the real state of the game; in a game with it, the same two lines
     * are taken for any creature that is not remains. In both cases the creature that was aimed at is untouched, which
     * is the promise this round is about.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void withouttheothermodnothingisraisedandnothingislost(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity aimedAt = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);

        SoulfireRising.Rising rising = SoulfireRising.raise(queen, aimedAt);

        expect(helper, "the rising was refused", rising.dragon() == null && !rising.report().ok());
        expect(helper, "and the creature it was aimed at was not touched", !aimedAt.isRemoved());
        expect(helper, "and it never became remains of this mod", !SoulfireRising.mayRaise(aimedAt));
        helper.succeed();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Round 7E, stage B: the flight state machine and the arbitration of commands
    // ------------------------------------------------------------------------------------------------------------

    /** 7E B1: a long MOVE order flies at one height, and never climbs into the sky. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 500)
    public static void alongflyoverdoesnotclimbforever(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        double startY = dragon.getY();

        // Far beyond the fifteen blocks that make a unit fly at all, in a straight line away from the structure.
        dragon.setMoveTo(helper.absolutePos(new BlockPos(90, 1, 2)), 1);

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;
            double climbed = dragon.getY() - startY;

            if (climbed > 25.0D)
            {
                helper.fail("the unit climbed " + (int) climbed + " blocks on a single MOVE order: the cruising height "
                        + "is being raised every tick again");
            }

            // Long enough that the old ladder would have put it a hundred blocks up.
            if (ticks[0] > 140)
            {
                helper.succeed();
            }
        });
    }

    /** 7E B2: a new order kills the flight of the old one in the same tick. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = TIMEOUT_TICKS)
    public static void anewordercancelstheflightoftheoldone(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);

        dragon.setMoveTo(helper.absolutePos(new BlockPos(60, 1, 60)), 3);

        int version = dragon.getCommandVersion();

        dragon.flyTo(new Vec3(dragon.getX(), dragon.getY() + 10.0D, dragon.getZ() + 40.0D), version);

        expect(helper, "the errand is steering the unit", dragon.isFlightActive());

        // The very case the round names: FOLLOW takes over while the MOVE flight is still running.
        dragon.followOrder();

        expect(helper, "a new order moves the command version on", dragon.getCommandVersion() > version);
        expect(helper, "the errand is gone", dragon.getMoveTarget() == null);
        expect(helper, "and its flight can not steer a single tick longer", !dragon.isFlightActive());
        expect(helper, "the unit is a follower again", dragon.getOrderMode() == SpiderOrderMode.FOLLOW);

        // The same for the stand-down key of the sceptre.
        dragon.setMoveTo(helper.absolutePos(new BlockPos(60, 1, 60)), 4);
        dragon.flyTo(new Vec3(dragon.getX(), dragon.getY() + 10.0D, dragon.getZ() + 40.0D),
                dragon.getCommandVersion());

        expect(helper, "a second errand is flying", dragon.isFlightActive());

        dragon.holdAt(helper.absolutePos(new BlockPos(2, 1, 2)));

        expect(helper, "the stand-down key kills it as well", !dragon.isFlightActive());
        expect(helper, "and leaves the unit on its post", dragon.getOrderMode() == SpiderOrderMode.HOLD);
        helper.succeed();
    }

    /** 7E B4: a unit saved in the air comes back with a gravity it can explain. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 200)
    public static void aunitsavedintheaircomesbackwithsensiblegravity(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);

        dragon.setFlying(true);
        dragon.setHovering(true);

        CompoundTag tag = new CompoundTag();
        dragon.saveWithoutId(tag);

        SoulfireDragonEntity reborn = HungryCraftEntities.SOULFIRE_DRAGON.get().create(helper.getLevel());

        expect(helper, "the unit can be built again", reborn != null);

        // The save carries the identity of the unit it came from, so the rebuild takes a fresh one <b>after</b> loading:
        // a second unit may never claim the identity of a unit that is still standing in the world, or the level would
        // simply refuse to add it (and this test is not about that).
        reborn.load(tag);
        reborn.setUUID(UUID.randomUUID());

        expect(helper, "the flight state came back", reborn.isFlying() && reborn.isHovering());
        expect(helper, "and gravity agrees with it", reborn.isNoGravity());
        expect(helper, "and no stale waypoint was resumed", !reborn.isFlightActive());

        // The other half of the rule of the round: a unit that is neither flying nor hovering may never keep floating,
        // whatever a save, another mod or an operator wrote on it.
        helper.getLevel().addFreshEntity(reborn);

        expect(helper, "the rebuilt unit is really in the world",
                helper.getLevel().getEntity(reborn.getUUID()) != null);

        reborn.setFlying(false);
        reborn.setHovering(false);
        reborn.setNoGravity(true);

        expect(helper, "the broken state is really there", reborn.isNoGravity());

        helper.onEachTick(() ->
        {
            if (!reborn.isNoGravity())
            {
                helper.succeed();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // Round 7F: the landing itself - the complaint that started the round
    // ------------------------------------------------------------------------------------------------------------

    /** How far from the floor of the structure a landed unit may stand, in blocks. */
    private static final double LANDING_TOLERANCE = 1.5D;

    /** One unit in the service of a fake queen, selected, so every command of the sceptre really reaches it. */
    private static SoulfireDragonEntity unitInService(GameTestHelper helper, ServerPlayer queen)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        dragon.beginContract(queen.getUUID(), queen.level().getGameTime() + 20000L);
        QueenDataCapability.get(queen).ifPresent(data -> data.broodSelection().selectOnly(List.of(dragon.getUUID())));

        return dragon;
    }

    /**
     * Carves the air a flying unit needs above the floor of the structure. It is three blocks wide, so a single hole
     * would only wedge it into the roof instead of letting it fly.
     */
    private static void carveAir(GameTestHelper helper, BlockPos centre, int dx, int dz, int height)
    {
        ServerLevel level = helper.getLevel();

        for (int x = -2; x <= dx; x++)
        {
            for (int z = -2; z <= dz; z++)
            {
                for (int y = 1; y <= height; y++)
                {
                    level.setBlockAndUpdate(centre.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    /** The exact shape of a finished landing: on the floor, nothing flying, nothing hovering, nothing left to land. */
    private static boolean hasLanded(SoulfireDragonEntity dragon)
    {
        return dragon.onGround() && !dragon.isFlying() && !dragon.isHovering() && !dragon.isLanding()
                && !dragon.isNoGravity();
    }

    /** @return the whole state of a unit, for the message of a landing test that ran out of time. */
    private static String stateOf(SoulfireDragonEntity dragon)
    {
        return "y=" + dragon.getY() + " onGround=" + dragon.onGround() + " flying=" + dragon.isFlying()
                + " hovering=" + dragon.isHovering() + " landing=" + dragon.isLanding()
                + " noGravity=" + dragon.isNoGravity() + " state=" + dragon.moveState();
    }

    /** 7F test 1: the stand-down key from high above ends on the floor, with nothing flapping in the air. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 600)
    public static void aholdfromhighabovefinallylands(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = unitInService(helper, queen);
        double floorY = dragon.getY();
        BlockPos start = helper.absolutePos(DRAGON_POS);

        carveAir(helper, start, 2, 2, 30);

        dragon.teleportTo(dragon.getX(), floorY + 18.0D, dragon.getZ());
        dragon.setFlying(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        expect(helper, "the stand-down key reaches the unit", SoulfireOrders.holdHere(queen) == 1);
        expect(helper, "and the unit is really in the air: " + stateOf(dragon), dragon.getY() > floorY + 15.0D);

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] > 500)
            {
                helper.fail("the unit never came down from its post: " + stateOf(dragon));
            }

            if (hasLanded(dragon))
            {
                expect(helper, "and it stands on the floor, not in the air: " + stateOf(dragon),
                        Math.abs(dragon.getY() - floorY) < LANDING_TOLERANCE);
                helper.succeed();
            }
        });
    }

    /** 7F test 2: a scout move of twenty blocks ends on the floor, near the place it was sent to. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 900)
    public static void adistanterrandendsonafloor(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = unitInService(helper, queen);
        double floorY = dragon.getY();
        BlockPos start = helper.absolutePos(DRAGON_POS);
        BlockPos destination = start.offset(20, 0, 0);
        Vec3 goal = Vec3.atBottomCenterOf(destination);

        // A clear lane, so the flight is not stopped by the roof or a wall of the structure: without it a unit inside a
        // covered arena could never fly anywhere, and this test would be about the arena instead of about the landing.
        carveAir(helper, start, 22, 2, 14);

        dragon.teleportTo(dragon.getX(), floorY + 18.0D, dragon.getZ());
        dragon.setFlying(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        expect(helper, "the scout key reaches the unit", SoulfireOrders.moveTo(queen, destination, 11) == 1);
        expect(helper, "and the unit took the errand", dragon.getMoveTarget() != null);

        int[] ticks = {0};
        double[] lastY = {dragon.getY()};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            // A descent is a series of small steps: a unit that drops in one tick was teleported, not flown.
            expect(helper, "the unit moves in steps, not in leaps: " + lastY[0] + " -> " + dragon.getY(),
                    Math.abs(dragon.getY() - lastY[0]) < 4.0D);
            lastY[0] = dragon.getY();

            if (ticks[0] > 800)
            {
                helper.fail("the errand never ended on a floor: " + stateOf(dragon) + " distance to the goal="
                        + dragon.position().distanceTo(goal));
            }

            if (hasLanded(dragon))
            {
                expect(helper, "and it stands on the floor of the world: " + stateOf(dragon),
                        Math.abs(dragon.getY() - floorY) < LANDING_TOLERANCE);
                expect(helper, "and it arrived where it was sent: " + dragon.position().distanceTo(goal),
                        dragon.position().distanceTo(goal) < 6.0D);
                helper.succeed();
            }
        });
    }

    /**
     * 7F test 3: the soft rally takes the errand away and the unit comes down again.
     *
     * <p>A game test world holds no real player, and the follow order of this unit looks its queen up in the player list
     * of the level, so what is proven here is the half that can be proven without a client: the old errand is cancelled,
     * the order really becomes FOLLOW, and the unit ends on the floor with every flight flag cleared instead of hanging
     * in the air with its wings out. The other half - flying to a queen who is really there and landing beside her - is
     * measured on a real server with a real player by the hazard probe, because no test structure can be a player.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 700)
    public static void afollowercomestothenextfloorallthesame(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = unitInService(helper, queen);
        double floorY = dragon.getY();
        BlockPos start = helper.absolutePos(DRAGON_POS);

        carveAir(helper, start, 2, 2, 30);

        // An errand of the old order, still steering, and a unit already in the air: the very state the round complains
        // about, where the wings keep beating after the order that sent it out is long over.
        SoulfireOrders.moveTo(queen, start.offset(30, 0, 0), 20);
        dragon.teleportTo(dragon.getX(), floorY + 16.0D, dragon.getZ());
        dragon.setFlying(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        expect(helper, "the soft rally reaches the unit", SoulfireOrders.followHere(queen) == 1);
        expect(helper, "and the unit is a follower now", dragon.getOrderMode() == SpiderOrderMode.FOLLOW);
        expect(helper, "and the errand of the old order is gone", dragon.getMoveTarget() == null);

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] > 600)
            {
                helper.fail("the follower never came down: " + stateOf(dragon));
            }

            if (hasLanded(dragon))
            {
                expect(helper, "and it stands on the floor: " + stateOf(dragon),
                        Math.abs(dragon.getY() - floorY) < LANDING_TOLERANCE);
                helper.succeed();
            }
        });
    }

    /**
     * 7F test 4: a descent that can never arrive gives itself up instead of hanging, and the unit can still be brought
     * down afterwards - the dead end the round names "landing without a target".
     *
     * <p>The unreachable landing place of this test lies behind a wall the unit can not pass. The old code sat in
     * LANDING on its way to it with the wings out for as long as the world stayed loaded, claiming to be landing while
     * going nowhere. The new limit turns that into a warning and a controlled hover, and the second half of the test
     * proves the unit is not lost by it: it reaches the floor of the world afterwards, on its own order.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 900)
    public static void alandingthatcannotarrivegivesupandtheunitstillcomessdown(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = unitInService(helper, queen);
        double floorY = dragon.getY();
        BlockPos start = helper.absolutePos(DRAGON_POS);
        ServerLevel level = helper.getLevel();

        carveAir(helper, start, 2, 2, 30);

        // A wall twenty-one blocks wide and twelve high, and a landing place far behind it: twenty-one wide because the
        // one temporary waypoint of the flight goes eight blocks sideways, and twelve high because a wall a unit can
        // simply fly over would make this test a test of nothing.
        for (int dx = 8; dx <= 16; dx++)
        {
            for (int dz = -10; dz <= 10; dz++)
            {
                for (int y = 1; y <= 12; y++)
                {
                    level.setBlockAndUpdate(start.offset(dx, y, dz), Blocks.STONE.defaultBlockState());
                }
            }
        }

        dragon.teleportTo(dragon.getX(), floorY + 10.0D, dragon.getZ());
        dragon.setFlying(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        dragon.landTo(new Vec3(start.getX() + 200.5D, floorY + 10.0D, start.getZ() + 0.5D),
                dragon.getCommandVersion());
        expect(helper, "the unit believes it is landing", dragon.isLanding());

        int[] ticks = {0};
        boolean[] gaveUp = {false};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] % 40 == 0)
            {
                SpiderQueenHungryCraft.LOGGER.info("[7F landing] tick={} gaveUp={} {}", ticks[0], gaveUp[0],
                        stateOf(dragon));
            }

            if (!gaveUp[0] && !dragon.isLanding())
            {
                // The descent gave itself up and returned to a controlled hover: nothing is hanging any more, and
                // nothing was claimed as a landing that never happened.
                expect(helper, "the abandoned descent leaves the unit hovering: " + stateOf(dragon),
                        dragon.isHovering());
                expect(helper, "and the abandoned descent is really over: " + stateOf(dragon), !dragon.isFlightActive());
                gaveUp[0] = true;
                return;
            }

            if (gaveUp[0] && hasLanded(dragon))
            {
                expect(helper, "and after the abandoned descent the unit still lands: " + stateOf(dragon),
                        Math.abs(dragon.getY() - floorY) < LANDING_TOLERANCE);
                helper.succeed();
            }

            if (ticks[0] > 700)
            {
                helper.fail("the unit neither gave the descent up nor landed: " + stateOf(dragon) + " gaveUp="
                        + gaveUp[0]);
            }
        });
    }

    /**
     * 7F test 5: a unit saved in the middle of a descent comes back able to land, instead of coming back as a LANDING
     * that has no place to go - the dead lock a reload used to leave behind.
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_dragon", timeoutTicks = 700)
    public static void aunitsavedwhilelandinglandsafterthereload(GameTestHelper helper)
    {
        ServerPlayer queen = FakePlayerFactory.getMinecraft(helper.getLevel());
        SoulfireDragonEntity dragon = unitInService(helper, queen);
        double floorY = dragon.getY();
        BlockPos start = helper.absolutePos(DRAGON_POS);

        carveAir(helper, start, 2, 2, 24);

        dragon.teleportTo(dragon.getX(), floorY + 16.0D, dragon.getZ());
        dragon.setFlying(true);
        dragon.setDeltaMovement(Vec3.ZERO);

        dragon.landTo(Vec3.atBottomCenterOf(start), dragon.getCommandVersion());

        expect(helper, "the descent is really running when the world is saved: " + stateOf(dragon),
                dragon.isLanding() && dragon.isFlightActive());

        CompoundTag tag = new CompoundTag();
        dragon.saveWithoutId(tag);

        SoulfireDragonEntity reborn = HungryCraftEntities.SOULFIRE_DRAGON.get().create(helper.getLevel());

        expect(helper, "the unit can be built again", reborn != null);

        reborn.load(tag);
        reborn.setUUID(UUID.randomUUID());
        helper.getLevel().addFreshEntity(reborn);

        expect(helper, "a descent without a target never survives a save: " + stateOf(reborn), !reborn.isLanding());
        expect(helper, "and no waypoint of the old world came back with it", !reborn.isFlightActive());
        expect(helper, "and the unit is still the flying one it was saved as", reborn.isFlying());
        expect(helper, "and gravity still agrees with that flight", reborn.isNoGravity());

        // The order that brings it home again, exactly as the sceptre would send it.
        reborn.holdAt(reborn.blockPosition());

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] > 600)
            {
                helper.fail("the reloaded unit never found its way down: " + stateOf(reborn));
            }

            if (hasLanded(reborn))
            {
                expect(helper, "and it lands after the reload: " + stateOf(reborn),
                        Math.abs(reborn.getY() - floorY) < LANDING_TOLERANCE);
                helper.succeed();
            }
        });
    }
}
