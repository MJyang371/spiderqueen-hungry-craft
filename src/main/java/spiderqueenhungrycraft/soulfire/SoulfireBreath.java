package spiderqueenhungrycraft.soulfire;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The soul fire breath of a soul fire dragon (round 7C).
 *
 * <p><b>The particles are not the damage.</b> They are two separate halves that are both ticked here, and neither is
 * derived from the other: the flames are spawned for the eye (every tick, along the line the muzzle is aiming at), and
 * the damage is decided on the <b>server</b> from real hit detection - a cone of {@value #CONE} around the aim inside
 * the reach of the unit, every {@value #DAMAGE_INTERVAL} ticks - so a client that sees a beautiful stream and a client
 * that sees nothing at all take exactly the same hits.
 *
 * <p><b>What it does not do.</b> It does not own a cooldown (the round asks for none: a breath lasts until another
 * order arrives, and the skill can be cast again at once), it does not write any state of its own, and it does not
 * decide <i>when</i> to breathe - the command goal does that, and it calls this class only for the ticks in which the
 * unit is really supposed to be spitting fire.
 *
 * <p>A breath never hurts the side of the unit: {@link SoulfireTargeting} is asked before anything is damaged, so the
 * queen, her spiders and her other units can stand in the stream all day.
 */
public final class SoulfireBreath
{
    /** How many ticks pass between two applications of damage. */
    public static final int DAMAGE_INTERVAL = 5;

    /** How many ticks pass between two shots of the muzzle. */
    public static final int SOUND_INTERVAL = 8;

    /** How many flames one tick of the stream throws into the air. */
    public static final int FLAMES_PER_TICK = 16;

    /** The damage every stage of the original remains adds to the breath. */
    public static final double DAMAGE_PER_STAGE = 0.5D;

    /** The damage of the breath of a stage one remains, before the stage scaling is added. */
    public static final float BASE_DAMAGE = 1.0F;

    /** How many seconds a victim of the soul fire keeps burning. */
    public static final int BURN_SECONDS = 5;

    /** How tight the cone around the aim is: the cosine of its half angle. */
    private static final double CONE = 0.90D;

    /** How far around the line the hit test looks, so a wing or a tail does not hide a whole victim. */
    private static final double HEAD_ROOM = 3.0D;

    private SoulfireBreath()
    {
    }

    /**
     * One tick of the stream, aimed at that point of the world.
     *
     * <p>It is called only on the server: the flames it spawns are sent to every client that can see the unit, and the
     * damage it applies is a server decision.
     */
    public static void tick(SoulfireDragonEntity dragon, Vec3 target)
    {
        if (!(dragon.level() instanceof ServerLevel level))
        {
            return;
        }

        Vec3 muzzle = dragon.getMuzzlePosition();
        Vec3 toTarget = target.subtract(muzzle);

        if (toTarget.lengthSqr() < 1.0E-6D)
        {
            return;
        }

        Vec3 aim = toTarget.normalize();

        // The unit spits where it looks, so the muzzle follows the point it was told to burn.
        dragon.setBreathing(true);
        dragon.getLookControl().setLookAt(target.x, target.y, target.z, 30.0F, 30.0F);

        spawnFlames(level, muzzle, aim);

        if (dragon.breathTicks() % SOUND_INTERVAL == 0)
        {
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.HOSTILE, 1.2F, 0.6F);
        }

        if (dragon.breathTicks() % DAMAGE_INTERVAL == 0)
        {
            burn(level, dragon, muzzle, aim, target);
        }

        dragon.advanceBreathTicks();
    }

    /** The visible half: the soul fire flames of the vanilla game, along the line of the stream. */
    private static void spawnFlames(ServerLevel level, Vec3 muzzle, Vec3 aim)
    {
        for (int flame = 0; flame < FLAMES_PER_TICK; flame++)
        {
            double along = level.random.nextDouble() * SoulfireDragonEntity.BREATH_RANGE * 0.7D;
            double spread = 0.3D + along * 0.05D;
            double x = muzzle.x + aim.x * along + (level.random.nextDouble() - 0.5D) * spread;
            double y = muzzle.y + aim.y * along + (level.random.nextDouble() - 0.5D) * spread;
            double z = muzzle.z + aim.z * along + (level.random.nextDouble() - 0.5D) * spread;

            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** The server half: everything inside the cone of the stream takes the damage of the unit and catches fire. */
    private static void burn(ServerLevel level, SoulfireDragonEntity dragon, Vec3 muzzle, Vec3 aim, Vec3 target)
    {
        AABB cone = new AABB(muzzle, target).inflate(HEAD_ROOM);

        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, cone))
        {
            if (!SoulfireTargeting.isHuntable(dragon, victim))
            {
                continue;
            }

            Vec3 toVictim = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D).subtract(muzzle);
            double distance = toVictim.length();

            if (distance > SoulfireDragonEntity.BREATH_RANGE)
            {
                continue;
            }

            if (distance > 0.5D && toVictim.normalize().dot(aim) < CONE)
            {
                continue;
            }

            victim.setSecondsOnFire(BURN_SECONDS);
            victim.hurt(level.damageSources().mobAttack(dragon),
                    BASE_DAMAGE + (float) (DAMAGE_PER_STAGE * dragon.getStage()));
        }
    }
}
