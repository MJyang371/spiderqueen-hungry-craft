package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The delayed spider mine (Spider Trigger round): the same trap, with a fuse.
 *
 * <p>It looks and is placed like a spider mine and it answers the same triggers - a redstone signal, the sceptre of the
 * queen, and a hand of fire ({@link SpiderMineBlock#use}) - but it does not go off: it <b>leaves the world and becomes
 * the primed entity</b> of this mod ({@link PrimedSpiderMineEntity}), which is a block of TNT of the game in every
 * respect. It falls, it hops, it puffs smoke, it flashes white and it blows up four seconds later, and only then are
 * the eight imp spiders delivered.
 *
 * <p>That delay is the whole point of it: an immediate mine fires where it stands, while a delayed one gives the
 * queen four seconds and the fact that it is a <b>moving</b> entity - it can be punched away, blown away by another
 * blast or simply fall off the wall it was stuck to, and the brood arrives wherever it ends up.
 *
 * <p><b>It is the one of the two mines that a blast lights</b>, exactly like the TNT it is built like. The block is
 * deliberately as easy to blow up as TNT ({@link #EXPLOSION_RESISTANCE}, which is nothing), so any explosion of the
 * world that reaches it - the blast of another spider mine or of another delayed one, of a block of TNT, of a creeper,
 * of anything - takes it out of the world and <b>lights its fuse</b> on the way: what comes out of that is the primed
 * entity of this mod with the short and rattling fuse of a chain of TNT, and not a hole in the ground. The spider mine
 * is the opposite case on purpose: it is blast proof and no explosion can light it.
 *
 * <p>Everything about the blast itself is the business of its parent and of {@link SpiderMineExplosion}: this class
 * only replaces "go off now" with "light the fuse".
 */
public class DelayedSpiderMineBlock extends SpiderMineBlock
{
    /**
     * The blast resistance of a delayed mine: none at all, the number of the block of TNT of the game.
     *
     * <p>It is the resistance that makes this mine lightable by a blast: the game only offers a block to an explosion
     * while the energy of that blast is above its resistance, and a number of zero is always below it. A block that
     * keeps a blast out can not be lit by one - which is exactly how the spider mine refuses to be lit.
     */
    public static final float EXPLOSION_RESISTANCE = 0.0F;

    public DelayedSpiderMineBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
    }

    /** The armed tick of a freshly placed mine: it lights its fuse when the place it landed in is already powered. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (level.hasNeighborSignal(pos))
        {
            this.prime(level, pos, false, null);
        }
    }

    /** The redstone trigger: any signal into the block lights the fuse. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean isMoving)
    {
        if (!level.isClientSide && level.hasNeighborSignal(pos))
        {
            this.prime((ServerLevel) level, pos, false, null);
        }
    }

    /** The sceptre trigger of the queen: it lights the fuse, and it says so, because nothing happens for a while. */
    @Override
    public void triggerFromScepter(ServerLevel level, BlockPos pos, ServerPlayer queen)
    {
        this.prime(level, pos, true, queen);
    }

    /**
     * The hand of fire of the game ({@link SpiderMineBlock#use}): it does not fire this mine, it lights its fuse.
     *
     * <p>That is what makes a delayed mine the TNT of this mod: a hand of fire on TNT does not set it off either, it
     * starts the four seconds of it.
     */
    @Override
    protected void ignite(Level level, BlockPos pos)
    {
        if (level instanceof ServerLevel server)
        {
            this.prime(server, pos, false, null);
        }
    }

    /**
     * The one trigger that is not a hand: <b>being caught by an explosion</b>, whoever set it off and whatever kind it
     * was - another spider mine, another delayed mine, a block of TNT, a creeper, anything that explodes.
     *
     * <p>It is the chain reaction of the block of TNT of the game, and it is deliberately that: the blast takes the
     * block out of the world and the mine answers by becoming its primed entity with a short fuse, so a row of mines
     * rattles away one after another instead of leaving a hole in the ground.
     *
     * <p><b>Why this method and not {@code wasExploded}.</b> The TNT of the game lights its fuse in
     * {@code wasExploded}, which is the hook the game calls for a block that an explosion destroyed - and its own chain
     * reaction needs nothing but the position it is given. A mine needs one thing more: the placer of it, which lives
     * in its block entity. By the time {@code wasExploded} runs that memory is already gone, because the default
     * implementation of the game clears the block first and only then calls it. The placer is therefore read here,
     * before anything is removed, and the block is taken out of the world by hand.
     */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion)
    {
        UUID owner = level.getBlockEntity(pos) instanceof SpiderMineBlockEntity mine ? mine.getOwner() : null;

        // The block is consumed by the blast, exactly as the default of the game consumes it - and exactly as the
        // priming of a redstone trigger consumes it: it can never be lit twice and it never comes back as an item.
        level.removeBlock(pos, false);

        if (level instanceof ServerLevel server)
        {
            this.lightFuse(server, pos, owner, false, chainFuse(server.random));
        }
    }

    /**
     * A delayed mine that was caught by a blast never comes back as an item: what is left of it is the primed entity
     * that blast lit, so dropping the block as well would hand the queen a second mine out of one.
     *
     * <p>This is the hook the game asks about loot before it destroys a block with an explosion, and the answer is the
     * one the block of TNT of the game gives.
     */
    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion)
    {
        return false;
    }

    /**
     * Lights the fuse: the block leaves the world and a primed entity takes its place, carrying the placer of the mine
     * and the trigger with it.
     */
    private void prime(ServerLevel level, BlockPos pos, boolean byScepter, @Nullable ServerPlayer queen)
    {
        if (!(level.getBlockState(pos).getBlock() instanceof DelayedSpiderMineBlock))
        {
            return;
        }

        UUID owner = level.getBlockEntity(pos) instanceof SpiderMineBlockEntity mine ? mine.getOwner() : null;

        // The block is consumed by the priming, exactly like it is by the blast of an immediate mine: it can never be
        // lit twice and it never comes back as an item.
        level.removeBlock(pos, false);

        this.lightFuse(level, pos, owner, byScepter, PrimedSpiderMineEntity.FUSE_TICKS);

        if (byScepter && queen != null && queen.connection != null)
        {
            queen.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.mine_fuse_lit"), true);
        }
    }

    /**
     * Puts the primed entity of this mine into the world where the block just stood: the object with its fuse, the
     * fuse it was given, and the sound the game makes when a fuse starts.
     *
     * @param fuse how many ticks that fuse is to run, which is what separates a hand that lit this mine from an
     *             explosion that caught it
     */
    private void lightFuse(ServerLevel level, BlockPos pos, @Nullable UUID owner, boolean byScepter, int fuse)
    {
        PrimedSpiderMineEntity primed = new PrimedSpiderMineEntity(level, pos.getX() + 0.5D, pos.getY(),
                pos.getZ() + 0.5D, owner, byScepter);

        // The constructor starts the ordinary fuse of a mine; the caller decides whether that is the one it wants.
        primed.setFuse(fuse);

        level.addFreshEntity(primed);
        level.playSound(null, primed.getX(), primed.getY(), primed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS,
                1.0F, 1.0F);
    }

    /**
     * @return the fuse of a mine that was caught by a blast: the short and half-random one the game gives a block of
     *         TNT that another explosion lit, so a chain of mines rattles away instead of marching in step.
     */
    private static int chainFuse(RandomSource random)
    {
        return random.nextInt(PrimedSpiderMineEntity.FUSE_TICKS / 4) + PrimedSpiderMineEntity.FUSE_TICKS / 8;
    }
}
