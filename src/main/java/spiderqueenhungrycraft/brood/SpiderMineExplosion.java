package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The blast of a spider mine (Spider Mine and Egg Arrow round), and the one thing that has to be protected from it.
 *
 * <p><b>The explosion is the explosion of the game.</b> Power four - the power of a block of TNT, exactly as the design
 * asks - the vanilla falloff, the vanilla knockback, the vanilla emitter, the vanilla sound and the vanilla blast rule
 * of {@link Level.ExplosionInteraction#TNT}, so a mine of this mod tears the world up exactly like the TNT it is built
 * around. Nothing about that is re-implemented here.
 *
 * <p><b>Its own children walk out of it unharmed.</b> The eight imp spiders are put down <i>before</i> the blast so
 * they really are inside it - that is what makes the picture - and every one of them is handed a short window of
 * {@link SpiderMinionEntity#grantExplosionGrace(int) explosion grace} first. The filter below then simply takes them
 * off the list of creatures the game is about to hurt, right before any damage is dealt. That is a property of
 * <b>these eight spiders</b> and not of their species: an imp spider that hatched from an egg, or one that was spawned
 * by a command, has no grace at all and burns in the very same blast like everything else.
 *
 * <p><b>Who owns the children.</b> The player who <b>placed</b> the mine - read from its block entity before the block
 * is consumed - and never the player who set the blast off. A mine a queen placed and a stranger triggered still gives
 * its spiders to the queen.
 *
 * <p>The mine itself is gone the moment it fires: the block is removed before the blast runs, so a trigger can never
 * happen twice and a mine never comes back as an item.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SpiderMineExplosion
{
    /** The power of the blast: the four of a block of TNT. */
    public static final float POWER = 4.0F;

    /**
     * How long a spider a mine delivered is spared by explosions, in ticks.
     *
     * <p>Half a second is far more than the one tick the blast of that very mine needs, which is the point: the window
     * only has to outlive the explosion it was granted for, and it is deliberately too short to be a shield.
     */
    public static final int EXPLOSION_GRACE_TICKS = 10;

    /** How far from the mine the eight spiders are put down. */
    private static final double SPAWN_RADIUS = 1.2D;

    private SpiderMineExplosion()
    {
    }

    /**
     * Fires one mine.
     *
     * <p>The caller only says where the mine is and why it went off; everything else - the spider count, the grace, the
     * blast and the hand-over of the spiders to their queen - is decided here, so the sceptre trigger and the redstone
     * trigger can never drift apart.
     *
     * @param level     the server level of the mine
     * @param pos       the position of the mine (and the centre of the blast)
     * @param byScepter true for the one trigger that is not redstone: the queen pointed her sceptre at it
     * @param queen     the queen who pointed the sceptre, only used for feedback
     */
    public static void detonate(ServerLevel level, BlockPos pos, boolean byScepter, @Nullable ServerPlayer queen)
    {
        // A mine that is not there any more is not fired: the redstone update that arrives after the blast, and a second
        // click on the same spot, both end here.
        if (!(level.getBlockState(pos).getBlock() instanceof SpiderMineBlock))
        {
            return;
        }

        UUID owner = level.getBlockEntity(pos) instanceof SpiderMineBlockEntity mine ? mine.getOwner() : null;

        // The mine is consumed by its own blast, deliberately before the blast runs: it can never be triggered twice and
        // it never drops itself as an item, exactly like the block of TNT it was built around.
        level.removeBlock(pos, false);

        // The centre of the blast is the centre of the block, and it is the work of nobody: `null` as its source keeps
        // the death message of a victim the ordinary one of an explosion of the world.
        blast(level, new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D), owner, byScepter, null);

        if (byScepter && queen != null && queen.connection != null)
        {
            // The click that fired the mine is answered in the action bar of the queen who made it. The spiders go to
            // the placer of the mine (handOver below) and not to the clicker, and the wording says exactly that.
            queen.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.spider_mine_detonated",
                            SpiderMineBlock.SPAWNED_SPIDERS), true);
        }
    }

    /**
     * The blast itself, wherever it comes from: a mine block that was fired, or the primed entity of a delayed mine
     * that ran out of fuse (Spider Trigger round).
     *
     * <p>The two entrances of a mine therefore hand the very same work to the very same method - the eight spiders,
     * the window that spares them from their own blast, the explosion of the game and the hand-over to the queen who
     * placed the mine - and nothing about the delay of the second one can ever drift away from the first.
     *
     * @param level     the server level of the blast
     * @param centre    the centre of the blast
     * @param owner     the placer of the mine, or null for a mine that was placed by anything else
     * @param byScepter true when the sceptre of the queen was the trigger, which is the only trigger that hands the
     *                  spiders over as a selection
     * @param source    the entity the blast is the work of, or null for a block that went off on its own
     */
    public static void blast(ServerLevel level, Vec3 centre, @Nullable UUID owner, boolean byScepter,
            @Nullable Entity source)
    {
        List<ImpSpiderEntity> spawned = spawnRing(level, centre, owner);

        // The grace has to be in place before the explosion exists, because the explosion hurts in the very same tick.
        for (ImpSpiderEntity spider : spawned)
        {
            spider.grantExplosionGrace(EXPLOSION_GRACE_TICKS);
        }

        level.explode(source, centre.x, centre.y, centre.z, POWER, false, Level.ExplosionInteraction.TNT);

        if (byScepter)
        {
            handOver(level, owner, spawned);
        }
    }

    /** @return the eight imp spiders of a mine, put down in a ring around it and already in the world. */
    private static List<ImpSpiderEntity> spawnRing(ServerLevel level, Vec3 centre, @Nullable UUID owner)
    {
        List<ImpSpiderEntity> spawned = new ArrayList<>(SpiderMineBlock.SPAWNED_SPIDERS);

        for (int index = 0; index < SpiderMineBlock.SPAWNED_SPIDERS; index++)
        {
            double angle = Math.PI * 2.0D / SpiderMineBlock.SPAWNED_SPIDERS * index;
            double x = centre.x + Math.cos(angle) * SPAWN_RADIUS;
            double z = centre.z + Math.sin(angle) * SPAWN_RADIUS;
            double y = floorY(level, x, centre.y + 0.5D, z);

            ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

            if (spider == null)
            {
                continue;
            }

            spider.setOwnerUUID(owner);
            spider.moveTo(x, y, z, level.random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(spider);
            spawned.add(spider);
        }

        return spawned;
    }

    /**
     * The selection side of a sceptre trigger: the spiders a mine delivered belong to the player who placed it, so they
     * become the group of <b>that</b> queen - and only when she is online to receive them, because a selection lives in
     * the queen data of a player.
     *
     * <p>A redstone trigger hands over nothing: those spiders are ordinary followers of their queen, which is exactly
     * what the design asks for.
     */
    private static void handOver(ServerLevel level, @Nullable UUID owner, List<ImpSpiderEntity> spawned)
    {
        if (owner == null || spawned.isEmpty() || level.getServer() == null)
        {
            return;
        }

        ServerPlayer placer = level.getServer().getPlayerList().getPlayer(owner);

        if (placer == null)
        {
            return;
        }

        List<UUID> ids = new ArrayList<>(spawned.size());

        for (ImpSpiderEntity spider : spawned)
        {
            ids.add(spider.getUUID());
        }

        BroodSelection.selectSpawned(placer, ids);
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

    /**
     * The one filter of a mine blast: a spider that was delivered by a mine and is still inside its grace window is
     * taken off the list of creatures the game is about to hurt.
     *
     * <p>It is the same hook the creeper spider uses for its own brood ({@code CreeperSpiderExplosionUtil}), and it only
     * ever touches spiders that really carry the grace - which only a mine hands out.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event)
    {
        event.getAffectedEntities()
                .removeIf(entity -> entity instanceof SpiderMinionEntity spider && spider.isSparedByExplosions());
    }
}
