package spiderqueenhungrycraft.hook;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The five qualities of the spider hook (feature SPIDER-HOOK). It is the one table of the feature: everything
 * a quality decides is written down here, so a new rod - a queen grade, a special silk, a drop of a boss - is
 * one more line and never a new branch in the code.
 *
 * <p>A quality decides: how hard the web head is pulled down while it flies, how many casts in a row the rod
 * takes before it needs a break, how far the web may be thrown, how far its owner may get from the anchor
 * before the silk snaps, how much wear a pull costs, and what the rod is repaired with. Everything else - the
 * rod in the hand, the web head in the air, the silk between hand and head and the pull on the player - is
 * shared by all five qualities, exactly like every fishing rod shares the same cast.
 *
 * <p>The two numbers of the reach and of the wear are read through the configuration of the mod, with the
 * values here as the fallback: a pack author may move them, the table stays the truth when he does not.
 *
 * <p>The gravity numbers are blocks per tick per tick, so they can be compared with the game itself: an
 * arrow falls with {@code 0.05}, which is what the golden hook is measured against.
 */
public enum SpiderHookTier
{
    /** The plain hook: a heavy web that drops fast, one cast and then a break, and the shortest silk. */
    PLAIN("spider_hook", 0.08D, 1, 24.0D, 30, Items.STRING, 128, 3.0D, 24.0D, 15),
    /** Iron: still a heavy web, but it drops noticeably less and its silk reaches farther. */
    IRON("iron_spider_hook", 0.05D, 1, 28.0D, 34, Items.IRON_INGOT, 256, 5.0D, 40.0D, 14),
    /** Golden: about as much drop as a fully charged arrow, two casts in a row, the longest silk for its price. */
    GOLDEN("golden_spider_hook", 0.04D, 2, 32.0D, 40, Items.GOLD_INGOT, 128, 8.0D, 64.0D, 22),
    /** Diamond: almost no drop at all, two casts in a row and a long reach. */
    DIAMOND("diamond_spider_hook", 0.012D, 2, 40.0D, 50, Items.DIAMOND, 512, 12.0D, 96.0D, 10),
    /** Netherite: no drop at all, three casts in a row and the longest silk of the mod. */
    NETHERITE("netherite_spider_hook", 0.0D, 3, 48.0D, 60, Items.NETHERITE_INGOT, 1024, 16.0D, 128.0D, 15);

    private static final SpiderHookTier[] VALUES = values();

    /** How much room the search for the hook of one player leaves beyond the longest range. */
    private static final double SEARCH_MARGIN = 4.0D;

    private final String itemName;
    private final double gravity;
    private final int usesBeforeCooldown;
    private final double maxRange;
    private final int maxFlightTicks;
    private final Item repairItem;
    private final int baseDurability;
    private final double baseBlocksPerDamage;
    private final double baseTetherDistance;
    private final int enchantability;

    SpiderHookTier(String itemName, double gravity, int usesBeforeCooldown, double maxRange, int maxFlightTicks,
            Item repairItem, int baseDurability, double baseBlocksPerDamage, double baseTetherDistance,
            int enchantability)
    {
        this.itemName = itemName;
        this.gravity = gravity;
        this.usesBeforeCooldown = usesBeforeCooldown;
        this.maxRange = maxRange;
        this.maxFlightTicks = maxFlightTicks;
        this.repairItem = repairItem;
        this.baseDurability = baseDurability;
        this.baseBlocksPerDamage = baseBlocksPerDamage;
        this.baseTetherDistance = baseTetherDistance;
        this.enchantability = enchantability;
    }

    /** @return the registry name of the rod of this quality. */
    public String itemName()
    {
        return this.itemName;
    }

    /** @return how far the web head falls every tick, in blocks per tick per tick. */
    public double gravity()
    {
        return this.gravity;
    }

    /** @return how many casts in a row this rod takes before it needs a break. */
    public int usesBeforeCooldown()
    {
        return this.usesBeforeCooldown;
    }

    /** @return the length of that break, in ticks. */
    public static int breakTicks()
    {
        return HungryCraftConfig.hookBreakTicks();
    }

    /** @return how far from its owner the web head may get before it is taken back, in blocks. */
    public double maxRange()
    {
        return this.maxRange;
    }

    /** @return how long the web head may stay in the air before it is taken back, in ticks. */
    public int maxFlightTicks()
    {
        return this.maxFlightTicks;
    }

    /** @return the item this quality is repaired with on an anvil. */
    public Item repairItem()
    {
        return this.repairItem;
    }

    /** @return the durability this quality is registered with, before the configuration has its say. */
    public int baseDurability()
    {
        return this.baseDurability;
    }

    /** @return the durability of this quality: the configuration wins over the table. */
    public int maxDurability()
    {
        return HungryCraftConfig.hookDurability(this.ordinal(), this.baseDurability);
    }

    /**
     * @return how far the owner of a hanging web may get from its anchor before the silk snaps, in blocks.
     *         This is the reach of a web that already hangs on a block, never the length of a throw.
     */
    public double tetherDistance()
    {
        return HungryCraftConfig.hookTetherDistance(this.ordinal(), this.baseTetherDistance);
    }

    /** @return how many blocks of being dragged cost one point of durability. */
    public double blocksPerDamage()
    {
        return HungryCraftConfig.hookBlocksPerDamage(this.ordinal(), this.baseBlocksPerDamage);
    }

    /** @return the enchantability of this quality, which is what the enchanting table offers a rod with. */
    public int enchantability()
    {
        return this.enchantability;
    }

    /**
     * @return the wear one finished pull costs, in points of durability. A web that was never pulled costs
     *         nothing, and the farther a pull dragged its owner the more it costs - the whole point of the
     *         rule is that a cast is paid for by what it did, not by how often it was cast.
     */
    public int damageFor(double travelledBlocks)
    {
        if (travelledBlocks <= 0.0D)
        {
            return 0;
        }

        return (int) Math.ceil(travelledBlocks / this.blocksPerDamage());
    }

    /** @return the id written into the save data of a web head. */
    public int id()
    {
        return this.ordinal();
    }

    /** @return the quality with that id, so a web head keeps the quality it was fired with. */
    public static SpiderHookTier byId(int id)
    {
        return id >= 0 && id < VALUES.length ? VALUES[id] : PLAIN;
    }

    /**
     * @return the farthest any quality may reach, whether the web is still flying or already hangs on a block.
     *         It bounds the search for the hook of one player, so a web can never be lost to a search that was
     *         too small: the rod has to find its own web even when its owner walked to the far end of the silk.
     */
    public static double longestReach()
    {
        double longest = 0.0D;

        for (SpiderHookTier tier : VALUES)
        {
            longest = Math.max(longest, Math.max(tier.maxRange, tier.tetherDistance()));
        }

        return longest + SEARCH_MARGIN;
    }
}
