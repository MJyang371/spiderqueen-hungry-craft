package spiderqueenhungrycraft.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.faction.FactionId;
import spiderqueenhungrycraft.faction.FactionRelation;
import spiderqueenhungrycraft.faction.FactionResolver;
import spiderqueenhungrycraft.faction.FactionTargeting;

/**
 * The old home of "monsters do not attack the Spider Queen" (Phase 6).
 *
 * <p>Since Phase HF1-VISIBLE the decision itself lives in the faction system: {@code FactionEvents} refuses the
 * target and {@code FactionTargeting} answers the questions. This class therefore subscribes to nothing any more -
 * keeping its old listeners would mean two systems deciding the same thing - and only offers the two questions the
 * rest of the mod still asks, now answered by the one faction source of truth.
 */
public final class QueenMonsterNeutrality
{
    private QueenMonsterNeutrality()
    {
    }

    /** @return true when this mob must not attack this player right now. */
    public static boolean isNeutralTowards(Mob mob, Player target)
    {
        return mob != null && target != null && FactionTargeting.shouldSuppressExistingTarget(mob, target);
    }

    /**
     * @return true when this kind of mob is neutral towards the Spider Queen, regardless of the current target.
     *
     * <p>Reads the faction of the mob against the queen faction, so the {@code always_hostile_to_spider_queen} tag and
     * the village defenders are respected exactly like they were before.
     */
    public static boolean isNeutralType(Mob mob)
    {
        return mob != null && FactionResolver.relationTowards(mob, FactionId.QUEEN) == FactionRelation.NEUTRAL;
    }
}
