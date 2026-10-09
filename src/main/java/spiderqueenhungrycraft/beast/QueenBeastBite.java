package spiderqueenhungrycraft.beast;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.attack.EmpoweredAttackHandler;
import spiderqueenhungrycraft.attack.VenomStrike;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastBiteFeedbackPacket;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The beast bite of the Queen Beast form (Phase 8B2).
 *
 * <p>One bite is one area attack in front of the spider: a volume of three blocks width, two blocks height and
 * two blocks depth by default, starting at the front edge of the beast body. Those three sizes, the armor of
 * the form and the camera impulse of the bite are read from the datapack tuning
 * ({@link QueenBeastTuning}) every time they are used, so a modpack retunes them with a json file and a
 * {@code /reload} instead of a recompiled mod. Only the horizontal look direction matters, so a player who
 * aims at the sky still bites the ground in front of it, and the volume starts at the feet so small prey is
 * really reachable.
 *
 * <p>The order of one accepted bite is deliberate: the cooldown, then the animation and the impact feedback,
 * and only then the victims. Snapping at empty air therefore plays the whole attack - the lunge, the impulse,
 * the broken ground and the sound - with nothing to damage, which is exactly what a heavy swing at nothing
 * should look like.
 *
 * <p>Every victim takes the plain bite damage. If a venom strike was prepared, this one bite releases it: every
 * victim of the bite takes the doubled damage and the venom debuffs, and the preparation is consumed exactly
 * once, no matter how many creatures were hit. A bite that hits nobody keeps the preparation, because the fangs
 * of a spider are not spent by snapping at air.
 *
 * <p>Everything here is server side and decided from the real player state; the client only announces that the
 * attack key was pressed and receives the feedback for the animation afterwards.
 */
public final class QueenBeastBite
{
    /** Vertical range of the bite search a little below the feet, so a slope is still caught. */
    private static final double SEARCH_DOWN = 1.0D;

    /**
     * How far the bite volume reaches in total, for the broad phase search box. It is derived from the tuning
     * of the moment (Phase 8B-POLISH-2) and not cached, so a {@code /reload} changes the reach of the very next
     * bite instead of the next server start.
     */
    private static double broadReach()
    {
        QueenBeastTuning.Values values = QueenBeastTuning.get();

        return values.biteWidth() / 2.0D + values.biteDepth() / 2.0D;
    }

    /** How far the ground samples keep away from the very edge of the bite footprint. */
    private static final double GROUND_SAMPLE_MARGIN = 0.2D;

    /** Random jitter of a ground sample spot, so the sampling grid itself is never visible. */
    private static final double GROUND_JITTER = 0.25D;

    /** The spread and speed of the debris, and how fast the dust of the impact rises. */
    private static final double GROUND_DEBRIS_SPREAD = 0.22D;
    private static final double GROUND_DEBRIS_SPEED = 0.16D;
    private static final double GROUND_DUST_SPEED = 0.04D;

    private QueenBeastBite()
    {
    }

    /** Server entry point: the client says the attack key was pressed while looking at something edible. */
    public static void tryBite(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player))
        {
            return;
        }

        if (!QueenBeastState.isTransformed(player))
        {
            return;
        }

        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        long now = player.level().getGameTime();
        long last = state.getLastBiteGameTime();

        // The server owns the pace. A client that sends a bite every tick only gets the first one through.
        if (last >= 0L && now - last < QueenBeastConstants.BEAST_BITE_COOLDOWN_TICKS)
        {
            return;
        }

        state.setLastBiteGameTime(now);

        Vec3 forward = horizontalForward(player);
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        // 1) The bite is announced, not earned: the animation, the impulse, the ground impact and the sound
        // are the answer to an accepted bite, so they happen before anything is even searched. Snapping at
        // empty air slams the ground exactly like biting a cow, only without a victim.
        HungryCraftNetwork.sendToTrackingAndSelf(new S2CBeastBiteFeedbackPacket(player.getId()), player);

        // 2) The impact of the bite happens in front of the beast, not under its own feet, so the debris and
        // the sound both come from the spot the fangs really reach.
        Vec3 impactCenter = player.position().add(forward.scale(
                QueenBeastConstants.BEAST_WIDTH / 2.0D + QueenBeastTuning.get().biteDepth() / 2.0D));

        spawnGroundImpact(player, forward, right, impactCenter);
        playBiteSound(player, impactCenter);

        // 3) Only now the fangs look for something to hurt. The venom is read once, before any damage happens,
        // so a whole area bite is either venomous or plain.
        boolean venom = EmpoweredAttackHandler.isArmed(player);
        float damage = QueenBeastConstants.BEAST_BITE_DAMAGE
                * (venom ? QueenBeastConstants.BEAST_BITE_VENOM_MULTIPLIER : 1.0F);
        int hits = 0;

        for (LivingEntity target : targetsInFront(player, forward, right,
                candidate -> isCandidate(player, candidate)))
        {
            if (!target.hurt(QueenBeastDamageTypes.bite(player), damage))
            {
                continue;
            }

            if (venom)
            {
                VenomStrike.applyEffects(target);
            }

            hits++;
        }

        // Consumed once, and only when the bite really landed on somebody: a spider does not spend its venom
        // on empty air.
        if (venom && hits > 0)
        {
            EmpoweredAttackHandler.clearArmed(player);
        }
    }

    /** The horizontal look direction of the player. A bite is aimed at the ground, never at the sky. */
    public static Vec3 horizontalForward(ServerPlayer player)
    {
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0.0D, look.z);

        if (forward.lengthSqr() < 1.0E-4D)
        {
            // Looking straight up or down: fall back to the body direction instead of a zero vector.
            forward = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        return forward.normalize();
    }

    /**
     * @return every creature in front of that beast that the given filter accepts, already filtered by the bite
     *         volume, the reach and the line of sight.
     *
     * <p>This is the one geometry of the beast: a bite asks for "who may be bitten" and a devour asks for "who
     * may be swallowed", and both get the very same 3 x 2 x 2 jaws, the very same horizontal look orientation,
     * the very same feet based vertical semantics and the very same block clipping. A second, slightly different
     * search would be a bite that reaches somewhere else than it looks, which is exactly what this signature
     * exists to prevent.
     */
    public static List<LivingEntity> targetsInFront(ServerPlayer player, Vec3 forward, Vec3 right,
            java.util.function.Predicate<LivingEntity> filter)
    {
        // The three sizes are the server authoritative values of the current reload. A client can neither see
        // nor influence them, so the reach of a bite is decided here and only here.
        QueenBeastTuning.Values values = QueenBeastTuning.get();
        double halfWidth = values.biteWidth() / 2.0D;
        double halfDepth = values.biteDepth() / 2.0D;
        double gap = QueenBeastConstants.BEAST_WIDTH / 2.0D;
        double reach = broadReach();

        // The volume starts at the front edge of the beast body, never inside its own belly.
        Vec3 center = player.position().add(forward.scale(gap + halfDepth));
        double minY = player.getY();
        double maxY = minY + values.biteHeight();

        AABB search = new AABB(center.x - reach, minY - SEARCH_DOWN, center.z - reach,
                center.x + reach, maxY + SEARCH_DOWN, center.z + reach);

        List<LivingEntity> found = new ArrayList<>();

        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, search,
                candidate -> filter.test(candidate)))
        {
            AABB box = target.getBoundingBox();

            if (box.maxY < minY || box.minY > maxY)
            {
                continue;
            }

            // Yaw aligned test: how far the target is along and beside the bite direction, widened by the
            // radius of the target itself, so a wide creature is caught by the edge of the jaws as well.
            Vec3 offset = box.getCenter().subtract(center);
            double radius = target.getBbWidth() / 2.0D;

            if (Math.abs(offset.dot(forward)) > halfDepth + radius)
            {
                continue;
            }

            if (Math.abs(offset.dot(right)) > halfWidth + radius)
            {
                continue;
            }

            if (!hasLineOfSight(player, target))
            {
                continue;
            }

            found.add(target);
        }

        return found;
    }

    /** @return true when that creature may be bitten at all. Vanilla decides the team and PVP rules. */
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

    /** @return true when no solid block sits between the head of the beast and that creature. */
    private static boolean hasLineOfSight(ServerPlayer player, LivingEntity target)
    {
        Vec3 from = player.getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        BlockHitResult hit = player.level()
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        return hit.getType() == HitResult.Type.MISS;
    }

    /**
     * The "the ground in front was bitten" impact: debris of whatever the bite really hit, spread over the whole
     * footprint, plus dust and a few ground bursts, so it reads as one heavy slam instead of a puff under a mob.
     *
     * <p>It is purely visual: no block is ever broken and no real explosion happens. A bite that hit nobody
     * still slams the ground, because the fangs did reach the floor.
     */
    private static void spawnGroundImpact(ServerPlayer player, Vec3 forward, Vec3 right, Vec3 center)
    {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();

        double gap = QueenBeastConstants.BEAST_WIDTH / 2.0D;
        QueenBeastTuning.Values values = QueenBeastTuning.get();
        double halfWidth = values.biteWidth() / 2.0D;
        double depth = values.biteDepth() - GROUND_SAMPLE_MARGIN;
        int feetY = Mth.floor(player.getY());

        // A jittered grid across the whole footprint, so the impact covers the full width and depth of the bite.
        for (int column = 0; column < QueenBeastConstants.BEAST_BITE_IMPACT_COLUMNS; column++)
        {
            for (int row = 0; row < QueenBeastConstants.BEAST_BITE_IMPACT_ROWS; row++)
            {
                double side = ((column + 0.5D) / QueenBeastConstants.BEAST_BITE_IMPACT_COLUMNS * 2.0D - 1.0D)
                        * (halfWidth - GROUND_SAMPLE_MARGIN);
                double front = gap + (row + 0.5D) / QueenBeastConstants.BEAST_BITE_IMPACT_ROWS * depth;
                Vec3 spot = player.position().add(forward.scale(front + jitter(random, GROUND_JITTER)))
                        .add(right.scale(side + jitter(random, GROUND_JITTER)));
                Ground ground = findGround(level, BlockPos.containing(spot.x, player.getY(), spot.z), feetY);

                if (ground == null)
                {
                    continue;
                }

                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.state()), spot.x,
                        ground.topY() + 0.08D, spot.z, QueenBeastConstants.BEAST_BITE_DEBRIS_PARTICLES,
                        GROUND_DEBRIS_SPREAD, 0.05D, GROUND_DEBRIS_SPREAD, GROUND_DEBRIS_SPEED);
            }
        }

        // Dust that rises out of the whole footprint, so the slam has volume instead of a single point.
        Ground centerGround = findGround(level, BlockPos.containing(center.x, player.getY(), center.z), feetY);
        double dustY = centerGround == null ? player.getY() + 0.15D : centerGround.topY() + 0.15D;

        level.sendParticles(ParticleTypes.POOF, center.x, dustY, center.z,
                QueenBeastConstants.BEAST_BITE_DUST_PUFFS, halfWidth, 0.1D, depth / 2.0D, GROUND_DUST_SPEED);
        level.sendParticles(ParticleTypes.SMOKE, center.x, dustY, center.z,
                QueenBeastConstants.BEAST_BITE_DUST_PUFFS, halfWidth, 0.15D, depth / 2.0D,
                GROUND_DUST_SPEED * 0.6D);

        // A few bursts along the reach of the fangs, so the ground itself erupts instead of a single spot.
        for (int index = 0; index < QueenBeastConstants.BEAST_BITE_IMPACT_FLASHES; index++)
        {
            double side = (random.nextDouble() * 2.0D - 1.0D) * halfWidth;
            Vec3 flash = center.add(right.scale(side)).add(forward.scale(jitter(random, depth / 2.0D)));

            level.sendParticles(ParticleTypes.EXPLOSION, flash.x, dustY + 0.05D, flash.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static double jitter(RandomSource random, double amount)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * amount;
    }

    /**
     * @return the first block with a real collision shape below that spot, or null when the bite looks into
     *         open air. The state is used for the debris particles, so grass, stone, sand or wood all produce
     *         their own material without a single new texture.
     */
    private static Ground findGround(ServerLevel level, BlockPos column, int feetY)
    {
        BlockPos.MutableBlockPos pos = column.mutable();

        for (int y = feetY + 1; y >= feetY - QueenBeastConstants.BEAST_BITE_GROUND_SEARCH_DEPTH; y--)
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

    /** The material the bite hit, plus the height its surface really has. */
    private record Ground(BlockState state, double topY)
    {
    }

    /** Deliberately quiet sounds, played at the impact spot: a bite may happen twice per second. */
    private static void playBiteSound(ServerPlayer player, Vec3 center)
    {
        ServerLevel level = (ServerLevel) player.level();

        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.HOSTILE, 0.55F,
                0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.45F,
                0.5F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 0.35F,
                0.85F);
    }
}
