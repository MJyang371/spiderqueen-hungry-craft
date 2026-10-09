package spiderqueenhungrycraft.ability;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Light sensitivity (feature LIGHT-SENSITIVITY): the price the Spider Queen pays for standing in the light of
 * the world.
 *
 * <p>It is a pure marker effect. It never ticks, it heals nothing, it hurts nothing, it removes no effect and
 * it changes no attribute: the whole rule - the countdown, the weakness, the slowness, the nausea and the
 * blindness it leads to, and every way out of it - lives in {@link QueenLightSensitivity}, which hands this
 * effect out and takes it away again.
 *
 * <p>The effect is a normal vanilla style effect with an icon, so the player can see that the light is doing
 * something to her, but it is always granted without particles: the light does not sparkle, and neither do
 * the debuffs it causes.
 *
 * <p>A player who is not the Spider Queen never gains anything from it, and a case of light sensitivity
 * somebody else invented can never be mistaken for this one: only the instances {@link QueenLightSensitivity}
 * handed out itself are ever removed by that class.
 */
public class LightSensitivityEffect extends MobEffect
{
    /** A hot gold, the colour a light sensitive eye sees the world in. */
    public static final int COLOR = 0xFFC85A;

    public LightSensitivityEffect()
    {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
