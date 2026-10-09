package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The spider mine (Spider Mine and Egg Arrow round): a block of TNT with a spider egg cluster built into it.
 *
 * <p>It has exactly three triggers and no others. A redstone signal of any kind sets it off - the ordinary
 * {@link #neighborChanged} of the game, plus one armed tick after placement so a mine that is put down next to a live
 * signal does not sit there armed and silent - the sceptre of the queen sets it off with a click
 * ({@link #triggerFromScepter}), and a hand of fire is laid to it exactly the way it is laid to the block of TNT of the
 * game ({@link #use}: flint and steel, or a fire charge). Every other right click - an empty hand, a bow, a bucket -
 * still does nothing at all: a mine is never set off by accident, and lighting one always takes a tool whose whole
 * purpose is fire.
 *
 * <p><b>It is blast proof</b> ({@link #EXPLOSION_RESISTANCE}). No explosion of the world takes this block out, and none
 * of them can light it either: the game only ever offers a block to a blast (and to the fuse lighting that a blast
 * carries with it, {@code wasExploded}) while the energy of that blast is stronger than the resistance of the block.
 * The delayed mine ({@link DelayedSpiderMineBlock}) is deliberately the other way round - it is the one of the two that
 * a blast lights, and it is the one a blast can take.
 *
 * <p>What happens then is the business of {@link SpiderMineExplosion} and is not re-decided here: one blast of the
 * power of TNT, eight imp spiders around it that survive it, and - only for the sceptre trigger - those eight as the
 * selection of the queen who placed the mine.
 *
 * <p><b>Who the spiders belong to is decided at the moment of placement</b> and lives in
 * {@link SpiderMineBlockEntity}: the placer of the mine, never the player or the machine that triggers it.
 */
public class SpiderMineBlock extends BaseEntityBlock
{
    /** How many imp spiders a mine delivers. */
    public static final int SPAWNED_SPIDERS = 8;

    /**
     * The blast resistance of a mine, in the units the properties of a block count it in.
     *
     * <p>It is the number of obsidian, and it is meant as <b>immunity</b> rather than as toughness: the game only
     * offers a block to an explosion at all while the energy of that blast is still above this number, so a mine is
     * never destroyed by a blast of the world - and, because that same offer is what carries the fuse lighting of
     * {@code wasExploded}, never lit by one either. A single blast can not grow into four digits: the strongest one the
     * game has (a charged creeper) spends itself around eight.
     *
     * <p>The mine never needs its own blast to break it, which is what makes that immunity free: the block leaves the
     * world before its explosion runs ({@link SpiderMineExplosion#detonate}).
     */
    public static final float EXPLOSION_RESISTANCE = 1200.0F;

    public SpiderMineBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new SpiderMineBlockEntity(pos, state);
    }

    /** A mine is a real block of the world and not an invisible holder of a block entity. */
    @Override
    public RenderShape getRenderShape(BlockState state)
    {
        return RenderShape.MODEL;
    }

    /**
     * The placer of a mine is the owner of the spiders it will deliver.
     *
     * <p>It is written here, at the moment the block really exists, because this is the one callback of the game that
     * still knows who placed it. Everything after that - a redstone line, a piston, a stranger or the queen herself -
     * reads the owner from the block entity and never asks who triggered the blast.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack)
    {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof SpiderMineBlockEntity mine)
        {
            mine.setOwner(player.getUUID());
        }
    }

    /**
     * A mine that is placed onto a signal that is already on arms itself one tick later.
     *
     * <p>It cannot be checked right here: the block entity that remembers the placer does not exist yet while a placed
     * block is being set up, and a mine that fired in this very moment would hand its spiders to nobody. One tick is
     * all it takes for the mine to be complete, and the check itself lives in {@link #tick}.
     */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide && !oldState.is(this))
        {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** The armed tick of a freshly placed mine: it fires when the place it landed in is already powered. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (level.hasNeighborSignal(pos))
        {
            SpiderMineExplosion.detonate(level, pos, false, null);
        }
    }

    /** The redstone trigger: any signal into the mine sets it off, whatever its source. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving)
    {
        if (!level.isClientSide && level.hasNeighborSignal(pos))
        {
            SpiderMineExplosion.detonate((ServerLevel) level, pos, false, null);
        }
    }

    /**
     * A hand of fire laid to a mine, exactly the way the game lays one to a block of TNT ({@code TntBlock#use}).
     *
     * <p>Only true fire is accepted: flint and steel and a fire charge, the two things of the game that light something
     * directly. Everything else is handed back to the ordinary right click of the block, which is nothing at all - the
     * block has no use of its own and still has none.
     *
     * <p>What the fire then does is the one thing the two mines disagree about, and it is left to {@link #ignite}: an
     * immediate mine goes off where it stands, because it has no fuse to light, while the delayed one only starts its
     * fuse. The click itself - what is accepted, what it costs and what the client is told - is written once, here.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!stack.is(Items.FLINT_AND_STEEL) && !stack.is(Items.FIRE_CHARGE))
        {
            return super.use(state, level, pos, player, hand, hit);
        }

        this.ignite(level, pos);

        // The tool is spent exactly like the game spends it on TNT: a point of the flint and steel (with the sound of a
        // tool breaking when it was its last one), one fire charge, and nothing at all in creative mode.
        if (!player.isCreative())
        {
            if (stack.is(Items.FLINT_AND_STEEL))
            {
                stack.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
            }
            else
            {
                stack.shrink(1);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(stack.getItem()));

        // Both sides answer with "taken": the server is the one that lit the mine, and the client has to say so as
        // well or the game would never send this click to the server in the first place.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Sets this mine off with a hand of fire ({@link #use}).
     *
     * <p>The level and not a server level is what this takes, because the click itself runs on both sides and only the
     * side that really owns the world may do the work: the check for that lives in each mine's own implementation.
     */
    protected void ignite(Level level, BlockPos pos)
    {
        if (level instanceof ServerLevel server)
        {
            SpiderMineExplosion.detonate(server, pos, false, null);
        }
    }

    /**
     * The sceptre trigger of the queen: one click on a mine fires it, and the spiders it delivers become her selection
     * when she is the player who placed it.
     *
     * <p>Nothing about the sceptre is done here - the client only ever asks "I used the sceptre" and the server casts
     * its own ray into this method, so no modified client can fire a mine it does not point at.
     */
    public void triggerFromScepter(ServerLevel level, BlockPos pos, ServerPlayer queen)
    {
        SpiderMineExplosion.detonate(level, pos, true, queen);
    }
}
