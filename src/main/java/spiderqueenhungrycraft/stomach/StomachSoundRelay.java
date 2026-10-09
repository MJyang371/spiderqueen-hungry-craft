package spiderqueenhungrycraft.stomach;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.devour.DevourTuning;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CStomachSoundPacket;

/**
 * The live sound relay of the stomach (Phase 8E2A-POLISH-2).
 *
 * <p>A stomach used to be a quiet place for its owner: whatever happened inside was invisible and inaudible from
 * the outside. This class closes the ear: every ordinary sound that is really played inside a loaded cell is
 * carried to the owner of that stomach, wherever they are, at half of its volume.
 *
 * <p>It is not a recording and nothing is buffered: Forge hands every sound of a level to this class with its
 * event, the position of that sound is resolved to the canonical cell of that stomach and to the canonical owner
 * of that cell, and the owner receives the metadata of the sound. The client plays the same sound event again
 * near its own body. Because the relay works on sound events and not on entity types, a mob of another mod is
 * relayed exactly like a zombie is.
 *
 * <p>Three things are never relayed: a sound of the categories that make no sense outside their world (music,
 * records, weather, ambient), the rumble of this mod itself, and any sound of a cell whose owner stands inside
 * that very cell - they hear it directly, and a relay would double it.
 *
 * <p>Two light limits keep a busy stomach from flooding a client: one cell sends at most
 * {@link DevourTuning#RELAY_MAX_PER_CELL_TICK} sounds per tick, and the very same sound of the same cell is
 * merged into one relay within {@link DevourTuning#RELAY_DUPLICATE_TICKS} ticks.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachSoundRelay
{
    /** The categories a stomach relays: everything a creature or an action inside it can make. */
    private static final Set<SoundSource> ALLOWED = EnumSet.of(SoundSource.HOSTILE, SoundSource.NEUTRAL,
            SoundSource.PLAYERS, SoundSource.BLOCKS, SoundSource.MASTER);

    /** The id of the rumble of this mod, which is never relayed. */
    private static final ResourceLocation RUMBLE =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "stomach_rumble");

    /** The budget of one cell within the current tick. */
    private static final class Budget
    {
        private long tick = -1L;
        private int sent;
        private final Map<ResourceLocation, Long> lastSent = new HashMap<>();

        private boolean allowed(long now, ResourceLocation sound)
        {
            if (this.tick != now)
            {
                this.tick = now;
                this.sent = 0;
            }

            if (this.sent >= DevourTuning.RELAY_MAX_PER_CELL_TICK)
            {
                return false;
            }

            Long last = this.lastSent.get(sound);

            if (last != null && now - last < DevourTuning.RELAY_DUPLICATE_TICKS)
            {
                return false;
            }

            this.lastSent.put(sound, now);
            this.sent++;

            return true;
        }
    }

    private static final Map<Integer, Budget> BUDGETS = new HashMap<>();

    private StomachSoundRelay()
    {
    }

    /** Every sound that the game plays in a level passes through here. Only the stomach reacts to it. */
    @SubscribeEvent
    public static void onLevelSound(PlayLevelSoundEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(StomachCellManager.DIMENSION)
                || !ALLOWED.contains(event.getSource()))
        {
            return;
        }

        Holder<SoundEvent> sound = event.getSound();

        if (sound == null)
        {
            return;
        }

        ResourceLocation id = sound.value().getLocation();

        if (RUMBLE.equals(id))
        {
            return;
        }

        Vec3 position = positionOf(event);

        if (position == null)
        {
            return;
        }

        relay(level, position, id, event.getSource(), event.getNewVolume(), event.getNewPitch());
    }

    /** @return where the sound came from: the entity it belongs to, or the fixed position it was played at. */
    private static Vec3 positionOf(PlayLevelSoundEvent event)
    {
        if (event instanceof PlayLevelSoundEvent.AtEntity atEntity)
        {
            return atEntity.getEntity().position();
        }

        if (event instanceof PlayLevelSoundEvent.AtPosition atPosition)
        {
            return atPosition.getPosition();
        }

        return null;
    }
    /** Resolves the sound to its canonical cell and owner, and hands a quiet copy to that owner. */
    private static void relay(ServerLevel level, Vec3 position, ResourceLocation sound, SoundSource source,
            float volume, float pitch)
    {
        StomachCell cell = StomachCellManager.resolveCellAt(level, position).orElse(null);

        if (cell == null)
        {
            return;
        }

        ServerPlayer owner = StomachOwnership.onlineOwnerOf(level.getServer(), cell);

        if (owner == null)
        {
            return;
        }

        // The owner is standing in this very cell: they hear the original sound, a relay would double it.
        if (StomachCellManager.resolveCell(owner).filter(own -> own.index() == cell.index()).isPresent())
        {
            return;
        }

        long now = level.getGameTime();

        if (!BUDGETS.computeIfAbsent(cell.index(), index -> new Budget()).allowed(now, sound))
        {
            return;
        }

        float relayed = volume * DevourTuning.RELAY_VOLUME_FACTOR;

        HungryCraftNetwork.sendToPlayer(
                new S2CStomachSoundPacket(owner.getUUID(), cell.index(), sound, source, relayed, pitch), owner);

        if (SpiderQueenHungryCraft.LOGGER.isDebugEnabled())
        {
            SpiderQueenHungryCraft.LOGGER.debug("[RELAY_FX] cell {} relays {} ({}) volume={} pitch={} to {}",
                    cell.index(), sound, source, relayed, pitch, owner.getUUID());
        }
    }

    /** The budgets of closed cells are forgotten, so the map can never grow with the world. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || BUDGETS.isEmpty())
        {
            return;
        }

        if (ServerLifecycleHooks.getCurrentServer() == null)
        {
            BUDGETS.clear();
            return;
        }

        if (BUDGETS.size() > 256)
        {
            BUDGETS.clear();
        }
    }
}
