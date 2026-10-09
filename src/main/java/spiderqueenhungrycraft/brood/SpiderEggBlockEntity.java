package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;

/**
 * The memory of an egg on the ground (this round's breeding pass): who placed it, and when this very egg is
 * due.
 *
 * <p>It holds nothing else. The countdown of the egg is not stored at all, because a freshly placed egg
 * always starts its ten seconds over - exactly the rule this round asks for: an egg that is picked up and
 * put down again waits ten seconds again.
 */
public class SpiderEggBlockEntity extends BlockEntity
{
    /** NBT key of the player whose egg this is. */
    private static final String KEY_OWNER = "HatchOwner";

    @Nullable
    private UUID owner;

    /**
     * The game time at which this very egg is due, 0 while it was never scheduled.
     *
     * <p>It is deliberately transient. The pending block tick of the world is saved with the chunk, so an
     * egg that was left behind still opens when it was due after a reload - and an egg that is placed again
     * gets a brand new block entity, and with it a brand new ten seconds, even if the old pending tick of
     * the egg that was taken away is still queued at that position.
     */
    private long hatchAt;

    public SpiderEggBlockEntity(BlockPos pos, BlockState state)
    {
        super(HungryCraftBlockEntities.SPIDER_EGG.get(), pos, state);
    }

    /**
     * A brand new egg starts counting down the moment its block entity really exists.
     *
     * <p>That moment has to be used, because {@code onPlace} of the block still runs <b>before</b> the game
     * creates the block entity of a freshly placed block - a countdown written there would be lost. This is
     * also what makes a re-placed egg safe: it gets a brand new block entity, so it always starts its own
     * ten seconds, whatever an egg that was broken here before it left in the tick queue of the world.
     */
    @Override
    public void setLevel(Level level)
    {
        super.setLevel(level);

        if (level != null && this.hatchAt == 0L)
        {
            this.hatchAt = level.getGameTime() + SpiderEggBlock.HATCH_TICKS;
        }
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
    }

    /** @return the player who placed the egg, and therefore owns the spider that hatches out of it. */
    @Nullable
    public UUID getOwner()
    {
        return this.owner;
    }

    /** Starts the countdown of this egg: it opens at that game time. */
    public void startCountdown(long hatchAt)
    {
        this.hatchAt = hatchAt;
    }

    /** @return the game time this egg is due at, 0 when it was never scheduled. */
    public long getHatchAt()
    {
        return this.hatchAt;
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);

        if (this.owner != null)
        {
            tag.putUUID(KEY_OWNER, this.owner);
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);

        if (tag.hasUUID(KEY_OWNER))
        {
            this.owner = tag.getUUID(KEY_OWNER);
        }
    }
}

