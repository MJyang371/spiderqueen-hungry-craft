package spiderqueenhungrycraft.beast;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;

/**
 * The one block breaker of the Queen Beast form (Phase 8C / 8D).
 *
 * <p>Every ability that removes blocks goes through here, so exactly one place decides how a block is judged,
 * how it is removed and what it drops. The judgement itself lives in {@link QueenBeastBlockRules}; this class
 * adds the three things that must never be forgotten:
 *
 * <ul>
 * <li><b>permission</b>: the vanilla break hook {@code ForgeHooks.onBlockBreakEvent} is fired for every single
 * block, so claims, region protection and every mod that guards a block can refuse it. A refused block is
 * never removed and is reported back to the caller, which is what turns it into a hard stop for a charge,</li>
 * <li><b>drops</b>: a rule may drop or not drop the loot. When it drops, the ordinary loot table of the block
 * is used with an empty hand, so ores and ancient debris drop their normal result while no enchantment of a
 * held pickaxe can leak into it,</li>
 * <li><b>budget</b>: every caller passes a remaining block budget, so a single stomp, charge tick or walk can
 * never rewrite thousands of blocks in one tick.</li>
 * </ul>
 */
public final class QueenBeastBlockBreaker
{
    /** Vanilla uses 512 as the recursion limit of a block break, the beast helpers use the very same value. */
    private static final int BLOCK_UPDATE_RECURSION = 512;

    private QueenBeastBlockBreaker()
    {
    }

    /**
     * Asks the rules and the protection hooks once, and never breaks anything itself.
     *
     * <p>This is the single place a break permission is asked. The corridor, the stomp and the clearance all
     * call it and then use {@link #claim} to really remove the block, so the vanilla break hook of a candidate
     * block fires exactly once instead of once for a probe and again for the break.
     *
     * @return true when that block may be broken right now
     */
    public static boolean canClaim(ServerLevel level, ServerPlayer player, BlockPos pos,
            QueenBeastTuning.RuleProfile profile)
    {
        BlockState state = level.getBlockState(pos);

        if (state.isAir() || !QueenBeastBlockRules.canBreak(level, pos, state, profile))
        {
            return false;
        }

        // The vanilla hook answers the experience of the break, or -1 when the break is denied.
        return ForgeHooks.onBlockBreakEvent(level, gameMode(player), player, pos) != -1;
    }

    /**
     * Removes a block that {@link #canClaim} already allowed. No rules and no hook run here, so a caller can
     * never ask for the same permission twice.
     */
    public static void claim(ServerLevel level, ServerPlayer player, BlockPos pos, boolean dropLoot)
    {
        BlockState state = level.getBlockState(pos);

        if (state.isAir())
        {
            return;
        }

        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;

        if (dropLoot)
        {
            // An empty hand on purpose: the beast smashes things with its body, so a hidden pickaxe may never
            // add fortune, silk touch or any other enchantment to the loot of a charge.
            Block.dropResources(state, level, pos, blockEntity, player, ItemStack.EMPTY);
        }

        level.destroyBlock(pos, false, player, BLOCK_UPDATE_RECURSION);
    }

    /**
     * Breaks one block if the rule and the permission hooks allow it.
     *
     * @return true when the block was really removed, false when the rule or a protection hook refused it
     */
    public static boolean breakBlock(ServerLevel level, ServerPlayer player, BlockPos pos,
            QueenBeastTuning.RuleProfile profile)
    {
        if (!canClaim(level, player, pos, profile))
        {
            return false;
        }

        claim(level, player, pos, QueenBeastTuning.rule(profile).dropBlocks());
        return true;
    }

    /**
     * Breaks every allowed block of that list, at most {@code budget} of them.
     *
     * @return the number of blocks that were really removed
     */
    public static int breakBlocks(ServerLevel level, ServerPlayer player, List<BlockPos> positions,
            QueenBeastTuning.RuleProfile profile, int budget)
    {
        int broken = 0;

        for (BlockPos pos : positions)
        {
            if (broken >= budget)
            {
                break;
            }

            if (breakBlock(level, player, pos, profile))
            {
                broken++;
            }
        }

        return broken;
    }

    private static GameType gameMode(ServerPlayer player)
    {
        return player.gameMode.getGameModeForPlayer();
    }
}
