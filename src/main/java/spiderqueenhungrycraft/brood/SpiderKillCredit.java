package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Who dealt the last blow (Brood Veterancy round, shared with the kill credit of the queen).
 *
 * <p>One creature dies and the question "who killed it" must have exactly one answer in this mod: the veterancy of the
 * spider that is promoted by the kill and the kill credit that is handed to its queen read the very same rule. So a
 * spider can never earn a rank for a kill that does not count for its queen, and a queen can never be credited for a
 * kill that no spider of hers made.
 *
 * <p>Three ways are asked, in this order: the creature that caused the damage (which is the shooter for the damage
 * source of an arrow of the game), the owner of the projectile that really hit, and the owner of the causing entity.
 * Taken together they cover a bite, a web shot, an arrow and anything else a future ranged spider may fire, without a
 * single branch per projectile type.
 */
public final class SpiderKillCredit
{
    private SpiderKillCredit()
    {
    }

    /** @return the spider that dealt the last blow, or null when the kill does not belong to the brood. */
    @Nullable
    public static SpiderMinionEntity killerOf(DamageSource source)
    {
        if (source.getEntity() instanceof SpiderMinionEntity spider)
        {
            return spider;
        }

        if (ownerOf(source.getDirectEntity()) instanceof SpiderMinionEntity spider)
        {
            return spider;
        }

        return ownerOf(source.getEntity()) instanceof SpiderMinionEntity spider ? spider : null;
    }

    /**
     * @return the creature behind that entity, or null when it has none
     *
     * <p>Deliberately explicit about the two kinds that have an owner in the game - a projectile names its shooter, an
     * ownable creature such as a tamed wolf names its master - instead of asking the base class, which knows nothing
     * about owners at all.
     */
    @Nullable
    private static Entity ownerOf(@Nullable Entity entity)
    {
        if (entity instanceof Projectile projectile)
        {
            return projectile.getOwner();
        }

        if (entity instanceof OwnableEntity ownable)
        {
            return ownable.getOwner();
        }

        return null;
    }
}
