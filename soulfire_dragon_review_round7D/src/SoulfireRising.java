package spiderqueenhungrycraft.soulfire;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;
import spiderqueenhungrycraft.compat.iceandfire.ProbeReport;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The rising of a soul fire dragon, and the giving back of the remains it was raised from (7C, hardened in 7E A3).
 *
 * <p><b>One remains in, one soul fire dragon out.</b> The skill no longer wakes the corpse of the other mod and drives
 * that animal around: it <b>takes the corpse away</b> and puts a creature of this mod in its place, owned by the queen
 * who cast the skill, under the same ten minute contract.
 *
 * <p><b>The remains are not destroyed, they are carried - and now they are also written down.</b> Round 7C kept the
 * whole state of the original bone dragon inside the new unit and laid it back down when the contract ended, which was
 * correct as long as nothing at all went wrong. Round 7E A3 closes the three doors through which a queen could lose
 * her dragon:
 *
 * <ol>
 * <li><b>a unit that can not be put into the world</b> - the corpse is only ever removed <i>after</i> the new unit was
 * really accepted by the level ({@link #handOverCorpse}), and a refused spawn leaves the corpse exactly where it was,</li>
 * <li><b>a restore that fails</b> - the remains are written into the {@link SoulfireContracts ledger} in a PENDING state
 * <i>before</i> the unit is removed, and a PENDING contract is retried until it succeeds, across restarts,</li>
 * <li><b>a unit that dies or expires in mid air</b> - both paths lead into the same release ({@link #release}), and the
 * remains are laid down on the first safe floor under the unit rather than in the sky.</li>
 * </ol>
 *
 * <p>Every Ice and Fire type is named behind the facade of the compatibility ({@link IceAndFireSupport}), so this file
 * compiles, loads and is testable in a workspace without that mod.
 */
public final class SoulfireRising
{
    /** How far under a unit the release looks for a floor before it gives up and uses the spot itself. */
    public static final int LANDING_SEARCH = 48;

    /**
     * What one casting of the skill did.
     *
     * @param report what happened, in the lines the caller logs and the player is told about
     * @param dragon the unit that was raised, or null when the skill was refused
     */
    public record Rising(ProbeReport report, @Nullable SoulfireDragonEntity dragon)
    {
    }

    private SoulfireRising()
    {
    }

    /** @return the answer of a skill that was refused. */
    private static Rising refused(String line)
    {
        return new Rising(ProbeReport.failure(line), null);
    }

    /**
     * The conversion: the remains a queen is looking at rise as one soul fire dragon of hers.
     *
     * <p>The order of the steps is the whole of round 7E A3, and it is deliberate:
     *
     * <ol>
     * <li>the contract is written into the {@link SoulfireContracts ledger} <b>first</b>, so the remains exist in two
     * places (inside the new unit and on disk) before the corpse is touched,</li>
     * <li>the new unit is put into the world and <b>checked</b> ({@link #handOverCorpse}),</li>
     * <li>only then is the corpse removed - and if the unit was refused, the ledger record is dropped again and the
     * corpse is left exactly where it stood.</li>
     * </ol>
     */
    public static Rising raise(ServerPlayer queen, Entity pointed)
    {
        if (!(queen.level() instanceof ServerLevel level))
        {
            return refused("the rising needs a server level");
        }

        if (!IceAndFireSupport.present())
        {
            return refused(IceAndFireSupport.NOT_INSTALLED);
        }

        if (!IceAndFireSupport.isBoneDragon(pointed))
        {
            return refused("refused: that creature is not a bone dragon");
        }

        IceAndFireSupport.BoneRecord remains = IceAndFireSupport.boneRecord(pointed);

        if (remains == null)
        {
            return refused("refused: the remains of that dragon could not be read");
        }

        SoulfireDragonEntity dragon = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (dragon == null)
        {
            return refused("the soul fire dragon could not be built");
        }

        String origin = remains.origin();
        int stage = remains.stage();
        UUID soul = pointed.getUUID();
        Vec3 where = pointed.position();

        dragon.moveTo(pointed.getX(), pointed.getY(), pointed.getZ(), pointed.getYRot(), 0.0F);
        dragon.setOrigin(SoulfireDragonEntity.Origin.byName(origin));
        dragon.setStage(stage);
        dragon.setOriginalBone(remains.state(), soul);
        dragon.beginContract(queen.getUUID(), level.getGameTime() + NecromancyService.CONTRACT_TICKS);
        dragon.setOrderMode(SpiderOrderMode.FOLLOW);

        // Step 1: the contract, with the remains in it, is on disk before anything leaves the world.
        SoulfireContracts ledger = SoulfireContracts.get(level);
        SoulfireContracts.Contract contract = new SoulfireContracts.Contract(dragon.getUUID(), soul, origin, stage,
                remains.state(), dragon.getExpiresAt(), SoulfireContracts.State.ACTIVE, 0, level.dimension(), where.x,
                where.y, where.z, pointed.getYRot(), "raised from those remains");
        ledger.put(contract);

        // Step 2 and 3: the unit in, and only then the corpse out.
        if (!handOverCorpse(level, dragon, pointed))
        {
            ledger.drop(dragon.getUUID());

            return refused("the soul fire dragon could not be put into the world: the remains were left untouched");
        }

        boolean struck = IceAndFireSupport.strikeLightning(level, where);

        List<String> lines = new ArrayList<>();
        lines.add("risen: soulfire dragon " + dragon.getUUID() + " from " + origin + " remains (stage " + stage + ")");
        lines.add("remains: original=" + soul + " stored=" + (remains.state() == null ? "no" : "yes")
                + " ledger=ACTIVE");
        lines.add("contract: owner=" + queen.getUUID() + " expiresAt=" + dragon.getExpiresAt() + " ("
                + NecromancyService.CONTRACT_TICKS + " ticks)");
        lines.add(struck ? "visual lightning struck at " + where
                : "WARNING: the game refused to build the lightning bolt");

        return new Rising(new ProbeReport(true, lines), dragon);
    }

    /**
     * Puts the risen unit into the world and removes the corpse it was raised from, <b>in that order and only if the
     * first step really succeeded</b>.
     *
     * <p>This method is where round 7C could silently eat a bone dragon: it called {@code addFreshEntity} and discarded
     * the corpse on the next line without asking whether the unit had been accepted at all. A refused spawn (a
     * duplicate identity, a full mob cap, a level that is shutting down, another mod cancelling the addition) would
     * then have removed the corpse and produced nothing to replace it.
     *
     * <p>It is package private on purpose: the failure injection of round 7E A3 (a spawn that has to fail) is a test of
     * this method, and a test may arm {@link SoulfireFaults} and call it directly.
     *
     * @return true when the unit is in the world and the corpse is gone; false when the corpse is <b>untouched</b>
     */
    static boolean handOverCorpse(ServerLevel level, SoulfireDragonEntity dragon, Entity corpse)
    {
        boolean injected = SoulfireFaults.takeSpawnFailure();
        boolean accepted = !injected && level.addFreshEntity(dragon);

        if (injected || !accepted || dragon.isRemoved() || level.getEntity(dragon.getUUID()) == null)
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "soulfire rising: the soul fire dragon {} was not accepted by the level ({}): the remains {} stay "
                            + "untouched",
                    dragon.getUUID(), injected ? "refused by the fault injector" : "the level refused it",
                    corpse.getUUID());

            if (!dragon.isRemoved())
            {
                // The unit that could not rise is taken out of the world again: nothing of it may be left behind.
                dragon.discard();
            }

            return false;
        }

        // The corpse is gone: what it was now lives inside the unit and in the ledger, and it is handed back the moment
        // the contract is over. Nothing of this mod may ever see that entity again.
        corpse.discard();

        return true;
    }

    /**
     * The end of a contract: the soul leaves the unit and the remains it was raised from are laid back down.
     *
     * <p>It is called by the unit itself when its ten minutes are over, and it is <b>idempotent by construction</b>: the
     * ledger record is written as PENDING and the unit is removed in the same call, so a second call - from a pending
     * tick, an operator or a death - finds a contract that is already being honoured.
     */
    public static void expire(SoulfireDragonEntity dragon)
    {
        release(dragon, true, "contract over");
    }

    /**
     * The death of a unit: the remains are laid back down as well (round 7E A3), because a bone dragon is not something
     * a queen loses by losing a fight. The unit is <b>not</b> removed here - the game is already removing it - but the
     * contract is marked PENDING and honoured in the same step.
     */
    public static void onDeath(SoulfireDragonEntity dragon)
    {
        release(dragon, false, "killed");
    }

    /**
     * @return true when that creature is remains this mod may raise, which is every bone dragon the other mod would
     *         itself allow to be woken.
     */
    public static boolean mayRaise(@Nullable Entity pointed)
    {
        return pointed != null && IceAndFireSupport.isBoneDragon(pointed);
    }

    /**
     * The one release behind both the end of a contract and the death of a unit (round 7E A3).
     *
     * <p><b>The remains are promised to the world before the unit is taken out of it.</b> The ledger record is written
     * as {@link SoulfireContracts.State#PENDING} first, then the unit leaves, then the restore is attempted - and if it
     * fails, the record stays and is retried. At no point in this sequence does the only copy of the remains exist
     * nowhere: before the first line it is inside the unit, and after the first line it is also on disk.
     *
     * @param removeUnit true when this call is the end of the contract (the unit must go), false when the game is
     *                   already removing the unit (a death)
     * @param why a short human sentence for the log
     */
    private static void release(SoulfireDragonEntity dragon, boolean removeUnit, String why)
    {
        if (!(dragon.level() instanceof ServerLevel level) || dragon.isRemoved())
        {
            return;
        }

        UUID soul = dragon.getUUID();
        CompoundTag remains = dragon.getOriginalBone();
        UUID original = dragon.getOriginalBoneUuid();
        SoulfireContracts ledger = SoulfireContracts.get(level);

        if (remains == null || original == null)
        {
            // A unit that was not raised from remains (a debug spawn): there is nothing to give back, and the record -
            // if there is one - must not keep a promise nobody can honour.
            ledger.drop(soul);

            if (removeUnit)
            {
                dragon.discard();
            }

            SpiderQueenHungryCraft.LOGGER.warn(
                    "DEBUG_UNIT_NO_ORIGINAL_REMAINS: soulfire dragon {} left ({}) and nothing is laid back down, because "
                            + "it was never raised from remains (a spawned unit has no original bone dragon to give back)",
                    soul, why);

            return;
        }

        // The spot the remains belong back in: the first safe floor under the unit, so a contract that ends in the sky
        // does not drop a bone dragon out of the air.
        BlockPos where = safeSpot(level, dragon.blockPosition());
        float yaw = dragon.getYRot();
        int attempts = 0;
        SoulfireContracts.Contract known = ledger.find(soul);

        if (known != null)
        {
            attempts = known.attempts();
        }

        SoulfireContracts.Contract contract = new SoulfireContracts.Contract(soul, original,
                dragon.getOrigin().name(), dragon.getStage(), remains, dragon.getExpiresAt(),
                SoulfireContracts.State.PENDING, attempts, level.dimension(), where.getX() + 0.5D, where.getY(),
                where.getZ() + 0.5D, yaw, why);

        // 1. The promise, on disk, before anything is removed.
        ledger.put(contract);

        // 2. The unit leaves (unless the game is already removing it).
        if (removeUnit)
        {
            dragon.discard();
        }

        // 3. And the remains come back, or the promise stays and is retried.
        if (restore(level, contract))
        {
            ledger.drop(soul);

            SpiderQueenHungryCraft.LOGGER.info("soulfire dragon {} left ({}): remains {} laid back down at {}", soul,
                    why, original, where);
        }
        else
        {
            ledger.put(contract.with(SoulfireContracts.State.PENDING, attempts + 1, level.dimension(),
                    where.getX() + 0.5D, where.getY(), where.getZ() + 0.5D, yaw, why));

            SpiderQueenHungryCraft.LOGGER.warn(
                    "soulfire dragon {} left ({}): the remains {} are NOT back yet - they are kept in the ledger and "
                            + "retried until they are (attempt {})",
                    soul, why, original, attempts + 1);
        }
    }

    /**
     * Lays one contract's remains back down, and answers whether they are really back.
     *
     * <p>Three guards, in this order: a contract without remains is nothing to honour; an identity that already stands
     * in the world means the remains are <b>already back</b> (which is what keeps a retry from growing a second bone
     * dragon - the original identity is the lock); and the other mod has to be there to build it.
     */
    private static boolean restore(ServerLevel level, SoulfireContracts.Contract contract)
    {
        if (contract.remains() == null)
        {
            return false;
        }

        if (level.getEntity(contract.original()) != null)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "soulfire ledger: the remains {} stand in the world already: the contract is done",
                    contract.original());

            return true;
        }

        if (!IceAndFireSupport.present())
        {
            // Not a loss: the record stays, and the sweep lays the remains down when the mod is there again.
            return false;
        }

        if (SoulfireFaults.takeRestoreFailure())
        {
            SpiderQueenHungryCraft.LOGGER.error(
                    "soulfire ledger: the restore of {} was refused by the fault injector: the record stays PENDING",
                    contract.original());

            return false;
        }

        return IceAndFireSupport.restoreBone(level,
                new IceAndFireSupport.BoneRecord(contract.origin(), contract.stage(), contract.remains()),
                contract.original(),
                new Vec3(contract.x(), contract.y(), contract.z()), contract.yaw());
    }

    /**
     * The retry sweep (round 7E A3): every PENDING contract of the server is attempted again.
     *
     * <p>It is driven by the tick hook of this feature, and it is the reason a failed restore is never a loss: the
     * remains live in the ledger of the world until the other mod (or whatever refused the restore) can take them.
     *
     * @return how many contracts were honoured by this sweep
     */
    public static int retryPending(MinecraftServer server)
    {
        int honoured = 0;

        for (SoulfireContracts.Contract contract : SoulfireContracts.get(server).pending())
        {
            if (contract.dimension() == null)
            {
                continue;
            }

            ServerLevel level = server.getLevel(contract.dimension());

            // A dimension nobody has loaded can not be written to, and its record is not lost by waiting.
            if (level == null)
            {
                continue;
            }

            if (restore(level, contract))
            {
                SoulfireContracts.get(server).drop(contract.soul());
                honoured++;
            }
            else if (IceAndFireSupport.present())
            {
                // Only a real attempt is counted and written back: without the other mod nothing was tried, and a sweep
                // that marked the world dirty every half a minute for nothing would be a cost with no promise behind it.
                SoulfireContracts.get(server).put(contract.with(SoulfireContracts.State.PENDING,
                        contract.attempts() + 1, contract.dimension(), contract.x(), contract.y(), contract.z(),
                        contract.yaw(), contract.why()));
            }
        }

        return honoured;
    }

    /**
     * @return the first spot at or under that point where remains can really be laid down: free air with a floor under
     *         it, or the point itself when there is none within {@value #LANDING_SEARCH} blocks.
     */
    public static BlockPos safeSpot(ServerLevel level, BlockPos from)
    {
        for (int down = 0; down <= LANDING_SEARCH; down++)
        {
            BlockPos candidate = from.below(down);

            if (level.isEmptyBlock(candidate) && level.isEmptyBlock(candidate.above())
                    && !level.isEmptyBlock(candidate.below()))
            {
                return candidate;
            }
        }

        // Nothing to stand on - a unit that ended over the void, or in the middle of a cave mouth. The remains are laid
        // down where it was rather than not at all.
        return from;
    }
}
