package spiderqueenhungrycraft.compat.iceandfire;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The Forge events of a dragon under a contract (Necromancy IV round): what may be done to it, and who may hurt whom.
 *
 * <p>Two families of handler, and both of them are deliberately <b>event</b> handlers and not mixins: the round forbids
 * a hook into {@code isOwnedBy} of Ice and Fire, because that one method guards the command staff, the meals, the
 * inventory, the armour, the banner and the riding all at once - answering "yes" there would hand the whole tame menu
 * of that mod to whoever holds a contract. So the two things a contract really has to change are taken one at a time:
 *
 * <ul>
 * <li><b>the interactions</b> ({@link PlayerInteractEvent.EntityInteract} and
 * {@link PlayerInteractEvent.EntityInteractSpecific}, the two halves of a right click of the game): each one is handed
 * to the compatibility, which blocks the equipment and keeps the two permissions the round grants - riding from stage
 * three, and meat, both for the player who holds the contract alone,</li>
 * <li><b>the damage</b> ({@link LivingAttackEvent}): a claw, a bite, a dragon breath, an explosion and a projectile of
 * a contracted dragon are all cancelled against the side of its own contract, and so is every blow from that side
 * against the skeleton. The targeting goal already stops the AI from picking a friend; this is what stops a breath that
 * was aimed at something else from burning the whole brood standing around it.</li>
 * </ul>
 *
 * <p>Nothing here names an Ice and Fire type: the handlers speak vanilla and hand the work to the facade, so a game
 * without that mod loads this class, asks {@link IceAndFireSupport#present()} once per event and returns. The one kind
 * of data this file does know is the contract itself, which is ordinary persistent data of an entity.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class NecromancyProtectionEvents
{
    private NecromancyProtectionEvents()
    {
    }

    /** A right click on a creature: the contract of a dragon decides whether it means anything. */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event)
    {
        guard(event, event.getTarget(), null);
    }

    /**
     * The same click, on a specific point of a model.
     *
     * <p>It is the path a multipart entity takes - a wing, a leg or a jaw of a dragon is an entity of its own - so the
     * compatibility is asked again here and looks through the part to the animal behind it. Without this handler a
     * click on the wing of a dragon could equip the armour that the same click on its body refuses.
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event)
    {
        guard(event, event.getTarget(), event.getLocalPos());
    }

    /** @return the animal behind a hit on one of its parts, so that a click on a wing is a click on the dragon. */
    private static Entity parentOf(Entity target)
    {
        return target instanceof PartEntity<?> part && part.getParent() != null ? part.getParent() : target;
    }

    /**
     * Hands one interaction to the contract, and cancels the event when the contract really decided it.
     *
     * <p><b>The server decides, and the client only avoids a wrong picture.</b> A right click is predicted on the client
     * before its packet reaches the server, so the client refuses the same equipment clicks as well - but a contract is
     * never written into the persistent data of a client (that data never crosses the wire), so what a client goes by
     * is the mirror of woken dragons it was told about (see {@link ClientNecromancyState}). The two permissions - the
     * ride and the meat - are deliberately <b>not</b> predicted there: feeding is consuming, and consuming twice is
     * worse than one frame of a picture that the answer of the server corrects.
     */
    private static void guard(PlayerInteractEvent event, Entity target, @Nullable Vec3 localHit)
    {
        Player player = event.getEntity();

        if (player instanceof ServerPlayer serverPlayer)
        {
            if (IceAndFireSupport.guardInteraction(serverPlayer, event.getHand(), target, localHit))
            {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }

            return;
        }

        if (!ClientNecromancyState.isRevived(parentOf(target).getId()))
        {
            return;
        }

        ItemStack held = event.getItemStack();

        if (IceAndFireSupport.blocksInteraction(held) || held.isEmpty() && player.isShiftKeyDown())
        {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * Who may hurt whom around a contract: <b>a friend of the contract can never be hurt by it, and the contract can
     * never be hurt by its friends.</b>
     *
     * <p>It is the second half of the friendly protection, and the one an AI can not give: the targeting goal keeps the
     * queen and her brood out of the claws of a contracted dragon, but a breath is an area attack with its own damage
     * source of Ice and Fire - so without this handler a single bombardment could burn the very army that ordered it.
     * Every damage source of that mod carries the dragon as its responsible entity (claws, bite, breath, explosion and
     * every projectile it throws), which is exactly why one filter covers all of them.
     *
     * <p>The check is cheap for the whole world: a source and a victim that carry no contract cost two persistent-data
     * lookups and nothing else, and nothing is ever cancelled unless a contract really is involved.
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        Entity victim = event.getEntity();
        Entity source = responsible(event.getSource());

        if (source == null || source == victim)
        {
            return;
        }

        if (NecromancyContract.isContracted(source) && NecromancyService.isFriendlyToContract(source, victim))
        {
            // The contracted dragon struck its own side: that blow never lands, whatever the order was.
            event.setCanceled(true);

            return;
        }

        if (NecromancyContract.isContracted(victim) && NecromancyService.isFriendlyToContract(victim, source))
        {
            // And the other direction: the queen, her spiders and another dragon of her own contract can not hurt the
            // skeleton either, so a stray swing or a spider that was mid attack never wounds it.
            event.setCanceled(true);
        }
    }

    /** @return the creature responsible for one damage: the shooter for a projectile, the attacker otherwise. */
    @Nullable
    private static Entity responsible(DamageSource source)
    {
        return source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
    }
}
