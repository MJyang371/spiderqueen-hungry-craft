package spiderqueenhungrycraft.player;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSelection;

/**
 * Glues the capability to player entities and keeps the data when the player entity is cloned,
 * either because of death or because of a dimension change.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenDataEvents
{
    private static final ResourceLocation QUEEN_DATA_KEY =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "queen_data");

    private QueenDataEvents()
    {
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (!(event.getObject() instanceof Player))
        {
            return;
        }

        QueenDataProvider provider = new QueenDataProvider();
        event.addCapability(QUEEN_DATA_KEY, provider);

        // Deliberately no invalidate listener here. A player who changes dimension is only removed from their old
        // level for a moment and keeps the very same entity, so an invalidated provider would never hand out the
        // queen data again - which is exactly the loss of the queen state this phase repairs. The data lives as
        // long as the player entity does and is saved with it.
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        Player oldPlayer = event.getOriginal();
        Player newPlayer = event.getEntity();

        // The original player is already invalidated at this point, so its caps have to be revived
        // before they can be read, and invalidated again afterwards.
        oldPlayer.reviveCaps();
        QueenDataCapability.get(oldPlayer).ifPresent(oldData ->
                QueenDataCapability.get(newPlayer).ifPresent(newData ->
                {
                    newData.copyFrom(oldData);

                    // The beast body only survives a rebuild of the player entity, never a death: a respawn
                    // always starts as an ordinary queen, and the reconcile of the beast events then removes
                    // every modifier and body size that could be left over.
                    if (!event.isWasDeath())
                    {
                        newData.beast().copyFrom(oldData.beast());
                    }
                    else
                    {
                        // The silk of the glands follows the hunger bar: a respawn begins with a full stomach
                        // in vanilla, and the glands of the queen are refilled with it. Only a real death
                        // empties them, an ordinary rebuild of the entity keeps the amount.
                        newData.setSilkAmount(QueenSilk.MAX_AMOUNT);
                    }
                }));
        oldPlayer.invalidateCaps();
    }

    /**
     * The selection of a queen is hers and lives on the server, but the outline she sees on her selected spiders is
     * drawn by her own client (Brood Command Group round). So the client is told again what is selected the moment the
     * queen really joins the world - otherwise a group that was picked before a logout would be invisible while her
     * commands still act on it.
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer queen)
        {
            BroodSelection.sendSelection(queen);
        }
    }

    /** The same for a queen who walks through a portal: the client of the new level knows nothing about her group yet. */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer queen)
        {
            BroodSelection.sendSelection(queen);
        }
    }
}
