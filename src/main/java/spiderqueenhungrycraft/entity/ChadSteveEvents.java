package spiderqueenhungrycraft.entity;

import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Super armor of Chad Steve (Phase 9C, part C).
 *
 * <p>The first layer of the armor is the {@code KNOCKBACK_RESISTANCE} attribute of
 * {@link ChadSteveEntity#createAttributes()}, which already removes the knockback of an ordinary attack. This
 * listener is the second layer: it catches the knockback that is applied directly through
 * {@code LivingEntity#knockback}, where the attribute of the victim is not part of the calculation, and refuses it
 * as well. The exact same pattern is already used for the stomach core in {@code StomachCoreEvents}.
 *
 * <p>Only the knockback of a Chad Steve is touched. Damage, fire, poison, explosions and the knockback of every
 * other entity are untouched on purpose - he is not invulnerable, he is simply not movable.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class ChadSteveEvents
{
    private ChadSteveEvents()
    {
    }

    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event)
    {
        if (event.getEntity() instanceof ChadSteveEntity)
        {
            event.setCanceled(true);
        }
    }
}
