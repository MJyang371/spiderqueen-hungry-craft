package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Where a spider of the brood earns its rank (Brood Veterancy round).
 *
 * <p>One creature dies, and the creature that struck the last blow gets the credit - the whole credit. There is no
 * splitting the experience between everybody who hit the victim, no sharing it with the spiders standing around, no
 * team experience and no experience for damage that did not kill: the last blow is what makes a kill, so the last blow
 * is what is paid.
 *
 * <p>Two things are handled with care here:
 *
 * <ul>
 * <li><b>Ranged kills count.</b> An arrow of a skeleton spider, a web shot, a thrown thing - the damage source of a
 * projectile names the creature behind it, so the shooter is found through it and gets the kill, exactly like the
 * spider that bit. That rule lives in {@link SpiderKillCredit}, because the kill credit of the queen reads the very same
 * one (Brood Kill Credit pass).</li>
 * <li><b>A capture is not a kill.</b> A victim that is wrapped in web and turned into a cocoon does not die at all: it
 * is removed from the world, and no death event ever fires for it. Nothing has to be filtered here, because there is
 * nothing to filter - and that is the point of listening to the death event instead of watching health.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SpiderVeterancyEvents
{
    private SpiderVeterancyEvents()
    {
    }

    /** The kill of that creature: the spider behind the last blow gets the experience for it. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide || !victim.isDeadOrDying())
        {
            return;
        }

        SpiderMinionEntity killer = SpiderKillCredit.killerOf(event.getSource());

        if (killer != null)
        {
            killer.addKillExperience(SpiderVeterancyData.experienceFrom(victim));
        }
    }
}
