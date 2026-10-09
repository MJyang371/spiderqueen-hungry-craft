package spiderqueenhungrycraft.loot;

import com.mojang.serialization.Codec;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Global loot modifiers of the mod (Phase 6-SIMPLE-3). Loaded from
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.
 */
public final class HungryCraftLootModifiers
{
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, SpiderQueenHungryCraft.MODID);

    public static final RegistryObject<Codec<QueenWebHarvestModifier>> QUEEN_WEB_HARVEST =
            LOOT_MODIFIERS.register("queen_web_harvest", () -> QueenWebHarvestModifier.CODEC);

    /**
     * The soul containers of the world (Soul Consumable round): the modifier that may add one stack of a lump of soul
     * to a loot container of a structure. Its rule book is data of this mod
     * ({@code data/spiderqueen_hungrycraft/soul_loot/}), so the modifier itself carries no knowledge of any structure.
     */
    public static final RegistryObject<Codec<SoulContainerLootModifier>> SOUL_CONTAINER =
            LOOT_MODIFIERS.register("soul_container", () -> SoulContainerLootModifier.CODEC);

    private HungryCraftLootModifiers()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        LOOT_MODIFIERS.register(modEventBus);
    }
}
