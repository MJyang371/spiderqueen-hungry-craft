package spiderqueenhungrycraft.beast;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The Forge hooks of the Queen Beast form (Phase 8A).
 *
 * <p>This class only connects the events to the controller: every rule, every number and every cleanup lives
 * there, so the beast form has one authority instead of one code path per event.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBeastEvents
{
    private QueenBeastEvents()
    {
    }

    /** Server side heartbeat of the form: hunger, regeneration, the compressed watch and the forced exits. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide())
        {
            return;
        }

        if (event.player instanceof ServerPlayer player)
        {
            QueenBeastController.tick(player);
        }
    }

    /**
     * The body of the beast. Forge fires this from {@code Entity#refreshDimensions}, so both sides get the very
     * same body from the very same state, and the first person eye height moves with it.
     */
    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }

        // Forge also fires this from the entity constructor, where the entity has no capabilities yet, so a
        // player that is not part of a world is left completely alone.
        if (!player.isAddedToWorld())
        {
            return;
        }

        // A compressed beast keeps its small, safe body: the large box is never pushed into blocks.
        if (!QueenBeastState.isTransformed(player) || QueenBeastState.isCompressed(player))
        {
            return;
        }

        if (player.isDeadOrDying() || player.isSpectator())
        {
            return;
        }

        event.setNewSize(QueenBeastDimensions.dimensions());
        event.setNewEyeHeight(QueenBeastConstants.BEAST_EYE_HEIGHT);
    }

    /** A fresh login restores the saved form and brings the body, the attributes and the room in line with it. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            QueenBeastController.reconcile(player);
        }
    }

    /**
     * A new body after a death or a rebuild. The state itself is copied by the queen data event, this only
     * reconciles the result, so a death never resurrects the beast and a rebuild never stacks a modifier.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            QueenBeastController.reconcile(player);
        }
    }

    /** The room of the new dimension decides the body again, the clearance is never repeated here. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            QueenBeastController.reconcile(player);
        }
    }

    /**
     * Somebody starts seeing a player: a beast has to hand over its current state, otherwise a second player
     * would keep seeing a normal skin while the first one already runs around as a giant spider.
     */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event)
    {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof ServerPlayer target))
        {
            return;
        }

        if (QueenBeastState.isTransformed(target))
        {
            QueenBeastController.syncTo(target, observer);
        }
    }

    /** Death always ends the form, so the next life starts as an ordinary queen without leftover attributes. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            QueenBeastController.forceUntransform(player, QueenBeastController.RevertCause.DEATH);
        }
    }

    /**
     * A beast never takes fall damage (Phase 8C).
     *
     * <p>This is deliberately not tied to a charged jump: the whole form is immune, so a beast that walks off a
     * cliff simply lands. Only the beast is affected, an ordinary queen and every other player keep the vanilla
     * rule, and nothing here touches the fall distance, which stays exactly as vanilla keeps it.
     */
    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player && QueenBeastState.isTransformed(player))
        {
            event.setCanceled(true);
        }
    }

    /**
     * A beast is never pushed around by an ordinary hit (8B-POLISH-1).
     *
     * <p>This is the second layer of the knockback immunity of the form: the {@code knockback_resistance}
     * attribute of the beast already reduces every ordinary knockback to nothing, and this guard makes sure it
     * stays that way even when another mod changes that attribute. Only the melee, arrow and shield knockback
     * path runs through here; real impulses such as an explosion move the entity directly and still toss the
     * beast, because that is physics rather than a shove.
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event)
    {
        if (QueenBeastState.isTransformed(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }
}
