package spiderqueenhungrycraft.bed;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The spider bed ritual (Phase 8E3A-GATE1).
 *
 * <p>The single authority on the one and only structure that turns webs into beds: a flat 5x5 of one
 * layer, a ring of sixteen frame blocks of {@code #spiderqueen_hungrycraft:spider_bed_frame} and a 3x3 of
 * nine queen webs inside it:
 *
 * <pre>
 * L L L L L
 * L W W W L
 * L W W W L
 * L W W W L
 * L L L L L
 * </pre>
 *
 * <p>The last of the nine webs is what triggers the ritual, no matter who put it there: the search always
 * starts at the position that just changed, so a hand placed web and a web shot run through exactly the
 * same code. The whole 5x5 is validated before the first block changes, then the nine webs become nine
 * independent spider beds in one go.
 *
 * <p>A ritual is not a multiblock. Nothing keeps the frame, nothing keeps the nine beds together, and a
 * single new web next to eight beds changes nothing at all: the middle has to be nine out of nine queen
 * webs for the ritual to fire, and a spider bed is not a queen web.
 */
public final class SpiderBedRitual
{
    /** The wooden frame of a ritual, a log or a wood of any kind, mixed freely. */
    public static final TagKey<Block> FRAME = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "spider_bed_frame"));

    /** Distance from the center web to the frame, which makes the structure 5x5. */
    private static final int RADIUS = 2;

    /** Distance from the center web to the edge of the inner 3x3. */
    private static final int WEB_RADIUS = 1;

    /**
     * The axes a ritual can lie in, in the fixed order the search uses. A fixed order keeps an overlapping
     * structure predictable: one trigger always forms the same bed and never two of them.
     */
    private static final Direction.Axis[] AXES = { Direction.Axis.Y, Direction.Axis.X, Direction.Axis.Z };

    /** The unit steps a plane is built from, the same three vectors every axis uses. */
    private static final Vec3i EAST = new Vec3i(1, 0, 0);
    private static final Vec3i SOUTH = new Vec3i(0, 0, 1);
    private static final Vec3i UP = new Vec3i(0, 1, 0);

    private SpiderBedRitual()
    {
    }

    /**
     * Tries to close a spider bed ritual around a queen web that just entered the world. Server side only:
     * a client never decides on its own that a structure is complete.
     *
     * <p>The changed position is not assumed to be the center of anything. It is only known to be one of
     * the nine inner webs, so each axis offers nine possible centers, which is at most 27 candidates of 25
     * block states each, and every one of them is read from the world around that single position.
     *
     * @param level      the server level the web was created in
     * @param changedPos the position of the queen web that just changed the world
     * @return true when nine queen webs were turned into nine spider beds
     */
    public static boolean tryFormAround(ServerLevel level, BlockPos changedPos)
    {
        if (level == null || changedPos == null)
        {
            return false;
        }

        // Only a real queen web can ever start a ritual. A spider bed, a vanilla cobweb, the web of another
        // mod or air never reaches the search at all.
        if (!isQueenWeb(level.getBlockState(changedPos)))
        {
            return false;
        }

        for (Direction.Axis axis : AXES)
        {
            Vec3i u = along(axis);
            Vec3i v = across(axis);

            for (int du = -WEB_RADIUS; du <= WEB_RADIUS; du++)
            {
                for (int dv = -WEB_RADIUS; dv <= WEB_RADIUS; dv++)
                {
                    // The changed web sits at (du, dv) of the inner 3x3, so the center of that structure is
                    // the same distance in the opposite direction. Nine candidates, not a chunk scan.
                    BlockPos center = changedPos.subtract(u.multiply(du)).subtract(v.multiply(dv));

                    if (matches(level, center, u, v))
                    {
                        form(level, center, u, v, axis);
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Reads the whole 5x5 of one plane and decides whether it is a complete ritual.
     *
     * <p>The ring of sixteen has to be a legal frame block, the 3x3 inside has to be nine queen webs and
     * nothing else. Everything else in the middle, whether it is a spider bed, a vanilla cobweb, a web of
     * another mod or air, makes the whole structure invalid.
     *
     * @return true when that plane holds sixteen frame blocks around nine queen webs
     */
    static boolean matches(ServerLevel level, BlockPos center, Vec3i u, Vec3i v)
    {
        for (int du = -RADIUS; du <= RADIUS; du++)
        {
            for (int dv = -RADIUS; dv <= RADIUS; dv++)
            {
                BlockState state = level.getBlockState(offset(center, u, du, v, dv));

                if (Math.abs(du) == RADIUS || Math.abs(dv) == RADIUS)
                {
                    // The outer ring is wood, without a specific species: any log or wood that joined the
                    // frame tag counts, and mixing several of them inside one ring is fine.
                    if (!state.is(FRAME))
                    {
                        return false;
                    }
                }
                else if (!isQueenWeb(state))
                {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Converts the middle of an already validated ritual. The whole 5x5 was checked before this runs, so
     * the conversion is atomic: either all nine webs become beds or nothing is touched at all.
     */
    private static void form(ServerLevel level, BlockPos center, Vec3i u, Vec3i v, Direction.Axis axis)
    {
        BlockState bed = HungryCraftBlocks.SPIDER_BED.get().defaultBlockState()
                .setValue(SpiderBedBlock.AXIS, axis);

        for (int du = -WEB_RADIUS; du <= WEB_RADIUS; du++)
        {
            for (int dv = -WEB_RADIUS; dv <= WEB_RADIUS; dv++)
            {
                // Plain block replacement: the frame keeps standing, no item is created and nothing is
                // consumed. Every bed is an ordinary, equal block from this moment on, there is no center
                // block and no master of any kind.
                level.setBlock(offset(center, u, du, v, dv), bed, Block.UPDATE_ALL);
            }
        }
    }

    /** The position inside one plane, du steps along u and dv steps along v away from the center. */
    private static BlockPos offset(BlockPos center, Vec3i u, int du, Vec3i v, int dv)
    {
        return center.offset(u.multiply(du)).offset(v.multiply(dv));
    }

    /**
     * @return true when that state is a queen web. This is the exact block of the mod, never the
     *         {@code web_blocks} tag: a vanilla cobweb, an elemental web, a spider bed or the web of
     *         another mod must never be accepted as a ritual material.
     */
    private static boolean isQueenWeb(BlockState state)
    {
        return state.is(HungryCraftBlocks.QUEEN_WEB.get());
    }

    /** The first direction inside a plane, u. */
    private static Vec3i along(Direction.Axis axis)
    {
        return axis == Direction.Axis.X ? SOUTH : EAST;
    }

    /** The second direction inside a plane, v. */
    private static Vec3i across(Direction.Axis axis)
    {
        return axis == Direction.Axis.Y ? SOUTH : UP;
    }
}
