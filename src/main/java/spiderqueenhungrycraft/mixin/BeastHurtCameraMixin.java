package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;
import spiderqueenhungrycraft.client.QueenBeastHurtClient;

/**
 * The one and only hurt camera change of the Queen Beast form (Phase 8B-POLISH-2): a scratch of one heart or
 * less does not throw the head of a giant spider around.
 *
 * <p>The vanilla hurt bob lives in the private {@code GameRenderer#bobHurt}, which no Forge event covers, so
 * this is the narrowest possible client only hook: the whole method is skipped when the server already judged
 * the incoming hit small. Nothing else is touched - no camera system, no roll, no fov, no view entity, and the
 * death animation of a dying player is never suppressed, because the client mark only covers a living beast.
 *
 * <p>Only the visual is skipped. The hurt of the vanilla player is completely unaffected: the damage, the
 * invulnerability frames, the hurt overlay, the sound and the death all still happen.
 */
@Mixin(GameRenderer.class)
public abstract class BeastHurtCameraMixin
{
    @Inject(method = "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("HEAD"), cancellable = true)
    private void hungrycraft$beastIgnoresSmallHits(PoseStack poseStack, float partialTick,
            CallbackInfo callbackInfo)
    {
        if (QueenBeastHurtClient.suppressesHurtBob())
        {
            callbackInfo.cancel();
        }
    }
}
