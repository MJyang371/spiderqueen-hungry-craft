package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The spider egg arrow (Spider Mine and Egg Arrow round): an ordinary arrow of the game with a clutch of spider eggs
 * instead of a head, loaded into a bow from the off hand.
 *
 * <p><b>It is a vanilla arrow in every way that matters.</b> It extends {@link Arrow}, so the flight, the gravity, the
 * ground behaviour, the damage formula of a bow of the game, Power, Punch and Flame and the ordinary arrow renderer's
 * model all come from the game itself. The launch is not copied either: the item of this arrow is simply part of the
 * {@code minecraft:arrows} item tag, so {@code BowItem} finds it as ammunition exactly like a plain arrow - and the
 * off hand is the first place a bow of the game looks, which is what makes "carry it in the off hand" work without a
 * single line of shooting logic of our own.
 *
 * <p><b>The difference is what happens on impact.</b> The moment it touches a creature or a block it flashes like an
 * explosion - purely as an effect: no block is broken, no point of damage is dealt by the flash - and three
 * {@link ImpSpiderEntity imp spiders} step out of it.
 *
 * <ul>
 * <li>Hit a block: three spiders appear near the spot and <b>patrol it</b>. They belong to their shooter, and their post
 * is the place they landed on.</li>
 * <li>Hit a creature: three spiders appear and are given the direct order to attack that very creature
 * ({@link SpiderMinionEntity#setForcedTarget}). Their order is {@link SpiderOrderMode#PATROL} beside the impact, so the
 * moment the victim dies or leaves the loaded world the order is the only thing left and they patrol there.</li>
 * <li>Hit the shooter herself, or somebody she may not attack: the attack order is refused by the owner protection of
 * the brood (see {@code QueenOwnerProtection}), and the spiders patrol right away.</li>
 * <li>Shot from a dispenser or anything else that is not a living creature: the spiders still come out and still attack
 * what the arrow hit, but they have <b>no owner</b> at all - nobody can command them and nobody can tame them.</li>
 * </ul>
 */
public class SpiderEggArrow extends Arrow
{
    /** How many imp spiders one arrow delivers. */
    public static final int SPAWNED_SPIDERS = 3;

    /** How far from the impact the spiders are put down. */
    private static final double SPAWN_RADIUS = 1.4D;

    /** The type of this arrow, as the registry knows it. */
    public SpiderEggArrow(EntityType<? extends SpiderEggArrow> entityType, Level level)
    {
        super(entityType, level);
    }

    /**
     * An arrow fired by that creature, the way {@code ArrowItem#createArrow} of the game builds one: the concrete type
     * of this arrow, the shooter as its owner and its position at the eyes of the shooter.
     */
    public SpiderEggArrow(Level level, LivingEntity shooter)
    {
        super(HungryCraftEntities.SPIDER_EGG_ARROW.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    /**
     * An arrow fired by a dispenser, which owns nobody.
     *
     * <p>It exists because a dispenser is not a living creature: the vanilla ammunition behaviour builds an arrow from a
     * position and no shooter, and this is that arrow.
     */
    public SpiderEggArrow(Level level, double x, double y, double z)
    {
        super(HungryCraftEntities.SPIDER_EGG_ARROW.get(), level);
        this.setPos(x, y, z);
    }

    /** The impact on a creature: the ordinary damage of an arrow of the game, and then the clutch. */
    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        this.burst(this.position(), result.getEntity() instanceof LivingEntity living ? living : null);
    }

    /** The impact on a block: nothing is damaged, and the clutch steps out at the point of impact. */
    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        this.burst(result.getLocation(), null);
    }

    /**
     * The one impact of this arrow: the flash, the three spiders and the end of the arrow.
     *
     * <p>It is server side only, and the test is the logical side: a client repeats the hit of a projectile in its own
     * world, and nothing there may ever spawn a spider.
     *
     * @param where  the point of impact, in the world
     * @param victim the creature that was hit, or null for a block (and for the objects of the world a plain arrow also
     *               hits without hurting them)
     */
    private void burst(Vec3 where, @Nullable LivingEntity victim)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            this.discard();

            return;
        }

        // The shooter is the owner, and only a player can be one: a dispenser's arrow, and the arrow of any other
        // creature, has none - and a spider without an owner can be commanded by nobody.
        UUID owner = this.getOwner() instanceof Player player ? player.getUUID() : null;

        flash(level, where);

        for (int index = 0; index < SPAWNED_SPIDERS; index++)
        {
            spawn(level, where, index, owner, victim);
        }

        this.discard();
    }

    /** The explosion that breaks nothing and hurts nobody: the emitter of the game, in a small puff, and its sound. */
    private static void flash(ServerLevel level, Vec3 where)
    {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, where.x, where.y, where.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, where.x, where.y, where.z, 8, 0.7D, 0.7D, 0.7D, 0.0D);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    /**
     * One of the three spiders of an impact.
     *
     * <p>It is put down near the point of impact, on the first free spot with solid ground under it, and the impact is
     * its post: <b>every</b> spider of a clutch patrols where it landed. That is not decoration but the whole point of
     * the arrow - a fresh spider is a follower, and a follower whose queen is more than
     * {@link spiderqueenhungrycraft.brood.goal.SpiderFollowOwnerGoal#FOLLOW_TELEPORT_DISTANCE thirty-two blocks} away
     * teleports straight back to her, so a clutch shot at anything further than that would be gone from the landing
     * spot before the next tick and never deploy anywhere. A patrol beside the impact keeps the spiders where they were
     * meant to be, and the sceptre of the queen can still send them wherever she wants them afterwards.
     *
     * <p>A victim turns the whole clutch into a patrol that was sent at that victim: the direct order
     * ({@link SpiderMinionEntity#setForcedTarget}) is refused by the owner protection when the victim is the shooter
     * herself or a creature of the same queen, and the patrol order beside the impact is then simply what is left - so
     * "she shot herself" and "the victim died" end in the very same state, which is exactly what the design asks for.
     */
    private static void spawn(ServerLevel level, Vec3 around, int index, @Nullable UUID owner,
            @Nullable LivingEntity victim)
    {
        double angle = Math.PI * 2.0D / SPAWNED_SPIDERS * index + level.random.nextDouble() * 0.5D;
        double x = around.x + Math.cos(angle) * SPAWN_RADIUS;
        double z = around.z + Math.sin(angle) * SPAWN_RADIUS;
        double y = floorY(level, x, around.y, z);

        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

        if (spider == null)
        {
            return;
        }

        spider.setOwnerUUID(owner);
        spider.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(spider);

        // The impact is the post of this spider, whatever the arrow met: patrol and not follow, because a follower of a
        // queen that is far away is teleported back to her at once and the landing spot would be empty.
        spider.holdAt(BlockPos.containing(x, y, z));
        spider.setOrderMode(SpiderOrderMode.PATROL);

        if (victim != null)
        {
            spider.setForcedTarget(victim);
        }
    }

    /** @return the height a spider should stand at near that column: free space with solid ground right below it. */
    private static double floorY(ServerLevel level, double x, double y, double z)
    {
        BlockPos start = BlockPos.containing(x, y, z);

        for (int step = 2; step >= -4; step--)
        {
            BlockPos candidate = start.offset(0, step, 0);

            if (isFree(level, candidate) && isFree(level, candidate.above()) && !isFree(level, candidate.below()))
            {
                return candidate.getY();
            }
        }

        return Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
    }

    /** @return true when that block has no collision at all. */
    private static boolean isFree(ServerLevel level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
