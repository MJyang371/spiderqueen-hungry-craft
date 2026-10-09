package spiderqueenhungrycraft.soul;

import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CSoulsPacket;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The souls of a player (Soul Foundation V1): the first RPG resource of the mod, deliberately independent of the
 * vanilla experience bar, of the item inventory and of the silk of a queen.
 *
 * <p>The amount lives in the {@link QueenData} capability of the player, so it is per player, saved with her and
 * copied on every clone of the entity - a logout, a dimension change and a respawn all keep it. It is a
 * {@code long} and never an int: a currency that overflows is a bug that only shows itself after a wipe.
 *
 * <p>Every gameplay class reads and writes souls through this one API, so nothing has to know how the amount is
 * stored or synchronised, and no class ever keeps a copy of it. The client only ever receives the number the server
 * really holds; the animation of the HUD is a pure client concern (see
 * {@code spiderqueenhungrycraft.client.SoulClientState}).
 */
public final class Souls
{
    /**
     * How many souls one point of really gained experience is worth (V1 rule, on purpose the only place this
     * number exists).
     *
     * <p>V1 does not look at where the experience came from: a furnace, a trade, an ore, a bottle or a kill all
     * count the same. A later round will narrow this down to combat experience orbs that the player really picked
     * up; that round then changes this rule and nothing else.
     */
    public static final long SOULS_PER_XP = 10L;

    private Souls()
    {
    }

    /**
     * @return the souls of that player.
     *
     *         <p>This is the value of the own logical side. A client never draws its HUD from it: the number there
     *         is the one the server sent, read through {@code SoulClientState.drawableSouls()}.
     */
    public static long getSouls(Player player)
    {
        return QueenDataCapability.get(player).map(QueenData::getSouls).orElse(0L);
    }

    /** Writes the amount, clamped at zero, and synced to that player's client the moment it really changed. */
    public static void setSouls(Player player, long amount)
    {
        Optional<QueenData> data = QueenDataCapability.get(player);

        if (data.isEmpty())
        {
            return;
        }

        QueenData queenData = data.get();
        long before = queenData.getSouls();
        queenData.setSouls(amount);

        if (queenData.getSouls() != before)
        {
            sync(player, queenData.getSouls());
        }
    }

    /**
     * Adds souls, never below zero. A non positive amount changes nothing.
     *
     * <p>The sum saturates instead of wrapping around: a balance can only ever be clamped at zero, so a wrapped sum
     * would turn a rich queen into a beggar in one step. Long arithmetic is used everywhere for exactly this reason,
     * and this is the one place where a sum could ever run out of digits.
     */
    public static void addSouls(Player player, long amount)
    {
        if (amount <= 0L)
        {
            return;
        }

        long held = getSouls(player);
        long sum = held > Long.MAX_VALUE - amount ? Long.MAX_VALUE : held + amount;

        setSouls(player, sum);
    }

    /**
     * The one path experience takes into souls (Soul Foundation V1): only a <b>gain</b> counts.
     *
     * <p>Experience that is spent - an anvil, a repair, an enchantment table, a command that takes levels away -
     * arrives here as a negative amount and is ignored completely, so a soul can never be paid away.
     */
    public static void grantForExperience(Player player, int experienceAmount)
    {
        if (experienceAmount <= 0)
        {
            return;
        }

        addSouls(player, (long) experienceAmount * SOULS_PER_XP);
    }

    /**
     * Takes souls out of the balance of that player (Progression Foundation round).
     *
     * <p>This is the way a purchase of the soul shop and an upgrade of the Queen Level pay: the amount is checked
     * against what the player really holds, and when they hold less nothing happens at all - the balance is never
     * allowed to go below zero, so a price can never be paid half.
     *
     * @return true when the amount was really paid, false when the player holds less and nothing was taken.
     */
    public static boolean spendSouls(Player player, long amount)
    {
        if (amount <= 0L)
        {
            return true;
        }

        Optional<QueenData> data = QueenDataCapability.get(player);

        if (data.isEmpty() || data.get().getSouls() < amount)
        {
            return false;
        }

        setSouls(player, data.get().getSouls() - amount);
        return true;
    }

    /** Pushes the whole soul state of that player to their own client, used on login, respawn and dimension change. */
    public static void syncTo(ServerPlayer player)
    {
        send(player, getSouls(player));
    }

    /**
     * One change is one small packet, and only ever from the server: a client can neither grant itself souls nor
     * spend them, the number it draws is decided here.
     */
    private static void sync(Player player, long amount)
    {
        if (player instanceof ServerPlayer serverPlayer)
        {
            send(serverPlayer, amount);
        }
    }

    private static void send(ServerPlayer player, long amount)
    {
        HungryCraftNetwork.sendToPlayer(new S2CSoulsPacket(amount), player);
    }
}
