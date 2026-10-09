package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * No ordinary player jump for a beast (Phase 8C).
 *
 * <p>The space bar of a transformed player belongs to the charged jump, which the server launches itself. The
 * vanilla jump would otherwise fire on the very same press and stack its fixed hop on top of the charged one,
 * so it is cancelled at its single source: {@code Player#jumpFromGround}, the one method both the client
 * prediction and the server call when a player leaves the ground.
 *
 * <p>Nothing else about movement is touched: falling, walking, swimming, climbing and the jump of every
 * non transformed player keep working exactly as vanilla does it.
 */
@Mixin(Player.class)
public abstract class BeastJumpSuppressionMixin
{
    @Inject(method = "Lnet/minecraft/world/entity/player/Player;jumpFromGround()V", at = @At("HEAD"),
            cancellable = true)
    private void hungrycraft$beastNeverVanillaJumps(CallbackInfo callbackInfo)
    {
        if (QueenBeastState.isTransformed((Player) (Object) this))
        {
            callbackInfo.cancel();
        }
    }
}
