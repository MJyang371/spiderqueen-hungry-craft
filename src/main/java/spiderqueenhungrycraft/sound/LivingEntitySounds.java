package spiderqueenhungrycraft.sound;

import java.lang.reflect.Method;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reads the protected sound accessors of a living entity without ever hurting it.
 *
 * <p>Both the official and the production (SRG) method names are tried and every step is guarded, so a
 * failure never breaks a caller. It only means "no sound could be resolved" and the caller can fall
 * back to a generic sound.
 */
public final class LivingEntitySounds
{
    /** Official and production names of LivingEntity#getHurtSound(DamageSource). */
    private static final String[] HURT_SOUND_METHODS = { "getHurtSound", "m_7975_" };

    /** Official and production names of LivingEntity#getDeathSound(). */
    private static final String[] DEATH_SOUND_METHODS = { "getDeathSound", "m_5592_" };

    private LivingEntitySounds()
    {
    }

    /**
     * @param source a damage source only used as an argument, no damage is ever dealt or triggered
     * @return the hurt sound this creature would make, or null when it cannot be read
     */
    @Nullable
    public static SoundEvent hurtSound(LivingEntity entity, DamageSource source)
    {
        return invoke(entity, HURT_SOUND_METHODS, source);
    }

    /** @return the death sound of this creature, or null when it cannot be read. */
    @Nullable
    public static SoundEvent deathSound(LivingEntity entity)
    {
        return invoke(entity, DEATH_SOUND_METHODS);
    }

    /**
     * Walks the class hierarchy and looks the accessor up by name and parameter count, so it works no
     * matter which class in the hierarchy overrides it.
     */
    @Nullable
    private static SoundEvent invoke(LivingEntity entity, String[] names, Object... arguments)
    {
        for (Class<?> type = entity.getClass(); type != null && LivingEntity.class.isAssignableFrom(type);
                type = type.getSuperclass())
        {
            for (String name : names)
            {
                try
                {
                    Method method = find(type, name, arguments.length);
                    method.setAccessible(true);
                    Object result = method.invoke(entity, arguments);

                    // The method was found, so a null result means this creature has no such sound.
                    return result instanceof SoundEvent ? (SoundEvent) result : null;
                }
                catch (ReflectiveOperationException | RuntimeException ignored)
                {
                    // Try the next candidate, a locked or missing accessor is not an error here.
                }
            }
        }

        return null;
    }

    private static Method find(Class<?> type, String name, int parameterCount) throws NoSuchMethodException
    {
        for (Method method : type.getDeclaredMethods())
        {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount)
            {
                return method;
            }
        }

        throw new NoSuchMethodException(name);
    }
}
