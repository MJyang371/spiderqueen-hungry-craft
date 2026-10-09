package spiderqueenhungrycraft.beast;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Queen claw mining (Phase 8A).
 *
 * <p>While transformed the item abilities are locked, but the queen may still break blocks: she does not swing
 * a pickaxe, she tears the block apart with her claws. Both sides of the digging pipeline are answered here,
 * the speed and the harvest check, and both ignore the real held item on purpose, so an empty hand digs just
 * as well as a netherite pickaxe and no enchantment of a held tool changes the claw speed.
 *
 * <p>Unlike the transformation clearance this is a real mining ability: the blocks drop exactly like a normal
 * block break. Unbreakable blocks are never touched, their negative hardness keeps the vanilla rule intact.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBeastClawMining
{
    private QueenBeastClawMining()
    {
    }

    /** The claws have their own speed, the tool in the hand is ignored completely. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();

        if (!QueenBeastState.isTransformed(player))
        {
            return;
        }

        if (!isClawMineable(player, event.getState(), event.getPosition().orElse(null)))
        {
            return;
        }

        event.setNewSpeed(QueenBeastConstants.BEAST_CLAW_DIG_SPEED);
    }

    /**
     * The claws harvest like the best tool of the game, so stone, deepslate and ores drop their real loot even
     * with an empty hand. Unbreakable blocks are refused, everything else is allowed.
     */
    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event)
    {
        Player player = event.getEntity();

        if (!QueenBeastState.isTransformed(player) || event.canHarvest())
        {
            return;
        }

        BlockState state = event.getTargetBlock();

        if (state.getDestroySpeed(player.level(), player.blockPosition()) < 0.0F)
        {
            return;
        }

        event.setCanHarvest(true);
    }

    /** @return true when the claws may tear that block apart, which is every block with a real hardness. */
    private static boolean isClawMineable(Player player, BlockState state, BlockPos pos)
    {
        BlockPos at = pos != null ? pos : player.blockPosition();

        return state.getDestroySpeed(player.level(), at) >= 0.0F;
    }
}
