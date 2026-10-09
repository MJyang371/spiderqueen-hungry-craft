package spiderqueenhungrycraft.web;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.hostility.QueenProvocation;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The elemental behaviour of the web features (Phase 7A).
 *
 * <p>This is the single place where an element decides what it does to a creature: the shot hit and the
 * effects of standing inside an elemental web. The game rules come from the entity itself, so nothing is
 * hard coded per mob: poison asks {@link LivingEntity#canBeAffected}, fire asks
 * {@link LivingEntity#fireImmune}.
 */
public final class WebElementEffects
{
    /** Poison of a poison shot hit, amplifier 2 is Poison III. */
    public static final int POISON_SHOT_AMPLIFIER = 2;

    /** Duration of the poison of a poison shot hit. */
    public static final int POISON_SHOT_DURATION_TICKS = 100;

    /** Burning time of a fire shot hit. */
    public static final int FIRE_SHOT_SECONDS = 5;

    /** Poison of a poison web, amplifier 1 is Poison II. */
    public static final int WEB_POISON_AMPLIFIER = 1;

    /** Duration of the poison of a poison web, refreshed while the target stays inside. */
    public static final int WEB_POISON_DURATION_TICKS = 60;

    /** A poison web only replaces its own effect when the remaining time drops below this. */
    public static final int WEB_POISON_REFRESH_BELOW_TICKS = 30;

    /** Burning time of a fire web, refreshed while the target stays inside. */
    public static final int WEB_FIRE_SECONDS = 4;

    private WebElementEffects()
    {
    }

    /**
     * Applies the elemental part of a projectile hit: real damage first, then, if the target is still
     * alive, the element effect. A capture is never rolled here, that is decided by the caller.
     *
     * <p>The damage goes through the normal server side pipeline with the projectile as the direct entity
     * and the shooter as the causing entity, so armour, enchantments, immunity frames, the damage event and
     * the pack provocation of the existing hostility system all apply without a second code path.
     */
    public static void applyShotHit(ServerLevel level, WebShotProjectile shot, LivingEntity target)
    {
        WebElement element = shot.element();
        float damage = element.directDamage();

        if (damage <= 0.0F)
        {
            return;
        }

        DamageSource source = target.damageSources().thrown(shot, shot.getOwner());
        boolean hurt = target.hurt(source, damage);

        if (!hurt && target.isAlive())
        {
            // The shot landed, but the target produced no damage event, for example because it was inside
            // its immunity frames. It still counts as one attack of the queen, so the pack is alerted here.
            provokeFallback(shot, target);
        }

        if (!target.isAlive())
        {
            return;
        }

        if (element == WebElement.POISON)
        {
            applyEffect(target, MobEffects.POISON, POISON_SHOT_AMPLIFIER, POISON_SHOT_DURATION_TICKS);
        }
        else if (element == WebElement.FIRE && !target.fireImmune())
        {
            target.setSecondsOnFire(FIRE_SHOT_SECONDS);
        }
    }

    /** The poison of a poison web, refreshed only when the running effect got weak. */
    public static void applyWebPoison(LivingEntity living)
    {
        MobEffectInstance current = living.getEffect(MobEffects.POISON);

        if (current != null && current.getAmplifier() >= WEB_POISON_AMPLIFIER
                && current.getDuration() > WEB_POISON_REFRESH_BELOW_TICKS)
        {
            return;
        }

        applyEffect(living, MobEffects.POISON, WEB_POISON_AMPLIFIER, WEB_POISON_DURATION_TICKS);
    }

    /** The burning of a fire web. Fire immune creatures live in it, the blaze never catches fire. */
    public static void applyWebFire(LivingEntity living)
    {
        if (living.fireImmune())
        {
            return;
        }

        living.setSecondsOnFire(WEB_FIRE_SECONDS);
    }

    /**
     * Adds the effect only when the creature itself accepts it. Undead and every other immune mob are
     * therefore skipped by the vanilla rule instead of a hardcoded list.
     */
    private static void applyEffect(LivingEntity living, MobEffect effect, int amplifier, int durationTicks)
    {
        MobEffectInstance instance = new MobEffectInstance(effect, durationTicks, amplifier, false, true, true);

        if (living.canBeAffected(instance))
        {
            living.addEffect(instance);
        }
    }

    private static void provokeFallback(WebShotProjectile shot, LivingEntity target)
    {
        if (shot.getOwner() instanceof ServerPlayer queen && QueenDataCapability.isQueen(queen))
        {
            QueenProvocation.provoke(queen, target, QueenProvocation.Cause.PROJECTILE, shot.volleyId());
        }
    }
}
