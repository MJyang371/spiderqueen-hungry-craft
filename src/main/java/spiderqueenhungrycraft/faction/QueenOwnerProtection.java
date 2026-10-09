package spiderqueenhungrycraft.faction;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyContract;

/**
 * The one protection rule of every owned creature (Brood Command AI).
 *
 * <p>Two creatures of the same Spider Queen are never enemies, whatever the faction matrix says and whoever gives the
 * order: a queen can not order her spiders at each other, and a spider can never be made to attack the queen it
 * belongs to. Every AI decision of a minion passes through {@link #mayAttack}, so this rule can not be forgotten in a
 * single goal - and the sceptre uses the very same gate for its commands.
 *
 * <p>It is a targeting rule and nothing else: it does not make a spider immune to anything. Fire, explosions,
 * potions and every other source of harm behave exactly like they always did.
 */
public final class QueenOwnerProtection
{
    private QueenOwnerProtection()
    {
    }

    /**
     * @return the queen UUID that creature belongs to, or null when it is not owned by anybody.
     *
     * <p>A creature of the colony answers with its own owner. So does an <b>Ice and Fire dragon under a HungryCraft
     * contract</b> (Necromancy IV): it carries no tame flag and no vanilla owner for the contract - that is the whole
     * point of the contract - so the contract itself is asked instead, and the answer is the queen who holds it. That
     * one line is what makes every rule built on this class treat a contracted skeleton as a creature of that queen:
     * the spiders of the brood never pick it as a victim, and it never picks them.
     */
    @Nullable
    public static UUID ownerOf(@Nullable Entity entity)
    {
        if (entity instanceof QueenOwnedEntity owned)
        {
            return owned.getQueenOwnerUUID();
        }

        return NecromancyContract.of(entity).map(NecromancyContract::owner).orElse(null);
    }

    /** @return true when both creatures are owned by the same queen (and really have an owner). */
    public static boolean isSameOwner(@Nullable Entity a, @Nullable Entity b)
    {
        UUID first = ownerOf(a);
        UUID second = ownerOf(b);

        return first != null && second != null && first.equals(second);
    }

    /**
     * @return true when that source creature is allowed to pick that target at all.
     *
     * <p>False for itself, for its own owner (however that owner is classified in the faction system) and for any
     * creature of the same owner - and true otherwise, which means "the normal rules may decide now", not "attack
     * it": whether a target is actually hostile stays the business of the faction layer.
     */
    public static boolean mayAttack(@Nullable Entity source, @Nullable Entity target)
    {
        if (source == null || target == null || source == target)
        {
            return false;
        }

        UUID owner = ownerOf(source);

        if (owner == null)
        {
            // Nothing is owned here, so this rule has nothing to protect.
            return true;
        }

        if (target instanceof Player player && owner.equals(player.getUUID()))
        {
            return false;
        }

        return !owner.equals(ownerOf(target));
    }
}
