package spiderqueenhungrycraft.bed;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.devour.DevourInput;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CSpiderBedHibernationPacket;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The Spider Bed Hibernation session (Phase 8E3 patch): the daylight rest of the Spider Queen, as an
 * experience instead of a command.
 *
 * <p>A session starts when a humanoid queen right clicks her bed in daylight and it lasts
 * {@link #DURATION_TICKS} ticks, the very same hundred ticks the vanilla sleep timer needs to be "long
 * enough". While it runs the queen is frozen on the server (movement, attacks, mining, item use and every
 * skill of the mod are refused), her client shows a screen with the vanilla leave bed button and the view
 * fades into the dark.
 *
 * <p>The time of day is advanced <em>only</em> when the session really finished. Getting up early, dying,
 * logging out, changing the dimension, losing the queen flag, turning into a beast or losing the bed all end
 * the session without touching the clock at all.
 *
 * <p>This is still not the vanilla sleep: no sleeping pose, no bed occupancy, no sleeping player list, no
 * {@code playersSleepingPercentage} and no weather change. A session is runtime only and is never saved.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SpiderBedHibernation
{
    /** Length of one hibernation, the hundred ticks the vanilla sleep timer needs to be "long enough". */
    public static final int DURATION_TICKS = 100;

    /** How far a frozen queen may drift away from her bed before she is put back, in blocks. */
    private static final double MAX_DRIFT = 1.5D;

    /** Every running session, keyed by the player and runtime only. */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private SpiderBedHibernation()
    {
    }

    /** One running hibernation. Never saved, never synced as a whole, only as a start and a stop. */
    private static final class Session
    {
        private final ResourceKey<Level> dimension;
        private final BlockPos bedPos;
        private final Vec3 anchor;
        private int elapsedTicks;

        Session(ResourceKey<Level> dimension, BlockPos bedPos, Vec3 anchor)
        {
            this.dimension = dimension;
            this.bedPos = bedPos;
            this.anchor = anchor;
        }
    }

    /** @return true while that player is inside a hibernation and therefore must not act. */
    public static boolean isHibernating(Player player)
    {
        return player != null && SESSIONS.containsKey(player.getUUID());
    }

    /** @return true when the actions of that player have to be refused right now. */
    public static boolean blocksActions(Player player)
    {
        return isHibernating(player);
    }

    /**
     * Starts a hibernation for that queen. The respawn point and the flavour line are already done by the
     * caller, the clock is not touched here at all.
     */
    public static void start(ServerPlayer player, BlockPos bedPos)
    {
        // Never two sessions for one player, and never a leftover press of the devour key inside a session.
        SESSIONS.remove(player.getUUID());
        DevourInput.clear(player);

        Session session = new Session(player.level().dimension(), bedPos.immutable(), player.position());
        SESSIONS.put(player.getUUID(), session);

        HungryCraftNetwork.sendToPlayer(new S2CSpiderBedHibernationPacket(true, DURATION_TICKS), player);
        freeze(player, session);

        SpiderQueenHungryCraft.LOGGER.debug("[hibernation] started player={} bed={}",
                player.getGameProfile().getName(), bedPos);
    }

    /** The client asked to get up again: the session ends and the clock stays exactly where it is. */
    public static void leave(ServerPlayer player)
    {
        cancel(player, "leave");
    }

    /**
     * One server tick of that player. The session is validated first, so a bed that is gone, a queen who is
     * not a queen any more, a beast, a death or a world change all end the session before anything else
     * happens, and none of them ever moves the clock.
     */
    public static void tick(ServerPlayer player)
    {
        Session session = SESSIONS.get(player.getUUID());

        if (session == null)
        {
            return;
        }

        if (!isStillValid(player, session))
        {
            cancel(player, "invalid");
            return;
        }

        session.elapsedTicks++;
        freeze(player, session);

        if (session.elapsedTicks >= DURATION_TICKS)
        {
            complete(player, session);
        }
    }

    /** @return true while the session may still run: alive, still the queen, not a beast, bed still there. */
    private static boolean isStillValid(ServerPlayer player, Session session)
    {
        if (player.isRemoved() || !player.isAlive() || player.isSpectator())
        {
            return false;
        }

        if (!player.level().dimension().equals(session.dimension))
        {
            return false;
        }

        if (!QueenDataCapability.isQueen(player) || QueenBeastState.isTransformed(player))
        {
            return false;
        }

        BlockState state = player.level().getBlockState(session.bedPos);
        return state.is(HungryCraftBlocks.SPIDER_BED.get());
    }

    /** Keeps a hibernating queen exactly where she curled up, without ever pushing her into a wall. */
    private static void freeze(ServerPlayer player, Session session)
    {
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.setSprinting(false);
        player.hurtMarked = true;

        Vec3 drift = session.anchor.subtract(player.position());

        if (drift.length() <= MAX_DRIFT)
        {
            return;
        }

        // The anchor was free when the session started and nobody is allowed to move her, so pulling her back
        // is only ever a correction after an outside force, never the normal case.
        if (player.level().noCollision(player, player.getBoundingBox().move(drift)))
        {
            player.teleportTo(session.anchor.x, session.anchor.y, session.anchor.z);
            player.hurtMarked = true;
        }
    }

    /** The session ran its full length: now, and only now, the night is called in. */
    private static void complete(ServerPlayer player, Session session)
    {
        SESSIONS.remove(player.getUUID());
        HungryCraftNetwork.sendToPlayer(new S2CSpiderBedHibernationPacket(false, 0), player);
        player.hurtMarked = true;

        SpiderQueenHungryCraft.LOGGER.debug("[hibernation] completed player={} ticks={}",
                player.getGameProfile().getName(), session.elapsedTicks);

        if (player.level() instanceof ServerLevel serverLevel)
        {
            // The same hook vanilla uses when a night is slept through, so a time controlling mod still sees
            // this jump. The weather cycle is never touched.
            serverLevel.setDayTime(ForgeEventFactory.onSleepFinished(serverLevel,
                    SpiderBedInteraction.nextNightTime(serverLevel.getDayTime()), serverLevel.getDayTime()));
        }

        // The rest really happened, so the rest counter is reset and the vanilla rest rewards are granted
        // through the ordinary public hooks. Nothing of this touches a sleeping pose or a bed state.
        player.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        player.awardStat(Stats.SLEEP_IN_BED);
        CriteriaTriggers.SLEPT_IN_BED.trigger(player);
    }

    /** Ends a session without a time jump, whatever the reason was. */
    private static void cancel(ServerPlayer player, String reason)
    {
        if (SESSIONS.remove(player.getUUID()) == null)
        {
            return;
        }

        HungryCraftNetwork.sendToPlayer(new S2CSpiderBedHibernationPacket(false, 0), player);
        player.hurtMarked = true;

        SpiderQueenHungryCraft.LOGGER.debug("[hibernation] cancelled player={} reason={}",
                player.getGameProfile().getName(), reason);
    }

    // ---------------------------------------------------------------- events

    /** The heartbeat of every session, and the only place that can ever finish one. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }

        tick(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            cancel(player, "logout");
        }
    }

    /** A death ends the session before the respawn, so nothing of it survives into the next life. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        if (event.getOriginal() instanceof ServerPlayer player)
        {
            cancel(player, "clone");
        }

        if (event.getEntity() instanceof ServerPlayer player)
        {
            cancel(player, "clone");
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            cancel(player, "dimension");
        }
    }

    /** No mining, no placing, no eating, no bucket and no using anything on a block. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event)
    {
        if (blocksActions(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (blocksActions(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (blocksActions(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        if (blocksActions(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    /** No breaking a block, whatever path is used to try it. */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event)
    {
        if (event.getPlayer() != null && blocksActions(event.getPlayer()))
        {
            event.setCanceled(true);
        }
    }

    /** No hitting anything, bare handed or with a tool. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event)
    {
        if (blocksActions(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }
}
