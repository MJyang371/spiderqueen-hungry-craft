package spiderqueenhungrycraft.ability;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.player.QueenSilk;

/**
 * How the web glands of a Spider Queen fill themselves again (feature SILK-CAPACITY).
 *
 * <p>The silk is spun out of what the queen ate, so the hunger bar decides how fast the glands work, and the
 * three speeds are the whole rule:
 *
 * <ul>
 * <li>a full stomach (20) spins {@link QueenSilk#FAST_RESTORE_AMOUNT} silk every second,</li>
 * <li>a stomach that is more than half full (11 to 19) spins {@link QueenSilk#SLOW_RESTORE_AMOUNT} silk
 * every two seconds,</li>
 * <li>half a stomach or less (10 and below) spins nothing at all.</li>
 * </ul>
 *
 * <p>It never goes above {@link QueenSilk#MAX_AMOUNT}, and none of it happens for a player who is not the
 * queen, for a spectator, for a dead player or in creative, where the web shot is free anyway. The recovery
 * is deliberately independent of the beast form: a beast fires webs too, and her glands keep working while
 * she runs around as one.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenSilkRegeneration
{
    /** The food level at which the hunger bar counts as full. */
    private static final int FULL_FOOD_LEVEL = 20;

    private QueenSilkRegeneration()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
        {
            return;
        }

        Player player = event.player;

        // Only a queen owns silk at all, and only while she really lives in the world.
        if (!QueenDataCapability.isQueen(player) || player.isCreative() || player.isSpectator() || !player.isAlive())
        {
            return;
        }

        // Full glands stop the recovery instead of wasting the tick.
        if (QueenSilk.getSilkAmount(player) >= QueenSilk.MAX_AMOUNT)
        {
            return;
        }

        int tick = player.tickCount;

        if (tick <= 0)
        {
            return;
        }

        int foodLevel = player.getFoodData().getFoodLevel();

        if (foodLevel >= FULL_FOOD_LEVEL && tick % QueenSilk.FAST_RESTORE_INTERVAL_TICKS == 0)
        {
            QueenSilk.restoreSilk(player, QueenSilk.FAST_RESTORE_AMOUNT);
        }
        else if (foodLevel > QueenSilk.RESTORE_MIN_FOOD_LEVEL && tick % QueenSilk.SLOW_RESTORE_INTERVAL_TICKS == 0)
        {
            QueenSilk.restoreSilk(player, QueenSilk.SLOW_RESTORE_AMOUNT);
        }
    }
}
