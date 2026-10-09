package spiderqueenhungrycraft.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import spiderqueenhungrycraft.soul.Souls;

/**
 * What buying something in the soul shop really does on the server (Progression Foundation round).
 *
 * <p>The same rule as everywhere else in this system: a request is a request, and every condition is checked here,
 * against the products the <b>server</b> currently holds. A product that a {@code /reload} took away, or that a pack
 * switched off, is refused even while the interface of a client still shows it.
 *
 * <p><b>A full inventory never stops a purchase.</b> That is on purpose: the whole point of the shop is that a queen
 * can turn souls into things she can carry <i>before</i> she walks into somewhere she might not come back from, so
 * the shop must never be a way to lose souls to a full bag. What does not fit lands at her feet as an ordinary item.
 */
public final class SoulShopService
{
    private SoulShopService()
    {
    }

    /**
     * The one way a product is bought.
     *
     * @return the code the client has to show; nothing is charged unless a purchase really happened.
     */
    public static ProgressionFeedback purchase(ServerPlayer player, BlockPos corePos, ResourceLocation entryId)
    {
        if (!ProgressionSessions.isValid(player, corePos))
        {
            return ProgressionFeedback.DENIED;
        }

        ShopEntry entry = SoulShop.find(entryId).orElse(null);

        if (entry == null || !entry.enabled())
        {
            return ProgressionFeedback.UNKNOWN_PRODUCT;
        }

        // A product that hangs off a skill the buyer does not own simply does not exist for her. This is the second
        // half of the rule of the shop (the first one is the snapshot, which never sends her such an entry): a client
        // that was modified to ask anyway is answered exactly like one that asks for a product that was never there
        // (Skill Progression round).
        if (!entry.visibleTo(player))
        {
            return ProgressionFeedback.UNKNOWN_PRODUCT;
        }

        if (!Souls.spendSouls(player, entry.price()))
        {
            return ProgressionFeedback.INSUFFICIENT_SOULS;
        }

        handOver(player, entry);
        return ProgressionFeedback.PURCHASE_OK;
    }

    /**
     * Hands one batch over: into the inventory first, and whatever does not fit is dropped in front of the player as
     * an ordinary item entity.
     *
     * <p>It is the plain insertion of the game and the plain drop of the game, nothing else. The remainder of a
     * partial insertion stays in the stack that {@code Inventory#add} was given, which is exactly what is dropped
     * afterwards - so a purchase can never hand out an item twice, and it can never swallow one either.
     */
    private static void handOver(ServerPlayer player, ShopEntry entry)
    {
        ItemStack stack = entry.createStack();

        if (!player.getInventory().add(stack) && !stack.isEmpty())
        {
            player.drop(stack, false);
        }
    }
}
