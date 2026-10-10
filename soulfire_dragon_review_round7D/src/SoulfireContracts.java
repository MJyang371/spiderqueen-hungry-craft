package spiderqueenhungrycraft.soulfire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The contract ledger of the soul fire dragons (round 7E, stage A3).
 *
 * <p><b>The problem it solves.</b> The remains a unit was raised from used to live in exactly one place: inside the
 * unit. That is enough while the unit is alive and holding its contract, and it is not enough the moment anything goes
 * wrong - a unit that can not be put into the world, a restore the other mod refuses, a unit that dies, a server that
 * stops between "the unit is gone" and "the remains are back". In every one of those moments the queen's bone dragon
 * would be gone for good, and a bone dragon is not a resource this mod is allowed to lose.
 *
 * <p><b>So the remains are written to the world before they are taken out of it.</b> Every contract is one record here,
 * and the record carries everything needed to lay the remains down again: their whole saved tag, their identity, the
 * kind and the stage they were, the dimension and the spot they belong back in, and - the important part - a
 * {@link State} that says where the contract is:
 *
 * <ul>
 * <li>{@link State#ACTIVE} - the unit is alive with the contract on it, and it carries the remains in its own tag as
 * well, so either copy is enough,</li>
 * <li>{@link State#PENDING} - the unit is gone and the remains are <b>not</b> back yet. This is the state that must
 * never be lost, and it is retried until it succeeds,</li>
 * <li>{@link State#DONE} - the remains were laid back down. The record is dropped.</li>
 * </ul>
 *
 * <p><b>It is stored with the overworld</b>, like the soul remnant registry of this mod, so a record written in any
 * dimension survives a restart of the server, and a unit whose chunk nobody loads still has a contract the world
 * remembers.
 */
public final class SoulfireContracts extends SavedData
{
    private static final String FILE_NAME = "spiderqueenhungrycraft_soulfire_contracts";

    private static final String KEY_CONTRACTS = "Contracts";
    private static final String KEY_SOUL = "Soul";
    private static final String KEY_ORIGINAL = "Original";
    private static final String KEY_ORIGIN = "Origin";
    private static final String KEY_STAGE = "Stage";
    private static final String KEY_REMAINS = "Remains";
    private static final String KEY_EXPIRES = "ExpiresAt";
    private static final String KEY_STATE = "State";
    private static final String KEY_ATTEMPTS = "Attempts";
    private static final String KEY_DIMENSION = "Dim";
    private static final String KEY_X = "X";
    private static final String KEY_Y = "Y";
    private static final String KEY_Z = "Z";
    private static final String KEY_YAW = "Yaw";
    private static final String KEY_WHY = "Why";

    /** Where a contract is (see the class comment: only PENDING is a promise the world still owes). */
    public enum State
    {
        ACTIVE, PENDING, DONE
    }

    private final Map<UUID, Contract> contracts = new LinkedHashMap<>();

    /** @return the ledger of that server, stored with its overworld so it always exists. */
    public static SoulfireContracts get(MinecraftServer server)
    {
        return server.overworld().getDataStorage()
                .computeIfAbsent(SoulfireContracts::load, SoulfireContracts::new, FILE_NAME);
    }

    /** @return the ledger of the server that level belongs to. */
    public static SoulfireContracts get(ServerLevel level)
    {
        return get(level.getServer());
    }

    /** Writes a contract, replacing whatever was known about that identity before. */
    public void put(Contract contract)
    {
        this.contracts.put(contract.soul(), contract);
        setDirty();
    }

    /** @return the contract of that unit, or null when this mod knows nothing about it. */
    @Nullable
    public Contract find(UUID soul)
    {
        return this.contracts.get(soul);
    }

    /** Drops a contract, which is what a finished restore does. */
    public void drop(UUID soul)
    {
        if (this.contracts.remove(soul) != null)
        {
            setDirty();
        }
    }

    /** @return every contract that still owes remains, which is what the retry sweep works on. */
    public List<Contract> pending()
    {
        List<Contract> owed = new ArrayList<>();

        for (Contract contract : this.contracts.values())
        {
            if (contract.state() == State.PENDING)
            {
                owed.add(contract);
            }
        }

        return owed;
    }

    /** @return every contract of the ledger, for a debug read out. */
    public Collection<Contract> all()
    {
        return List.copyOf(this.contracts.values());
    }

    /** @return how many contracts the ledger holds, whatever their state. */
    public int size()
    {
        return this.contracts.size();
    }

    /**
     * One contract.
     *
     * @param soul      the identity of the unit that was raised (its temporary identity, never the original one)
     * @param original  the identity the remains come back with
     * @param origin    what the remains were, one of {@code SoulfireDragonEntity.Origin}
     * @param stage     the growth stage of those remains
     * @param remains   their whole saved entity tag, or null when it could not be read
     * @param expiresAt the game time the contract runs out
     * @param state     where the contract is
     * @param attempts  how many times a PENDING restore was tried, for the log and for the test
     * @param dimension where the remains belong back in
     * @param x         where they belong back in, on the safe spot the restore was prepared with
     * @param y         the same
     * @param z         the same
     * @param yaw       the facing the remains are laid back down with
     * @param why       a short human sentence about why the record is in its state, for the log
     */
    public record Contract(UUID soul, UUID original, String origin, int stage, @Nullable CompoundTag remains,
            long expiresAt, State state, int attempts, @Nullable ResourceKey<Level> dimension, double x, double y,
            double z, float yaw, String why)
    {
        /** @return a copy of this contract in another state, ready for a restore at that place. */
        public Contract with(State next, int nextAttempts, @Nullable ResourceKey<Level> at, double px, double py,
                double pz, float pyaw, String reason)
        {
            return new Contract(this.soul, this.original, this.origin, this.stage, this.remains, this.expiresAt, next,
                    nextAttempts, at, px, py, pz, pyaw, reason);
        }
    }

    // ------- saving and loading -------

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();

        for (Contract contract : this.contracts.values())
        {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(KEY_SOUL, contract.soul());
            entry.putUUID(KEY_ORIGINAL, contract.original());
            entry.putString(KEY_ORIGIN, contract.origin());
            entry.putInt(KEY_STAGE, contract.stage());
            entry.putLong(KEY_EXPIRES, contract.expiresAt());
            entry.putString(KEY_STATE, contract.state().name());
            entry.putInt(KEY_ATTEMPTS, contract.attempts());
            entry.putDouble(KEY_X, contract.x());
            entry.putDouble(KEY_Y, contract.y());
            entry.putDouble(KEY_Z, contract.z());
            entry.putFloat(KEY_YAW, contract.yaw());
            entry.putString(KEY_WHY, contract.why());

            if (contract.remains() != null)
            {
                // The whole point of the ledger: the remains themselves go to disk, not a reference to them.
                entry.put(KEY_REMAINS, contract.remains());
            }

            if (contract.dimension() != null)
            {
                entry.putString(KEY_DIMENSION, contract.dimension().location().toString());
            }

            list.add(entry);
        }

        tag.put(KEY_CONTRACTS, list);

        return tag;
    }

    /** @return the ledger read back from a save. */
    public static SoulfireContracts load(CompoundTag tag)
    {
        SoulfireContracts ledger = new SoulfireContracts();
        ListTag list = tag.getList(KEY_CONTRACTS, Tag.TAG_COMPOUND);

        for (int index = 0; index < list.size(); index++)
        {
            CompoundTag entry = list.getCompound(index);

            if (!entry.hasUUID(KEY_SOUL) || !entry.hasUUID(KEY_ORIGINAL))
            {
                // A record without its two identities can not be honoured, and guessing one would be worse than
                // dropping it: it is said out loud and skipped.
                spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.error(
                        "soulfire ledger: a contract without an identity was dropped on load: {}", entry);

                continue;
            }

            State state;

            try
            {
                state = State.valueOf(entry.getString(KEY_STATE));
            }
            catch (IllegalArgumentException unknown)
            {
                // A state this build does not know is treated as "still owed": never as "done".
                state = State.PENDING;
            }

            ResourceKey<Level> dimension = entry.contains(KEY_DIMENSION)
                    ? ResourceKey.create(Registries.DIMENSION, new ResourceLocation(entry.getString(KEY_DIMENSION)))
                    : null;

            Contract contract = new Contract(entry.getUUID(KEY_SOUL), entry.getUUID(KEY_ORIGINAL),
                    entry.getString(KEY_ORIGIN), Math.max(1, entry.getInt(KEY_STAGE)),
                    entry.contains(KEY_REMAINS, Tag.TAG_COMPOUND) ? entry.getCompound(KEY_REMAINS) : null,
                    entry.getLong(KEY_EXPIRES), state, entry.getInt(KEY_ATTEMPTS), dimension, entry.getDouble(KEY_X),
                    entry.getDouble(KEY_Y), entry.getDouble(KEY_Z), entry.getFloat(KEY_YAW), entry.getString(KEY_WHY));

            ledger.contracts.put(contract.soul(), contract);
        }

        return ledger;
    }
}
