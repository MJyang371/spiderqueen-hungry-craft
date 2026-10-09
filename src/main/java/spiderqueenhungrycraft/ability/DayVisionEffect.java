package spiderqueenhungrycraft.ability;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * The daylight vision of the Spider Queen (Phase 8E4): while she has it, the bright light of the world no
 * longer blinds her.
 *
 * <p>It is a pure marker effect. It never ticks, it heals nothing, it hurts nothing, it removes no effect and
 * it changes no attribute: the whole rule lives in {@link QueenPhysiology}, which simply stops handing out
 * its own strong light blindness while this effect is present.
 *
 * <p>It only ever touches the queen's own light physiology. A blindness from a potion, from a command, from
 * the darkness of a warden or from any other mod is completely untouched, and everybody who is not the
 * Spider Queen may drink the potion as well without gaining anything from it.
 */
public class DayVisionEffect extends MobEffect
{
    /** A cold pearl white, the colour the potion of day vision is tinted with. */
    public static final int COLOR = 0xF4F8FF;

    public DayVisionEffect()
    {
        super(MobEffectCategory.BENEFICIAL, COLOR);
    }
}
