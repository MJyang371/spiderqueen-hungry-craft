package spiderqueenhungrycraft.bed;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.web.WebAffinity;

/**
 * The spider bed (Phase 8E3): the daylight lair the Spider Queen weaves out of her own webs.
 *
 * <p>It is an ordinary, independent block: no block entity, no controller, no owner and no memory of the
 * ritual that created it. Nine of them appear at once when a queen web completes a 5x5 wooden frame, but
 * from that moment on every single bed lives on its own, exactly like a bed that was placed by hand.
 *
 * <p>The only state a bed carries is the axis of the plane it lies in, which is the normal of the surface
 * it is attached to: {@code Y} is a floor or a ceiling, {@code X} and {@code Z} are the two kinds of wall.
 * The up or down side is deliberately not part of the state, a bed has no head end and no face.
 *
 * <p>The bed is three things at once, and none of them is the vanilla bed: a web block, in which web
 * natives keep their gentle physics and everybody else is caught like in a cobweb; a resting place, which
 * always hands a humanoid queen her respawn point and, in daylight, hibernates the rest of the day away;
 * and a terrible idea in a dimension where beds do not work, where it explodes exactly like a vanilla bed.
 *
 * <p>It never puts anybody to sleep: there is no sleeping pose, no bed part, no occupancy and no vanilla
 * sleeping pipeline anywhere in this block.
 */
public class SpiderBedBlock extends Block
{
    /** Hardness of one bed, low on purpose: a bed is a mat of web, not a wall. */
    public static final float HARDNESS = 0.3F;

    /** Explosion resistance of one bed, in the same ballpark as its hardness. */
    public static final float EXPLOSION_RESISTANCE = 0.3F;

    /** The plane the bed lies in, the value is the normal axis of that plane. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    /**
     * What a normal entity is caught with inside a bed: the very same multiplier a vanilla cobweb uses, so
     * a villager really has to fight its way through a spider bed.
     */
    public static final Vec3 STUCK_MULTIPLIER = new Vec3(0.25D, 0.05D, 0.25D);

    public SpiderBedBlock(BlockBehaviour.Properties properties)
    {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Y));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AXIS);
    }

    /**
     * The clicked face decides the plane of a freshly placed bed, so a bed taken out of the creative tab
     * lies flat on the floor, lies flat under a ceiling and stands on a wall, without any ritual at all.
     * Only the axis of that face is used, never its direction: a bed has no front and no back.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
    }

    /**
     * The whole interaction of a bed: the invalid dimension explosion, the respawn point, the daylight
     * hibernation and the flavour lines. Everything is decided on the server, the client only swings.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit)
    {
        return SpiderBedInteraction.use(level, pos, player);
    }

    /**
     * The web physics of a bed.
     *
     * <p>A web native, that is the Spider Queen and everything in the spider_like tag, is never caught:
     * its gentle drag, its limited fall speed and its fall damage immunity come from the shared web
     * affinity of the mod, which runs once per tick for every block of the
     * {@code #spiderqueen_hungrycraft:web_blocks} tag, and a bed is part of that tag.
     *
     * <p>Everybody else is caught exactly like in a vanilla cobweb.
     */
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (WebAffinity.isWebNative(entity))
        {
            return;
        }

        entity.makeStuckInBlock(state, STUCK_MULTIPLIER);
    }

    /**
     * The position a player is moved to when she respawns at this bed after a death. This is the official
     * Forge 1.20.1 extension point, which the vanilla respawn pipeline calls with the block state at the
     * recorded spawn position.
     *
     * <p>Nothing else about respawning is changed: the vanilla {@code ServerPlayer#setRespawnPosition} still
     * stores the position, the vanilla pipeline still resolves it and the vanilla fallback still applies
     * when this bed is gone or when no safe spot is left around it. An empty result simply means vanilla.
     */
    @Override
    public Optional<Vec3> getRespawnPosition(BlockState state, EntityType<?> type, LevelReader levelReader, BlockPos pos,
            float orientation, @Nullable LivingEntity entity)
    {
        if (!(levelReader instanceof ServerLevel serverLevel) || !BedBlock.canSetSpawn(serverLevel))
        {
            return Optional.empty();
        }

        return SpiderBedRespawn.findSafePosition(type, serverLevel, pos, state.getValue(AXIS));
    }
}
