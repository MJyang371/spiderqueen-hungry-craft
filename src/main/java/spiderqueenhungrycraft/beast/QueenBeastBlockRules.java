package spiderqueenhungrycraft.beast;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one block rule matcher of the Queen Beast form (Phase 8C / 8D).
 *
 * <p>The transformation clearance, the body crush of an ordinary walk, the stomp of a landing and the charge
 * of a hard run all ask this class, so the four abilities can never drift apart and a pack changes what the
 * beast destroys in one place: {@code data/<namespace>/queen_beast/tuning.json}, section
 * {@code block_breaking}. Nothing here hardcodes a block, a tag or a hardness number beyond the built in
 * defaults that a datapack file simply overrides.
 *
 * <p>The priority of the rules is fixed and testable, from the strongest to the weakest:
 * <ol>
 * <li>a protected block or protected tag is never broken, whatever else says otherwise,</li>
 * <li>a block with negative hardness can never be broken,</li>
 * <li>a block with stored data is only broken when the rule allows block entities,</li>
 * <li>an explicitly allowed block or tag is broken even above the hardness limit,</li>
 * <li>everything else is judged by the hardness of the rule, or by the hardness of the reference block the
 * rule names instead. A reference block is compared strictly, which is what makes obsidian a hard stop for a
 * charge while everything softer gives way.</li>
 * </ol>
 */
public final class QueenBeastBlockRules
{
    /** Blocks the beast tears off, a small obstacle rather than a real wall. */
    public static final TagKey<Block> BEAST_FRAGILE = tag("beast_fragile");

    /** Blocks no beast ability ever breaks. The strongest rule of all. */
    public static final TagKey<Block> BEAST_PROTECTED = tag("beast_protected");

    private static final Set<Block> NO_BLOCKS = Set.of();
    private static final List<TagKey<Block>> NO_TAGS = List.of();

    private QueenBeastBlockRules()
    {
    }

    /**
     * The built in rule of one ability. It is the value the game uses when the tuning file says nothing about
     * that ability, and the starting point every parsed file is layered on top of.
     */
    public static QueenBeastTuning.Rule builtInDefaults(QueenBeastTuning.RuleProfile profile)
    {
        List<TagKey<Block>> noBreak = List.of(BEAST_PROTECTED);

        return switch (profile)
        {
            case TRANSFORM_CLEARANCE -> new QueenBeastTuning.Rule(3.0D, null, false, false, NO_BLOCKS,
                    List.of(BlockTags.LOGS, BlockTags.PLANKS, BlockTags.LEAVES, BEAST_FRAGILE), NO_BLOCKS, noBreak);
            case BODY_CRUSH -> new QueenBeastTuning.Rule(0.2D, null, false, false, NO_BLOCKS,
                    List.of(BlockTags.LOGS, BlockTags.LEAVES, BEAST_FRAGILE), NO_BLOCKS, noBreak);
            case STOMP -> new QueenBeastTuning.Rule(1.0D, null, false, false, NO_BLOCKS,
                    List.of(BlockTags.DIRT, BlockTags.LOGS, BlockTags.LEAVES, BEAST_FRAGILE), NO_BLOCKS, noBreak);
            case CHARGE -> new QueenBeastTuning.Rule(0.0D, Blocks.OBSIDIAN, true, true, NO_BLOCKS, NO_TAGS,
                    NO_BLOCKS, noBreak);
        };
    }

    /** @return true when that ability may break that block right now, using the rules of the current reload. */
    public static boolean canBreak(BlockGetter level, BlockPos pos, BlockState state,
            QueenBeastTuning.RuleProfile profile)
    {
        return canBreak(level, pos, state, QueenBeastTuning.rule(profile));
    }

    /** @return true when that rule may break that block right now. */
    public static boolean canBreak(BlockGetter level, BlockPos pos, BlockState state, QueenBeastTuning.Rule rule)
    {
        // 1) The protected rule wins over everything, even over an explicit allow entry.
        if (isProtected(state, rule))
        {
            return false;
        }

        float hardness = state.getDestroySpeed(level, pos);

        // 2) Bedrock and every other unbreakable block survive every ability of the beast.
        if (hardness < 0.0F)
        {
            return false;
        }

        // 3) A chest, a machine or any other stored data is only touched when the rule really allows it.
        if (state.hasBlockEntity() && !rule.allowBlockEntities())
        {
            return false;
        }

        // 4) An explicit entry breaks the block even when it is harder than the rule would allow.
        if (rule.allowBlocks().contains(state.getBlock()))
        {
            return true;
        }

        for (TagKey<Block> tag : rule.allowTags())
        {
            if (state.is(tag))
            {
                return true;
            }
        }

        // 5) The hardness of the rule, or of the reference block it names.
        Block reference = rule.hardnessReference();

        if (reference != null)
        {
            return hardness < reference.defaultBlockState().getDestroySpeed(level, pos);
        }

        return hardness <= rule.maxHardness();
    }

    /** @return true when that block is protected from every beast ability of that rule. */
    public static boolean isProtected(BlockState state, QueenBeastTuning.Rule rule)
    {
        if (rule.protectedBlocks().contains(state.getBlock()))
        {
            return true;
        }

        for (TagKey<Block> tag : rule.protectedTags())
        {
            if (state.is(tag))
            {
                return true;
            }
        }

        return false;
    }

    private static TagKey<Block> tag(String name)
    {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, name));
    }
}
