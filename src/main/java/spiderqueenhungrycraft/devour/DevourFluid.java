package spiderqueenhungrycraft.devour;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftParticles;

/**
 * The dark green digestive fluid, in all three of its shapes (Phase 8E2A-POLISH-2).
 *
 * <p>One fluid, three ways of leaving a mouth:
 *
 * <ul>
 * <li>a <strong>vomit</strong> - a wide, fast cone of dust with a spray of vanilla particles around it, thrown
 * out over a few ticks,</li>
 * <li><strong>saliva</strong> - the mouthful a beast throws when it snaps, the droplet particle, narrower and
 * slower,</li>
 * <li><strong>digestion</strong> - a few droplets dripping off a victim, in
 * {@link spiderqueenhungrycraft.stomach.StomachCoreEntity}.</li>
 * </ul>
 *
 * <p>The droplets are delivered one packet per particle with a particle count of zero, because that is the only
 * form of {@code ClientboundLevelParticlesPacket} that carries an exact position and an exact velocity: with a
 * count above zero the client randomises both of them per particle, which is why an earlier version of this
 * class produced a cloud that hovered instead of flying.
 *
 * <p>The tinted potion particle of the game is a registered {@code SimpleParticleType} in 1.20.1 and takes its
 * colour from those very velocity arguments, so it cannot carry a fixed dark green over a broadcast. The dust
 * carries its colour in its options, and the droplet types of this mod carry it in their provider: every client
 * sees the same fluid.
 */
public final class DevourFluid
{
    /** The one colour of the vomit cloud. */
    private static final ParticleOptions FLUID = new DustParticleOptions(
            new Vector3f(DevourTuning.FLUID_RED, DevourTuning.FLUID_GREEN, DevourTuning.FLUID_BLUE),
            DevourTuning.FLUID_SCALE);

    /** The droplets of the remaining batches, one entry per running vomit. */
    private static final List<Pending> PENDING = new ArrayList<>();

    private DevourFluid()
    {
    }

    /** One vomit that still owes the world a few batches of fluid. */
    private record Pending(UUID owner, ResourceKey<Level> dimension, Vec3 forward, int batchesLeft)
    {
    }

    /** The cloud of a vomit leaves the mouth together with the contents that are leaving the stomach. */
    public static void start(ServerPlayer owner, Vec3 forward)
    {
        int sent = emit(owner, forward);

        if (sent > 0)
        {
            SpiderQueenHungryCraft.LOGGER.debug("[REGURG_FX] fluid batch 1/{} sent to {} clients",
                    DevourTuning.FLUID_BATCHES, sent);
        }

        followUp(owner, forward);
    }

    /** The rest of the cloud, one batch per server tick, so the jet has a length instead of a single frame. */
    public static void followUp(ServerPlayer owner, Vec3 forward)
    {
        if (DevourTuning.FLUID_BATCHES <= 1)
        {
            return;
        }

        PENDING.add(new Pending(owner.getUUID(), owner.level().dimension(), forward, DevourTuning.FLUID_BATCHES - 1));
    }

    /**
     * The mouthful a beast throws when it snaps at something (Phase 8E2A-POLISH-2).
     *
     * <p>It is the same fluid as a vomit, in the shape of a narrow cone of droplets that fall to the ground after
     * they left the mouth. It is deliberately not a cloud: a snap sprays, it does not empty a stomach.
     *
     * @return how many clients the saliva was sent to
     */
    public static int saliva(ServerPlayer beast, Vec3 forward)
    {
        if (!(beast.level() instanceof ServerLevel level))
        {
            return 0;
        }

        RandomSource random = level.getRandom();
        Basis basis = Basis.of(forward);
        Vec3 mouth = new Vec3(beast.getX(), beast.getY() + DevourTuning.MOUTH_HEIGHT, beast.getZ());
        int sent = 0;

        for (int drop = 0; drop < DevourTuning.SALIVA_COUNT; drop++)
        {
            double distance = range(random, DevourTuning.SALIVA_START_MIN, DevourTuning.SALIVA_START_MAX);
            double side = jitter(random, DevourTuning.SALIVA_SPREAD);
            double up = jitter(random, DevourTuning.SALIVA_SPREAD * 0.6D);
            Vec3 birth = mouth.add(basis.forward().scale(distance)).add(basis.right().scale(side))
                    .add(0.0D, up, 0.0D);

            Vec3 direction = cone(random, basis, DevourTuning.SALIVA_CONE_HALF_ANGLE);
            double speed = range(random, DevourTuning.SALIVA_FORWARD_MIN, DevourTuning.SALIVA_FORWARD_MAX);
            double vertical = range(random, DevourTuning.SALIVA_UP_MIN, DevourTuning.SALIVA_UP_MAX);
            Vec3 velocity = direction.scale(speed).add(0.0D, vertical, 0.0D);

            sent = Math.max(sent, level.sendParticles(HungryCraftParticles.GASTRIC_DROPLET.get(), birth.x, birth.y,
                    birth.z, 0, velocity.x, velocity.y, velocity.z, 1.0D));
        }

        SpiderQueenHungryCraft.LOGGER.debug("[DEVOUR_FX] saliva {} droplets sent to {} clients",
                DevourTuning.SALIVA_COUNT, sent);

        return sent;
    }
    /** One server tick of the remaining batches; every vomit forgets itself after its last batch. */
    public static void tick(MinecraftServer server)
    {
        if (PENDING.isEmpty() || server == null)
        {
            return;
        }

        for (int index = PENDING.size() - 1; index >= 0; index--)
        {
            Pending pending = PENDING.get(index);
            ServerPlayer owner = server.getPlayerList().getPlayer(pending.owner());

            // A queen who left the server, died or changed world in the meantime simply stops spraying.
            if (owner == null || owner.isRemoved() || !owner.isAlive()
                    || !owner.level().dimension().equals(pending.dimension()))
            {
                PENDING.remove(index);
                continue;
            }

            int sent = emit(owner, pending.forward());

            if (pending.batchesLeft() <= 1)
            {
                PENDING.remove(index);
                SpiderQueenHungryCraft.LOGGER.debug("[REGURG_FX] fluid batch {}/{} sent to {} clients, vomit done",
                        DevourTuning.FLUID_BATCHES, DevourTuning.FLUID_BATCHES, sent);
            }
            else
            {
                PENDING.set(index, new Pending(pending.owner(), pending.dimension(), pending.forward(),
                        pending.batchesLeft() - 1));
            }
        }
    }

    /** @return how many clients one whole batch of the cloud was sent to. */
    private static int emit(ServerPlayer owner, Vec3 forward)
    {
        if (!(owner.level() instanceof ServerLevel level))
        {
            return 0;
        }

        RandomSource random = level.getRandom();
        Basis basis = Basis.of(forward);
        Vec3 mouth = new Vec3(owner.getX(), owner.getY() + DevourTuning.MOUTH_HEIGHT, owner.getZ());
        int sent = 0;

        // The jet: dark green droplets, born in a wide aperture in front of the mouth, flying in a real cone.
        for (int drop = 0; drop < DevourTuning.FLUID_PARTICLES_MAIN / DevourTuning.FLUID_BATCHES; drop++)
        {
            double distance = range(random, DevourTuning.FLUID_START_MIN, DevourTuning.FLUID_START_MAX);
            double side = jitter(random, DevourTuning.FLUID_BIRTH_SPREAD);
            double up = jitter(random, DevourTuning.FLUID_BIRTH_HEIGHT);
            Vec3 birth = mouth.add(basis.forward().scale(distance)).add(basis.right().scale(side)).add(0.0D, up, 0.0D);

            Vec3 direction = cone(random, basis, DevourTuning.FLUID_CONE_HALF_ANGLE);
            double speed = range(random, DevourTuning.FLUID_FORWARD_MIN, DevourTuning.FLUID_FORWARD_MAX);
            double vertical = range(random, DevourTuning.FLUID_UP_MIN, DevourTuning.FLUID_UP_MAX);
            Vec3 velocity = direction.scale(speed).add(0.0D, vertical, 0.0D);

            // A count of zero means one single particle at exactly this place with exactly this velocity.
            sent = Math.max(sent, level.sendParticles(FLUID, birth.x, birth.y, birth.z, 0, velocity.x, velocity.y,
                    velocity.z, 1.0D));
        }

        // The spray: ordinary particles of the game, thrown in every direction like a real splash of liquid.
        Vec3 spray = mouth.add(basis.forward().scale(DevourTuning.FLUID_START_MIN + 0.3D));

        sent = Math.max(sent, level.sendParticles(ParticleTypes.SPIT, spray.x, spray.y, spray.z,
                DevourTuning.FLUID_SPIT_PARTICLES / DevourTuning.FLUID_BATCHES, 0.5D, 0.35D, 0.5D,
                DevourTuning.FLUID_SPRAY_SPEED));
        sent = Math.max(sent, level.sendParticles(ParticleTypes.SPLASH, spray.x, spray.y - 0.2D, spray.z,
                DevourTuning.FLUID_SPLASH_PARTICLES / DevourTuning.FLUID_BATCHES, 0.6D, 0.35D, 0.6D,
                DevourTuning.FLUID_SPRAY_SPEED));
        sent = Math.max(sent, level.sendParticles(ParticleTypes.POOF, mouth.x, mouth.y - 0.3D, mouth.z,
                DevourTuning.FLUID_POOF_PARTICLES / DevourTuning.FLUID_BATCHES, 0.45D, 0.25D, 0.45D, 0.05D));

        return sent;
    }

    /**
     * One direction inside the cone around a look direction (Phase 8E2A-POLISH-2).
     *
     * <p>Public on purpose: the debug probe of this phase measures the cone with this very method, so the shape
     * that is measured is the shape that is really used. A caller never gets a direction outside the cone and
     * never a direction that points backwards.
     *
     * @param halfAngleDegrees the half angle of the cone, exactly like {@link DevourTuning}
     */
    public static Vec3 sampleConeDirection(RandomSource random, Vec3 look, double halfAngleDegrees)
    {
        return cone(random, Basis.of(look), halfAngleDegrees);
    }

    /**
     * One direction inside the cone around a look direction.
     *
     * <p>The cosine of the angle is sampled uniformly, which spreads the directions evenly over the opening
     * instead of clustering them in the middle, and the angle is never larger than the half angle: nothing of a
     * vomit ever flies backwards or sideways out of the cone.
     */
    private static Vec3 cone(RandomSource random, Basis basis, double halfAngleDegrees)
    {
        double cosMax = Math.cos(Math.toRadians(halfAngleDegrees));
        double cosTheta = cosMax + random.nextDouble() * (1.0D - cosMax);
        double sinTheta = Math.sqrt(Math.max(0.0D, 1.0D - cosTheta * cosTheta));
        double phi = random.nextDouble() * Math.PI * 2.0D;

        return basis.forward().scale(cosTheta).add(basis.right().scale(sinTheta * Math.cos(phi)))
                .add(basis.up().scale(sinTheta * Math.sin(phi))).normalize();
    }

    /** The three axes of a look direction: forward, right and up, all unit length. */
    private record Basis(Vec3 forward, Vec3 right, Vec3 up)
    {
        private static Basis of(Vec3 look)
        {
            Vec3 forward = new Vec3(look.x, 0.0D, look.z).normalize();
            Vec3 right = new Vec3(-forward.z, 0.0D, forward.x).normalize();
            Vec3 up = right.cross(forward).normalize();

            return new Basis(forward, right, up);
        }
    }

    private static double range(RandomSource random, double min, double max)
    {
        return min + random.nextDouble() * (max - min);
    }

    private static double jitter(RandomSource random, double amount)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

}
