package spiderqueenhungrycraft.beast;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The single authoritative Queen Beast form state of one player (Phase 8A).
 *
 * <p>There is exactly one instance per player and logical side: it lives inside the queen capability of that
 * entity. The renderer, the size hook, the dimension handling and the server gameplay therefore all read the
 * very same flag instead of keeping a second copy of it. The S2C state packet only updates this object on the
 * client, so a beast is a beast on every side and for every player who tracks it.
 *
 * <p>Only the server ever writes {@link #transformed}: the transformation rules, the hunger metabolism and the
 * transformation clearance all run there, and the result is synced afterwards. Only {@link #transformed} and
 * {@link #compressed} are read outside of the server logic, and only the transformed flag is persisted. The
 * tap counter and the regeneration accumulator are server side scratch data: they are never saved, never
 * synced and never read by a client.
 */
public final class QueenBeastState
{
    private static final String TAG_TRANSFORMED = "BeastTransformed";

    /** True while this player is in the Queen Beast form. Persisted, server authoritative. */
    private boolean transformed;

    /** Derived from the room around the beast. Never persisted, re-derived by the login reconcile. */
    private boolean compressed;

    /** Server side tap counter of the transform key, runtime only. */
    private int transformTapCount;

    /** Server side game time until which another tap continues the same triple tap attempt. */
    private long transformTapDeadline;

    /** Server side sum of the health points the beast regeneration really healed. */
    private float regenHealAccumulator;

    /** Server side game time of the last accepted beast bite, or -1 when the current form never bit. */
    private long lastBiteGameTime = -1L;

    /** Server side game time the jump key was pressed, or -1 while no jump is being charged. */
    private long jumpChargeStartGameTime = -1L;

    /** Server side: the last charged jump was strong enough to arm a stomp on the next landing. */
    private boolean stompArmed;

    /** Server side power of that armed stomp, kept for the log and for future scaling. */
    private float stompPower;

    /** Server side: the continuous charge is running. */
    private boolean chargeActive;

    /** Server side: a hard impact ended the charge, so another press is needed before the next one. */
    private boolean chargeBlocked;

    /** Server side: active charge ticks since the last food payment. */
    private int chargeActiveTicks;

    /** Server side: ticks since a hard impact locked the charge, used by the lost release safety net. */
    private int chargeBlockedTicks;

    /** Server side: the horizontal direction the charge keeps, a unit vector. */
    private Vec3 chargeDirection = Vec3.ZERO;

    /** Server side: the entities the current charge already hit, so nobody is hit twice by one charge. */
    private final Set<UUID> chargeHits = new HashSet<>();

    /** Server side: the beast body of the previous tick, for the swept volumes of the crush and the charge. */
    /** Server side: whether the devour key is currently held down. Never persisted, never synced. */
    private boolean devourPressed;

    /** Server side game time the devour key was pressed, or -1 while no press is open. */
    private long devourPressGameTime = -1L;

    /** Server side: the action of the current press was already spent, so the release has nothing left to do. */
    private boolean devourHoldConsumed;

    private AABB lastBody;

    /** Server side: whether the beast stood on the ground in the previous tick, used by the stomp landing. */
    private boolean lastOnGround;

    public boolean isTransformed()
    {
        return this.transformed;
    }

    public void setTransformed(boolean value)
    {
        this.transformed = value;
    }

    /**
     * @return true while the beast form is active but the full beast body does not fit into the room around
     *         the player. The form itself stays active, only the physical size stays small.
     */
    public boolean isCompressed()
    {
        return this.compressed;
    }

    public void setCompressed(boolean value)
    {
        this.compressed = value;
    }

    public int getTransformTapCount()
    {
        return this.transformTapCount;
    }

    public void setTransformTapCount(int count)
    {
        this.transformTapCount = count;
    }

    public long getTransformTapDeadline()
    {
        return this.transformTapDeadline;
    }

    public void setTransformTapDeadline(long tick)
    {
        this.transformTapDeadline = tick;
    }

    public void resetTransformTaps()
    {
        this.transformTapCount = 0;
        this.transformTapDeadline = 0L;
    }

    public float getRegenHealAccumulator()
    {
        return this.regenHealAccumulator;
    }

    public void setRegenHealAccumulator(float value)
    {
        this.regenHealAccumulator = value;
    }

    /** Game time of the last accepted beast bite, or -1 when this form never bit. */
    public long getLastBiteGameTime()
    {
        return this.lastBiteGameTime;
    }

    public void setLastBiteGameTime(long gameTime)
    {
        this.lastBiteGameTime = gameTime;
    }

    /** @return true while the devour key of this player is held down. */
    public boolean isDevourPressed()
    {
        return this.devourPressed;
    }

    public void setDevourPressed(boolean pressed)
    {
        this.devourPressed = pressed;
    }

    /** Game time of the current press of the devour key, or -1 while no press is open. */
    public long getDevourPressGameTime()
    {
        return this.devourPressGameTime;
    }

    public void setDevourPressGameTime(long gameTime)
    {
        this.devourPressGameTime = gameTime;
    }

    /** True when the current press already did its one thing, so its release must stay silent. */
    public boolean isDevourHoldConsumed()
    {
        return this.devourHoldConsumed;
    }

    public void setDevourHoldConsumed(boolean consumed)
    {
        this.devourHoldConsumed = consumed;
    }

    /** Clears every piece of runtime scratch data, used whenever the form changes or the player logs in. */
    public void resetTransientData()
    {
        resetTransformTaps();
        this.regenHealAccumulator = 0.0F;
        this.lastBiteGameTime = -1L;
        clearMotion();
    }

    /** Drops the jump, stomp and charge state of the form, used on every exit and on every login. */
    public void clearMotion()
    {
        this.jumpChargeStartGameTime = -1L;
        this.stompArmed = false;
        this.stompPower = 0.0F;
        this.chargeActive = false;
        this.chargeBlocked = false;
        this.chargeActiveTicks = 0;
        this.chargeBlockedTicks = 0;
        this.chargeDirection = Vec3.ZERO;
        this.chargeHits.clear();
        this.lastBody = null;
        this.lastOnGround = false;
        this.devourPressed = false;
        this.devourPressGameTime = -1L;
        this.devourHoldConsumed = false;
    }

    /** True while the beast stood on the ground in the previous tick. */
    public boolean isLastOnGround()
    {
        return this.lastOnGround;
    }

    public void setLastOnGround(boolean onGround)
    {
        this.lastOnGround = onGround;
    }

    /** Server side game time the jump key was pressed, or -1 while no jump is being charged. */
    public long getJumpChargeStartGameTime()
    {
        return this.jumpChargeStartGameTime;
    }

    public void setJumpChargeStartGameTime(long gameTime)
    {
        this.jumpChargeStartGameTime = gameTime;
    }

    public boolean isStompArmed()
    {
        return this.stompArmed;
    }

    public void setStompArmed(boolean armed)
    {
        this.stompArmed = armed;
    }

    public float getStompPower()
    {
        return this.stompPower;
    }

    public void setStompPower(float power)
    {
        this.stompPower = power;
    }

    public boolean isChargeActive()
    {
        return this.chargeActive;
    }

    public void setChargeActive(boolean active)
    {
        this.chargeActive = active;
    }

    public boolean isChargeBlocked()
    {
        return this.chargeBlocked;
    }

    /** Blocks the charge until the player releases the key, so a hard impact can never repeat itself. */
    public void setChargeBlocked(boolean blocked)
    {
        this.chargeBlocked = blocked;
    }

    public int getChargeActiveTicks()
    {
        return this.chargeActiveTicks;
    }

    public void setChargeActiveTicks(int ticks)
    {
        this.chargeActiveTicks = ticks;
    }

    /** Ticks since a hard impact locked the charge of this beast, never shared with any other player. */
    public int getChargeBlockedTicks()
    {
        return this.chargeBlockedTicks;
    }

    public void setChargeBlockedTicks(int ticks)
    {
        this.chargeBlockedTicks = ticks;
    }

    public Vec3 getChargeDirection()
    {
        return this.chargeDirection;
    }

    public void setChargeDirection(Vec3 direction)
    {
        this.chargeDirection = direction;
    }

    /** The entities the current charge already hit. */
    public Set<UUID> getChargeHits()
    {
        return this.chargeHits;
    }

    /** The beast body of the previous tick, or null when there is none yet. */
    public AABB getLastBody()
    {
        return this.lastBody;
    }

    public void setLastBody(AABB box)
    {
        this.lastBody = box;
    }

    /** Copies the real form state of another instance, used when a dimension change rebuilds the entity. */
    public void copyFrom(QueenBeastState other)
    {
        this.transformed = other.transformed;
        this.compressed = other.compressed;
    }

    /** Only the form itself is worth saving, everything else is derived or scratch data. */
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG_TRANSFORMED, this.transformed);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag)
    {
        this.transformed = tag.getBoolean(TAG_TRANSFORMED);
        this.compressed = false;
        resetTransientData();
    }

    /** @return the beast state of that entity, empty for everything that is not a player. */
    public static Optional<QueenBeastState> of(Entity entity)
    {
        if (!(entity instanceof Player player))
        {
            return Optional.empty();
        }

        return QueenDataCapability.get(player).map(QueenData::beast);
    }

    /** @return true while that player is in the beast form. Answered from the state of the own logical side. */
    public static boolean isTransformed(Entity entity)
    {
        return of(entity).map(QueenBeastState::isTransformed).orElse(false);
    }

    /** @return true while that player is a beast that does not fit into its room yet. */
    public static boolean isCompressed(Entity entity)
    {
        return of(entity).map(QueenBeastState::isCompressed).orElse(false);
    }
}
