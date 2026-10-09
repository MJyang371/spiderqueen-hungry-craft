package spiderqueenhungrycraft.faction;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.hostility.HumanProvocation;
import spiderqueenhungrycraft.human.HumanEntityTypes;

/**
 * The event face of the faction system (Phase HF1-VISIBLE, parts K, L, M and P).
 *
 * <p>This replaces the scattered listeners of the old design: {@code QueenMonsterNeutrality} no longer subscribes to
 * anything itself and {@code QueenVillageHostility} no longer hands out targets, both now only keep their helper
 * methods and delegate their decisions here.
 *
 * <p>Four small jobs, nothing more:
 * <ol>
 * <li>refuse a target the faction design forbids, the moment a mob tries to pick it,</li>
 * <li>do the same once per second for the paths that never post that event,</li>
 * <li>remember who hit whom, so a neutral party may hit back,</li>
 * <li>and give every mob that takes part in the faction war the one faction target goal.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class FactionEvents
{
    /** How often an already set target is looked at again. */
    private static final int TARGET_RECHECK_INTERVAL_TICKS = 20;

    /**
     * Priority of the injected goal. It is deliberately higher (that is, weaker) than every vanilla target goal, so
     * the AI a mob already has is never overwritten by the faction system.
     */
    private static final int INJECTED_GOAL_PRIORITY = 8;

    private FactionEvents()
    {
    }

    /** The cheapest moment to refuse a target: the tick a mob tries to pick it. */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event)
    {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide())
        {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();

        if (newTarget != null && FactionTargeting.shouldSuppressExistingTarget(mob, newTarget))
        {
            event.setNewTarget(null);
            event.setCanceled(true);
        }
    }

    /** The same decision for the paths that bypass the change target event, for example a revenge goal. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()
                || mob.tickCount % TARGET_RECHECK_INTERVAL_TICKS != 0)
        {
            return;
        }

        LivingEntity target = mob.getTarget();

        if (target != null && FactionTargeting.shouldSuppressExistingTarget(mob, target))
        {
            mob.setTarget(null);
        }
    }

    /**
     * Neutral means neutral until somebody swings: the victim remembers who hit it and may fight back for the next
     * twenty seconds. A friendly pair is recorded as well, but that memory is never consulted for a friendly
     * relation, so two humans never start a faction war over it.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F)
        {
            return;
        }

        Entity causing = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();

        LivingEntity attacker = causing instanceof LivingEntity living ? living
                : direct instanceof LivingEntity living ? living : null;

        if (attacker != null)
        {
            FactionAggroMemory.remember(event.getEntity(), attacker);

            // The pack rules of the human units (human pack aggro round). The faction memory above stays exactly what
            // it is; this is the second layer, and it only ever runs for a human of this mod as the victim. A web shot
            // deals no damage and never reaches this listener, so WebShotProjectile tells the humans by hand.
            if (HungryCraftConfig.humanBandAggro() && HumanEntityTypes.isHumanMob(event.getEntity()))
            {
                HumanProvocation.provoke(event.getEntity(), attacker,
                        event.getSource().is(DamageTypeTags.IS_PROJECTILE) ? HumanProvocation.Cause.PROJECTILE
                                : HumanProvocation.Cause.MELEE_OR_DAMAGE);
            }
        }
    }

    /** Gives every mob that takes part in the faction war the one faction target goal, exactly once. */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event)
    {
        Level level = event.getLevel();

        if (level.isClientSide() || !(event.getEntity() instanceof Mob mob))
        {
            return;
        }

        // Chad Steve owns his targeting: he registers the very same FactionTargetGoal in his own registerGoals(), so
        // the faction system must not inject a second one. The question is asked through the marker interface, never
        // through the name of one entity type, so a future special human states the same thing by implementing it.
        if (mob instanceof OwnFactionTargeting || !FactionTargeting.needsFactionGoal(mob))
        {
            return;
        }

        // Goals are never part of a save, the persistent data of a mob always is: an "already injected" flag in that
        // data would survive a save and reload while the goal itself is gone, and the mob would keep that blind spot
        // for good - which is exactly how village golems stopped hunting the queen after a restart. Asking the
        // selector is exact instead: it is true only while the goal is really there, and it still keeps a reload from
        // adding the same goal twice (Phase HF1-VISIBLE part M, fixed in Phase 9B5).
        if (hasFactionGoal(mob))
        {
            return;
        }

        mob.targetSelector.addGoal(INJECTED_GOAL_PRIORITY, new FactionTargetGoal(mob));
    }

    /** @return true while that mob really holds a faction target goal right now. */
    private static boolean hasFactionGoal(Mob mob)
    {
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals())
        {
            if (wrapped.getGoal() instanceof FactionTargetGoal)
            {
                return true;
            }
        }

        return false;
    }
}
