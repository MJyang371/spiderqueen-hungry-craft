package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CQueenScepterPacket;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.soulfire.SoulfireOrders;

/**
 * The server side of the queen sceptre (Queen Scepter command patch), and the only place where a command really
 * happens.
 *
 * <p>The client asks "I used the sceptre"; everything else is decided here, from the eyes and the view of the real
 * {@link ServerPlayer}:
 * <ul>
 * <li>a sneaking click is the bombardment of the contract dragons (Necromancy IV round) and nothing else - it was the
 * hard rally once, and the rally is a command of the wheel now (Command Wheel round),</li>
 * <li>a creature that is not one of her own spiders or one of her own contract dragons: the attack order,</li>
 * <li>her own spider: the order cycle of that one spider,</li>
 * <li>her own contract dragon: the selection of that one dragon,</li>
 * <li>a block: the scout move of the following spiders and of the selected contract dragons.</li>
 * </ul>
 *
 * <p>The item in the hand is checked again here and the command ray is cast again here, so neither the interaction reach
 * of the game nor anything a client claims about its target plays a role. Only a command that really happened is
 * answered with a confirmation packet.
 */
public final class QueenScepterCommands
{
    /** The id of the next scout move; only the client uses it to tell one marker from the next. */
    private static final AtomicInteger NEXT_MOVE_ID = new AtomicInteger(1);

    private QueenScepterCommands()
    {
    }

    /** Handles one sceptre use of that queen, in the shape the client classified it (Queen Staff input timing round). */
    public static void use(ServerPlayer queen, InteractionHand hand, boolean sneaking,
            spiderqueenhungrycraft.network.C2SQueenScepterUsePacket.Action action, @Nullable Vec3 pressPoint)
    {
        ItemStack stack = queen.getItemInHand(hand);

        // The client only asks; whether the sceptre is really in that hand is decided here.
        if (!stack.is(HungryCraftItems.QUEEN_SCEPTER.get()) || !(queen.level() instanceof ServerLevel level))
        {
            return;
        }

        if (action == spiderqueenhungrycraft.network.C2SQueenScepterUsePacket.Action.HOLD)
        {
            hold(queen, level);

            return;
        }

        // A sneaking click is nothing for the brood any more (Command Wheel round): it used to be the hard rally, and
        // the wheel holds that command now. It is the bombardment of the contract dragons instead (Necromancy IV
        // round), and a sneaking click that catches no dragon is refused exactly as before - so not even a client that
        // still sends the old gesture can recall a whole brood with a click.
        if (sneaking)
        {
            if (bombard(queen, level))
            {
                return;
            }

            answer(queen, S2CQueenScepterPacket.none());

            return;
        }

        QueenCommandRay.Hit hit = QueenCommandRay.cast(queen);

        // A click on one of her own spiders or on one of her contract dragons is how a queen picks a single unit (and
        // steps its order), so that gesture may never be taken by a mine - the design is explicit about it: the mine
        // command displaces the march and the attack, never the selection. A box selection does not reach this method
        // at all.
        boolean pickingOneSpider = hit.hasEntity() && (SpiderCommand.isOwnSpider(queen, hit.entity())
                || NecromancyOrders.isContractDragonOf(queen, hit.entity())
                || SoulfireOrders.isSoulfireDragonOf(queen, hit.entity()));

        // The mine command comes first and takes the place of everything else (Spider Trigger round): a click whose two
        // ends both live inside the ball of one mine is a trigger, not a march and not an attack.
        if (!pickingOneSpider && fireMineIfAimedAtOne(queen, level, hit, pressPoint))
        {
            return;
        }

        if (hit.hasEntity() && hit.entity() != null)
        {
            useOnEntity(queen, level, hit.entity());
        }
        else if (hit.hasBlock() && hit.blockPos() != null)
        {
            move(queen, level, hit.blockPos(), hit.face());
        }
        else
        {
            // Nothing to point at: the client only hears that nothing happened.
            answer(queen, S2CQueenScepterPacket.none());
        }
    }

    /**
     * The one question a click has to answer before it can mean anything else: is a mine of this mod under it?
     *
     * <p><b>Both ends of the click count.</b> The point of the release comes from the ray the server casts itself; the
     * point of the press is the one thing only the client can know, so it travels in the request and is checked here -
     * a point further away than the sceptre can reach is not believed and is treated as "pressed where released". A
     * mine only answers when <b>both</b> points lie inside its ball, which is what keeps a drag of the mouse across a
     * mine from being a trigger: the drag is classified as a box selection long before this method is ever called, and
     * a click that started somewhere else is not a mine command either.
     *
     * @return true when a mine was fired, in which case the click means nothing else at all - the march order and the
     *         attack order of the same click are taken by it, while a <b>selection</b> (a click on one spider or a drag
     *         of a box) never reaches this method in the first place and is therefore untouched
     */
    private static boolean fireMineIfAimedAtOne(ServerPlayer queen, ServerLevel level, QueenCommandRay.Hit hit,
            @Nullable Vec3 pressPoint)
    {
        Vec3 release = QueenCommandRay.cursorPoint(queen, hit);
        Vec3 press = isPlausiblePressPoint(queen, pressPoint) ? pressPoint : release;
        BlockPos mine = SpiderMineTargeting.nearestMine(level, release, press);

        if (mine == null)
        {
            return false;
        }

        SpiderMineTargeting.fire(level, mine, queen);

        // Nothing else of this click happens, and the queen is told nothing more than that: the visual answer of a
        // mine is the mine itself going off.
        answer(queen, S2CQueenScepterPacket.none());

        return true;
    }

    /** @return true when that point could really have been the crosshair of this queen: not too far, not nowhere. */
    private static boolean isPlausiblePressPoint(ServerPlayer queen, @Nullable Vec3 pressPoint)
    {
        if (pressPoint == null)
        {
            return false;
        }

        // One block of slack over the reach of the ray itself: the point of a ray that met nothing sits exactly on the
        // edge of it, and a little floating point noise must never throw a real click away.
        double reach = QueenCommandRay.RANGE + 1.0D;

        return queen.getEyePosition().distanceToSqr(pressPoint) <= reach * reach;
    }

    /**
     * The hold ability of the sceptre (Queen Staff input timing round).
     *
     * <p><b>Reserved, and deliberately empty.</b> This round builds the input framework only: the client measures how
     * long the right button was down and reports the verdict as {@code HOLD}, and this method is the one place on the
     * server where the ability of a long press will live. No ability is bound to it yet, so a hold has no effect at all
     * today - which is exactly what the round asks for.
     *
     * <p>When one is bound it belongs here and nowhere else: the ray is cast here, the sceptre is checked here, and the
     * client still only ever says "the button was held".
     */
    private static void hold(ServerPlayer queen, ServerLevel level)
    {
        // Reserved for the hold ability of the sceptre: nothing is bound to a long press yet.
    }

    /** The creature case: the selection of her own spider, otherwise the attack order of the whole brood. */
    private static void useOnEntity(ServerPlayer queen, ServerLevel level, LivingEntity target)
    {
        if (SpiderCommand.isOwnSpider(queen, target))
        {
            // Right clicking one of her own spiders is the selection of that one unit (Brood RTS Mouse Selection round):
            // that spider becomes the group every further command acts on, and nothing else happens - no spider is sent
            // anywhere and no order is touched. The persistent order of a single spider is cycled with the order key
            // since the Brood Command Group round.
            BroodSelection.selectSingle(queen, (SpiderMinionEntity) target);
            answer(queen, S2CQueenScepterPacket.modeChanged());

            return;
        }

        if (NecromancyOrders.isContractDragonOf(queen, target) || SoulfireOrders.isSoulfireDragonOf(queen, target))
        {
            // The very same gesture, for the other kind of unit (Necromancy IV round): a skeleton on a payroll is
            // picked by clicking it with the sceptre and is then the group the next move, attack or bombardment act on.
            // It is a selection and nothing else - not a command, not a step of an order.
            BroodSelection.selectSingleDragon(queen, target);
            answer(queen, S2CQueenScepterPacket.modeChanged());

            return;
        }

        if (!mayBeOrderedAt(queen, target))
        {
            answer(queen, S2CQueenScepterPacket.none());

            return;
        }

        int ordered = SpiderCommand.attack(queen, target);
        int dragons = NecromancyOrders.attack(queen, target) + SoulfireOrders.attack(queen, target);

        level.playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS, 0.8F, 0.7F);

        if (ordered + dragons <= 0)
        {
            // Nobody answered: either no spider and no dragon of hers is loaded at all, or the group she selected is
            // not here (its units are in another dimension or in an unloaded chunk) and no follower is around either.
            // Saying that out loud is worth the one line: a command that silently does nothing is the one a queen
            // cannot debug.
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_attack_none"));
            answer(queen, S2CQueenScepterPacket.none());

            return;
        }

        if (ordered > 0)
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_attack",
                    target.getDisplayName(), ordered));
        }

        if (dragons > 0)
        {
            // The skeleton answers in a line of its own - it is not a spider and a command that reached both kinds of
            // unit says so twice - but with the very same shape as the spider line (Necromancy IV round 6): the victim
            // and the count. One command, one meaning, two kinds of unit.
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.command_attack",
                    target.getDisplayName(), dragons));
        }

        answer(queen, S2CQueenScepterPacket.attackAccepted(target.getId()));
    }

    /** The block case: a scout move for the followers, at a spot next to the hit that a spider can really reach. */
    private static void move(ServerPlayer queen, ServerLevel level, BlockPos clicked, @Nullable Direction face)
    {
        BlockPos navigationTarget = navigationTargetNear(level, clicked, face);
        BlockPos dragonTarget = clicked.relative(face == null ? Direction.UP : face);
        int commandId = NEXT_MOVE_ID.getAndIncrement();
        int sent = SpiderCommand.moveTo(queen, navigationTarget, commandId);
        int dragons = NecromancyOrders.moveTo(queen, dragonTarget) + SoulfireOrders.moveTo(queen, dragonTarget, commandId);
        int total = sent + dragons;

        level.playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_STEP,
                SoundSource.PLAYERS, 0.7F, 1.2F);

        if (total <= 0)
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_none"));
            answer(queen, S2CQueenScepterPacket.none());

            return;
        }

        if (sent > 0)
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_move", sent));
        }

        if (dragons > 0)
        {
            // The skeleton answers in a line of its own, with the count of the spider line (Necromancy IV round 6).
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.command_move", dragons));
        }

        // The marker of the queen sits exactly where she clicked; the spiders only walk to the spot beside it.
        answer(queen, S2CQueenScepterPacket.moveAccepted(commandId, dragonTarget));
    }

    /**
     * The sneaking click: the bombardment of the selected contract dragons (Necromancy IV round).
     *
     * <p>It is deliberately the <b>only</b> thing a sneaking click still means, and it means nothing at all unless a
     * contract dragon of that queen is selected: the brood has no bombardment, and the old gesture (the hard rally) is
     * a command of the wheel since the Command Wheel round. The target is the block face the server cast itself, never
     * a point a client sent.
     *
     * @return true when at least one dragon was ordered, in which case the click means nothing else
     */
    private static boolean bombard(ServerPlayer queen, ServerLevel level)
    {
        QueenCommandRay.Hit hit = QueenCommandRay.cast(queen);

        if (!hit.hasBlock() || hit.blockPos() == null)
        {
            return false;
        }

        BlockPos target = hit.blockPos().relative(hit.face() == null ? Direction.UP : hit.face());
        int dragons = NecromancyOrders.bombard(queen, target) + SoulfireOrders.breathAll(queen, target);

        if (dragons <= 0)
        {
            return false;
        }

        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.command_bombard", dragons));
        answer(queen, S2CQueenScepterPacket.moveAccepted(NEXT_MOVE_ID.getAndIncrement(), target));

        return true;
    }

    /**
     * The hard rally, and the only command of this class that means everybody (Command Wheel round).
     *
     * <p>It is public because it is cast from the command wheel of the sceptre now: the gesture that used to call it - a
     * sneaking right click - is gone, and the wheel is its one entrance.
     */
    public static void rally(ServerPlayer queen, ServerLevel level)
    {
        int called = SpiderCommand.rally(queen) + NecromancyOrders.rally(queen) + SoulfireOrders.rally(queen);

        // The rally drops the selection of that queen (Brood Command Group round), so she is told which units are left:
        // none, and the outline of her old group disappears with it.
        BroodSelection.sendSelection(queen);

        level.playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.PLAYERS, 1.0F, 1.6F);

        if (called > 0)
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_rally", called));
        }
        else
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_none"));
        }

        answer(queen, S2CQueenScepterPacket.rallyAccepted());
    }

    /** @return true when that creature may be ordered at all: no spectator, no creative onlooker, not the queen. */
    private static boolean mayBeOrderedAt(ServerPlayer queen, LivingEntity target)
    {
        if (target == queen)
        {
            return false;
        }

        return !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /** Action bar feedback of a command: never chat, and never a silent order that reached nobody. */
    private static void tell(ServerPlayer queen, Component message)
    {
        if (queen.connection != null)
        {
            queen.displayClientMessage(message, true);
        }
    }

    /**
     * The visual answer of a command, which only a client can use. A player without a connection - a fake player of a
     * test or of another mod - has nothing to be told, and nothing is sent to one.
     */
    private static void answer(ServerPlayer queen, S2CQueenScepterPacket packet)
    {
        if (queen.connection != null)
        {
            HungryCraftNetwork.sendToPlayer(packet, queen);
        }
    }

    /**
     * @return the spot a spider should really walk to for a click on that block.
     *
     * <p>A wall, a tree or a mountain would otherwise be a destination inside solid rock, so the neighbours of the hit
     * are searched for a place that has air for the spider and ground under it - and the hit itself is the fallback,
     * which the ordinary navigation then approaches as close as it can.
     */
    public static BlockPos navigationTargetNear(ServerLevel level, BlockPos clicked, @Nullable Direction face)
    {
        List<BlockPos> candidates = new ArrayList<>();

        if (face != null)
        {
            candidates.add(clicked.relative(face));
        }

        candidates.add(clicked.above());

        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            candidates.add(clicked.relative(direction));
            candidates.add(clicked.relative(direction).above());
        }

        candidates.add(clicked.above(2));

        for (BlockPos candidate : candidates)
        {
            if (isStandable(level, candidate))
            {
                return candidate;
            }
        }

        return face == null ? clicked : clicked.relative(face);
    }

    /** @return true when a spider could stand there: free space, with something to stand on below. */
    private static boolean isStandable(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);

        return state.getCollisionShape(level, pos).isEmpty()
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }
}
