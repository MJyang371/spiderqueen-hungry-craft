package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.ChronoShiftAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;

/**
 * The chrono spider (Special Spiders round I): the heavy spider of the brood, and the only one that jumps through space.
 *
 * <p>It is a {@link SpiderMinionEntity} like every other spider of the brood - same ownership, same orders, same
 * commands, same faction, same carrier, same ordinary ground pathfinding of a spider, and the ordinary size of a spider
 * (never a giant).
 *
 * <p><b>It never teleports on its own.</b> There is no teleport in this class and no teleport goal is added to it:
 * <ul>
 * <li>no random teleport of an enderman,</li>
 * <li>no teleport because a victim is far away,</li>
 * <li>no teleport because the pathfinder failed,</li>
 * <li>no teleport away from a hit.</li>
 * </ul>
 * It walks to its victim like any other spider and bites it. The only jump that exists is the one the queen orders
 * with the ability key of her sceptre ({@link ChronoShiftAbility}), at the place she herself points at.
 *
 * <p>The single teleport that belongs to every follower of the brood - the safe relocation next to its queen when it has
 * fallen more than thirty-two blocks behind and has nothing to fight - is not a chrono spider ability: it comes from the
 * shared follow goal of all spiders of this mod and happens to every one of them, exactly as before. It is never a
 * combat, escape or pathfinding teleport.
 */
public class ChronoSpiderEntity extends SpiderMinionEntity
{
    /** Forty-five health: the toughest spider of the brood so far. */
    public static final double MAX_HEALTH = 45.0D;

    /** Seven damage with its bite. */
    public static final double ATTACK_DAMAGE = 7.0D;

    /** The 1.4 x 0.9 hitbox of a vanilla spider: it is a spider first. */
    public static final float COLLISION_WIDTH = 1.4F;
    public static final float COLLISION_HEIGHT = 0.9F;

    public ChronoSpiderEntity(EntityType<? extends ChronoSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The same speed, armor and knockback resistance as every other spider of the brood, with its own health. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE);
    }

    /** The chrono shift of this spider: the only way it ever teleports, and only when the queen orders it. */
    @Override
    public SpiderAbility getAbility()
    {
        return ChronoShiftAbility.INSTANCE;
    }
}
