package spiderqueenhungrycraft.faction;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The colony layer of the queen side (Queen Colony framework).
 *
 * <p>The faction layer answers <b>which side</b> a creature stands on, and it is static: {@code QUEEN} is the
 * Spider Queen system as a whole, and it will never be split into one faction per player (no dynamic faction, no
 * {@code queen_faction_<uuid>}, no runtime tag writing - {@link FactionId} stays a fixed list of big categories).
 *
 * <p>This class answers the second question, <b>whose</b> creature it is: the colony of an entity is the UUID of the
 * Spider Queen it belongs to. That is the dynamic half, and it lives in entity data (the owner UUID of a minion, the
 * queen capability of a player), never in a tag.
 *
 * <p>So two queens and their broods are all {@code QUEEN} on the faction layer, and that alone never makes them
 * friends. Only {@link #isSameColony(Entity, Entity)} does - and even that is not a declaration of war for the
 * others: two different queens are left undecided here, to be settled later by PvP rules, the future queen sceptre,
 * the revenge memory or a server rule.
 */
public final class QueenColonyRelations
{
    private QueenColonyRelations()
    {
    }

    /**
     * @return true when that entity stands on the queen side at all: its faction is {@code QUEEN}, it is a Spider
     *         Queen player, or it is a creature whose queen is known.
     */
    public static boolean isQueenSide(@Nullable Entity entity)
    {
        if (entity == null)
        {
            return false;
        }

        if (FactionResolver.getFaction(entity) == FactionId.QUEEN)
        {
            return true;
        }

        if (entity instanceof Player player)
        {
            return QueenDataCapability.isQueen(player);
        }

        return entity instanceof QueenOwnedEntity owned && owned.getQueenOwnerUUID() != null;
    }

    /**
     * @return the colony of that entity, that is the UUID of its Spider Queen, or null when it has none.
     *
     * <p>A Spider Queen player is her own colony: the colony of a queen is the player UUID, so a queen and her
     * brood always end up in the same one.
     */
    @Nullable
    public static UUID getColonyId(@Nullable Entity entity)
    {
        if (entity instanceof Player player)
        {
            return QueenDataCapability.isQueen(player) ? player.getUUID() : null;
        }

        if (entity instanceof QueenOwnedEntity owned)
        {
            return owned.getQueenOwnerUUID();
        }

        return null;
    }

    /**
     * @return true only when both sides have a colony and it is exactly the same one. An entity without a colony
     *         (a queen side creature whose owner is unknown) is the colony of nobody, not even of itself.
     */
    public static boolean isSameColony(@Nullable Entity a, @Nullable Entity b)
    {
        UUID first = getColonyId(a);
        UUID second = getColonyId(b);

        return first != null && second != null && first.equals(second);
    }

    /**
     * @return true when these two are of the same colony, which is the only case this framework calls them friends.
     *
     * <p>False everywhere else, and deliberately so:
     * <ul>
     * <li>not a queen side pair at all - the faction matrix decides, this method does not interfere,</li>
     * <li>two queen side creatures of <b>different</b> queens (or without a colony) - not friendly, but not hostile
     * either. Nothing here declares a war between two colonies: that is for PvP, the future queen sceptre, the
     * revenge memory or a server rule to decide.</li>
     * </ul>
     */
    public static boolean isQueenSideFriendly(@Nullable Entity a, @Nullable Entity b)
    {
        if (a == null || b == null || a == b)
        {
            return false;
        }

        return isSameColony(a, b);
    }
}
