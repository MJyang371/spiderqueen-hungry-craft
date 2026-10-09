package spiderqueenhungrycraft.beast;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;

/**
 * The physical body of the Queen Beast form (Phase 8A).
 *
 * <p>It is an ordinary axis aligned box that follows world down gravity, exactly like every other entity. No
 * rotation and no surface crawling is involved, the numbers only come from {@link QueenBeastConstants} so the
 * size hook, the clearance and the controller can never disagree about the body.
 */
public final class QueenBeastDimensions
{
    private static final EntityDimensions BEAST_DIMENSIONS =
            EntityDimensions.scalable(QueenBeastConstants.BEAST_WIDTH, QueenBeastConstants.BEAST_HEIGHT);

    private QueenBeastDimensions()
    {
    }

    /** The full beast body, whatever the player currently uses. */
    public static EntityDimensions dimensions()
    {
        return BEAST_DIMENSIONS;
    }

    /**
     * @return the box the beast body would occupy where that player stands right now. Used for the clearance
     *         and for the room check, so both always talk about the same volume.
     */
    public static AABB beastBox(Entity entity)
    {
        return BEAST_DIMENSIONS.makeBoundingBox(entity.position());
    }
}
