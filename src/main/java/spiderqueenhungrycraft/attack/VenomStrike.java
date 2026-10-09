package spiderqueenhungrycraft.attack;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The Venom Strike of the queen: the debuffs and the damage multiplier a prepared strike releases.
 *
 * <p>Both consumers share this helper on purpose. The ordinary melee path applies it to the single target of
 * its swing, the beast bite applies it to every victim of its area at once, and both use the very same effect
 * levels and durations, so venom never behaves differently depending on the form the queen is in.
 */
public final class VenomStrike
{
    /** Amplifier 1 is level two, so Poison II and Wither II. */
    public static final int EFFECT_AMPLIFIER = 1;

    private VenomStrike()
    {
    }

    /** The damage multiplier of a released venom strike, read from the configuration. */
    public static float damageMultiplier()
    {
        return (float) HungryCraftConfig.empoweredAttackDamageMultiplier();
    }

    /** Grants the venom debuffs to one victim, with the configured durations. */
    public static void applyEffects(LivingEntity target)
    {
        target.addEffect(new MobEffectInstance(MobEffects.POISON,
                HungryCraftConfig.empoweredAttackPoisonDurationTicks(), EFFECT_AMPLIFIER));
        target.addEffect(new MobEffectInstance(MobEffects.WITHER,
                HungryCraftConfig.empoweredAttackWitherDurationTicks(), EFFECT_AMPLIFIER));
    }
}
