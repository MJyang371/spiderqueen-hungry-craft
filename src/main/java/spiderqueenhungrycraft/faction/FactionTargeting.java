package spiderqueenhungrycraft.faction;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.world.QueenVillageHostility;

/**
 * The decisions of the faction system (Phase HF1-VISIBLE parts J and P, extended by the faction split pass and
 * the Queen Colony framework).
 *
 * <p>{@link #getRelation} is the general entry point of the whole system, and it answers for <b>any</b> pair of
 * entities - never only for a player. A spider, a golem, a villager, a raider, a creature of another mod and a player
 * are all asked the same way, and the answer is one of {@link FactionRelation#FRIENDLY},
 * {@link FactionRelation#NEUTRAL}, {@link FactionRelation#HOSTILE} or {@link FactionRelation#PASS_THROUGH}
 * ("this system has no opinion here"). Every other rule of this class is written on top of that one answer, so no
 * caller ever has to know what class the other side is - the faction of an entity is read through
 * {@link FactionResolver}, which is a tag question for every entity type and a queen flag question for a player.
 *
 * <p>{@link #shouldFactionGoalAttack} answers the question of every faction target goal of this mod: may this mob
 * pick that victim? It asks the faction matrix, and for the queen side it asks the colony layer first
 * ({@link QueenColonyRelations}) so that two different queens can never be locked into a friendship by their shared
 * faction. {@link #shouldSuppressExistingTarget} answers the much narrower question of which vanilla target a mob is
 * <b>not</b> allowed to keep, and it is deliberately short: only the ordinary monster faction hunting the queen is
 * corrected. Everything else - a zombie hunting a villager, a golem hunting a zombie, a guard hunting a zombie, a
 * piglin hunting a player who did not wear gold, a monster hunting a normal player - stays exactly the vanilla
 * behaviour it always was.
 */
public final class FactionTargeting
{
    private FactionTargeting()
    {
    }

    /**
     * @return true when a faction target goal of this mod may let that mob attack that victim right now.
     *
     * <p>Hostile relations are attacked, friendly and pass-through relations never are, and a neutral relation is
     * attacked only by a victim that remembers being hit first. Humans and village defenders additionally treat a
     * hostile monster that no faction tag mentions as an enemy - see {@link #isGenericHostileMonster}.
     *
     * <p>This is the goal side of {@link #getRelation} and it differs from it in exactly two points, both of which are
     * about a goal and not about a relation: a victim that is already gone or dead is no target any more, and a
     * creative or spectating player is never picked by a faction goal, however hostile the relation may be.
     */
    public static boolean shouldFactionGoalAttack(Mob source, LivingEntity target)
    {
        if (source == null || target == null || source == target || target.isRemoved() || !target.isAlive())
        {
            return false;
        }

        // Creative and spectator players are never attacked by a faction goal, no matter the relation.
        if (target instanceof Player player && (player.isCreative() || player.isSpectator()))
        {
            return false;
        }

        return switch (getRelation(source, target))
        {
            case HOSTILE -> true;
            case NEUTRAL -> remembersTheAttack(source, target);
            default -> false;
        };
    }

    /**
     * @return how this mod sees two entities right now. The general question of the whole faction system, and it takes
     *         <b>any</b> pair of entities: whoever asks it never has to know whether the other side is a player, a
     *         villager, a golem, a raider, a spider or a creature of another mod.
     *
     * <p>The answer is the complete rule set of this mod, in the order it is decided:
     * <ol>
     * <li>the colony layer - two creatures of the same Spider Queen are {@link FactionRelation#FRIENDLY} and never
     * fight each other, whatever the matrix says (Queen Colony framework),</li>
     * <li>the village rules of this mod, which can take the village side off a queen target - see
     * {@link #villageMayHuntQueen} - and then answer "no opinion" instead of hostile,</li>
     * <li>the generic hostile monster fallback of the faction split pass: the human side, the villages and the queen
     * side treat a hostile monster that no faction tag mentions as an enemy,</li>
     * <li>and finally the faction matrix of {@link FactionResolver}.</li>
     * </ol>
     *
     * <p>{@link FactionRelation#PASS_THROUGH} is the answer whenever one side is not a living creature, whenever one
     * side has no faction at all and for the one village case above. It is not a relation: it means the faction system
     * has no opinion about that pair, so the vanilla and third party behaviour stays exactly as it is.
     */
    public static FactionRelation getRelation(@Nullable Entity source, @Nullable Entity target)
    {
        if (source == null || target == null || !(source instanceof LivingEntity attacker)
                || !(target instanceof LivingEntity victim))
        {
            return FactionRelation.PASS_THROUGH;
        }

        if (source == target)
        {
            return FactionRelation.FRIENDLY;
        }

        if (victim.isRemoved() || !victim.isAlive())
        {
            return FactionRelation.PASS_THROUGH;
        }

        FactionId mine = FactionResolver.getFaction(attacker);

        if (mine == FactionId.NONE)
        {
            return FactionRelation.PASS_THROUGH;
        }

        FactionId theirs = FactionResolver.getFaction(victim);

        // The village side of the matrix (Phase 8H), asked for every queen side target. A target that is not a player
        // is answered by villageMayHuntQueen without a single type test on it - which is the fix of the crash this
        // round is about: the cast to Player used to be made before this question could even be asked, so a village
        // unit that scanned a creeper spider threw a ClassCastException instead of answering it.
        if (mine == FactionId.VILLAGE && theirs == FactionId.QUEEN && attacker instanceof Mob villageUnit
                && !villageMayHuntQueen(villageUnit, victim))
        {
            return FactionRelation.PASS_THROUGH;
        }

        // The queen side is not one block (Queen Colony framework). Two creatures of the same Spider Queen are family
        // and are never attacked by a faction goal. Two queen side creatures of different queens - or of a queen side
        // type without an owner - are deliberately left alone here: not friendly, but not hostile either, because
        // that verdict belongs to PvP rules, the future queen sceptre or the revenge memory, not to this matrix.
        if (QueenColonyRelations.isQueenSideFriendly(attacker, victim))
        {
            return FactionRelation.FRIENDLY;
        }

        // The generic hostile monster fallback of the faction split pass: a human, a village defender or a creature of
        // the queen side treats a hostile monster of the vanilla game - or of any other mod - as an enemy even when no
        // faction tag mentions it at all. A type that is deliberately filed into no_faction is never one of them, and
        // neither is a creature that belongs to a faction of its own (a piglin, for example, which the queen side
        // leaves alone).
        if ((mine == FactionId.HUMAN || mine == FactionId.VILLAGE || mine == FactionId.QUEEN)
                && isGenericHostileMonster(victim))
        {
            return FactionRelation.HOSTILE;
        }

        return FactionResolver.resolve(attacker, victim);
    }

    /**
     * @return true when that candidate is a hostile monster that no faction tag mentions at all.
     *
     * <p>"Hostile monster" is the vanilla marker: a {@code Monster} of the game or any entity of the monster spawn
     * category - which is exactly the set the prepared humans have always hunted. The one exception is a type that a
     * pack author deliberately filed into {@code no_faction}: that hard exclusion wins over the class of a creature,
     * so nobody this mod controls is ever forced to attack it.
     */
    public static boolean isGenericHostileMonster(@Nullable LivingEntity candidate)
    {
        if (candidate == null || candidate.getType().is(FactionTags.NO_FACTION))
        {
            return false;
        }

        if (FactionResolver.getFaction(candidate) != FactionId.NONE)
        {
            return false;
        }

        return candidate instanceof Monster || candidate.getType().getCategory() == MobCategory.MONSTER;
    }

    /**
     * @return true when that already chosen target must be cleared again, because the vanilla game picked something
     *         the faction design says it should not attack.
     *
     * <p>The one case of this round: a monster that picked a Spider Queen on its own. A monster may always answer an
     * attack, so a queen who hit it first is never taken away from it.
     */
    public static boolean shouldSuppressExistingTarget(Mob source, LivingEntity target)
    {
        if (source == null || target == null || source == target)
        {
            return false;
        }

        if (FactionResolver.getFaction(source) != FactionId.MONSTER
                || FactionResolver.getFaction(target) != FactionId.QUEEN)
        {
            return false;
        }

        if (remembersTheAttack(source, target))
        {
            return false;
        }

        return FactionResolver.resolve(source, target) == FactionRelation.NEUTRAL;
    }

    /** @return true when that mob belongs to a faction that should receive the faction target goal (part M). */
    public static boolean needsFactionGoal(Mob mob)
    {
        return switch (FactionResolver.getFaction(mob))
        {
            // Humans and the hostile ecologies hunt through the faction rules, a village only through its defenders: an
            // ordinary villager must never turn into a soldier. The piglin ecology stays out completely - it is neutral
            // towards the queen, and everything else about a piglin is decided by its own vanilla AI.
            case HUMAN, MONSTER, ILLAGER, NETHER_FORTRESS -> true;
            case VILLAGE -> FactionResolver.isVillageDefender(mob.getType());
            default -> false;
        };
    }

    /**
     * @return true while that mob is allowed to fight back after being hit.
     *
     * <p>Both memories count: the short faction memory of this system and the vanilla revenge target, which the
     * provocation of a web shot or a melee hit sets as well.
     */
    private static boolean remembersTheAttack(Mob source, LivingEntity target)
    {
        return FactionAggroMemory.isAngryAt(source, target) || source.getLastHurtByMob() == target;
    }

    /**
     * The village rules of this mod, unchanged (Phase 8H): a village unit only hunts a queen while the village
     * hostility is switched on, while she is a real threat instead of a creative or spectating onlooker, while she
     * does not carry Hero of the Village, and while the unit is not a player built golem that is set to stay calm.
     *
     * <p><b>A queen side creature that is not a player.</b> The four rules above are about a human Spider Queen: her
     * queen flag, her game mode, her effect and the golem that was built for her can only be asked about a player. A
     * creeper spider, an infested Steve or any other creature of the {@code queen_faction} tag is a queen side target
     * of the matrix and has none of that, so the village side simply stands as the matrix wrote it - the village is at
     * war with the queen side, not only with her. The master switch of the village hostility above still covers the
     * whole war for both cases, and {@code ignoresSpiderQueen} stays a rule about the queen player herself: a player
     * built golem that ignored every spider would no longer defend the village it was built for.
     */
    private static boolean villageMayHuntQueen(Mob source, LivingEntity queen)
    {
        if (!HungryCraftConfig.villageHostilityEnabled())
        {
            return false;
        }

        if (!(queen instanceof Player player))
        {
            return true;
        }

        return QueenVillageHostility.isActiveQueen(player) && !QueenVillageHostility.isExemptByHero(player)
                && !QueenVillageHostility.ignoresSpiderQueen(source);
    }
}
