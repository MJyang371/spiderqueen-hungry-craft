package spiderqueenhungrycraft.compat.iceandfire;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.faction.QueenOwnerProtection;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CNecromancyStatePacket;
import spiderqueenhungrycraft.registry.HungryCraftEffects;

/**
 * The clock of every contract of a level (Necromancy IV round).
 *
 * <p>One pass per tick and per level, and it is <b>free while nothing is under contract</b>: the pass asks the index of
 * the level whether it holds anything at all, and returns on the spot when it does not - no world scan, no entity
 * sweep, no per-tick work of any kind. When there are contracts it walks that short list and nothing else, resolves
 * each dragon by its id (a map lookup that answers null for an unloaded chunk) and either ends the contract or keeps
 * its mark fresh.
 *
 * <p>The deadline is absolute server game time (see {@link NecromancyContract}), so a dragon whose chunk is unloaded
 * is simply skipped this tick: its clock keeps running in the world and the contract is ended the moment that dragon
 * is loaded again. Nothing here ever forces a chunk to load.
 */
public final class NecromancyService
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ten minutes, as the round asks: 12 000 ticks of the world. */
    public static final int CONTRACT_TICKS = 12_000;

    /** How far the effect may lag behind the contract before it is refreshed, in ticks. */
    private static final int EFFECT_DRIFT_TICKS = 40;

    /** A ceiling for the duration written into an effect instance, so nothing can overflow it. */
    private static final long EFFECT_MAX_TICKS = 24L * 60L * 60L * 20L;

    private NecromancyService()
    {
    }

    /** One goal that was detached from a selector, with the priority it had, so it can be put back exactly there. */
    public record RemovedGoal(int priority, Goal goal)
    {
    }

    /**
     * Removes every goal of those kinds from that selector and returns them (Necromancy IV round 7, the AI isolation).
     *
     * <p><b>Idempotent by construction.</b> Removing a goal that is not there is a no-op, so a second call simply
     * returns an empty list - which is what makes the isolation safe across an entity being loaded, a chunk being
     * reloaded and a contract being renewed: the removal is asked once, and nothing is ever removed twice.
     *
     * <p><b>It is deliberately pure vanilla</b>: the kinds are passed in by the caller, so the method itself knows
     * nothing about Ice and Fire, and the no-Ice-and-Fire world loads it like any other class of this mod. The caller
     * that hands it Ice and Fire's goals only ever runs while that mod is present.
     *
     * @return the goals that were removed, newest-to-oldest, so {@link #restoreGoals} can put them back
     */
    public static List<RemovedGoal> detachGoals(GoalSelector selector, Class<?>... kinds)
    {
        List<RemovedGoal> removed = new ArrayList<>();

        for (WrappedGoal wrapped : new ArrayList<>(selector.getAvailableGoals()))
        {
            for (Class<?> kind : kinds)
            {
                if (kind.isInstance(wrapped.getGoal()))
                {
                    removed.add(new RemovedGoal(wrapped.getPriority(), wrapped.getGoal()));
                    selector.removeGoal(wrapped.getGoal());
                    break;
                }
            }
        }

        return removed;
    }

    /** Puts goals back at the priorities they were taken from, in the order they were removed. */
    public static void restoreGoals(GoalSelector selector, List<RemovedGoal> removed)
    {
        for (RemovedGoal entry : removed)
        {
            selector.addGoal(entry.priority(), entry.goal());
        }
    }

    /** One tick of one level: ends what has run out and keeps the rest marked. */
    public static void tickLevel(ServerLevel level)
    {
        if (!IceAndFireSupport.present())
        {
            return;
        }

        NecromancyIndex index = NecromancyIndex.get(level);

        if (index.isEmpty())
        {
            // The ordinary tick of every world that has no skeleton on a payroll.
            return;
        }

        long now = level.getGameTime();

        for (UUID id : index.dragons())
        {
            Entity dragon = level.getEntity(id);

            if (dragon == null)
            {
                // Not loaded right now: the deadline is absolute, so there is nothing to do for it - and nothing may
                // be done to a chunk that is not loaded.
                continue;
            }

            Optional<NecromancyContract> contract = NecromancyContract.of(dragon);

            if (contract.isEmpty())
            {
                // A corpse whose tag is gone (killed, or cleaned by hand): the index has to let go of it.
                index.remove(id);
                continue;
            }

            long remaining = contract.get().remaining(now);

            if (remaining <= 0L)
            {
                end(level, dragon, "the ten minutes are over");
                continue;
            }

            keepMarked(dragon, remaining);

            if (dragon instanceof LivingEntity living)
            {
                IceAndFireSupport.enforceContract(living);
            }
        }
    }

    /**
     * @return the player whose contract holds that dragon, or empty when it is not under one.
     *
     * <p>It is the one question every permission of the contract is decided with, and it is deliberately asked here
     * and nowhere else: {@link #isFriendlyToContract} and the sceptre of the queen do not each carry their own copy of
     * "who owns this skeleton", so a later round can change the rule in one place.
     */
    public static Optional<UUID> contractOwner(@Nullable Entity dragon)
    {
        return NecromancyContract.of(dragon).map(NecromancyContract::owner);
    }

    /** @return true when that creature is a dragon under a live contract at all, whoever holds it. */
    public static boolean isContractedDragon(@Nullable Entity entity)
    {
        return NecromancyContract.isContracted(entity);
    }

    /**
     * @return the player that dragon <b>belongs to</b> right now: the holder of its contract, or - for a dragon that
     *         was woken out of its bones - the player who woke it up.
     *
     * <p><b>Why two sources and not one</b> (the basic fix of this round). The marker of a revived dragon has carried
     * the UUID of the player who woke it since the very first prototype ({@link NecromancyMarker#owner}), but nothing
     * ever <i>read</i> it: every ownership question in this feature was answered from the contract tag alone. A dragon
     * that a player had just woken up was therefore ownerless as far as the game was concerned - it was not his in any
     * way that could be observed - until a contract was written on it. Now the waking itself makes it his: the contract
     * still decides <b>how long</b> the skeleton works for him and what it has been ordered to do, and the marker
     * decides <b>whose</b> it is in between.
     *
     * <p>An expired contract clears the marker with the corpse it makes (see {@code endContract} → {@code restore}), so
     * a dragon can never keep a stale claim from a shift that is over: a contract dragon answers with its contract, and
     * a dragon whose shift ended has no marker left to answer with.
     */
    public static Optional<UUID> ownerOf(@Nullable Entity dragon)
    {
        Optional<UUID> contract = contractOwner(dragon);

        if (contract.isPresent())
        {
            return contract;
        }

        return NecromancyMarker.isRevived(dragon) ? NecromancyMarker.owner(dragon) : Optional.empty();
    }

    /** @return true when that creature has a necromancy owner at all - a contract, or the player who woke it up. */
    public static boolean hasNecromancyOwner(@Nullable Entity dragon)
    {
        return ownerOf(dragon).isPresent();
    }

    /** @return true when that dragon belongs to that player: he holds its contract, or he woke it up. */
    public static boolean belongsTo(@Nullable Entity dragon, @Nullable Entity who)
    {
        return who != null && belongsTo(dragon, who.getUUID());
    }

    /** @return true when that dragon belongs to the player of that uuid: he holds its contract, or he woke it up. */
    public static boolean belongsTo(@Nullable Entity dragon, @Nullable UUID who)
    {
        return who != null && ownerOf(dragon).map(owner -> owner.equals(who)).orElse(false);
    }

    /** @return true when that player holds the contract of that dragon. */
    public static boolean isContractOwner(@Nullable Entity dragon, @Nullable Entity who)
    {
        return who != null && isContractOwner(dragon, who.getUUID());
    }

    /** @return true when the player of that uuid holds the contract of that dragon. */
    public static boolean isContractOwner(@Nullable Entity dragon, @Nullable UUID who)
    {
        return who != null && contractOwner(dragon).map(owner -> owner.equals(who)).orElse(false);
    }

    /**
     * @return true when that creature is on the side of the contract of that dragon: the queen who holds it, a spider
     *         of that queen, or another dragon under a contract of the same queen.
     *
     * <p>This is the single friendly question of the whole feature. The targeting goal of a contracted dragon asks it
     * before it accepts a victim, the sceptre asks it before it writes an attack order, and the damage filter asks it
     * before it lets a claw, a bite or a breath through - so a queen can not be burned by her own skeleton, whatever
     * way the order reached it.
     */
    public static boolean isFriendlyToContract(@Nullable Entity dragon, @Nullable Entity other)
    {
        if (other == null || other == dragon)
        {
            return false;
        }

        // The owner is asked through #ownerOf and not through the contract alone: the dragon belongs to whoever woke it
        // up even before its first contract, and a skeleton is never at war with the side that owns it.
        return ownerOf(dragon).map(owner -> isAllyOf(owner, other)).orElse(false);
    }

    /** @return true when that creature belongs to that queen: herself, her spiders, or her own contract dragons. */
    public static boolean isAllyOf(UUID queen, @Nullable Entity entity)
    {
        if (entity == null)
        {
            return false;
        }

        // QueenOwnerProtection answers this for every creature of a colony, and it answers the contract owner for a
        // dragon under contract as well (see that class), so the two halves of the brood can never disagree.
        return queen.equals(QueenOwnerProtection.ownerOf(entity))
                || entity instanceof Player player && queen.equals(player.getUUID());
    }

    /**
     * Tells the player who holds the contract of that dragon what its state is right now (Necromancy IV round, the
     * inspect panel of the sceptre).
     *
     * <p>It is the same mirror the skeleton texture already travels in, with the three things only the owner is told:
     * that the dragon is hers, the standing order it holds, the errand it is on, and whether it is old enough to be sent
     * on a bombardment. It is sent from here - and not to everybody tracking the dragon - because that sheet is the
     * business of the queen alone, and it is sent again whenever one of those values really changed.
     */
    public static void resendState(Entity dragon)
    {
        Optional<NecromancyContract> contract = NecromancyContract.of(dragon);

        if (contract.isEmpty() || dragon.level().getServer() == null)
        {
            return;
        }

        ServerPlayer owner = dragon.level().getServer().getPlayerList().getPlayer(contract.get().owner());

        if (owner == null || owner.connection == null)
        {
            return;
        }

        HungryCraftNetwork.sendToPlayer(new S2CNecromancyStatePacket(dragon.getId(), true, true,
                contract.get().stance().ordinal(), contract.get().order().ordinal(),
                IceAndFireSupport.canBreathe(dragon)), owner);
    }

    /** Ends every contract of that level, which is all an operator command needs to know. */
    public static int endAll(ServerLevel level, String why)
    {
        NecromancyIndex index = NecromancyIndex.get(level);
        int ended = 0;

        for (UUID id : index.dragons())
        {
            Entity dragon = level.getEntity(id);

            if (dragon != null)
            {
                end(level, dragon, why);
                ended++;
            }
            else
            {
                index.remove(id);
            }
        }

        return ended;
    }

    /**
     * Ends one contract and gives the dragon back to Ice and Fire as the corpse it was.
     *
     * <p>Every step is idempotent on purpose, because this is called from several endings (the clock, the death of the
     * dragon, a broken state and an operator) and the round asks that a second call may not produce a second corpse, a
     * second drop or a changed identity. After the first call there is no contract left to find, so a second one does
     * nothing at all.
     */
    public static void end(ServerLevel level, Entity dragon, String why)
    {
        if (NecromancyContract.of(dragon).isEmpty())
        {
            NecromancyIndex.get(level).remove(dragon.getUUID());

            return;
        }

        LOGGER.info("{} contract ended for {} ({})", IceAndFireSupport.PROBE_LOG_PREFIX, dragon.getUUID(), why);

        // Ice and Fire first, and it is asked to read the contract itself: the aging flag the dragon had before the
        // contract took it over is written in there, and it has to be given back.
        IceAndFireSupport.endContract(dragon);

        // Then the two halves this mod owns: the tag, the index and the visible mark. The tag is cleared here as well
        // as inside the compatibility, so a world whose Ice and Fire was removed mid-contract can still be cleaned up.
        NecromancyContract.clear(dragon);
        NecromancyIndex.get(level).remove(dragon.getUUID());

        if (dragon instanceof LivingEntity living)
        {
            MobEffect mark = HungryCraftEffects.NECROMANCY_COUNTDOWN.get();

            if (living.hasEffect(mark))
            {
                living.removeEffect(mark);
            }
        }
    }

    /**
     * Keeps the visible mark of one dragon in step with its contract.
     *
     * <p>The effect is repaired rather than trusted: anything that removes effects (a milk bucket, a command, another
     * mod) takes a picture away and never a contract, and the next tick draws it again with the time that is really
     * left. That is the whole reason the two are separate: the truth is on the server, the picture is a courtesy.
     */
    private static void keepMarked(Entity dragon, long remaining)
    {
        if (!(dragon instanceof LivingEntity living))
        {
            return;
        }

        MobEffect mark = HungryCraftEffects.NECROMANCY_COUNTDOWN.get();
        MobEffectInstance current = living.getEffect(mark);
        int wanted = (int) Math.min(remaining, EFFECT_MAX_TICKS);

        if (current == null || current.getDuration() < wanted - EFFECT_DRIFT_TICKS)
        {
            living.addEffect(new MobEffectInstance(mark, wanted, 0, false, false, true));
        }
    }
}
