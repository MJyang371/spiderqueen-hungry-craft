package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.beast.QueenBeastPhysics;

/**
 * The body collision part of the absolute super armor (Phase 8B-POLISH-2).
 *
 * <p>Two living entities that touch shove each other through {@code LivingEntity#pushEntities} and
 * {@code Entity#push}, and both of them ask {@code isPushable()} of the entity they want to move. Answering
 * that question with false for a transformed beast is the whole change, and it is deliberately asymmetric:
 * the beast itself still shoves whatever it walks into, because that check is made on the other entity, while
 * nothing can shove the beast back. A zombie that walks into a resting queen therefore behaves like a zombie
 * walking into a wall, and the queen can still walk through a crowd instead of being locked in place by it.
 *
 * <p>{@code isPushable()} is also what {@code Entity#push} uses to decide whether the contact counts as a hurt
 * mark, so a beast no longer flinches from being touched either.
 */
@Mixin(LivingEntity.class)
public abstract class BeastPushImmunityMixin
{
    @Inject(method = "Lnet/minecraft/world/entity/LivingEntity;isPushable()Z", at = @At("HEAD"),
            cancellable = true)
    private void hungrycraft$beastCannotBePushed(CallbackInfoReturnable<Boolean> callbackInfo)
    {
        if (QueenBeastPhysics.blocksExternalImpulse((LivingEntity) (Object) this))
        {
            callbackInfo.setReturnValue(false);
        }
    }
}
