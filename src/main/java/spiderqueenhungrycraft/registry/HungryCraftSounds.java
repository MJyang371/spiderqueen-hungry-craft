package spiderqueenhungrycraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The sound events of the mod (Phase 8E1-GATE2B).
 *
 * <p>The rumble of the stomach is a real sound event of the game now, because the music of the stomach biome has to
 * name it like any other music of any other biome. The file behind it stays the same {@code stomach_rumble.ogg}.
 */
public final class HungryCraftSounds
{
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, SpiderQueenHungryCraft.MODID);

    /** The only background music of a stomach. */
    public static final RegistryObject<SoundEvent> STOMACH_RUMBLE = SOUND_EVENTS.register("stomach_rumble",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "stomach_rumble")));

    /**
     * The little chime of a collected soul remnant (Soul Foundation round): played once, at the place where the
     * souls of a lost life were picked up, together with the golden burst of the collection.
     */
    public static final RegistryObject<SoundEvent> FIND_SOUL = SOUND_EVENTS.register("find_soul",
            () -> SoundEvent.createVariableRangeEvent(
                    ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "find_soul")));

    private HungryCraftSounds()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        SOUND_EVENTS.register(modEventBus);
    }
}
