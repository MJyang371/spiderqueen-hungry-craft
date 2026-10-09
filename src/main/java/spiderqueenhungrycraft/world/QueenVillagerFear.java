package spiderqueenhungrycraft.world;

import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * Villagers are afraid of the Spider Queen and remember the first meeting with a one time reputation
 * penalty. The fear uses the vanilla villager brain: writing {@link MemoryModuleType#NEAREST_HOSTILE}
 * makes the vanilla {@code VillagerPanicTrigger} switch the villager into the panic activity, which then
 * walks away from that entity. No custom flee movement is involved.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenVillagerFear
{
    /** How often a villager checks for the Spider Queen. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /** Persistent per villager record of the villagers that already met a given player. */
    private static final String TAG_INITIAL_FEAR_APPLIED = "HungryCraftInitialFearApplied";

    private QueenVillagerFear()
    {
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (!(event.getEntity() instanceof Villager))
        {
            return;
        }

        Villager villager = (Villager) event.getEntity();
        if (villager.level().isClientSide() || villager.tickCount % CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        if (!HungryCraftConfig.villageHostilityEnabled())
        {
            return;
        }

        ServerLevel level = (ServerLevel) villager.level();

        Player scare = QueenVillageHostility.findNearestQueen(level, villager, HungryCraftConfig.villagerFearRange(), false);
        if (scare != null)
        {
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.NEAREST_HOSTILE, scare,
                    HungryCraftConfig.villagerFearMemoryTicks());
        }
        else
        {
            forgetExemptQueen(villager);
        }

        Player met = QueenVillageHostility.findNearestQueen(level, villager, HungryCraftConfig.villagerFearRange(), true);
        if (met != null)
        {
            applyInitialFear(villager, met);
        }
    }

    /**
     * Drops a fear memory that points at a queen who is currently protected by Hero of the Village, so the
     * village stops fleeing. Later, when the effect runs out, the villager starts fearing her again.
     */
    private static void forgetExemptQueen(Villager villager)
    {
        Optional<LivingEntity> hostile = villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE);

        // The pattern variable is the whole check: a memory that points at anything but a player is not the fear this
        // touches, so no cast is ever made on it (round 9 safety pass over the faction code).
        if (hostile.isEmpty() || !(hostile.get() instanceof Player queen) || !QueenVillageHostility.isExemptByHero(queen))
        {
            return;
        }

        villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
    }

    /**
     * One time handling of the first meeting between this villager and this player. The meeting is always
     * recorded, so the penalty is never applied twice and never applied later just because Hero of the
     * Village ended in between.
     */
    private static void applyInitialFear(Villager villager, Player player)
    {
        if (!HungryCraftConfig.initialVillagerReputationPenaltyEnabled())
        {
            return;
        }

        CompoundTag data = villager.getPersistentData();
        CompoundTag applied = data.getCompound(TAG_INITIAL_FEAR_APPLIED);
        String key = player.getUUID().toString();
        if (applied.getBoolean(key))
        {
            return;
        }

        applied.putBoolean(key, true);
        data.put(TAG_INITIAL_FEAR_APPLIED, applied);

        if (QueenVillageHostility.isExemptByHero(player))
        {
            return;
        }

        // Vanilla reputation: major negative gossip weights -5 per point, so this is a clear but not
        // crippling price increase that the player can undo with the normal vanilla mechanics.
        villager.getGossips().add(player.getUUID(), GossipType.MAJOR_NEGATIVE,
                HungryCraftConfig.initialVillagerNegativeGossipValue());
    }
}
