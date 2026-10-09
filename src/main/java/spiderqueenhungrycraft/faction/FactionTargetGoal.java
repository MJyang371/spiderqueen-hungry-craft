package spiderqueenhungrycraft.faction;

import java.util.EnumSet;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.world.QueenVillageHostility;

/**
 * The faction target goal (Phase HF1-VISIBLE, part M).
 *
 * <p>One goal for all three cases, because the rules already live in {@link FactionTargeting}: a human hunts queens,
 * ordinary monsters, illagers and the nether fortress, a monster hunts humans, an illager or a nether fortress unit
 * hunts humans, villages and the queen, and a village defender hunts the queen, monsters, illagers and the nether
 * fortress. The piglin ecology never gets this goal at all: it is neutral towards the queen and everything else about
 * a piglin belongs to its own vanilla AI. It is deliberately a thin extension of the vanilla
 * {@link NearestAttackableTargetGoal} so that the search box, the follow range and the "nearest wins" selection behave
 * exactly like they do everywhere else in the game.
 *
 * <p>Injecting it into an existing mob never overwrites the AI that mob already has. The injector uses a low
 * priority, so a vanilla target goal always wins, and {@link FactionTargeting#shouldSuppressExistingTarget} only ever
 * corrects the one relation that would contradict the faction design.
 */
public class FactionTargetGoal extends NearestAttackableTargetGoal<LivingEntity>
{
    private final Mob owner;

    public FactionTargetGoal(Mob mob)
    {
        super(mob, LivingEntity.class, 10, false, false, candidate -> FactionTargeting.shouldFactionGoalAttack(mob, candidate));
        this.owner = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    /**
     * The faction rules are checked again on every tick the goal runs, so a target that stops being valid - the queen
     * flag was cleared, she turned creative, she got Hero of the Village, she died or left the area - is dropped at
     * once instead of being chased forever.
     */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.owner.getTarget();

        if (target == null || !FactionTargeting.shouldFactionGoalAttack(this.owner, target))
        {
            return false;
        }

        return super.canContinueToUse();
    }

    /**
     * The village side of the search, fixed for player built golems (Phase 9B4).
     *
     * <p>Vanilla refuses a victim in principle through {@code Mob#canAttackType}, and the target conditions of
     * {@link NearestAttackableTargetGoal} ask exactly that question before this goal ever sees the candidate.
     * {@code IronGolem} answers "no" for <b>every</b> player while it was built by a player, and a modded guard that
     * reuses the iron golem inherits that refusal - so the injected faction goal could never select a Spider Queen
     * for those two, no matter what {@code player_created_golems_attack_spider_queen} said. This override answers the
     * one case the vanilla search is blind to and leaves every other mob on the untouched vanilla path.
     */
    @Override
    protected void findTarget()
    {
        Player queen = this.findQueenTheVanillaSearchRefuses();

        if (queen != null)
        {
            this.setTarget(queen);
            return;
        }

        super.findTarget();
    }

    /**
     * @return the nearest Spider Queen this mob may attack, but only for a mob whose own type rule refuses every
     *         player target, which is the one case the vanilla search can never see. {@code null} everywhere else, so
     *         a mob with working vanilla player targeting keeps using the vanilla path and is never changed by this.
     */
    @Nullable
    private Player findQueenTheVanillaSearchRefuses()
    {
        if (this.mob.canAttackType(EntityType.PLAYER) || this.mob.level().getDifficulty() == Difficulty.PEACEFUL
                || !(this.mob.level() instanceof ServerLevel level))
        {
            return null;
        }

        // The same rules the village side always used: an active queen, no Hero of the Village, line of sight - and
        // then the one faction rule, which is also where the config switch of the player built golems sits.
        Player queen = QueenVillageHostility.findNearestQueen(level, this.mob, this.getFollowDistance(), false);

        if (queen == null || !FactionTargeting.shouldFactionGoalAttack(this.mob, queen))
        {
            return null;
        }

        return queen;
    }

    /**
     * A village unit keeps the reach its own tuning always gave it: the {@code village_defender_attack_range} config
     * value wins whenever it is shorter than the follow range of the mob, so an existing pack setting still works.
     */
    @Override
    protected double getFollowDistance()
    {
        double followRange = super.getFollowDistance();

        if (FactionResolver.getFaction(this.owner) == FactionId.VILLAGE)
        {
            return Math.min(followRange, HungryCraftConfig.villageDefenderAttackRange());
        }

        return followRange;
    }
}
