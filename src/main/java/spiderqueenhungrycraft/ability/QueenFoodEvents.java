package spiderqueenhungrycraft.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * Food physiology of the Spider Queen (Phase 9D-MICRO).
 *
 * <p>The queen digests living creatures whole, so a piece of rotten flesh is simply food to her: she keeps every
 * benefit of it and loses the one drawback vanilla puts on spoiled meat, the hunger effect.
 *
 * <p><b>What this is not.</b> This is <b>not</b> a general hunger immunity. Only the single hunger effect that rotten
 * flesh itself carries is taken back, and only on that one meal. Hunger from a potion, a splash or lingering bottle, a
 * husk blow, a command, another mod, a future faction skill or any other food keeps working exactly as vanilla
 * does - the listener never touches the state of a player who was not eating rotten flesh at that very moment.
 *
 * <p><b>Since the Skill Progression round it belongs to the visceral enhancement.</b> The whole of this class is one
 * more way the queen shrugs off the status effect of hunger, so a queen who does not own that node eats her rotten
 * flesh like anybody else and keeps its hunger: the check is the first line of the meal, and it is the same node the
 * refusal of {@link QueenImmunity} reads. Nothing else about the digestion of the queen changed.
 *
 * <p><b>Timing.</b> {@code LivingEntity.completeUsingItem()} passes {@code this.useItem.finishUsingItem(...)} as an
 * argument to {@code ForgeEventFactory.onItemUseFinish(...)}, so the vanilla food and its effects are applied
 * <b>before</b> {@link LivingEntityUseItemEvent.Finish} is posted. The roll back therefore happens right inside the
 * finish handler and no delayed restore of a later tick is needed.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenFoodEvents
{
    /** Duration of the hunger effect a piece of rotten flesh carries, in ticks. */
    private static final int ROTTEN_FLESH_HUNGER_DURATION_TICKS = 600;

    /**
     * The vanilla effect is created freshly for the meal, so its duration is still the full value when the finish
     * event arrives. A few ticks of slack cover a meal that ended a tick late, and the check still tells it apart
     * from a hunger that a husk gave the queen earlier.
     */
    private static final int FRESH_EFFECT_TOLERANCE_TICKS = 5;

    /** The hunger state of every player that is currently chewing, server side only. */
    private static final Map<UUID, Snapshot> CHEWING = new HashMap<>();

    private QueenFoodEvents()
    {
    }

    /** Remembers the hunger of the queen from before the very first bite of a piece of rotten flesh. */
    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event)
    {
        if (!isQueenEatingRottenFlesh(event.getEntity(), event.getItem()))
        {
            return;
        }

        MobEffectInstance hunger = event.getEntity().getEffect(MobEffects.HUNGER);

        CHEWING.put(event.getEntity().getUUID(),
                new Snapshot(event.getItem().copy(), hunger == null ? null : new MobEffectInstance(hunger)));
    }

    /** A meal that was interrupted is not a meal: the note is dropped again. */
    @SubscribeEvent
    public static void onUseItemStop(LivingEntityUseItemEvent.Stop event)
    {
        if (event.getEntity() instanceof Player player)
        {
            CHEWING.remove(player.getUUID());
        }
    }

    /**
     * Takes back the hunger of the piece that was just eaten, and only that.
     *
     * <p>The item is read from the note taken at the start of the meal and not from the event: on
     * {@link LivingEntityUseItemEvent.Finish} the {@code getItem()} accessor of Forge hands back the leftover stack
     * of the use, not the consumed one.
     */
    @SubscribeEvent
    public static void onUseItemFinish(LivingEntityUseItemEvent.Finish event)
    {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide())
        {
            return;
        }

        Snapshot snapshot = CHEWING.remove(player.getUUID());

        // The queen flag is read again here on purpose: a player who lost it in the middle of the meal (or who never
        // had it) eats like everybody else, and the stale note is dropped either way. The visceral enhancement is read
        // with it, so a node that was taken back mid meal changes nothing either (Skill Progression round).
        if (snapshot == null || !snapshot.item().is(Items.ROTTEN_FLESH) || !QueenDataCapability.isQueen(player)
                || !QueenSkills.has(player, QueenSkills.VISCERAL_ENHANCEMENT))
        {
            return;
        }

        rollBackRottenFleshHunger(player, snapshot.hungerBefore());
    }

    /**
     * Removes exactly the hunger that this piece of rotten flesh added.
     *
     * <p>Without hunger before the meal, a freshly created rotten flesh hunger is removed while a hunger that
     * clearly came from somewhere else is left alone. With hunger before the meal the old state is put back - but
     * only when the piece really made it stronger or longer, so a husk that hit the queen while she was chewing
     * keeps its own effect.
     */
    private static void rollBackRottenFleshHunger(Player player, @Nullable MobEffectInstance hungerBefore)
    {
        MobEffectInstance hungerNow = player.getEffect(MobEffects.HUNGER);

        if (hungerNow == null)
        {
            // The one in five case: this piece did not carry any hunger at all.
            return;
        }

        if (hungerBefore == null)
        {
            if (isFreshRottenFleshHunger(hungerNow))
            {
                player.removeEffect(MobEffects.HUNGER);
            }

            return;
        }

        if (hungerNow.getAmplifier() > hungerBefore.getAmplifier()
                || hungerNow.getDuration() > hungerBefore.getDuration())
        {
            player.removeEffect(MobEffects.HUNGER);
            player.addEffect(new MobEffectInstance(hungerBefore));
        }
    }

    /** @return true for the effect a piece of rotten flesh brings: plain, not ambient and freshly full length. */
    private static boolean isFreshRottenFleshHunger(MobEffectInstance hunger)
    {
        return hunger.getAmplifier() == 0 && !hunger.isAmbient()
                && hunger.getDuration() >= ROTTEN_FLESH_HUNGER_DURATION_TICKS - FRESH_EFFECT_TOLERANCE_TICKS;
    }

    /** @return true when that is a Spider Queen who owns the visceral enhancement, eating vanilla rotten flesh. */
    private static boolean isQueenEatingRottenFlesh(LivingEntity entity, ItemStack stack)
    {
        return entity instanceof Player player && !player.level().isClientSide() && stack.is(Items.ROTTEN_FLESH)
                && QueenDataCapability.isQueen(player)
                && QueenSkills.has(player, QueenSkills.VISCERAL_ENHANCEMENT);
    }

    /** One meal in progress: what is being eaten and the hunger state from before the first bite. */
    private record Snapshot(ItemStack item, @Nullable MobEffectInstance hungerBefore)
    {
    }
}
