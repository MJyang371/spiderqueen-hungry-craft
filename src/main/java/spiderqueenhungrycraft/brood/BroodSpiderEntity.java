package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The ordinary brood spider (this round's breeding pass): the spider that hatches out of a cocoon which
 * carried a spider egg.
 *
 * <p>It is a spider of the mod and never {@code minecraft:spider}: the same size as one - the hitbox of the
 * vanilla spider and the full model - with twenty health, four damage, no armor and no knockback
 * resistance, at the walking speed of a vanilla spider.
 */
public class BroodSpiderEntity extends SpiderMinionEntity
{
    /** Twenty health: the size of a vanilla spider, with a little more life in it. */
    public static final double MAX_HEALTH = 20.0D;

    /** Four damage. */
    public static final double ATTACK_DAMAGE = 4.0D;

    /** The 1.4 x 0.9 hitbox of a vanilla spider. */
    public static final float COLLISION_WIDTH = 1.4F;
    public static final float COLLISION_HEIGHT = 0.9F;

    public BroodSpiderEntity(EntityType<? extends BroodSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE);
    }
}
