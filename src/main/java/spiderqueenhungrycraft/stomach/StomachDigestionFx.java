package spiderqueenhungrycraft.stomach;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.devour.DevourTuning;
import spiderqueenhungrycraft.registry.HungryCraftParticles;

/**
 * What a digested creature looks and sounds like (Phase 8E2A-POLISH-2).
 *
 * <p>The digestive pulse used to be nothing but a number of health points. Now every victim that really takes
 * the acid answers with a few dark green droplets that drip off its body and with the hiss of fire being
 * quenched - the same fluid and the same idea as the saliva of a devour and the cloud of a vomit, only in the
 * smallest of the three shapes.
 *
 * <p>The sound is played at the body of the victim inside the stomach, in the world of the cell, and that is
 * deliberate: a guest inside hears it at full volume, and the sound relay of this phase carries a quieter copy
 * to the owner who is somewhere else. A hit that was refused by the game - a creative player, an invulnerable
 * creature, a cancelled event - produces nothing at all.
 */
public final class StomachDigestionFx
{
    private StomachDigestionFx()
    {
    }

    /** The little acid splash of one victim of the pulse. */
    public static void feedback(ServerLevel level, LivingEntity victim)
    {
        AABB box = victim.getBoundingBox();
        RandomSource random = level.getRandom();
        int sent = 0;

        for (int drop = 0; drop < DevourTuning.DIGESTION_DROPLETS; drop++)
        {
            double x = victim.getX() + (random.nextDouble() * 2.0D - 1.0D) * box.getXsize() * 0.6D;
            double y = victim.getY() + random.nextDouble() * box.getYsize() * 0.9D;
            double z = victim.getZ() + (random.nextDouble() * 2.0D - 1.0D) * box.getZsize() * 0.6D;
            double speed = DevourTuning.DIGESTION_SPEED_MIN
                    + random.nextDouble() * (DevourTuning.DIGESTION_SPEED_MAX - DevourTuning.DIGESTION_SPEED_MIN);
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dx = Math.cos(angle) * speed;
            double dz = Math.sin(angle) * speed;
            double dy = DevourTuning.DIGESTION_UP_MIN
                    + random.nextDouble() * (DevourTuning.DIGESTION_UP_MAX - DevourTuning.DIGESTION_UP_MIN);

            // A count of zero: one droplet, exactly here, exactly this slow, and then gravity does the rest.
            sent = Math.max(sent, level.sendParticles(HungryCraftParticles.GASTRIC_DRIP.get(), x, y, z, 0, dx, dy, dz,
                    1.0D));
        }

        float pitch = DevourTuning.DIGESTION_SOUND_PITCH_MIN + random.nextFloat()
                * (DevourTuning.DIGESTION_SOUND_PITCH_MAX - DevourTuning.DIGESTION_SOUND_PITCH_MIN);

        level.playSound(null, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, DevourTuning.DIGESTION_SOUND_VOLUME, pitch);

        SpiderQueenHungryCraft.LOGGER.debug("[DIGEST_FX] {} digested: {} droplets to {} clients, acid hiss pitch={}",
                victim.getType(), DevourTuning.DIGESTION_DROPLETS, sent, pitch);
    }
}
