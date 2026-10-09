package spiderqueenhungrycraft.progression;

import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The Queen Level of a player (Progression Foundation round): the permanent milestone every later round of growth
 * hangs off.
 *
 * <p>It lives in the {@link QueenData} capability, so it is per player, saved with them and copied on every clone of
 * the entity - a logout, a dimension change and a respawn all keep it, and <b>a death never takes it away</b>. It is
 * server authoritative: the client only ever learns it from a snapshot the server sends, and nothing here is derived
 * from the client side state of a player.
 *
 * <p><b>The level itself grants nothing.</b> It changes no attribute, no damage, no armour, no health and no hunger;
 * it is a mark of progress and nothing else. A later round hangs the right to buy a skill node off the levels a
 * player has here - reaching a level is not the same thing as owning what a level may open.
 */
public final class QueenLevels
{
    private QueenLevels()
    {
    }

    /** @return the Queen Level of that player, at least {@link QueenData#MIN_QUEEN_LEVEL}. */
    public static int getLevel(Player player)
    {
        return QueenDataCapability.get(player).map(QueenData::getQueenLevel).orElse(QueenData.MIN_QUEEN_LEVEL);
    }

    /**
     * Writes the Queen Level.
     *
     * <p>Only the bottom is clamped (see {@link QueenData#setQueenLevel(int)}). A level is never reduced here: the
     * only caller that lowers a value is an administrator command, and the maximum of the progression definition is
     * deliberately not enforced on the stored value, so a world whose definition shrinks can never delete a level
     * somebody already paid for.
     */
    public static void setLevel(Player player, int level)
    {
        QueenDataCapability.get(player).ifPresent(data -> data.setQueenLevel(level));
    }

    /** @return true when that player has reached at least that level. */
    public static boolean hasReached(Player player, int level)
    {
        return getLevel(player) >= level;
    }
}
