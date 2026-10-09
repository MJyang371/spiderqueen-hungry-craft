package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastHurtSound;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The incoming hurt sound of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>{@code LivingEntity#playHurtSound} is the single place where a hurt sound of a living entity is chosen,
 * so this is the narrowest possible hook for the one thing that has to change: an attack on the beast is
 * answered with the hit sound of a netherite block instead of the outcry of a player. The vanilla sound is
 * cancelled in the same breath, never added to, so only one of the two is ever heard, and the volume and the
 * pitch come from the beast constants instead of the randomized voice pitch of a human.
 *
 * <p>The hook is guarded twice: only a transformed player (never a mob, never an ordinary player) and only a
 * real combat hit (an attacker, a projectile or an explosion). Drowning, starving, freezing and every other
 * environmental hurt run through here untouched and keep the ordinary player sound. The method itself only
 * runs on the logical server, where the hurt of an entity is decided, so no client needs this class.
 */
@Mixin(LivingEntity.class)
public abstract class BeastHurtSoundMixin
{
    @Inject(method = "Lnet/minecraft/world/entity/LivingEntity;playHurtSound(Lnet/minecraft/world/damagesource/DamageSource;)V",
            at = @At("HEAD"), cancellable = true)
    private void hungrycraft$beastHurtSound(DamageSource source, CallbackInfo callbackInfo)
    {
        if (!(((Object) this) instanceof Player player) || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        if (!QueenBeastHurtSound.isCombatHit(source))
        {
            return;
        }

        player.playSound(QueenBeastHurtSound.HIT_SOUND, QueenBeastConstants.BEAST_HURT_SOUND_VOLUME,
                QueenBeastConstants.BEAST_HURT_SOUND_PITCH);
        callbackInfo.cancel();
    }
}
