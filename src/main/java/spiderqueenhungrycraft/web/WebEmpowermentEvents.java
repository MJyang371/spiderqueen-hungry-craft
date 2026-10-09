package spiderqueenhungrycraft.web;

import com.mojang.datafixers.util.Pair;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * How the queen gains a web empowerment (Phase 7A): a poisonous meal, a poisonous potion or a piece of
 * blaze powder. Only a real consumption counts, and only for the Spider Queen, everybody else keeps the
 * untouched vanilla behaviour of the item.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class WebEmpowermentEvents
{
    /** A small burst of flame while the blaze powder is taken in. */
    private static final int BLAZE_POWDER_PARTICLES = 5;

    private static final double BLAZE_POWDER_SPREAD = 0.3D;
    private static final double BLAZE_POWDER_SPEED = 0.02D;

    /** How often a running empowerment shows one particle, in ticks. */
    private static final int EMPOWERMENT_PARTICLE_INTERVAL_TICKS = 15;

    private WebEmpowermentEvents()
    {
    }

    /** Eating or drinking something poisonous empowers the poison web. */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !QueenDataCapability.isQueen(player))
        {
            return;
        }

        if (isPoisonIntake(event.getItem(), player))
        {
            WebEmpowerment.grant(player, WebElement.POISON);
        }
    }

    /**
     * Blaze powder is an elemental material the Spider Queen can take in. It is a normal vanilla item, so
     * nothing is registered or replaced: a non queen right clicking it keeps the vanilla behaviour.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide())
        {
            return;
        }

        if (!QueenDataCapability.isQueen(player) || !event.getItemStack().is(Items.BLAZE_POWDER))
        {
            return;
        }

        WebEmpowerment.grant(player, WebElement.FIRE);

        if (!player.isCreative())
        {
            event.getItemStack().shrink(1);
        }

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        ((ServerLevel) player.level()).sendParticles(ParticleTypes.FLAME, player.getX(),
                player.getY() + 1.0D, player.getZ(), BLAZE_POWDER_PARTICLES, BLAZE_POWDER_SPREAD,
                BLAZE_POWDER_SPREAD, BLAZE_POWDER_SPREAD, BLAZE_POWDER_SPEED);
        player.swing(event.getHand(), true);

        // No hunger, no saturation: this is an elemental material, not a meal.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** BONUS B: a rare particle of the running empowerment, one particle per interval and player. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
        {
            return;
        }

        if (event.player.tickCount % EMPOWERMENT_PARTICLE_INTERVAL_TICKS != 0)
        {
            return;
        }

        WebElement element = WebEmpowerment.current(event.player);

        if (!element.isElemental())
        {
            return;
        }

        ((ServerLevel) event.player.level()).sendParticles(element.particle(), event.player.getX(),
                event.player.getY() + 1.2D, event.player.getZ(), 1, BLAZE_POWDER_SPREAD, 0.4D,
                BLAZE_POWDER_SPREAD, 0.0D);
    }

    /**
     * @return true when eating or drinking that stack counts as taking in poison. The rule is read from the
     *         consumed item, never from the effect the player ends up with: the poison immunity of the queen
     *         must not stop the empowerment.
     */
    private static boolean isPoisonIntake(ItemStack stack, ServerPlayer player)
    {
        if (stack.is(WebEmpowerment.POISON_WEB_TRIGGER_FOODS))
        {
            return true;
        }

        FoodProperties food = stack.getFoodProperties(player);

        if (food != null)
        {
            for (Pair<MobEffectInstance, Float> pair : food.getEffects())
            {
                if (pair.getFirst().getEffect() == MobEffects.POISON)
                {
                    return true;
                }
            }
        }

        // Only a potion the player really drank counts, splash and lingering potions are thrown instead.
        if (stack.is(Items.POTION))
        {
            for (MobEffectInstance effect : PotionUtils.getMobEffects(stack))
            {
                if (effect.getEffect() == MobEffects.POISON)
                {
                    return true;
                }
            }
        }

        return false;
    }
}
