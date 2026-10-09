 package spiderqueenhungrycraft.ability;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * What a Spider Queen can never be affected by.
 *
 * <p><b>Two different kinds of immunity live here</b>, and the difference matters (Skill Progression round):
 *
 * <ul>
 * <li>the immunity to <b>darkness</b> is part of her body: it belongs to the night vision of the base abilities, it is
 * granted with the queen flag and it is never bought, so it stays exactly as it was;</li>
 * <li>the immunity to <b>poison</b> and to the <b>status effect of hunger</b> is the visceral enhancement, a bought
 * skill node. A queen who does not own it is poisoned and starved like anybody else, and she is immune to both the
 * moment she buys the node - without a relog, because the effect that is already on her is taken away by the check
 * interval of {@link QueenPhysiology}.</li>
 * </ul>
 *
 * <p><b>The hunger of the hunger bar is not touched by any of this.</b> What is refused here is the potion effect
 * {@code minecraft:hunger} - the one rotten flesh and husks apply - and never the ordinary exhaustion of walking,
 * mining and fighting, and never the forced drain of the queen's own metabolism. Nothing about starvation changes.
 *
 * <p>Both are checked whenever the game asks whether an effect may be applied to the player, which is the one place
 * every source of an effect goes through: a potion, an arrow, a mob, a command and a beacon all ask here.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenImmunity
{
    private QueenImmunity()
    {
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event)
    {
        if (!(event.getEntity() instanceof Player))
        {
            return;
        }

        Player player = (Player) event.getEntity();

        if (isImmune(player, event.getEffectInstance().getEffect()))
        {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * @return true if a Spider Queen can never be affected by the given effect <b>right now</b>.
     *
     *         <p>A player who is not the queen at all is never immune: this is her body and not a gadget.
     */
    public static boolean isImmune(Player player, MobEffect effect)
    {
        if (!QueenDataCapability.isQueen(player))
        {
            return false;
        }

        if (effect == MobEffects.DARKNESS)
        {
            return true;
        }

        if (effect != MobEffects.POISON && effect != MobEffects.HUNGER)
        {
            return false;
        }

        return QueenSkills.has(player, QueenSkills.VISCERAL_ENHANCEMENT);
    }

    /**
     * Takes poison and the status effect of hunger away from a queen who owns the visceral enhancement.
     *
     * <p>It exists for the one moment the refusal above can not cover: an effect that was applied <b>before</b> the
     * node was bought. Without this a newly bought enhancement would look broken until the old poison ran out, and
     * with it the promise holds from the first second on. It removes nothing from a queen without the node, and it
     * never touches the hunger bar.
     */
    public static void clearManagedEffects(Player player)
    {
        if (!QueenSkills.has(player, QueenSkills.VISCERAL_ENHANCEMENT))
        {
            return;
        }

        removeIfPresent(player, MobEffects.POISON);
        removeIfPresent(player, MobEffects.HUNGER);
    }

    private static void removeIfPresent(Player player, MobEffect effect)
    {
        if (player.hasEffect(effect))
        {
            player.removeEffect(effect);
        }
    }
}
