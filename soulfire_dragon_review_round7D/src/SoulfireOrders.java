package spiderqueenhungrycraft.soulfire;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.entity.PartEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.brood.SpiderOrderMode;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The soul fire dragons of one queen, as units of her sceptre (round 7C).
 *
 * <p><b>They are commanded with the very same keys as the brood.</b> The selection is the same set
 * ({@code BroodSelectionSet}), the standing orders are the same four ({@link SpiderOrderMode}) and the temporary
 * commands are the same two (a scout move and a forced victim), so a queen who has spiders in the field and skeletons
 * in the air commands both without learning a second vocabulary.
 *
 * <p><b>What is different is only how they are executed</b> - the dragons walk or fly with their own goal, and the
 * spiders keep theirs - and that is deliberate: no line of {@code SpiderMinionEntity} or {@code SpiderCommand} is
 * touched by this class, it only speaks the same language.
 *
 * <p>Every command works on <b>the selected units and nobody else</b>. A command without a selection is refused and
 * said out loud, exactly like a command of the brood.
 */
public final class SoulfireOrders
{
    /** How far above a point of the world the column has to be open before the skill hovers over it. */
    public static final int SKY_HEIGHT = SoulfireDragonEntity.SKY_HEIGHT;

    private SoulfireOrders()
    {
    }

    /**
     * The soul fire dragons of that queen that answer a command right now: <b>the selected ones, and nothing else</b>.
     *
     * <p>A selected dragon whose chunk is not loaded is simply not answered this time - its order stays on the unit
     * and is executed the moment it is loaded again (the entity saves and restores everything it was told to do).
     */
    public static List<SoulfireDragonEntity> commanded(ServerPlayer queen)
    {
        List<SoulfireDragonEntity> chosen = new ArrayList<>();
        Collection<UUID> selected = selectionOf(queen);

        if (selected.isEmpty() || !(queen.level() instanceof ServerLevel level))
        {
            return chosen;
        }

        for (UUID id : selected)
        {
            if (level.getEntity(id) instanceof SoulfireDragonEntity dragon && dragon.isOwnedBy(queen))
            {
                chosen.add(dragon);
            }
        }

        return chosen;
    }

    /** @return true when that creature is a soul fire dragon of that queen. */
    public static boolean isSoulfireDragonOf(ServerPlayer queen, @Nullable Entity candidate)
    {
        Entity host = candidate instanceof PartEntity<?> part ? part.getParent() : candidate;
        return host instanceof SoulfireDragonEntity dragon && dragon.isOwnedBy(queen);
    }

    /** @return the entity ids of the selected units that are loaded, for the outline of the selection. */
    public static List<Integer> selectedEntityIds(ServerPlayer queen)
    {
        List<Integer> ids = new ArrayList<>();

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            ids.add(dragon.getId());
        }

        return ids;
    }

    /** @return the units of that queen inside a box, which is the other half of the drag selection. */
    public static List<SoulfireDragonEntity> unitsIn(ServerPlayer queen, AABB box)
    {
        List<SoulfireDragonEntity> chosen = new ArrayList<>();

        java.util.Set<UUID> seen = new java.util.HashSet<>();
        for (Entity candidate : queen.level().getEntities(queen, box))
        {
            Entity host = candidate instanceof PartEntity<?> part ? part.getParent() : candidate;
            if (host instanceof SoulfireDragonEntity dragon && dragon.isOwnedBy(queen)
                    && seen.add(dragon.getUUID()))
            {
                chosen.add(dragon);
            }
        }

        return chosen;
    }

    /** @return every unit of that queen that is loaded right now, selected or not. */
    public static List<SoulfireDragonEntity> ownedLoaded(ServerPlayer queen)
    {
        List<SoulfireDragonEntity> owned = new ArrayList<>();

        if (!(queen.level() instanceof ServerLevel level))
        {
            return owned;
        }

        for (Entity entity : level.getAllEntities())
        {
            if (entity instanceof SoulfireDragonEntity dragon && dragon.isOwnedBy(queen))
            {
                owned.add(dragon);
            }
        }

        return owned;
    }

    /** @return the units of that queen that are loaded and selected right now. */
    public static List<SoulfireDragonEntity> selected(ServerPlayer queen)
    {
        return commanded(queen);
    }

    /** @return the units of that queen a command may reach, which is what every command of the sceptre works on. */
    public static boolean hasUnits(ServerPlayer queen)
    {
        return !commanded(queen).isEmpty();
    }

    /** @return the selection of that queen, empty when she selected nothing. */
    private static Collection<UUID> selectionOf(ServerPlayer queen)
    {
        return QueenDataCapability.get(queen).map(data -> data.broodSelection().ids()).orElse(List.of());
    }
    // ------------------------------------------------------------------------------------------------------------
    // The commands of the sceptre
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The scout move: every selected unit walks to that spot, or flies to it when it is further than fifteen blocks
     * away. An older errand of those units is replaced by it - and only theirs, so a second group that is already
     * marching somewhere else keeps marching.
     */
    public static int moveTo(ServerPlayer queen, BlockPos target, int commandId)
    {
        int sent = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            dragon.clearTemporaryCommand();
            dragon.setMoveTo(target, commandId);
            dragon.leaveFreeAttack();
            sent++;
        }

        return sent;
    }

    /**
     * The attack order: every selected unit goes for that victim.
     *
     * <p><b>A friend is never a victim.</b> The queen herself, a spider of her brood and another unit of her own are
     * refused here (the entity refuses them a second time, every tick of the fight), and a refused order is not even
     * written on the unit.
     */
    public static int attack(ServerPlayer queen, LivingEntity victim)
    {
        int ordered = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            if (dragon == victim)
            {
                continue;
            }

            if (dragon.setForcedTarget(victim))
            {
                dragon.leaveFreeAttack();
                ordered++;
            }
        }

        return ordered;
    }

    /** The stand-down key: the selected units guard the blocks they stand on right now, each on its own. */
    public static int holdHere(ServerPlayer queen)
    {
        int held = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            dragon.holdAt(dragon.blockPosition());
            held++;
        }

        return held;
    }

    /** The soft rally: the selected units become followers of their queen, without one of them being moved. */
    public static int followHere(ServerPlayer queen)
    {
        int called = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            // Through the entity's own entry point, so the command version moves on and every flight of an older order
            // dies with it (round 7E, stage B2).
            dragon.followOrder();
            called++;
        }

        return called;
    }

    /** The hunt: the selected units attack whatever is not of the queen's own side, around wherever they are. */
    public static int freeAttack(ServerPlayer queen)
    {
        int hunting = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            dragon.huntOrder();
            hunting++;
        }

        return hunting;
    }
    // ------- skill and questions -------

    /**
     * The order key: the selected units step through the four orders together, as one group - follow, hold, patrol,
     * free attack, follow ...
     *
     * @return the order the group holds now, or null when nothing at all was commanded
     */
    @Nullable
    public static SpiderOrderMode cycleOrder(ServerPlayer queen)
    {
        List<SoulfireDragonEntity> units = commanded(queen);

        if (units.isEmpty())
        {
            return null;
        }

        if (!isUniform(units))
        {
            // A mixed group is unified first, so the first press can never take a group somewhere nobody asked for.
            for (SoulfireDragonEntity dragon : units)
            {
                dragon.followOrder();
            }

            return SpiderOrderMode.FOLLOW;
        }

        SpiderOrderMode next = units.get(0).getOrderMode().next();

        for (SoulfireDragonEntity dragon : units)
        {
            dragon.clearTemporaryCommand();
            dragon.cycleOrder();
        }

        return next;
    }

    /** The hard rally of the command wheel: every unit of that queen comes home and walks with her from now on. */
    public static int rally(ServerPlayer queen)
    {
        int called = 0;

        for (SoulfireDragonEntity dragon : ownedLoaded(queen))
        {
            dragon.rally();
            called++;
        }

        return called;
    }

    /**
     * The skill: that unit breathes at that place until another order arrives.
     *
     * <p>It is the ability of a skeleton exactly as it is the ability of a spider: the key of the queen, the place the
     * sceptre of the server pointed at, and <b>no cooldown anywhere</b> - a second cast simply recomputes the place and
     * starts the stream again.
     */
    public static boolean breathAt(SoulfireDragonEntity dragon, BlockPos target)
    {
        dragon.leaveFreeAttack();
        dragon.breatheAt(target);

        return true;
    }

    /** The skill of a whole group: every selected unit breathes at that place. */
    public static int breathAll(ServerPlayer queen, BlockPos target)
    {
        int breathing = 0;

        for (SoulfireDragonEntity dragon : commanded(queen))
        {
            if (breathAt(dragon, target))
            {
                breathing++;
            }
        }

        return breathing;
    }

    /**
     * @return true when the column of {@value SoulfireDragonEntity#SKY_HEIGHT} blocks above that point is open air,
     *         which is what decides whether the skill hovers over the point or comes down to stand beside it.
     */
    public static boolean skyOpen(Level level, BlockPos point)
    {
        for (int up = 1; up <= SoulfireDragonEntity.SKY_HEIGHT; up++)
        {
            if (!level.isEmptyBlock(point.above(up)))
            {
                return false;
            }
        }

        return true;
    }

    /** @return the standing order of that unit, {@link SpiderOrderMode#FOLLOW} when it is not one of ours. */
    public static SpiderOrderMode orderModeOf(@Nullable Entity dragon)
    {
        return dragon instanceof SoulfireDragonEntity unit ? unit.getOrderMode() : SpiderOrderMode.FOLLOW;
    }

    /** @return the post of that unit, or null when it has none. */
    @Nullable
    public static BlockPos anchorOf(@Nullable Entity dragon)
    {
        return dragon instanceof SoulfireDragonEntity unit ? unit.getAnchor() : null;
    }

    /** @return true when every unit of that group holds the same order right now. */
    private static boolean isUniform(List<SoulfireDragonEntity> units)
    {
        SpiderOrderMode first = units.get(0).getOrderMode();

        for (SoulfireDragonEntity dragon : units)
        {
            if (dragon.getOrderMode() != first)
            {
                return false;
            }
        }

        return true;
    }

}
