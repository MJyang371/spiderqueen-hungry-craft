package spiderqueenhungrycraft;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.loot.HungryCraftLootModifiers;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.registry.HungryCraftAttributes;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.registry.HungryCraftCreativeTabs;
import spiderqueenhungrycraft.registry.HungryCraftEffects;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.registry.HungryCraftParticles;
import spiderqueenhungrycraft.registry.HungryCraftPotions;
import spiderqueenhungrycraft.registry.HungryCraftSounds;
import spiderqueenhungrycraft.soul.SoulRules;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(SpiderQueenHungryCraft.MODID)
public class SpiderQueenHungryCraft
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "spiderqueen_hungrycraft";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    public SpiderQueenHungryCraft(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Gameplay logic lives in its own classes and is wired up through the event annotations there.
        // Only registries, the network channel and the configuration are registered here.
        HungryCraftBlocks.register(modEventBus);
        HungryCraftBlockEntities.register(modEventBus);
        HungryCraftAttributes.register(modEventBus);
        HungryCraftSounds.register(modEventBus);
        HungryCraftEffects.register(modEventBus);
        HungryCraftPotions.register(modEventBus);
        HungryCraftItems.register(modEventBus);
        HungryCraftCreativeTabs.register(modEventBus);
        HungryCraftEntities.register(modEventBus);
        HungryCraftParticles.register(modEventBus);
        HungryCraftLootModifiers.register(modEventBus);
        HungryCraftNetwork.register();

        // The world rule of the soul system (Soul Foundation V1) has to exist before the game builds its first
        // GameRules: the rule types are collected in a static map, and a level that was created without it could
        // never read it. The constructor of the mod is the one moment that is early enough for that.
        SoulRules.register();

        context.registerConfig(ModConfig.Type.COMMON, HungryCraftConfig.SPEC);
        LOGGER.info("Spider Queen: Hungry Craft loaded");
    }
}
