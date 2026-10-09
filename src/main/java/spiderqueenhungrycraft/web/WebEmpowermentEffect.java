package spiderqueenhungrycraft.web;

import java.util.function.Consumer;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import spiderqueenhungrycraft.client.WebEmpowermentIcons;

/**
 * The empowerment of one element (Phase 7A). It is a pure state marker: beneficial, no attribute changes,
 * no per tick behaviour. The client only overrides its icon, which is drawn from a vanilla item so no new
 * texture is needed.
 */
public class WebEmpowermentEffect extends MobEffect
{
    private final WebElement element;

    public WebEmpowermentEffect(WebElement element)
    {
        super(MobEffectCategory.BENEFICIAL, element.tintColor());
        this.element = element;
    }

    /** The element this empowerment belongs to. */
    public WebElement element()
    {
        return this.element;
    }

    @Override
    public void initializeClient(Consumer<IClientMobEffectExtensions> consumer)
    {
        // Forge calls this from the constructor of MobEffect, so nothing here may read a field of this class:
        // the element field is only assigned after that call. The icon is resolved later, when it is drawn.
        consumer.accept(WebEmpowermentIcons.forEffect(this));
    }
}
