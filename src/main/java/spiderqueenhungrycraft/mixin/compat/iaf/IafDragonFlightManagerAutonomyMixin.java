package spiderqueenhungrycraft.mixin.compat.iaf;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.entity.IafDragonFlightManager;

import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;

/**
 * The flight-destination gate of a dragon under contract (Necromancy IV round 7, the isolation).
 *
 * <p>{@link IafDragonFlightManager#update} is the one place where Ice and Fire recomputes, every tick, the point a
 * flying dragon flies towards - the arc of a chase, the random circle of a hover-blast, or the block it looks at while
 * it wanders. For a contract dragon that recomputation is exactly the behaviour this round removes, so the manager is
 * cancelled before it can run and the destination stays whatever the contract wrote. The dragon keeps its wings, its
 * speed and its animations: only the <b>decision</b> of where to fly is taken away.
 *
 * <p>{@code remap = false} and {@code require = 0}, for the same reason as the other gates, and the proof of a real
 * interception is the {@link NecromancyDiagnostics#announce} line it writes the first time it fires.
 */
@Mixin(IafDragonFlightManager.class)
public abstract class IafDragonFlightManagerAutonomyMixin
{
    @Shadow(remap = false)
    @Final
    private EntityDragonBase dragon;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hungrycraft$noAutonomousFlightDestination(CallbackInfo ci)
    {
        if (NecromancyService.isContractedDragon(dragon))
        {
            NecromancyDiagnostics.announce("autonomy-gate:flightManager.update",
                    "autonomy gate intercepted a contract dragon: IafDragonFlightManager.update");
            ci.cancel();
        }
    }
}
