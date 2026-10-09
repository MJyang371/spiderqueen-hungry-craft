package spiderqueenhungrycraft.hook;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The spider hook rod (feature SPIDER-HOOK): a weapon that is held, never thrown.
 *
 * <p>Right clicking fires {@link SpiderHookEntity}, a web that travels on its own and catches the first block
 * or creature it touches. Right clicking again either takes a flying web back, starts to pull the player to a
 * block it hangs on, or starts to drag a creature it caught - and a click while one of those runs reels the web
 * in on the spot, which is the way out of a pull that got caught on a corner of the world. Leaving the rod out
 * of the hand does the very same thing, so the pull can also be triggered by a plain item switch - exactly the
 * catalogue of a fishing rod: cast, retrieve, cast again. That check lives in {@link SpiderHookEntity}: the web
 * knows which rod fired it and takes itself back the moment that very rod leaves the hand, whatever the player
 * holds instead.
 *
 * <p>The rod never leaves the hand, but it does wear out like any tool of the game. Three rules carry that:
 * a cast that really hung on a block is worth one use, a web that missed costs nothing; the rod takes a break
 * once the casts of its quality are used up; and a pull that dragged its owner costs durability according to
 * how far it really dragged him. All of it lives in {@link SpiderHookTier} and is backed by the configuration
 * of the mod, so a pack author can move the numbers without touching this class.
 */
public class SpiderHookItem extends Item
{
    /**
     * The mark of the cast written into the rod while its web is out. It ties this very rod to exactly one
     * flying web, which is what lets the web tell a switched rod from the rod that fired it.
     */
    private static final String TAG_CAST = "HungryCraftHookCast";

    /** How many casts of the current run are already made. */
    private static final String TAG_USES = "HungryCraftHookUses";

    /** The tick of the last counted cast, which is what a run is measured against. */
    private static final String TAG_LAST_CAST = "HungryCraftHookLastCast";

    /** A run only counts as a run while the casts follow each other inside this window. */
    private static final long COMBO_WINDOW_TICKS = 100L;

    /**
     * Hands out one mark per cast. Zero means "this rod has no web out", so it is never handed out, and the
     * very first mark of a session is drawn from a wide random range: a rod that still carries the mark of
     * a cast from before a restart can never be mistaken for the rod of a new one.
     */
    private static final AtomicLong NEXT_CAST = new AtomicLong(
            1L + ThreadLocalRandom.current().nextLong(1L << 40));

    private final SpiderHookTier tier;

    public SpiderHookItem(SpiderHookTier tier, Item.Properties properties)
    {
        super(properties);
        this.tier = tier;
    }

    /** @return the quality of this rod. */
    public SpiderHookTier tier()
    {
        return this.tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        // The client only reports the click, the server decides what it means. A rod that is taking its
        // break may neither fire nor play a sound, but it can always take a hanging web back and pull.
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer)
        {
            this.useHook(serverPlayer, stack, hand);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * The one right click behaviour of the rod: with no web out it fires, with a web out it takes the web back,
     * pulls the player in, starts to drag a caught being or lets go of a running pull - the state of the web
     * decides, never the item.
     */
    private void useHook(ServerPlayer player, ItemStack stack, InteractionHand hand)
    {
        SpiderHookEntity hook = SpiderHookEntity.findOwned(player);

        if (hook != null)
        {
            if (hook.isGrabbing() || hook.isPulling())
            {
                // A click while the web drags reels it in on the spot - a pull that got caught on a corner of
                // the world just as much as a caught being. It counts as the one use a finished cast is worth,
                // so the rod can be forced free and then take its break.
                hook.retractFromPull();
            }
            else if (hook.isHookedToEntity())
            {
                // A click on a web that holds a creature starts the drag: who is moved is decided by the being.
                hook.requestEntityDrag();
            }
            else if (hook.isAttached())
            {
                hook.requestPull();
            }
            else
            {
                // A web that is still in the air is simply taken back.
                hook.recall();
            }

            player.swing(hand, true);
            return;
        }

        if (player.getCooldowns().isOnCooldown(this))
        {
            // The rod is still taking its break. The server decides this, so no client can fire through it.
            return;
        }

        this.fire(player, stack, hand);
    }

    /**
     * Throws the web head, which is the only part of the rod that ever leaves the hand. It leaves from
     * {@link HookOrigin}, the very point the silk is drawn from, so the web starts at the rod tip instead of
     * somewhere in front of the eyes.
     *
     * <p>Firing costs nothing: the cast is written into the rod, so the web can be traced back to it, and
     * the use is only counted later, once the web comes home after it really hung on a block.
     */
    private void fire(ServerPlayer player, ItemStack stack, InteractionHand hand)
    {
        Vec3 look = player.getLookAngle();
        Vec3 tip = HookOrigin.rodTip(player, 1.0F);

        SpiderHookEntity hook = new SpiderHookEntity(player.level(), player, this.tier);
        hook.setCastId(NEXT_CAST.getAndIncrement());
        hook.setPos(tip.x, tip.y, tip.z);
        hook.shoot(look.x, look.y, look.z, SpiderHookEntity.LAUNCH_SPEED, 0.0F);
        player.level().addFreshEntity(hook);

        markCast(stack, hook.castId());

        player.swing(hand, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.8F,
                1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));
    }

    /**
     * One finished cast is one use. A rod only takes a limited number of casts in a row and then needs a
     * break: the same idea as a cooldown, but with a counter behind it. Its length is one number for all five
     * rods ({@link SpiderHookTier#breakTicks()}), so it can be tuned by a pack author. It is only ever called
     * by {@link #endCast} for a web that really hung on a block. Creative mode never counts, exactly like the
     * rest of the mod never charges a creative player.
     */
    private void countUse(ServerPlayer player, ItemStack stack)
    {
        if (player.isCreative())
        {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();
        long now = player.level().getGameTime();
        long last = tag.getLong(TAG_LAST_CAST);

        int uses = last > 0L && now - last <= COMBO_WINDOW_TICKS ? tag.getInt(TAG_USES) : 0;
        tag.putLong(TAG_LAST_CAST, now);

        if (uses + 1 >= this.tier.usesBeforeCooldown())
        {
            tag.putInt(TAG_USES, 0);
            player.getCooldowns().addCooldown(this, SpiderHookTier.breakTicks());
            return;
        }

        tag.putInt(TAG_USES, uses + 1);
    }

    /**
     * The cast is over and the web is home: this is the one place a cast is counted, and only if the web had
     * really hung on a block. A throw that missed, a web that was taken back in the air, a web that was
     * recycled because its anchor was gone - none of them cost a use, exactly as the design asks.
     *
     * <p>It is called by the web itself when it comes home, so the rod does not even have to be in the hand
     * when the pull ends: a cast that was started by this rod is charged to this rod, wherever it lies.
     *
     * @param hit true while the web had attached to a block at least once.
     * @param travelledBlocks how far that web dragged its owner while it pulled: that is what the silk is worn
     *        by. Zero for a web that was never pulled, and then the cast costs no durability at all.
     */
    public static void endCast(ServerPlayer player, long castId, SpiderHookTier tier, boolean hit,
            double travelledBlocks)
    {
        ItemStack stack = findCastStack(player, castId, tier);

        if (stack == null)
        {
            return;
        }

        clearCast(stack);

        if (stack.getItem() instanceof SpiderHookItem rod)
        {
            if (hit)
            {
                rod.countUse(player, stack);
            }

            rod.wearSilk(player, stack, travelledBlocks);
        }
    }

    /**
     * @return true while the very rod that fired that web is still in one of the hands of the player. Only
     *         that exact rod counts, so switching from one hook to the next one is a plain item switch too
     *         and takes the web back like any other change of the hand.
     */
    public static boolean isCastHeld(Player player, long castId)
    {
        return castId != 0L
                && (castId(player.getMainHandItem()) == castId || castId(player.getOffhandItem()) == castId);
    }

    /** @return the mark of the cast on that rod, or zero while no web of it is out. */
    public static long castId(@Nullable ItemStack stack)
    {
        CompoundTag tag = stack == null ? null : stack.getTag();

        return tag == null ? 0L : tag.getLong(TAG_CAST);
    }

    /** Writes the mark of a cast into the rod, so the web and the rod know about each other. */
    private static void markCast(ItemStack stack, long castId)
    {
        stack.getOrCreateTag().putLong(TAG_CAST, castId);
    }

    /**
     * The silk of a rod is worn by what it drags: the wear of a finished pull is the distance the owner was
     * dragged, divided by how many blocks of dragging this quality takes per point of durability. A cast that
     * never hung on a block, and one that hung without ever pulling, cost nothing at all.
     *
     * <p>The wear goes through {@link ItemStack#hurtAndBreak}, so Unbreaking, the durability bar, the break
     * itself and Mending behave exactly as they do on any other tool of the game. A creative player never
     * pays, like everywhere else in this mod.
     */
    private void wearSilk(ServerPlayer player, ItemStack stack, double travelledBlocks)
    {
        if (player.isCreative() || !HungryCraftConfig.hookDurabilityEnabled() || travelledBlocks <= 0.0D)
        {
            return;
        }

        int damage = this.tier.damageFor(travelledBlocks);

        if (damage <= 0)
        {
            return;
        }

        // The break and the wear speak through the sound of the rod and the bar of the stack, never through a
        // line of text at the bottom of the screen.
        stack.hurtAndBreak(damage, player, broken -> {
        });
    }

    /**
     * @return the durability of this rod in the eyes of the game. It is read from the configuration instead of
     *         the value the item was registered with, so a pack author can move it without a new build. The
     *         plain {@code getMaxDamage()} of vanilla is final, which is exactly why this stack sensitive
     *         version of Forge exists.
     */
    @Override
    public int getMaxDamage(ItemStack stack)
    {
        return this.tier.maxDurability();
    }

    /** An anvil repairs a rod with the material of its own quality: silk for the plain one, ingots above it. */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair)
    {
        return !repair.isEmpty() && repair.is(this.tier.repairItem());
    }

    /** @return the enchantability of the quality, so the table offers a rod like the material it is made of. */
    @Override
    public int getEnchantmentValue()
    {
        return this.tier.enchantability();
    }

    /** Frees the rod again: it may fire the moment the web is home. */
    private static void clearCast(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();

        if (tag != null)
        {
            tag.remove(TAG_CAST);
        }
    }

    /**
     * @return the rod a finished cast belongs to: first the rod that carries that very mark, and if it is
     *         not around any more (dropped, stored away) the first rod of the same quality, because that is
     *         the quality the web was fired with.
     */
    @Nullable
    private static ItemStack findCastStack(ServerPlayer player, long castId, SpiderHookTier tier)
    {
        for (ItemStack stack : player.getInventory().items)
        {
            if (castId(stack) == castId)
            {
                return stack;
            }
        }

        for (ItemStack stack : player.getInventory().offhand)
        {
            if (castId(stack) == castId)
            {
                return stack;
            }
        }

        for (ItemStack stack : player.getInventory().items)
        {
            if (stack.getItem() instanceof SpiderHookItem rod && rod.tier() == tier)
            {
                return stack;
            }
        }

        return null;
    }
}
