package spiderqueenhungrycraft.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CTerritoryRangePacket;
import spiderqueenhungrycraft.progression.ProgressionService;

/**
 * The territory core of a Spider Queen lair (Phase LAIR-CORE-1).
 *
 * <p>It looks like a glowing web core and is nothing else: an ordinary, solid block with ordinary collision. It does
 * not slow anybody down, it never wraps, it never captures, it never decays and it never turns into a cocoon. Its
 * only power is that no creature spawns naturally inside the 3x3x3 chunk sections around it.
 *
 * <p>Two things about it are deliberate:
 * <ul>
 * <li>a break takes <b>exactly sixty seconds</b> with any tool, because the destroy progress per tick is a constant
 * and never comes from the hardness of the block - so Efficiency, a netherite pickaxe or a bare hand all need the
 * same 1200 ticks,</li>
 * <li>a right click shows the protected range to that one player for five seconds, through rock, because the
 * protection itself does not care about walls either.</li>
 * </ul>
 */
public class TerritoryCoreBlock extends Block
{
    /** The light the core emits: it glows in a cave but does not light a whole room. */
    public static final int LIGHT_LEVEL = 10;

    /** The progress one tick of mining adds up to. 1/1200 means 1200 ticks, which is sixty seconds. */
    public static final float DESTROY_PROGRESS_PER_TICK = 1.0F / 1200.0F;

    /** How often the portal particle of a core appears, on average one in six client ticks. */
    private static final int PARTICLE_CHANCE = 6;

    public TerritoryCoreBlock(Properties properties)
    {
        super(properties);
    }

    /** The range is protected from the very tick the core is placed. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        super.onPlace(state, level, pos, oldState, movedByPiston);

        if (!level.isClientSide && !oldState.is(this) && level instanceof ServerLevel serverLevel)
        {
            TerritoryCoreIndex.register(serverLevel, pos);
        }
    }

    /**
     * The range stops being protected the moment the core is gone, whatever took it away: a pickaxe, an explosion, a
     * piston or a command. A chunk that is merely unloaded never removes the block, so the protection of a core
     * survives a player walking away.
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston)
    {
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel)
        {
            TerritoryCoreIndex.unregister(serverLevel, pos);
        }

        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * The ender particles of the core. They are pure decoration: they deal no damage, they teleport nothing and they
     * tell no entity anything.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(PARTICLE_CHANCE) != 0)
        {
            return;
        }

        double x = pos.getX() + 0.25D + random.nextDouble() * 0.5D;
        double y = pos.getY() + 0.25D + random.nextDouble() * 0.5D;
        double z = pos.getZ() + 0.25D + random.nextDouble() * 0.5D;
        level.addParticle(ParticleTypes.PORTAL, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    /**
     * A constant progress, so the time to break the core never depends on the tool. Vanilla reads this value from the
     * block state every tick while a player mines, which is exactly why nothing else has to be tracked anywhere.
     */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        return DESTROY_PROGRESS_PER_TICK;
    }

    /**
     * What a right click on a core does (Progression Foundation round).
     *
     * <p>The old gesture keeps its own meaning and nothing else claims it: <b>sneaking with both hands empty</b> shows
     * the protected range. <b>Every other right click opens the growth interface</b> of this core - with an empty hand,
     * with something carried, and while sneaking with something carried (that last one is handed to this block by
     * {@link TerritoryCoreInteractionEvents}, because vanilla would otherwise never ask the block at all).
     *
     * <p>The client only answers that this is an interaction and swings the arm: the range is sent, and the interface
     * is opened, by the server alone.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit)
    {
        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.CONSUME;
        }

        if (isRangeGesture(player))
        {
            HungryCraftNetwork.sendToPlayer(new S2CTerritoryRangePacket(pos), serverPlayer);
            serverPlayer.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.territory_range_shown"), true);

            return InteractionResult.CONSUME;
        }

        ProgressionService.openInterface(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    /**
     * @return true when that click is the one that shows the protected range: the player sneaks and carries nothing
     *         at all. Sneaking with something in hand is deliberately <b>not</b> this gesture - that click belongs to
     *         the growth interface.
     */
    private static boolean isRangeGesture(Player player)
    {
        return player.isSecondaryUseActive() && player.getMainHandItem().isEmpty()
                && player.getOffhandItem().isEmpty();
    }
}
