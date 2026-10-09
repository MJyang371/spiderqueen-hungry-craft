package spiderqueenhungrycraft.compat.iceandfire;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * The contract of one woken bone dragon (Necromancy IV round): who holds it, when it ends, and what the dragon was
 * told to do.
 *
 * <p>It lives in the ordinary Forge persistent data of the dragon, under one root key, exactly like the prototype
 * marker next door - so it is saved with the world, survives a chunk unload, a relog and a server restart, and needs
 * no capability and no new field on somebody else's entity.
 *
 * <p><b>The clock is the game time of the server</b> ({@code ServerLevel#getGameTime}), which is the tick counter of
 * the world and not its day/night time. That answers every timing question of the round at once: {@code /time set}
 * does not touch it, it keeps running while the chunk of the dragon is unloaded (so a contract can not be extended by
 * parking a skeleton in a corner nobody loads), it is written into the save file, and it does not advance while the
 * server is switched off.
 *
 * <p>The effect {@code necromancy_countdown} a player sees is <b>not</b> the truth of any of this; it is a copy the
 * server keeps refreshing. A player who removes it with milk has removed a picture.
 *
 * <p>The whole file is pure vanilla: it names no Ice and Fire type, so it compiles and is tested in a workspace
 * without that mod - only the caller decides which entity is worth a contract.
 */
public final class NecromancyContract
{
    /** The one root key this mod writes into the persistent data of a foreign entity for a contract. */
    public static final String ROOT_KEY = "spiderqueen_hungrycraft:necromancy_contract";

    private static final String OWNER = "owner";
    private static final String STARTED_AT = "started_at";
    private static final String EXPIRES_AT = "expires_at";
    private static final String AGING_WAS_DISABLED = "aging_was_disabled";
    private static final String COMMAND = "command";
    private static final String COMMAND_KIND = "kind";
    private static final String COMMAND_X = "x";
    private static final String COMMAND_Y = "y";
    private static final String COMMAND_Z = "z";
    private static final String COMMAND_TARGET = "target";
    private static final String COMMAND_PHASE = "phase";
    private static final String STANCE = "stance";
    private static final String ANCHOR_X = "anchor_x";
    private static final String ANCHOR_Y = "anchor_y";
    private static final String ANCHOR_Z = "anchor_z";

    /** What a contract dragon has been told to do. */
    public enum Order
    {
        /** Nothing: the dragon is under contract and idle. */
        NONE,
        /** Walk or fly to a place. */
        MOVE,
        /** Hunt one creature. */
        ATTACK,
        /** Fly to a place and breathe on it. */
        BOMBARD
    }

    /** Where a bombardment stands, so the dragon can not breathe forever without ever arriving. */
    public enum Phase
    {
        /** On the way to the target. */
        TRAVELLING,
        /** In position, turning towards the target. */
        AIMING,
        /** Breathing. */
        BREATHING
    }

    private final UUID owner;
    private final long startedAt;
    private final long expiresAt;
    private final boolean agingWasDisabled;
    private final Order order;
    @Nullable
    private final BlockPos position;
    @Nullable
    private final UUID target;
    private final Phase phase;
    private final SpiderOrderMode stance;
    @Nullable
    private final BlockPos anchor;

    private NecromancyContract(UUID owner, long startedAt, long expiresAt, boolean agingWasDisabled, Order order,
            @Nullable BlockPos position, @Nullable UUID target, Phase phase, SpiderOrderMode stance,
            @Nullable BlockPos anchor)
    {
        this.owner = owner;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.agingWasDisabled = agingWasDisabled;
        this.order = order;
        this.position = position;
        this.target = target;
        this.phase = phase;
        this.stance = stance;
        this.anchor = anchor;
    }

    /** @return the contract a dragon has just been given, with no order on it yet and the follower stance. */
    public static NecromancyContract started(UUID owner, long now, int durationTicks, boolean agingWasDisabled)
    {
        return new NecromancyContract(owner, now, now + durationTicks, agingWasDisabled, Order.NONE, null, null,
                Phase.TRAVELLING, SpiderOrderMode.FOLLOW, null);
    }

    /** @return the same contract, with that order on it and no target of the other kind. */
    public NecromancyContract withOrder(Order newOrder, @Nullable BlockPos newPosition, @Nullable UUID newTarget)
    {
        return new NecromancyContract(this.owner, this.startedAt, this.expiresAt, this.agingWasDisabled, newOrder,
                newPosition, newTarget, Phase.TRAVELLING, this.stance, this.anchor);
    }

    /** @return the same contract, one step further through a bombardment. */
    public NecromancyContract withPhase(Phase newPhase)
    {
        return new NecromancyContract(this.owner, this.startedAt, this.expiresAt, this.agingWasDisabled, this.order,
                this.position, this.target, newPhase, this.stance, this.anchor);
    }

    /**
     * @return the same contract, with that standing order on it (Necromancy IV round, the brood stances): the very
     *         follow, hold, patrol and free attack of a spider minion, on a skeleton.
     *
     * <p>The anchor is the place the stance keeps: a hold stands on it, a patrol walks its rounds around it and a hunt
     * works the neighbourhood of it - and a follower belongs to its queen instead, so it carries no anchor at all.
     */
    public NecromancyContract withStance(SpiderOrderMode newStance, @Nullable BlockPos newAnchor)
    {
        return new NecromancyContract(this.owner, this.startedAt, this.expiresAt, this.agingWasDisabled, this.order,
                this.position, this.target, this.phase, newStance, newStance.needsAnchor() ? newAnchor : null);
    }

    public UUID owner()
    {
        return this.owner;
    }

    public long startedAt()
    {
        return this.startedAt;
    }

    public long expiresAt()
    {
        return this.expiresAt;
    }

    /** @return true when Ice and Fire had aging switched off for this dragon before the contract took it over. */
    public boolean agingWasDisabled()
    {
        return this.agingWasDisabled;
    }

    public Order order()
    {
        return this.order;
    }

    @Nullable
    public BlockPos position()
    {
        return this.position;
    }

    @Nullable
    public UUID target()
    {
        return this.target;
    }

    public Phase phase()
    {
        return this.phase;
    }

    /** @return the standing order of that dragon: follow, hold, patrol or free attack (Brood stance, this round). */
    public SpiderOrderMode stance()
    {
        return this.stance;
    }

    /** @return the place that stance keeps, or null for a follower (and for an unknown anchor). */
    @Nullable
    public BlockPos anchor()
    {
        return this.anchor;
    }

    public boolean isOwner(UUID who)
    {
        return this.owner.equals(who);
    }

    /** @return true when that game time is at or past the deadline of this contract. */
    public boolean hasExpired(long now)
    {
        return now >= this.expiresAt;
    }

    /** @return how many ticks are left, never negative. */
    public long remaining(long now)
    {
        return Math.max(this.expiresAt - now, 0L);
    }

    /** @return the middle of the target position, or null when this contract has none. */
    @Nullable
    public Vec3 positionCentre()
    {
        return this.position == null ? null : Vec3.atCenterOf(this.position);
    }

    /** Writes this contract, order and all, under {@link #ROOT_KEY}. */
    public void write(Entity dragon)
    {
        CompoundTag root = new CompoundTag();
        root.putUUID(OWNER, this.owner);
        root.putLong(STARTED_AT, this.startedAt);
        root.putLong(EXPIRES_AT, this.expiresAt);
        root.putBoolean(AGING_WAS_DISABLED, this.agingWasDisabled);

        CompoundTag command = new CompoundTag();
        command.putString(COMMAND_KIND, this.order.name());
        command.putInt(COMMAND_PHASE, this.phase.ordinal());

        if (this.position != null)
        {
            command.putInt(COMMAND_X, this.position.getX());
            command.putInt(COMMAND_Y, this.position.getY());
            command.putInt(COMMAND_Z, this.position.getZ());
        }

        if (this.target != null)
        {
            command.putUUID(COMMAND_TARGET, this.target);
        }

        root.put(COMMAND, command);
        root.putString(STANCE, this.stance.id());

        if (this.anchor != null)
        {
            root.putInt(ANCHOR_X, this.anchor.getX());
            root.putInt(ANCHOR_Y, this.anchor.getY());
            root.putInt(ANCHOR_Z, this.anchor.getZ());
        }

        dragon.getPersistentData().put(ROOT_KEY, root);
    }

    /** @return the contract of that entity, or empty when it is not under one. */
    public static Optional<NecromancyContract> of(@Nullable Entity entity)
    {
        if (entity == null)
        {
            return Optional.empty();
        }

        CompoundTag data = entity.getPersistentData();

        if (!data.contains(ROOT_KEY, Tag.TAG_COMPOUND))
        {
            return Optional.empty();
        }

        CompoundTag root = data.getCompound(ROOT_KEY);

        if (!root.hasUUID(OWNER) || !root.contains(EXPIRES_AT, Tag.TAG_LONG))
        {
            return Optional.empty();
        }

        CompoundTag command = root.contains(COMMAND, Tag.TAG_COMPOUND) ? root.getCompound(COMMAND)
                : new CompoundTag();

        return Optional.of(new NecromancyContract(root.getUUID(OWNER), root.getLong(STARTED_AT),
                root.getLong(EXPIRES_AT), root.getBoolean(AGING_WAS_DISABLED), readOrder(command),
                readPosition(command), readTarget(command), readPhase(command),
                SpiderOrderMode.byId(root.getString(STANCE)), readAnchor(root)));
    }

    /** @return true when that entity is under any contract at all. */
    public static boolean isContracted(@Nullable Entity entity)
    {
        return of(entity).isPresent();
    }

    /** Removes the contract, which is how one ends. */
    public static void clear(Entity dragon)
    {
        dragon.getPersistentData().remove(ROOT_KEY);
    }

    /** @return the order of that command tag, NONE when it names nothing this mod knows. */
    private static Order readOrder(CompoundTag command)
    {
        for (Order candidate : Order.values())
        {
            if (candidate.name().equals(command.getString(COMMAND_KIND)))
            {
                return candidate;
            }
        }

        return Order.NONE;
    }

    /** @return the phase of that command tag, the first one when it names nothing this mod knows. */
    private static Phase readPhase(CompoundTag command)
    {
        int ordinal = command.getInt(COMMAND_PHASE);

        return ordinal >= 0 && ordinal < Phase.values().length ? Phase.values()[ordinal] : Phase.TRAVELLING;
    }

    /** @return the place of that command tag when it carries all three coordinates, else null. */
    @Nullable
    private static BlockPos readPosition(CompoundTag command)
    {
        if (!command.contains(COMMAND_X) || !command.contains(COMMAND_Y) || !command.contains(COMMAND_Z))
        {
            return null;
        }

        return new BlockPos(command.getInt(COMMAND_X), command.getInt(COMMAND_Y), command.getInt(COMMAND_Z));
    }

    /** @return the victim of that command tag, or null. */
    @Nullable
    private static UUID readTarget(CompoundTag command)
    {
        return command.hasUUID(COMMAND_TARGET) ? command.getUUID(COMMAND_TARGET) : null;
    }

    /** @return the anchor of that contract, or null when it carries none. */
    @Nullable
    private static BlockPos readAnchor(CompoundTag root)
    {
        if (!root.contains(ANCHOR_X) || !root.contains(ANCHOR_Y) || !root.contains(ANCHOR_Z))
        {
            return null;
        }

        return new BlockPos(root.getInt(ANCHOR_X), root.getInt(ANCHOR_Y), root.getInt(ANCHOR_Z));
    }
}
