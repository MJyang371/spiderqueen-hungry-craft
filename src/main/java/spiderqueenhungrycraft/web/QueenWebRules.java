package spiderqueenhungrycraft.web;

import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEffects;

/**
 * Shared rules of the web features: who ignores webs and how strong the Webbed stacking is.
 */
public final class QueenWebRules
{
    /** Duration of the very first Webbed hit. */
    public static final int FIRST_WEBBED_DURATION_TICKS = 100;

    /** Duration of every following Webbed hit. */
    public static final int NEXT_WEBBED_DURATION_TICKS = 120;

    /** Amplifier 0 to 3, which is Webbed I to Webbed IV. */
    public static final int MAX_WEBBED_AMPLIFIER = 3;

    private QueenWebRules()
    {
    }

    /** @return true when that entity is a spider, the single authority is {@link WebAffinity}. */
    public static boolean isSpiderLike(Entity entity)
    {
        return WebAffinity.isSpiderLike(entity);
    }

    /** Spider Queens are immune to their own webs, normal players are not. */
    public static boolean isQueen(Entity entity)
    {
        return entity instanceof Player && QueenDataCapability.isQueen((Player) entity);
    }

    /**
     * @return true when this entity ignores the Webbed effect entirely. This is the escape hatch tag, it is
     *         not the same as being a spider: spider like creatures are inside it through the datapack, and a
     *         non spider may join it too. The physical web behaviour is handled by {@link WebAffinity}.
     */
    public static boolean isWebImmune(Entity entity)
    {
        return isQueen(entity) || entity.getType().is(QueenWebEntityTags.WEB_IMMUNE);
    }

    public static int webImmunityFreeAmplifier(int amplifier)
    {
        return Mth.clamp(amplifier, 0, MAX_WEBBED_AMPLIFIER);
    }

    /**
     * @param currentAmplifier the amplifier the target already has, or -1 when it has none
     * @return the amplifier a new hit should apply
     */
    public static int nextWebbedAmplifier(int currentAmplifier)
    {
        return webImmunityFreeAmplifier(currentAmplifier + 1);
    }

    /**
     * @param currentAmplifier the amplifier the target already has, or -1 when it has none
     * @return how long the new Webbed instance should last
     */
    public static int webbedDurationTicks(int currentAmplifier)
    {
        return currentAmplifier < 0 ? FIRST_WEBBED_DURATION_TICKS : NEXT_WEBBED_DURATION_TICKS;
    }

    /** @return the current Webbed amplifier of this entity, or -1 when it is not webbed. */
    public static int currentWebbedAmplifier(LivingEntity entity)
    {
        MobEffectInstance instance = entity.getEffect(HungryCraftEffects.WEBBED.get());
        return instance == null ? -1 : instance.getAmplifier();
    }
}
