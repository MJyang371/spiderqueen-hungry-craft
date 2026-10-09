package spiderqueenhungrycraft.soulfire;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.faction.QueenOwnedEntity;

/**
 * Who is a friend of a soul fire dragon, and who may be hunted (round 7C, the friendship rule of the brood).
 *
 * <p>It is the very same question the spiders of the brood answer, asked through the one interface the colony layer
 * already uses ({@link QueenOwnedEntity}): a creature belongs to a queen, and two creatures of the same queen never
 * fight each other. On top of that a dragon refuses to be aimed at its own queen and at a creative or spectator
 * player - not because they are friendly, but because ordering an attack on them is not a decision that means anything
 * and the round asks that a friend can never become a victim by accident.
 *
 * <p><b>It is read in two directions and both matter:</b> the order is refused before it is written (so the sceptre
 * can not point a dragon at its own side) and the target is refused again on every tick of a fight (so a contract that
 * changed hands can not leave a dragon hitting its own brood).
 */
public final class SoulfireTargeting
{
    private SoulfireTargeting()
    {
    }

    /** @return true when that creature stands on the side of the dragon and may never be attacked by it. */
    public static boolean isFriendOf(SoulfireDragonEntity dragon, @Nullable Entity other)
    {
        if (other == null || other == dragon)
        {
            return true;
        }

        UUID owner = dragon.getOwnerUuid();

        if (other instanceof Player player)
        {
            return (owner != null && owner.equals(player.getUUID())) || player.isCreative() || player.isSpectator();
        }

        if (other instanceof QueenOwnedEntity theirs)
        {
            UUID theirOwner = theirs.getQueenOwnerUUID();

            return owner != null && owner.equals(theirOwner);
        }

        // Anything else is a stranger, and a stranger is nobody this unit protects.
        return false;
    }

    /** @return true when that creature may be hunted by the free attack order of that dragon. */
    public static boolean isHuntable(SoulfireDragonEntity dragon, @Nullable LivingEntity other)
    {
        return other != null && other != dragon && other.isAlive() && !other.isRemoved() && other.attackable()
                && !(other instanceof SoulfireDragonEntity) && !isFriendOf(dragon, other);
    }
}
