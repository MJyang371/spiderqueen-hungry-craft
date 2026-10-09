package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.attack.ShieldProjectileInteraction;
import spiderqueenhungrycraft.entity.HumanCombatantTargeting;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * One tick worth of "what is going on around me" (Sweatlord round).
 *
 * <p>Every skill of the brain reads this snapshot and nothing else, so no skill has to query the world in its own way
 * and two skills can never disagree about how far the enemy is or whether there is line of sight. It is captured once
 * per tick, before any decision is taken.
 */
public final class CombatContext
{
    /** Inside this distance he fights with the sword instead of the bow. */
    public static final double MELEE_DISTANCE = 4.0D;

    /** Inside this distance a bow is a club, so it is put away and he closes in. */
    public static final double BOW_MIN_DISTANCE = 5.0D;

    /** Outside this distance the bow, the eggs and the trident are the main options. */
    public static final double BOW_MAX_DISTANCE = 24.0D;

    private final SweatlordSteveEntity mob;

    /** The enemy of the encounter, null while there is none. */
    @Nullable
    private final LivingEntity target;

    private final double distance;
    private final boolean lineOfSight;
    private final float healthRatio;
    private final boolean targetShieldUp;
    private final boolean inMeleeReach;
    private final boolean appleBuffActive;
    private final boolean inDangerousFluid;
    private final boolean badGroundAhead;

    private CombatContext(SweatlordSteveEntity mob, @Nullable LivingEntity target, double distance,
            boolean lineOfSight, float healthRatio, boolean targetShieldUp, boolean inMeleeReach,
            boolean appleBuffActive, boolean inDangerousFluid, boolean badGroundAhead)
    {
        this.mob = mob;
        this.target = target;
        this.distance = distance;
        this.lineOfSight = lineOfSight;
        this.healthRatio = healthRatio;
        this.targetShieldUp = targetShieldUp;
        this.inMeleeReach = inMeleeReach;
        this.appleBuffActive = appleBuffActive;
        this.inDangerousFluid = inDangerousFluid;
        this.badGroundAhead = badGroundAhead;
    }

    /** Takes the snapshot of this tick. */
    public static CombatContext capture(SweatlordSteveEntity mob)
    {
        LivingEntity target = mob.getTarget();
        double distance = target == null ? Double.MAX_VALUE : mob.distanceTo(target);

        return new CombatContext(mob, target, distance, target != null && mob.hasLineOfSight(target),
                mob.getHealth() / mob.getMaxHealth(),
                target != null && isReallyBlocking(target),
                target != null && distance <= mob.attackReach(),
                hasAppleBuff(mob),
                EnvironmentSafety.isInDangerousFluid(mob),
                EnvironmentSafety.isGroundAheadUnsafe(mob));
    }

    /** @return true when that body is really holding a shield up in front of itself right now. */
    public static boolean isReallyBlocking(LivingEntity body)
    {
        return ShieldProjectileInteraction.holdsUpAShield(body);
    }

    /** @return true while the buffs of a (plain or enchanted) golden apple are still on that body. */
    public static boolean hasAppleBuff(LivingEntity body)
    {
        return body.hasEffect(MobEffects.ABSORPTION) || body.hasEffect(MobEffects.REGENERATION)
                || body.hasEffect(MobEffects.DAMAGE_RESISTANCE) || body.hasEffect(MobEffects.FIRE_RESISTANCE);
    }

    /** @return true when that entity is a legal enemy of this body, asked through the shared human rules. */
    public static boolean isValidEnemy(SweatlordSteveEntity mob, LivingEntity candidate)
    {
        if (candidate == mob || candidate.isRemoved() || !candidate.isAlive()
                || candidate instanceof SweatlordSteveEntity)
        {
            return false;
        }

        // The very rule every other human of this mod uses: a Spider Queen, a monster of the monster faction, and
        // whoever attacked him or one of his comrades (the grudge of the pack layer). Asking that rule instead of a
        // private copy of it is what keeps the Sweatlord on exactly the same side as the humans he fights next to -
        // and it is what makes the pack aggro of the human units reach him as well.
        if (HumanCombatantTargeting.isAttackableTarget(mob, candidate))
        {
            return true;
        }

        // And then the one rule of this body, which is the fight he is already in: an ordinary player is part of the
        // human side and only the grudge of the pack layer ever put him on that list at all, so the memory running out in
        // the middle of a duel dropped him - the body stopped wanting the fight and stood there until the next hit, which
        // is exactly the "lost AI" of the client report. A lone PvP opponent who attacked him and is still visible and
        // in reach stays his enemy for as long as that is true; walking away or breaking the line of sight still ends it.
        return stillEngagedWith(mob, candidate);
    }

    /**
     * @return true when that body is still the enemy this one is fighting right now: the target he already holds, alive,
     *         in line of sight and inside the reach of his fight.
     *
     * <p>It is deliberately narrow: it never <b>picks</b> an enemy (the grudge or the alignment does that), it only
     * <b>keeps</b> the one he is already in a fight with. So a player who broke the contact, hid behind a wall or walked
     * out of range is dropped exactly as before, and a whole fight is never held together by this rule alone.
     */
    private static boolean stillEngagedWith(SweatlordSteveEntity mob, LivingEntity candidate)
    {
        if (mob.getTarget() != candidate)
        {
            return false;
        }

        if (!mob.hasLineOfSight(candidate))
        {
            return false;
        }

        return mob.distanceTo(candidate) <= SweatlordCombatBrain.TARGET_SEARCH_RANGE * 1.5D;
    }

    @Nullable
    public LivingEntity target()
    {
        return this.target;
    }

    public boolean hasTarget()
    {
        return this.target != null;
    }

    public double distance()
    {
        return this.distance;
    }

    public boolean lineOfSight()
    {
        return this.lineOfSight;
    }

    public float healthRatio()
    {
        return this.healthRatio;
    }

    public boolean targetShieldUp()
    {
        return this.targetShieldUp;
    }

    public boolean inMeleeReach()
    {
        return this.inMeleeReach;
    }

    public boolean appleBuffActive()
    {
        return this.appleBuffActive;
    }

    public boolean inDangerousFluid()
    {
        return this.inDangerousFluid;
    }

    public boolean badGroundAhead()
    {
        return this.badGroundAhead;
    }

    /** @return true when the enemy is far enough that the ranged kit is the right one. */
    public boolean wantsRanged()
    {
        return this.distance > MELEE_DISTANCE;
    }

    /** @return a short line about the enemy, for the gated debug log and the debug state dump. */
    public String summary()
    {
        if (this.target == null)
        {
            return "no target, hp=" + Math.round(this.healthRatio * 100.0F) + "%";
        }

        return this.target.getType().toShortString()
                + " d=" + String.format(java.util.Locale.ROOT, "%.1f", this.distance)
                + (this.lineOfSight ? " los" : " nolos")
                + (this.targetShieldUp ? " shieldUp" : "")
                + " hp=" + Math.round(this.healthRatio * 100.0F) + "%"
                + " main=" + this.mob.loadout().mainHand().getItem();
    }
}
