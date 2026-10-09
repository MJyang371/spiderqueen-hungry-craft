package spiderqueenhungrycraft.attack;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The one server side answer to "did the shield of that body stop this projectile?" (Sweatlord round).
 *
 * <p>Before this class the mod had two half answers that did not agree with each other and neither of them agreed
 * with the game:
 *
 * <ul>
 * <li>a body of this mod could raise a shield and the vanilla hurt pipeline would block ordinary damage with it, but
 * a web shot deals <b>no damage</b> - it applies the webbed effect by hand - so it walked straight through a raised
 * shield,</li>
 * <li>and asking {@code isBlocking()} alone is not the question either: a shield only covers the <b>front</b>, which
 * is what {@code LivingEntity#isDamageSourceBlocked} works out from the direction the hit comes from.</li>
 * </ul>
 *
 * <p>So there is one rule here, and it is the game's own rule, not a copy: the vanilla question is asked first (it
 * already covers "is he really using a shield", {@code BYPASSES_SHIELD} damage and a piercing arrow), and only when
 * the game can not answer it geometrically - a projectile that has already reached the body has almost no position
 * delta left - the approach direction of the shot is used instead, with the same front/back meaning.
 *
 * <p>Nothing here is a special case for a player, a prepared human or a Sweatlord: every {@link LivingEntity} that
 * holds a shield in its hand and faces the shot is covered, including a modded guard.
 */
public final class ShieldProjectileInteraction
{
    /** Below this squared length the position delta of a projectile says nothing about where it came from. */
    private static final double MIN_DELTA_SQR = 1.0E-4D;

    /** Below this squared length a direction is not a direction at all. */
    private static final double MIN_DIRECTION_SQR = 1.0E-6D;

    private ShieldProjectileInteraction()
    {
    }

    /**
     * @return true when the shield that body really holds up stops that projectile, so the caller must drop the whole
     *         impact: no damage, no webbed, no capture, no web block.
     */
    public static boolean blocksProjectile(LivingEntity victim, Entity projectile, @Nullable Entity owner)
    {
        if (victim == null || projectile == null || victim.level().isClientSide())
        {
            return false;
        }

        if (!holdsUpAShield(victim))
        {
            return false;
        }

        if (victim.isDamageSourceBlocked(damageSourceOf(victim, projectile, owner)))
        {
            return true;
        }

        return approachesFromTheFront(victim, projectile);
    }

    /**
     * @return true when that body is really using a shield right now: it has to be using an item, the item has to be
     *         able to block (a shield does, a bow does not) and the shield has to count as up.
     *
     * <p>For every body of the game that means the vanilla five tick wind up of {@code LivingEntity#isBlocking}, which
     * is the same state the vanilla damage pipeline and the renderer see. The one exception is the Sweatlord: his
     * instant projectile guard may answer a projectile on the tick his shield comes up, and only while that reflex is
     * really answering a shot ({@code reflexGuardReady}). No player, no ordinary human, no chad and no modded guard
     * gets that - it is a property of that one body and not of the rule.
     */
    public static boolean holdsUpAShield(LivingEntity body)
    {
        if (!body.isUsingItem() || !body.getUseItem().canPerformAction(ToolActions.SHIELD_BLOCK))
        {
            return false;
        }

        if (body.isBlocking())
        {
            return true;
        }

        return body instanceof SweatlordSteveEntity sweatlord && sweatlord.reflexGuardReady();
    }

    /**
     * @return true when that projectile is coming in from the half of the world that body is facing.
     *
     * <p>The meaning is exactly the one of {@code LivingEntity#isDamageSourceBlocked}: the vector from the projectile
     * to the body, flattened to the horizontal plane, points against the view vector when the shot comes from the
     * front. A shot from behind gives the opposite sign, so a raised shield never blocks a hit in the back.
     */
    public static boolean approachesFromTheFront(LivingEntity victim, Entity projectile)
    {
        Vec3 view = victim.getViewVector(1.0F);
        Vec3 flatView = new Vec3(view.x, 0.0D, view.z);

        if (flatView.lengthSqr() < MIN_DIRECTION_SQR)
        {
            return false;
        }

        Vec3 delta = victim.position().subtract(projectile.position());
        Vec3 flat = new Vec3(delta.x, 0.0D, delta.z);

        if (flat.lengthSqr() < MIN_DELTA_SQR)
        {
            // The shot is already on top of the body, so its position says nothing any more: a projectile that just hit
            // is flying the way it came, which is the same direction the delta would have had.
            Vec3 motion = projectile.getDeltaMovement();
            flat = new Vec3(motion.x, 0.0D, motion.z);
        }

        if (flat.lengthSqr() < MIN_DIRECTION_SQR)
        {
            return false;
        }

        return flat.normalize().dot(flatView.normalize()) < 0.0D;
    }

    /** @return a damage source of that projectile, which is what the vanilla shield question needs to be asked. */
    public static DamageSource damageSourceOf(LivingEntity victim, Entity projectile, @Nullable Entity owner)
    {
        return victim.damageSources().mobProjectile(projectile, owner instanceof LivingEntity living ? living : null);
    }

    /**
     * One shield-block feedback for every client nearby, which is the entity event vanilla broadcasts when the game
     * itself blocks a hit.
     *
     * <p>It goes through {@link ShieldBlockFeedback}, so a block answered here can never become a second sound on top of
     * the one the game played for the same tick. The impact types that really need this call are the ones the game can
     * not block at all: a web shot, an egg, a snowball - everything with no damage to block never reaches the shield
     * path of {@code LivingEntity#hurt}.
     */
    public static void playBlockedFeedback(ServerLevel level, LivingEntity body)
    {
        ShieldBlockFeedback.feedbackOnce(body);
    }
}
