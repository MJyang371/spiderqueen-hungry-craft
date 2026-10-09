package spiderqueenhungrycraft.soul;

import org.joml.Vector3f;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import spiderqueenhungrycraft.registry.HungryCraftSounds;

/**
 * The collection feedback of a soul remnant (Soul Foundation V1): a small burst of golden firework sparks where the
 * remnant was, and its chime.
 *
 * <p><b>It is feedback and nothing else.</b> No firework entity is spawned, nothing explodes, nothing is damaged, no
 * block is broken and nobody is knocked back: these are particles and one sound the server sends to the clients that
 * can see the spot, exactly like any other effect of the game.
 *
 * <p>The colour is fixed here instead of being left to the firework particle, whose sparks pick their colours at
 * random: the firework sparks carry the shape while the dust sparks guarantee that the burst really reads as
 * <b>yellow</b>.
 */
public final class SoulClaimFx
{
    /** The colour of a soul: a warm gold, the colour of the texture of the remnant itself. */
    private static final DustParticleOptions SOUL_DUST =
            new DustParticleOptions(new Vector3f(1.0F, 0.84F, 0.26F), 1.1F);

    /** The sparks of the burst, and how far they fly. */
    private static final int FIREWORK_SPARKS = 60;
    private static final int DUST_SPARKS = 30;
    private static final double SPREAD = 0.3D;
    private static final double SPEED = 0.08D;

    /** The chime of a collection is played once, at its own pitch and its own volume. */
    private static final float SOUND_VOLUME = 1.0F;
    private static final float SOUND_PITCH = 1.0F;

    private SoulClaimFx()
    {
    }

    /** Plays the burst and the chime at the position of a remnant. Nothing happens on a client: it only receives. */
    public static void play(Entity remnant)
    {
        if (!(remnant.level() instanceof ServerLevel level))
        {
            return;
        }

        double x = remnant.getX();
        double y = remnant.getY() + remnant.getBbHeight() * 0.5D;
        double z = remnant.getZ();

        // The flash first, so the burst has its one bright moment, then the two clouds of sparks around it.
        level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FIREWORK, x, y, z, FIREWORK_SPARKS, SPREAD, SPREAD, SPREAD, SPEED);
        level.sendParticles(SOUL_DUST, x, y, z, DUST_SPARKS, SPREAD, SPREAD, SPREAD, SPEED * 0.6D);

        // And the chime of the souls coming home, once, from the very place they were picked up.
        level.playSound(null, x, y, z, HungryCraftSounds.FIND_SOUL.get(), SoundSource.PLAYERS, SOUND_VOLUME,
                SOUND_PITCH);
    }
}
