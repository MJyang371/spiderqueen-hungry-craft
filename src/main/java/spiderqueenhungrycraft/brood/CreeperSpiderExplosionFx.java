package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The look of the two blasts of a creeper spider (Explosion FX round).
 *
 * <p><b>Why this class exists.</b> The blast itself is the blast of the game, and so is the particle of the game: an
 * explosion draws exactly <b>one</b> emitter particle, and that emitter is always the same size. So a mega blast - power
 * eight, twice the power of a block of TNT, twice the damage, twice the knockback and a crater twice as wide - used to
 * look exactly like a block of TNT going off. The hole in the ground said "mega", the flash said "small".
 *
 * <p><b>What this class does.</b> It draws the <b>size</b> of the blast: the emitters are spread over the radius the
 * explosion really reached instead of sitting in one point, a ring of dust hugs the rim of the crater, a column of smoke
 * climbs out of it with sparks and debris in it, and the sound of the blast is layered with a deeper one that carries
 * farther. Every number below is scaled from the power the explosion was really fired with, so there is one description
 * of a flash here and not one per ability.
 *
 * <p><b>What this class never touches.</b> The explosion: the falloff, the knockback, the block damage, the damage
 * source, the friendly filter, the vanilla emitter and the vanilla sound are all still made by the game and by
 * {@link CreeperSpiderExplosionUtil} exactly as before. Everything here happens <b>after</b> the blast has already
 * happened, and it is particles plus sound, nothing else.
 */
public final class CreeperSpiderExplosionFx
{
    /** How many emitters one block of blast radius buys. The mega blast of power eight gets eleven, a death blast six. */
    private static final double EMITTERS_PER_BLOCK = 1.35D;

    /** A blast is never drawn with fewer emitters than this: a flash of one particle is the bug this class fixes. */
    private static final int MIN_EMITTERS = 3;

    /** ... and never with more than this, whatever power it was fired with. */
    private static final int MAX_EMITTERS = 24;

    /**
     * The share of the real radius the emitters are spread over. Deliberately below one: the flash is meant to fill the
     * crater the blast really dug, not to claim a hole that is not there.
     */
    private static final double EMITTER_SPREAD = 0.72D;

    /** How far above the feet of the spider the flash is drawn. The blast of the game also starts at the feet. */
    private static final double FLASH_LIFT = 0.35D;

    /** How many small poofs of the game ride inside the flash per block of radius, and the cap on that. */
    private static final double POOFS_PER_BLOCK = 2.2D;
    private static final int MAX_POOFS = 56;

    /** The ball of a blast is a ball and not a pancake: the vertical spread is this share of the horizontal one. */
    private static final double POOF_VERTICAL_SHARE = 0.75D;

    /** The ground ring: one cloud every this many blocks of radius, at least {@value #MIN_RING} of them. */
    private static final double RING_PER_BLOCK = 7.0D;
    private static final int MIN_RING = 12;
    private static final int MAX_RING = 72;

    /** The ring sits at this multiple of the radius, so it hugs the rim of the crater instead of its middle. */
    private static final double RING_RADIUS = 1.02D;

    /** The height of the ring above the feet of the spider, the scatter of it and the speed it drifts outward with. */
    private static final double RING_LIFT = 0.16D;
    private static final double RING_JITTER = 0.55D;
    private static final double RING_SPEED = 0.02D;

    /** The column of smoke: one puff per this many blocks of radius, and never more than {@value #MAX_COLUMN}. */
    private static final double COLUMN_PER_BLOCK = 1.7D;
    private static final int MAX_COLUMN = 40;

    /** How far the column reaches up, in blocks per point of power, and how high above the feet it starts. */
    private static final double COLUMN_HEIGHT = 1.05D;
    private static final double COLUMN_LIFT = 0.6D;

    /** How far the column drifts out while it rises, how fast it drifts and how thick one puff is drawn. */
    private static final double COLUMN_WIDEN = 0.55D;
    private static final double COLUMN_SPEED = 0.06D;

    /** The fire inside the column and the debris of the ground the blast tore up. */
    private static final double FLAMES_PER_BLOCK = 3.0D;
    private static final int MAX_FLAMES = 60;
    private static final double FLAME_LIFT = 0.45D;
    private static final double FLAME_SPREAD = 0.45D;
    private static final double DEBRIS_PER_BLOCK = 4.0D;
    private static final int MAX_DEBRIS = 48;
    private static final double DEBRIS_SPREAD = 0.85D;
    private static final double DEBRIS_LIFT = 0.4D;
    private static final double DEBRIS_SPEED = 0.25D;

    /** The two layers of sound of a blast: the deep one is what makes a big hole sound like a big hole. */
    private static final float MEGA_BOOM_VOLUME = 7.0F;
    private static final float MEGA_BOOM_PITCH = 0.62F;
    private static final float DEEP_BOOM_VOLUME = 4.0F;
    private static final float DEEP_BOOM_PITCH = 0.82F;

    /** How much of all of the above a death blast gets. The farewell of a spider stays a farewell, not a firework. */
    private static final double DEATH_STRENGTH = 0.45D;

    /** The sound of a death blast: the same layering, at the volume of an ordinary creeper. */
    private static final float DEATH_BOOM_VOLUME = 3.0F;
    private static final float DEATH_BOOM_PITCH = 0.75F;

    private CreeperSpiderExplosionFx()
    {
    }

    /**
     * The flash of the mega blast: the ability the queen casts, twice the power of a block of TNT.
     *
     * @param power  the power the explosion was really fired with - every size below is scaled from it
     * @param ground the block the blast tore up, or null when it was fired in the air: it is only used for the debris
     */
    public static void megaBlast(ServerLevel level, double x, double y, double z, float power,
            @Nullable BlockState ground)
    {
        draw(level, x, y, z, power, ground, 1.0D, true);
    }

    /**
     * The flash of the farewell of a spider: the blast a dying creeper spider leaves, the power of a block of TNT.
     *
     * <p>It is the very same description at {@value #DEATH_STRENGTH} of its strength, so the two blasts of this species
     * look like two sizes of one thing instead of two unrelated effects.
     */
    public static void deathBlast(ServerLevel level, double x, double y, double z, float power,
            @Nullable BlockState ground)
    {
        draw(level, x, y, z, power, ground, DEATH_STRENGTH, false);
    }

    /** One blast, drawn: the flash, the ball, the ring, the column, the sparks, the debris and the sound. */
    private static void draw(ServerLevel level, double x, double y, double z, float power, @Nullable BlockState ground,
            double strength, boolean mega)
    {
        RandomSource random = level.getRandom();

        // The radius the blast really reached: the game throws its rays out to power * (0.7 .. 1.3), and this is the
        // conservative middle of that, pulled in a little more for the small blast so the two sizes stay apart.
        double radius = power * EMITTER_SPREAD * (0.55D + 0.45D * strength);

        // The flash: emitters all over the ball instead of one in its middle. This loop is the whole fix.
        int emitters = scaled(power * EMITTERS_PER_BLOCK * strength, MIN_EMITTERS, MAX_EMITTERS);

        for (int i = 0; i < emitters; ++i)
        {
            double[] at = ballPoint(random, radius);
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x + at[0], y + at[1] + FLASH_LIFT, z + at[2],
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // The body of the ball: the small poofs of the game, scattered through the radius the blast really reached.
        int poofs = scaled(power * POOFS_PER_BLOCK * strength, 0, MAX_POOFS);

        if (poofs > 0)
        {
            level.sendParticles(ParticleTypes.EXPLOSION, x, y + FLASH_LIFT, z, poofs, radius,
                    radius * POOF_VERTICAL_SHARE, radius, 0.0D);
        }

        // The shockwave on the ground: a ring at the rim of the crater, drifting outward and slightly up.
        int ring = scaled(power * RING_PER_BLOCK * strength, MIN_RING, MAX_RING);
        double rim = power * RING_RADIUS;

        for (int i = 0; i < ring; ++i)
        {
            double angle = Math.PI * 2.0D * (double) i / (double) ring;
            double rx = x + Math.cos(angle) * rim;
            double rz = z + Math.sin(angle) * rim;

            level.sendParticles(ParticleTypes.CLOUD, rx, y + RING_LIFT, rz, 1, RING_JITTER, RING_JITTER * 0.5D,
                    RING_JITTER, RING_SPEED);

            // Every second cloud of a mega blast also throws dust: the rim of a big hole is not a clean circle.
            if (mega && (i & 1) == 0)
            {
                level.sendParticles(ParticleTypes.POOF, rx, y + RING_LIFT, rz, 1, RING_JITTER, RING_JITTER * 0.5D,
                        RING_JITTER, RING_SPEED);
            }
        }

        // The column: smoke climbing out of the crater, widening as it goes, with fire in its foot.
        int column = scaled(power * COLUMN_PER_BLOCK * strength, 0, MAX_COLUMN);
        double columnHeight = power * COLUMN_HEIGHT * strength;

        for (int i = 0; i < column; ++i)
        {
            double t = column <= 1 ? 0.0D : (double) i / (double) (column - 1);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double drift = rim * COLUMN_WIDEN * t * random.nextDouble();

            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    x + Math.cos(angle) * drift, y + COLUMN_LIFT + columnHeight * t, z + Math.sin(angle) * drift,
                    1, 0.2D, 0.1D, 0.2D, COLUMN_SPEED);
        }

        int flames = scaled(power * FLAMES_PER_BLOCK * strength, 0, MAX_FLAMES);

        if (flames > 0)
        {
            level.sendParticles(ParticleTypes.FLAME, x, y + FLAME_LIFT, z, flames, radius * FLAME_SPREAD, 0.25D,
                    radius * FLAME_SPREAD, 0.02D);
        }

        // The debris of the ground the blast tore up. The block is sampled by the caller before the crater exists.
        if (ground != null && !ground.isAir())
        {
            int debris = scaled(power * DEBRIS_PER_BLOCK * strength, 1, MAX_DEBRIS);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, y + DEBRIS_LIFT, z, debris,
                    radius * DEBRIS_SPREAD, 0.4D, radius * DEBRIS_SPREAD, DEBRIS_SPEED);
        }

        if (mega)
        {
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, MEGA_BOOM_VOLUME,
                    MEGA_BOOM_PITCH);
            level.playSound(null, x, y, z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, DEEP_BOOM_VOLUME,
                    DEEP_BOOM_PITCH);
        }
        else
        {
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, DEATH_BOOM_VOLUME,
                    DEATH_BOOM_PITCH);
        }

        // The size of a blast is a number a test can read, so it is written down: whoever wonders whether the effect
        // really scaled with the power finds the answer in the log and not only on the screen. The mega blast is a
        // deliberate act of the queen and happens rarely, so it is worth a line of its own; the farewell of a spider is
        // a routine death and stays on the debug level it shares with the rest of the beast tuning.
        String report = "{} blast of power {} at {} {} {}: {} emitters, {} poofs, {} ring, {} smoke, {} flames, "
                + "ground={}";

        if (mega)
        {
            SpiderQueenHungryCraft.LOGGER.info("[creeper fx] " + report, "mega", power, x, y, z, emitters, poofs, ring,
                    column, flames, ground == null ? "air" : ground.getBlock());
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.debug("[creeper fx] " + report, "death", power, x, y, z, emitters, poofs,
                    ring, column, flames, ground == null ? "air" : ground.getBlock());
        }
    }

    /**
     * @return a random point inside the ball of that radius, biased towards its shell
     *
     * <p>The cube root is what makes the points fill the volume evenly: without it they would pile up in the middle,
     * which is exactly the single-puff look this class replaces.
     */
    private static double[] ballPoint(RandomSource random, double radius)
    {
        double theta = random.nextDouble() * Math.PI * 2.0D;
        double z = random.nextDouble() * 2.0D - 1.0D;
        double flat = Math.sqrt(1.0D - z * z);
        double r = radius * Math.cbrt(random.nextDouble());

        return new double[] { Math.cos(theta) * flat * r, z * r, Math.sin(theta) * flat * r };
    }

    /** @return that scaled count as a whole number inside the given bounds, so no blast is drawn with nothing at all. */
    private static int scaled(double value, int min, int max)
    {
        return (int) Math.round(Mth.clamp(value, (double) min, (double) max));
    }
}

