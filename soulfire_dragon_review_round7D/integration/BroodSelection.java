package spiderqueenhungrycraft.brood;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.entity.PartEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.network.C2SBroodSelectionPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBroodSelectionPacket;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireOrders;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The selection keys of the brood (Brood Selection + Inspect HUD round, reworked into command groups by the Brood
 * Command Group round).
 *
 * <p><b>Selecting and commanding are two different things from here on.</b> Four entrances select - the drag of the
 * right button, the sceptre click on one own spider, "what I can see" and "the same species" - and each of them does
 * exactly one thing: it replaces the selection of that queen. None of them changes an order, stops a spider or clears an
 * errand any more, which is what makes it possible to pick a second group while the first one is still marching: the
 * marching group is not part of the new selection, so nothing touches it.
 *
 * <p>The commands are the other side of it and live next door in {@link SpiderCommand}: they act on the selected units and
 * on <b>nobody else</b>, so a command without a selection is refused and said out loud instead of being sent to whoever
 * happens to be marching. The stand-down key, the soft rally key and the order key of this class are commands in that
 * sense and never selections - and the hard rally, which teleports the whole brood home, stays where it has always been:
 * on the command wheel of the sceptre, and it is the one command that means everybody.
 *
 * <p><b>The way out of a selection is the left button.</b> A swing at a block with the sceptre in hand gives the group up
 * ({@code C2SBroodSelectionPacket.Action#DESELECT}); the gesture is read by the client of the queen, which only asks, and
 * the group is cleared here like every other change of it. The hard rally clears the selection as well, because an
 * emergency recall has no group left to command.
 *
 * <p>Nothing here trusts the client: the ids of a view selection are looked up again among the spiders the server itself
 * has loaded, owns and finds inside the box, so no key and no packet can ever touch a spider that is not the queen's. A
 * selection that catches nothing is not a selection - a drag over the sky, a view with no spider in it and a species
 * nobody has change nothing at all, so a queen can never lose her group to a click that caught nothing.
 *
 * <p>Every selection that is decided is written into the queen data and <b>echoed to her own client</b>
 * ({@link S2CBroodSelectionPacket}), because the outline she sees on her selected spiders is the only thing that tells
 * her which units a command will reach.
 */
public final class BroodSelection
{
    private BroodSelection()
    {
    }

    /** Runs one brood key of that queen. */
    public static void perform(ServerPlayer queen, C2SBroodSelectionPacket packet)
    {
        // Every key of this class only speaks for a queen who is holding her sceptre.
        if (!holdsScepter(queen))
        {
            return;
        }

        switch (packet.action())
        {
            case SELECT_VISIBLE -> selectVisible(queen, packet.candidates());
            case SELECT_SAME_TYPES -> selectSameTypes(queen);
            case HOLD_SELECTED -> standDown(queen);
            case FOLLOW_SELECTED -> followSelected(queen);
            case CYCLE_ORDER -> cycleOrder(queen);
            case FREE_ATTACK -> freeAttack(queen);
            case RALLY -> rallyHome(queen);
            case DESELECT -> deselect(queen);
        }
    }

    /**
     * The hard rally, the one command that means everybody (Command Wheel round).
     *
     * <p>The work itself lives next door in {@link QueenScepterCommands#rally}, where it always lived - the wheel only
     * knocks on that door now instead of the sneaking right click that used to.
     */
    private static void rallyHome(ServerPlayer queen)
    {
        if (queen.level() instanceof ServerLevel level)
        {
            QueenScepterCommands.rally(queen, level);
        }
    }

    /**
     * Everything the queen can see becomes her selection. The ids of the client are only a claim: they are matched
     * against the spiders the server itself has loaded and owns.
     */
    private static void selectVisible(ServerPlayer queen, int[] candidates)
    {
        Set<UUID> chosen = new LinkedHashSet<>();

        for (SpiderMinionEntity spider : SpiderCommand.ownedLoaded(queen))
        {
            if (claimed(candidates, spider.getId()))
            {
                chosen.add(spider.getUUID());
            }
        }

        // And the contract dragons of that queen, on the very same terms (Necromancy IV round 5): the client proposes
        // the ids it believes are on screen - it only ever knows its own, because the answer of the server about who
        // owns a dragon is told to that owner alone - and the server keeps the ones that really are hers and really are
        // loaded. Nothing of what the client says is trusted as ownership.
        List<Entity> dragons = new ArrayList<>(NecromancyOrders.ownedLoaded(queen));
        dragons.addAll(SoulfireOrders.ownedLoaded(queen));

        for (Entity dragon : dragons)
        {
            if (claimed(candidates, dragon.getId()))
            {
                chosen.add(dragon.getUUID());
            }
        }

        NecromancyDiagnostics.log("select visible: candidates=" + candidates.length
                + " dragons loaded=" + dragons.size() + " chosen=" + chosen.size());

        if (chosen.isEmpty())
        {
            return;
        }

        applySelection(queen, chosen,
                Component.translatable("message.spiderqueen_hungrycraft.select_visible", chosen.size()));
    }

    /** @return true when that entity id was claimed by the client. */
    private static boolean claimed(int[] candidates, int entityId)
    {
        for (int candidate : candidates)
        {
            if (candidate == entityId)
            {
                return true;
            }
        }

        return false;
    }

    /**
     * Every loaded spider of the species that the queen picked joins the selection.
     *
     * <p><b>The key needs a group</b>, because the species it means are the species of the spiders she picked: with
     * nothing selected there is no species to mean, and it refuses exactly like a command does
     * ({@link #tellNobodyAnswered(ServerPlayer)}). The same is said for a group that is not here at all, whose species
     * simply cannot be read from this level.
     */
    private static void selectSameTypes(ServerPlayer queen)
    {
        List<SpiderMinionEntity> brood = SpiderCommand.ownedLoaded(queen);
        Collection<UUID> current = selectionOf(queen);

        if (current.isEmpty())
        {
            tellNobodyAnswered(queen);

            return;
        }

        Set<EntityType<?>> species = new LinkedHashSet<>();

        for (SpiderMinionEntity spider : brood)
        {
            if (current.contains(spider.getUUID()))
            {
                species.add(spider.getType());
            }
        }

        // The other kind of unit of a queen is picked the same way (Necromancy IV round): a contract dragon that is in
        // the selection means "every contract dragon of mine", because there is only one species of them. A selection
        // of spiders alone never catches a skeleton by this key: a dragon is not the same kind of animal.
        boolean dragons = false;

        for (Entity dragon : NecromancyOrders.commanded(queen))
        {
            dragons |= current.contains(dragon.getUUID());
        }

        for (SoulfireDragonEntity dragon : SoulfireOrders.commanded(queen))
        {
            dragons |= current.contains(dragon.getUUID());
        }

        if (species.isEmpty() && !dragons)
        {
            tellNobodyAnswered(queen);

            return;
        }

        Set<UUID> chosen = new LinkedHashSet<>();

        for (SpiderMinionEntity spider : brood)
        {
            if (species.contains(spider.getType()))
            {
                chosen.add(spider.getUUID());
            }
        }

        if (dragons)
        {
            for (Entity dragon : NecromancyOrders.commanded(queen))
            {
                chosen.add(dragon.getUUID());
            }

            // The soul fire dragons answer the same key (round 7C): a group that holds one of them means "every unit of
            // this kind that I own", so the J and K keys reach them with the very same press.
            for (SoulfireDragonEntity dragon : SoulfireOrders.commanded(queen))
            {
                chosen.add(dragon.getUUID());
            }
        }

        applySelection(queen, chosen,
                Component.translatable("message.spiderqueen_hungrycraft.select_same", chosen.size()));
    }

    /**
     * One spider of the queen becomes the whole selection (Brood RTS Mouse Selection round, reworked by the Brood
     * Command Group round).
     *
     * <p>It is the rule the sceptre right click on an own spider uses: picking one unit out of the brood and nothing
     * else. It changes no order and stops nobody - the earlier form of this method turned every other follower of the
     * queen into a garrison where it stood, and that is exactly what used to make a second command stop the first one.
     * The stand-down key is the way to post the units a queen picked. A spider that is not hers is refused.
     */
    public static void selectSingle(ServerPlayer queen, SpiderMinionEntity target)
    {
        if (!target.isOwnedBy(queen))
        {
            return;
        }

        applySelection(queen, List.of(target.getUUID()),
                Component.translatable("message.spiderqueen_hungrycraft.select_single", target.getDisplayName()));
    }

    /**
     * The same gesture, for the other kind of unit a queen can command (Necromancy IV round): one contract dragon of
     * Ice and Fire becomes the group, and every further move, attack or bombardment of the sceptre acts on it.
     *
     * <p>Nothing about the dragon itself is written here - not its order, not its target - and nothing about the
     * spiders either: the selection is replaced and echoed, exactly like a click on a spider. The caller has already
     * checked that the dragon really carries a contract of this queen, and the check is repeated here so this method is
     * safe to call from anywhere.
     */
    public static void selectSingleDragon(ServerPlayer queen, Entity dragon)
    {
        // A ray cast against a wing/neck must select its host; never save a part UUID.
        if (dragon instanceof PartEntity<?> part)
        {
            dragon = part.getParent();
        }
        if (!NecromancyOrders.isContractDragonOf(queen, dragon) && !SoulfireOrders.isSoulfireDragonOf(queen, dragon))
        {
            return;
        }

        applySelection(queen, List.of(dragon.getUUID()),
                Component.translatable("message.spiderqueen_hungrycraft.necromancy.scepter_selected",
                        dragon.getDisplayName()));
    }

    /**
     * The spiders a spider mine just delivered, handed to the queen who placed that mine (Spider Mine and Egg Arrow
     * round).
     *
     * <p>It is the one selection of this class that is not a gesture: nobody dragged a box and nobody clicked a spider -
     * a mine simply opened, and its clutch becomes the group of its placer. It means the very same thing a click on one
     * spider means ("this is my group now"), for eight units at once, which is the whole point of firing a mine with the
     * sceptre: the squad is ready for the next key.
     *
     * <p>The list is built by the caller from spiders the server itself just created, so nothing is looked up here and
     * nothing is trusted. An empty list is not a selection and changes nothing.
     */
    public static void selectSpawned(ServerPlayer queen, Collection<UUID> spiders)
    {
        if (spiders.isEmpty())
        {
            return;
        }

        applySelection(queen, spiders,
                Component.translatable("message.spiderqueen_hungrycraft.spider_mine_selected", spiders.size()));
    }

    /** How far outside the two corners of a drag a spider may be and still count as chosen. */
    private static final double BOX_MARGIN = 0.5D;

    /**
     * The vertical slack of a drag selection: a first person camera has no RTS plane, so the two corners of a drag are
     * never at the same height and often are not even at the height of the spiders (Brood RTS Mouse Selection round).
     * Sixteen blocks above and below the drag turn a flat line on the screen into a pickable volume, and the numbers
     * still come from a real ray cast on both ends.
     */
    public static final double BOX_VERTICAL_SLACK = 16.0D;

    /**
     * @return the volume a drag from one point to the other selects
     *
     * <p>It is public and static on purpose: the client draws the very same box it is about to ask for, and it even
     * uses it to highlight the spiders the moment the mouse is released. One rule, drawn and asked for by two sides,
     * instead of a copy on each side.
     */
    public static AABB selectionBox(Vec3 first, Vec3 second)
    {
        return new AABB(Math.min(first.x, second.x) - BOX_MARGIN, Math.min(first.y, second.y) - BOX_VERTICAL_SLACK,
                Math.min(first.z, second.z) - BOX_MARGIN, Math.max(first.x, second.x) + BOX_MARGIN,
                Math.max(first.y, second.y) + BOX_VERTICAL_SLACK, Math.max(first.z, second.z) + BOX_MARGIN);
    }

    /**
     * The drag selection of the sceptre: every unit of the queen inside the box becomes the selection, and nobody else
     * is touched.
     *
     * <p><b>An empty box is not a selection.</b> If the drag caught none of her units - she dragged over the sky, a
     * wall or somebody else's brood - nothing changes at all, so a queen can never lose her group to a click that hit
     * nothing.
     *
     * <p><b>Two kinds of unit, one box</b> (Necromancy IV round). The spiders come from the brood and the contract
     * dragons of Ice and Fire come from {@link NecromancyOrders}, and both are found <b>on the server</b>, from the
     * entities it has loaded and from the contract it holds for each one. That is why a skeleton that was framed
     * together with a swarm is commanded by the very same keys afterwards: it is in the same selection, not in a
     * second one.
     *
     * <p>The two points are all the client sends: which units are inside is worked out here.
     */
    public static void selectBox(ServerPlayer queen, Vec3 first, Vec3 second)
    {
        if (!holdsScepter(queen))
        {
            return;
        }

        AABB box = selectionBox(first, second);
        Set<UUID> chosen = new LinkedHashSet<>();

        for (SpiderMinionEntity spider : SpiderCommand.ownedLoaded(queen))
        {
            if (box.contains(spider.getX(), spider.getY() + spider.getBbHeight() * 0.5D, spider.getZ()))
            {
                chosen.add(spider.getUUID());
            }
        }

        for (Entity dragon : NecromancyOrders.dragonsIn(queen, box))
        {
            // A dragon is a big animal and a queen drags a box over what she can see of it, so a dragon counts as
            // inside the box when its <b>bounding box</b> meets the drag and not only when its centre happens to fall
            // inside it (Necromancy IV round 5). That is what lets a stage five skeleton be picked by a box that only
            // covers one of its wings - the shape a player draws when the animal is bigger than her screen.
            if (box.intersects(dragon.getBoundingBox()))
            {
                chosen.add(dragon.getUUID());
            }
        }

        // And the soul fire dragons of that queen, on exactly the same terms (round 7C): they are units of the brood now,
        // so a box that is dragged over one of them picks it like it picks a spider or a skeleton.
        for (SoulfireDragonEntity dragon : SoulfireOrders.unitsIn(queen, box))
        {
            if (box.intersects(dragon.getBoundingBox()))
            {
                chosen.add(dragon.getUUID());
            }
        }

        if (chosen.isEmpty())
        {
            NecromancyDiagnostics.log("select box: nothing inside " + box + " (drag was empty of units)");

            return;
        }

        NecromancyDiagnostics.log("select box: chosen=" + chosen.size() + " from " + box);

        applySelection(queen, chosen,
                Component.translatable("message.spiderqueen_hungrycraft.select_box", chosen.size()));
    }

    /**
     * The stand-down key: the selected units guard the blocks they stand on right now, each on its own spot. It is the
     * one key of this class that changes an order, because posting a garrison is exactly that.
     */
    private static void standDown(ServerPlayer queen)
    {
        int held = SpiderCommand.holdHere(queen) + NecromancyOrders.holdHere(queen) + SoulfireOrders.holdHere(queen);

        if (held <= 0)
        {
            tellNobodyAnswered(queen);

            return;
        }

        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.hold_selected", held));
    }

    /**
     * The order key: the commanded units step through the order cycle together, as the one group they are (Brood Command
     * Group round). A mixed group is unified into followers by the first press, a uniform group walks the cycle follow,
     * hold, patrol.
     *
     * <p>The message names the group and its new order, not a single spider: a queen who pressed a group command wants
     * one answer about her group.
     */
    private static void cycleOrder(ServerPlayer queen)
    {
        int units = SpiderCommand.commanded(queen).size() + NecromancyOrders.commanded(queen).size()
                + SoulfireOrders.commanded(queen).size();
        SpiderOrderMode order = SpiderCommand.cycleOrder(queen);
        SpiderOrderMode dragonOrder = SoulfireOrders.cycleOrder(queen);

        if (dragonOrder == null)
        {
            dragonOrder = NecromancyOrders.cycleStance(queen);
        }

        if (units <= 0 || order == null && dragonOrder == null)
        {
            tellNobodyAnswered(queen);

            return;
        }

        // The group holds one order when both kinds of unit answered it, and the one order the units that answered hold
        // otherwise.
        SpiderOrderMode shown = order != null ? order : dragonOrder;
        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.spider_order", units,
                Component.translatable(shown.translationKey())));
    }

    /**
     * The free attack key (free attack round): the commanded units switch their hunt on - everything around them that is
     * not of their own side becomes a victim, and they walk their rounds while they hunt.
     *
     * <p>It is the order command that is not of the order cycle, which is why it has a key and a place of its own on the
     * wheel: a post or a march is where a group should be, and this is what it should be doing there. The difference to
     * the order key is one press wide - the order key <b>walks</b> the four orders and leaves the hunt with the next one,
     * this key <b>sets</b> the hunt and touches nothing else.
     */
    private static void freeAttack(ServerPlayer queen)
    {
        int sent = SpiderCommand.freeAttack(queen) + NecromancyOrders.freeAttack(queen) + SoulfireOrders.freeAttack(queen);

        if (sent <= 0)
        {
            tellNobodyAnswered(queen);

            return;
        }

        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_free_attack", sent));
    }

    /**
     * The soft rally key: the commanded units march with the queen from now on, on foot. It is the group version of the
     * command wheel of the sceptre, and the difference between the two is the teleport - this one moves nobody,
     * it only changes what those spiders are.
     *
     * <p>It is the way a queen turns a garrison or a patrol she picked into her escort without pulling it out of the
     * machine it is standing in, and it is why the hard rally does not need a key of its own.
     */
    private static void followSelected(ServerPlayer queen)
    {
        int called = SpiderCommand.followHere(queen) + NecromancyOrders.followHere(queen) + SoulfireOrders.followHere(queen);

        if (called <= 0)
        {
            tellNobodyAnswered(queen);

            return;
        }

        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_follow_selected", called));
    }

    /**
     * The one feedback of every command that reached nobody (Brood Command Group round).
     *
     * <p>There are two reasons and they are told apart, because only one of them is fixed by selecting: either nothing is
     * selected at all - the usual mistake of a queen who is used to the old sceptre and simply clicked - or the group she
     * picked is not here, in another dimension or in a chunk that is not loaded.
     */
    public static void tellNobodyAnswered(ServerPlayer queen)
    {
        if (SpiderCommand.hasSelection(queen))
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_none"));

            return;
        }

        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.command_no_selection"));
    }

    /** Writes a new selection into the queen data and tells the queen, in the action bar and on her client. */
    private static void applySelection(ServerPlayer queen, Collection<UUID> chosen, Component message)
    {
        QueenDataCapability.get(queen).ifPresent(data -> data.broodSelection().selectOnly(chosen));
        tell(queen, message);
        sendSelection(queen);
    }

    /**
     * The deselect of the left button: the queen lets go of her group (Brood Command Group round).
     *
     * <p>Her next command needs a group again, because a command without a selection is refused and said out loud
     * ({@link SpiderCommand#hasSelection(Player)}): letting go is not the same as ordering the whole brood to march. The
     * quickest ways back are one key away - J for everything in sight, K for the species she last picked - and the hard
     * rally (a command of the wheel) means everybody either way. Nothing about the spiders themselves changes with it: not
     * one order, not one errand, not one path.
     *
     * <p>A queen who has selected nothing gets no message and no packet: there is nothing to let go of.
     */
    private static void deselect(ServerPlayer queen)
    {
        QueenData data = QueenDataCapability.get(queen).orElse(null);

        if (data == null || data.broodSelection().isEmpty())
        {
            return;
        }

        data.broodSelection().clear();
        tell(queen, Component.translatable("message.spiderqueen_hungrycraft.selection_cleared"));
        sendSelection(queen);
    }

    /** @return the spiders the queen has selected right now, empty when she has selected nothing. */
    private static Collection<UUID> selectionOf(ServerPlayer queen)
    {
        return QueenDataCapability.get(queen).map(data -> data.broodSelection().ids()).orElse(List.of());
    }

    /**
     * Tells the own client of that queen which units are selected right now (Brood Command Group round).
     *
     * <p>Only the loaded selected spiders are in it: a spider in an unloaded chunk or in another dimension can not be
     * drawn anyway, and it is still selected the moment its queen comes back to it. The login and the dimension change of
     * a queen call this as well, so the outline of her group is right the moment she sees the world again. A player
     * without a connection - a fake player of a test - has nobody to be told, and nothing is sent to one.
     */
    public static void sendSelection(ServerPlayer queen)
    {
        if (queen.connection == null)
        {
            return;
        }

        Collection<UUID> selected = selectionOf(queen);
        List<Integer> ids = new ArrayList<>();

        if (!selected.isEmpty())
        {
            for (SpiderMinionEntity spider : SpiderCommand.ownedLoaded(queen))
            {
                if (selected.contains(spider.getUUID()))
                {
                    ids.add(spider.getId());
                }
            }

            // And the contract dragons of that queen, which are units of the very same selection (Necromancy IV round):
            // the outline is what tells her which units a command will reach, and a skeleton she can not see marked is a
            // unit she can not command. The ids are resolved from her own selection on the server, so a client can not
            // invent one.
            ids.addAll(NecromancyOrders.selectedEntityIds(queen));
            ids.addAll(SoulfireOrders.selectedEntityIds(queen));
        }

        int[] packed = new int[ids.size()];

        for (int index = 0; index < packed.length; index++)
        {
            packed[index] = ids.get(index);
        }

        HungryCraftNetwork.sendToPlayer(new S2CBroodSelectionPacket(packed), queen);
    }

    /** @return true when the sceptre is in one of the hands of that queen. */
    private static boolean holdsScepter(ServerPlayer queen)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = queen.getItemInHand(hand);

            if (held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }

    /** Action bar feedback, and nothing for a player without a connection (a fake player of a test). */
    private static void tell(ServerPlayer queen, Component message)
    {
        if (queen.connection != null)
        {
            queen.displayClientMessage(message, true);
            queen.level().playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_STEP,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
        }
    }
}
