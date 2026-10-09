package spiderqueenhungrycraft.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The open growth interface of a player (Progression Foundation round): the context every request of the interface is
 * bound to.
 *
 * <p>A core has no block entity, and this round does not invent one just to hold a screen: a session is a small entry
 * in memory, written when the server opens the interface for a core and asked on every request. It remembers the
 * <b>dimension</b> and the <b>position</b> of that core, so a packet that arrives while the player is somewhere else
 * entirely can never buy anything - the check is not "did a client claim to have a screen open", it is "is this
 * player really standing at that core of this world, and is the core still there".
 *
 * <p>A session goes away when it stops making sense: on logout, on death, on a dimension change, and whenever a new
 * interface is opened (a player has one open core at a time). Nothing else has to be cleaned up, because a session
 * that is left behind is simply never valid again.
 */
public final class ProgressionSessions
{
    /** How far from the core a player may stand while using it: the ordinary reach of a container, generously. */
    public static final double MAX_DISTANCE = 8.0D;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ProgressionSessions()
    {
    }

    /** One open interface: which core, of which world. */
    private record Session(ResourceKey<Level> dimension, BlockPos corePos)
    {
    }

    /** Opens the interface of that core for that player, replacing whatever they had open before. */
    public static void open(ServerPlayer player, BlockPos corePos)
    {
        SESSIONS.put(player.getUUID(), new Session(player.level().dimension(), corePos.immutable()));
    }

    /** Closes the interface of that player, used on logout, death and a dimension change. */
    public static void close(@Nullable UUID player)
    {
        if (player != null)
        {
            SESSIONS.remove(player);
        }
    }

    /** @return the open core of that player, or null when they have no interface open that is still valid. */
    @Nullable
    public static BlockPos openCoreOf(ServerPlayer player)
    {
        Session session = SESSIONS.get(player.getUUID());

        if (session == null || !isValid(player, session.corePos()))
        {
            return null;
        }

        return session.corePos();
    }

    /**
     * @return true when that player may use that core right now: the interface was opened for exactly that core, the
     *         player is alive, they are still in the dimension of that core, the core is still a territory core, and
     *         they are still standing at it.
     */
    public static boolean isValid(ServerPlayer player, @Nullable BlockPos corePos)
    {
        if (corePos == null || !player.isAlive() || player.isRemoved())
        {
            return false;
        }

        Session session = SESSIONS.get(player.getUUID());

        if (session == null || !session.corePos().equals(corePos))
        {
            return false;
        }

        if (!session.dimension().equals(player.level().dimension()))
        {
            return false;
        }

        ServerLevel level = player.serverLevel();

        if (!level.getBlockState(corePos).is(HungryCraftBlocks.TERRITORY_CORE.get()))
        {
            return false;
        }

        return player.distanceToSqr(Vec3.atCenterOf(corePos)) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
