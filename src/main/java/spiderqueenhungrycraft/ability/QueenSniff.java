package spiderqueenhungrycraft.ability;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * The sniff of the queen (Spider Trigger round follow-up): one press and every living creature within
 * {@link #RADIUS} blocks of her wears the outline of the game for {@link #GLOW_TICKS} ticks.
 *
 * <p>It is the queen's own sense, and it is paid for like every other sense of hers: it costs
 * {@link #HUNGER_COST} points of hunger, it needs more than {@link #MIN_FOOD_LEVEL} of them to be cast at all, and it
 * has a cooldown of {@link #COOLDOWN_TICKS} ticks. A queen who presses the key while starving is told that she is
 * starving; a queen whose skill is still resting is told how many seconds are left; and a player who is not the
 * Spider Queen at all gets nothing, because this is her body and not a gadget.
 *
 * <p><b>The outline is the outline of the game.</b> It is the ordinary glowing effect, which the renderer of a
 * creature draws from its own model - so it follows the creature wherever it goes, is visible through walls and
 * expires on its own, and it is the render layer and not the hit box that is drawn. Everything the queen can see,
 * she sees at once: this is the sense that finds what is hiding behind a wall in the dark, so nothing about it is
 * filtered by line of sight.
 *
 * <p>Server authoritative from beginning to end: the client only asks, and nothing here trusts a word of it.
 */
public final class QueenSniff
{
    /** How far the sniff reaches: sixty four blocks, the reach of the sceptre itself. */
    public static final double RADIUS = 64.0D;

    /** How long the outline stays: five seconds. */
    public static final int GLOW_TICKS = 100;

    /** What one sniff costs from the hunger bar. */
    public static final int HUNGER_COST = 5;

    /** And how much food the queen must have to be able to sniff at all. */
    public static final int MIN_FOOD_LEVEL = 5;

    /** Ten seconds of rest between two sniffs. */
    public static final int COOLDOWN_TICKS = 200;

    /** Player to game time at which the next sniff may be cast. Runtime only, like every cooldown of a body. */
    private static final Map<Player, Long> READY_AT = new WeakHashMap<>();

    private QueenSniff()
    {
    }

    /** One press of the sniff key of that player, in the shape the server decides it. */
    public static void cast(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player))
        {
            // Not a queen, not alive or not really there: nothing happens and nothing is said.
            return;
        }

        // The sense itself is the sniff of the queen (Skill Progression round): a queen who does not own that node
        // gets nothing at all from the key - no hunger, no cooldown, no scan, no outline, no sound and no line. This
        // is the very first check after her body, so a locked key is completely silent on the server as well, and the
        // client half of the silence keeps the request from ever arriving in the first place.
        if (!QueenSkills.has(player, QueenSkills.QUEEN_SNIFF))
        {
            return;
        }

        FoodData food = player.getFoodData();
        boolean creative = player.isCreative();

        // Hunger first: a queen who is starving is told exactly that, whatever else her skill is doing.
        if (!creative && food.getFoodLevel() < MIN_FOOD_LEVEL)
        {
            player.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.too_hungry"), true);

            return;
        }

        long now = player.level().getGameTime();
        Long readyAt = READY_AT.get(player);

        if (readyAt != null && readyAt > now)
        {
            // The rest of the skill is counted down out loud, rounded up, so the queen knows when to press again.
            long seconds = (readyAt - now + 19L) / 20L;
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.sniff_cooldown", seconds), true);

            return;
        }

        if (!creative)
        {
            // Straight food level change, no exhaustion and no saturation buffer - the same purse every other skill
            // of the queen pays from.
            food.setFoodLevel(Math.max(food.getFoodLevel() - HUNGER_COST, 0));
        }

        READY_AT.put(player, now + COOLDOWN_TICKS);

        int found = outlineAround(player);

        // The line says what a body feels and never what a report counts: no number of creatures in it, and one line
        // for a lair that is full of breathing things and one for a place that is dead.
        player.displayClientMessage(Component.translatable(found > 0
                ? "message.spiderqueen_hungrycraft.sniff_cast"
                : "message.spiderqueen_hungrycraft.sniff_empty"), true);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    /** @return how many creatures the queen found: every living thing in the ring, herself included. */
    private static int outlineAround(ServerPlayer player)
    {
        List<LivingEntity> found = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RADIUS));

        for (LivingEntity creature : found)
        {
            creature.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false));
        }

        return found.size();
    }
}
