package spiderqueenhungrycraft.brood.goal;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * The follow goal of a spider minion (Brood Command AI), the vanilla pet feel: walk after the owner when it is far
 * enough away, stop when it is close, and never teleport through the world.
 *
 * <p>It is nothing like a leash: the path is recalculated by the ordinary navigation of the game (wall climbing and
 * every other spider ability included), and the only emergency exit is the safe teleport below, which copies the
 * behaviour of a tamed wolf - it is only used when the spider is really far away, has nothing to fight, and only to a
 * spot that is free, has ground under it and is not inside a liquid.
 */
public class SpiderFollowOwnerGoal extends Goal
{
    /** The spider starts walking once the owner is further away than this. */
    public static final double START_DISTANCE = 10.0D;

    /** It stops walking once it is this close again. */
    public static final double STOP_DISTANCE = 3.0D;

    /**
     * Distance at which the spider gives up walking and tries to teleport, in blocks (32 blocks, so a queen who is
     * teleported a little further away still walks - her escort is only pulled in when it really cannot catch up).
     */
    public static final double FOLLOW_TELEPORT_DISTANCE = 32.0D;

    /** How often a new path is calculated. */
    private static final int PATH_RECALC_TICKS = 10;

    /** How often the safe teleport is tried at most. */
    private static final int TELEPORT_RETRY_TICKS = 20;

    /** How many positions around the owner are tried before the spider gives up for now. */
    private static final int TELEPORT_ATTEMPTS = 10;

    protected final SpiderMinionEntity spider;

    private int recalcTicks;
    private int teleportTicks;

    public SpiderFollowOwnerGoal(SpiderMinionEntity spider)
    {
        this.spider = spider;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (this.spider.getOrderMode() != SpiderOrderMode.FOLLOW || this.spider.getTemporaryCommand().isFight())
        {
            return false;
        }

        // A spider that is on a scout move belongs to the sceptre for now: it must be able to walk sixty blocks away
        // without being pulled back by its own follow goal (and the teleport below is part of that).
        if (this.spider.getMoveAnchor() != null)
        {
            return false;
        }

        Player owner = this.owner();

        return owner != null && this.spider.distanceToSqr(owner) > START_DISTANCE * START_DISTANCE;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.spider.getOrderMode() != SpiderOrderMode.FOLLOW || this.spider.getMoveAnchor() != null)
        {
            return false;
        }

        Player owner = this.owner();

        return owner != null && this.spider.distanceToSqr(owner) > STOP_DISTANCE * STOP_DISTANCE;
    }

    @Override
    public void start()
    {
        this.recalcTicks = 0;
        this.teleportTicks = 0;
    }

    @Override
    public void stop()
    {
        this.spider.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        Player owner = this.owner();

        if (owner == null)
        {
            return;
        }

        this.spider.getLookControl().setLookAt(owner, 10.0F, (float) this.spider.getMaxHeadXRot());

        if (this.recalcTicks > 0)
        {
            this.recalcTicks--;
        }

        if (this.recalcTicks <= 0)
        {
            this.recalcTicks = this.adjustedTickDelay(PATH_RECALC_TICKS);
            this.spider.getNavigation().moveTo(owner, 1.0D);
        }

        // Walking is always preferred; the teleport is the last resort of a pet that got stuck, exactly like a wolf.
        if (this.teleportTicks > 0)
        {
            this.teleportTicks--;
        }

        if (this.teleportTicks <= 0
                && this.spider.distanceToSqr(owner) > FOLLOW_TELEPORT_DISTANCE * FOLLOW_TELEPORT_DISTANCE
                && !this.spider.isInCombat()
                && this.spider.getMoveAnchor() == null)
        {
            this.teleportTicks = this.adjustedTickDelay(TELEPORT_RETRY_TICKS);
            teleportNear(owner);
        }
    }

    @Nullable
    protected Player owner()
    {
        return this.spider.getOwnerPlayer();
    }

    /**
     * Tries to move the spider to a safe spot around the owner.
     *
     * <p>Several positions are tried, and every one of them has to pass the same checks the game uses for a teleport
     * of its own: a free collision box (never inside a solid block) and no liquid around it (never into lava). The
     * owner's own position is never used as a destination.
     */
    public void teleportNear(Player owner)
    {
        teleportToOwner(this.spider, owner);
    }

    /**
     * Brings one spider to a safe spot around its owner, the way a pet is set down next to its master.
     *
     * <p>It is the one emergency move of the brood: the follow goal uses it when a spider fell behind hopelessly, and the
     * hard rally of the queen (the shift and right click of the sceptre) uses it to pull her escort home at once instead
     * of letting it walk - both go through this single method, so the two can never place a spider differently.
     *
     * @return true when the spider really arrived
     */
    public static boolean teleportToOwner(SpiderMinionEntity spider, Player owner)
    {
        Level level = spider.level();
        BlockPos base = owner.blockPosition();

        for (int attempt = 0; attempt < TELEPORT_ATTEMPTS; attempt++)
        {
            BlockPos target = findSafeSpot(level, base);

            if (target != null && spider.randomTeleport(target.getX() + 0.5D, target.getY(),
                    target.getZ() + 0.5D, true))
            {
                spider.getNavigation().stop();
                Vec3 where = spider.position();
                level.playSound(null, where.x, where.y, where.z, SoundEvents.SPIDER_AMBIENT, SoundSource.NEUTRAL,
                        0.6F, 1.5F);

                return true;
            }
        }

        return false;
    }

    /**
     * @return one free and safe spot near that base, or null when the first try happened to be a bad one.
     *
     * <p>This is the one and only safe placement of the brood: the follow goal teleports with it, and the escort
     * relocation of the queen (Brood Selection + Escort Relocation round) uses the very same method to decide where a
     * relocated spider may stand, so a teleport never lands inside a wall, in lava or over the void.
     */
    @Nullable
    public static BlockPos findSafeSpot(Level level, BlockPos base)
    {
        int x = level.random.nextInt(7) - 3;
        int y = level.random.nextInt(3) - 1;
        int z = level.random.nextInt(7) - 3;

        if (x == 0 && z == 0)
        {
            return null;
        }

        BlockPos target = base.offset(x, y, z);

        return isSafeSpot(level, target) ? target : null;
    }

    /** @return true when a spider may stand there: free space, ground under it, no liquid and inside the world border. */
    public static boolean isSafeSpot(Level level, BlockPos pos)
    {
        return level.isEmptyBlock(pos) && !level.isEmptyBlock(pos.below()) && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.below()).isEmpty() && level.getWorldBorder().isWithinBounds(pos);
    }
}
