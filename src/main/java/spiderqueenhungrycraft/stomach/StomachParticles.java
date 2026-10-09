package spiderqueenhungrycraft.stomach;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The provisional ambience of a Stomach Cell (Phase 8E1).
 *
 * <p>The whole particle look of the stomach is decided in this one place: the types, the amount and the chance.
 * The current visuals are deliberately temporary - a very sparse drift of crimson spores with the occasional
 * bubble at the water line - so the final look can be swapped without touching any other system.
 */
public final class StomachParticles
{
    /** Chance per ambience tick of one drifting spore inside the cavity. */
    private static final double SPORE_CHANCE = 0.35D;

    /** Chance per ambience tick of one bubble at the water line. */
    private static final double BUBBLE_CHANCE = 0.2D;

    /** Tries used to find a random point inside the sphere before giving up. */
    private static final int SPHERE_TRIES = 8;

    private StomachParticles()
    {
    }

    /** Emits the sparse ambience of one occupied cell; the manager calls this every ten ticks. */
    public static void ambient(ServerLevel level, StomachCell cell)
    {
        RandomSource random = level.getRandom();

        if (random.nextDouble() < SPORE_CHANCE)
        {
            BlockPos point = randomPointInside(level, cell, random);

            if (point != null)
            {
                spread(level, ParticleTypes.CRIMSON_SPORE, point, 1, 0.4D, 0.4D, 0.4D, 0.0D);
            }
        }

        if (random.nextDouble() < BUBBLE_CHANCE)
        {
            BlockPos center = cell.center();
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(random.nextDouble()) * (StomachCellGeometry.INNER_RADIUS - 1.0D);
            double x = center.getX() + 0.5D + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5D + Math.sin(angle) * radius;

            spread(level, ParticleTypes.BUBBLE_POP, null, x, StomachCellGeometry.WATER_SURFACE_Y + 0.4D, z, 1, 0.2D,
                    0.0D, 0.2D, 0.01D);
        }
    }

    /** @return a random air position inside the cavity, or null when the random tries were all in the wall. */
    private static BlockPos randomPointInside(ServerLevel level, StomachCell cell, RandomSource random)
    {
        BlockPos center = cell.center();
        int radius = StomachCellGeometry.INNER_RADIUS - 1;

        for (int attempt = 0; attempt < SPHERE_TRIES; attempt++)
        {
            BlockPos candidate = center.offset(random.nextInt(radius * 2 + 1) - radius,
                    random.nextInt(radius * 2 + 1) - radius, random.nextInt(radius * 2 + 1) - radius);

            if (candidate.getY() > StomachCellGeometry.WATER_SURFACE_Y && cell.containsInner(candidate.getX() + 0.5D,
                    candidate.getY() + 0.5D, candidate.getZ() + 0.5D) && level.getBlockState(candidate).isAir())
            {
                return candidate;
            }
        }

        return null;
    }

    private static void spread(ServerLevel level, ParticleOptions particle, BlockPos pos, int count, double dx,
            double dy, double dz, double speed)
    {
        spread(level, particle, pos, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, count, dx, dy, dz, speed);
    }

    private static void spread(ServerLevel level, ParticleOptions particle, BlockPos unused, double x, double y,
            double z, int count, double dx, double dy, double dz, double speed)
    {
        level.sendParticles(particle, x, y, z, count, dx, dy, dz, speed);
    }
}
