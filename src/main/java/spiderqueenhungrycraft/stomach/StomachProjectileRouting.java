package spiderqueenhungrycraft.stomach;

import java.util.Optional;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Arrows that end their flight on the stomach wall (Phase 8E1-GATE2A-POLISH-1).
 *
 * <p>A core is deliberately not a pickable entity, so an arrow can never hit it as an entity: it simply sticks in
 * the wall. This routing gives that impact its meaning - an arrow that ends anywhere inside a cell instead of on a
 * real creature is a hit on the wall of that cell, whoever shot it.
 *
 * <p>The impact itself stays completely vanilla: the arrow really stops in the wall, the block protection of the
 * stomach is untouched, and a creature that was hit keeps its ordinary arrow damage. Only the hit on the wall is
 * handed to the core of the cell, with the vanilla arrow damage and the real shooter as the attacker.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachProjectileRouting
{
    private StomachProjectileRouting()
    {
    }

    /** A projectile that ends its flight inside a cell hits the wall of that cell. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event)
    {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level))
        {
            return;
        }

        if (!level.dimension().equals(StomachCellManager.DIMENSION))
        {
            return;
        }

        // A creature that was hit keeps the ordinary arrow hit, and a piercing arrow keeps on piercing.
        if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY)
        {
            return;
        }

        Optional<StomachCell> resolved = StomachCellManager.resolveCellAt(level, arrow.position());

        if (resolved.isEmpty())
        {
            return;
        }

        StomachCoreEntity core = StomachCellManager.coreOf(level, resolved.get()).orElse(null);

        if (core == null || !core.isAlive())
        {
            return;
        }

        // The impact itself is left untouched, so the arrow really ends in the wall and can never hit a second
        // time: an arrow that is stuck in the wall has no impact left to give.
        core.hurt(damageSource(level, arrow), arrowDamage(arrow));
    }

    /** @return the damage source of that arrow, keeping its real shooter so the wall knows who shot it. */
    private static DamageSource damageSource(ServerLevel level, AbstractArrow arrow)
    {
        Entity shooter = arrow.getOwner();

        return level.damageSources().arrow(arrow, shooter == null ? arrow : shooter);
    }

    /**
     * The damage of an arrow, exactly the way the vanilla arrow computes it when it hits a creature: the speed of
     * the flight times the base damage of the arrow (which already carries the power of the bow that shot it),
     * rounded up, plus the critical bonus of the shot.
     *
     * <p>An arrow that barely moves therefore does (almost) nothing on the wall, exactly like it does nothing to a
     * creature, and a skeleton arrow hits the wall as hard as it would hit a player.
     */
    private static float arrowDamage(AbstractArrow arrow)
    {
        float speed = (float) arrow.getDeltaMovement().length();
        int damage = Mth.ceil(Mth.clamp((double) speed * arrow.getBaseDamage(), 0.0D, (double) Integer.MAX_VALUE));

        if (arrow.isCritArrow())
        {
            long bonus = (long) arrow.level().getRandom().nextInt(damage / 2 + 2);
            damage = (int) Math.min(bonus + (long) damage, (long) Integer.MAX_VALUE);
        }

        return (float) damage;
    }
}
