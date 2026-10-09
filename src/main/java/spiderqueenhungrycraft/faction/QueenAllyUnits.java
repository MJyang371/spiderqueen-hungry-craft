package spiderqueenhungrycraft.faction;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;

/**
 * The one rule that answers "is that creature one of mine?" for a shot of the queen side (web shot fix round).
 *
 * <p>It is the rule a web-type hit passes through before it may land on anybody: the creatures of the same Spider Queen
 * - the queen herself and every spider that belongs to her - and the shooter's own tames are flown through instead of
 * being hit ({@code WebShotProjectile#canHitEntity}, {@code SpiderArrow#canHitEntity} and
 * {@code SpiderTrident#canHitEntity} all ask this same question).
 *
 * <p><b>Two layers, and neither of them is the faction matrix.</b> The first is the colony
 * ({@link QueenColonyRelations}): the queen and her brood are one colony, so a queen and her own spiders never shoot
 * each other. The second is vanilla ownership ({@link OwnableEntity}): the wolf, cat, horse or parrot that belongs to
 * the shooter is the shooter's creature as well, so a web shot of its owner passes through it instead of webbing it and
 * turning it against her. {@code FactionId} is deliberately never consulted here - "queen side" is a static camp of the
 * war matrix, while this rule is about the personal belonging of exactly one shooter, which is the very distinction the
 * colony layer exists for.
 *
 * <p>Two queens stay strangers to each other: their creatures are of different colonies, so a shot of one queen still
 * hits the spiders of the other. Nothing here declares a friendship between two colonies, and nothing here is a
 * targeting rule - it only says who a web-type hit may not touch at all.
 */
public final class QueenAllyUnits
{
    private QueenAllyUnits()
    {
    }

    /**
     * @return true when that creature is one of the shooter's own and a web-type hit has to fly through it: no damage,
     *         no web, no capture, no provocation, no knockback, no thud and no lost speed.
     */
    public static boolean isAllyOf(@Nullable Entity shooter, @Nullable Entity target)
    {
        if (shooter == null || target == null || shooter == target)
        {
            return false;
        }

        if (QueenColonyRelations.isQueenSideFriendly(shooter, target))
        {
            return true;
        }

        return isOwnTame(shooter, target);
    }

    /**
     * @return true when that creature is a tame owned by the shooter. Vanilla ownership only, so a pet of the shooter
     *         is never hit by the shooter's own web - whatever species it is, and without a single faction tag.
     */
    private static boolean isOwnTame(Entity shooter, Entity target)
    {
        if (!(target instanceof OwnableEntity ownable))
        {
            return false;
        }

        UUID owner = ownable.getOwnerUUID();

        return owner != null && owner.equals(shooter.getUUID());
    }
}
