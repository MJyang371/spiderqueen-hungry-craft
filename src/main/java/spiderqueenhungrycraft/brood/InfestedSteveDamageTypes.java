package spiderqueenhungrycraft.brood;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The damage type of the self slaughter of an infested Steve (LEEROY JENKINS!! round).
 *
 * <p>{@code self_slaughter} is what a brood body dies of when the queen orders it to cut its own throat. It is a type of
 * this mod and a datapack entry like the two stomach types, so the death of the body can be reported as what it was
 * ("%1$s fell on their own blade") instead of the nameless "was killed" of the game.
 *
 * <p>The source carries <b>no attacker at all</b>, on purpose: nobody killed this body, and nothing may be blamed for it.
 * No pack is provoked by it, no kill is credited to anybody and no revenge memory is written - the body is simply gone.
 *
 * <p>Like the stomach types it is registered in the vanilla damage tags of this mod (armour, enchantments, resistance,
 * shield and invulnerability frames are all bypassed): an order to die is not something a helmet, a shield or a second of
 * invulnerability may argue with. Should a pack ever delete the data file of this type, the whole feature falls back to
 * the ordinary kill source of the game instead of losing the death.
 */
public final class InfestedSteveDamageTypes
{
    public static final ResourceKey<DamageType> SELF_SLAUGHTER = key("self_slaughter");

    private InfestedSteveDamageTypes()
    {
    }

    private static ResourceKey<DamageType> key(String path)
    {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, path));
    }

    /** @return true for the death a brood body inflicts on itself. */
    public static boolean isSelfSlaughter(DamageSource source)
    {
        return source != null && source.is(SELF_SLAUGHTER);
    }

    /** @return the source of that type, with the vanilla kill source as the fallback of a missing data file. */
    public static DamageSource selfSlaughter(ServerLevel level)
    {
        return level.registryAccess().registry(Registries.DAMAGE_TYPE)
                .flatMap(registry -> registry.getHolder(SELF_SLAUGHTER))
                .<DamageSource>map(holder -> new DamageSource(holder)).orElse(level.damageSources().genericKill());
    }
}
