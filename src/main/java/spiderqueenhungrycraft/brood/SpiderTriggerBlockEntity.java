package spiderqueenhungrycraft.brood;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.registry.HungryCraftBlockEntities;

/**
 * The memory and the watch of a spider trigger (Spider Trigger round).
 *
 * <p><b>What it remembers.</b> The player who placed it - the one queen a trigger reports to and the one whose own
 * creatures never set it off - and the block it was disguised as, which is what its plate is drawn with. The disguise
 * is written as the registry name of a block, so a world that is loaded without that mod simply comes back with the
 * plain stone plate of the trigger instead of a broken one.
 *
 * <p><b>What it watches.</b> Once every {@link SpiderTriggerBlock#SCAN_INTERVAL_TICKS} ticks the sphere around the
 * block is searched for living creatures, and every creature that is <b>not</b> the placer, one of her spiders or one
 * of her tamed animals is an intruder. Every intruder is an <b>edge</b> and not a state: a creature that is already
 * inside is remembered in {@link #inside} and never reported twice, and the moment it leaves that set forgets it, so
 * the same creature walking in a second time is a second report - the fire once per arrival rule of the design.
 *
 * <p><b>What an intrusion does.</b> Two things, and both of them start in {@link #report}: one pulse of redstone
 * (queued in {@link #pendingPulses} so that two creatures in the same tick really do produce two signals instead of
 * one long one) and one action bar alert to the placer together with five seconds of outline on the creature. The
 * alert and the outline are only sent when the placer is online; the redstone works either way, because a trigger is
 * a machine first.
 */
public class SpiderTriggerBlockEntity extends BlockEntity
{
    /** NBT key of the player this trigger reports to. */
    private static final String KEY_OWNER = "TriggerOwner";

    /** NBT key of the block this trigger is disguised as. */
    private static final String KEY_DISGUISE = "TriggerDisguise";

    /** The placer of this trigger: the queen it reports to and the one creature it never reports about. */
    @Nullable
    private UUID owner;

    /** The block whose top is drawn on the plate, or null while the plate is still the plain stone one. */
    @Nullable
    private BlockState disguise;

    /**
     * The creatures that are inside the detection sphere right now.
     *
     * <p>Runtime only, and that is deliberate: a creature standing inside when the chunk comes back <b>is</b> inside,
     * so the first scan after a reload simply says so once more. This set is the memory of an <b>edge</b>, and an
     * edge nobody watched is not a promise a block can keep.
     */
    private final Set<UUID> inside = new HashSet<>();

    /**
     * How many redstone pulses are still owed.
     *
     * <p>Runtime only. Each arrival adds one; the block emits them one after another with a short gap, so two
     * creatures in the same tick produce two signals that a lamp and an observer both really see.
     */
    private int pendingPulses;

    /** How many ticks have been counted since the block entity was loaded, for the scan rhythm. */
    private int scanCount;

    public SpiderTriggerBlockEntity(BlockPos pos, BlockState state)
    {
        super(HungryCraftBlockEntities.SPIDER_TRIGGER.get(), pos, state);
    }

    // ------------------------------------------------------------------ the placer and the disguise

    public void setOwner(@Nullable UUID owner)
    {
        this.owner = owner;
    }

    /** @return the player this trigger reports to, or null for a trigger that was placed by anything else. */
    @Nullable
    public UUID getOwner()
    {
        return this.owner;
    }

    /** @return the block this trigger is disguised as, or null while it shows its own stone plate. */
    @Nullable
    public BlockState getDisguise()
    {
        return this.disguise;
    }

    /**
     * Disguises this trigger as that block: the remembered block, the model flag of the block state and the packet
     * that tells every client watching the chunk.
     *
     * <p>The state and the memory are both written here and never separately, so the empty model of a disguised
     * trigger and the plate its renderer draws can never disagree about anything.
     */
    public void setDisguise(Level level, BlockPos pos, BlockState state, BlockState want)
    {
        this.disguise = want;
        this.setChanged();
        this.applyDisguisedState(level, pos, state);

        // The clients that already know this chunk have to be told what the plate is drawn with.
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /** And the packet of a disguise that only came back from a save file. */
    private void applyDisguisedState(Level level, BlockPos pos, BlockState state)
    {
        if (!state.getValue(SpiderTriggerBlock.DISGUISED))
        {
            // The plate of the block model is switched off; the renderer of this block entity takes over.
            level.setBlock(pos, state.setValue(SpiderTriggerBlock.DISGUISED, true),
                    Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
        }
    }

    /** Keeps the model flag of the block state in step with the remembered block, once per tick and cheaply. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SpiderTriggerBlockEntity trigger)
    {
        if (trigger.disguise != null)
        {
            trigger.applyDisguisedState(level, pos, state);
        }

        if (++trigger.scanCount % SpiderTriggerBlock.SCAN_INTERVAL_TICKS != 0)
        {
            return;
        }

        if (level instanceof ServerLevel server)
        {
            trigger.scan(server, pos);
        }
    }

    // ------------------------------------------------------------------ the watch

    /** One sweep of the detection sphere: every new arrival is reported, every departure is forgotten. */
    private void scan(ServerLevel level, BlockPos pos)
    {
        AABB sphere = new AABB(pos).inflate(SpiderTriggerBlock.DETECTION_RANGE);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, sphere, this::isIntruder);

        Set<UUID> present = new HashSet<>();

        for (LivingEntity intruder : found)
        {
            UUID id = intruder.getUUID();
            present.add(id);

            if (this.inside.add(id))
            {
                this.report(level, intruder);
            }
        }

        // Whatever is not inside any more stops being inside: the next arrival of the same creature is a new event.
        this.inside.retainAll(present);
    }

    /**
     * @return true when that creature is one a trigger reports: alive, not a spectator, and neither the placer nor
     *         one of her spiders nor one of her tamed animals.
     */
    private boolean isIntruder(LivingEntity candidate)
    {
        if (!candidate.isAlive() || candidate.isSpectator())
        {
            return false;
        }

        if (this.owner == null)
        {
            // A trigger nobody placed has nobody to protect: everything that walks in is an intruder.
            return true;
        }

        if (candidate instanceof Player player && this.owner.equals(player.getUUID()))
        {
            return false;
        }

        // The brood of the queen: every spider of hers, the imp as much as the infested Steve.
        if (candidate instanceof SpiderMinionEntity spider && this.owner.equals(spider.getQueenOwnerUUID()))
        {
            return false;
        }

        // And every creature of the game this queen has tamed.
        return !(candidate instanceof TamableAnimal pet && pet.isTame() && this.owner.equals(pet.getOwnerUUID()));
    }

    /**
     * One arrival: one redstone pulse, one alert to the placer and one outline on the creature.
     *
     * <p>The two halves of it are independent on purpose. The pulse is the machine, so it always happens. The alert
     * and the outline are for a player, so they only happen when the placer is online - a trigger of a sleeping queen
     * keeps guarding the place and simply tells nobody.
     */
    private void report(ServerLevel level, LivingEntity intruder)
    {
        this.noteIntrusion(level);

        ServerPlayer placer = level.getServer() == null ? null
                : level.getServer().getPlayerList().getPlayer(this.owner);

        if (placer == null || placer.connection == null)
        {
            return;
        }

        BlockPos where = intruder.blockPosition();
        placer.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.trigger_alert",
                intruder.getDisplayName(), where.getX(), where.getY(), where.getZ()), true);

        // Five seconds of the ordinary outline of the game: it is drawn by the model pass of the creature, so it
        // follows it wherever it goes and is visible through walls, and it expires on its own - which is exactly the
        // "even out of range, for five seconds" the design asks for.
        intruder.addEffect(new MobEffectInstance(MobEffects.GLOWING, SpiderTriggerBlock.GLOW_TICKS, 0, false, false));
    }

    // ------------------------------------------------------------------ the redstone pulse

    /** Books one pulse and starts it when the block is idle. */
    private void noteIntrusion(ServerLevel level)
    {
        if (this.pendingPulses < SpiderTriggerBlock.MAX_PENDING_PULSES)
        {
            ++this.pendingPulses;
        }

        this.pumpPulses(level);
    }

    /**
     * Emits the next owed pulse, one at a time.
     *
     * <p>A signal that is still running is never restarted - that is what makes the pulses countable: the block goes
     * on, off, on, off, one arrival each, instead of staying on for as long as a crowd keeps arriving.
     */
    private void pumpPulses(ServerLevel level)
    {
        BlockState state = this.getBlockState();

        if (this.pendingPulses <= 0)
        {
            if (state.getValue(SpiderTriggerBlock.POWERED))
            {
                this.setPowered(level, state, false);
            }

            return;
        }

        if (state.getValue(SpiderTriggerBlock.POWERED))
        {
            // The pulse of an earlier arrival is still running; the queued ones follow when it ends.
            return;
        }

        this.setPowered(level, state, true);
        level.scheduleTick(this.getBlockPos(), state.getBlock(), SpiderTriggerBlock.PULSE_ON_TICKS);
    }

    /** The scheduled tick of the block: it ends a pulse, waits out the gap and starts the next one. */
    public void onScheduledTick(ServerLevel level)
    {
        BlockState state = this.getBlockState();

        if (state.getValue(SpiderTriggerBlock.POWERED))
        {
            this.pendingPulses = Math.max(0, this.pendingPulses - 1);
            this.setPowered(level, state, false);

            if (this.pendingPulses > 0)
            {
                level.scheduleTick(this.getBlockPos(), state.getBlock(), SpiderTriggerBlock.PULSE_GAP_TICKS);
            }

            return;
        }

        this.pumpPulses(level);
    }

    private void setPowered(ServerLevel level, BlockState state, boolean powered)
    {
        // Flag three: the neighbours have to hear about it (that is the whole point of a signal) and the clients have
        // to see it (a signal source that looks switched off would be a lie).
        level.setBlock(this.getBlockPos(), state.setValue(SpiderTriggerBlock.POWERED, powered), 3);
    }

    // ------------------------------------------------------------------ saving and the client

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);

        if (this.owner != null)
        {
            tag.putUUID(KEY_OWNER, this.owner);
        }

        if (this.disguise != null)
        {
            tag.putString(KEY_DISGUISE, BuiltInRegistries.BLOCK.getKey(this.disguise.getBlock()).toString());
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);

        if (tag.hasUUID(KEY_OWNER))
        {
            this.owner = tag.getUUID(KEY_OWNER);
        }

        this.disguise = readDisguise(tag);
    }

    /**
     * @return the block of that name as a state, or null when the name is unknown - which is what happens to a
     *         disguise of a mod that is not installed any more. The plate then simply stays the stone one of the
     *         trigger itself instead of turning into a broken texture.
     */
    @Nullable
    private static BlockState readDisguise(CompoundTag tag)
    {
        if (!tag.contains(KEY_DISGUISE))
        {
            return null;
        }

        ResourceLocation id = ResourceLocation.tryParse(tag.getString(KEY_DISGUISE));

        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id))
        {
            return null;
        }

        return BuiltInRegistries.BLOCK.get(id).defaultBlockState();
    }

    /** The disguise is the one thing a client needs from this block entity: its renderer draws the plate with it. */
    @Override
    public CompoundTag getUpdateTag()
    {
        CompoundTag tag = super.getUpdateTag();

        if (this.disguise != null)
        {
            tag.putString(KEY_DISGUISE, BuiltInRegistries.BLOCK.getKey(this.disguise.getBlock()).toString());
        }

        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet)
    {
        CompoundTag tag = packet.getTag();

        if (tag != null)
        {
            this.disguise = readDisguise(tag);
        }
    }

    /** The same read on the path a client takes when it loads a whole chunk. */
    @Override
    public void handleUpdateTag(CompoundTag tag)
    {
        this.disguise = readDisguise(tag);
    }
}
