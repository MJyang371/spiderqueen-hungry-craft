package spiderqueenhungrycraft.beast;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.SoundType;

/**
 * The hurt sound of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>A giant spider does not gasp like a player when a zombie scratches its chitin, so every attack that
 * really comes from somebody else answers with the hit sound of a netherite block: the dull, heavy clang of
 * a metal shell. The sound is taken straight from the vanilla sound type of that block, so no new OGG file
 * and no new sound event exist for it, and a resource pack that retextures netherite retextures the beast
 * hurt as well.
 *
 * <p>Only combat is replaced. Environmental damage such as drowning, starving, freezing or a cactus keeps
 * the ordinary player hurt sound, because that is a body complaining rather than an impact on armor. The
 * classification is deliberately conservative: an attacker, a projectile or an explosion is combat, anything
 * else is left completely alone.
 */
public final class QueenBeastHurtSound
{
    /** The vanilla hit sound of a netherite block, used as the incoming attack sound of the beast. */
    public static final SoundEvent HIT_SOUND = SoundType.NETHERITE_BLOCK.getHitSound();

    private QueenBeastHurtSound()
    {
    }

    /**
     * @return true when that damage came from somebody else: a mob, a player, an arrow, a projectile or an
     *         explosion. Everything without an attacker and without those tags is treated as environment.
     */
    public static boolean isCombatHit(DamageSource source)
    {
        return source.getEntity() != null
                || source.getDirectEntity() != null
                || source.is(DamageTypeTags.IS_PROJECTILE)
                || source.is(DamageTypeTags.IS_EXPLOSION);
    }
}
