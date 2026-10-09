package spiderqueenhungrycraft.soulfire;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
 * The rising of a soul fire dragon, and the giving back of the remains it was raised from (round 7C).
 *
 * <p><b>One remains in, one soul fire dragon out.</b> This is the whole change of the round: the skill no longer wakes
 * the corpse of the other mod and drives that animal around, it <b>takes the corpse away</b> and puts a creature of
 * this mod in its place, owned by the queen who cast the skill, under the same ten minute contract.
 *
 * <p><b>The remains are not destroyed, they are carried.</b> The exact state of the original bone dragon - its type,
 * its identity and its whole saved tag - is written <b>into</b> the soul fire dragon, and it is what is laid back down
 * when the contract ends: an ice bone dragon becomes a soul fire dragon, and it becomes an ice bone dragon again.
 *
 * <p>Every Ice and Fire type is named behind the facade of the compatibility ({@link IceAndFireSupport}), so this file
 * compiles, loads and is testable in a workspace without that mod.
 */
public final class SoulfireRising
{
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
     * <p>The refusals are the same ones the skill always had - no other mod, no bone dragon, no server level - but the
     * success is a different animal: the corpse is <b>removed</b> (its state having been copied into the new unit) and
     * the dragon takes its place, so the world never holds both the alive dragon and its own corpse.
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
        level.addFreshEntity(dragon);

        // The corpse is gone: what it was now lives inside the unit, and it is handed back the moment the contract is
        // over. Nothing of this mod may ever see that entity again.
        pointed.discard();

        boolean struck = IceAndFireSupport.strikeLightning(level, where);

        List<String> lines = new ArrayList<>();
        lines.add("risen: soulfire dragon " + dragon.getUUID() + " from " + origin + " remains (stage " + stage + ")");
        lines.add("remains: original=" + soul + " stored=" + (remains.state() == null ? "no" : "yes"));
        lines.add("contract: owner=" + queen.getUUID() + " expiresAt=" + dragon.getExpiresAt() + " ("
                + NecromancyService.CONTRACT_TICKS + " ticks)");
        lines.add(struck ? "visual lightning struck at " + where
                : "WARNING: the game refused to build the lightning bolt");

        return new Rising(new ProbeReport(true, lines), dragon);
    }
    /**
     * The end of a contract: the soul leaves the unit and the remains it was raised from are laid back down.
     *
     * <p>It is called by the unit itself when its ten minutes are over, and it is <b>idempotent by construction</b>:
     * the unit is removed first, so a second call - from a pending tick, an operator or a death - finds nothing to do.
     *
     * <p>Without a record (a unit spawned by a debug command rather than raised from a corpse) there is nothing to give
     * back, and the unit simply leaves: no crash and no half restored animal.
     */
    public static void expire(SoulfireDragonEntity dragon)
    {
        if (!(dragon.level() instanceof ServerLevel level) || dragon.isRemoved())
        {
            return;
        }

        CompoundTag remains = dragon.getOriginalBone();
        UUID originalBone = dragon.getOriginalBoneUuid();
        BlockPos where = dragon.blockPosition();
        float yaw = dragon.getYRot();
        String origin = dragon.getOrigin().name();
        int stage = dragon.getStage();
        UUID soul = dragon.getUUID();

        // The unit goes first, so that nothing of it can act between this line and the restore below.
        dragon.discard();

        if (remains == null || originalBone == null || !IceAndFireSupport.present())
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "soulfire dragon {} left: no remains were recorded for it, so nothing is laid back down", soul);

            return;
        }

        boolean restored = IceAndFireSupport.restoreBone(level, new IceAndFireSupport.BoneRecord(origin, stage, remains),
                originalBone, Vec3.atBottomCenterOf(where), yaw);

        SpiderQueenHungryCraft.LOGGER.info("soulfire dragon {} left: remains {} laid back down ({})", soul,
                originalBone, restored ? "ok" : "REFUSED by Ice and Fire");
    }

    /**
     * @return true when that creature is remains this mod may raise, which is every bone dragon the other mod would
     *         itself allow to be woken.
     */
    public static boolean mayRaise(@Nullable Entity pointed)
    {
        return pointed != null && IceAndFireSupport.isBoneDragon(pointed);
    }

}
