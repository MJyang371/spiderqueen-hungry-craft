package spiderqueenhungrycraft.entity;

import java.util.EnumSet;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import spiderqueenhungrycraft.stomach.StomachCell;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;

/**
 * Makes Chad Steve go for the stomach core of the cell he was swallowed into (Phase 9C, parts F to H).
 *
 * <p><b>Why this goal exists.</b> A core is a {@link net.minecraft.world.entity.LivingEntity} and nothing else: it
 * is not a monster, it is not a player, so the two ordinary target goals of this round could never accept it. The
 * core itself already points every fighting mob of its cell at itself with its taunt, which means Chad Steve did
 * get the core as his target before - but his attack goal then refused that target, fell back to wandering and
 * looked like he was ignoring the wall completely. This goal is the missing piece: it resolves the cell, keeps the
 * core in place and hands it to him as the target with the highest priority of all.
 *
 * <p>Priority is claimed by registering it at the head of the target selector (Phase 9C, part G):
 * <pre>
 *   0  stomach core of the cell he is inside
 *   1  Spider Queen player
 *   2  monster
 * </pre>
 * Outside the stomach dimension the goal simply finds no cell and does nothing, so the behaviour there is exactly
 * what it was before.
 */
public class ChadSteveStomachCoreTargetGoal extends Goal
{
    /** The cell is only looked at this often; the core itself is not going anywhere. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private final ChadSteveEntity mob;

    /** The core this goal picked, {@code null} while it is not running. */
    @Nullable
    private StomachCoreEntity core;

    private int scanTicker;

    public ChadSteveStomachCoreTargetGoal(ChadSteveEntity mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        if (this.mob.level().isClientSide || !(this.mob.level() instanceof ServerLevel level))
        {
            return false;
        }

        if (++this.scanTicker < SCAN_INTERVAL_TICKS)
        {
            return false;
        }

        this.scanTicker = 0;

        Optional<StomachCell> cell = this.mob.currentStomachCell();

        if (cell.isEmpty())
        {
            // Not inside a stomach: the two ordinary target goals own this situation.
            return false;
        }

        // The same helper the rest of the stomach uses keeps exactly one living core in a loaded cell. A core that
        // died or was removed therefore comes back and is attacked again, and no second core system is created here
        // (part G.3 and part L.4).
        StomachCellManager.ensureCore(level, cell.get());

        this.core = StomachCellManager.coreOf(level, cell.get()).orElse(null);

        return this.core != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.core == null || this.core.isRemoved() || !this.core.isAlive() || !this.mob.isCellCore(this.core))
        {
            return false;
        }

        // Claim the target every tick, the same way the vanilla target goals do, so a core that is still standing
        // can not be lost to a stray setTarget from somewhere else.
        this.mob.setTarget(this.core);

        return true;
    }

    @Override
    public void start()
    {
        this.mob.setTarget(this.core);
    }

    @Override
    public void stop()
    {
        if (this.mob.getTarget() == this.core)
        {
            this.mob.setTarget(null);
        }

        this.core = null;
    }
}
