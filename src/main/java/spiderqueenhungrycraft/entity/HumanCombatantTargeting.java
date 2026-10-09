package spiderqueenhungrycraft.entity;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.faction.FactionId;
import spiderqueenhungrycraft.faction.FactionResolver;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.hostility.HumanBandState;
import spiderqueenhungrycraft.hostility.HumanThreatMemory;

/**
 * The alignment of the prepared humans (Phase HF0A, extended in Phase HF0C for the three tiers).
 *
 * <p><b>TODO: HF1 full faction system will replace temporary human targeting.</b> This class is the one place that
 * answers "who is this human allowed to attack" for this round, so the answer can be swapped out without touching the
 * goal or the brain.
 *
 * <p>The temporary rule of this round: every prepared human counts as HUMAN (the low, mid and high tier ones are the
 * same java class, so they are covered by the same rule), a player with the queen flag counts as QUEEN and a player
 * without it counts as HUMAN. So a prepared human hunts queens and monsters, and never a normal player, a villager, a
 * golem, an animal or one of his own. The queen flag itself is never guessed - {@link FactionResolver} reads the
 * queen capability of that player every single time, so {@code /hungrycraft queen set} flips the alignment instantly.
 *
 * <p>Because the faction layer of this mod already exists, the temporary rule is expressed on top of it instead of
 * next to it: no second matrix, no second set of tags, no second source of truth.
 */
public final class HumanCombatantTargeting
{
    private HumanCombatantTargeting()
    {
    }

    /**
     * @return true when this combatant may pick that player as the target it attacks on its own: a real queen, alive,
     *         not creative and not spectating.
     */
    public static boolean isHostileQueen(@Nullable LivingEntity candidate)
    {
        if (!(candidate instanceof Player player))
        {
            return false;
        }

        if (!player.isAlive() || player.isRemoved() || player.isCreative() || player.isSpectator())
        {
            return false;
        }

        return FactionResolver.getFaction(player) == FactionId.QUEEN;
    }

    /**
     * @return true when that candidate is a target of this combatant right now: a Spider Queen player, a monster of
     *         the monster faction, or an attacker this human remembers (human pack aggro round).
     *
     * <p>The third case is what lets a whole group answer a spider that webbed one of them: a web shot is not a legal
     * target by alignment for a human, and without the grudge the group would record the attack and then stand there.
     * The grudge runs out on its own ({@link HumanThreatMemory#MEMORY_TICKS}), so nobody holds a target forever.
     */
    public static boolean isAttackableTarget(LivingEntity combatant, @Nullable LivingEntity candidate)
    {
        if (candidate == null || candidate == combatant || candidate.isRemoved() || !candidate.isAlive())
        {
            return false;
        }

        return isHostileQueen(candidate) || isHostileMonster(combatant, candidate)
                || isProvoked(combatant, candidate);
    }

    /**
     * @return true when this human still remembers being hit by that attacker, which makes that attacker a legal
     *         target of his for as long as the memory holds.
     */
    public static boolean isProvoked(LivingEntity combatant, @Nullable LivingEntity candidate)
    {
        if (candidate == null || candidate == combatant || !HungryCraftConfig.humanBandAggro())
        {
            return false;
        }

        return HumanThreatMemory.scoreOf(combatant, candidate, combatant.level().getGameTime()) > 0.0D;
    }

    /**
     * The band rule of the pack aggro round, asked by both target goals before a candidate may be picked: one enemy at
     * a time, and no new fight during the short peace after the last one.
     *
     * <p>It is a rule about <b>when</b> a human may pick a target at all, not about who he may attack - that question
     * stays with {@link #isAttackableTarget(LivingEntity, LivingEntity)}. So the vanilla "nearest wins" search can
     * never quietly hand a soldier a different enemy than the one he is committed to, which is the bug this round
     * exists to prevent.
     */
    public static boolean mayPickBandTarget(LivingEntity combatant, LivingEntity candidate)
    {
        long now = combatant.level().getGameTime();

        // A soldier who is walking back to his post starts no new fight on the way: without this rule the vanillar
        // "nearest wins" search would hand him the very enemy he just walked away from, one tick after the leash
        // dropped it, and the two rules would fight each other at the edge of his ground for ever.
        if (HumanBandState.isReturning(combatant))
        {
            return false;
        }

        if (HumanBandState.isLockedOn(combatant, candidate, now))
        {
            return true;
        }

        if (HumanBandState.lockedTarget(combatant, now) != null)
        {
            return false;
        }

        return !HumanBandState.inPeace(combatant, now) || isProvoked(combatant, candidate);
    }

    /**
     * @return true when that candidate is a monster this combatant should hunt.
     *
     * <p>The rule is data first: the {@code monster_faction} entity type tag decides. Types that are filed as
     * something else - a human, a villager, a queen, an illager, a piglin, a fortress unit or an explicit
     * {@code no_faction} entry - are respected and never attacked, and only a type that no tag mentions at all falls
     * back to the shared generic hostile monster rule of the faction system, which is the vanilla {@code Monster} or
     * {@code MobCategory.MONSTER} marker. So a pack author can always take a creature out of the war with one json
     * line, and this combatant can never disagree with Chad Steve or a village defender about what a monster is.
     */
    public static boolean isHostileMonster(LivingEntity combatant, @Nullable LivingEntity candidate)
    {
        if (candidate == null || candidate == combatant || candidate.isRemoved() || !candidate.isAlive())
        {
            return false;
        }

        if (HungryCraftConfig.humanCombatantIgnoresMonsters())
        {
            return false;
        }

        // The faction is asked first, and it decides on its own - exactly like it does for Chad Steve, a village
        // defender or a zombie. What that type extends is irrelevant: a creature that is filed as a human, a villager,
        // a queen, an illager, a piglin, a fortress unit or an explicit no_faction entry is never a monster for this
        // combatant. That is what keeps a modded human which happens to extend Monster from being hunted by his own
        // kind. A player is covered by the same rule: a normal player is HUMAN, a Spider Queen is QUEEN.
        FactionId faction = FactionResolver.getFaction(candidate);

        if (faction != FactionId.NONE)
        {
            return faction == FactionId.MONSTER;
        }

        // Only a type that no faction tag mentions at all reaches this point, and only then does the shared generic
        // hostile monster rule of the faction system decide: the vanilla Monster marker is the fallback of last resort,
        // never the first question. A type that a pack author filed into no_faction never ends up here.
        return FactionTargeting.isGenericHostileMonster(candidate);
    }

    /** @return true while a target that was already picked is still a valid target. */
    public static boolean isTargetStillAllowed(LivingEntity combatant, @Nullable LivingEntity target)
    {
        return target != null && isAttackableTarget(combatant, target);
    }
}
