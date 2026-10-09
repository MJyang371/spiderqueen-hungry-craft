package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import net.minecraft.world.level.Explosion;
import spiderqueenhungrycraft.beast.QueenBeastPhysics;

/**
 * The explosion part of the absolute super armor (Phase 8B-POLISH-2).
 *
 * <p>A vanilla explosion does not use the knockback event of Forge, it computes an impulse and adds it to the
 * movement of every entity in range. The one number that scales that impulse is the knockback dampener of the
 * entity, which is where this hook sits: for a transformed beast it answers zero, so the impulse becomes a
 * zero vector before it is applied.
 *
 * <p>Zeroing it at the source solves both halves of the problem at once. The beast is not moved on the server,
 * and the {@code ClientboundExplodePacket} that the explosion sends to players carries the same zero vector
 * and the client adds nothing to its own movement either, so server and client never disagree about it.
 *
 * <p>Only the impulse is touched. The explosion still hurts the beast, still breaks the blocks around it and
 * still throws every other entity, because the damage is applied on a different line of the very same loop.
 */
@Mixin(Explosion.class)
public abstract class ExplosionBeastImpulseMixin
{
    @Redirect(method = "Lnet/minecraft/world/level/Explosion;explode()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/ProtectionEnchantment;getExplosionKnockbackAfterDampener(Lnet/minecraft/world/entity/LivingEntity;D)D"))
    private double hungrycraft$beastTakesNoExplosionImpulse(LivingEntity entity, double knockback)
    {
        if (QueenBeastPhysics.blocksExternalImpulse(entity))
        {
            return 0.0D;
        }

        return ProtectionEnchantment.getExplosionKnockbackAfterDampener(entity, knockback);
    }
}
