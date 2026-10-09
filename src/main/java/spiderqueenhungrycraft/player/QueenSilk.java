package spiderqueenhungrycraft.player;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CQueenSilkPacket;

/**
 * The stored silk of a Spider Queen (feature SILK-CAPACITY): the resource her own web glands hold, completely
 * separate from the hunger bar.
 *
 * <p>Hunger is the energy of the body and keeps her alive, the silk is what her abdomen has spun and tucked
 * away. The two are deliberately not the same bar: a fed queen refills her silk on her own, and a web shot is
 * paid from the silk first and only falls back to the hunger bar while the glands are empty.
 *
 * <p>Only a queen owns silk at all. The amount lives in the {@link QueenData} capability of the player, so it
 * is per player and saved with her, and it is never an attribute of an item: the spider hook stays what it
 * always was, a tool with its own wear. No ordinary player ever has the value, the bar or the recovery.
 *
 * <p>Every gameplay class reads and writes the silk through this one API, so no skill has to know how the
 * resource is stored, capped or synchronised, and no skill ever keeps a copy of it. The client only receives
 * the amount the server really holds; nothing here is ever derived from the client side state of a player.
 */
public final class QueenSilk
{
    /** Most silk the glands of a queen can hold: ten cobweb icons of two points each. */
    public static final int MAX_AMOUNT = 20;

    /** Silk one icon of the HUD stands for. */
    public static final int AMOUNT_PER_ICON = 2;

    /** Icons the HUD draws, one for every second point of silk. */
    public static final int ICON_COUNT = MAX_AMOUNT / AMOUNT_PER_ICON;

    /**
     * Silk one web shot costs. While the glands are empty the hunger bar pays exactly the same amount, so the
     * price of a shot never changes, only the resource it is taken from.
     */
    public static final int WEB_SHOT_COST = 2;

    /** A full stomach spins this much silk this often. */
    public static final int FAST_RESTORE_AMOUNT = 2;
    public static final int FAST_RESTORE_INTERVAL_TICKS = 20;

    /** A stomach that is more than half full spins slowly. */
    public static final int SLOW_RESTORE_AMOUNT = 1;
    public static final int SLOW_RESTORE_INTERVAL_TICKS = 40;

    /** Above this food level the glands spin at all; half a stomach or less spins nothing. */
    public static final int RESTORE_MIN_FOOD_LEVEL = 10;

    /** Client mirror of the queen flag and the silk of the local player, written by the sync packet only. */
    private static boolean clientQueen;
    private static int clientAmount;

    private QueenSilk()
    {
    }

    /**
     * @return the silk in the glands of that queen, or zero for a player who has no queen capability.
     *
     *         <p>This is the value of the own logical side. A client never draws its HUD from it: the amount
     *         there is the one the server sent, read through {@link #clientAmount()}.
     */
    public static int getSilkAmount(Player player)
    {
        return QueenDataCapability.get(player).map(QueenData::getSilkAmount).orElse(0);
    }

    /** Writes the amount, clamped into the allowed range, and synced to the client when it really changed. */
    public static void setSilkAmount(Player player, int amount)
    {
        Optional<QueenData> data = QueenDataCapability.get(player);

        if (data.isEmpty())
        {
            return;
        }

        QueenData queenData = data.get();
        int before = queenData.getSilkAmount();
        queenData.setSilkAmount(amount);

        if (queenData.getSilkAmount() != before)
        {
            sync(player, queenData.getSilkAmount());
        }
    }

    /**
     * Takes silk out of the glands of that queen.
     *
     * @return true when the amount was really paid, false when the glands hold less and nothing happened.
     */
    public static boolean consumeSilk(Player player, int amount)
    {
        if (amount <= 0)
        {
            return true;
        }

        Optional<QueenData> data = QueenDataCapability.get(player);

        if (data.isEmpty() || !data.get().consumeSilk(amount))
        {
            return false;
        }

        sync(player, data.get().getSilkAmount());
        return true;
    }

    /** Adds silk to the glands of that queen, never above the maximum. */
    public static void restoreSilk(Player player, int amount)
    {
        if (amount <= 0)
        {
            return;
        }

        setSilkAmount(player, getSilkAmount(player) + amount);
    }

    /** @return true when the glands of that queen hold at least that much silk. */
    public static boolean hasSilk(Player player, int amount)
    {
        return amount <= 0 || getSilkAmount(player) >= amount;
    }

    /** Pushes the whole silk state of that queen to her own client, used by the runtime sync. */
    public static void syncTo(ServerPlayer player)
    {
        send(player, QueenDataCapability.isQueen(player), getSilkAmount(player));
    }

    /** Client only: applies the state the server sent for the local player. */
    public static void acceptClientState(boolean queen, int amount)
    {
        clientQueen = queen;
        clientAmount = Mth.clamp(amount, 0, MAX_AMOUNT);
    }

    /** @return true when the local player is a queen according to the last state the server sent. */
    public static boolean clientIsQueen()
    {
        return clientQueen;
    }

    /** @return the silk the server last reported for the local player. */
    public static int clientAmount()
    {
        return clientAmount;
    }

    /**
     * One change is one small packet, and only ever from the server: a client can neither consume nor grant
     * itself silk, the amount it draws is decided here.
     */
    private static void sync(Player player, int amount)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            send(serverPlayer, QueenDataCapability.isQueen(player), amount);
        }
    }

    private static void send(ServerPlayer player, boolean queen, int amount)
    {
        HungryCraftNetwork.sendToPlayer(new S2CQueenSilkPacket(queen, amount), player);
    }
}
