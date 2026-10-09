package spiderqueenhungrycraft.stomach;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.event.entity.EntityTeleportEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Keeps the stomach dimension read only for ordinary gameplay (Phase 8E1).
 *
 * <p>Nothing is patched into {@code Level#setBlock}: only public Forge events are used, so other mods keep
 * working and the mod's own world writes are never affected. Explosions keep hurting entities, they simply may
 * not change any block.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachProtection
{
    private StomachProtection()
    {
    }

    /** @return true when that level is the stomach dimension. */
    public static boolean isStomach(LevelAccessor level)
    {
        return level instanceof Level real && real.dimension().equals(StomachCellManager.DIMENSION);
    }

    /** A player can neither mine nor otherwise break a block inside a stomach. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** No block can be placed inside a stomach. */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** The same for the multi place form, which mods and some items use. */
    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** Right clicks on blocks, which covers buckets, flint and steel, fire charges and spawn eggs. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** Filling or emptying a bucket inside a stomach is refused. */
    @SubscribeEvent
    public static void onBucket(FillBucketEvent event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** A stomach can never be part of a portal, so no portal may be lit inside it. */
    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** Pistons may not reshape a stomach. */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event)
    {
        if (isStomach(event.getLevel()))
        {
            event.setCanceled(true);
        }
    }

    /** Explosions still damage entities but lose every block they would have destroyed. */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event)
    {
        if (isStomach(event.getLevel()))
        {
            event.getAffectedBlocks().clear();
        }
    }

    /**
     * A player cannot leave a stomach through a vanilla portal, and nothing can enter a stomach through one.
     * The internal teleports of this mod set the bypass and are therefore never touched.
     */
    @SubscribeEvent
    public static void onTravelToDimension(EntityTravelToDimensionEvent event)
    {
        if (StomachTeleport.isInternalMove())
        {
            return;
        }

        if (isStomach(event.getEntity().level()) && !event.getDimension().equals(StomachCellManager.DIMENSION))
        {
            event.setCanceled(true);
        }
    }

    /** The vanilla teleports - command, ender pearl, chorus fruit - cannot move anything out of a stomach. */
    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event)
    {
        if (StomachTeleport.isInternalMove())
        {
            return;
        }

        if (isStomach(event.getEntity().level()))
        {
            event.setCanceled(true);
        }
    }
}
