package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;

/**
 * The spider trigger (Spider Trigger round): a pressure plate that watches.
 *
 * <p>It is the eyes of a queen's lair. Anything that is not hers stepping into the small sphere around it is reported
 * to her in the action bar, gets five seconds of outline, and trips a redstone signal on the way - and the plate it
 * is built as can be dressed up as any full block of the game, so a queen can hide it in her own floor.
 *
 * <p><b>Redstone.</b> The block is a signal source in the ordinary way of the game: a state bit, fifteen of signal
 * while it is set and none while it is not. It is never walkable and never has a "pressed" state of its own - the
 * plate of the game reacts to feet, this one reacts to creatures.
 *
 * <p><b>The disguise.</b> A queen who sneaks and right clicks the plate with a full block hides it as that block, and
 * the plate is then drawn with the <b>top</b> texture of it (a grass block hides it as grass). The block model of the
 * trigger is empty in that state and a block entity renderer draws the plate, which is the only way a single block
 * can wear a texture that is not known while the resource pack is loaded.
 *
 * <p>Only full blocks are accepted, and the test is the collision shape of the block state - the same answer the game
 * itself uses for "is this a full cube".
 */
public class SpiderTriggerBlock extends BaseEntityBlock
{
    /** The state bit of the redstone signal this trigger is emitting right now. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    /** The state bit that says the block model is off and the renderer of the block entity owns the plate. */
    public static final BooleanProperty DISGUISED = BooleanProperty.create("disguised");

    /**
     * How far this trigger watches, in blocks.
     *
     * <p>Slightly smaller than the blast of a spider mine, which reaches the four blocks of a block of TNT: a trigger
     * is the trip wire in front of the mine, so it has to be a little closer than the blast it warns about.
     */
    public static final double DETECTION_RANGE = 3.0D;

    /** How often the sphere is swept: four times a second is far more than a walking creature needs. */
    public static final int SCAN_INTERVAL_TICKS = 5;

    /** How long one redstone pulse lasts. */
    public static final int PULSE_ON_TICKS = 2;

    /** And how long the silence between two pulses of two arrivals is. */
    public static final int PULSE_GAP_TICKS = 2;

    /** The most pulses one trigger keeps queued: a crowd is not a metronome. */
    public static final int MAX_PENDING_PULSES = 32;

    /** Five seconds of outline on the intruder. */
    public static final int GLOW_TICKS = 100;

    /** The plate: the shape of a pressure plate of the game, so a queen can walk over her own trigger. */
    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 1.0D, 15.0D);

    public SpiderTriggerBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, false).setValue(DISGUISED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(POWERED, DISGUISED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SpiderTriggerBlockEntity(pos, state);
    }

    /** The watch runs on the server only: a trigger decides nothing on a client. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        return level.isClientSide ? null
                : createTickerHelper(type, HungryCraftBlockEntities.SPIDER_TRIGGER.get(),
                        SpiderTriggerBlockEntity::serverTick);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    /** The plate is a real model of the world - the stone one, or the empty one a disguise turns it into. */
    @Override
    public net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state)
    {
        return net.minecraft.world.level.block.RenderShape.MODEL;
    }

    /** The placer of a trigger is the queen it reports to and the one creature it never reports about. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SpiderTriggerBlockEntity trigger)
        {
            trigger.setOwner(player.getUUID());
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (level.getBlockEntity(pos) instanceof SpiderTriggerBlockEntity trigger)
        {
            trigger.onScheduledTick(level);
        }
    }

    // ------------------------------------------------------------------ the signal

    @Override
    public boolean isSignalSource(BlockState state)
    {
        return true;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return state.getValue(POWERED) ? 15 : 0;
    }

    /** A signal a neighbour reads out of the block itself: a lamp, a piston and a door all have to answer. */
    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return this.getSignal(state, level, pos, direction);
    }

    // ------------------------------------------------------------------ the disguise

    /**
     * Right click with a full block: the plate is hidden as that block and drawn with its top texture.
     *
     * <p><b>A plain right click does it, and a sneaking one does not.</b> Sneaking is the gesture of the game for
     * "place the block I am holding against this, whatever this block would rather do", and a queen who is building
     * her own floor must be able to lay a block onto her own trigger - so a sneak click is handed straight back to the
     * game (this method does nothing) and the block in her hand is placed as usual. Every other right click with a
     * full block is the disguise.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit)
    {
        if (player.isShiftKeyDown())
        {
            // Sneaking: not our business at all, so the vanilla placement of the held block happens.
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);

        if (!(held.getItem() instanceof BlockItem blockItem))
        {
            return InteractionResult.PASS;
        }

        BlockState want = blockItem.getBlock().defaultBlockState();

        if (!want.isCollisionShapeFullBlock(level, pos))
        {
            // Only a full block can hide a plate: a slab, a plant or a torch would show half a plate and half a lie.
            return InteractionResult.PASS;
        }

        if (level.getBlockEntity(pos) instanceof SpiderTriggerBlockEntity trigger)
        {
            if (!level.isClientSide)
            {
                trigger.setDisguise(level, pos, state, want);
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_PLACE,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.1F);
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }
}
