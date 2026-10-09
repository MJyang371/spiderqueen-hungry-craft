package spiderqueenhungrycraft.web;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Webbed, the spider queen's own slow effect. It is a separate effect instead of vanilla slowness so
 * both can exist side by side and keep their own lifetime. The movement speed penalty scales with the
 * amplifier: I is -15%, II is -30%, III is -45% and IV is -60%.
 */
public class WebbedEffect extends MobEffect
{
    private static final String MOVEMENT_SPEED_MODIFIER = "5d1c9a4b-2f77-4c6e-9d3a-7b0f1e2c8a45";
    private static final double SPEED_PER_LEVEL = -0.15D;

    public WebbedEffect()
    {
        super(MobEffectCategory.HARMFUL, 0xE6E6E6);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_MODIFIER, SPEED_PER_LEVEL,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
