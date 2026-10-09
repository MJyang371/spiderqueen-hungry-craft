package spiderqueenhungrycraft.brood;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;

/**
 * The stray spider (Stray and Husk Spider round): the skeleton spider of the cold.
 *
 * <p>Hatched from a cocoon that digested a {@code minecraft:stray}. It is its skeleton cousin in every way that matters
 * - twenty health, five points of damage, twenty blocks of reach, one shot every thirty ticks, the same walk-then-stop
 * feet, the same web, the same ammunition switch on the ability key - with exactly one difference: its arrow carries the
 * chill of a stray, so whatever it hits is slowed down as well.
 *
 * <p>That difference lives in one method, {@link #prepareArrow(SpiderArrow)}, and nothing else about the shot changes:
 * the same arrow entity of the game, the same flight, the same aim, and the same flight through the allies of its queen.
 */
public class StraySpiderEntity extends SkeletonSpiderEntity
{
    /** How long the chill of one slowness arrow stays on the victim: ten seconds, like the arrow of a stray. */
    public static final int SLOWNESS_TICKS = 200;

    /** Level one of the slowdown, which is what a stray of the game fires. */
    public static final int SLOWNESS_AMPLIFIER = 0;

    /** The 1.4 x 0.9 hitbox of a vanilla spider, exactly like its skeleton cousin. */
    public static final float COLLISION_WIDTH = SkeletonSpiderEntity.COLLISION_WIDTH;
    public static final float COLLISION_HEIGHT = SkeletonSpiderEntity.COLLISION_HEIGHT;

    /** The ammunition of this species: a chilled arrow, and the web shot of the queen. */
    private static final SpiderAmmo[] MODES = { SpiderAmmo.SLOWNESS_ARROW, SpiderAmmo.WEB };

    public StraySpiderEntity(EntityType<? extends StraySpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The very same attributes as the skeleton spider: the numbers of a species are not what makes it a species. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return SkeletonSpiderEntity.createAttributes();
    }

    @Override
    protected SpiderAmmo[] ammoModes()
    {
        return MODES;
    }

    /** It is born with its chilled arrow loaded, not with the plain one. */
    @Override
    protected SpiderAmmo defaultAmmo()
    {
        return SpiderAmmo.SLOWNESS_ARROW;
    }

    /** The chill: the arrow of the game with one effect on it, exactly the way a tipped arrow is put together. */
    @Override
    protected void prepareArrow(SpiderArrow arrow)
    {
        arrow.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOWNESS_TICKS, SLOWNESS_AMPLIFIER));
    }
}
