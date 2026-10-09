package spiderqueenhungrycraft.loot;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import spiderqueenhungrycraft.bed.SpiderBedBlock;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.web.WebAffinity;

/**
 * The web harvest of the Spider Queen (Phase 6-SIMPLE-3B).
 *
 * <p>Whenever the queen breaks a block of {@code #spiderqueen_hungrycraft:web_blocks}, whatever she holds, the
 * drops are replaced by four to six string. The vanilla drops are discarded completely, so shears or a silk
 * touch tool can never hand out a web block in addition to the string.
 *
 * <p>Everything is tag driven: the vanilla cobweb, the queen web of a web shot and any web a datapack or
 * another mod adds to the tag all run through this single rule. Every other player keeps the untouched loot.
 */
public class QueenWebHarvestModifier extends LootModifier
{
    /** Lowest amount of string a queen harvest gives. */
    public static final int QUEEN_WEB_HARVEST_MIN = 4;

    /** Highest amount of string a queen harvest gives. */
    public static final int QUEEN_WEB_HARVEST_MAX = 6;

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<QueenWebHarvestModifier> CODEC = RecordCodecBuilder.create(
            instance -> LootModifier.codecStart(instance).apply(instance, QueenWebHarvestModifier::new));

    public QueenWebHarvestModifier(LootItemCondition[] conditions)
    {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot,
            LootContext context)
    {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);

        if (!WebAffinity.isWebBlock(state))
        {
            return generatedLoot;
        }

        // A spider bed counts as a web for movement, but it is a piece of furniture and not a harvest: its
        // own loot table stays untouched, so a queen gets nothing bare handed and one bed with silk touch.
        if (state.getBlock() instanceof SpiderBedBlock)
        {
            return generatedLoot;
        }

        Entity breaker = context.getParamOrNull(LootContextParams.THIS_ENTITY);

        if (!(breaker instanceof Player player) || !QueenDataCapability.isQueen(player))
        {
            return generatedLoot;
        }

        // Creative keeps the vanilla habit of dropping nothing, the harvest is a survival rule.
        if (player.isCreative())
        {
            return generatedLoot;
        }

        // One roll per broken block, on the server side random source of that player.
        int count = QUEEN_WEB_HARVEST_MIN
                + player.getRandom().nextInt(QUEEN_WEB_HARVEST_MAX - QUEEN_WEB_HARVEST_MIN + 1);

        LOGGER.debug("[web-harvest] queen harvest block={} count={} tool={}", state, count,
                player.getMainHandItem());

        // Replace, never extend: no web block, no cobweb plus string, no double drops.
        ObjectArrayList<ItemStack> harvested = new ObjectArrayList<>();
        harvested.add(new ItemStack(Items.STRING, count));
        return harvested;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec()
    {
        return HungryCraftLootModifiers.QUEEN_WEB_HARVEST.get();
    }
}
