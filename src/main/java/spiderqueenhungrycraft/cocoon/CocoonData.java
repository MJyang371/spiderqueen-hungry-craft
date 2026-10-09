package spiderqueenhungrycraft.cocoon;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Everything the mod knows about one captured creature. The data is created once at capture time and
 * then travels unchanged from the cocoon entity to the cocoon item and back, so nutrition is never
 * calculated twice.
 *
 * <p>It also carries the reward of the prey - its normal experience, read at capture time - and, when a
 * spider egg was pushed into the cocoon, the egg itself: who pushed it in and how much time it still needs
 * (this round's breeding pass). A cocoon that is packed into an item while its egg incubates therefore
 * continues exactly where it stopped.
 *
 * <p>Since feature COCOON-RELEASE the stored prey data is not only food for the hatching table any more: a
 * pair of shears cuts a cocoon open and {@link CocoonRelease} rebuilds the creature out of this very data, so
 * what steps out of the silk is the individual that was wrapped up - same name, same health, same wounds,
 * same gear, same effects, same memories - and not a new one of its species.
 */
public final class CocoonData
{
    /** Root NBT key, used both on the entity and on the item stack. */
    public static final String TAG_KEY = "CocoonData";

    private static final String KEY_ID = "CocoonId";
    private static final String KEY_TYPE = "CapturedEntityType";
    private static final String KEY_NBT = "CapturedEntityNbt";
    private static final String KEY_WIDTH = "CapturedWidth";
    private static final String KEY_HEIGHT = "CapturedHeight";
    private static final String KEY_DEPTH = "CapturedDepth";
    private static final String KEY_NUTRITION = "Nutrition";
    private static final String KEY_DISPLAY_NAME = "CapturedDisplayName";
    private static final String KEY_DEATH_SOUND = "CapturedDeathSound";
    private static final String KEY_SOUND_SOURCE = "CapturedSoundSource";

    /** The normal experience of the prey, read once at capture time (this round's breeding pass). */
    private static final String KEY_EXPERIENCE = "CapturedExperience";

    /** Whether a spider egg was pushed into this cocoon, who pushed it and how much time is left. */
    private static final String KEY_SPIDER_EGG = "SpiderEgg";
    private static final String KEY_SPIDER_EGG_OWNER = "SpiderEggOwner";
    private static final String KEY_SPIDER_EGG_HATCH_TICKS = "SpiderEggHatchTicks";

    /**
     * The only keys that are deliberately not restored (feature COCOON-RELEASE), and every one of them is
     * about where the prey stood in the world and never about the prey itself.
     *
     * <p>Everything else is kept and put back on release: the custom name, the health, the absorption hearts,
     * the remaining breath and fire, the attributes with each of their modifiers, every active effect, the
     * whole equipment with its drop chances, the inventory (a villager's trades and experience, a horse's
     * saddle, an allay's bundle), the owner of a tamed animal, the persistence flag, the scoreboard tags, the
     * brain with its memories and the capability data of other mods. A released creature is therefore the very
     * individual that was wrapped up, not a fresh one of its species.
     */
    private static final String[] WORLD_TRANSIENT_KEYS = {
            // Identity. A cocoon item can be duplicated by creative mode or by a storage mod, and the same
            // identity must never walk a world twice.
            "UUID",
            // Where the prey stood, how fast it was going and where it looked: the release puts it down where
            // the silk was cut open and at a standstill, exactly like any other freshly placed creature.
            "Pos", "Motion", "Rotation",
            // A stale fall distance would kill the prey the instant it is let out.
            "FallDistance",
            // The id of the world the prey was captured in. A creature out of another save must not claim to
            // have come from this one.
            "WorldUUID",
            // Only the prey was wrapped up, never its rider: restoring the passenger would duplicate a living
            // creature that may still be walking around outside.
            "Passengers",
            // The holder of the leash is not necessarily there any more, and the knot would dangle.
            "Leash",
            // Remains of a dying body. A cocoon only ever wraps living prey.
            "DeathTime"
    };

    private final UUID cocoonId;
    private final ResourceLocation entityTypeId;
    private final CompoundTag entityNbt;
    private final float width;
    private final float height;
    private final float depth;
    private final int nutrition;
    private final String displayNameJson;

    /** Registry id of the death sound of the prey, empty when it could not be read safely. */
    private final String deathSoundId;

    /** Ordinal of the sound source of the prey, -1 when it could not be read safely. */
    private final int soundSourceOrdinal;

    /** The normal experience of the prey, kept so eating the cocoon never has to guess it. */
    private final int experienceReward;

    /** Whether a spider egg is incubating in this cocoon (this round's breeding pass). */
    private final boolean spiderEgg;

    /** Ticks the egg inside still needs, 0 when the cocoon holds no egg. */
    private final int hatchTicks;

    /** The player who pushed the egg in, and therefore owns the spider that hatches out of it. */
    @Nullable
    private final UUID eggOwner;

    private CocoonData(UUID cocoonId, ResourceLocation entityTypeId, CompoundTag entityNbt, float width, float height,
            float depth, int nutrition, String displayNameJson, String deathSoundId, int soundSourceOrdinal,
            int experienceReward, boolean spiderEgg, int hatchTicks, @Nullable UUID eggOwner)
    {
        this.cocoonId = cocoonId;
        this.entityTypeId = entityTypeId;
        this.entityNbt = entityNbt;
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.nutrition = nutrition;
        this.displayNameJson = displayNameJson;
        this.deathSoundId = deathSoundId;
        this.soundSourceOrdinal = soundSourceOrdinal;
        this.experienceReward = experienceReward;
        this.spiderEgg = spiderEgg;
        this.hatchTicks = hatchTicks;
        this.eggOwner = eggOwner;
    }

    /**
     * Reads the prey once, at the moment the capture succeeds. A new cocoon id is created here and
     * then never changes, so every cocoon stays an individual even while it is an item.
     */
    public static CocoonData capture(LivingEntity target, int nutrition)
    {
        AABB box = target.getBoundingBox();
        Component name = target.hasCustomName() ? target.getCustomName() : target.getType().getDescription();

        return new CocoonData(UUID.randomUUID(), EntityType.getKey(target.getType()), filteredNbt(target),
                (float) box.getXsize(), (float) box.getYsize(), (float) box.getZsize(), nutrition,
                Component.Serializer.toJson(name), CocoonSoundResolver.deathSoundId(target),
                CocoonSoundResolver.soundSourceOrdinal(target), target.getExperienceReward(), false, 0, null);
    }

    /** @return the prey data with the world transient keys removed, ready for long term storage. */
    private static CompoundTag filteredNbt(LivingEntity target)
    {
        CompoundTag tag = new CompoundTag();
        target.saveWithoutId(tag);

        for (String key : WORLD_TRANSIENT_KEYS)
        {
            tag.remove(key);
        }

        return tag;
    }

    public void save(CompoundTag target)
    {
        CompoundTag tag = new CompoundTag();
        tag.putUUID(KEY_ID, this.cocoonId);
        tag.putString(KEY_TYPE, this.entityTypeId.toString());
        tag.put(KEY_NBT, this.entityNbt.copy());
        tag.putFloat(KEY_WIDTH, this.width);
        tag.putFloat(KEY_HEIGHT, this.height);
        tag.putFloat(KEY_DEPTH, this.depth);
        tag.putInt(KEY_NUTRITION, this.nutrition);
        tag.putString(KEY_DISPLAY_NAME, this.displayNameJson);
        tag.putString(KEY_DEATH_SOUND, this.deathSoundId);
        tag.putInt(KEY_SOUND_SOURCE, this.soundSourceOrdinal);
        tag.putInt(KEY_EXPERIENCE, this.experienceReward);
        tag.putBoolean(KEY_SPIDER_EGG, this.spiderEgg);
        tag.putInt(KEY_SPIDER_EGG_HATCH_TICKS, this.hatchTicks);

        if (this.eggOwner != null)
        {
            tag.putUUID(KEY_SPIDER_EGG_OWNER, this.eggOwner);
        }

        target.put(TAG_KEY, tag);
    }

    /** @return the stored data, or null when the tag is missing or incomplete. */
    @Nullable
    public static CocoonData load(CompoundTag tag)
    {
        if (!tag.contains(TAG_KEY, CompoundTag.TAG_COMPOUND))
        {
            return null;
        }

        CompoundTag data = tag.getCompound(TAG_KEY);
        ResourceLocation typeId = data.contains(KEY_TYPE, CompoundTag.TAG_STRING)
                ? ResourceLocation.tryParse(data.getString(KEY_TYPE))
                : null;

        if (!data.hasUUID(KEY_ID) || typeId == null || !data.contains(KEY_NBT, CompoundTag.TAG_COMPOUND))
        {
            return null;
        }

        return new CocoonData(data.getUUID(KEY_ID), typeId, data.getCompound(KEY_NBT).copy(),
                data.getFloat(KEY_WIDTH), data.getFloat(KEY_HEIGHT), data.getFloat(KEY_DEPTH),
                data.getInt(KEY_NUTRITION), data.getString(KEY_DISPLAY_NAME),
                data.contains(KEY_DEATH_SOUND, CompoundTag.TAG_STRING) ? data.getString(KEY_DEATH_SOUND) : "",
                data.contains(KEY_SOUND_SOURCE) ? data.getInt(KEY_SOUND_SOURCE) : -1,
                data.getInt(KEY_EXPERIENCE), data.getBoolean(KEY_SPIDER_EGG), data.getInt(KEY_SPIDER_EGG_HATCH_TICKS),
                data.hasUUID(KEY_SPIDER_EGG_OWNER) ? data.getUUID(KEY_SPIDER_EGG_OWNER) : null);
    }

    /**
     * @return a copy of this cocoon with a spider egg inside: the egg belongs to that player and the
     *         countdown starts at the given number of ticks. The prey itself is untouched, so a cocoon
     *         with an egg is still the same cocoon.
     */
    public CocoonData withSpiderEgg(@Nullable UUID owner, int hatchTicks)
    {
        return new CocoonData(this.cocoonId, this.entityTypeId, this.entityNbt, this.width, this.height, this.depth,
                this.nutrition, this.displayNameJson, this.deathSoundId, this.soundSourceOrdinal,
                this.experienceReward, true, hatchTicks, owner);
    }

    /** @return a copy of this cocoon with that much hatching time left on its egg. */
    public CocoonData withHatchTicks(int hatchTicks)
    {
        return new CocoonData(this.cocoonId, this.entityTypeId, this.entityNbt, this.width, this.height, this.depth,
                this.nutrition, this.displayNameJson, this.deathSoundId, this.soundSourceOrdinal,
                this.experienceReward, this.spiderEgg, hatchTicks, this.eggOwner);
    }

    /** @return true when a spider egg incubates in this cocoon. */
    public boolean hasSpiderEgg()
    {
        return this.spiderEgg;
    }

    /** @return the ticks the egg inside still needs, 0 when there is no egg. */
    public int getHatchTicks()
    {
        return this.hatchTicks;
    }

    /** @return the player who pushed the egg in, or null when the cocoon holds no egg. */
    @Nullable
    public UUID getEggOwner()
    {
        return this.eggOwner;
    }

    /** @return the normal experience of the prey, read when it was captured. */
    public int getExperienceReward()
    {
        return this.experienceReward;
    }

    /** @return the registry id of the prey death sound, empty when it is unknown. */
    public String getDeathSoundId()
    {
        return this.deathSoundId;
    }

    /** @return the sound source ordinal of the prey, -1 when it is unknown. */
    public int getSoundSourceOrdinal()
    {
        return this.soundSourceOrdinal;
    }

    public void writeTo(ItemStack stack)
    {
        save(stack.getOrCreateTag());
    }

    @Nullable
    public static CocoonData from(ItemStack stack)
    {
        return stack.hasTag() ? load(stack.getTag()) : null;
    }

    public UUID getCocoonId()
    {
        return this.cocoonId;
    }

    public ResourceLocation getEntityTypeId()
    {
        return this.entityTypeId;
    }

    /**
     * @return a copy of the stored prey data, the exact state the prey had at capture time apart from the
     *         keys of {@link #WORLD_TRANSIENT_KEYS}. It is what the release rebuilds the creature from.
     */
    public CompoundTag copyEntityNbt()
    {
        return this.entityNbt.copy();
    }

    public float getWidth()
    {
        return this.width;
    }

    public float getHeight()
    {
        return this.height;
    }

    public float getDepth()
    {
        return this.depth;
    }

    /** @return the hunger this prey restores, calculated once at capture time. */
    public int getNutrition()
    {
        return this.nutrition;
    }

    /** @return the name shown to the player, the custom name if the prey had one. */
    public Component getDisplayName()
    {
        if (this.displayNameJson.isEmpty())
        {
            return fallbackName();
        }

        Component parsed = Component.Serializer.fromJson(this.displayNameJson);
        return parsed == null ? fallbackName() : parsed;
    }

    private Component fallbackName()
    {
        return EntityType.byString(this.entityTypeId.toString())
                .map(EntityType::getDescription)
                .orElse(Component.literal(this.entityTypeId.toString()));
    }
}
