package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.brood.goal.SpiderFollowOwnerGoal;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The group commands of the queen sceptre (Queen Scepter command patch, scoped to the selection by the Brood Command
 * Group round), separated from the item and the network handler so the rules can be read (and asked) without touching an
 * interaction.
 *
 * <p><b>No radius.</b> The sceptre reaches as far as its ray reaches, so a command calls every spider of that queen
 * that is <i>loaded right now in the same dimension</i> - and nothing else. The enumeration walks the loaded entities
 * of the level once, at the moment the queen really gives the order; nothing here polls the world, and nothing here
 * ever force loads a chunk or reaches into another dimension.
 *
 * <p><b>Who answers a command.</b> Every command of this class works on the units {@link #commanded(Player)} hands it, and
 * those are <b>the spiders the queen selected</b> - nobody else, ever. A command without a selection is refused and said
 * out loud instead of being guessed at ({@link #hasSelection(Player)}): the sceptre commands an army that was chosen,
 * exactly like the real time strategy games it is modelled after. A queen who wants everyone to answer selects everyone
 * first, and her keys are exactly that - J for everything she can see, K for every spider of the species she picked, and
 * a drag of the right button for anything else.
 *
 * <p>That single rule is what makes it possible to send one group to one place and another group to another place at the
 * same time: a command touches its own units and leaves every other spider of the brood exactly where it was, with its
 * errand and its order untouched.
 *
 * <p><b>Two rallies, and one exception.</b> {@link #rally(Player)} is the hard one (a command of the sceptre wheel):
 * the whole brood comes home at once and is teleported there, and it is the one command of this class that ignores the
 * selection - an emergency recall has to mean everybody, whatever happens to be picked. {@link #followHere(Player)} is
 * the soft one (the U key): the commanded units become followers of their queen on foot, without a single spider being
 * moved, and it obeys the selection like every other group command.
 *
 * <p>Every command passes the owner protection of the spider itself ({@link SpiderMinionEntity#setForcedTarget}), so no
 * order of a queen can ever make her own brood attack itself, and a persistent order (follow / hold / patrol /
 * free attack) is never touched by a temporary command.
 *
 * <p><b>The hunt and the commands.</b> {@link #freeAttack(Player)} switches the hunt of the selected units on
 * ({@link SpiderOrderMode#FREE_ATTACK}), and every other command of this class switches it off on the way - a spider
 * that is told to walk somewhere, to attack something, to come home or to hold a post is an ordinary follower again. The
 * two commands that are not orders (the scout move and the attack order) say it out loud, the commands that are orders
 * write that order, and the order key walks the cycle of the four orders without being a command in this sense at all.
 */
public final class SpiderCommand
{
    private SpiderCommand()
    {
    }

    /** @return every loaded spider of that queen in the level of that queen, in no particular order. */
    public static List<SpiderMinionEntity> ownedLoaded(Player queen)
    {
        List<SpiderMinionEntity> found = new ArrayList<>();

        // Only the entities of the level the queen is in, and only the ones that are loaded right now: nothing here
        // loads a chunk, and a spider in another dimension is simply not part of the command.
        if (!(queen.level() instanceof ServerLevel level))
        {
            return found;
        }

        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof SpiderMinionEntity spider && spider.isOwnedBy(queen))
            {
                found.add(spider);
            }
        }

        return found;
    }

    /**
     * The units a command of the sceptre acts on (Brood Command Group round): <b>the selected spiders, and nothing
     * else</b>.
     *
     * <p>There is deliberately no fallback any more. Every sceptre command used to be sent to "whatever is marching with
     * the queen", which is why a second command used to cancel the first and why two groups could never be sent two ways
     * at once; from this round on a command reaches exactly the units a queen picked, and a command with an empty
     * selection reaches nobody at all (the callers say so in the action bar, see {@code BroodSelection}).
     *
     * <p>The only command that does not go through here is the hard rally ({@link #rally(Player)}), which is a global
     * recall by definition.
     *
     * <p>An ordered list is built once per command, so nothing below ever looks at the selection again: the commands only
     * ever see the units they were given.
     *
     * <p><b>Reserved space for squadrons.</b> A later round with numbered squadrons changes this one method - it hands
     * out the units of the active group instead of the single selection - and every command keeps working unchanged.
     */
    public static List<SpiderMinionEntity> commanded(Player queen)
    {
        List<SpiderMinionEntity> chosen = new ArrayList<>();

        Collection<UUID> selected = QueenDataCapability.get(queen)
                .map(data -> data.broodSelection().ids())
                .orElse(List.of());

        if (selected.isEmpty())
        {
            return chosen;
        }

        for (SpiderMinionEntity spider : ownedLoaded(queen))
        {
            if (selected.contains(spider.getUUID()))
            {
                chosen.add(spider);
            }
        }

        return chosen;
    }

    /** @return true when that queen has selected at least one spider, whether it is loaded right now or not. */
    public static boolean hasSelection(Player queen)
    {
        return QueenDataCapability.get(queen).map(data -> !data.broodSelection().isEmpty()).orElse(false);
    }

    /**
     * The attack order (Queen Scepter, right click on a creature): every commanded spider goes for that victim, whatever
     * the faction matrix thinks of it. An old scout move is replaced by it, because a spider has exactly one temporary
     * command.
     *
     * <p>Who answers follows from the selection: a unit the queen picked obeys even when its order is hold or patrol (an
     * order given to a unit that was picked is an order, and it walks there under any order), and a queen who picked
     * nothing gets no order at all, because {@link #commanded(Player)} hands out an empty list then. A spider that was not
     * picked is never touched: it keeps its post, its round and its errand.
     *
     * @return how many spiders were ordered
     */
    public static int attack(Player queen, LivingEntity victim)
    {
        int ordered = 0;

        for (SpiderMinionEntity spider : commanded(queen))
        {
            if (spider == victim)
            {
                continue;
            }

            spider.setForcedTarget(victim);

            if (spider.getForcedTarget() == victim)
            {
                // A new command outranks the hunt: a spider that was hunting is an ordinary follower again the moment it
                // is sent at something (free attack round).
                spider.leaveFreeAttack();
                ordered++;
            }
        }

        return ordered;
    }

    /**
     * The scout move (Queen Scepter, right click on a block): the commanded units walk to that spot and nothing else
     * changes. An older errand of those units is dropped, because a spider has exactly one temporary command; every
     * other spider of the brood is not touched at all, so a group that is already walking somewhere else keeps walking.
     *
     * <p>This is the line that makes several groups possible (Brood Command Group round): before it, every loaded spider
     * of the queen was cleared and re-sent here, which is why a second move order cancelled the first one.
     *
     * @return how many spiders were sent
     */
    public static int moveTo(Player queen, BlockPos navigationTarget, int commandId)
    {
        int sent = 0;

        for (SpiderMinionEntity spider : commanded(queen))
        {
            spider.clearTemporaryCommand();
            spider.setMoveTo(navigationTarget, commandId);

            // A new command outranks the hunt (free attack round): the order of a spider that was hunting is a march
            // again, and the errand it was just given runs under it like under any other order.
            spider.leaveFreeAttack();
            sent++;
        }

        return sent;
    }

    /**
     * The free attack command: the commanded units <b>switch their hunt on</b>. Everything around them that is not of
     * their own side becomes a victim - a human, a villager, a zombie, a skeleton, a cow, a golem, a creature of another
     * mod, the spiders of another queen - whether the faction matrix has an opinion about it or not, and they walk their
     * rounds while they hunt instead of standing still.
     *
     * <p>It writes the persistent order, like every other order command of this class, and the hunting ground of a
     * spider is decided by the spider itself ({@link SpiderMinionEntity#freeAttackHere()}): a follower hunts from where
     * it stands, and a spider that already had a post keeps that post.
     *
     * <p><b>Asking for the hunt again is not a new command</b>, so this is also the one command that leaves a hunting
     * spider hunting. Every other command takes the hunt away: the scout move and the attack order do it out loud
     * ({@link #moveTo(Player, BlockPos, int)}, {@link #attack(Player, LivingEntity)}), and every command that is itself
     * an order - a rally, a march, a post - does it by writing that order. The order key is the fourth way out and is
     * not a command in this sense at all: it walks the cycle of the four orders and takes the hunt with it like any
     * other order it steps over.
     *
     * @return how many spiders started hunting
     */
    public static int freeAttack(Player queen)
    {
        int sent = 0;

        for (SpiderMinionEntity spider : commanded(queen))
        {
            stopErrand(spider);
            spider.freeAttackHere();
            sent++;
        }

        return sent;
    }

    /**
     * The hard rally (Queen Scepter, a command of the wheel): every loaded spider of the queen comes home, whatever it
     * was doing - post, patrol, scout move and last order are dropped, the spider is a follower again and gathers around
     * its queen. The soft rally of the brood keys is {@link #followHere(Player)}, which moves nobody.
     *
     * <p>And it really means <b>at once</b>: a spider that is not already standing at her side is teleported to her,
     * with the very same safe placement the follow goal uses when a pet fell behind (free space, ground below, no liquid,
     * inside the world border). A queen who calls for her brood does not wait for it to walk.
     *
     * <p>The selection is dropped with it (Brood Command Group round): a queen who calls the whole brood home has no
     * group any more, and her next command needs one again - which, a moment after a rally, is one key away, because J
     * picks everything she can see and every spider that answered is a follower by then.
     *
     * @return how many spiders answered the call
     */
    public static int rally(Player queen)
    {
        QueenDataCapability.get(queen).ifPresent(data -> data.broodSelection().clear());

        int called = 0;

        for (SpiderMinionEntity spider : ownedLoaded(queen))
        {
            spider.rally();

            if (spider.distanceToSqr(queen) > SpiderFollowOwnerGoal.STOP_DISTANCE * SpiderFollowOwnerGoal.STOP_DISTANCE)
            {
                SpiderFollowOwnerGoal.teleportToOwner(spider, queen);
            }

            called++;
        }

        return called;
    }

    /**
     * The stand-down key of the brood keys (Brood Command Group round): the commanded units guard the blocks they happen
     * to stand on right now, each on its own, and every errand they had is dropped.
     *
     * <p>It is the one command of this class that <b>writes the persistent order</b> of a spider, and it writes hold,
     * because that is what it means: not "wait here for a moment" but "this is your post". A queen who wants a garrison
     * somewhere else walks it there first and presses this key there, which is also how a scouting group becomes one.
     *
     * @return how many spiders really stood down
     */
    public static int holdHere(Player queen)
    {
        int held = 0;

        for (SpiderMinionEntity spider : commanded(queen))
        {
            stopErrand(spider);
            spider.holdAt(spider.blockPosition());
            held++;
        }

        return held;
    }

    /**
     * The soft rally of the brood keys (the U key): the commanded units stop what they are doing and march with the
     * queen - on foot. The errand, the path and the victim they had are dropped, their order becomes
     * {@link SpiderOrderMode#FOLLOW}, and <b>not a single spider is moved</b>.
     *
     * <p>Two rallies, and the difference is the whole point: the command wheel of the sceptre
     * ({@link #rally(Player)}) calls the whole brood home at once and teleports it there, which is what a queen needs
     * when her brood is scattered over half a village. This one is for the group she picked: it turns garrison spiders
     * into her escort without pulling them out of the machine they are standing in, so they simply walk after her from
     * wherever they are.
     *
     * <p>The selection is deliberately kept: those units are still her group, and the next command reaches them again.
     *
     * @return how many spiders turned around
     */
    public static int followHere(Player queen)
    {
        int called = 0;

        for (SpiderMinionEntity spider : commanded(queen))
        {
            stopErrand(spider);
            spider.setOrderMode(SpiderOrderMode.FOLLOW);
            called++;
        }

        return called;
    }

    /**
     * Drops everything temporary a spider is busy with: its errand, its path and the victim it was fighting. Its
     * persistent order is never touched here, because that is what the order commands of this class change.
     */
    private static void stopErrand(SpiderMinionEntity spider)
    {
        spider.clearTemporaryCommand();
        spider.getNavigation().stop();
        spider.setTarget(null);
    }

    /**
     * The order key of the brood keys: the commanded units step through the order cycle together, as one group - follow,
     * hold, patrol, follow ...
     *
     * <p><b>A group is cycled from the state of the group</b>, never from the state of each spider on its own: a group
     * whose spiders all hold the same order steps to the next order together, so it can never end up half guarding and
     * half patrolling. And a <b>mixed</b> group is unified first - its first press makes the whole group a marching one,
     * and the presses after that walk the cycle.
     *
     * <p>The anchor rule of a step is the rule of the spider itself ({@link SpiderMinionEntity#cycleOrder()}): follow to
     * hold takes the block each spider stands on as its own post, hold to patrol keeps that post, and patrol back to
     * follow drops it. Every unit also drops its errand and its fight, because an order is a decision about what a
     * spider <b>is</b> from now on and not something to do after the current errand.
     *
     * @return the order the group holds now, or null when nothing at all was commanded
     */
    @Nullable
    public static SpiderOrderMode cycleOrder(Player queen)
    {
        List<SpiderMinionEntity> units = commanded(queen);

        if (units.isEmpty())
        {
            return null;
        }

        if (!isUniform(units))
        {
            // A mixed group is unified before it is cycled: the first press of the key can never take a group somewhere
            // nobody asked for.
            for (SpiderMinionEntity spider : units)
            {
                stopErrand(spider);
                spider.setOrderMode(SpiderOrderMode.FOLLOW);
            }

            return SpiderOrderMode.FOLLOW;
        }

        SpiderOrderMode next = units.get(0).getOrderMode().next();

        for (SpiderMinionEntity spider : units)
        {
            stopErrand(spider);
            spider.cycleOrder();
        }

        return next;
    }

    /** @return true when every spider of that group holds the same order right now. */
    private static boolean isUniform(List<SpiderMinionEntity> units)
    {
        SpiderOrderMode first = units.get(0).getOrderMode();

        for (SpiderMinionEntity spider : units)
        {
            if (spider.getOrderMode() != first)
            {
                return false;
            }
        }

        return true;
    }

    /** @return true when that victim is one of the spiders of that queen, which can never be ordered at. */
    public static boolean isOwnSpider(Player queen, @Nullable Entity victim)
    {
        return victim instanceof SpiderMinionEntity spider && spider.isOwnedBy(queen);
    }
}

