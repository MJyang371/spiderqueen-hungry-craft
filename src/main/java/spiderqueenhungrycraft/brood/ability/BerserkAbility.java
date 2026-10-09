package spiderqueenhungrycraft.brood.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * The rage of the zombie spider (Brood Ability framework): the first ability of the brood.
 *
 * <p>It needs nothing at all - no enemy, no place, no target - because a spider that has just digested a zombie is angry
 * wherever it stands. It only makes <b>itself</b> faster and stronger for twenty seconds, and it starts its one minute of
 * rest at the instant of the cast, so the ability is ready again forty seconds after the rage has faded.
 *
 * <p>Everything else about the spider is left alone: its order, its scout move, its forced victim and its navigation are
 * exactly what they were before the key was pressed.
 */
public final class BerserkAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state, the spider holds the cooldown. */
    public static final BerserkAbility INSTANCE = new BerserkAbility();

    /** The stable id of this ability. */
    public static final String ID = "berserk";

    /** How long the rage lasts: twenty seconds. */
    public static final int EFFECT_TICKS = 400;

    /** The amplifier of "II" - one step above the potion of the game, never two. */
    public static final int EFFECT_AMPLIFIER = 1;

    /** How long the ability rests after a cast: one minute, counted from the cast itself. */
    public static final int COOLDOWN_TICKS = 1200;

    private BerserkAbility()
    {
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public Component displayName()
    {
        return Component.translatable("ability.spiderqueen_hungrycraft.berserk");
    }

    @Override
    public SpiderAbilityTarget targetRequirement()
    {
        return SpiderAbilityTarget.NONE;
    }

    @Override
    public int cooldownTicks()
    {
        return COOLDOWN_TICKS;
    }

    @Override
    public boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        // The rage of a zombie spider never needs an enemy: the key is enough.
        return true;
    }

    /** @return true while the rage of the cast is still burning on that spider. */
    @Override
    public boolean isActive(SpiderMinionEntity spider)
    {
        return spider.getEffect(MobEffects.MOVEMENT_SPEED) != null && spider.getEffect(MobEffects.DAMAGE_BOOST) != null;
    }

    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        // Speed II and Strength II for twenty seconds. The effects are added with the ordinary rule of the game, so a
        // stronger potion that somebody else gave to this spider is never torn down by this ability.
        spider.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_TICKS, EFFECT_AMPLIFIER));
        spider.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_TICKS, EFFECT_AMPLIFIER));

        // A spider sound of its own and a burst of sparks, so the cast is visible and audible without ever borrowing
        // anything from the undead this spider was bred from.
        spider.level().playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.HOSTILE, 1.0F, 0.7F);

        if (spider.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.CRIT, spider.getX(), spider.getY() + spider.getBbHeight() * 0.5D,
                    spider.getZ(), 14, spider.getBbWidth() * 0.4D, spider.getBbHeight() * 0.3D,
                    spider.getBbWidth() * 0.4D, 0.05D);
        }

        // The rage always happens, wherever the spider stands and whatever it is looking at.
        return true;
    }
}
