package spiderqueenhungrycraft.hostility;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import spiderqueenhungrycraft.human.HumanEntityTypes;

/**
 * The one question the pack rules ask about a pair of humans: are these two of the same group? (human pack aggro
 * round, prepared for the patrol round.)
 *
 * <p>It is deliberately the <b>only</b> place that answers it, so that the pack rules, the broadcast quota and the
 * leash never grow a second opinion about who belongs together. Today the answer is:
 * <ul>
 * <li>both have to be human units of this mod (a player is never one of them),</li>
 * <li>two humans of <b>different</b> squads are never comrades, however close they stand - squad loyalty beats
 * proximity, which is what makes a patrol a unit later,</li>
 * <li>everything else is a comrade: a human without a squad helps his neighbours, exactly what this round wants.</li>
 * </ul>
 *
 * <p>The distance is <b>not</b> part of this question on purpose. It belongs to the caller, which scans a radius
 * around the victim - so the patrol round only has to start handing out squad ids ({@link HumanBandState#setSquad})
 * and nothing else in the pack rules has to change: a patrol then answers as a unit across the whole distance it is
 * spread over, while ungrouped humans keep helping whoever stands next to them.
 */
public final class HumanComrade
{
    private HumanComrade()
    {
    }

    /** @return true when these two are humans of the same group, and the pack rules may treat them as comrades. */
    public static boolean isComrade(@Nullable Entity first, @Nullable Entity second)
    {
        if (first == null || second == null || first == second)
        {
            return false;
        }

        if (!HumanEntityTypes.isHumanMob(first) || !HumanEntityTypes.isHumanMob(second))
        {
            return false;
        }

        if (first.level() != second.level())
        {
            return false;
        }

        String squad = HumanBandState.squadOf(first);
        String otherSquad = HumanBandState.squadOf(second);

        // Two patrols of their own are never one pack. A human without a squad is everyone's neighbour, which is the
        // whole behaviour of this round; the patrol round only has to start writing the squad id for that to change.
        return squad.isEmpty() || otherSquad.isEmpty() || squad.equals(otherSquad);
    }

    /** @return true when that creature belongs to the squad of that human, which is the leader rule of the patrols. */
    public static boolean isSameSquad(@Nullable Entity first, @Nullable Entity second)
    {
        if (first == null || second == null || first == second)
        {
            return false;
        }

        String squad = HumanBandState.squadOf(first);
        String otherSquad = HumanBandState.squadOf(second);

        return !squad.isEmpty() && squad.equals(otherSquad);
    }
}
