package spiderqueenhungrycraft.brood;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The husk spider (Stray and Husk Spider round): the zombie spider of the desert.
 *
 * <p>Hatched from a cocoon that digested a {@code minecraft:husk}. It is its zombie cousin in every way that matters -
 * forty health, four points of damage, the same walk, the same carrier, the same rage on the ability key, which is
 * Strength II and Speed II for twenty seconds with a minute of rest - with exactly one difference: whatever its fangs
 * catch is left with venom in the wound.
 *
 * <p>It is deliberately <b>not</b> an undead: no sun damage, no husk AI, no husk loot, and the venom is the plain
 * poison of the game at its own level and duration, not the queen's own venom strike.
 */
public class HuskSpiderEntity extends ZombieSpiderEntity
{
    /** The venom of its bite: five seconds of poison. */
    public static final int POISON_TICKS = 100;

    /** Level one, so a spider with many bites poisons harder simply by biting more often. */
    public static final int POISON_AMPLIFIER = 0;

    /** The 1.4 x 0.9 hitbox of a vanilla spider, exactly like its zombie cousin. */
    public static final float COLLISION_WIDTH = ZombieSpiderEntity.COLLISION_WIDTH;
    public static final float COLLISION_HEIGHT = ZombieSpiderEntity.COLLISION_HEIGHT;

    public HuskSpiderEntity(EntityType<? extends HuskSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The very same attributes as the zombie spider: the numbers of a species are not what makes it a species. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return ZombieSpiderEntity.createAttributes();
    }

    /** Every bite that lands leaves poison behind. */
    @Override
    public boolean doHurtTarget(Entity target)
    {
        boolean hit = super.doHurtTarget(target);

        if (hit && target instanceof LivingEntity living)
        {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, POISON_AMPLIFIER), this);
        }

        return hit;
    }
}
