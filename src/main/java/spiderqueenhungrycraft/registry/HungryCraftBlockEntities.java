package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderEggBlockEntity;
import spiderqueenhungrycraft.brood.SpiderMineBlockEntity;
import spiderqueenhungrycraft.brood.SpiderTriggerBlockEntity;

/** Block entities of the mod. */
public final class HungryCraftBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SpiderQueenHungryCraft.MODID);

    /**
     * The one block entity of the mod so far: a spider egg on the ground remembers who placed it, so the
     * spider that hatches out of it belongs to that player.
     */
    public static final RegistryObject<BlockEntityType<SpiderEggBlockEntity>> SPIDER_EGG =
            BLOCK_ENTITIES.register("spider_egg", () -> BlockEntityType.Builder
                    .of(SpiderEggBlockEntity::new, HungryCraftBlocks.SPIDER_EGG.get()).build(null));

    /**
     * The memory of a spider mine (Spider Mine and Egg Arrow round): the player who placed it, and therefore the queen
     * of the eight spiders its blast leaves behind. Nobody but the placer ever owns them, however the mine is triggered.
     *
     * <p>The delayed mine (Spider Trigger round) uses the very same memory and the very same type: its block carries
     * the same one value, and a second type for one UUID would be a second thing to keep in step for no reason.
     */
    public static final RegistryObject<BlockEntityType<SpiderMineBlockEntity>> SPIDER_MINE =
            BLOCK_ENTITIES.register("spider_mine", () -> BlockEntityType.Builder
                    .of(SpiderMineBlockEntity::new, HungryCraftBlocks.SPIDER_MINE.get(),
                            HungryCraftBlocks.DELAYED_SPIDER_MINE.get())
                    .build(null));

    /**
     * The memory and the watch of a spider trigger (Spider Trigger round): the placer it reports to and the block it
     * is disguised as.
     */
    public static final RegistryObject<BlockEntityType<SpiderTriggerBlockEntity>> SPIDER_TRIGGER =
            BLOCK_ENTITIES.register("spider_trigger", () -> BlockEntityType.Builder
                    .of(SpiderTriggerBlockEntity::new, HungryCraftBlocks.SPIDER_TRIGGER.get()).build(null));

    private HungryCraftBlockEntities()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        BLOCK_ENTITIES.register(modEventBus);
    }
}
