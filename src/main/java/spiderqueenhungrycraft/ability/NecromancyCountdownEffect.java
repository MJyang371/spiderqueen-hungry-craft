package spiderqueenhungrycraft.ability;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 距下班还剩 (Necromancy IV round): the mark a woken bone dragon wears until its contract runs out.
 *
 * <p><b>It is a mark and not a boon.</b> It adds no attribute modifier of any kind - no health, no damage, no armour
 * and no speed - because the contract is not supposed to make a dragon stronger, only to say out loud that this
 * skeleton has taken a job. The work is done by the contract itself, which the server keeps in the persistent data of
 * the dragon; this effect is the visible half of it.
 *
 * <p>It is beneficial in the sense of the game (a blue frame and a place at the front of the row) so that it reads as
 * "this is my creature and it is on a timer" rather than as a debuff.
 *
 * <p><b>Milk does not end a contract.</b> The effect can be taken away by anything that removes effects, and a player
 * who does so loses nothing and gains nothing: the server re-applies it on the next tick as long as the contract is
 * alive, and the contract itself ends when its own deadline passes and at no other moment.
 */
public class NecromancyCountdownEffect extends MobEffect
{
    /** The colour of the icon: the violet of old bone, next to nothing else in this mod. */
    private static final int COLOUR = 0x6A3FA0;

    public NecromancyCountdownEffect()
    {
        super(MobEffectCategory.BENEFICIAL, COLOUR);
    }

    /**
     * @return false, always: this effect does nothing on a clock of its own.
     *
     *         <p>There is no periodic work to do - the countdown is the duration the game draws by itself, and every
     *         rule of the contract lives on the server side of the compatibility.
     */
    @Override
    public boolean isDurationEffectTick(int duration, int amplifier)
    {
        return false;
    }
}
