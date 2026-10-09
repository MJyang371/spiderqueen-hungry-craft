package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The imp spider (this round's breeding pass): the small spider that a spider egg on the ground hatches
 * into.
 *
 * <p>Half a spider in every direction - half the hitbox and half the model, which the renderer draws with
 * a scale of one half - with ten health, three damage, no armor and no knockback resistance. It walks at
 * exactly the speed of a vanilla spider and it climbs like one.
 */
public class ImpSpiderEntity extends SpiderMinionEntity
{
    /** Ten health: half of the twenty of an ordinary spider of the brood. */
    public static final double MAX_HEALTH = 10.0D;

    /** Three damage. */
    public static final double ATTACK_DAMAGE = 3.0D;

    /** Half of the 1.4 x 0.9 hitbox of a vanilla spider. */
    public static final float COLLISION_WIDTH = 0.7F;
    public static final float COLLISION_HEIGHT = 0.45F;

    public ImpSpiderEntity(EntityType<? extends ImpSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE);
    }
}
