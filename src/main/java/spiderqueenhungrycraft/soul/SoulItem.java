package spiderqueenhungrycraft.soul;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A lump of soul a player can hold and swallow (Soul Consumable round): the seven soul items of the mod, from the
 * soul of the benevolent up to the soul of a hero.
 *
 * <p><b>Using one is the whole feature.</b> One right click hands the player the worth of the soul, plays the very
 * collection a reclaimed soul remnant plays, and takes one piece out of the hand - one click is one soul item, even
 * out of a stack of sixty four.
 *
 * <p><b>The worth belongs to the item, never to the client.</b> It is a constant handed in at registration, so there
 * is no number on the wire that a client could choose: the packet a client may send is a right click, and the server
 * turns that click into souls through {@link Souls}. The same rule holds for the rest of the item: the shrink is what
 * the server did to the stack, and the creative hand keeps its item exactly like every other consumable of the game.
 *
 * <p>The item knows nothing about where it came from. A soul is worth the same whoever holds it, and the loot that
 * hands it out (see {@code spiderqueenhungrycraft.loot.SoulContainerLootModifier}) is a separate rule that only ever
 * writes an {@link ItemStack}.
 */
public class SoulItem extends Item
{
    /** What one of these is worth, in souls, decided here and never read from the world. */
    private final long soulValue;

    public SoulItem(long soulValue, Item.Properties properties)
    {
        super(properties);
        this.soulValue = soulValue;
    }

    /** @return the souls this item pays when it is used. */
    public long soulValue()
    {
        return this.soulValue;
    }

    /**
     * The one right click of a soul: the client only reports the click, the server pays.
     *
     * <p>A client that lies about having clicked changes nothing but its own animation: it cannot name an amount,
     * because the amount is not part of the exchange at all.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            this.swallow(serverPlayer, stack);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * The whole transaction of one soul item: souls in, the collection of a claimed remnant around the player who
     * swallowed it, and one piece out of the hand.
     *
     * <p>The order matters in exactly one way: the souls are paid through {@link Souls}, which is what syncs the HUD
     * and what keeps a balance from ever going below zero, and only then is the item taken - so a swallowed soul can
     * never be lost to a failed write.
     */
    private void swallow(ServerPlayer player, ItemStack stack)
    {
        Souls.addSouls(player, this.soulValue);

        // The same golden burst and the same chime a soul remnant plays when it is claimed, centred on the player who
        // swallowed the soul. It is feedback and only feedback: nothing explodes, nothing is damaged, no one is moved.
        SoulClaimFx.play(player);

        // Creative keeps the item, exactly like the vanilla consumables - and like them the click still counts.
        if (!player.isCreative())
        {
            stack.shrink(1);
        }
    }
}
