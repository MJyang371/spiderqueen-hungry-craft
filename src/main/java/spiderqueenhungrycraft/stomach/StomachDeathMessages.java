package spiderqueenhungrycraft.stomach;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The one death hook of the stomach system (Phase 8E1-GATE2B).
 *
 * <p>It does the two things a death inside a stomach means: the owner of that stomach is fed with the victim, and a
 * player who dies inside the stomach of somebody else is reported as having been swallowed - whatever happened to
 * end their life in there.
 *
 * <p>The two stomach damage types carry their own death messages through the ordinary vanilla pipeline, so nothing
 * here ever speaks on its own: there is exactly one death report, and it is the one the game makes.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachDeathMessages
{
    private StomachDeathMessages()
    {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide || StomachDamageTypes.isSwallowed(event.getSource()))
        {
            // The re-entered death of a swallowed player: the meal is already given and the report is on its way.
            return;
        }

        StomachNutrition.grant(victim);
        StomachRelease.onOwnerDeath(victim);

        if (!(victim instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level))
        {
            return;
        }

        StomachCell cell = StomachCellManager.resolveCell(player).orElse(null);

        // Dying inside your own stomach is the emergency case of that cell, not a swallow.
        if (cell == null || cell.owner().equals(player.getUUID()))
        {
            return;
        }

        // The death is relabelled: the vanilla death runs once more with the source of being swallowed. This is
        // safe exactly here, because the event fires before any death side effect of a player. The name of the
        // stomach has to be there to be told, so a sleeping owner keeps the ordinary report of the game.
        ServerPlayer owner = StomachOwnership.onlineOwnerOf(level.getServer(), cell);

        if (owner == null)
        {
            return;
        }

        event.setCanceled(true);

        try
        {
            // The death runs once more, this time with the source of being swallowed, so the death report of the
            // game itself names the stomach. The blow is just lethal enough, and never a second death.
            float lethal = Math.max(1.0F, player.getHealth() + player.getAbsorptionAmount() + 1.0F);
            player.hurt(StomachDamageTypes.swallowed(level, owner), lethal);
        }
        catch (RuntimeException exception)
        {
            SpiderQueenHungryCraft.LOGGER.error("The stomach of {} could not report a swallowed death",
                    cell.owner(), exception);
        }
    }
}
