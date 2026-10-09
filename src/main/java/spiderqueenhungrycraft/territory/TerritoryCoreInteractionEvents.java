package spiderqueenhungrycraft.territory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;

/**
 * The one right click vanilla would never hand to a territory core (Progression Foundation round).
 *
 * <p>By the rule of the game itself, a player who sneaks <b>while carrying something</b> never asks the block at all:
 * the click goes straight to the item in their hand (that is how a block is placed against a chest, and it is
 * deliberate). The growth interface of a queen must not depend on what she happens to be holding, so this one gesture
 * is claimed here for the core: the block is asked after all, and the carried item is refused for that click.
 *
 * <p><b>Nothing else is touched.</b> A normal click, a normal click with an item, and sneaking with empty hands are
 * all left exactly as the game handles them - the first two reach {@link TerritoryCoreBlock#use} on their own, and the
 * third is the range gesture that belongs to that method. No vanilla behaviour of any other block is affected, because
 * everything here returns immediately unless the click really lands on a territory core.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class TerritoryCoreInteractionEvents
{
    private TerritoryCoreInteractionEvents()
    {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();

        BlockState state = event.getLevel().getBlockState(event.getPos());

        if (!state.is(HungryCraftBlocks.TERRITORY_CORE.get()))
        {
            return;
        }

        // Sneaking with both hands empty is the range gesture, which belongs to the block and is handled there.
        boolean handsEmpty = player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty();

        if (!player.isSecondaryUseActive() || handsEmpty)
        {
            return;
        }

        // Sneaking with something carried: the block gets the click, and the carried item does not.
        event.setUseBlock(Event.Result.ALLOW);
        event.setUseItem(Event.Result.DENY);
    }
}
