package spiderqueenhungrycraft.world;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.faction.FactionAggroMemory;

/**
 * The old retaliation memory of the queen design (Phase 6).
 *
 * <p>Neutral does not mean harmless: a mob that was attacked remembers its attacker for a while and may fight back.
 * Since Phase HF1-VISIBLE that memory belongs to the faction system ({@link FactionAggroMemory}) and works for every
 * pair of factions, not only for the queen. This class keeps the old entry points working and subscribes to nothing,
 * so there is exactly one memory and never two that could disagree.
 */
public final class QueenRetaliationTracker
{
    /** How long a mob may fight back after being attacked, see {@link FactionAggroMemory#MEMORY_DURATION_TICKS}. */
    public static final int RETALIATION_DURATION_TICKS = FactionAggroMemory.MEMORY_DURATION_TICKS;

    private QueenRetaliationTracker()
    {
    }

    /** Records that this mob was attacked by this player. */
    public static void startRetaliation(Mob mob, Player player)
    {
        FactionAggroMemory.remember(mob, player);
    }

    /** @return true while this mob is allowed to attack this player because she attacked it first. */
    public static boolean isRetaliating(Mob mob, Player player)
    {
        return FactionAggroMemory.isAngryAt(mob, player);
    }

    /** Drops the memory of this mob. */
    public static void clear(Mob mob)
    {
        FactionAggroMemory.forget(mob);
    }
}
