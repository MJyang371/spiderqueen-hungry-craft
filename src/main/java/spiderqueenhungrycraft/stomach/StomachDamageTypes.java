package spiderqueenhungrycraft.stomach;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The two damage types of the stomach system (Phase 8E1-GATE2B).
 *
 * <p>{@code stomach_link} is what the owner feels when a prey hits the wall of their stomach: the amount the wall
 * really took, carried over one to one, with the original attacker still attached, so death reports and every
 * other combat hook keep naming the real killer.
 *
 * <p>{@code stomach_swallowed} is what a death is relabelled with when a player dies inside the stomach of
 * somebody else, so the death is reported as being swallowed instead of the accident that happened to end it.
 *
 * <p>Both types are datapack entries of this mod and both carry the tags that keep the second defense of the owner
 * out of the way (see the tag files of this mod). If a pack ever removes them, the whole system falls back to the
 * ordinary sources instead of losing the damage completely.
 */
public final class StomachDamageTypes
{
    public static final ResourceKey<DamageType> STOMACH_LINK = key("stomach_link");
    public static final ResourceKey<DamageType> STOMACH_SWALLOWED = key("stomach_swallowed");

    private StomachDamageTypes()
    {
    }

    private static ResourceKey<DamageType> key(String path)
    {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, path));
    }

    /** @return true for the damage the stomach carries from a core to its owner. */
    public static boolean isStomachLink(DamageSource source)
    {
        return source != null && source.is(STOMACH_LINK);
    }

    /** @return true for the damage a swallowed death is reported with. */
    public static boolean isSwallowed(DamageSource source)
    {
        return source != null && source.is(STOMACH_SWALLOWED);
    }

    /**
     * @return the link source: the original direct entity (an arrow, for example) and the original causing entity
     *         (the player or mob behind it) are kept, so the owner knows exactly who is hitting the wall.
     */
    public static DamageSource link(ServerLevel level, @Nullable Entity direct, @Nullable Entity causing)
    {
        return of(level, STOMACH_LINK, direct, causing, level.damageSources().genericKill());
    }

    /** @return the source of being swallowed by that owner, or a nameless swallow when they are away. */
    public static DamageSource swallowed(ServerLevel level, @Nullable Entity cellOwner)
    {
        return of(level, STOMACH_SWALLOWED, cellOwner, cellOwner, level.damageSources().genericKill());
    }

    /** @return the source of that type, with the vanilla fallback when the data file of the type is missing. */
    private static DamageSource of(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity direct,
            @Nullable Entity causing, DamageSource fallback)
    {
        return level.registryAccess().registry(Registries.DAMAGE_TYPE).flatMap(registry -> registry.getHolder(type))
                .<DamageSource>map(holder -> new DamageSource(holder, direct, causing)).orElse(fallback);
    }
}
