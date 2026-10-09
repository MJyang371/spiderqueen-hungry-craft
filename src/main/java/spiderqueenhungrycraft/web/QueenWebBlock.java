package spiderqueenhungrycraft.web;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.bed.SpiderBedRitual;

/**
 * Temporary web block created by a web shot. It copies the vanilla cobweb physics and disappears on its own.
 *
 * <p>It keeps the normal block loot pipeline (empty loot table), so the web harvest of the Spider Queen,
 * which replaces the drops with string, applies to it exactly like it does to a vanilla cobweb. Everybody
 * else gets nothing out of it, so webbing around can not be turned into free string.
 */
public class QueenWebBlock extends WebBlock
{
    /** 6000 ticks, about five minutes. */
    public static final int LIFETIME_TICKS = 6000;

    public QueenWebBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        // Web natives are not excluded from the web any more, they get the gentle Web Affinity physics from
        // the vanilla path (the stuck multiplier is softened for them) plus the per tick affinity helper.
        super.entityInside(state, level, pos, entity);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving)
    {
        super.onPlace(state, level, pos, oldState, isMoving);

        if (level instanceof ServerLevel serverLevel)
        {
            // A scheduled tick instead of a block entity, so hundreds of webs cause no ticking load.
            serverLevel.scheduleTick(pos, this, LIFETIME_TICKS);

            // The spider bed ritual (Phase 8E3A-GATE1): a queen web that completes a 5x5 wooden frame turns
            // the nine webs of the inner 3x3 into spider beds. This is the only trigger of the whole feature
            // and it is server side, but it serves a hand placed web and a web shot alike, because both of
            // them enter the world through Level#setBlock and therefore both of them run through this hook.
            SpiderBedRitual.tryFormAround(serverLevel, pos);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        level.removeBlock(pos, false);
    }
}
