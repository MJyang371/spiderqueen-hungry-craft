package spiderqueenhungrycraft.cocoon;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * The packed form of a cocoon. Always a single item, because it stands for one specific prey
 * individual, not for a stack of silk.
 */
public class CocoonItem extends Item
{
    public CocoonItem(Item.Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null)
        {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        Level level = context.getLevel();

        // Safety valve: if some storage mod forced several cocoons into one stack, refuse instead of
        // duplicating a captured creature.
        if (stack.getCount() > 1)
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.cocoon_stack_unsafe"), true);
            }

            return InteractionResult.FAIL;
        }

        CocoonData data = CocoonData.from(stack);
        if (data == null)
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.cocoon_invalid_data"), true);
            }

            return InteractionResult.FAIL;
        }

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        CocoonEntity cocoon = new CocoonEntity(level);
        cocoon.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);
        cocoon.setCocoonData(data);

        if (!level.noCollision(cocoon))
        {
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.cocoon_no_space"), true);
            return InteractionResult.FAIL;
        }

        level.addFreshEntity(cocoon);
        level.playSound(null, cocoon.getX(), cocoon.getY(), cocoon.getZ(), SoundEvents.WOOL_PLACE,
                SoundSource.BLOCKS, 1.0F, 0.8F);

        if (!player.isCreative())
        {
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(false);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        CocoonData data = CocoonData.from(stack);

        if (data == null)
        {
            tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.cocoon.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.cocoon.captured", data.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.cocoon.nutrition", data.getNutrition())
                .withStyle(ChatFormatting.GRAY));

        // A cocoon with a child in it says so, because it is a nest and no longer a meal.
        if (data.hasSpiderEgg())
        {
            tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.cocoon.hatching")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /** Cocoon items are never damaged, the hook only keeps the item from being used as fuel or repair material. */
    @Override
    public boolean isCorrectToolForDrops(BlockState state)
    {
        return false;
    }
}
