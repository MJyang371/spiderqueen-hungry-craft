package spiderqueenhungrycraft.brood;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * The Queen Scepter (Queen Scepter command patch): the long range command tool of a queen over her brood.
 *
 * <p>Right click one of your own spiders and it cycles its order (follow, hold, patrol, follow); right click a creature
 * and the brood is sent at it; right click a block and the spiders that follow you scout that spot; shift and right
 * click and every spider of yours drops everything and comes home. An empty hand still picks a spider up as a carrier -
 * a spider is only commanded with the sceptre really in the hand.
 *
 * <p><b>The item itself does nothing.</b> Neither the reach of the game nor the tiny window of a right click is enough
 * for a tool that has to command sixty four blocks away, so the item is only the thing a queen holds: the client turns
 * a right click into a request, and the server - which casts the command ray of {@link QueenCommandRay} itself - decides
 * what that request means.
 *
 * <p>Every method below therefore stays out of the way on purpose: the game never runs a second command path beside the
 * real one, wherever a click happens to land.
 */
public class QueenScepterItem extends Item
{
    public QueenScepterItem(Item.Properties properties)
    {
        super(properties);
    }

    /** Right click into the air: the command pipeline of the client has already asked the server. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /** Right click a block: the command pipeline has already asked the server, which casts its own ray. */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        return InteractionResult.PASS;
    }

    /** Right click a creature: the command pipeline has already asked the server, which casts its own ray. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand)
    {
        return InteractionResult.PASS;
    }
}
