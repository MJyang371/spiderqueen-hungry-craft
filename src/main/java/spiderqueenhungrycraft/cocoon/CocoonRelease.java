package spiderqueenhungrycraft.cocoon;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The other end of {@link CocoonCapture}: the prey steps out of the silk again (feature COCOON-RELEASE).
 *
 * <p>The creature is not spawned from its {@link EntityType} and then dressed up: it is rebuilt from the very
 * data {@link CocoonData} read off it at capture time. {@link EntityType#loadEntityRecursive} therefore brings
 * back the whole individual - the name, the health it had when it was wrapped up, its wounds, its gear, its
 * potion effects, its trades, its owner, its memories, its tags - and this class only decides where it stands
 * afterwards. What the loaded data deliberately does not contain is listed in
 * {@link CocoonData#WORLD_TRANSIENT_KEYS}.
 *
 * <p>Everything here is server side and never runs before the caller has decided to destroy the cocoon. A
 * creature that can no longer be built - the mod that registered it is gone, or the entity type is disabled -
 * leaves this class as null, and the caller then keeps the cocoon instead of trading it for nothing.
 */
public final class CocoonRelease
{
    /** The key {@code loadEntityRecursive} uses to find out which species it has to build. */
    private static final String TAG_ID = "id";

    /** How many blocks above the cocoon a spot is searched for when the silk is walled in. */
    private static final int LIFT_ATTEMPTS = 3;

    private CocoonRelease()
    {
    }

    /**
     * Rebuilds the prey of that cocoon and puts it into the world, standing where the silk was.
     *
     * @return the released creature, or null when it could not be rebuilt or nowhere around the cocoon was
     *         room for it. Nothing has been changed in the world in that case.
     */
    @Nullable
    public static LivingEntity release(ServerLevel level, CocoonData data, double x, double y, double z, float yaw)
    {
        LivingEntity prey = rebuild(level, data);

        if (prey == null || !place(level, prey, x, y, z, yaw))
        {
            return null;
        }

        if (!level.addFreshEntity(prey))
        {
            SpiderQueenHungryCraft.LOGGER.warn("[Cocoon] {} refused a released {} at {}, the cocoon is kept",
                    level.dimension().location(), data.getEntityTypeId(), prey.position());
            return null;
        }

        return prey;
    }

    /**
     * @return the prey as the data of that cocoon describes it, already holding its own health, gear, effects
     *         and everything else, or null when the data can no longer be turned into a creature
     */
    @Nullable
    private static LivingEntity rebuild(ServerLevel level, CocoonData data)
    {
        CompoundTag tag = data.copyEntityNbt();
        tag.putString(TAG_ID, data.getEntityTypeId().toString());

        // The loader walks passengers as well, but the capture never stored any, so this really is one entity.
        Entity entity = EntityType.loadEntityRecursive(tag, level, loaded -> loaded);

        return entity instanceof LivingEntity prey ? prey : null;
    }

    /**
     * Puts the prey down at the cocoon, or a little above it when the silk stands in a spot the creature does
     * not fit into. A released creature never starts inside a wall and never keeps the velocity it was
     * carrying when it was caught.
     *
     * @return true when a free spot was found and the prey stands in it
     */
    private static boolean place(ServerLevel level, LivingEntity prey, double x, double y, double z, float yaw)
    {
        for (int lift = 0; lift <= LIFT_ATTEMPTS; lift++)
        {
            prey.moveTo(x, y + lift, z, yaw, 0.0F);
            prey.setDeltaMovement(Vec3.ZERO);

            if (level.noCollision(prey))
            {
                return true;
            }
        }

        return false;
    }
}
