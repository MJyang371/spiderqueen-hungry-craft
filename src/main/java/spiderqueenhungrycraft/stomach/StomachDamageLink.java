package spiderqueenhungrycraft.stomach;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * The damage link of the stomach system (Phase 8E1-GATE2B).
 *
 * <p>When a creature hurts the wall of a stomach, the owner of that stomach is the one who bleeds: the amount the
 * core really took after its own visceral armor is carried over one to one. The link never guesses: it needs a
 * canonical core (see {@link StomachOwnership}), an online owner, and a hit that really came from a combat entity.
 * A command, the void or any other damage without an attacker never reaches the owner, and a cell whose owner is
 * away keeps its pain to itself.
 */
public final class StomachDamageLink
{
    /** Guards the link against being carried on by its own damage. */
    private static boolean linking;

    private StomachDamageLink()
    {
    }

    /**
     * Carries the damage a core really took to the owner of its cell.
     *
     * @return the amount that reached the owner, or 0 when nothing was carried
     */
    public static float forward(ServerLevel level, StomachCoreEntity core, DamageSource source, float amount)
    {
        if (linking || amount <= 0.0F || StomachDamageTypes.isStomachLink(source)
                || StomachDamageTypes.isSwallowed(source))
        {
            return 0.0F;
        }

        // Only real combat is carried: an administrator, the void or the environment has no attacker to name.
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();

        if (direct == null && causing == null)
        {
            return 0.0F;
        }

        ServerPlayer owner = StomachOwnership.damageTargetOf(level, core).orElse(null);

        if (owner == null)
        {
            return 0.0F;
        }

        linking = true;

        try
        {
            owner.hurt(StomachDamageTypes.link(level, direct, causing), amount);
        }
        finally
        {
            linking = false;
        }

        return amount;
    }

    /** @return the owner that would receive the damage of that core, for the debug and probe output. */
    @Nullable
    public static ServerPlayer targetOf(ServerLevel level, StomachCoreEntity core)
    {
        return StomachOwnership.damageTargetOf(level, core).orElse(null);
    }
}
