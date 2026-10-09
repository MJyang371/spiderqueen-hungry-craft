package spiderqueenhungrycraft.bed;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The whole use interaction of a spider bed (Phase 8E3).
 *
 * <p>The order is the design, and it is the same order a vanilla bed uses:
 * <ol>
 * <li>a dimension in which beds do not work is answered with the vanilla bad respawn point explosion,
 * before any queen or beast rule can look at the player,</li>
 * <li>everybody who is not the Spider Queen leaves the bed alone completely,</li>
 * <li>the beast form of the queen is far too big for a bed, so it gets a flavour line and nothing else,</li>
 * <li>a humanoid queen always gets her respawn point, in daylight and in the night alike,</li>
 * <li>in daylight she then starts a hibernation session, at night she is simply told to go away.</li>
 * </ol>
 *
 * <p>Nothing here puts anybody to sleep. There is no sleeping pose, no bed occupancy, no sleeping player
 * list and no vanilla sleep pipeline: a hibernation is a session of its own, and the clock is only moved
 * when that session really ends.
 */
public final class SpiderBedInteraction
{
    /** The beast form of the queen does not fit into a bed. */
    private static final String BEAST_MESSAGE = "message.spiderqueen_hungrycraft.spider_bed.too_large";

    /** Night line: the queen is told that this is not the time to sleep. */
    private static final String NIGHT_MESSAGE = "message.spiderqueen_hungrycraft.spider_bed.night";

    /** Day line: the queen curls up until nightfall. */
    private static final String HIBERNATION_MESSAGE = "message.spiderqueen_hungrycraft.spider_bed.hibernate";

    /** The day time a hibernation advances to, the first tick of the vanilla night. */
    public static final long NIGHT_TIME = 13000L;

    /** Length of one full day and night in ticks. */
    public static final long DAY_LENGTH = 24000L;

    private SpiderBedInteraction()
    {
    }

    /**
     * Server authoritative use of a bed. The client only ever swings its arm, exactly like it does on a
     * vanilla bed, so a client can never decide on an explosion, a respawn point or a time jump.
     */
    public static InteractionResult use(Level level, BlockPos pos, Player player)
    {
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        // 1) The dimension rule comes first and it comes alone: not even a queen beast may talk her way out
        // of the explosion of a bed in a dimension where beds do not work. No respawn point is ever stored.
        if (!BedBlock.canSetSpawn(level))
        {
            explode(level, pos);
            return InteractionResult.SUCCESS;
        }

        // 2) A spider bed is a queen's furniture. Everybody else leaves it completely untouched, without a
        // hint, without a respawn point and without a single message.
        if (!QueenDataCapability.isQueen(player))
        {
            return InteractionResult.SUCCESS;
        }

        // 3) The beast form is too big for a bed: flavour line only, no respawn point and no time skip.
        if (QueenBeastState.isTransformed(player))
        {
            player.displayClientMessage(Component.translatable(BEAST_MESSAGE), true);
            return InteractionResult.SUCCESS;
        }

        // 4) A humanoid queen always gets her respawn point, whatever the time of day is. The vanilla call
        // stores the vanilla respawn state, keeps the vanilla "respawn point set" message and fires the
        // ordinary spawn events, so nothing about respawning has to be reinvented here.
        if (player instanceof ServerPlayer serverPlayer)
        {
            serverPlayer.setRespawnPosition(level.dimension(), pos, serverPlayer.getYRot(), false, true);
        }

        // 5) and 6) Daytime starts a real hibernation session, nighttime is nothing but the respawn point.
        if (level.isDay())
        {
            player.displayClientMessage(Component.translatable(HIBERNATION_MESSAGE), true);

            if (player instanceof ServerPlayer serverPlayer)
            {
                // The clock is not touched here at all: the night is only called in when this session has
                // really run its full length, never at the click.
                SpiderBedHibernation.start(serverPlayer, pos);
            }
        }
        else
        {
            player.displayClientMessage(Component.translatable(NIGHT_MESSAGE), true);
        }

        return InteractionResult.SUCCESS;
    }

    /**
     * The bad respawn point explosion, exactly the vanilla 1.20.1 bed: the bed is removed first, then the
     * world is blown up with the vanilla power of 5, fire enabled, block interaction enabled and the vanilla
     * bad respawn point damage source.
     */
    private static void explode(Level level, BlockPos pos)
    {
        level.removeBlock(pos, false);

        Vec3 center = pos.getCenter();
        level.explode(null, level.damageSources().badRespawnPointExplosion(center), null, center, 5.0F, true,
                Level.ExplosionInteraction.BLOCK);
    }

    /**
     * @return the day time of the next 13000 of the level, the first tick of the following night.
     *
     * <p>Time only ever moves forward: a hibernation in the early morning skips most of the day, one in the
     * afternoon skips only the evening, and one during the night skips a whole day to the next nightfall.
     */
    public static long nextNightTime(long dayTime)
    {
        long phase = Math.floorMod(dayTime, DAY_LENGTH);
        long delta = NIGHT_TIME - phase;

        if (delta <= 0L)
        {
            delta += DAY_LENGTH;
        }

        return dayTime + delta;
    }
}
