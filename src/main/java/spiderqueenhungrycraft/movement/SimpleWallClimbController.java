package spiderqueenhungrycraft.movement;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.bed.SpiderBedHibernation;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CWallClimbStatePacket;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The whole wall climb of the mod (Phase 6-RESET): one enabled flag per player plus the ladder-like physics.
 *
 * <p>There is no attach state, no surface direction, no transition and no anchor. The only thing that is
 * stored is whether the player switched the mode on, and even that is runtime only and never persisted.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SimpleWallClimbController
{
    private static final String MESSAGE_ON = "message.spiderqueen_hungrycraft.wall_crawl_on";
    private static final String MESSAGE_OFF = "message.spiderqueen_hungrycraft.wall_crawl_off";

    /** Server authoritative flag per player, runtime only. */
    private static final Map<Player, Boolean> ENABLED = new WeakHashMap<>();

    /** Client mirror of the flag for the local player. Only ever read by client side entities. */
    private static boolean clientEnabled;

    private SimpleWallClimbController()
    {
    }

    /** @return true when that player may climb walls right now, answered from its own logical side. */
    public static boolean isEnabled(Player player)
    {
        if (player.level().isClientSide())
        {
            return clientEnabled;
        }

        return ENABLED.getOrDefault(player, Boolean.FALSE);
    }

    /** Client only: applies the flag the server sent for the local player. */
    public static void acceptClientState(boolean enabled)
    {
        clientEnabled = enabled;
    }

    /** Client request to switch the mode on or off. */
    public static void toggle(ServerPlayer player)
    {
        if (!canUse(player))
        {
            return;
        }

        boolean enabled = !ENABLED.getOrDefault(player, Boolean.FALSE);
        ENABLED.put(player, enabled);

        SpiderQueenHungryCraft.LOGGER.debug("[wallclimb] toggle player={} enabled={}", player.getGameProfile().getName(),
                enabled);

        HungryCraftNetwork.sendToPlayer(new S2CWallClimbStatePacket(enabled), player);
        player.displayClientMessage(
                Component.translatable(enabled ? MESSAGE_ON : MESSAGE_OFF), true);
    }

    /**
     * Sets the mode of that player to a fixed value and synchronises it (Phase 8C).
     *
     * <p>The transformation uses this to really switch the mode off, so the state of the player is the truth and
     * not merely ignored while the body is a beast. Leaving the form does not restore anything: the player has
     * to switch the mode on again by pressing the key.
     */
    public static void setEnabled(ServerPlayer player, boolean enabled)
    {
        if (ENABLED.getOrDefault(player, Boolean.FALSE) == enabled)
        {
            return;
        }

        ENABLED.put(player, enabled);
        HungryCraftNetwork.sendToPlayer(new S2CWallClimbStatePacket(enabled), player);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        // The beast body cannot climb, no matter how the mode is switched: the toggle is never touched, only
        // the physics stands aside, so turning back into a queen restores the very same setting as before.
        if (QueenBeastState.isTransformed(event.player))
        {
            return;
        }

        // A queen who is hibernating in her spider bed hangs on nothing: the physics stands aside here too,
        // and the mode itself is left exactly as the player set it.
        if (SpiderBedHibernation.isHibernating(event.player))
        {
            return;
        }

        // The same rule on both sides, so the client movement and the server's view of it agree.
        SimpleWallClimbPhysics.apply(event.player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        ENABLED.remove(player);

        // Safety net: a leftover noGravity of the removed experimental crawl can never survive a login.
        if (QueenDataCapability.isQueen(player) && player.isNoGravity() && !player.getAbilities().flying)
        {
            player.setNoGravity(false);
        }

        HungryCraftNetwork.sendToPlayer(new S2CWallClimbStatePacket(false), player);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        ENABLED.remove(event.getOriginal());
        ENABLED.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        ENABLED.remove(player);
        HungryCraftNetwork.sendToPlayer(new S2CWallClimbStatePacket(false), player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        ENABLED.remove(event.getEntity());
    }

    /** @return true when that player may use the mode at all. */
    private static boolean canUse(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player))
        {
            return false;
        }

        return !player.isPassenger() && !player.isSleeping();
    }
}
