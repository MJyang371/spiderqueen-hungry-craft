package spiderqueenhungrycraft.compat.iceandfire.ai;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.brood.BroodEntityTags;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyService;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.faction.FactionRelation;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.faction.QueenOwnerProtection;

/**
 * Who a contract dragon may hunt (Necromancy IV round, the brood stances of this round).
 *
 * <p>It is deliberately a second copy of the two questions {@code SpiderMinionTargeting} asks for a spider, written on
 * the vanilla {@link Mob} surface and for the very same rules - because a skeleton has to hunt what its brothers hunt:
 *
 * <ul>
 * <li><b>the natural prey</b> - what the queen side is at war with, asked through the faction layer <b>as the queen</b>
 * ({@link FactionTargeting#getRelation}); the spider of that queen asks it as itself, and the two answers are the same
 * relation because the colony of a spider is its queen. The villager exception of the brood is applied here as well, so
 * a dragon that guards a base can never empty the breeder of its own queen,</li>
 * <li><b>the free hunt</b> - everything alive that is not of the own side, which is the definition the brood uses for
 * {@code FREE_ATTACK} as well.</li>
 * </ul>
 *
 * <p>What is protected in both of them is the own side and the props: the queen herself, her spiders, her other
 * contract dragons, everything she has tamed, and the things the game does not even let a crosshair select. A creative
 * or spectating player is never a victim either.
 *
 * <p>This file names no Ice and Fire type: the two goals that use it are installed by the compatibility, and they work
 * on vanilla entities alone.
 */
public final class NecromancyHunting
{
    private NecromancyHunting()
    {
    }

    /** @return true when that creature is a legitimate target at all: alive, in the same level, and attackable. */
    public static boolean isTarget(@Nullable Mob minion, @Nullable LivingEntity candidate)
    {
        return minion != null && candidate != null && candidate != minion && candidate.isAlive()
                && !candidate.isRemoved() && candidate.level() == minion.level() && minion.canAttack(candidate);
    }

    /**
     * @return true when that creature is what a spider of that queen would attack: the natural prey of the brood, on a
     *         skeleton. It is the rule of follow, hold and patrol.
     */
    public static boolean isNaturalPrey(Mob minion, @Nullable Entity queen, LivingEntity candidate)
    {
        if (!isTarget(minion, candidate) || isOwnSide(minion, candidate))
        {
            return false;
        }

        if (queen == null)
        {
            return false;
        }

        // A villager is never handed to the brood by nature, and the dragon inherits the rule of the spiders.
        if (HungryCraftConfig.broodSpidersIgnoreVillagers()
                && candidate.getType().is(BroodEntityTags.VILLAGE_CIVILIANS))
        {
            return false;
        }

        return FactionTargeting.getRelation(queen, candidate) == FactionRelation.HOSTILE;
    }

    /**
     * @return true when that creature is prey of the free hunt: everything alive that is not of the own side, whether
     *         the faction matrix has an opinion about it or not. It is the rule of the free attack stance.
     */
    public static boolean isFreeAttackPrey(Mob minion, LivingEntity candidate)
    {
        if (!isTarget(minion, candidate) || isOwnSide(minion, candidate))
        {
            return false;
        }

        if (isProp(candidate))
        {
            return false;
        }

        return !(candidate instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /**
     * @return true when that creature stands on the side of the contract of that dragon: the queen who holds it, her
     *         spiders, her other contract dragons, and everything she has tamed.
     */
    public static boolean isOwnSide(Mob minion, LivingEntity candidate)
    {
        if (NecromancyService.isFriendlyToContract(minion, candidate) || !QueenOwnerProtection.mayAttack(minion, candidate))
        {
            return true;
        }

        // The tamed creatures of the queen are the household of the colony, exactly as they are for a spider: the
        // colony layer already answers them through the contract owner, and this is the same answer for a pet that the
        // colony layer does not know.
        return NecromancyService.contractOwner(minion)
                .map(owner -> candidate instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID()))
                .orElse(false);
    }

    /** @return true when that creature is a prop and not prey: never pickable, or a thing no monster swings at. */
    public static boolean isProp(LivingEntity candidate)
    {
        return !candidate.isPickable() || candidate instanceof ArmorStand;
    }
}
