package spiderqueenhungrycraft.ability;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * Hunger metabolism of the Spider Queen: constant hunger burn, starvation death in every difficulty
 * and slow regeneration while fed. All of it only runs on the server, never per tick, and only while
 * the player is the queen.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenMetabolism
{
    /** 30 seconds. */
    private static final int HUNGER_DRAIN_INTERVAL_TICKS = 600;

    private static final int STARVATION_INTERVAL_TICKS = 80;
    private static final float STARVATION_DAMAGE = 1.0F;

    private static final int REGENERATION_INTERVAL_TICKS = 80;
    private static final float REGENERATION_AMOUNT = 1.0F;
    private static final int REGENERATION_MIN_FOOD_LEVEL = 10;

    private static final int MAX_FOOD_LEVEL = 20;

    /** In peaceful mode the game refills the hunger bar with one point every 10 ticks. */
    private static final int PEACEFUL_FOOD_REFILL_INTERVAL_TICKS = 10;

    private QueenMetabolism()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide())
        {
            return;
        }
        if (!QueenDataCapability.isQueen(player) || player.isCreative() || player.isSpectator() || !player.isAlive())
        {
            return;
        }

        int tick = player.tickCount;
        undoPeacefulFoodRefill(player, tick);

        if (tick > 0 && tick % HUNGER_DRAIN_INTERVAL_TICKS == 0)
        {
            drainHunger(player);
        }
        if (tick > 0 && tick % STARVATION_INTERVAL_TICKS == 0)
        {
            applyStarvationDamage(player);
        }
        if (tick > 0 && tick % REGENERATION_INTERVAL_TICKS == 0)
        {
            regenerate(player);
        }
    }

    /**
     * Peaceful mode heals one health point every 20 ticks as long as the player is hurt. Without this
     * the queen could never starve to death, because that recovery is faster than the starvation damage.
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event)
    {
        if (!(event.getEntity() instanceof Player))
        {
            return;
        }

        Player player = (Player) event.getEntity();
        if (!QueenDataCapability.isQueen(player) || !isStarving(player) || !hasPeacefulRecovery(player))
        {
            return;
        }

        event.setCanceled(true);
    }

    /** @return true while the queen has no food left. */
    public static boolean isStarving(Player player)
    {
        return player.getFoodData().getFoodLevel() <= 0;
    }

    private static void drainHunger(Player player)
    {
        FoodData foodData = player.getFoodData();

        // The beast form eats more, but it never adds up: the beast amount replaces the ordinary amount, so a
        // transformed queen burns three food per cycle instead of the ordinary two, never five.
        int amount = QueenBeastState.isTransformed(player)
                ? QueenBeastConstants.BEAST_METABOLISM_FOOD
                : QueenBeastConstants.NORMAL_METABOLISM_FOOD;

        // Deliberately bypasses exhaustion and saturation: moving or not, peaceful or hard, the queen
        // always burns hunger.
        foodData.setFoodLevel(Math.max(foodData.getFoodLevel() - amount, 0));
    }

    private static void applyStarvationDamage(Player player)
    {
        if (!isStarving(player))
        {
            return;
        }

        // Vanilla starvation damage is gated behind difficulty and current health, the queen's is not.
        player.hurt(player.damageSources().starve(), STARVATION_DAMAGE);
    }

    private static void regenerate(Player player)
    {
        // A beast has its own, much faster regeneration that works from any food level above zero, so the
        // ordinary regeneration of the queen stands aside completely while the beast form is active.
        if (QueenBeastState.isTransformed(player))
        {
            return;
        }

        if (player.getFoodData().getFoodLevel() <= REGENERATION_MIN_FOOD_LEVEL)
        {
            return;
        }
        if (player.getHealth() >= player.getMaxHealth())
        {
            return;
        }

        player.heal(REGENERATION_AMOUNT);
    }

    /**
     * Peaceful mode refills the hunger bar with one point every 10 ticks, which would cancel the forced
     * drain completely and the queen would never starve there. This takes exactly that point back right
     * after the game added it, so eating and every other food source still work normally.
     */
    private static void undoPeacefulFoodRefill(Player player, int tick)
    {
        if (tick % PEACEFUL_FOOD_REFILL_INTERVAL_TICKS != 0 || !hasPeacefulRecovery(player))
        {
            return;
        }

        FoodData foodData = player.getFoodData();
        int before = foodData.getLastFoodLevel();
        if (before >= MAX_FOOD_LEVEL || foodData.getFoodLevel() != before + 1)
        {
            return;
        }

        foodData.setFoodLevel(before);
    }

    private static boolean hasPeacefulRecovery(Player player)
    {
        return player.level().getDifficulty() == Difficulty.PEACEFUL
                && player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
    }
}
