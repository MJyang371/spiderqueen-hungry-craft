package spiderqueenhungrycraft.beast;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The absolute super armor of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>While transformed the queen is moved by exactly one thing: her own movement input. Ordinary knockback,
 * the shove of a shield, the punch of an arrow, the body collision of a zombie and even the impulse of a TNT
 * explosion are all refused. Damage is never refused, only the displacement: a creeper still hurts the beast,
 * it just cannot throw it around.
 *
 * <p>This is deliberately not {@code noPhysics}, not a cancelled {@code move()} call and not a velocity reset
 * per tick. The beast keeps its own walk, its gravity, its fall, its portals, its teleports and its future
 * active abilities, because none of those run through the paths this class guards:
 *
 * <ul>
 * <li>the melee, arrow and shield knockback path is already cancelled in the event layer, and the
 * {@code knockback_resistance} attribute of the form keeps it cancelled even if another mod changes it,</li>
 * <li>the body collision push of another entity asks {@link net.minecraft.world.entity.LivingEntity#isPushable}
 * of the entity it wants to shove, which is answered with false for a beast,</li>
 * <li>the impulse of a vanilla explosion runs through the knockback dampener inside
 * {@link net.minecraft.world.level.Explosion}, which is zeroed for a beast.</li>
 * </ul>
 *
 * <p>{@link #forceBeastImpulse} is the one channel that stays open. Nothing in this phase calls it except the
 * documentation and the future active abilities: a charge that crashes into obsidian, a stomp that throws the
 * beast back or a leap always go through an explicit call, so a designed recoil is never confused with an
 * external shove.
 */
public final class QueenBeastPhysics
{
    private QueenBeastPhysics()
    {
    }

    /**
     * @return true when that entity must never be moved by an external force. Answered from the beast state of
     *         the own logical side, so the server and the client agree without extra network traffic.
     */
    public static boolean blocksExternalImpulse(Entity entity)
    {
        return QueenBeastState.isTransformed(entity);
    }

    /**
     * The one door of the super armor: an impulse this mod really wants the beast to receive, used by the
     * active abilities of later phases (charge recoil, stomp, leap).
     *
     * <p>It is applied straight to the movement of the entity and marked as a moved entity, so the server sends
     * the new velocity to every client that can see it. Nothing else in the form is touched, so a beast that is
     * thrown this way still walks exactly as before once the impulse faded.
     *
     * @param beast   the transformed player, or any entity of the form
     * @param impulse the velocity to add, in blocks per tick
     */
    public static void forceBeastImpulse(Entity beast, Vec3 impulse)
    {
        if (beast == null || impulse == null || impulse.lengthSqr() <= 0.0D)
        {
            return;
        }

        beast.setDeltaMovement(beast.getDeltaMovement().add(impulse));
        beast.hurtMarked = true;
    }

    /** Convenience overload of {@link #forceBeastImpulse(Entity, Vec3)} for a raw velocity triple. */
    public static void forceBeastImpulse(Entity beast, double x, double y, double z)
    {
        forceBeastImpulse(beast, new Vec3(x, y, z));
    }
}
