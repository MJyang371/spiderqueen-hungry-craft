package spiderqueenhungrycraft.mixin.compat.iaf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;

import spiderqueenhungrycraft.compat.iceandfire.IceAndFireTextureOverride;

/**
 * The <b>second</b>, equally optional hook of the Ice and Fire compatibility (Necromancy IV prototype 2), and the one
 * the round asks to be measured before it is trusted.
 *
 * <p>Ice and Fire draws a glow-in-the-dark eye layer over a dragon's head ({@code LayerDragonEyes}), and it asks one
 * question before it does: {@code EntityDragonBase#shouldRenderEyes()}, which is
 * {@code !isSleeping() && !isModelDead() && !isBlinking() && !isStoneMob(this)}. A plain corpse therefore has no eyes
 * at all - and a dragon that HungryCraft has woken up has them again, because waking it up is exactly what makes
 * {@code isModelDead()} false.
 *
 * <p>What that means in practice is worth stating plainly: Ice and Fire has <b>no skeleton eye texture</b>
 * ({@code EnumDragonTextures#getEyeTextureFromDragon} only ever switches on the growth stage, never on the corpse),
 * so a woken dragon is drawn as a skull with the living, glowing eyes of its stage on it. Whether that is the best
 * look a necromancer could ask for is a matter of taste, and the round asks for it to be measured rather than assumed:
 *
 * <ul>
 * <li>this hook changes <b>nothing</b> while {@code compat.ice_and_fire_skeleton_glowing_eyes} is true, which is its
 * default - Ice and Fire's own eyes stay exactly where they are,</li>
 * <li>a pack that prefers a bare skull sets it to false, and this hook then answers the question above with "no" for a
 * dragon that HungryCraft has woken, and for nothing else,</li>
 * <li>and the texture hook of this compatibility reports both the answer of Ice and Fire and the eye texture it would
 * use into the log, once per state change, so the decision can be made from evidence.</li>
 * </ul>
 */
@Mixin(value = EntityDragonBase.class, remap = false)
public abstract class EntityDragonBaseEyesMixin
{
    @Inject(method = "shouldRenderEyes", at = @At("HEAD"), cancellable = true, remap = false)
    private void hungrycraft$eyesOfRevivedDragon(CallbackInfoReturnable<Boolean> callback)
    {
        if (IceAndFireTextureOverride.hidesEyesOfRevivedDragon((EntityDragonBase) (Object) this))
        {
            callback.setReturnValue(false);
        }
    }
}
