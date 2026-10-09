package spiderqueenhungrycraft.brood;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The carrier of one collected spider (this round's breeding pass), the mod's own take on the "pick a
 * creature up and put it down again" idea of Modular Golems.
 *
 * <p>One carrier is one spider, so it is never stackable. It remembers the species, the health, the custom name and the
 * owner of the spider that was picked up, plus the two things the class that owns them hands over beside that: the
 * veterancy (Brood Veterancy round) and the <b>gear</b> (Carrier Gear round) - the armour, the shield, the blade, the bow
 * and the apples of a body that owns any. It still stores no entity UUID, no position and no motion. Deploying it
 * therefore never teleports a spider and never heals one - a spider that was picked up with three of ten health is put
 * down with three of ten health.
 *
 * <p>An empty carrier is not an item of the game: a carrier only ever comes into existence by picking a
 * spider up, so it has no recipe and is deliberately absent from the creative tab.
 */
public class SpiderCarrierItem extends Item
{
    /**
     * The tag of the gear of the carried spider (Carrier Gear round). It is written by the spider itself through
     * {@link SpiderMinionEntity#writeCarriedGear} and read back through {@link SpiderMinionEntity#readCarriedGear}, so
     * the carrier never has to know what gear a species owns - it only has to carry it.
     */
    public static final String KEY_GEAR = "SpiderGear";

    public SpiderCarrierItem(Item.Properties properties)
    {
        super(properties);
    }

    /** @return a filled carrier of that spider, ready to be handed to a player. */
    public static ItemStack of(SpiderMinionEntity spider)
    {
        ItemStack stack = new ItemStack(HungryCraftItems.SPIDER_CARRIER.get());
        SpiderCarrierData.capture(spider).writeTo(stack);

        // The veterancy rides along as its own tag (Brood Veterancy round): a veteran that is packed away is still a
        // veteran when it is unpacked. It is kept beside the carrier data and not inside it, because the data class of
        // the carrier knows nothing about ranks - and neither it has to.
        stack.getOrCreateTag().put(SpiderVeterancyData.KEY_VETERANCY, spider.writeVeterancyTag());

        // The gear rides along the same way (Carrier Gear round): armour, shield, blade, bow and apples are written by
        // the spider, because only the species knows what its own gear is. A packed soldier used to come back naked.
        CompoundTag gear = new CompoundTag();
        spider.writeCarriedGear(gear);

        if (!gear.isEmpty())
        {
            stack.getOrCreateTag().put(KEY_GEAR, gear);

            // Only a species that really owns gear is worth a line, and that is a player action, not a tick: a queen
            // who packs a soldier away can read in the log that the sword went with it.
            SpiderQueenHungryCraft.LOGGER.info("[carrier] packed {} {} with {} keys of gear ({} health)",
                    spider.getType().toShortString(), spider.getStringUUID(), gear.size(), spider.getHealth());
        }

        return stack;
    }

    /** @return true when this carrier kept the gear of its spider, so the tooltip of the bag can say so. */
    public static boolean carriesGear(ItemStack stack)
    {
        return stack.hasTag() && !stack.getTag().getCompound(KEY_GEAR).isEmpty();
    }

    /** The carrier wears the name of the spider it holds, so a named spider keeps its name in the bag. */
    @Override
    public Component getName(ItemStack stack)
    {
        SpiderCarrierData data = SpiderCarrierData.from(stack);

        return data == null ? super.getName(stack) : data.getDisplayName();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        SpiderCarrierData data = SpiderCarrierData.from(stack);

        if (data == null)
        {
            tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.spider_carrier.empty")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }

        // Only worth a line when the name of the spider hides the species.
        if (data.getCustomName() != null)
        {
            tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.spider_carrier.species",
                    data.getSpeciesName()).withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.spider_carrier.health",
                Mth.ceil(data.getHealth()), Mth.ceil(data.getMaxHealth())).withStyle(ChatFormatting.GRAY));

        // The gear is a line of its own: "the soldier came back with its sword" is the first thing a queen checks, and
        // it is the one thing a carrier used to lose without ever saying so (Carrier Gear round).
        if (carriesGear(stack))
        {
            tooltip.add(Component.translatable("tooltip.spiderqueen_hungrycraft.spider_carrier.gear")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Deploys the spider on the clicked ground. The spider is only created when it really fits there, and
     * the carrier is only used up once it really arrived - a refused deployment costs nothing.
     */
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
        SpiderCarrierData data = SpiderCarrierData.from(stack);

        if (data == null)
        {
            if (!level.isClientSide)
            {
                player.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.spider_carrier_invalid"), true);
            }

            return InteractionResult.FAIL;
        }

        if (level.isClientSide)
        {
            return InteractionResult.SUCCESS;
        }

        Entity created = create(level, data);

        if (!(created instanceof SpiderMinionEntity spider))
        {
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.spider_carrier_invalid"), true);
            return InteractionResult.FAIL;
        }

        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        spider.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot(), 0.0F);

        // The queen who puts the spider down is its queen from now on (Brood Command AI): a carrier can be handed to
        // somebody else, and the spider then belongs to whoever deployed it. The order of a freshly deployed spider is
        // always FOLLOW - a fight or a post is not part of a carried spider.
        spider.setOwnerUUID(player.getUUID());
        spider.setOrderMode(SpiderOrderMode.FOLLOW);

        Component name = data.getCustomName();

        if (name != null)
        {
            spider.setCustomName(name);
        }

        // The wounds of the spider are kept, but a carrier can never heal beyond its own maximum.
        // The veterancy is put back first (Brood Veterancy round), because it is what decides that maximum: a veteran
        // that is unpacked is a veteran again before its three of ten health are read back onto it.
        spider.readVeterancyTag(stack.getTag() == null ? null : stack.getTag().getCompound(
                SpiderVeterancyData.KEY_VETERANCY));

        // Then the gear comes back, and before the health (Carrier Gear round): gear is part of what the maximum health
        // is made of - a body in its armour can take more than the same body without it - and a wound that was clamped
        // against a smaller maximum would be lost for good. A spider that leaves the carrier naked is a spider whose
        // sword is gone, and this is where that used to happen.
        CompoundTag gear = stack.getTag() == null ? null : stack.getTag().getCompound(KEY_GEAR);

        if (gear != null && !gear.isEmpty())
        {
            spider.readCarriedGear(gear);

            SpiderQueenHungryCraft.LOGGER.info("[carrier] deployed {} {} with {} keys of gear restored",
                    spider.getType().toShortString(), spider.getStringUUID(), gear.size());
        }

        spider.setHealth(Mth.clamp(data.getHealth(), 1.0F, (float) spider.getMaxHealth()));

        // The rest of its ability is kept as well (Brood Ability framework): packing a spider away is not a way to get
        // a second rage for free. The buffs themselves are not carried - a packed spider simply has none.
        spider.setAbilityCooldownRemaining(data.getAbilityCooldownTicks());

        // It is put down exactly where the queen clicked, and the world is not asked whether it likes that: a carrier is
        // a deliberate act of the player, who can see the ground she aims at. No completeness check of the block and no
        // room check of the box - if the spider lands tight, that is her call to make.
        level.addFreshEntity(spider);
        level.playSound(null, spider.getX(), spider.getY(), spider.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.NEUTRAL, 0.8F, 0.9F);

        if (!player.isCreative())
        {
            stack.shrink(1);
        }

        return InteractionResult.sidedSuccess(false);
    }

    /** @return a real spider of that species, or null when the stored species is not a spider of this mod. */
    @Nullable
    private static Entity create(Level level, SpiderCarrierData data)
    {
        ResourceLocation typeId = data.getTypeId();
        EntityType<?> type = EntityType.byString(typeId.toString()).orElse(null);

        if (type == null)
        {
            return null;
        }

        Entity created = type.create(level);

        // A carrier only ever holds a spider of this mod (Brood Ability framework): the species in the NBT of an item can
        // be edited, and no edited carrier may turn into an arbitrary creature of the game.
        //
        // The test is the <b>class</b> of the creature that was really created, not a list of registry ids. That list is
        // what made this bug: it was written when the brood had three species and was never told about the four that
        // came later, so a carrier of a skeleton, chrono, stray or husk spider was called invalid and could never be put
        // down again. The class cannot be forgotten: every species of this mod is a SpiderMinionEntity, today and later.
        if (created instanceof SpiderMinionEntity spider)
        {
            return spider;
        }

        if (created != null)
        {
            // Something else answered to that id: it is not ours, so it is dropped without ever entering the world.
            created.discard();
        }

        return null;
    }
}
