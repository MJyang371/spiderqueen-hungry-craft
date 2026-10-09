package spiderqueenhungrycraft.brood;

import net.minecraft.core.Position;
import net.minecraft.core.dispenser.AbstractProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The dispenser behaviour of the spider egg arrow (Spider Mine and Egg Arrow round).
 *
 * <p>A bow of the game finds its ammunition through the {@code minecraft:arrows} item tag, but a dispenser does not: it
 * keeps a plain table from an item to what that item does when it is thrown out, and only the items somebody put in
 * that table are ever fired. This class puts the spider egg arrow in it, exactly the way the game does it for a plain
 * arrow - same launch, same power, same wobble, same sound.
 *
 * <p>That is also the whole of the "shot by a dispenser" special case of this round: the arrow of a dispenser has no
 * owner, because a dispenser is not a living creature, so the spiders it delivers belong to nobody. They still come out
 * and they still attack what the arrow hit, but nobody can command them and nobody can tame them.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class BroodAmmoDispensers
{
    private BroodAmmoDispensers()
    {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        // The dispenser table is a plain map beside the registries, so it is filled after the registries are complete.
        event.enqueueWork(BroodAmmoDispensers::registerBehaviors);
    }

    private static void registerBehaviors()
    {
        DispenserBlock.registerBehavior(HungryCraftItems.SPIDER_EGG_ARROW.get(),
                new AbstractProjectileDispenseBehavior()
                {
                    @Override
                    protected Projectile getProjectile(Level level, Position position, ItemStack stack)
                    {
                        SpiderEggArrow arrow = new SpiderEggArrow(level, position.x(), position.y(), position.z());

                        // It explodes where it lands, so it is never picked back up - exactly like an arrow that stuck.
                        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;

                        return arrow;
                    }
                });
    }
}
