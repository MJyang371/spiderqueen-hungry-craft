package spiderqueenhungrycraft.compat.iceandfire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The contract dragons of one queen, as units of her sceptre (Necromancy IV round, the sceptre half of it).
 *
 * <p><b>Why this class exists at all.</b> The real time strategy of the brood is written end to end around
 * {@code SpiderMinionEntity}: the selection set holds spider ids, {@code SpiderCommand} hands out spiders, and every
 * goal of a minion is a spider goal. A bone dragon of Ice and Fire is none of those things and must not be made one.
 * So the sceptre gets a <b>second kind of unit</b> beside the spiders, and this class is that kind:
 *
 * <ul>
 * <li><b>the same selection</b> - a contract dragon is chosen by clicking it with the sceptre exactly like a spider,
 * and its uuid is written into the same {@code BroodSelectionSet} of the queen. There is no second set to keep in
 * step, no second key and no second item: a queen who has a skeleton on a payroll and a brood in the field commands
 * both with the sceptre in her hand,</li>
 * <li><b>the same orders</b> - the order of a dragon is the {@link NecromancyContract.Order} that is written into the
 * contract of that dragon (its persistent data), which is what makes it survive a chunk unload, a relog and a server
 * restart for free,</li>
 * <li><b>its own execution</b> - the spiders keep their goals ({@code SpiderMinionEntity} is not touched by a single
 * line of this round), and a dragon is driven by two goals of the compatibility, which are installed while a contract
 * lives and removed when it ends.</li>
 * </ul>
 *
 * <p><b>Nothing here is Ice and Fire by accident.</b> This file names no type of that mod at all, so it compiles,
 * loads and is tested in a workspace without it - only the goals that really move a dragon need the other mod. Every
 * command is also refused the same way twice: once here, from the server side truth about who holds what, and once
 * inside the goals of the dragon before they act on it.
 */
public final class NecromancyOrders
{
    /** How far above the point a queen clicked the dragon takes its bombardment position. */
    public static final int BOMBARD_HEIGHT = 12;

    /**
     * How many blocks of open air above a point a bombardment needs before it is flown instead of walked (Necromancy
     * IV round).
     *
     * <p>It is the rule of the round in its own words - twenty five blocks above the point with nothing in them - and it
     * is asked of the <b>column</b> and not of the open sky of the game: a point in a valley that a mountain never
     * covers is a point a dragon may hover over, and a point under a single leaf is not.
     */
    public static final int SKY_HEIGHT = 25;

    /** How far a contract dragon may be from its queen before the hard rally teleports it home instead of walking. */
    private static final double RALLY_DISTANCE = 12.0D;

    /** How many places beside a queen are tried before one pull of a dragon is given up. */
    private static final int PULL_ATTEMPTS = 12;

    private NecromancyOrders()
    {
    }

    /**
     * The contract dragons of that queen that answer a command of her sceptre right now: <b>the selected ones, and
     * nothing else</b>, exactly like {@code SpiderCommand#commanded} does it for the brood.
     *
     * <p>A command is sent to the units a queen picked, never to "whatever is nearby": a dragon she has not chosen
     * keeps its order, and a selection that happens to hold no dragon costs this method one empty list. A selected
     * dragon whose chunk is not loaded, or that moved to another dimension, is simply not answered this time - the
     * order stays on the contract and is executed the moment that dragon is loaded again.
     */
    public static List<Entity> commanded(ServerPlayer queen)
    {
        List<Entity> chosen = new ArrayList<>();
        Collection<UUID> selected = selectionOf(queen);

        if (selected.isEmpty() || !(queen.level() instanceof ServerLevel level))
        {
            return chosen;
        }

        for (UUID id : selected)
        {
            Entity dragon = level.getEntity(id);

            if (isContractDragonOf(queen, dragon))
            {
                chosen.add(dragon);
            }
        }

        return chosen;
    }

    /** @return true when that creature is a dragon under a contract of that queen, loaded or not. */
    public static boolean isContractDragonOf(ServerPlayer queen, @Nullable Entity candidate)
    {
        return NecromancyService.isContractOwner(candidate, queen);
    }

    /**
     * @return the entity ids of the selected contract dragons that are loaded right now, for the outline of the
     *         selection on the client of that queen.
     */
    public static List<Integer> selectedEntityIds(ServerPlayer queen)
    {
        List<Integer> ids = new ArrayList<>();

        for (Entity dragon : commanded(queen))
        {
            ids.add(dragon.getId());
        }

        return ids;
    }

    /**
     * The move order of the sceptre, for the dragons: every selected contract dragon of that queen walks or flies to
     * that spot, and an older command of those dragons is replaced by it.
     *
     * @return how many dragons were ordered
     */
    public static int moveTo(ServerPlayer queen, BlockPos target)
    {
        int sent = 0;

        for (Entity dragon : commanded(queen))
        {
            write(dragon, NecromancyContract.Order.MOVE, target, null, NecromancyContract.Phase.TRAVELLING);
            sent++;
        }

        return sent;
    }

    /**
     * The attack order of the sceptre, for the dragons: every selected contract dragon of that queen goes for that
     * victim.
     *
     * <p><b>A friend is never a victim.</b> The queen herself, a spider of her brood and another dragon of her own
     * contract are refused here, in the same breath as the order is given, so the order is not even written - and the
     * targeting goal of the dragon refuses them a second time, every tick, in case a contract changed hands in
     * between.
     *
     * @return how many dragons accepted the order
     */
    public static int attack(ServerPlayer queen, LivingEntity victim)
    {
        int ordered = 0;

        for (Entity dragon : commanded(queen))
        {
            if (dragon == victim || NecromancyService.isFriendlyToContract(dragon, victim))
            {
                continue;
            }

            write(dragon, NecromancyContract.Order.ATTACK, null, victim.getUUID(),
                    NecromancyContract.Phase.TRAVELLING);
            ordered++;
        }

        return ordered;
    }

    /**
     * The bombardment order of the sceptre, for the dragons: every selected contract dragon of that queen flies to a
     * point in the air above that spot and breathes on it.
     *
     * <p>The stage of the dragon is <b>not</b> checked here: a contract dragon that is too young refuses the order
     * inside its own goal, where the real Ice and Fire stage can be read, and the queen is told there. What is written
     * here is the order and the point, which is all the contract has to carry.
     *
     * @return how many dragons were ordered
     */
    public static int bombard(ServerPlayer queen, BlockPos target)
    {
        int sent = 0;

        for (Entity dragon : commanded(queen))
        {
            bombard(dragon, target);
            sent++;
        }

        return sent;
    }

    /**
     * Sends one contract dragon on a bombardment of that point.
     *
     * <p>It is the single write of that order, used by the sceptre click of the queen and by the ability key of the
     * brood (whose spiders have an ability of their own and whose skeletons breathe). The dragon flies to a point above
     * the place and breathes on it until it is given another order.
     */
    public static void bombard(Entity dragon, BlockPos target)
    {
        write(dragon, NecromancyContract.Order.BOMBARD, target, null, NecromancyContract.Phase.TRAVELLING);
    }

    /** Drops the order of that dragon: it is idle again, under the same contract. */
    public static void clearOrder(Entity dragon)
    {
        write(dragon, NecromancyContract.Order.NONE, null, null, NecromancyContract.Phase.TRAVELLING);
    }

    /**
     * @return true when the column above that point is open for {@value #SKY_HEIGHT} blocks, which is the one question
     *         that decides the two shapes of a bombardment: one that is flown, and one that is walked (Necromancy IV
     *         round).
     *
     * <p>It is asked of the world and not of the dragon, so the goal that calls it needs no type of Ice and Fire for it.
     */
    public static boolean skyOpen(Level level, BlockPos point)
    {
        for (int up = 1; up <= SKY_HEIGHT; up++)
        {
            if (!level.isEmptyBlock(point.above(up)))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @return the contract dragons of that queen that are inside a box right now, which is the other half of the drag
     *         selection of her sceptre (Necromancy IV round): the spiders come from {@code SpiderCommand}, these come
     *         from here, and both halves are found by the server so no client can name a unit it does not own.
     *
     * <p>The search is a box query of the level and a read of the persistent data of every living creature in it, and it
     * is deliberately not a per tick question: it is asked once per drag of the mouse, like the one for the spiders.
     */
    public static List<Entity> dragonsIn(ServerPlayer queen, AABB box)
    {
        List<Entity> chosen = new ArrayList<>();

        for (LivingEntity candidate : queen.level().getEntitiesOfClass(LivingEntity.class, box))
        {
            if (isContractDragonOf(queen, candidate))
            {
                chosen.add(candidate);
            }
        }

        return chosen;
    }

    /**
     * The standing order of the sceptre, for the dragons (Necromancy IV round, the brood stances).
     *
     * <p>It is the same list of stances a spider minion has - follow, hold, patrol, free attack - written into the
     * contract of the dragon, so that a queen commands a skeleton and a spider with the very same keys and the very
     * same words. The anchor is the place a hold stands on, a patrol walks its rounds around and a hunt works; a
     * follower carries none, because it belongs to its queen.
     */
    public static void setStance(Entity dragon, SpiderOrderMode stance, @Nullable BlockPos anchor)
    {
        NecromancyContract.of(dragon).ifPresent(contract -> contract.withStance(stance, anchor).write(dragon));

        // Leaving a post is getting up. The pose of a holding dragon is written by the goal that keeps the post (see
        // IceAndFireSupport#setSitting), and a goal that is no longer allowed to run - because a follower that is close
        // enough does not walk, and a hunter that is idle does - could never clear it: so the order that ends a post
        // clears it, in the same breath as it is given.
        if (stance != SpiderOrderMode.HOLD)
        {
            IceAndFireSupport.setSitting(dragon, false);
        }

        NecromancyService.resendState(dragon);
    }

    /** @return the standing order of that dragon, {@code FOLLOW} when it is not under a contract at all. */
    public static SpiderOrderMode stanceOf(@Nullable Entity dragon)
    {
        return NecromancyContract.of(dragon).map(NecromancyContract::stance).orElse(SpiderOrderMode.FOLLOW);
    }

    /**
     * The soft rally of the brood keys (the U key), for the dragons: every selected contract dragon becomes a
     * <b>follower</b> of its queen, and not one of them is moved by it.
     *
     * @return how many dragons turned around
     */
    public static int followHere(ServerPlayer queen)
    {
        int called = 0;

        for (Entity dragon : commanded(queen))
        {
            setStance(dragon, SpiderOrderMode.FOLLOW, null);
            called++;
        }

        return called;
    }

    /**
     * The stand-down key of the brood keys, for the dragons: every selected contract dragon guards the block it
     * happens to stand on right now, each on its own post.
     *
     * <p><b>A post is an interrupt and not a wish</b> (Necromancy IV round 5). It has to outrank an errand, so the
     * standing order of every selected dragon is cleared first, exactly the way {@link #clearOrder} lets a march go:
     * that takes the errand off the contract, which is the only reason the goal that holds a post was not allowed to
     * run at all. Then the Ice and Fire half of the interruption is written by {@link IceAndFireSupport#interrupt} -
     * the victim, the path, the move control, the vertical control, a running breath and the hover - and only after
     * both of those is the stance written. The goal that owns the stance then walks the skeleton home, brings it down
     * to the ground if it was flying, and lies it down there.
     *
     * @return how many dragons really stood down
     */
    public static int holdHere(ServerPlayer queen)
    {
        int held = 0;

        for (Entity dragon : commanded(queen))
        {
            NecromancyContract.Order interrupted = orderOf(dragon);

            // 1. the errand goes, and with it every reason the post goal had not to run
            clearOrder(dragon);

            // 2. and the body is taken off it: victim, path, move control, vertical control, breath, hover
            IceAndFireSupport.interrupt(dragon);

            // 3. and only now the order that replaces all of it
            setStance(dragon, SpiderOrderMode.HOLD, dragon.blockPosition());

            NecromancyDiagnostics.log(() -> "HOLD written for " + dragon.getUUID()
                    + ": interrupted errand=" + interrupted + " post=" + dragon.blockPosition().toShortString()
                    + ", stance=HOLD; " + IceAndFireSupport.describeState(dragon));

            held++;
        }

        return held;
    }

    /**
     * @return every loaded contract dragon of that queen, asked of the <b>index</b> of her level rather than by a world
     *         scan (Necromancy IV round 5).
     *
     * <p>It is the dragon half of "everything I can see" of the sceptre - the same question
     * {@code SpiderCommand#ownedLoaded} answers for the spiders - and answering it from the index is what makes it
     * cheap and exact at once: the index is the list the contract itself maintains, so a dragon whose chunk is not
     * loaded is simply not in the answer and a dragon of somebody else is filtered out by its own contract.
     */
    public static List<Entity> ownedLoaded(ServerPlayer queen)
    {
        List<Entity> owned = new ArrayList<>();

        if (!(queen.level() instanceof ServerLevel level))
        {
            return owned;
        }

        for (UUID id : NecromancyIndex.get(level).dragons())
        {
            Entity dragon = level.getEntity(id);

            if (isContractDragonOf(queen, dragon))
            {
                owned.add(dragon);
            }
        }

        return owned;
    }

    /**
     * The free attack command, for the dragons: every selected contract dragon <b>switches its hunt on</b> - it walks
     * the rounds of a patrol and attacks everything alive that does not stand on the side of its contract.
     *
     * <p>Asking for the hunt again is not a new command, exactly like for the spiders: a dragon that is already hunting
     * keeps the ground it hunts on, and only a dragon that was doing something else takes the place it stands on now.
     *
     * @return how many dragons started hunting
     */
    public static int freeAttack(ServerPlayer queen)
    {
        int sent = 0;

        for (Entity dragon : commanded(queen))
        {
            BlockPos anchor = stanceOf(dragon) == SpiderOrderMode.FREE_ATTACK
                    ? anchorOf(dragon, dragon.blockPosition())
                    : dragon.blockPosition();

            setStance(dragon, SpiderOrderMode.FREE_ATTACK, anchor);
            sent++;
        }

        return sent;
    }

    /**
     * The order key of the brood keys, for the dragons: the selected contract dragons step through the order cycle
     * together, as one group - follow, hold, patrol, free attack, follow ...
     *
     * <p>The rules are the rules of the spiders, because they are the rules of the gesture: a group is cycled from the
     * state of the group (a mixed group is unified into followers by the first press and walks the cycle from there),
     * the way into hold takes the block each dragon stands on as its post, hold to patrol keeps that post, and patrol
     * back to follow drops it.
     *
     * @return the stance the group holds now, or null when no contract dragon answered
     */
    @Nullable
    public static SpiderOrderMode cycleStance(ServerPlayer queen)
    {
        List<Entity> units = commanded(queen);

        if (units.isEmpty())
        {
            return null;
        }

        SpiderOrderMode first = stanceOf(units.get(0));

        for (Entity dragon : units)
        {
            if (stanceOf(dragon) != first)
            {
                // A mixed group is unified before it is cycled, so the first press of the key can never take a group
                // somewhere nobody asked for.
                for (Entity member : units)
                {
                    setStance(member, SpiderOrderMode.FOLLOW, null);
                }

                return SpiderOrderMode.FOLLOW;
            }
        }

        SpiderOrderMode next = first.next();

        for (Entity dragon : units)
        {
            BlockPos anchor = next == SpiderOrderMode.PATROL
                    ? anchorOf(dragon, dragon.blockPosition())
                    : dragon.blockPosition();

            setStance(dragon, next, anchor);
        }

        return next;
    }

    /** @return the anchor of that dragon's contract, or that fallback when it carries none. */
    private static BlockPos anchorOf(Entity dragon, BlockPos fallback)
    {
        BlockPos anchor = NecromancyContract.of(dragon).map(NecromancyContract::anchor).orElse(null);

        return anchor == null ? fallback : anchor;
    }

    /**
     * The hard rally of the sceptre wheel, for the dragons (Necromancy IV round): every loaded contract dragon of that
     * queen comes home, whatever it was doing - errand, victim and errand are dropped, it becomes a follower again, and
     * one that is too far away is <b>set down beside its queen</b> instead of walking, exactly like the spiders of the
     * brood are pulled home by the same command.
     *
     * @return how many dragons answered the call
     */
    public static int rally(ServerPlayer queen)
    {
        int called = 0;

        for (Entity dragon : ownedLoaded(queen))
        {
            if (!(dragon instanceof Mob minion))
            {
                continue;
            }

            NecromancyContract.Order interrupted = orderOf(minion);

            clearOrder(minion);
            setStance(minion, SpiderOrderMode.FOLLOW, null);
            minion.setDeltaMovement(Vec3.ZERO);

            if (minion.distanceToSqr(queen) > RALLY_DISTANCE * RALLY_DISTANCE)
            {
                pullNear(minion, queen);
            }

            NecromancyDiagnostics.log(() -> "rally: dragon=" + minion.getUUID() + " interrupted errand=" + interrupted
                    + " stance=FOLLOW; " + IceAndFireSupport.describeState(minion));

            called++;
        }

        return called;
    }

    /**
     * @return every loaded contract dragon of that queen, in no particular order: the very same list
     *         {@link #ownedLoaded} answers.
     *
     * <p><b>One source of truth</b> (Necromancy IV round 6). This method used to scan {@code level.getAllEntities()} and
     * ask each entity about its contract, while {@link #ownedLoaded} asked the <b>index</b> of the contract - two answers
     * to one question, and the slow one was the one the hard rally used. There is only one list of the dragons of a
     * queen, it is the index the contract itself maintains, and both names now return that list.
     */
    public static List<Entity> loadedAll(ServerPlayer queen)
    {
        return ownedLoaded(queen);
    }

    /**
     * Sets one dragon down on a free spot beside that creature, the way a pet that fell behind is put next to its
     * master.
     *
     * <p>It is the one emergency move of a contract dragon, used by the follow stance when its queen got away and by the
     * hard rally. The places are tried in the neighbourhood, and the game itself refuses any of them that would put the
     * animal inside a wall or overlapping another body - a dragon of five blocks of wingspan is harder to set down than
     * a spider, so a few places are tried before one attempt is given up.
     *
     * @return true when the dragon really arrived
     */
    public static boolean pullNear(Mob dragon, Entity anchor)
    {
        for (int attempt = 0; attempt < PULL_ATTEMPTS; attempt++)
        {
            int x = anchor.blockPosition().getX() + dragon.getRandom().nextInt(11) - 5;
            int z = anchor.blockPosition().getZ() + dragon.getRandom().nextInt(11) - 5;
            int y = anchor.blockPosition().getY() + dragon.getRandom().nextInt(4);

            if (!dragon.level().isEmptyBlock(new BlockPos(x, y, z))
                    || !dragon.level().isEmptyBlock(new BlockPos(x, y + 1, z)))
            {
                continue;
            }

            if (dragon.randomTeleport(x + 0.5D, y, z + 0.5D, true))
            {
                dragon.getNavigation().stop();

                return true;
            }
        }

        return false;
    }

    /** Moves one bombardment of that dragon to its next phase. */
    public static void writePhase(Entity dragon, NecromancyContract.Phase phase)
    {
        NecromancyContract.of(dragon).ifPresent(contract -> contract.withPhase(phase).write(dragon));
    }

    /** @return the order of that dragon, {@code NONE} when it is not under a contract at all. */
    public static NecromancyContract.Order orderOf(@Nullable Entity dragon)
    {
        return NecromancyContract.of(dragon).map(NecromancyContract::order).orElse(NecromancyContract.Order.NONE);
    }

    /** @return the contract of that dragon, for the goals that have to read the point of an order. */
    public static Optional<NecromancyContract> contractOf(@Nullable Entity dragon)
    {
        return NecromancyContract.of(dragon);
    }

    /** @return the creature one dragon was ordered at, when it is still loaded and still worth attacking. */
    public static Optional<LivingEntity> orderedVictim(@Nullable Entity dragon)
    {
        Optional<NecromancyContract> contract = NecromancyContract.of(dragon);

        if (contract.isEmpty() || contract.get().order() != NecromancyContract.Order.ATTACK)
        {
            return Optional.empty();
        }

        UUID victim = contract.get().target();

        if (victim == null || !(dragon.level() instanceof ServerLevel level))
        {
            return Optional.empty();
        }

        return level.getEntity(victim) instanceof LivingEntity living && living.isAlive() && !living.isRemoved()
                ? Optional.of(living)
                : Optional.empty();
    }

    /** Writes one whole order into the contract of that dragon, keeping its owner and its deadline untouched. */
    private static void write(Entity dragon, NecromancyContract.Order order, @Nullable BlockPos position,
            @Nullable UUID target, NecromancyContract.Phase phase)
    {
        NecromancyContract.of(dragon).ifPresent(contract -> contract.withOrder(order, position, target)
                .withPhase(phase).write(dragon));

        // One line per order, which is the one place where "did my command reach the dragon" is answered. It is written
        // after the write, so a line in the log always means the contract really carries that order now.
        NecromancyDiagnostics.log("order written: dragon=" + dragon.getUUID() + " order=" + order + " phase=" + phase
                + " pos=" + (position == null ? "none" : position.toShortString())
                + " target=" + (target == null ? "none" : target.toString()));

        // And the queen is told, so the panel she sees on that skeleton is never a stale one.
        NecromancyService.resendState(dragon);
    }

    /** @return the units that queen has selected, empty when she has selected nothing. */
    private static Collection<UUID> selectionOf(ServerPlayer queen)
    {
        return QueenDataCapability.get(queen).map(data -> data.broodSelection().ids()).orElse(List.of());
    }
}
