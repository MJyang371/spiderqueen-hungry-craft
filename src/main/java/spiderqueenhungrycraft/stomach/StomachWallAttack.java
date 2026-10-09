package spiderqueenhungrycraft.stomach;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

/**
 * The server half of "attack the stomach wall" (Phase 8E1-GATE2A).
 *
 * <p>When a player stands inside their cell and swings at the wall, the water or the empty air, the client sends
 * one wall attack request. Everything else is decided here: who sent it, whether they are really inside a loaded
 * cell, whether that cell currently owns a core and whether the click was a real charged click.
 *
 * <p>The attack itself is the ordinary vanilla player attack, so the current weapon, its enchantments, the attack
 * cooldown and every combat hook of the platform take part. The client never damages anything on its own.
 *
 * <p>The cadence of the wall is kept by the server clock (Phase 8E1-GATE2A-POLISH-1): the charge of a swing has to
 * travel over the network, so a strict charge check would refuse real clicks, while a gap measured on the server
 * keeps exactly the rhythm of the client and still makes a spam of packets harmless.
 */
public final class StomachWallAttack
{
    /**
     * The last accepted wall attack of every player, on the server clock. The cadence of the wall is kept here
     * instead of in the charge of the swing, because the charge arrives over the network and a little latency must
     * never turn a real click into a refused one.
     */
    private static final Map<UUID, Long> LAST_WALL_ATTACK = new HashMap<>();

    private StomachWallAttack()
    {
    }

    /** @return true when the request was accepted and the wall of the cell was really attacked. */
    public static boolean handle(ServerPlayer player)
    {
        if (player == null || player.isRemoved() || !player.isAlive() || player.isSpectator())
        {
            return false;
        }

        if (!(player.level() instanceof ServerLevel level))
        {
            return false;
        }

        Optional<StomachCell> resolved = StomachCellManager.resolveCellAt(level, player.position());

        if (resolved.isEmpty())
        {
            return false;
        }

        StomachCell cell = resolved.get();

        if (!StomachCellManager.isLoaded(cell))
        {
            return false;
        }

        StomachCoreEntity core = StomachCellManager.coreOf(level, cell).orElse(null);

        if (core == null || !core.isAlive() || !core.belongsTo(cell))
        {
            return false;
        }

        // The same rhythm as the client, kept by the clock of the server: one swing of a weapon is one hit on the
        // wall, and no spam of packets can ever become a storm of blows. The charge of the swing is deliberately
        // not used as the pace here, because the vanilla client resets it on every tick in which the button is not
        // held - a real click would then be refused for a reason the player cannot see.
        long now = level.getGameTime();
        Long last = LAST_WALL_ATTACK.get(player.getUUID());
        int minimumGap = Math.max(1,
                Mth.ceil(player.getCurrentItemAttackStrengthDelay() * StomachConstants.WALL_ATTACK_MIN_CHARGE));

        if (last != null && now - last < minimumGap)
        {
            return false;
        }

        LAST_WALL_ATTACK.put(player.getUUID(), now);
        player.attack(core);

        return true;
    }
}
