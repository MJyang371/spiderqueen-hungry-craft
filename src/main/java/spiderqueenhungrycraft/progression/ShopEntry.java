package spiderqueenhungrycraft.progression;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One product of the soul shop (Progression Foundation round, extended by the Skill Progression round).
 *
 * <p>A product is a batch: {@code count} pieces of one item for {@code price} souls, bought in one click. The first
 * round ships exactly one product - one spider egg for a hundred souls - and the shop of this mod is deliberately
 * about one theme: souls are turned into the resources an army of spiders eats through.
 *
 * <p><b>A product may hang off a skill</b> ({@code requires_skill} in its file). The spider egg does: it is the laying
 * of a brood queen turned into a purchase, so a queen who does not own that node does not see the product at all. That
 * is decided in one place - {@link #visibleTo} - and it is asked twice: once when the snapshot is built, so the entry
 * is not sent to her, and once again when a purchase arrives, so a client that asks anyway is refused.
 *
 * @param id      the identity of the product, taken from the file that declared it
 * @param item    the item the batch contains
 * @param count   how many pieces one purchase gives, at least one
 * @param price   how many souls one purchase costs, never negative
 * @param enabled false keeps the product out of the shop without deleting it
 * @param requiresSkill the skill a buyer has to own, or null when the product is for everybody
 */
public record ShopEntry(ResourceLocation id, Item item, int count, long price, boolean enabled,
        @Nullable ResourceLocation requiresSkill)
{
    /** @return one stack of what a purchase of this product hands out. */
    public ItemStack createStack()
    {
        return new ItemStack(this.item, Math.max(1, this.count));
    }

    /**
     * @return true when that player may see and buy this product: either it has no requirement at all, or she owns the
     *         skill it names.
     *
     *         <p>A product whose requirement is not met does not exist for that player: it is not drawn greyed out and
     *         not drawn locked, it is simply not there.
     */
    public boolean visibleTo(@Nullable Player player)
    {
        return this.requiresSkill == null || QueenSkills.has(player, this.requiresSkill);
    }
}
