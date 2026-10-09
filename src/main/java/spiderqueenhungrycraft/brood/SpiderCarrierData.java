package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

/**
 * Everything a spider carrier item remembers about the spider it holds.
 *
 * <p>The data travels one way only, from the spider into the item and back out of it, exactly like a cocoon
 * carries its prey: the carrier stores the species, the health it had at the moment it was picked up, its
 * own maximum health, the custom name and the owner. It deliberately stores <b>no</b> entity UUID, no
 * position, no motion and no AI target - a deployed spider is a new arrival, not a teleported one.
 */
public final class SpiderCarrierData
{
    /** Root NBT key of the carrier on the item stack. */
    public static final String TAG_KEY = "SpiderCarrier";

    private static final String KEY_TYPE = "SpiderType";
    private static final String KEY_HEALTH = "SpiderHealth";
    private static final String KEY_MAX_HEALTH = "SpiderMaxHealth";
    private static final String KEY_CUSTOM_NAME = "SpiderCustomName";
    private static final String KEY_OWNER = "SpiderOwner";

    /**
     * How much rest the ability of the carried spider still had when it was picked up (Brood Ability framework).
     *
     * <p>The buffs of a spider end with the spider itself when it is picked up, but its cooldown travels with it: a
     * zombie spider that went berserk and is then packed away and put down again does not get a free second rage.
     */
    private static final String KEY_ABILITY_COOLDOWN = "SpiderAbilityCooldown";

    private final ResourceLocation typeId;
    private final float health;
    private final float maxHealth;
    private final String customNameJson;
    @Nullable
    private final UUID owner;
    private final int abilityCooldownTicks;

    private SpiderCarrierData(ResourceLocation typeId, float health, float maxHealth, String customNameJson,
            @Nullable UUID owner, int abilityCooldownTicks)
    {
        this.typeId = typeId;
        this.health = health;
        this.maxHealth = maxHealth;
        this.customNameJson = customNameJson;
        this.owner = owner;
        this.abilityCooldownTicks = Math.max(0, abilityCooldownTicks);
    }

    /** Reads the state of that spider exactly as it is right now, including its wounds and its name. */
    public static SpiderCarrierData capture(SpiderMinionEntity spider)
    {
        Component name = spider.getCustomName();

        return new SpiderCarrierData(EntityType.getKey(spider.getType()), spider.getHealth(),
                (float) spider.getMaxHealth(), name == null ? "" : Component.Serializer.toJson(name),
                spider.getOwnerUUID(), spider.getAbilityCooldownRemaining());
    }

    public void save(CompoundTag tag)
    {
        CompoundTag data = new CompoundTag();
        data.putString(KEY_TYPE, this.typeId.toString());
        data.putFloat(KEY_HEALTH, this.health);
        data.putFloat(KEY_MAX_HEALTH, this.maxHealth);
        data.putString(KEY_CUSTOM_NAME, this.customNameJson);
        data.putInt(KEY_ABILITY_COOLDOWN, this.abilityCooldownTicks);

        if (this.owner != null)
        {
            data.putUUID(KEY_OWNER, this.owner);
        }

        tag.put(TAG_KEY, data);
    }

    public void writeTo(ItemStack stack)
    {
        save(stack.getOrCreateTag());
    }

    /** @return the stored data, or null when the tag is missing or does not name a species. */
    @Nullable
    public static SpiderCarrierData load(@Nullable CompoundTag tag)
    {
        if (tag == null || !tag.contains(TAG_KEY, CompoundTag.TAG_COMPOUND))
        {
            return null;
        }

        CompoundTag data = tag.getCompound(TAG_KEY);

        if (!data.contains(KEY_TYPE, CompoundTag.TAG_STRING))
        {
            return null;
        }

        ResourceLocation typeId = ResourceLocation.tryParse(data.getString(KEY_TYPE));

        if (typeId == null)
        {
            return null;
        }

        float health = data.getFloat(KEY_HEALTH);
        float maxHealth = data.contains(KEY_MAX_HEALTH) ? data.getFloat(KEY_MAX_HEALTH) : health;
        UUID owner = data.hasUUID(KEY_OWNER) ? data.getUUID(KEY_OWNER) : null;

        return new SpiderCarrierData(typeId, health, maxHealth, data.getString(KEY_CUSTOM_NAME), owner,
                data.getInt(KEY_ABILITY_COOLDOWN));
    }

    @Nullable
    public static SpiderCarrierData from(ItemStack stack)
    {
        return stack.hasTag() ? load(stack.getTag()) : null;
    }

    public ResourceLocation getTypeId()
    {
        return this.typeId;
    }

    /** @return the health the spider had when it was picked up. */
    public float getHealth()
    {
        return this.health;
    }

    /** @return the health the spider had at full life, used for its tooltip. */
    public float getMaxHealth()
    {
        return this.maxHealth;
    }

    @Nullable
    public UUID getOwnerUUID()
    {
        return this.owner;
    }

    /** @return the rest the ability of the spider still had when it was picked up, 0 when it was ready. */
    public int getAbilityCooldownTicks()
    {
        return this.abilityCooldownTicks;
    }

    @Nullable
    public Component getCustomName()
    {
        if (this.customNameJson.isEmpty())
        {
            return null;
        }

        return Component.Serializer.fromJson(this.customNameJson);
    }

    /** @return the name of the species, the vanilla name when the species is unknown. */
    public Component getSpeciesName()
    {
        return EntityType.byString(this.typeId.toString()).map(EntityType::getDescription)
                .orElse(Component.literal(this.typeId.toString()));
    }

    /** @return what the carrier is called: the name of the spider, or the name of its species. */
    public Component getDisplayName()
    {
        Component name = this.getCustomName();

        return name == null ? this.getSpeciesName() : name;
    }
}
