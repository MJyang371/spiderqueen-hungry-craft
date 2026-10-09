package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The one and only camera change of the Queen Beast form (Phase 8A): the third person camera of a beast backs
 * up further, because a giant spider does not fit into the ordinary distance.
 *
 * <p>Only the base distance of the vanilla third person camera is scaled. The vanilla collision clip runs
 * afterwards exactly as before, so the camera still backs off in the open and still moves close in a cave, and
 * nothing else about the camera is touched: no roll, no yaw, no pitch, no gravity. A client only mixin,
 * declared in the client section of the mixin configuration.
 */
@Mixin(Camera.class)
public abstract class BeastCameraMixin
{
    @ModifyArg(method = "Lnet/minecraft/client/Camera;setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(D)D"))
    private double hungrycraft$beastThirdPersonDistance(double distance)
    {
        Player player = Minecraft.getInstance().player;

        if (player == null || !QueenBeastState.isTransformed(player))
        {
            return distance;
        }

        return distance * QueenBeastConstants.BEAST_CAMERA_DISTANCE_MULTIPLIER;
    }
}
