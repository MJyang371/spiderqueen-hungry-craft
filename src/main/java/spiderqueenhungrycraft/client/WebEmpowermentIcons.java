package spiderqueenhungrycraft.client;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientMobEffectExtensions;
import spiderqueenhungrycraft.web.WebElement;
import spiderqueenhungrycraft.web.WebEmpowermentEffect;

/**
 * Client only icons of the web empowerment effects (Phase 7A).
 *
 * <p>A vanilla item icon is drawn instead of an effect texture, so the mod ships no new PNG and the HUD still
 * shows a proper icon next to the remaining time.
 *
 * <p>The element is deliberately read from the effect when the icon is drawn, never captured when the
 * renderer is created: Forge calls {@code initializeClient} from the constructor of {@code MobEffect}, at
 * which point the element field of the effect is not assigned yet.
 */
public final class WebEmpowermentIcons
{
    private WebEmpowermentIcons()
    {
    }

    /** @return the icon renderer of that effect. */
    public static IClientMobEffectExtensions forEffect(WebEmpowermentEffect effect)
    {
        return new Renderer(effect);
    }

    /**
     * The icon renderer of one empowerment effect. The only thing it holds is the effect itself, an icon can
     * therefore never be null because of the construction order. Public so the development probe can check it.
     */
    public static final class Renderer implements IClientMobEffectExtensions
    {
        private final WebEmpowermentEffect effect;

        Renderer(WebEmpowermentEffect effect)
        {
            this.effect = effect;
        }

        /** @return the vanilla item drawn as the icon, empty while the effect carries no element. */
        public ItemStack icon()
        {
            WebElement element = this.effect.element();

            if (element == null)
            {
                return ItemStack.EMPTY;
            }

            return element.icon().getDefaultInstance();
        }

        @Override
        public boolean renderInventoryIcon(MobEffectInstance instance, EffectRenderingInventoryScreen<?> screen,
                GuiGraphics guiGraphics, int x, int y, int blitOffset)
        {
            renderIcon(guiGraphics, x, y);
            return true;
        }

        @Override
        public boolean renderGuiIcon(MobEffectInstance instance, Gui gui, GuiGraphics guiGraphics, int x, int y,
                float z, float alpha)
        {
            renderIcon(guiGraphics, x, y);
            return true;
        }

        private void renderIcon(GuiGraphics guiGraphics, int x, int y)
        {
            ItemStack icon = this.icon();

            if (!icon.isEmpty())
            {
                guiGraphics.renderItem(icon, x, y);
            }
        }
    }
}
