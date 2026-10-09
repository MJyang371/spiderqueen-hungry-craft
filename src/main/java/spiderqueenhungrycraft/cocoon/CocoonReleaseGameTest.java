package spiderqueenhungrycraft.cocoon;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The runtime proof of feature COCOON-RELEASE: a pair of shears cuts a cocoon open and the prey comes back as
 * the individual that was wrapped up.
 *
 * <p>The test dresses one zombie up as thoroughly as the game allows - a custom name, a wound, absorption
 * hearts, a potion effect, a helmet, a scoreboard tag, a few seconds of fire and half a lung of air - wraps it
 * up with the production capture, throws the original away and then cuts the cocoon open with the production
 * click of a player who is deliberately <em>not</em> the Spider Queen.
 *
 * <p>What it asserts is exactly what a player would notice: the cocoon is gone, the shears paid for the cut,
 * and the creature that stands there is the same one - every single field it was given is back - while its
 * identity is deliberately fresh, because a cocoon item can be duplicated and one identity must never walk a
 * world twice.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server
 * never executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class CocoonReleaseGameTest
{
    /** The whole click is synchronous, so the test needs a few ticks and nothing more. */
    private static final int TIMEOUT_TICKS = 40;

    /** How the prey names itself, so the released creature can be told apart from a fresh spawn of its kind. */
    private static final String PREY_NAME = "Cocoon Release Test Prey";

    /** A scoreboard tag of the prey, the mark of a specific individual. */
    private static final String PREY_TAG = "cocoon_release_test_prey";

    /** The state that has to survive the silk. */
    private static final float PREY_HEALTH = 7.5F;
    private static final float PREY_ABSORPTION = 4.0F;
    private static final int PREY_FIRE_TICKS = 100;
    private static final int PREY_AIR = 200;
    private static final int PREY_EFFECT_AMPLIFIER = 1;
    private static final int PREY_EFFECT_TICKS = 1200;

    /** The hunger the capture is told to read off the prey. */
    private static final int PREY_NUTRITION = 5;

    /** How far around the cocoon the released prey is looked for. */
    private static final double SEARCH_RADIUS = 4.0D;

    /** The damage a cut costs, see {@code CocoonEntity#CUT_OPEN_SHEARS_DAMAGE}. */
    private static final int CUT_OPEN_SHEARS_DAMAGE = 1;

    private CocoonReleaseGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "cocoon_release", timeoutTicks = TIMEOUT_TICKS)
    public static void shearsCutACocoonOpenAndThePreyComesBackAsItself(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = placePlayer(level);

        // The requirement: any player may cut a cocoon open, queen or not. This one is deliberately not a queen.
        QueenDataCapability.setQueen(player, false);

        Zombie prey = buildPrey(level, player);
        CocoonData data = CocoonData.capture(prey, PREY_NUTRITION);
        UUID preyId = prey.getUUID();
        prey.discard();

        CocoonEntity cocoon = placeCocoon(level, player, data);

        SpiderQueenHungryCraft.LOGGER.info("[CocoonRelease] cut open a cocoon of {} holding {} at {}",
                data.getEntityTypeId(), PREY_NAME, cocoon.position());

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        InteractionResult result = cocoon.interact(player, InteractionHand.MAIN_HAND);

        expect(helper, "the click with the shears was taken by the cocoon", result.consumesAction());
        expect(helper, "the cut destroyed the cocoon", cocoon.isRemoved());

        ItemStack shears = player.getItemInHand(InteractionHand.MAIN_HAND);
        expect(helper, "the shears paid " + CUT_OPEN_SHEARS_DAMAGE + " point of durability, not "
                + shears.getDamageValue(), shears.getDamageValue() == CUT_OPEN_SHEARS_DAMAGE);

        assertPrey(helper, released(level, cocoon), preyId);
    }

    /** Every field the prey was given has to be back, and only its identity is allowed to be new. */
    private static void assertPrey(GameTestHelper helper, Zombie released, UUID preyId)
    {
        expect(helper, "the prey stands in the world again", released != null);

        if (released == null)
        {
            return;
        }

        MobEffectInstance effect = released.getEffect(MobEffects.MOVEMENT_SPEED);

        expect(helper, "the name of the prey survived, name=" + released.getCustomName(),
                released.getCustomName() != null && PREY_NAME.equals(released.getCustomName().getString()));
        expect(helper, "the wound of the prey survived, health=" + released.getHealth(),
                Math.abs(released.getHealth() - PREY_HEALTH) < 0.01F);
        expect(helper, "the absorption hearts survived, absorption=" + released.getAbsorptionAmount(),
                Math.abs(released.getAbsorptionAmount() - PREY_ABSORPTION) < 0.01F);
        expect(helper, "the potion effect survived, amplifier="
                        + (effect == null ? "none" : effect.getAmplifier()),
                effect != null && effect.getAmplifier() == PREY_EFFECT_AMPLIFIER);
        expect(helper, "the helmet survived, helmet=" + released.getItemBySlot(EquipmentSlot.HEAD),
                released.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET));
        expect(helper, "the scoreboard tag survived, tags=" + released.getTags(),
                released.getTags().contains(PREY_TAG));
        expect(helper, "the fire of the prey survived, fire=" + released.getRemainingFireTicks(),
                released.getRemainingFireTicks() == PREY_FIRE_TICKS);
        expect(helper, "the breath of the prey survived, air=" + released.getAirSupply(),
                released.getAirSupply() == PREY_AIR);
        expect(helper, "the prey came back as a new identity on purpose, uuid=" + released.getUUID(),
                !released.getUUID().equals(preyId));

        SpiderQueenHungryCraft.LOGGER.info("[CocoonRelease] RESULT released uuid={} name={} health={} "
                        + "absorption={} effect={} helmet={} tags={} fire={} air={}",
                released.getUUID(), released.getCustomName(), released.getHealth(), released.getAbsorptionAmount(),
                effect == null ? "none" : effect.getAmplifier(), released.getItemBySlot(EquipmentSlot.HEAD),
                released.getTags(), released.getRemainingFireTicks(), released.getAirSupply());

        helper.succeed();
    }

    /** @return the dressed up prey of this test. It is never added to the level, only wrapped up. */
    private static Zombie buildPrey(ServerLevel level, ServerPlayer player)
    {
        Zombie prey = EntityType.ZOMBIE.create(level);

        if (prey == null)
        {
            throw new IllegalStateException("the game refused to build the zombie of the cocoon release test");
        }

        prey.moveTo(player.getX() + 1.0D, player.getY(), player.getZ(), player.getYRot(), 0.0F);
        prey.setCustomName(Component.literal(PREY_NAME));
        prey.setHealth(PREY_HEALTH);
        prey.setAbsorptionAmount(PREY_ABSORPTION);
        prey.setRemainingFireTicks(PREY_FIRE_TICKS);
        prey.setAirSupply(PREY_AIR);
        prey.addTag(PREY_TAG);
        prey.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        prey.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, PREY_EFFECT_TICKS, PREY_EFFECT_AMPLIFIER));

        return prey;
    }

    /** @return the cocoon around that prey, standing exactly where the player is. */
    private static CocoonEntity placeCocoon(ServerLevel level, ServerPlayer player, CocoonData data)
    {
        CocoonEntity cocoon = new CocoonEntity(level);
        cocoon.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
        cocoon.setCocoonData(data);
        level.addFreshEntity(cocoon);

        return cocoon;
    }

    /** @return the released zombie around that cocoon, or null when nothing stands there. */
    @Nullable
    private static Zombie released(ServerLevel level, CocoonEntity cocoon)
    {
        List<Zombie> found = level.getEntitiesOfClass(Zombie.class,
                new AABB(cocoon.blockPosition()).inflate(SEARCH_RADIUS));

        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * Puts a Forge fake player on the ground: a real player entity with the real capability, without the login
     * a headless game test server can not perform.
     */
    private static ServerPlayer placePlayer(ServerLevel level)
    {
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "cocoon_test_player"));

        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition());
        player.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);

        if (!level.players().contains(player))
        {
            level.addNewPlayer(player);
        }

        return player;
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("COCOON-RELEASE: " + what);
        }
    }
}
