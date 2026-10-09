package spiderqueenhungrycraft.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * Makes brand new worlds start at night, which is the home turf of the Spider Queen.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenWorldStartEvents
{
    private QueenWorldStartEvents()
    {
    }

    /**
     * The game fires this while a brand new level is initialized (existing worlds already have an
     * initialized spawn position and never reach this point again), which is exactly the safe moment
     * to pick a start time. The vanilla spawn search is left untouched.
     */
    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event)
    {
        if (!(event.getLevel() instanceof ServerLevel))
        {
            return;
        }

        applyNewWorldTime((ServerLevel) event.getLevel(), event.getSettings());
    }

    /**
     * @param level    the level that is being created
     * @param settings the persistent level data of that level
     */
    public static void applyNewWorldTime(ServerLevel level, ServerLevelData settings)
    {
        if (!HungryCraftConfig.startNewWorldAtMidnight())
        {
            return;
        }
        if (level.dimension() != Level.OVERWORLD)
        {
            return;
        }

        settings.setDayTime(HungryCraftConfig.initialOverworldTime());
    }
}
