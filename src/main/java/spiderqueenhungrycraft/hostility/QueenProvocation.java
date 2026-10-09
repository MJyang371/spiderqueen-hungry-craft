package spiderqueenhungrycraft.hostility;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.faction.QueenAllyUnits;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.web.WebVolleyTracker;
import spiderqueenhungrycraft.world.QueenRetaliationTracker;

/**
 * One place that turns "the Spider Queen attacked something" into AI hostility (Phase 6-SIMPLE-3).
 *
 * <p>Melee, arrows and web shots all come through here, so there is exactly one pack alert per attack. The
 * alert reuses the existing retaliation system of the mod: the victim and every mob of the very same
 * {@link net.minecraft.world.entity.EntityType} nearby are allowed to fight that queen for the same, already
 * existing duration. Nothing is stored permanently, so the normal queen neutrality comes back by itself.
 *
 * <p><b>Her own are never provoked (web shot fix round).</b> A creature that belongs to that queen - a spider of her
 * colony, her own tame - is refused here before anything is written. This is the single gate that writes a target by
 * hand, and it is the gate {@link spiderqueenhungrycraft.faction.QueenOwnerProtection} promises every minion AI keeps:
 * a spider can never be made to attack the queen it belongs to. Without it a web shot, a sword swing or a beast bite
 * aimed at one of her own spiders set that spider's target straight to its own queen, and the spider bit her half a
 * second later.
 */
public final class QueenProvocation
{
    /** How far the kin of the victim is alerted. Searched once per attack, never per tick. */
    public static final double PACK_AGGRO_RADIUS = 16.0D;

    /** What provoked the attack, informational for now. */
    public enum Cause
    {
        MELEE_OR_DAMAGE,
        PROJECTILE,
        WEB_SHOT
    }

    private QueenProvocation()
    {
    }

    /** Marks the victim and its kin as retaliating against that queen. Harmless for non mob victims. */
    public static void provoke(ServerPlayer queen, LivingEntity victim, Cause cause)
    {
        provoke(queen, victim, cause, WebVolleyTracker.NO_VOLLEY);
    }

    /**
     * The same as {@link #provoke(ServerPlayer, LivingEntity, Cause)}, for one projectile of a volley.
     *
     * <p>The pack scan is the only thing that is deduplicated inside a volley: five projectiles landing on the
     * same prey must not search the very same neighbourhood five times. Damage, Webbed, the capture roll, the
     * poison and the fire of those projectiles are never filtered this way, they all stay independent.
     */
    public static void provoke(ServerPlayer queen, LivingEntity victim, Cause cause, int volleyId)
    {
        if (victim.level().isClientSide() || !QueenDataCapability.isQueen(queen))
        {
            return;
        }

        // Her own are never provoked, whatever hit them. The web shot fix round made this the second half of the same
        // rule the shot itself obeys: a creature of the shooter's own is neither hit nor blamed.
        if (QueenAllyUnits.isAllyOf(queen, victim))
        {
            return;
        }

        // The single deduplication of a beast volley, and it only ever skips the search below.
        if (!WebVolleyTracker.markProvocation(volleyId, victim))
        {
            return;
        }

        if (!(victim instanceof Mob mob) || mob.isRemoved())
        {
            return;
        }

        alert(mob, queen);

        // Pack alert: the same EntityType, nothing else. No class names, no categories, no tags.
        AABB area = mob.getBoundingBox().inflate(PACK_AGGRO_RADIUS);

        for (Mob other : mob.level().getEntitiesOfClass(Mob.class, area,
                candidate -> candidate != mob && candidate.getType() == mob.getType()))
        {
            alert(other, queen);
        }
    }

    /**
     * Reuses the project retaliation state, then lets the mob target the queen. The retaliation is written
     * first on purpose: the neutrality guard of the project only allows a queen as target while that state is
     * set, so this is the single supported way to make a neutral mob fight.
     */
    private static void alert(Mob mob, ServerPlayer queen)
    {
        if (mob.isRemoved() || !mob.isAlive())
        {
            return;
        }

        QueenRetaliationTracker.startRetaliation(mob, queen);
        mob.setLastHurtByMob(queen);
        mob.setTarget(queen);
    }
}
