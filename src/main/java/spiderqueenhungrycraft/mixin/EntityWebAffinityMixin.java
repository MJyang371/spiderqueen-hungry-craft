package spiderqueenhungrycraft.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.web.WebAffinity;

/**
 * Keeps the vanilla web from crushing a web native (Phase 6-SIMPLE-2).
 *
 * <p>Vanilla webs slow every entity down with one very heavy stuck multiplier. This hook only replaces that
 * multiplier with a neutral one when the block is in {@code #spiderqueen_hungrycraft:web_blocks} and the
 * entity is web native; the gentle drag and the limited fall speed are then applied once per tick by
 * {@link WebAffinity#applyWebPhysics}. Every other entity and every other block state is untouched and keeps
 * the full vanilla behaviour.
 */
@Mixin(Entity.class)
public abstract class EntityWebAffinityMixin
{
    @ModifyVariable(method = "Lnet/minecraft/world/entity/Entity;makeStuckInBlock(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"), argsOnly = true)
    private Vec3 hungrycraft$gentleWebMultiplier(Vec3 multiplier, BlockState state)
    {
        Entity self = (Entity) (Object) this;

        if (!WebAffinity.isWebBlock(state) || !WebAffinity.isWebNative(self))
        {
            return multiplier;
        }

        return WebAffinity.NATIVE_STUCK_MULTIPLIER;
    }
}
