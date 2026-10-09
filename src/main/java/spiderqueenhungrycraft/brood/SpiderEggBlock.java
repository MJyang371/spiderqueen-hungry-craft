package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The spider egg on the ground (this round's breeding pass): a purple egg of the shape of a head.
 *
 * <p>It is placed with a right click, it needs no tool and no special ground, an empty hand breaks it
 * quickly and it always comes back as the egg item itself. Ten seconds after it was placed it opens by
 * itself and one {@link ImpSpiderEntity imp spider} steps out - and the spider belongs to the player who
 * placed the egg.
 *
 * <p><b>The shape of the model is a head</b> (a cube from 4,0,4 to 12,8,12, the silhouette of a skull of the
 * game), and the picture of the egg is drawn on every one of its six faces, so the egg reads the same from
 * every side and from above. The texture is the size of a head texture of the game, 64 by 64 pixels
 * ({@code textures/block/spider_egg.png}): the picture the queen drew is 32 by 32 and is doubled into that
 * canvas, and the model of the block reads it with the 0..16 units every block model of the game uses.
 *
 * <p>Broken is not hatched: a broken egg drops its own item and never opens. An egg that was taken back into
 * the inventory is gone from the world, and placing it again always starts a fresh ten seconds of its own -
 * even when the pending block tick of the egg that was broken there before it is still queued at that very
 * position, which is why every egg remembers when it is due (see {@link SpiderEggBlockEntity}).
 */
public class SpiderEggBlock extends BaseEntityBlock
{
    /** Ten seconds of incubation, in ticks. */
    public static final int HATCH_TICKS = 200;

    /** The selection and outline box of the egg: exactly the cube of the head shaped model of this block. */
    private static final VoxelShape SHAPE = Block.box(4.0D, 0.0D, 4.0D, 12.0D, 8.0D, 12.0D);

    public SpiderEggBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SpiderEggBlockEntity(pos, state);
    }

    /** The egg is a small model on the floor, not an invisible block that only hides a block entity. */
    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    /** A freshly placed egg waits its ten seconds, whether it is placed by hand or by anything else. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (level.isClientSide)
        {
            return;
        }

        // The block entity of this placement records when this egg is due the moment it is attached (see
        // SpiderEggBlockEntity#setLevel), so a pending tick that is still queued at this position from an egg
        // that was broken here can never open this one.
        level.scheduleTick(pos, this, HATCH_TICKS);
    }

    /** The placer of an egg is the owner of the spider that hatches out of it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SpiderEggBlockEntity egg)
        {
            egg.setOwner(player.getUUID());
        }
    }

    /**
     * The ten second countdown is over: the egg opens and one imp spider steps out of it. The egg itself is
     * simply gone - no egg item, no loot and no experience, exactly like a turtle egg that hatches.
     *
     * <p>A tick only ever opens the egg it was scheduled for. Breaking an egg does not take its pending tick
     * out of the queue of the world - the block simply is not there any more, so the tick does nothing - but
     * a new egg placed on that very position within those ten seconds would inherit it. The block entity
     * therefore remembers when this egg is due, and an early tick of a previous egg only puts the countdown
     * back on schedule instead of hatching.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        SpiderEggBlockEntity egg = level.getBlockEntity(pos) instanceof SpiderEggBlockEntity entity ? entity : null;
        long remaining = egg == null ? 0L : egg.getHatchAt() - level.getGameTime();

        if (remaining > 0L)
        {
            level.scheduleTick(pos, this, (int) Math.min(remaining, Integer.MAX_VALUE));
            return;
        }

        UUID owner = egg == null ? null : egg.getOwner();
        ImpSpiderEntity spider = HungryCraftEntities.IMP_SPIDER.get().create(level);

        if (spider != null)
        {
            spider.setOwnerUUID(owner);
            spider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
            level.addFreshEntity(spider);
        }

        level.removeBlock(pos, false);
        level.levelEvent(2001, pos, Block.getId(state));
        level.playSound(null, pos, SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 0.8F, 1.1F);
    }
}
