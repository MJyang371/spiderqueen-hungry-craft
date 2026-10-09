package spiderqueenhungrycraft.devour;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;

/**
 * Who may be devoured (Phase 8E2A).
 *
 * <p>The query itself is not written here: a devour uses the very same jaws as a bite, so the volume, the line of
 * sight and the vertical semantics come from {@code QueenBeastBite}. This class only answers the question that
 * differs - what a throat refuses to swallow.
 *
 * <p>Refused are the queen herself, the wall of a stomach, the dead, the spectators, everything that is already
 * inside a stomach, everything a pack marked as undevourable, a creature that is carrying passengers (its riders
 * would be left behind in another dimension) and - for players - the creative and the protected ones, following
 * the ordinary vanilla rules for hitting another player.
 */
public final class DevourTargets
{
    /** Entity types no queen may ever swallow, filled by datapacks. */
    public static final TagKey<EntityType<?>> UNDEVOURABLE = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "undevourable"));

    private DevourTargets()
    {
    }

    /** @return true when that creature may end up in the stomach of that beast. */
    public static boolean eligible(ServerPlayer beast, LivingEntity candidate)
    {
        if (candidate == beast || candidate.isRemoved() || !candidate.isAlive() || candidate.isSpectator())
        {
            return false;
        }

        if (candidate instanceof StomachCoreEntity)
        {
            return false;
        }

        // Somebody who is already in a stomach is inside a stomach, not in front of a mouth.
        if (candidate.level().dimension().equals(StomachCellManager.DIMENSION))
        {
            return false;
        }

        if (candidate.getType().is(UNDEVOURABLE))
        {
            return false;
        }

        // A mount with riders is deliberately not supported yet: the whole passenger tree would have to travel,
        // and a half moved tree is worse than a refused snack.
        if (!candidate.getPassengers().isEmpty())
        {
            return false;
        }

        if (candidate instanceof Player other)
        {
            // A creative or spectating player is a moderator, not prey.
            if (other.isCreative() || other.isSpectator())
            {
                return false;
            }

            return beast.canHarmPlayer(other);
        }

        return true;
    }
}
