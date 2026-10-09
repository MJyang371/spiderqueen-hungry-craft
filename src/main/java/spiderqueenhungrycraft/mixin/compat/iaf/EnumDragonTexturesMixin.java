package spiderqueenhungrycraft.mixin.compat.iaf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.enums.EnumDragonTextures;

import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireTextureOverride;

/**
 * Draws a <b>revived</b> dragon of Ice and Fire with the skeleton it was (Necromancy IV prototype 1).
 *
 * <p>This is the whole client side of the compatibility, and it is deliberately the smallest possible one: one method
 * of Ice and Fire, entered at its head, where the answer of {@link IceAndFireTextureOverride} is handed back instead
 * of the original one. The method itself is not rewritten, the model is not touched, no animation is touched, and
 * nothing of Ice and Fire is reimplemented - a dragon that Ice and Fire considers a corpse, a living dragon and a
 * dragon of this mod that is neither are all still answered by Ice and Fire's own code.
 *
 * <p><b>Why it is optional in three different ways.</b>
 * <ul>
 * <li>it lives in its own mixin configuration ({@code spiderqueen_hungrycraft_iaf.mixins.json}) with
 * {@code "required": false}, so a failure of it can never fail a launch,</li>
 * <li>it is listed under {@code "client"}, so a dedicated server never even looks at it,</li>
 * <li>and the configuration has a plugin ({@link IceAndFireMixinPlugin}) that refuses to apply it at all when Ice and
 * Fire is not installed - so the class below, which names Ice and Fire types, is never loaded in a game without that
 * mod and can neither throw {@code NoClassDefFoundError} nor drag the target in.</li>
 * </ul>
 */
@Mixin(value = EnumDragonTextures.class, remap = false)
public abstract class EnumDragonTexturesMixin
{
    /**
     * The one hook of the whole prototype.
     *
     * <p>{@code getTextureFromDragon} is the single entrance Ice and Fire's own renderer uses to ask which PNG a
     * dragon is drawn with, so replacing its answer replaces the appearance and nothing else.
     */
    @Inject(method = "getTextureFromDragon", at = @At("HEAD"), cancellable = true, remap = false)
    private static void hungrycraft$skeletonTextureOfRevivedDragon(EntityDragonBase dragon,
            CallbackInfoReturnable<ResourceLocation> callback)
    {
        ResourceLocation skeletal = IceAndFireTextureOverride.skeletalTextureFor(dragon);

        if (skeletal != null)
        {
            callback.setReturnValue(skeletal);
        }
    }
}
