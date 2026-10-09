package spiderqueenhungrycraft.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEffects;

/**
 * The runtime proof of the light sensitivity of the Spider Queen (feature LIGHT-SENSITIVITY).
 *
 * <p>The whole ladder is walked synchronously: the countdown of {@link QueenLightSensitivity} grows by
 * {@link QueenPhysiology#CHECK_INTERVAL_TICKS} ticks per call of its update method, so the ten minutes of the
 * rule cost this test ten milliseconds instead of ten minutes. Nothing here reaches into the rule: every state
 * it reads is read through the vanilla effect API from a real player, and every transition is caused by the
 * very method the server tick loop calls in the game.
 *
 * <p>It asserts the five promises of the rule:
 * <ol>
 *     <li>the ladder itself - nothing for two minutes, then weakness I, weakness II, slowness, nausea and at
 *     last blindness, each stage at its own minute and not one minute earlier,</li>
 *     <li>the marker and every debuff of the light show no particles but do show their icon,</li>
 *     <li>the dim band of light levels 8..11 already counts as "out of the light": everything is gone and the
 *     countdown is reset,</li>
 *     <li>the potion of day vision both wipes a countdown that is already running and keeps the light from
 *     starting a new one, and the light starts over from zero the moment the potion runs out,</li>
 *     <li>a creative player is never touched by the light, and a weakness of somebody else is never removed
 *     and never pushed down to the level of this rule.</li>
 * </ol>
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server
 * never executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class QueenLightSensitivityGameTest
{
    /** A light level of the bright band, the light that hurts a queen. */
    private static final int BRIGHT_LIGHT = 15;

    /** A light level of the dim band in the middle, which already counts as "out of the light" for this rule. */
    private static final int DIM_LIGHT = 9;

    /** The whole test is synchronous, so a few ticks are plenty, and a slow run can not hide behind a timeout. */
    private static final int TIMEOUT_TICKS = 40;

    /** How long the potion of day vision of this test lasts. Longer than the walk through the ladder. */
    private static final int DAY_VISION_TICKS = 600;

    /** The duration and the level of the foreign weakness of a potion that this rule must never touch. */
    private static final int FOREIGN_DURATION_TICKS = 12000;
    private static final int FOREIGN_AMPLIFIER = 3;

    /** The amplifier reported for an effect a player does not carry at all. */
    private static final int NO_EFFECT = -1;

    private QueenLightSensitivityGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "light_sensitivity", timeoutTicks = TIMEOUT_TICKS)
    public static void lightSensitivity_walksItsLadderAndEndsOnEveryWayOut(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper.getLevel());

        SpiderQueenHungryCraft.LOGGER.info("[LightTest] weaknessI={}t weaknessII={}t slowness={}t nausea={}t "
                        + "blindness={}t, {} ticks per check",
                QueenLightSensitivity.WEAKNESS_TICKS, QueenLightSensitivity.STRONG_WEAKNESS_TICKS,
                QueenLightSensitivity.SLOWNESS_TICKS, QueenLightSensitivity.NAUSEA_TICKS,
                QueenLightSensitivity.BLINDNESS_TICKS, QueenPhysiology.CHECK_INTERVAL_TICKS);

        verifyGracePeriod(helper, queen);
        verifyLadder(helper, queen);
        verifyQuietEffects(helper, queen);
        verifyWaysOut(helper, queen);
        verifyCreative(helper, queen);
        verifyForeignEffects(helper, queen);

        SpiderQueenHungryCraft.LOGGER.info("[LightTest] RESULT every stage of the ladder, every way out of the "
                + "light and every signature of it checked | {}", state(queen));

        helper.succeed();
    }

    /** The first two minutes of the light cost nothing but the marker. */
    private static void verifyGracePeriod(GameTestHelper helper, ServerPlayer queen)
    {
        holdInLight(queen, QueenLightSensitivity.WEAKNESS_TICKS - QueenPhysiology.CHECK_INTERVAL_TICKS);

        expect(helper, queen, "the marker appears right away", hasOwnMarker(queen));
        expect(helper, queen, "the countdown runs", QueenLightSensitivity.countdownTicks(queen) > 0);
        expect(helper, queen, "weakness is still absent one check before two minutes",
                !queen.hasEffect(MobEffects.WEAKNESS));
        expect(helper, queen, "slowness is still absent one check before two minutes",
                !queen.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
    }

    /** The whole ladder, one stage at a time, with the next stage still missing at every step. */
    private static void verifyLadder(GameTestHelper helper, ServerPlayer queen)
    {
        holdInLight(queen, QueenLightSensitivity.WEAKNESS_TICKS);
        expect(helper, queen, "weakness I at two minutes", amplifier(queen, MobEffects.WEAKNESS) == 0);

        holdInLight(queen, QueenLightSensitivity.STRONG_WEAKNESS_TICKS - QueenPhysiology.CHECK_INTERVAL_TICKS);
        expect(helper, queen, "still weakness I one check before four minutes",
                amplifier(queen, MobEffects.WEAKNESS) == 0);

        holdInLight(queen, QueenLightSensitivity.STRONG_WEAKNESS_TICKS);
        expect(helper, queen, "weakness II at four minutes", amplifier(queen, MobEffects.WEAKNESS) == 1);
        expect(helper, queen, "no slowness yet at four minutes", !queen.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));

        holdInLight(queen, QueenLightSensitivity.SLOWNESS_TICKS);
        expect(helper, queen, "slowness I at five minutes", amplifier(queen, MobEffects.MOVEMENT_SLOWDOWN) == 0);
        expect(helper, queen, "no nausea yet at five minutes", !queen.hasEffect(MobEffects.CONFUSION));

        holdInLight(queen, QueenLightSensitivity.NAUSEA_TICKS);
        expect(helper, queen, "nausea at seven minutes", queen.hasEffect(MobEffects.CONFUSION));
        expect(helper, queen, "no blindness yet at seven minutes", !queen.hasEffect(MobEffects.BLINDNESS));

        holdInLight(queen, QueenLightSensitivity.BLINDNESS_TICKS);
        expect(helper, queen, "blindness at ten minutes", amplifier(queen, MobEffects.BLINDNESS) == 0);
        expect(helper, queen, "the weakness is still the second level at ten minutes",
                amplifier(queen, MobEffects.WEAKNESS) == 1);
        expect(helper, queen, "the slowness and the nausea survive to ten minutes",
                queen.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && queen.hasEffect(MobEffects.CONFUSION));
        expect(helper, queen, "the countdown reached the end of the ladder",
                QueenLightSensitivity.countdownTicks(queen) == QueenLightSensitivity.BLINDNESS_TICKS);
    }

    /** Everything the light gives shows no particles, but everything the light gives shows its icon. */
    private static void verifyQuietEffects(GameTestHelper helper, ServerPlayer queen)
    {
        expectQuiet(helper, queen, "the marker", marker());
        expectQuiet(helper, queen, "the weakness", MobEffects.WEAKNESS);
        expectQuiet(helper, queen, "the slowness", MobEffects.MOVEMENT_SLOWDOWN);
        expectQuiet(helper, queen, "the nausea", MobEffects.CONFUSION);
        expectQuiet(helper, queen, "the blindness", MobEffects.BLINDNESS);
    }

    /**
     * The two ways out of the light that a player triggers herself: stepping into a darker place and drinking
     * the potion of day vision. Both have to wipe everything and reset the countdown, and the potion has to
     * keep the light quiet for as long as it lasts.
     */
    private static void verifyWaysOut(GameTestHelper helper, ServerPlayer queen)
    {
        // The dim band is already out of the light, and there everything the light gave is taken away again.
        QueenLightSensitivity.update(queen, DIM_LIGHT);

        expect(helper, queen, "the dim band takes the marker away", !queen.hasEffect(marker()));
        expect(helper, queen, "the dim band takes every debuff away",
                !queen.hasEffect(MobEffects.WEAKNESS) && !queen.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && !queen.hasEffect(MobEffects.CONFUSION) && !queen.hasEffect(MobEffects.BLINDNESS));
        expect(helper, queen, "the dim band resets the countdown",
                QueenLightSensitivity.countdownTicks(queen) == 0);

        // The same when the potion is drunk while the countdown is already running.
        holdInLight(queen, QueenLightSensitivity.STRONG_WEAKNESS_TICKS);
        expect(helper, queen, "the countdown runs again after the reset",
                QueenLightSensitivity.countdownTicks(queen) == QueenLightSensitivity.STRONG_WEAKNESS_TICKS);

        queen.addEffect(new MobEffectInstance(HungryCraftEffects.DAY_VISION.get(), DAY_VISION_TICKS));
        QueenLightSensitivity.update(queen, BRIGHT_LIGHT);

        expect(helper, queen, "a drunk potion of day vision wipes the marker", !queen.hasEffect(marker()));
        expect(helper, queen, "a drunk potion of day vision wipes the weakness",
                !queen.hasEffect(MobEffects.WEAKNESS));
        expect(helper, queen, "a drunk potion of day vision resets the countdown",
                QueenLightSensitivity.countdownTicks(queen) == 0);

        // While it lasts the bright light never starts a new countdown, not even after ten minutes of it.
        check(queen, BRIGHT_LIGHT, QueenLightSensitivity.BLINDNESS_TICKS / QueenPhysiology.CHECK_INTERVAL_TICKS);

        expect(helper, queen, "no marker while the potion of day vision lasts", !queen.hasEffect(marker()));
        expect(helper, queen, "no countdown while the potion of day vision lasts",
                QueenLightSensitivity.countdownTicks(queen) == 0);
        expect(helper, queen, "no debuff while the potion of day vision lasts",
                !queen.hasEffect(MobEffects.WEAKNESS));

        // And the moment it runs out the light starts over from zero instead of where it was.
        queen.removeEffect(HungryCraftEffects.DAY_VISION.get());
        QueenLightSensitivity.update(queen, BRIGHT_LIGHT);

        expect(helper, queen, "the marker is back the moment the potion is gone", hasOwnMarker(queen));
        expect(helper, queen, "the countdown starts over from zero",
                QueenLightSensitivity.countdownTicks(queen) == QueenPhysiology.CHECK_INTERVAL_TICKS);
        expect(helper, queen, "the light starts over without a debuff", !queen.hasEffect(MobEffects.WEAKNESS));

        QueenLightSensitivity.clear(queen);
    }

    /**
     * A creative player is out of the game and never gains anything from the light, and a queen who switches
     * to creative in the middle of the ladder is cleaned up on the spot.
     *
     * <p>A {@code ServerPlayer} reads its game mode and not its abilities, so the test switches the game mode
     * itself - exactly what the vanilla {@code /gamemode} command ends in.
     */
    private static void verifyCreative(GameTestHelper helper, ServerPlayer queen)
    {
        queen.setGameMode(GameType.CREATIVE);
        check(queen, BRIGHT_LIGHT, 10);

        expect(helper, queen, "a creative player never gains the marker", !queen.hasEffect(marker()));
        expect(helper, queen, "a creative player never gains a countdown",
                QueenLightSensitivity.countdownTicks(queen) == 0);
        expect(helper, queen, "a creative player gains no debuff of the light",
                !queen.hasEffect(MobEffects.WEAKNESS));

        queen.setGameMode(GameType.SURVIVAL);
        holdInLight(queen, QueenLightSensitivity.BLINDNESS_TICKS);
        expect(helper, queen, "the ladder runs for a survival player", queen.hasEffect(MobEffects.BLINDNESS));

        queen.setGameMode(GameType.CREATIVE);
        QueenLightSensitivity.update(queen, BRIGHT_LIGHT);

        expect(helper, queen, "switching to creative wipes the marker and every debuff",
                !queen.hasEffect(marker()) && !queen.hasEffect(MobEffects.WEAKNESS)
                        && !queen.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                        && !queen.hasEffect(MobEffects.CONFUSION) && !queen.hasEffect(MobEffects.BLINDNESS));
        expect(helper, queen, "switching to creative resets the countdown",
                QueenLightSensitivity.countdownTicks(queen) == 0);

        queen.setGameMode(GameType.SURVIVAL);
    }

    /**
     * The effect of somebody else is sacrosanct: a weakness of a potion is not ours, so neither a way out of
     * the light nor a stage of the ladder may remove it or push it down to the level of this rule.
     */
    private static void verifyForeignEffects(GameTestHelper helper, ServerPlayer queen)
    {
        queen.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FOREIGN_DURATION_TICKS, FOREIGN_AMPLIFIER,
                false, true, true));

        QueenLightSensitivity.clear(queen);
        expect(helper, queen, "a weakness of a potion survives the clear of the light",
                amplifier(queen, MobEffects.WEAKNESS) == FOREIGN_AMPLIFIER);

        holdInLight(queen, QueenLightSensitivity.BLINDNESS_TICKS);

        expect(helper, queen, "a weakness of a potion is never pushed down to the level of the light",
                amplifier(queen, MobEffects.WEAKNESS) == FOREIGN_AMPLIFIER);
        expect(helper, queen, "the particles of a weakness of a potion are left alone",
                isVisible(queen, MobEffects.WEAKNESS));
        expect(helper, queen, "the light still reaches a player who carries a foreign effect",
                queen.hasEffect(MobEffects.BLINDNESS));

        QueenLightSensitivity.clear(queen);
        expect(helper, queen, "the clear of the light leaves the potion effect alone",
                amplifier(queen, MobEffects.WEAKNESS) == FOREIGN_AMPLIFIER);

        queen.removeEffect(MobEffects.WEAKNESS);
    }

    /**
     * Puts a Forge fake player into the level: a real player entity with the real capability, without the login
     * a headless game test server can not perform.
     */
    private static ServerPlayer placeQueen(ServerLevel level)
    {
        ServerPlayer queen = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "light_test_queen"));

        if (!level.players().contains(queen))
        {
            level.addNewPlayer(queen);
        }

        return queen;
    }

    /**
     * Fast-forwards the countdown of that queen: it runs the production check of the server tick loop until she
     * has stood in the bright light for that many ticks. The guard is only there to make an endless loop
     * impossible - a rule that never counts up must fail the test, not hang it.
     */
    private static void holdInLight(ServerPlayer queen, int ticks)
    {
        int guard = ticks / QueenPhysiology.CHECK_INTERVAL_TICKS + 1;

        while (QueenLightSensitivity.countdownTicks(queen) < ticks && guard-- > 0)
        {
            QueenLightSensitivity.update(queen, BRIGHT_LIGHT);
        }
    }

    /** Runs that many checks of the production loop, whatever the rule decides to do with them. */
    private static void check(ServerPlayer queen, int lightLevel, int times)
    {
        for (int i = 0; i < times; i++)
        {
            QueenLightSensitivity.update(queen, lightLevel);
        }
    }

    /** Fails the test with the whole state of the queen when that promise of the rule does not hold. */
    private static void expect(GameTestHelper helper, ServerPlayer queen, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("LIGHT-SENSITIVITY: " + what + " | state: " + state(queen));
        }
    }

    /** Requirement: everything the light gives shows no particles, but everything the light gives shows its icon. */
    private static void expectQuiet(GameTestHelper helper, ServerPlayer queen, String what, MobEffect effect)
    {
        MobEffectInstance instance = queen.getEffect(effect);

        expect(helper, queen, what + " of the light is on the player", instance != null);

        if (instance != null)
        {
            expect(helper, queen, what + " of the light shows no particles", !instance.isVisible());
            expect(helper, queen, what + " of the light still shows its icon", instance.showIcon());
        }
    }

    /** @return true while that player carries the marker of the light in the exact shape this rule hands it out. */
    private static boolean hasOwnMarker(ServerPlayer queen)
    {
        MobEffectInstance instance = queen.getEffect(marker());

        return instance != null && !instance.isAmbient() && !instance.isVisible() && instance.showIcon();
    }

    private static MobEffect marker()
    {
        return HungryCraftEffects.LIGHT_SENSITIVITY.get();
    }

    /** @return the amplifier of that effect on that player, or {@link #NO_EFFECT} while she does not carry it. */
    private static int amplifier(ServerPlayer queen, MobEffect effect)
    {
        MobEffectInstance instance = queen.getEffect(effect);

        return instance == null ? NO_EFFECT : instance.getAmplifier();
    }

    /** @return true while that effect shows particles on that player, the mark of an effect of somebody else. */
    private static boolean isVisible(ServerPlayer queen, MobEffect effect)
    {
        MobEffectInstance instance = queen.getEffect(effect);

        return instance != null && instance.isVisible();
    }

    /** @return every effect this rule can hand out in one line, so a failure carries its own evidence. */
    private static String state(ServerPlayer queen)
    {
        return "countdown=" + QueenLightSensitivity.countdownTicks(queen)
                + " marker=" + describe(queen, marker())
                + " weakness=" + describe(queen, MobEffects.WEAKNESS)
                + " slowness=" + describe(queen, MobEffects.MOVEMENT_SLOWDOWN)
                + " nausea=" + describe(queen, MobEffects.CONFUSION)
                + " blindness=" + describe(queen, MobEffects.BLINDNESS);
    }

    private static String describe(ServerPlayer queen, MobEffect effect)
    {
        MobEffectInstance instance = queen.getEffect(effect);

        if (instance == null)
        {
            return "none";
        }

        return "amplifier=" + instance.getAmplifier() + " duration=" + instance.getDuration()
                + " particles=" + instance.isVisible() + " icon=" + instance.showIcon();
    }
}


