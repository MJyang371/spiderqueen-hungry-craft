package spiderqueenhungrycraft.web;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEffects;

/**
 * The empowerment of the queen that decides which element her next web shot carries (Phase 7A).
 *
 * <p>The empowerment is a plain state flag, it changes no attribute and does nothing on its own. Every
 * gameplay entry point grants it here, so only one element can ever be active: granting an element always
 * removes the other one, and granting the same element again only refreshes its timer.
 */
public final class WebEmpowerment
{
    /** How long an empowerment lasts, 600 ticks is thirty seconds. */
    public static final int DURATION_TICKS = 600;

    /**
     * Foods that always trigger the poison empowerment, on top of every food whose own food properties
     * already carry poison. A pack can add its own poisonous food here.
     */
    public static final TagKey<Item> POISON_WEB_TRIGGER_FOODS = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "poison_web_trigger_foods"));

    private WebEmpowerment()
    {
    }

    /**
     * Grants the empowerment of that element to the queen. The other element is always removed first, and
     * a repeated grant only refreshes the timer, it never stacks.
     */
    public static void grant(ServerPlayer player, WebElement element)
    {
        MobEffect effect = effectOf(element);

        if (effect == null)
        {
            return;
        }

        for (WebElement other : WebElement.values())
        {
            if (other != element)
            {
                clear(player, other);
            }
        }

        player.addEffect(new MobEffectInstance(effect, DURATION_TICKS, 0, false, false, true));
    }

    /** Removes the empowerment of that element, if the player has it. */
    public static void clear(Player player, WebElement element)
    {
        MobEffect effect = effectOf(element);

        if (effect != null)
        {
            player.removeEffect(effect);
        }
    }

    /**
     * The element the next shot of that player carries. It is read from the real effects of the player on
     * the server, a client can never ask for a specific element.
     */
    public static WebElement current(Player player)
    {
        if (player.hasEffect(effectOf(WebElement.FIRE)))
        {
            return WebElement.FIRE;
        }

        if (player.hasEffect(effectOf(WebElement.POISON)))
        {
            return WebElement.POISON;
        }

        return WebElement.NORMAL;
    }

    /** @return the empowerment effect of that element, or null for the plain web. */
    public static MobEffect effectOf(WebElement element)
    {
        return switch (element)
        {
            case NORMAL -> null;
            case POISON -> HungryCraftEffects.POISON_WEB_EMPOWERMENT.get();
            case FIRE -> HungryCraftEffects.FIRE_WEB_EMPOWERMENT.get();
        };
    }
}
