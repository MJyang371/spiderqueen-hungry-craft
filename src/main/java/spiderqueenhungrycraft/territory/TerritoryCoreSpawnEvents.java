package spiderqueenhungrycraft.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Cancels natural spawn attempts inside a territory (Phase LAIR-CORE-1).
 *
 * <p>The event is {@link MobSpawnEvent.PositionCheck}: Forge fires it for every mob whose position is being validated,
 * after the spawn placement rules of that mob ran and before the entity is finalised. It carries the spawn type, so
 * this handler can restrict itself to {@link net.minecraft.world.entity.MobSpawnType#NATURAL} and leave every other
 * spawn alone - and it has a result, so a denied attempt simply aborts.
 *
 * <p>Nothing is killed, nothing is pushed, nothing is blocked from walking in: only the attempt itself is refused.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class TerritoryCoreSpawnEvents
{
    private TerritoryCoreSpawnEvents()
    {
    }

    @SubscribeEvent
    public static void onPositionCheck(MobSpawnEvent.PositionCheck event)
    {
        if (!TerritoryCoreSpawnRules.isNaturalSpawnType(event.getSpawnType()))
        {
            return;
        }

        // Spawn attempts happen on a real server level; during world generation the accessor is not a ServerLevel and
        // no territory can exist there anyway.
        if (!(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }

        Mob mob = event.getEntity();
        BlockPos pos = BlockPos.containing(mob.getX(), mob.getY(), mob.getZ());

        if (TerritoryCoreSpawnRules.isNaturalSpawnBlocked(level, pos))
        {
            event.setResult(Event.Result.DENY);
            SpiderQueenHungryCraft.LOGGER.debug("[Territory] refused a natural {} spawn at {} {} {}", mob.getType(),
                    pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
