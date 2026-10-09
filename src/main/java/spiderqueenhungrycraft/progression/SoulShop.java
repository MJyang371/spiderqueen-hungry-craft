package spiderqueenhungrycraft.progression;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The products of the soul shop (Progression Foundation round).
 *
 * <p>It is one static holder, filled by {@link SoulShopReloadListener} from the data pack and replaced wholesale on
 * {@code /reload}, so the rules and the interface read one list and never a file.
 *
 * <p><b>The server is the only authority.</b> A purchase is validated against the list the server holds, never
 * against what a client thinks it saw, and a product that a reload took away simply stops being buyable - the
 * interface of a client that still shows it is refused on the next click.
 */
public final class SoulShop
{
    /** Nothing is for sale until the data pack said otherwise. */
    private static volatile List<ShopEntry> entries = List.of();

    private SoulShop()
    {
    }

    /** @return every product the world declared, enabled or not, in the order they were read. */
    public static List<ShopEntry> entries()
    {
        return entries;
    }

    /** @return the products that are really for sale. */
    public static List<ShopEntry> enabledEntries()
    {
        return entries.stream().filter(ShopEntry::enabled).toList();
    }

    /** @return the product of that identity, or empty when the world has none of that name. */
    public static Optional<ShopEntry> find(ResourceLocation id)
    {
        for (ShopEntry entry : entries)
        {
            if (entry.id().equals(id))
            {
                return Optional.of(entry);
            }
        }

        return Optional.empty();
    }

    /** Replaces the whole list, used by the reload listener. */
    public static void set(List<ShopEntry> loaded)
    {
        entries = List.copyOf(loaded);
        SpiderQueenHungryCraft.LOGGER.info("[SoulShop] {} products loaded, {} of them for sale", entries.size(),
                enabledEntries().size());
    }

    /** Empties the shop, used when a world has no shop files at all. */
    public static void reset()
    {
        entries = List.of();
    }
}
