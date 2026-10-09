package spiderqueenhungrycraft.beast;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The item lock of the Queen Beast form (Phase 8A, extended by 8B-POLISH-2).
 *
 * <p>A giant spider has no hands for a bow, a shield, a potion or a block of dirt, so while transformed the
 * item abilities of the player are denied. This is deliberately not a hotbar lock: the player may still
 * scroll, open the inventory, look at and pick up items. Only using them is refused.
 *
 * <p>Since 8B-POLISH-2 the right mouse button of the beast belongs to the bite and to nothing else. Every
 * ordinary use of that button is refused on the server: no chest, no door, no lever, no button, no placed
 * block, no eaten item, no traded villager and no equipped saddle. The client already swallows those clicks,
 * but the server refuses them again on its own, so a modified client cannot open a chest mid bite.
 *
 * <p>Mining is a separate feature ({@link QueenBeastClawMining}) and is untouched here: the left mouse button
 * keeps tearing blocks apart with the claws.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBeastItemLock
{
    private QueenBeastItemLock()
    {
    }

    /** Eating, drinking, charging a bow, raising a shield and using every other item is denied. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        deny(event);
    }

    /** No block interaction at all: the right button of a beast is the bite, not a door handle. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        deny(event);
    }

    /** No interaction with a creature either: no feeding, no trading, no lead, no saddle. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        deny(event);
    }

    /** The precise variant of the same click, for the interactions that need a hit point on the creature. */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)
    {
        deny(event);
    }

    /** Safety net for item use that starts without the two events above, for example from another mod. */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event)
    {
        if (event.getEntity() instanceof Player player && isLocked(player))
        {
            event.setCanceled(true);
        }
    }

    /** No ordinary melee while transformed: the left button belongs to the claws and to nothing else. */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event)
    {
        if (isLocked(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    /** Refuses one interaction of a beast, with the same answer a broken tool would give. */
    private static void deny(PlayerInteractEvent event)
    {
        if (!isLocked(event.getEntity()))
        {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
    }

    private static boolean isLocked(Player player)
    {
        return QueenBeastState.isTransformed(player);
    }
}
