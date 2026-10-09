package spiderqueenhungrycraft.web;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A permanent elemental web block (Phase 7A), used for the poison and the fire web of the queen.
 *
 * <p>Unlike the ordinary queen web of a web shot it never expires on its own: it only disappears when a
 * player breaks it or when the world removes it. Everything that differs per element is read from the
 * {@link WebElement} it was created with, so one class serves every element.
 *
 * <p>Web natives, that is the Spider Queen and everything in the spider_like tag, pass through untouched:
 * no slowdown, no poison, no fire.
 */
public class ElementalQueenWebBlock extends WebBlock
{
    /** Chance per client tick of a single ambient decoration particle, deliberately low. */
    private static final float AMBIENT_PARTICLE_CHANCE = 0.12F;

    private static final double AMBIENT_PARTICLE_SPREAD = 0.4D;

    private final WebElement element;

    public ElementalQueenWebBlock(BlockBehaviour.Properties properties, WebElement element)
    {
        super(properties);
        this.element = element;
    }

    /** The element of this web, poison or fire. */
    public WebElement element()
    {
        return this.element;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        // Web natives are safe in an elemental web, not even the vanilla web drag is applied to them.
        if (WebAffinity.isWebNative(entity))
        {
            return;
        }

        // Everybody else gets the ordinary cobweb handling of the vanilla web block.
        super.entityInside(state, level, pos, entity);

        if (level.isClientSide || !(entity instanceof LivingEntity living))
        {
            return;
        }

        if (this.element == WebElement.POISON)
        {
            WebElementEffects.applyWebPoison(living);
        }
        else if (this.element == WebElement.FIRE)
        {
            WebElementEffects.applyWebFire(living);
        }
    }

    /** A few ambient particles of the element, client side only and rare enough to stay readable. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        super.animateTick(state, level, pos, random);

        if (random.nextFloat() >= AMBIENT_PARTICLE_CHANCE)
        {
            return;
        }

        level.addParticle(this.element.particle(),
                pos.getX() + random.nextDouble(),
                pos.getY() + random.nextDouble(),
                pos.getZ() + random.nextDouble(),
                (random.nextDouble() - 0.5D) * AMBIENT_PARTICLE_SPREAD,
                0.0D,
                (random.nextDouble() - 0.5D) * AMBIENT_PARTICLE_SPREAD);
    }
}
