package spiderqueenhungrycraft.beast;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The damage source of a beast bite (Phase 8B2).
 *
 * <p>The bite uses its own damage type so a kill is reported as a bite instead of a sword swing, while every
 * rule of the game stays untouched: the source names the queen as both the direct and the causing entity, so
 * armour, resistance, damage events, death attribution, mob retaliation and the pack aggro of the mod all run
 * through the ordinary vanilla pipeline.
 *
 * <p>The type itself is a datapack entry of this mod. If a pack ever removes it, the bite falls back to the
 * ordinary player attack, so a bite can never become harmless because of a missing data file.
 */
public final class QueenBeastDamageTypes
{
    public static final ResourceKey<DamageType> BEAST_BITE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "beast_bite"));

    /** The landing of a charged jump (Phase 8C). */
    public static final ResourceKey<DamageType> BEAST_STOMP = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "beast_stomp"));

    /** The impact of a continuous charge (Phase 8D). */
    public static final ResourceKey<DamageType> BEAST_CHARGE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "beast_charge"));

    private QueenBeastDamageTypes()
    {
    }

    /** @return the damage source of a bite of that beast, with real player attribution. */
    public static DamageSource bite(ServerPlayer player)
    {
        return of(player, BEAST_BITE);
    }

    /** @return the damage source of a stomp landing of that beast. */
    public static DamageSource stomp(ServerPlayer player)
    {
        return of(player, BEAST_STOMP);
    }

    /** @return the damage source of a charge impact of that beast. */
    public static DamageSource charge(ServerPlayer player)
    {
        return of(player, BEAST_CHARGE);
    }

    /** The queen is both the direct and the causing entity, so the whole vanilla pipeline keeps working. */
    private static DamageSource of(ServerPlayer player, ResourceKey<DamageType> type)
    {
        return player.level().registryAccess().registry(Registries.DAMAGE_TYPE)
                .flatMap(registry -> registry.getHolder(type))
                .<DamageSource>map(holder -> new DamageSource(holder, player, player))
                .orElseGet(() -> player.damageSources().playerAttack(player));
    }
}
