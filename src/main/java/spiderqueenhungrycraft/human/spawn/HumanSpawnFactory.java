package spiderqueenhungrycraft.human.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;

/**
 * The one way a human of this mod enters the world through the spawn system (Phase HF0D).
 *
 * <p>It keeps the order that matters: the entity is created, his spawn origin is written on him, then he finalises
 * (which is where his loadout and his face are rolled), and only then is he added to the world. Every spawner of this
 * phase goes through here, so a human can never be placed without an origin.
 */
public final class HumanSpawnFactory
{
    private HumanSpawnFactory()
    {
    }

    /** @return the spawned human, or null when that type could not be created or added at all. */
    public static Mob spawn(EntityType<?> type, ServerLevel level, BlockPos pos, MobSpawnType reason,
            HumanSpawnOrigin origin)
    {
        Entity created = type.create(level);

        if (!(created instanceof Mob mob))
        {
            if (created != null)
            {
                created.discard();
            }

            return null;
        }

        mob.moveTo(pos.getX() + 0.5D, (double) pos.getY(), pos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);

        if (mob instanceof HumanCombatantEntity human)
        {
            human.setSpawnOrigin(origin);
        }

        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), reason, null, null);

        if (!level.addFreshEntity(mob))
        {
            mob.discard();
            return null;
        }

        return mob;
    }
}
