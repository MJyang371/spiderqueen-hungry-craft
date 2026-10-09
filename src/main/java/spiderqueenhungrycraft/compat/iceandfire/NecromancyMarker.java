package spiderqueenhungrycraft.compat.iceandfire;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

/**
 * The <b>prototype</b> necromancy marker of a revived dragon (Necromancy IV prototype 1).
 *
 * <p>It is the smallest possible record of "HungryCraft woke this corpse up": who did it and what the dragon was
 * before. It lives in the ordinary Forge persistent data of the entity, which means it is saved with the world,
 * survives a chunk unload, a relog and a server restart, and needs no capability, no attachment and no new field on
 * somebody else's entity.
 *
 * <p><b>It is deliberately pure vanilla.</b> Nothing in this file knows that Ice and Fire exists, so it compiles,
 * loads and is tested in a workspace without that mod - which is exactly what the compatibility round asks for. Only
 * the caller decides which entity is worth marking.
 *
 * <p>What it holds, and nothing more:
 * <ul>
 * <li>{@code revived} - the flag the whole prototype hangs on: this entity is currently a <b>living</b> dragon that
 * was a corpse, and it has to be given back to Ice and Fire as a corpse by {@code restore},</li>
 * <li>{@code owner} - the player who revived it. The prototype has no gameplay attached to it yet; it is recorded so
 * that the later Queen Scepter round has a necromancy owner that is <b>not</b> the vanilla tame owner of the dragon,</li>
 * <li>{@code saved_command} - the Ice and Fire command of the dragon at the moment it was woken. The prototype never
 * writes that command itself, it only puts the same value back during the restore,</li>
 * <li>{@code saved_health} - the health the corpse had before it was woken, for the inspection report. The restore of
 * the prototype deliberately ends a revived dragon at zero health (see the round's specification), because that is the
 * state Ice and Fire's own {@code tickDeath} turns into a corpse.</li>
 * </ul>
 *
 * <p>There is deliberately <b>no</b> countdown in here. The ten minute "shift is over" timer of the later round will
 * add one field to this very tag; the prototype stops at the four above.
 */
public final class NecromancyMarker
{
    /** The one root key this mod ever writes into the persistent data of a foreign entity. */
    public static final String ROOT_KEY = "spiderqueen_hungrycraft:necromancy";

    private static final String REVIVED = "revived";
    private static final String OWNER = "owner";
    private static final String SAVED_COMMAND = "saved_command";
    private static final String SAVED_HEALTH = "saved_health";

    private NecromancyMarker()
    {
    }

    /** @return true when that entity is currently a HungryCraft revived dragon, and not null and not an unrelated being. */
    public static boolean isRevived(@Nullable Entity entity)
    {
        return entity != null && root(entity).map(tag -> tag.getBoolean(REVIVED)).orElse(false);
    }

    /** Writes the whole marker, replacing whatever was there before. */
    public static void mark(Entity entity, UUID owner, int savedCommand, float savedHealth)
    {
        CompoundTag root = new CompoundTag();
        root.putBoolean(REVIVED, true);
        root.putUUID(OWNER, owner);
        root.putInt(SAVED_COMMAND, savedCommand);
        root.putFloat(SAVED_HEALTH, savedHealth);
        entity.getPersistentData().put(ROOT_KEY, root);
    }

    /** @return the player that revived that dragon, or empty. */
    public static Optional<UUID> owner(@Nullable Entity entity)
    {
        return root(entity).filter(tag -> tag.contains(OWNER)).map(tag -> tag.getUUID(OWNER));
    }

    /** @return the command the dragon had before it was woken, or the fallback when there is no marker. */
    public static int savedCommand(@Nullable Entity entity, int fallback)
    {
        return root(entity).filter(tag -> tag.contains(SAVED_COMMAND)).map(tag -> tag.getInt(SAVED_COMMAND))
                .orElse(fallback);
    }

    /** @return the health the corpse had before it was woken, or the fallback when there is no marker. */
    public static float savedHealth(@Nullable Entity entity, float fallback)
    {
        return root(entity).filter(tag -> tag.contains(SAVED_HEALTH)).map(tag -> tag.getFloat(SAVED_HEALTH))
                .orElse(fallback);
    }

    /** Removes the marker: the entity is nothing but what Ice and Fire says it is again. */
    public static void clear(Entity entity)
    {
        entity.getPersistentData().remove(ROOT_KEY);
    }

    /** @return the root compound, when the entity carries one at all. */
    private static Optional<CompoundTag> root(@Nullable Entity entity)
    {
        if (entity == null)
        {
            return Optional.empty();
        }

        CompoundTag data = entity.getPersistentData();

        return data.contains(ROOT_KEY, Tag.TAG_COMPOUND) ? Optional.of(data.getCompound(ROOT_KEY)) : Optional.empty();
    }
}
