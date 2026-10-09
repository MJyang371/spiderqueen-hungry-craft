package spiderqueenhungrycraft.cocoon;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import spiderqueenhungrycraft.sound.LivingEntitySounds;

/**
 * Resolves the death sound of a captured creature so eating a cocoon sounds like swallowing that
 * creature. The creature is never spawned: the sound is read from the prey at capture time, or from a
 * throwaway instance that is never added to the world, ticked or killed.
 *
 * <p>Every step is guarded. If a sound cannot be resolved, the eating sound is used instead, because
 * eating a cocoon must always work.
 */
public final class CocoonSoundResolver
{
    private CocoonSoundResolver()
    {
    }

    /** @return the registry id of the death sound of this creature, empty when unreadable. */
    public static String deathSoundId(LivingEntity entity)
    {
        SoundEvent sound = LivingEntitySounds.deathSound(entity);
        if (sound == null)
        {
            return "";
        }

        ResourceLocation id = ForgeRegistries.SOUND_EVENTS.getKey(sound);
        return id == null ? "" : id.toString();
    }

    /** @return the ordinal of the sound source of this entity, -1 when unreadable. */
    public static int soundSourceOrdinal(Entity entity)
    {
        try
        {
            return entity.getSoundSource().ordinal();
        }
        catch (RuntimeException exception)
        {
            return -1;
        }
    }

    /** Plays the prey death sound at the cocoon, or the eating sound when that is not possible. */
    public static void playEatSound(ServerLevel level, CocoonEntity cocoon)
    {
        CocoonData data = cocoon.getCocoonData();
        SoundEvent sound = data == null ? null : resolveSound(data.getDeathSoundId());
        SoundSource source = data == null ? SoundSource.NEUTRAL : resolveSource(data.getSoundSourceOrdinal());

        if (sound == null)
        {
            LivingEntity probe = createProbe(level, data);
            if (probe != null)
            {
                sound = LivingEntitySounds.deathSound(probe);
                source = sound == null ? source : probe.getSoundSource();
            }
        }

        if (sound == null)
        {
            sound = SoundEvents.GENERIC_EAT;
            source = SoundSource.PLAYERS;
        }

        level.playSound(null, cocoon.getX(), cocoon.getY(), cocoon.getZ(), sound, source, 1.0F, 1.0F);
    }

    @Nullable
    private static SoundEvent resolveSound(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(key);
    }

    private static SoundSource resolveSource(int ordinal)
    {
        SoundSource[] values = SoundSource.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : SoundSource.NEUTRAL;
    }

    /**
     * @return a throwaway instance used only to read its death sound. It is never added to a level, so
     *         it cannot be ticked, damaged, killed or dropped by anything.
     */
    @Nullable
    private static LivingEntity createProbe(Level level, @Nullable CocoonData data)
    {
        if (data == null)
        {
            return null;
        }

        try
        {
            Optional<EntityType<?>> type = EntityType.byString(data.getEntityTypeId().toString());
            if (type.isEmpty())
            {
                return null;
            }

            Entity created = type.get().create(level);
            return created instanceof LivingEntity ? (LivingEntity) created : null;
        }
        catch (RuntimeException exception)
        {
            return null;
        }
    }
}
