package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.faction.QueenOwnerProtection;

/**
 * The targeting rules of a spider minion (Brood Command AI), kept in one place so no goal ever invents a second
 * version of "who may we attack".
 *
 * <p>Every rule first passes {@link QueenOwnerProtection#mayAttack}: the owner and the spiders of the same owner are
 * never valid targets, no matter which goal asks.
 *
 * <p><b>Two questions, and the order of the spider decides which one is asked.</b> An ordinary spider
 * ({@link #isNaturalEnemy}) hunts what the faction matrix calls an enemy of the queen side; a spider on the
 * {@link SpiderOrderMode#FREE_ATTACK free attack} order ({@link #isFreeAttackEnemy}) hunts <b>everything</b> that is not
 * of its own side, whether the matrix has an opinion about that creature or not. The goal behind both of them asks
 * {@link #isEnemy}, so the switch is one line and can not be forgotten in a goal.
 *
 * <p>The natural rule does not hard code a single creature type. It asks the faction layer
 * ({@link FactionTargeting#shouldFactionGoalAttack}), so the spiders hunt exactly what the queen side is at war with
 * (illagers, the nether fortress, humans and villages), ignore what the matrix calls neutral (ordinary monsters and
 * the piglin ecology) and still see an unaffiliated hostile monster of the game or of another mod as an enemy.
 *
 * <p><b>One exception, decided by the brood itself (Brood Villager Neutrality pass).</b> An ordinary villager is never
 * a natural enemy of a spider, however hostile the faction matrix is between the queen side and the village: nature does
 * not hand a villager to a spider, so a spider that guards a base can never empty the breeder or the raid farm of the
 * player who built it. The exception is one entry of {@link BroodEntityTags#VILLAGE_CIVILIANS} and one config switch,
 * and it is asked before the faction question, so it can neither be forgotten by a goal nor widened by a future entry of
 * the faction tags. Iron golems, guards and every other village defender are deliberately <b>not</b> in that tag: a
 * village still defends itself, and a spider that was hit still answers.
 *
 * <p>Everything a queen <b>orders</b> her spiders to do is untouched by it, because no order comes through this method:
 * the sceptre, the help of a follower while its queen fights a villager and the self defence of a spider all go through
 * {@link #isAttackable(SpiderMinionEntity, LivingEntity)}.
 */
public final class SpiderMinionTargeting
{
    private SpiderMinionTargeting()
    {
    }

    /**
     * @return true when the targeting goal of that spider may pick that victim right now: the faction question under
     *         every ordinary order, and the question of the free attack order while the spider is hunting.
     */
    public static boolean isEnemy(SpiderMinionEntity spider, LivingEntity candidate)
    {
        return spider.getOrderMode().huntsFreely()
                ? isFreeAttackEnemy(spider, candidate)
                : isNaturalEnemy(spider, candidate);
    }

    /**
     * @return true when nature may give that spider that victim: not the owner, not a sibling, and either a faction
     *         the queen side is hostile to or an unaffiliated hostile monster.
     */
    public static boolean isNaturalEnemy(SpiderMinionEntity spider, LivingEntity candidate)
    {
        // A direct order always outranks nature: while a forced target is set, the spider does not look for others.
        if (spider.getForcedTarget() != null)
        {
            return false;
        }

        if (!QueenOwnerProtection.mayAttack(spider, candidate))
        {
            return false;
        }

        // A villager is never a natural enemy, whatever the faction matrix says about the queen side and the village:
        // nature simply does not hand a villager to a spider. The master switch of the rule is asked here as well, so a
        // pack author gets the old behaviour back with one line of the common config.
        if (HungryCraftConfig.broodSpidersIgnoreVillagers()
                && candidate.getType().is(BroodEntityTags.VILLAGE_CIVILIANS))
        {
            return false;
        }

        return FactionTargeting.shouldFactionGoalAttack(spider, candidate);
    }

    /**
     * The rule of the free attack order: <b>everything alive that is not of the own side of that spider is a victim</b>
     * - a human, a villager, a zombie, a skeleton, a cow, an iron golem, a creature of another mod, and the spiders of
     * another queen.
     *
     * <p>It is deliberately not the faction matrix. A queen who tells her brood to hunt has decided that the world
     * around her is prey, and the matrix would leave most of it alone: ordinary monsters are neutral towards the queen
     * side, and a villager is never handed to a spider by nature. Both of those are rules of <b>nature</b>, and this is
     * an order - the one place in this class where a villager really is a valid victim.
     *
     * <p>What stays protected is the own side and the props: the queen herself and every spider of hers
     * ({@link QueenOwnerProtection#mayAttack}), everything she has tamed and the creeper ({@link #isOwnSide}), and the
     * things that are not creatures of the world at all ({@link #isProp}). A creative or spectating player is never a
     * victim either, exactly as in every other targeting rule of the game.
     */
    public static boolean isFreeAttackEnemy(SpiderMinionEntity spider, LivingEntity candidate)
    {
        if (spider == null || candidate == null || candidate == spider)
        {
            return false;
        }

        // A direct order always outranks the hunt as well: while a forced target is set, the spider does not look for
        // others.
        if (spider.getForcedTarget() != null)
        {
            return false;
        }

        // Never the queen, never a spider of the same queen, whatever the order says.
        if (!QueenOwnerProtection.mayAttack(spider, candidate))
        {
            return false;
        }

        if (isOwnSide(spider, candidate))
        {
            return false;
        }

        // A prop is not prey either: something the game does not even let a crosshair select (the stomach core of a
        // queen is one) or a decoration that no monster of the game ever swings at (an armour stand) is not a creature
        // of the world, and a hunt must never break what the queen built.
        if (isProp(candidate))
        {
            return false;
        }

        if (!candidate.isAlive() || candidate.isRemoved() || candidate.level() != spider.level())
        {
            return false;
        }

        return !(candidate instanceof Player player && (player.isCreative() || player.isSpectator()));
    }

    /**
     * @return true when that creature is a <b>prop</b> rather than a creature of the world: not pickable, so the game
     *         itself never lets a crosshair land on it (the stomach core of a queen), or a decoration that the monsters
     *         of the game never attack either (an armour stand).
     *
     * <p>It is asked on the free attack path only. An order of the queen still reaches a prop - the sceptre can send a
     * spider at an armour stand, because that is a decision of the player and not a decision of nature.
     */
    public static boolean isProp(LivingEntity candidate)
    {
        return !candidate.isPickable() || candidate instanceof ArmorStand;
    }

    /**
     * @return true when that creature belongs to the own side of that spider without the colony rule covering it: a
     *         creature the queen has <b>tamed</b>, or a <b>creeper</b>.
     *
     * <p>The tamed creatures are the obvious half: a wolf, a cat, a parrot, a horse, a donkey, a camel and whatever else
     * a player can own is part of the household, and a hunting spider must not kill the pets of its own queen.
     *
     * <p>The creeper is the odd half and it is on the list by design. It is not one of the queen's creatures and the
     * faction matrix never sees a creeper as anything but an ordinary monster, but a spider that bites a creeper dies in
     * the blast of the very creeper it bit - and the brood of this mod works beside creepers rather than against them
     * (the creeper spider is one of its own species). The hunt therefore leaves the creeper alone, while the creeper
     * itself still explodes on anything that walks into its path.
     *
     * @param spider    the spider whose side is asked
     * @param candidate the creature that was found nearby
     */
    public static boolean isOwnSide(SpiderMinionEntity spider, LivingEntity candidate)
    {
        if (candidate instanceof Creeper)
        {
            return true;
        }

        UUID owner = spider.getOwnerUUID();

        return owner != null && candidate instanceof OwnableEntity ownable && owner.equals(ownable.getOwnerUUID());
    }

    /**
     * @return true when that victim may be attacked right now by a goal that ignores the faction matrix: the owner
     *         assist of the queen, the revenge of the spider itself and an order of the sceptre all use this gate.
     */
    public static boolean isAttackable(@Nullable SpiderMinionEntity spider, @Nullable LivingEntity candidate)
    {
        return spider != null && candidate != null && candidate.isAlive() && !candidate.isRemoved()
                && candidate.level() == spider.level() && QueenOwnerProtection.mayAttack(spider, candidate);
    }
}
