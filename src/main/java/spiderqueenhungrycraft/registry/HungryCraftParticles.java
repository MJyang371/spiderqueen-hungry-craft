package spiderqueenhungrycraft.registry;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The particle types of the mod (Phase 8E2A-POLISH-2).
 *
 * <p>Both types are the dark green digestive fluid of the stomach: a droplet that is thrown out of the mouth of
 * a beast when it snaps at something, and a slightly smaller one that drips off a victim while it is being
 * digested. They deliberately share one visual language - the same vanilla dust sprites and the very same colour
 * - with the cloud of a regurgitation, so a player reads all three as one and the same fluid.
 *
 * <p>No texture is added by this mod. The particle json files of both types list the very sprite set the vanilla
 * dust particle uses ({@code minecraft:generic_0} ... {@code minecraft:generic_7}), which is the whole reason the
 * droplets look exactly like the dust of a vomit.
 */
public final class HungryCraftParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, SpiderQueenHungryCraft.MODID);

    /** The droplet a beast throws out of its mouth when it snaps: the larger of the two. */
    public static final RegistryObject<SimpleParticleType> GASTRIC_DROPLET =
            PARTICLE_TYPES.register("gastric_droplet", () -> new SimpleParticleType(false));

    /** The droplet that drips off a creature while it is digested: a little smaller. */
    public static final RegistryObject<SimpleParticleType> GASTRIC_DRIP =
            PARTICLE_TYPES.register("gastric_drip", () -> new SimpleParticleType(false));

    private HungryCraftParticles()
    {
    }

    public static void register(IEventBus modEventBus)
    {
        PARTICLE_TYPES.register(modEventBus);
    }
}
