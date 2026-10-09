package spiderqueenhungrycraft.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastStompFeedbackPacket;

/**
 * The stomp of the beast (Phase 8C): what a charged jump does when it comes back down.
 *
 * <p>The landing is detected by watching the ground state of the beast, never the fall distance: a beast is
 * immune to fall damage, so its {@code fallDistance} is not a reliable source. A jump that was strong enough
 * arms the stomp, and the very first tick that finds the beast on the ground again fires it, disarms it in the
 * same breath and therefore can never run twice for one jump, even when the landing breaks the blocks under
 * the feet.
 *
 * <p>Every victim of the radius takes the fixed stomp damage, so a barely armed stomp hits exactly as hard as
 * a full one, plus a clear upward impulse and a small push away from the landing point. The beast's own super
 * armor never touches those victims: it only protects the beast.
 *
 * <p>The visual is a whole tier above the bite: the ground of the landing is sampled all over the footprint,
 * so stone, grass, sand and wood erupt with their own material, and the destruction is limited to a thin band
 * around the surface, which keeps a stomp a stomp and not an open pit mine.
 */
public final class QueenBeastStomp
{
    /** Random jitter of a ground sample, so the sampling grid itself is never visible. */
    private static final double SAMPLE_JITTER = 0.35D;

    /** Spread and speed of the debris of the landing. */
    private static final double DEBRIS_SPREAD = 0.3D;
    private static final double DEBRIS_SPEED = 0.22D;

    private QueenBeastStomp()
    {
    }

    /** One server tick of a beast that might be falling: this is where a landing becomes a stomp. */
    public static void tick(ServerPlayer player, QueenBeastState state)
    {
        boolean onGround = player.onGround();
        boolean wasOnGround = state.isLastOnGround();
        state.setLastOnGround(onGround);

        if (!state.isStompArmed())
        {
            return;
        }

        // Only a real landing counts: the beast has to have been in the air before this tick.
        if (!onGround || wasOnGround)
        {
            return;
        }

        // Disarmed first, so a landing that breaks the ground below the feet can never arm a second stomp.
        state.setStompArmed(false);
        float power = state.getStompPower();
        state.setStompPower(0.0F);

        if (!player.isAlive() || player.isSpectator() || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        perform(player, power);
    }

    /** The landing itself: damage, launch, ground impact and the destruction of the shallow surface. */
    public static void perform(ServerPlayer player, float power)
    {
        ServerLevel level = (ServerLevel) player.level();
        QueenBeastTuning.Stomp stomp = QueenBeastTuning.get().stomp();
        Vec3 center = player.position();

        int hits = damage(level, player, center, stomp);

        destroySurface(level, player, center, stomp);
        spawnImpact(level, player, center, stomp);

        // Only the landing player feels the impulse of the landing, everybody else just sees the crater.
        HungryCraftNetwork.sendToTrackingAndSelf(new S2CBeastStompFeedbackPacket(player.getId()), player);

        spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.debug("[stomp] power={} hits={} at {} {} {}",
                power, hits, player.getX(), player.getY(), player.getZ());
    }

    /** The area damage: every legal target in the range takes the fixed damage and a heavy launch. */
    private static int damage(ServerLevel level, ServerPlayer player, Vec3 center, QueenBeastTuning.Stomp stomp)
    {
        double radius = stomp.radius();
        AABB box = new AABB(center.x - radius, center.y - stomp.verticalRange(), center.z - radius,
                center.x + radius, center.y + stomp.verticalRange(), center.z + radius);
        int hits = 0;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> isCandidate(player, candidate)))
        {
            if (!target.hurt(QueenBeastDamageTypes.stomp(player), (float) stomp.damage()))
            {
                continue;
            }

            Vec3 away = target.position().subtract(center);
            Vec3 outward = new Vec3(away.x, 0.0D, away.z);

            if (outward.lengthSqr() > 1.0E-4D)
            {
                outward = outward.normalize().scale(stomp.knockOutward());
            }
            else
            {
                outward = Vec3.ZERO;
            }

            // Pushed straight, without the knockback event and without the beast's own super armor: this is a
            // launch of the victim, not a shove against the beast.
            target.push(outward.x, stomp.knockUp(), outward.z);
            target.hurtMarked = true;
            hits++;
        }

        return hits;
    }

    /**
     * The ground impact of the landing: debris of whatever the surface really is, spread over the whole
     * footprint, plus dust, smoke and a few ground flashes. Purely cosmetic, no real explosion happens here.
     */
    private static void spawnImpact(ServerLevel level, ServerPlayer player, Vec3 center,
            QueenBeastTuning.Stomp stomp)
    {
        RandomSource random = player.getRandom();
        int feetY = Mth.floor(player.getY());
        double radius = stomp.radius();
        double dustY = surfaceHeight(level, BlockPos.containing(center.x, player.getY(), center.z), feetY);

        for (int index = 0; index < QueenBeastConstants.BEAST_STOMP_DEBRIS_SPOTS; index++)
        {
            double angle = (index + random.nextDouble()) / QueenBeastConstants.BEAST_STOMP_DEBRIS_SPOTS
                    * Math.PI * 2.0D;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            Vec3 spot = center.add(Math.cos(angle) * distance + jitter(random, SAMPLE_JITTER), 0.0D,
                    Math.sin(angle) * distance + jitter(random, SAMPLE_JITTER));
            Ground ground = findGround(level, BlockPos.containing(spot.x, center.y, spot.z), feetY);

            if (ground == null)
            {
                continue;
            }

            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.state()), spot.x,
                    ground.topY() + 0.08D, spot.z, QueenBeastConstants.BEAST_STOMP_DEBRIS_PARTICLES, DEBRIS_SPREAD,
                    0.08D, DEBRIS_SPREAD, DEBRIS_SPEED);
        }

        level.sendParticles(ParticleTypes.POOF, center.x, dustY + 0.15D, center.z,
                QueenBeastConstants.BEAST_STOMP_DUST_PUFFS, radius * 0.6D, 0.15D, radius * 0.6D, 0.05D);
        level.sendParticles(ParticleTypes.SMOKE, center.x, dustY + 0.2D, center.z,
                QueenBeastConstants.BEAST_STOMP_DUST_PUFFS, radius * 0.5D, 0.2D, radius * 0.5D, 0.03D);
        level.sendParticles(ParticleTypes.CLOUD, center.x, dustY + 0.1D, center.z, 10, radius * 0.35D, 0.05D,
                radius * 0.35D, 0.02D);

        // A ring of bursts along the footprint, so the shock wave is visible as a wave and not as one puff.
        for (int index = 0; index < QueenBeastConstants.BEAST_STOMP_IMPACT_FLASHES; index++)
        {
            double angle = (index + random.nextDouble()) / QueenBeastConstants.BEAST_STOMP_IMPACT_FLASHES
                    * Math.PI * 2.0D;
            double distance = radius * (0.4D + random.nextDouble() * 0.6D);

            level.sendParticles(ParticleTypes.EXPLOSION, center.x + Math.cos(angle) * distance, dustY + 0.1D,
                    center.z + Math.sin(angle) * distance, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        // One flash right under the body, which is where the whole weight of the landing really arrives.
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, player.getY() + 0.1D, center.z, 1, 0.0D,
                0.0D, 0.0D, 0.0D);
    }

    /**
     * The destruction of the landing: only the shallow band around the surface of the footprint is touched, and
     * only blocks the {@code block_breaking.stomp} rule allows. Nothing is dropped by default, so a single
     * landing can never spawn hundreds of item entities. The whole scan is capped by the stomp block budget.
     */
    private static void destroySurface(ServerLevel level, ServerPlayer player, Vec3 center,
            QueenBeastTuning.Stomp stomp)
    {
        int feetY = Mth.floor(player.getY());
        double radius = stomp.radius();
        int budget = QueenBeastConstants.BEAST_STOMP_MAX_BLOCKS;
        int broken = 0;

        for (double x = center.x - radius; x <= center.x + radius; x += 1.0D)
        {
            for (double z = center.z - radius; z <= center.z + radius; z += 1.0D)
            {
                if (broken >= budget)
                {
                    return;
                }

                double dx = x + 0.5D - center.x;
                double dz = z + 0.5D - center.z;

                if (dx * dx + dz * dz > radius * radius)
                {
                    continue;
                }

                BlockPos column = BlockPos.containing(x + 0.5D, center.y, z + 0.5D);

                // The landing floor plus one block up and down, never five layers into the ground.
                for (int y = feetY + 1; y >= feetY - 1; y--)
                {
                    if (broken >= budget)
                    {
                        return;
                    }

                    if (QueenBeastBlockBreaker.breakBlock(level, player, column.atY(y),
                            QueenBeastTuning.RuleProfile.STOMP))
                    {
                        broken++;
                    }
                }
            }
        }
    }

    /** @return true when that creature may be hit by the stomp of that player. */
    private static boolean isCandidate(ServerPlayer player, LivingEntity candidate)
    {
        if (candidate == player || candidate.isRemoved() || !candidate.isAlive())
        {
            return false;
        }

        if (candidate instanceof Player other)
        {
            return !other.isSpectator() && player.canHarmPlayer(other);
        }

        return true;
    }

    private static double jitter(RandomSource random, double amount)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    /** @return the height of the surface of that spot, used to place the dust of the impact. */
    private static double surfaceHeight(ServerLevel level, BlockPos column, int feetY)
    {
        Ground ground = findGround(level, column, feetY);

        return ground == null ? feetY : ground.topY();
    }

    /**
     * @return the first block with a real collision shape below that spot, or null in open air. The state is
     *         used for the debris particles, so grass, stone, sand or wood all produce their own material.
     */
    private static Ground findGround(ServerLevel level, BlockPos column, int feetY)
    {
        BlockPos.MutableBlockPos pos = column.mutable();

        for (int y = feetY + 1; y >= feetY - QueenBeastConstants.BEAST_STOMP_GROUND_SEARCH_DEPTH; y--)
        {
            pos.setY(y);

            BlockState state = level.getBlockState(pos);
            VoxelShape shape = state.getCollisionShape(level, pos);

            if (shape.isEmpty())
            {
                continue;
            }

            return new Ground(state, (double) y + shape.max(Direction.Axis.Y));
        }

        return null;
    }

    /** The material of the ground under the landing, plus the height its surface really has. */
    private record Ground(BlockState state, double topY)
    {
    }
}
