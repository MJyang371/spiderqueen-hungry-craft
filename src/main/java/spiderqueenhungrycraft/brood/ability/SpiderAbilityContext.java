package spiderqueenhungrycraft.brood.ability;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.QueenCommandRay;

/**
 * What the queen was pointing at when she pressed her ability key (Brood Ability framework).
 *
 * <p>One context is built per key press - by the <b>server</b>, from its own cast of the 64 block command ray of the
 * sceptre (see {@code SpiderAbilityCast}) - and then handed to every spider that answers it. The client never sends a
 * target, so nothing here can be forged: the position and the creature in this context are the ones the server itself
 * saw in front of the queen.
 *
 * <p>The ray is optional by design. An ability whose {@link SpiderAbilityTarget} is {@link SpiderAbilityTarget#NONE}
 * does not care whether anything was in front of the queen at all; one that needs a position or a creature simply is not
 * allowed to fire.
 */
public record SpiderAbilityContext(Player queen, @Nullable LivingEntity targetEntity, @Nullable Vec3 targetPosition,
        QueenCommandRay.Kind rayKind, double rayDistance)
{
    /** @return the context of one cast, read from the command ray of that queen right now. */
    public static SpiderAbilityContext of(Player queen)
    {
        QueenCommandRay.Hit hit = QueenCommandRay.cast(queen);

        if (hit.hasEntity() && hit.entity() != null)
        {
            // A creature: the ability may be aimed at it, and it stands at its own feet.
            return new SpiderAbilityContext(queen, hit.entity(), hit.entity().position(), hit.kind(), hit.distance());
        }

        if (hit.hasBlock() && hit.location() != null)
        {
            // Only a block: there is no creature, but the spot the sceptre pointed at is a place an ability can use.
            return new SpiderAbilityContext(queen, null, hit.location(), hit.kind(), hit.distance());
        }

        return new SpiderAbilityContext(queen, null, null, hit.kind(), hit.distance());
    }

    /** @return true when the ray of this cast found a creature. */
    public boolean hasEntity()
    {
        return this.targetEntity != null;
    }

    /** @return true when the ray of this cast found a place to work with. */
    public boolean hasPosition()
    {
        return this.targetPosition != null;
    }
}
