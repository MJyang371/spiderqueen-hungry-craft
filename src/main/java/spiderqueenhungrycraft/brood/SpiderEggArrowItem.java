package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The spider egg arrow as an item (Spider Mine and Egg Arrow round): the ammunition a bow loads into a
 * {@link SpiderEggArrow}.
 *
 * <p>It extends {@link ArrowItem} and that is the whole trick of its use. The ammunition a bow of the game accepts is
 * the {@code minecraft:arrows} item tag, and the off hand is the first place it is looked for - so the item only has to
 * be in that tag (see {@code data/minecraft/tags/items/arrows.json}) and the game itself does the rest: a queen with a
 * bow in her hand and this arrow in her off hand shoots it, with the ordinary charge, the ordinary sound and the
 * ordinary damage, and the arrow that leaves the bow is ours because {@link #createArrow} says so.
 *
 * <p>It is never infinite. {@code ArrowItem#isInfinite} of the game only spares a plain arrow of the game, so the
 * Infinity enchantment of a bow does not turn one egg arrow into a free supply of them - the same rule a tipped arrow
 * of the game lives by.
 */
public class SpiderEggArrowItem extends ArrowItem
{
    public SpiderEggArrowItem(Item.Properties properties)
    {
        super(properties);
    }

    /** The one thing this item decides: a shot of it becomes a spider egg arrow. */
    @Override
    public AbstractArrow createArrow(Level level, ItemStack stack, LivingEntity shooter)
    {
        return new SpiderEggArrow(level, shooter);
    }
}
