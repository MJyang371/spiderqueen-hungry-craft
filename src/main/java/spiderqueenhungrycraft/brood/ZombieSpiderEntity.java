package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.BerserkAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;

/**
 * The zombie spider (Brood Ability framework): the first special spider of the brood.
 *
 * <p>A spider that was hatched from a cocoon which had digested a {@code minecraft:zombie}, and nothing else. It is a
 * {@link SpiderMinionEntity} like every other spider of the brood - same ownership, same follow, hold and patrol, same
 * commands of the queen sceptre, same carrier - with more life and one ability of its own.
 *
 * <p>It is deliberately <b>not</b> an undead: it does not burn in the sun, it does not sound like a zombie, it has no
 * zombie AI, no reinforcements, no infection and no zombie loot. Only its body and its eyes look the part, and only its
 * {@link BerserkAbility} borrows the rage of the undead it came from.
 */
public class ZombieSpiderEntity extends SpiderMinionEntity
{
    /** Forty health: twice the life of an ordinary spider of the brood. */
    public static final double MAX_HEALTH = 40.0D;

    /** Four damage, like the ordinary brood spider. */
    public static final double ATTACK_DAMAGE = 4.0D;

    /** The 1.4 x 0.9 hitbox of a vanilla spider: it is a spider first. */
    public static final float COLLISION_WIDTH = 1.4F;
    public static final float COLLISION_HEIGHT = 0.9F;

    public ZombieSpiderEntity(EntityType<? extends ZombieSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The same speed, armor and knockback resistance as every other spider of the brood, with its own health. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE);
    }

    /** The rage of this spider: the first ability of the brood, cast with the ability key of the queen. */
    @Override
    public SpiderAbility getAbility()
    {
        return BerserkAbility.INSTANCE;
    }
}
