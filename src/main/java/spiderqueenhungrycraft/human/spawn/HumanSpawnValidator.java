package spiderqueenhungrycraft.human.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.resources.ResourceLocation;

import spiderqueenhungrycraft.human.spawn.HumanSpawnConfig.SurfaceRules;
import spiderqueenhungrycraft.territory.TerritoryCoreSpawnRules;

/**
 * The one who says no (Phase HF0D).
 *
 * <p>Every rule of the phase lives here: the dimension, the open sky, the full block under the feet, the forbidden
 * ground tags, the fluids and the space the human himself needs. A position that fails any of them is simply not
 * used, there is no "place him anyway" path.
 */
public final class HumanSpawnValidator
{
    private HumanSpawnValidator()
    {
    }

    /** @return true when that dimension is one the rules allow. */
    public static boolean isAllowedDimension(ServerLevel level, SurfaceRules rules)
    {
        for (ResourceLocation allowed : rules.dimensions())
        {
            if (allowed.equals(level.dimension().location()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * The conditions a spawn position has to pass, one constant per check of this class.
     *
     * <p>They exist so a rejected position can name the very condition that refused it: without that, a spawner that
     * never places a human looks exactly like a spawner that was never asked to.
     */
    public enum Reason
    {
        /** The target position is not in a loaded chunk yet. */
        CHUNK_NOT_LOADED,
        /** There is a fluid where he would stand or where his head would be. */
        FLUID,
        /** The rules ask for open sky and something is over the position. */
        NO_SKY,
        /** The block under his feet is air, so the heightmap lied or he would fall. */
        NO_GROUND,
        /** The block under his feet is part of a forbidden tag, for example leaves. */
        FORBIDDEN_GROUND,
        /** The rules ask for a full block and the ground is not one. */
        GROUND_NOT_FULL,
        /** His own bounding box does not fit: a wall, a roof or a window is in the way. */
        COLLISION,
        /**
         * A <b>natural</b> spawn in a peaceful world. The humans of this mod are {@code PathfinderMob}s and never
         * vanilla monsters, so nothing in the game stops them - this rule is that stop, and it is deliberately ours.
         */
        PEACEFUL,
        /** A <b>natural</b> spawn inside the protection of a territory core (Phase LAIR-CORE-1). */
        TERRITORY;
    }

    /**
     * @return true when a human of that type may stand at that position.
     *
     * <p>The checks are the ones the phase asks for: no fluid at his feet or head, open sky above him when the rules
     * ask for it, a solid full block under his feet that is not part of a forbidden tag (the vanilla leaf tag is
     * always forbidden, so a tree top is never a spawn spot), and finally a free bounding box of his own size, so
     * nobody is squeezed into a wall, a roof or a window.
     */
    public static boolean isValidSurfacePosition(ServerLevel level, BlockPos pos, EntityType<?> type, SurfaceRules rules)
    {
        return rejectReason(level, pos, type, rules) == null;
    }

    /**
     * The same question, asked by a <b>natural</b> spawner, with the two world level rules in front of it.
     *
     * <p>Two conditions decide before the position is even looked at, and both are deliberately rules of this mod
     * instead of something the game does for us:
     *
     * <ul>
     * <li>{@link Reason#PEACEFUL}: a peaceful world spawns no hostile capable fighter out of nothing. The humans of
     * this mod are {@code PathfinderMob}s and not vanilla monsters, so the vanilla peaceful rule does not reach them -
     * this one does, and it is the exact counterpart of a zombie not spawning in peaceful.</li>
     * <li>{@link Reason#TERRITORY}: a position inside the protection of a territory core is refused, exactly like the
     * vanilla spawner refuses it through {@code MobSpawnEvent.PositionCheck}. A spawner of this mod never fires that
     * event, so it asks {@link TerritoryCoreSpawnRules#isNaturalSpawnBlocked} itself.</li>
     * </ul>
     *
     * <p>Only natural spawns go through here. A village garrison ({@code STRUCTURE}), a spawn egg or a command keeps
     * calling {@link #isValidSurfacePosition}: those are not natural spawns and a territory core never touches them.
     */
    public static Reason rejectNaturalSpawnReason(ServerLevel level, BlockPos pos, EntityType<?> type,
            SurfaceRules rules)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            return Reason.PEACEFUL;
        }

        if (TerritoryCoreSpawnRules.isNaturalSpawnBlocked(level, pos))
        {
            return Reason.TERRITORY;
        }

        return rejectReason(level, pos, type, rules);
    }

    /**
     * The same checks as {@link #isValidSurfacePosition}, but the one that refused the position is named.
     *
     * @return null when a human of that type may stand at that position, otherwise the first condition that said no.
     */
    public static Reason rejectReason(ServerLevel level, BlockPos pos, EntityType<?> type, SurfaceRules rules)
    {
        if (!level.isLoaded(pos))
        {
            return Reason.CHUNK_NOT_LOADED;
        }

        if (!level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.above()).isEmpty())
        {
            return Reason.FLUID;
        }

        if (rules.mustSeeSky() && !level.canSeeSky(pos))
        {
            return Reason.NO_SKY;
        }

        BlockPos ground = pos.below();
        BlockState groundState = level.getBlockState(ground);

        if (groundState.isAir())
        {
            return Reason.NO_GROUND;
        }

        for (TagKey<Block> tag : rules.forbiddenGroundTags())
        {
            if (groundState.is(tag))
            {
                return Reason.FORBIDDEN_GROUND;
            }
        }

        if (rules.fullBlockGround() && !groundState.isCollisionShapeFullBlock(level, ground))
        {
            return Reason.GROUND_NOT_FULL;
        }

        EntityDimensions dimensions = type.getDimensions();
        AABB space = dimensions.makeBoundingBox(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        return level.noCollision(null, space) ? null : Reason.COLLISION;
    }
}
