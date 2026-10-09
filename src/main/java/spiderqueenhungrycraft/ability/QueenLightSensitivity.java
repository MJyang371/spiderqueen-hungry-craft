package spiderqueenhungrycraft.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.registry.HungryCraftEffects;

/**
 * The light sensitivity of the Spider Queen (feature LIGHT-SENSITIVITY).
 *
 * <p>The old rule was brutal and instant: the moment the queen stood in a bright light she was blinded. This
 * rule replaces it with a slow, readable price. Standing in the light of the world with a light level of
 * {@link QueenPhysiology#BRIGHTEST_LIGHT_LEVEL} or more hands her the visible marker effect
 * {@code spiderqueen_hungrycraft:light_sensitivity} and starts a countdown. The countdown is the whole rule:
 *
 * <ul>
 *     <li>below two minutes: nothing at all, the sun simply does not suit her,</li>
 *     <li>two minutes: weakness I,</li>
 *     <li>four minutes: weakness II instead of weakness I,</li>
 *     <li>five minutes: slowness I on top of it,</li>
 *     <li>seven minutes: nausea on top,</li>
 *     <li>ten minutes: blindness on top, the full penalty of a long day.</li>
 * </ul>
 *
 * <p>The countdown ends in exactly the ways the design asks for, and they all clear everything in one single
 * step, so there is never a half state where the marker is gone but its debuffs are not: the queen steps out
 * of the bright light (any darker level, including the dim band in the middle), she drinks the potion of day
 * vision, she stops being the Spider Queen, she plays in creative or spectator mode, or she leaves the world
 * and the tick loop of {@link QueenPhysiology} stops looking at her.
 *
 * <p>Milk is deliberately not a way out: vanilla takes the marker away, the next check hands it back, and the
 * countdown is untouched by that because the queen never left the light.
 *
 * <p>Only the instances this class handed out itself are ever removed by it, and they are recognised by their
 * signature: no ambient flag, no particles, icon shown. An instance that shows particles belongs to somebody
 * else - a potion, a command, a warden or another mod - and that instance is not only never removed, it is not
 * even touched: the ladder waits and lets it run out on its own. That is deliberately stricter than "do not
 * remove it", because the vanilla merge copies the flags of the incoming instance over the running one, so a
 * single merge would take the particles of the foreign effect away and make it look like one of ours for the
 * rest of its life. The hidden effects of the dark rule of {@link QueenPhysiology} never show an icon and are
 * never mistaken for one of these either.
 *
 * <p>Server side and main thread only. The countdown is a plain map of "player uuid to ticks spent in the
 * light" that holds one int per queen who is currently standing in the light; the entry of every other player
 * is dropped the moment she is looked at.
 */
public final class QueenLightSensitivity
{
    private static final int MINUTE_TICKS = 20 * 60;

    /** The grace period: this long in the light still costs nothing. */
    public static final int WEAKNESS_TICKS = 2 * MINUTE_TICKS;

    /** From here the weakness is the second level. */
    public static final int STRONG_WEAKNESS_TICKS = 4 * MINUTE_TICKS;

    /** From here the queen is slowed as well. */
    public static final int SLOWNESS_TICKS = 5 * MINUTE_TICKS;

    /** From here the queen is nauseous as well. */
    public static final int NAUSEA_TICKS = 7 * MINUTE_TICKS;

    /** From here the queen is blind as well, the end of the ladder. */
    public static final int BLINDNESS_TICKS = 10 * MINUTE_TICKS;

    /**
     * The marker stays on for a minute and is refreshed a fifth of a minute before it would run out, so the
     * queen sees one stable "light sensitivity" on her HUD for as long as she stands in the light and it is
     * gone within a second of her leaving it.
     */
    private static final int MARKER_DURATION_TICKS = 1200;

    /** Refresh window of that marker. */
    private static final int MARKER_REFRESH_BELOW_TICKS = 1000;

    /** The debuffs last half a minute each and are refreshed well before that, so they never flicker. */
    private static final int DEBUFF_DURATION_TICKS = 600;

    /** Refresh window of one debuff. */
    private static final int DEBUFF_REFRESH_BELOW_TICKS = 400;

    /** Value used internally for "the countdown has not reached this debuff yet". */
    private static final int NO_DEBUFF = -1;

    /** Ticks this queen has already spent in the bright light, one int per queen who is in it. */
    private static final Map<UUID, Integer> COUNTDOWNS = new HashMap<>();

    private QueenLightSensitivity()
    {
    }

    /**
     * The whole rule, called by the tick loop of {@link QueenPhysiology} once every
     * {@link QueenPhysiology#CHECK_INTERVAL_TICKS} ticks with the light the queen stands in.
     *
     * <p>Everything that is not "the Spider Queen standing in the bright light without the potion of day
     * vision" clears the marker, every debuff it caused and the countdown.
     */
    public static void update(Player player, int lightLevel)
    {
        if (!isBrightEnough(lightLevel) || hasDayVision(player) || isProtected(player))
        {
            clear(player);
            return;
        }

        int elapsed = advance(player);
        refreshMarker(player);
        applyDebuffs(player, elapsed);
    }

    /**
     * Removes the marker of this class, every debuff this class handed out, and resets the countdown. A
     * weakness, a slowness, a nausea or a blindness of any other source is deliberately left alone.
     */
    public static void clear(Player player)
    {
        COUNTDOWNS.remove(player.getUUID());

        removeOwnEffect(player, HungryCraftEffects.LIGHT_SENSITIVITY.get());
        removeOwnEffect(player, MobEffects.WEAKNESS);
        removeOwnEffect(player, MobEffects.MOVEMENT_SLOWDOWN);
        removeOwnEffect(player, MobEffects.CONFUSION);
        removeOwnEffect(player, MobEffects.BLINDNESS);
    }

    /** @return true while that light level is bright enough to hurt the eyes of a queen. */
    public static boolean isBrightEnough(int lightLevel)
    {
        return lightLevel >= QueenPhysiology.BRIGHTEST_LIGHT_LEVEL;
    }

    /** @return true while that player carries the day vision of the mod, queen or not. */
    public static boolean hasDayVision(Player player)
    {
        return player.hasEffect(HungryCraftEffects.DAY_VISION.get());
    }

    /**
     * @return true while that player is out of the game itself. A creative or spectating player is a
     *         moderator, not a spider, so the light never touches her.
     */
    public static boolean isProtected(Player player)
    {
        return player.isCreative() || player.isSpectator();
    }

    /**
     * The weakness of a countdown, as the one number it is: the level of the effect, or {@link #NO_DEBUFF}
     * while the grace period is still running.
     *
     * <p>It exists so the whole ladder can be read in one place - by a probe, by a test or by a reader -
     * instead of being spread over the tick path.
     */
    public static int weaknessAmplifier(int elapsedTicks)
    {
        if (elapsedTicks >= STRONG_WEAKNESS_TICKS)
        {
            return 1;
        }
        return elapsedTicks >= WEAKNESS_TICKS ? 0 : NO_DEBUFF;
    }

    /** @return the ticks that player has spent in the bright light, 0 while no countdown is running. */
    public static int countdownTicks(Player player)
    {
        return COUNTDOWNS.getOrDefault(player.getUUID(), 0);
    }

    /** Counts this one check into the countdown of that queen. */
    private static int advance(Player player)
    {
        int elapsed = countdownTicks(player) + QueenPhysiology.CHECK_INTERVAL_TICKS;
        COUNTDOWNS.put(player.getUUID(), elapsed);
        return elapsed;
    }

    private static void refreshMarker(Player player)
    {
        MobEffect effect = HungryCraftEffects.LIGHT_SENSITIVITY.get();
        MobEffectInstance current = player.getEffect(effect);

        if (current != null && !isOwnEffect(current, effect))
        {
            // Somebody else put this effect on the queen. It is left exactly as it is, see the class comment.
            return;
        }

        if (current != null && !current.endsWithin(MARKER_REFRESH_BELOW_TICKS))
        {
            return;
        }

        // ambient = false, particles = false, HUD icon = true: the light is silent but it is not hidden.
        player.addEffect(new MobEffectInstance(effect, MARKER_DURATION_TICKS, 0, false, false, true));
    }

    private static void applyDebuffs(Player player, int elapsedTicks)
    {
        applyDebuff(player, MobEffects.WEAKNESS, weaknessAmplifier(elapsedTicks));
        applyDebuff(player, MobEffects.MOVEMENT_SLOWDOWN, elapsedTicks >= SLOWNESS_TICKS ? 0 : NO_DEBUFF);
        applyDebuff(player, MobEffects.CONFUSION, elapsedTicks >= NAUSEA_TICKS ? 0 : NO_DEBUFF);
        applyDebuff(player, MobEffects.BLINDNESS, elapsedTicks >= BLINDNESS_TICKS ? 0 : NO_DEBUFF);
    }

    /**
     * Holds one debuff of the ladder on that player.
     *
     * <p>An instance of somebody else is left completely alone, see the class comment: no level is pushed down,
     * no duration is shortened and no particle is taken away. This rule simply waits until that instance has
     * run out and then starts handing out its own.
     *
     * @param amplifier the level to hold, or {@link #NO_DEBUFF} while the countdown has not reached it yet
     */
    private static void applyDebuff(Player player, MobEffect effect, int amplifier)
    {
        if (amplifier == NO_DEBUFF)
        {
            removeOwnEffect(player, effect);
            return;
        }

        MobEffectInstance current = player.getEffect(effect);

        if (current != null && !isOwnEffect(current, effect))
        {
            return;
        }

        if (current != null && current.getAmplifier() >= amplifier
                && !current.endsWithin(DEBUFF_REFRESH_BELOW_TICKS))
        {
            return;
        }

        // Same signature as the marker: this debuff is caused by the light, so it shows no particles either.
        player.addEffect(new MobEffectInstance(effect, DEBUFF_DURATION_TICKS, amplifier, false, false, true));
    }

    private static void removeOwnEffect(Player player, MobEffect effect)
    {
        MobEffectInstance current = player.getEffect(effect);

        if (current != null && isOwnEffect(current, effect))
        {
            player.removeEffect(effect);
        }
    }

    /**
     * The signature of everything this class hands out: no beacon style ambient flag, no particles at all,
     * but the icon is shown.
     */
    private static boolean isOwnEffect(MobEffectInstance instance, MobEffect effect)
    {
        return instance.getEffect() == effect
                && !instance.isAmbient()
                && !instance.isVisible()
                && instance.showIcon();
    }
}
