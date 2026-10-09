package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;

/**
 * The memory of a spider mine (Spider Mine and Egg Arrow round): who placed it, and nothing else.
 *
 * <p>The one value it holds is the answer to the only question a mine ever has to ask about its owner: the spiders
 * that come out of the blast belong to the player who <b>placed</b> the mine, never to whoever happens to set it off
 * later - a redstone line, a piston, a stranger with a bow or the queen herself.
 *
 * <p>It is the same shape as the memory of a spider egg ({@code SpiderEggBlockEntity}), and for the same reason: a
 * block that hands a living creature to a player has to remember that player across a save, and a block entity is
 * the one place the game keeps it for a block.
 */
public class SpiderMineBlockEntity extends BlockEntity
{
    /** NBT key of the player this mine belongs to. */
    private static final String KEY_OWNER = "MineOwner";

    /** The placer of the mine, and therefore the queen of every spider it delivers. */
    @Nullable
    private UUID owner;

    public SpiderMineBlockEntity(BlockPos pos, BlockState state)
    {
        super(HungryCraftBlockEntities.SPIDER_MINE.get(), pos, state);
    }

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
    }

    /** @return the player who placed this mine, or null for a mine that was placed by anything else. */
    @Nullable
    public UUID getOwner()
    {
        return this.owner;
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
