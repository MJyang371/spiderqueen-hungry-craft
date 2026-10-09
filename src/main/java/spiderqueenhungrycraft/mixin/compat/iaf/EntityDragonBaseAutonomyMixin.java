package spiderqueenhungrycraft.mixin.compat.iaf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;

import net.minecraft.world.entity.Entity;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;

/**
 * The four autonomy gates of a dragon under contract (Necromancy IV round 7, the isolation).
 *
 * <p>Together with the goal removal of {@code IceAndFireBridge#isolateNativeGoals} they make the contract the
 * <b>only</b> writer of a skeleton's target, its flight and its attack strategy, while the dragon keeps every animation,
 * the breath and the riding of Ice and Fire. Each gate is the smallest possible cut and nothing else:
 *
 * <ul>
 * <li>{@code updateCheckPlayer} - the wake-and-attack-a-player decision, so a skeleton never locks onto its owner or a
 * nearby Steve on its own,</li>
 * <li>{@code randomizeAttacks} - the random switch between bite / wing / breath, so the controller decides the attack
 * and the execution fields stay exactly where the last command put them,</li>
 * <li>{@code isAllowedToTriggerFlight} - the spontaneous take-off, so a skeleton never leaves the ground unless a
 * command asks it to,</li>
 * <li>{@code isTimeToWake} - the sleep cycle, so a skeleton on a payroll never naps and never lies down on its own.</li>
 * </ul>
 *
 * <p><b>Every gate is {@code remap = false} and {@code require = 0}.</b> The four methods are Ice and Fire's own, so
 * their names are identical in a development workspace and in a published jar ({@code remap = false}); {@code require = 0}
 * is the safety net for a different mapping environment, and it must never be read as "the gate worked" - the proof
 * that a gate really intercepted a dragon is the one line it writes through {@link NecromancyDiagnostics#announce} the
 * first time it fires. A missing line is itself the diagnosis.
 *
 * <p>A gate never runs for an ordinary dragon: {@link NecromancyService#isContractedDragon} is a persistent-data lookup
 * that is false for every wild and tamed dragon, so they keep the behaviour Ice and Fire gave them.
 */
@Mixin(EntityDragonBase.class)
public abstract class EntityDragonBaseAutonomyMixin
{
    @Inject(method = "updateCheckPlayer", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hungrycraft$noAutonomousPlayerTarget(CallbackInfo ci)
    {
        if (contracted("updateCheckPlayer"))
        {
            ci.cancel();
        }
    }

    @Inject(method = "randomizeAttacks", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hungrycraft$noAutonomousAttackSwitch(CallbackInfo ci)
    {
        if (contracted("randomizeAttacks"))
        {
            ci.cancel();
        }
    }

    @Inject(method = "isAllowedToTriggerFlight", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hungrycraft$noAutonomousTakeoff(CallbackInfoReturnable<Boolean> cir)
    {
        if (contracted("isAllowedToTriggerFlight"))
        {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "isTimeToWake", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void hungrycraft$neverSleepsOnContract(CallbackInfoReturnable<Boolean> cir)
    {
        if (contracted("isTimeToWake"))
        {
            cir.setReturnValue(true);
        }
    }

    /** @return true when this dragon is under a contract, and says so once per gate per launch when it is. */
    private boolean contracted(String gate)
    {
        boolean isContracted = NecromancyService.isContractedDragon((Entity) (Object) this);

        if (isContracted)
        {
            NecromancyDiagnostics.announce("autonomy-gate:" + gate,
                    "autonomy gate intercepted a contract dragon: " + gate + " (EntityDragonBase)");
        }

        return isContracted;
    }
}
