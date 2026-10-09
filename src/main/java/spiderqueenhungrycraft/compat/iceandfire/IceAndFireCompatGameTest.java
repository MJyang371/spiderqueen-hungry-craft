package spiderqueenhungrycraft.compat.iceandfire;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.S2CNecromancyStatePacket;

/**
 * The automatic half of the Ice and Fire compatibility (Necromancy IV prototype 1).
 *
 * <p>This workspace does <b>not</b> run Ice and Fire and must never be made to, so what can be proven here is the
 * other half of the promise - the half that matters to everybody who does not use that mod:
 *
 * <ul>
 * <li>the compatibility says so, plainly, instead of pretending ({@link IceAndFireSupport#present()}),</li>
 * <li>its classes are really not on the runtime classpath of this run, so the tests below are not proving anything
 * about a mod that happens to be there,</li>
 * <li>every entrance a command or a future key binding can call answers with a sentence when the other mod is
 * absent - never with a {@code NoClassDefFoundError}, never with a {@code ClassNotFoundException} and never with a
 * crash,</li>
 * <li>and the one part of the compatibility that is pure vanilla, the necromancy marker, saves, loads and clears the
 * way any other persistent data of an entity does.</li>
 * </ul>
 *
 * <p>The other half - a real bone dragon waking up and going back to sleep - happens in the external Forge instance
 * that has Ice and Fire installed, through {@code /hungrycraft debug iaf ...} and the probe plan of the round.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class IceAndFireCompatGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** The class the whole compatibility is built on, named as a string so that this test file needs no Ice and Fire. */
    private static final String DRAGON_CLASS = "com.github.alexthe666.iceandfire.entity.EntityDragonBase";

    private IceAndFireCompatGameTest()
    {
    }

    /**
     * <b>The compatibility knows that Ice and Fire is not here.</b>
     *
     * <p>And, just as important, the class this test would otherwise be proof about is really not on the classpath:
     * if it were, the safe no-op tests below would be worthless.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void thecompatibilityknowsthaticeandfireisnothere(GameTestHelper helper)
    {
        expect(helper, "the probe must report Ice and Fire as absent", !IceAndFireSupport.present());
        expect(helper, "Ice and Fire must really be absent from this runtime", !classIsPresent(DRAGON_CLASS));

        helper.succeed();
    }

    /**
     * <b>Every entrance of the compatibility is a safe no-op without Ice and Fire.</b>
     *
     * <p>Each of the five is called exactly as the debug commands call it, with a real player, and each of them has to
     * answer with the sentence {@link IceAndFireSupport#NOT_INSTALLED} instead of doing anything or throwing anything.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void everyentranceissafe(GameTestHelper helper)
    {
        ServerPlayer queen = fakePlayer(helper, "ice_fire_compat_queen");

        expect(helper, "spawn is refused with a sentence",
                refused(IceAndFireSupport.spawnBoneDragon(queen, "fire", 75)));
        expect(helper, "inspect is refused with a sentence", refused(IceAndFireSupport.inspect(queen)));
        expect(helper, "revive is refused with a sentence", refused(IceAndFireSupport.revive(queen)));
        expect(helper, "restore is refused with a sentence", refused(IceAndFireSupport.restore(queen)));
        expect(helper, "restore_all is refused with a sentence", refused(IceAndFireSupport.restoreAll(queen)));

        helper.succeed();
    }

    /**
     * <b>The necromancy marker is ordinary persistent data of an entity.</b>
     *
     * <p>It is the one part of this compatibility that compiles, loads and runs without Ice and Fire, and it is the
     * part the later rounds will build on, so it is tested on its own: it is written, read back, described, written
     * into the NBT of an entity, loaded into a second entity from that NBT, and cleared again.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void themarkerroundtripslikeanyotherforgedata(GameTestHelper helper)
    {
        LivingEntity cow = cow(helper, 0);
        UUID owner = UUID.randomUUID();

        expect(helper, "an untouched entity carries no marker", !NecromancyMarker.isRevived(cow));
        expect(helper, "and nothing describes it", IceAndFireSupport.describeMarker(cow) == null);

        NecromancyMarker.mark(cow, owner, 2, 17.5F);

        expect(helper, "the marker is written", NecromancyMarker.isRevived(cow));
        expect(helper, "the owner is written", NecromancyMarker.owner(cow).equals(Optional.of(owner)));
        expect(helper, "the saved command is written", NecromancyMarker.savedCommand(cow, -1) == 2);
        expect(helper, "the saved health is written", NecromancyMarker.savedHealth(cow, -1.0F) == 17.5F);
        expect(helper, "the facade can describe it", IceAndFireSupport.describeMarker(cow) != null);

        CompoundTag saved = new CompoundTag();
        cow.saveWithoutId(saved);

        LivingEntity reloaded = cow(helper, 2);
        reloaded.load(saved);

        expect(helper, "the marker survives a save and a load", NecromancyMarker.isRevived(reloaded));
        expect(helper, "and so does the owner", NecromancyMarker.owner(reloaded).equals(Optional.of(owner)));
        expect(helper, "and so does the saved command", NecromancyMarker.savedCommand(reloaded, -1) == 2);

        NecromancyMarker.clear(cow);

        expect(helper, "clearing removes the marker", !NecromancyMarker.isRevived(cow));
        expect(helper, "a fallback is used when there is no marker", NecromancyMarker.savedCommand(cow, 7) == 7);

        helper.succeed();
    }

    /**
     * <b>A visual bolt hurts nobody and burns nothing - and the test proves it has to, by striking a real one too.</b>
     *
     * <p>Two cows, eight blocks apart (a bolt reaches three blocks around itself and six above, so neither can reach
     * the other), one bolt on each of them:
     *
     * <ul>
     * <li>the visual bolt of this mod, which is the one {@code lightning_revive} strikes: its cow must be completely
     * unhurt, it must have set no block on fire and it must have hit nobody,</li>
     * <li>and a plain bolt of the game as the control, whose cow <b>must</b> be hurt - without that half, "nothing
     * happened" on the other side would prove nothing at all.</li>
     * </ul>
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = 120)
    public static void thevisualboltdealsnodamageandsetnofire(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        Cow spared = cowInLevel(helper, 0, 0);
        Cow struck = cowInLevel(helper, 8, 0);
        float sparedHealth = spared.getHealth();
        float struckHealth = struck.getHealth();

        LightningBolt visual = bolt(level, spared.position(), true);
        LightningBolt real = bolt(level, struck.position(), false);

        if (visual == null || real == null)
        {
            throw new IllegalStateException("the game refused to build a lightning bolt");
        }

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() ->
                {
                    expect(helper, "the visual bolt left the cow beside it completely unhurt",
                            spared.getHealth() == sparedHealth);
                    expect(helper, "the visual bolt set no block on fire", visual.getBlocksSetOnFire() == 0);
                    expect(helper, "the visual bolt hit no being at all", visual.getHitEntities().count() == 0L);
                    expect(helper, "the control bolt of the game did hurt its own cow, so the test above means something",
                            struck.getHealth() < struckHealth);
                })
                .thenSucceed();
    }

    /**
     * <b>The client mirror follows exactly what the server tells it.</b>
     *
     * <p>It is the client's copy of the necromancy marker - the marker itself can not travel, because Forge never sends
     * persistent data - and it is what the rendering thread of a client asks. It is tested here, in a plain server
     * game test, because the class is deliberately free of any client only type.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void theclientmirrorfollowswhattheservertellsit(GameTestHelper helper)
    {
        ClientNecromancyState.clear("test start");

        expect(helper, "an empty mirror knows nothing", !ClientNecromancyState.isRevived(4242));

        ClientNecromancyState.set(4242, true);
        expect(helper, "the server can put a dragon in", ClientNecromancyState.isRevived(4242));
        expect(helper, "and the mirror counts it", ClientNecromancyState.size() == 1);

        ClientNecromancyState.set(4242, false);
        expect(helper, "and take it out again", !ClientNecromancyState.isRevived(4242));
        expect(helper, "with the size following", ClientNecromancyState.size() == 0);

        for (int index = 0; index < 10; index++)
        {
            ClientNecromancyState.set(5000 + index, true);
        }

        expect(helper, "many dragons fit at once", ClientNecromancyState.size() == 10);
        expect(helper, "and each of them is known by its own id", ClientNecromancyState.isRevived(5009));

        ClientNecromancyState.clear("test end");
        expect(helper, "clearing a world forgets every dragon", ClientNecromancyState.size() == 0);

        helper.succeed();
    }

    /**
     * <b>The mirror packet carries the id and the flag, and nothing else.</b>
     *
     * <p>It is the one packet of this compatibility, so it is worth one test of its own: what a server writes must be
     * exactly what a client reads back.
     */
    @GameTest(template = "humanspawn_flat", batch = "ice_fire_compat", timeoutTicks = TIMEOUT_TICKS)
    public static void themirrorpacketroundtrips(GameTestHelper helper)
    {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new S2CNecromancyStatePacket(123456, true).encode(buffer);
        new S2CNecromancyStatePacket(7, false).encode(buffer);

        S2CNecromancyStatePacket first = new S2CNecromancyStatePacket(buffer);
        S2CNecromancyStatePacket second = new S2CNecromancyStatePacket(buffer);

        expect(helper, "the entity id survives", first.entityId() == 123456);
        expect(helper, "the flag survives", first.revived());
        expect(helper, "and the negative answer of the next one as well",
                second.entityId() == 7 && !second.revived());

        helper.succeed();
    }

    /** @return true when that probe answer is the plain sentence of a missing mod, with nothing else in it. */
    private static boolean refused(ProbeReport report)
    {
        return !report.ok() && report.lines().size() == 1 && report.lines().get(0).equals(IceAndFireSupport.NOT_INSTALLED);
    }

    /** @return true when that class is on the classpath of this run. */
    private static boolean classIsPresent(String className)
    {
        try
        {
            Class.forName(className, false, IceAndFireCompatGameTest.class.getClassLoader());
            return true;
        }
        catch (Throwable absent)
        {
            return false;
        }
    }

    /** @return one cow of this test, deliberately never put into the level. */
    private static LivingEntity cow(GameTestHelper helper, int offsetX)
    {
        ServerLevel level = helper.getLevel();
        LivingEntity cow = EntityType.COW.create(level);

        if (cow == null)
        {
            throw new IllegalStateException("the game refused to build a cow");
        }

        BlockPos pos = helper.absolutePos(new BlockPos(offsetX, 1, 0));
        cow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);

        return cow;
    }

    /** @return one cow of this test that really is part of the level, so that a bolt can find it. */
    private static Cow cowInLevel(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        Cow cow = EntityType.COW.create(level);

        if (cow == null)
        {
            throw new IllegalStateException("the game refused to build a cow");
        }

        BlockPos pos = helper.absolutePos(new BlockPos(offsetX, 1, offsetZ));
        cow.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(cow);

        return cow;
    }

    /** @return one bolt of the game at that place, added to the level, or null when none could be built. */
    @Nullable
    private static LightningBolt bolt(ServerLevel level, Vec3 where, boolean visualOnly)
    {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);

        if (bolt == null)
        {
            return null;
        }

        bolt.moveTo(where.x, where.y, where.z);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);

        return bolt;
    }

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

    /** Fails the test on the promise that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("ICE-FIRE-COMPAT: " + what);
        }
    }
}
