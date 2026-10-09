package spiderqueenhungrycraft.attack;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * Server authoritative empowered melee attack. The client only asks to arm it, the server charges the
 * hunger cost, stores the deadline, and the damage hook consumes the charge on the next melee hit.
 *
 * <p>Hunger is the resource of the Spider Queen, so preparing the attack costs food exactly like a web
 * shot does. Preparing it twice never charges twice.
 */
public final class EmpoweredAttackHandler
{
    /** Hunger cost of preparing one empowered melee attack in survival and adventure. */
    private static final int HUNGER_COST = 2;

    /** Preparing it needs more than this much food. */
    private static final int MIN_FOOD_LEVEL = 2;

    /** Action bar message throttle, in ticks. */
    private static final int STARVING_MESSAGE_COOLDOWN_TICKS = 20;

    /** Player to game time until which the armed attack stays valid. */
    private static final Map<Player, Long> ARMED_UNTIL = new WeakHashMap<>();

    private static final Map<Player, Long> LAST_STARVING_MESSAGE = new WeakHashMap<>();

    private EmpoweredAttackHandler()
    {
    }

    public static void tryArm(ServerPlayer player)
    {
        if (!HungryCraftConfig.empoweredAttackEnabled())
        {
            return;
        }
        if (!player.isAlive() || player.isSpectator())
        {
            return;
        }
        if (!QueenDataCapability.isQueen(player))
        {
            return;
        }

        // The stance of the queen (Skill Progression round): without that node the key means nothing at all. The check
        // sits in front of every line below, so a locked skill costs no hunger, arms nothing, plays nothing and - what
        // matters most - says nothing. This is the server half of the silence; the client half keeps the packet from
        // ever leaving in the first place.
        if (!QueenSkills.has(player, QueenSkills.QUEEN_STANCE))
        {
            return;
        }

        // Never charge twice: a second press only refreshes the feedback.
        if (isArmed(player))
        {
            sendReadyMessage(player);
            return;
        }

        FoodData foodData = player.getFoodData();
        boolean creative = player.isCreative();

        if (!creative && foodData.getFoodLevel() <= MIN_FOOD_LEVEL)
        {
            sendStarvingMessage(player);
            return;
        }

        ARMED_UNTIL.put(player, player.level().getGameTime() + HungryCraftConfig.empoweredAttackWindowTicks());

        if (!creative)
        {
            // Straight food level change, no exhaustion and no saturation buffer.
            foodData.setFoodLevel(Math.max(foodData.getFoodLevel() - HUNGER_COST, 0));
        }

        sendReadyMessage(player);
    }

    /** @return true when this hit is the armed empowered attack, consuming the charge. */
    public static boolean consumeArmed(Player player)
    {
        boolean armed = isArmed(player);
        ARMED_UNTIL.remove(player);

        if (!armed)
        {
            return false;
        }

        // Death, spectator mode, losing the queen flag or losing the stance all invalidate a prepared attack. The last
        // one is what makes a revocation, or an administrator who takes the node back, cancel a charge that was already
        // prepared (Skill Progression round).
        return player.isAlive() && !player.isSpectator() && QueenDataCapability.isQueen(player)
                && QueenSkills.has(player, QueenSkills.QUEEN_STANCE);
    }

    /**
     * @return true when that player has a prepared empowered attack waiting. Read only, nothing is consumed,
     *         so an area attack can ask once and then enhance every one of its victims.
     */
    public static boolean isArmed(Player player)
    {
        // The stance is asked here as well, and not only where the charge is prepared: a charge that was prepared
        // before the node was taken away is worth nothing afterwards, and the beast bite asks this method to decide
        // whether it releases a venom strike at all (Skill Progression round).
        if (!QueenSkills.has(player, QueenSkills.QUEEN_STANCE))
        {
            return false;
        }

        Long until = ARMED_UNTIL.get(player);
        return until != null && until >= player.level().getGameTime();
    }

    /**
     * Throws the prepared attack away without using it, used by an area attack after it handed the venom to
     * every victim of one swing. A prepared attack is always consumed exactly once.
     */
    public static void clearArmed(Player player)
    {
        ARMED_UNTIL.remove(player);
    }

    private static void sendReadyMessage(ServerPlayer player)
    {
        player.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.empowered_ready"), true);
    }

    private static void sendStarvingMessage(ServerPlayer player)
    {
        long now = player.level().getGameTime();
        Long last = LAST_STARVING_MESSAGE.get(player);

        if (last != null && now - last < STARVING_MESSAGE_COOLDOWN_TICKS)
        {
            return;
        }

        LAST_STARVING_MESSAGE.put(player, now);
        player.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.too_hungry"), true);
    }
}
