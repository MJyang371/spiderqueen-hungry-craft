package spiderqueenhungrycraft.soulfire;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.SpiderOrderMode;

/**
 * 灵火龙 (Soulfire Dragon) - the unified unit every revived bone dragon of HungryCraft becomes.
 *
 * <p><b>It is a creature of this mod and of nothing else.</b> The hierarchy is pure vanilla
 * ({@code TamableAnimal}), there is not a single Ice and Fire import in this file, and no Ice and Fire AI, flight
 * manager, target selector, prey grab or riding rule is anywhere near it: Ice and Fire only supplies the corpse this
 * unit was raised from, and - on the client, behind its own optional gate - the model, the poses and the bone texture.
 *
 * <p><b>One kind, whatever the corpse was.</b> A fire, an ice and a lightning bone dragon all rise as this very same
 * entity. What the corpse was survives only as {@link Origin} (the size, the stage and the bookkeeping of the original
 * remains); it never changes the model, the breath, the resistances or a single rule of behaviour.
 *
 * <p><b>It is commanded exactly like a spider of the brood.</b> The sceptre picks it with the same selection set, and
 * the commands are the same ones: a scout move, a forced attack, the four standing orders (follow, hold, patrol, free
 * attack) and the ability key. Two rules run through all of them:
 * <ul>
 * <li><b>the newest command wins</b> - every accepted command bumps {@link #getCommandVersion()} and drops whatever the
 * previous one was doing, so a unit never finishes an old errand first and never keeps fighting after being told to
 * march,</li>
 * <li><b>the standing order is the fallback, never the master</b> - a scout move, a forced victim and the breath skill
 * of the ability key are temporary commands that run <i>on top of</i> the order, and the order is what the unit returns
 * to when they are over, exactly like a spider of the brood.</li>
 * </ul>
 *
 * <p><b>What it never does.</b> It does not look for an enemy on its own (only the free attack order makes it hunt),
 * it never grabs or shakes anything, and it can not be ridden: a right click on it does nothing at all.
 */
public class SoulfireDragonEntity extends TamableAnimal implements spiderqueenhungrycraft.faction.QueenOwnedEntity
{
    /**
     * What the corpse was. It decides the size and the stage of the remains, and nothing else: the unit itself is one
     * kind of creature, and the original kind is only remembered so that the corpse can be handed back unchanged when
     * the contract ends.
     */
    public enum Origin
    {
        FIRE, ICE, LIGHTNING;

        /** @return the kind with that name, {@link #FIRE} when it names nothing this mod knows. */
        public static Origin byName(@Nullable String name)
        {
            if (name != null)
            {
                for (Origin origin : values())
                {
                    if (origin.name().equalsIgnoreCase(name))
                    {
                        return origin;
                    }
                }
            }

            return FIRE;
        }
    }
    // ------- state -------

    private static final EntityDataAccessor<Integer> DATA_ORIGIN = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HOVERING = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SITTING = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ORDER = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_BREATHING = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.BOOLEAN);

    /** How fast this unit flies, in blocks per tick. */
    public static final double FLY_SPEED = 0.55D;

    /** How fast it walks when it is not flying. */
    public static final double WALK_SPEED = 0.28D;

    /** The distance above which a destination is flown to instead of walked to: the fifteen blocks of the round. */
    public static final double FLY_DISTANCE = 15.0D;

    /** The steepest climb or dive, in blocks per tick, so that no flight can oscillate. */
    public static final double MAX_CLIMB = 0.35D;

    /** How close counts as arrived, in blocks. */
    public static final double ARRIVE_DISTANCE = 3.0D;

    /** How far the jaws really reach, in blocks: a victim further away than this is breathed at instead. */
    public static final double MELEE_REACH = 5.0D;

    /** How far the breath goes, in blocks. */
    public static final double BREATH_RANGE = 24.0D;

    /** How far above a target the unit hovers when the column over it is open: the fifteen blocks of the round. */
    public static final double HOVER_HEIGHT = 15.0D;

    /** How many blocks of open air above a point are asked for before it is hovered over: the twenty five of the round. */
    public static final int SKY_HEIGHT = 25;

    /** The player who holds the contract of this unit, or null when nobody does. */
    @Nullable
    private UUID ownerUuid;

    /** The game time at which the contract runs out, or zero while there is none. */
    private long expiresAt;

    /** The exact remains this unit was raised from, kept so that they can be given back unchanged. */
    @Nullable
    private CompoundTag originalBone;

    /** The uuid of those remains, which is the identity they come back with. */
    @Nullable
    private UUID originalBoneUuid;

    /** Bumped on every accepted command, so a path or a waypoint of an older command is discarded by itself. */
    private int commandVersion;

    /** Where the unit was told to go, in world coordinates. */
    @Nullable
    private Vec3 moveTarget;

    /** The id of the scout move this errand belongs to, so the oldest errand can never win. */
    private int moveCommandId;

    /** The creature this unit was told to attack, as an id, so it survives a save and a reload. */
    @Nullable
    private UUID forcedTargetUuid;

    /** Where the ability key of the sceptre told this unit to breathe, or null when it was not told to. */
    @Nullable
    private Vec3 breathTarget;

    /** How many ticks the current breath has run, which is what its sound and its damage tick are measured in. */
    private int breathTicks;

    /** The point a patrol or a hunt walks its rounds around, or null when the unit has no post. */
    @Nullable
    private BlockPos anchor;

    /** A temporary waypoint of an obstacle detour, and null while the line to the target is clear. */
    @Nullable
    private Vec3 detourWaypoint;

    /** How many ticks the current detour has been flown. */
    private int detourTicks;

    /** Where the flight executor is steering, and null when the unit is not flying anywhere. */
    @Nullable
    private Vec3 flightTarget;

    /** The version of the command the current flight belongs to, so an older flight can never steer. */
    private int flightVersion = -1;
    // ------- end state -------

    public SoulfireDragonEntity(EntityType<? extends SoulfireDragonEntity> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
    }

    /** The attributes of the unit: the size and the strength of a stage four skeleton dragon. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.FLYING_SPEED, FLY_SPEED)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ORIGIN, Origin.FIRE.ordinal());
        this.entityData.define(DATA_STAGE, 4);
        this.entityData.define(DATA_FLYING, false);
        this.entityData.define(DATA_HOVERING, false);
        this.entityData.define(DATA_SITTING, false);
        this.entityData.define(DATA_ORDER, SpiderOrderMode.FOLLOW.ordinal());
        this.entityData.define(DATA_BREATHING, false);
    }

    /**
     * The goals of the unit, and there are deliberately only three of them.
     *
     * <p>{@link FloatGoal} so that it can not drown, the command goal - which is the whole behaviour: orders, errand,
     * forced victim and breath - and the free attack goal, which is the only thing here that may look for an enemy, and
     * only while that order is standing. There is no wander goal and no rival for the movement flag: <b>a unit that
     * nobody ordered stands where it is.</b>
     */
    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new spiderqueenhungrycraft.soulfire.goal.SoulfireCommandGoal(this));
        this.targetSelector.addGoal(3, new spiderqueenhungrycraft.soulfire.goal.SoulfireFreeAttackGoal(this));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Who it is, and what it was
    // ------------------------------------------------------------------------------------------------------------

    public Origin getOrigin()
    {
        return Origin.values()[this.entityData.get(DATA_ORIGIN)];
    }

    public void setOrigin(Origin origin)
    {
        this.entityData.set(DATA_ORIGIN, origin.ordinal());
    }

    /** @return the growth stage of the dragon the corpse was, one to five. */
    public int getStage()
    {
        return this.entityData.get(DATA_STAGE);
    }

    public void setStage(int stage)
    {
        this.entityData.set(DATA_STAGE, Math.max(1, Math.min(5, stage)));
    }

    @Nullable
    public UUID getOwnerUuid()
    {
        return this.ownerUuid;
    }

    /** @return true when that player is the one who raised and commands this unit. */
    public boolean isOwnedBy(@Nullable Entity who)
    {
        return who instanceof Player player && this.ownerUuid != null && this.ownerUuid.equals(player.getUUID());
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>This override is not decoration, it is the fix of the worst bug of round 7C.</b> {@link TamableAnimal}
     * already declares {@code isOwnedBy(LivingEntity)}, and that signature is <i>more specific</i> than the
     * {@code isOwnedBy(Entity)} above. Java therefore resolved every call that was handed a player - which is every
     * call the sceptre makes - to the <b>vanilla</b> method, and the vanilla method answers with {@code getOwner()},
     * which is the vanilla tamed-owner field that this unit never writes, because its contract is its own field. The
     * answer was therefore <b>false for every soul fire dragon of every queen</b>: the unit spawned, held its ten
     * minutes, obeyed the goals it was given directly, and could not be selected or commanded by the sceptre at all.
     *
     * <p>It was found by a game test that asked the entity the very question the sceptre asks
     * ({@code dragon.isOwnedBy(queen)}), which is exactly what a test is for: the failure was invisible in the code and
     * invisible in a running game, because a dragon that ignores an order looks like a dragon with nothing to do.
     */
    @Override
    public boolean isOwnedBy(LivingEntity who)
    {
        return this.isOwnedBy((Entity) who);
    }

    /**
     * {@inheritDoc}
     *
     * <p>It is how the rest of the brood knows whose creature this is: a soul fire dragon stands on the side of its
     * queen, exactly like a spider, and the colony layer can ask it the same question it asks every minion.
     */
    @Override
    @Nullable
    public UUID getQueenOwnerUUID()
    {
        return this.ownerUuid;
    }

    /** @return how many ticks the current breath has run. */
    public int breathTicks()
    {
        return this.breathTicks;
    }

    public void advanceBreathTicks()
    {
        this.breathTicks++;
    }

    public void resetBreathTicks()
    {
        this.breathTicks = 0;
    }

    /** Takes this unit into the service of that player, until that game time. */
    public void beginContract(UUID owner, long expiresAtGameTime)
    {
        this.ownerUuid = owner;
        this.expiresAt = expiresAtGameTime;

        // The vanilla tamed-owner field is written as well, so that this unit answers the same name to the whole game
        // and not only to this mod: a creature whose {@code getOwner()} is null while its own contract names a queen is
        // a contradiction that another mod's "is this pet owned by" check would read the wrong way. Note that this is
        // <b>not</b> taming: {@code isTame()} stays false, so no vanilla pet behaviour (sitting, teleporting, the
        // wandering of a tame animal) is switched on by it.
        this.setOwnerUUID(owner);
    }

    /** @return the game time at which this contract runs out, or zero while there is none. */
    public long getExpiresAt()
    {
        return this.expiresAt;
    }

    /** @return true while this unit is under a contract at all. */
    public boolean hasContract()
    {
        return this.ownerUuid != null && this.expiresAt > 0L;
    }

    /** Remembers the remains this unit was raised from, which are what is handed back when the contract ends. */
    public void setOriginalBone(@Nullable CompoundTag remains, @Nullable UUID identity)
    {
        this.originalBone = remains;
        this.originalBoneUuid = identity;
    }

    /** @return the saved state of the original remains, or null when this unit was not raised from any. */
    @Nullable
    public CompoundTag getOriginalBone()
    {
        return this.originalBone;
    }

    /** @return the identity the original remains come back with, or null. */
    @Nullable
    public UUID getOriginalBoneUuid()
    {
        return this.originalBoneUuid;
    }

    public boolean isFlying()
    {
        return this.entityData.get(DATA_FLYING);
    }

    public void setFlying(boolean flying)
    {
        this.entityData.set(DATA_FLYING, flying);
        this.setNoGravity(flying || this.isHovering());
    }

    public boolean isHovering()
    {
        return this.entityData.get(DATA_HOVERING);
    }

    public void setHovering(boolean hovering)
    {
        this.entityData.set(DATA_HOVERING, hovering);
        this.setNoGravity(hovering || this.isFlying());
    }

    public boolean isSitting()
    {
        return this.entityData.get(DATA_SITTING);
    }

    public void setSitting(boolean sitting)
    {
        this.entityData.set(DATA_SITTING, sitting);
    }

    /** @return true while the soul fire breath runs, which the client uses for the muzzle pose. */
    public boolean isBreathing()
    {
        return this.entityData.get(DATA_BREATHING);
    }

    public void setBreathing(boolean breathing)
    {
        this.entityData.set(DATA_BREATHING, breathing);
    }

    /** @return the standing order of this unit: the mode a spider of the brood has as well. */
    public SpiderOrderMode getOrderMode()
    {
        return SpiderOrderMode.values()[this.entityData.get(DATA_ORDER)];
    }

    /** Writes the standing order. It is the fallback of the unit, never a temporary command. */
    public void setOrderMode(SpiderOrderMode mode)
    {
        this.entityData.set(DATA_ORDER, mode.ordinal());
        this.setSitting(false);
    }

    @Nullable
    public BlockPos getAnchor()
    {
        return this.anchor;
    }

    /** Gives this unit a post: where it guards, and what its rounds go around. */
    public void setAnchor(@Nullable BlockPos post)
    {
        this.anchor = post;
    }

    // ------------------------------------------------------------------------------------------------------------
    // The commands: one at a time, and always the newest one
    // ------------------------------------------------------------------------------------------------------------

    /** @return the version of the last accepted command; a path or a flight of an older one is ignored. */
    public int getCommandVersion()
    {
        return this.commandVersion;
    }

    /**
     * The scout move: walk to that spot when it is close, fly to it when it is more than
     * {@link #FLY_DISTANCE} blocks away, and drop everything else that was being done.
     */
    public void setMoveTo(BlockPos target, int commandId)
    {
        this.acceptCommand();
        this.moveTarget = Vec3.atBottomCenterOf(target);
        this.moveCommandId = commandId;
    }

    @Nullable
    public Vec3 getMoveTarget()
    {
        return this.moveTarget;
    }

    public int getMoveCommandId()
    {
        return this.moveCommandId;
    }

    /**
     * The forced attack: go for that creature and stop whatever else was being done.
     *
     * <p>A friend is refused here, in the same breath as the order is given, so the order is never even written: this
     * unit may not be pointed at its own queen, at a spider of her brood or at another unit of hers.
     */
    public boolean setForcedTarget(@Nullable LivingEntity victim)
    {
        if (victim != null && SoulfireTargeting.isFriendOf(this, victim))
        {
            return false;
        }

        this.acceptCommand();
        this.forcedTargetUuid = victim == null ? null : victim.getUUID();

        if (victim != null)
        {
            this.setTarget(victim);
        }

        return true;
    }

    /** @return the creature this unit was told to attack, when it is still there and still worth attacking. */
    @Nullable
    public LivingEntity getForcedTarget()
    {
        if (this.forcedTargetUuid == null || !(this.level() instanceof ServerLevel server))
        {
            return null;
        }

        Entity victim = server.getEntity(this.forcedTargetUuid);

        return victim instanceof LivingEntity living && living.isAlive() && !living.isRemoved() ? living : null;
    }

    /** @return true when the ability key of the sceptre told this unit to breathe at a place. */
    public boolean hasBreathOrder()
    {
        return this.breathTarget != null;
    }

    @Nullable
    public Vec3 getBreathTarget()
    {
        return this.breathTarget;
    }

    /**
     * The skill: breathe at that place until another command arrives.
     *
     * <p><b>It has no cooldown and no end of its own.</b> The way there and the decision whether to hover over the
     * point or to stand in front of it are recomputed from this one place, and the breath stops only when the queen
     * gives the unit another order - a move, an attack, a post, a hunt - or casts the skill again.
     */
    public void breatheAt(BlockPos target)
    {
        this.acceptCommand();
        this.breathTarget = Vec3.atBottomCenterOf(target);
        this.breathTicks = 0;
        this.setBreathing(true);
    }

    /** Ends the breath skill without touching the standing order. */
    public void stopBreathOrder()
    {
        this.breathTarget = null;
        this.breathTicks = 0;
        this.setBreathing(false);
    }

    /** @return true when this unit is really spitting soul fire right now. */
    public boolean isBreathingFire()
    {
        return this.isBreathing();
    }

    /** The stand-down key: this spot becomes the post of the unit, and every errand is dropped. */
    public void holdAt(BlockPos post)
    {
        this.acceptCommand();
        this.anchor = post;
        this.getNavigation().stop();
        this.setOrderMode(SpiderOrderMode.HOLD);
    }

    /** Leaves the hunt without touching anything else, which is what every command that outranks it does. */
    public void leaveFreeAttack()
    {
        if (this.getOrderMode() == SpiderOrderMode.FREE_ATTACK)
        {
            this.setOrderMode(this.anchor == null ? SpiderOrderMode.FOLLOW : SpiderOrderMode.HOLD);
        }
    }

    /** Drops the temporary commands (errand, forced victim, breath) and keeps the standing order. */
    public void clearTemporaryCommand()
    {
        this.moveTarget = null;
        this.forcedTargetUuid = null;
        this.setTarget(null);
        this.stopBreathOrder();
    }

    /** The order key: follow, hold, patrol, free attack, follow ... */
    public void cycleOrder()
    {
        SpiderOrderMode next = this.getOrderMode().next();

        if (next.needsAnchor() && this.anchor == null)
        {
            this.anchor = this.blockPosition();
        }

        if (!next.needsAnchor())
        {
            this.anchor = null;
        }

        this.setOrderMode(next);
    }

    /** The hard rally: the whole brood came home, so this unit drops everything and walks with its queen. */
    public void rally()
    {
        this.acceptCommand();
        this.anchor = null;
        this.setOrderMode(SpiderOrderMode.FOLLOW);
    }

    /** Drops everything this unit was doing, standing order included, and puts it out of the air. */
    public void interrupt()
    {
        this.clearTemporaryCommand();
        this.getNavigation().stop();
        this.stopFlight();

        if (this.isFlying() || this.isHovering())
        {
            this.setFlying(false);
            this.setHovering(false);
        }
    }

    /**
     * The one gate every command goes through.
     *
     * <p>It is what makes "the newest command wins" true by construction: the version moves on, so every path, flight,
     * waypoint and decision that was computed for the previous command becomes unusable in the same instant - a unit
     * can never be steered by an errand it was already told to forget.
     */
    private void acceptCommand()
    {
        this.commandVersion++;
        this.getNavigation().stop();
        this.setTarget(null);
        this.moveTarget = null;
        this.forcedTargetUuid = null;
        this.breathTarget = null;
        this.breathTicks = 0;
        this.setBreathing(false);
        this.setSitting(false);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Movement: one writer, and it is travel()
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Starts (or keeps) a flight towards that point.
     *
     * <p>It is the <b>only</b> way a flight target is set, and it records the command version with it: the flight
     * executor discards everything the moment the version moves on, which is what keeps a waypoint of a cancelled
     * order from steering the unit a tick later.
     */
    public void flyTo(Vec3 target, int version)
    {
        if (version != this.commandVersion)
        {
            return;
        }

        this.flightTarget = target;
        this.flightVersion = version;

        if (!this.isFlying() && !this.isHovering())
        {
            this.getNavigation().stop();
            this.setFlying(true);
        }
    }

    /** @return true while a flight of the current command is steering this unit. */
    public boolean isFlightActive()
    {
        return this.flightTarget != null && this.flightVersion == this.commandVersion;
    }

    /** Ends the flight, and puts the unit back under gravity when it was in the air. */
    public void stopFlight()
    {
        this.flightTarget = null;
        this.flightVersion = -1;
        this.detourWaypoint = null;
        this.detourTicks = 0;
    }

    /**
     * Brings the unit to a standstill where it is, which is what a breath needs: a dragon that walked while it spat
     * would drag its own stream across the countryside, and the round asks for a muzzle that stays aimed.
     */
    public void holdStill()
    {
        this.stopFlight();
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.2D, this.isFlying() || this.isHovering() ? 0.2D : 1.0D,
                0.2D));
    }

    /**
     * The movement of the unit, and the only place its velocity is written.
     *
     * <p>Two cases and no third one: in the air the flight executor of this class steers, on the ground the ordinary
     * travel of a walking mob runs. <b>There is no rider branch</b>: this unit can not be ridden, so no player input
     * ever reaches this method.
     */
    @Override
    public void travel(Vec3 travelVector)
    {
        if (this.isFlying() || this.isHovering())
        {
            this.travelAirborne();

            return;
        }

        super.travel(travelVector);
    }

    /** The straight line flight of the current command, with a limited climb and one temporary waypoint. */
    private void travelAirborne()
    {
        if (!this.isEffectiveAi() || this.flightTarget == null || this.flightVersion != this.commandVersion)
        {
            // Nothing of the current command steers, so nothing does, and the unit only loses its own speed.
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6D));

            return;
        }

        Vec3 steerAt = this.detourWaypoint != null ? this.detourWaypoint : this.flightTarget;
        Vec3 toTarget = steerAt.subtract(this.position());
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        double vertical = Math.max(-MAX_CLIMB, Math.min(MAX_CLIMB, toTarget.y * 0.25D));
        Vec3 motion = horizontal > 0.05D
                ? new Vec3(toTarget.x / horizontal * FLY_SPEED, vertical, toTarget.z / horizontal * FLY_SPEED)
                : new Vec3(0.0D, vertical, 0.0D);

        this.setDeltaMovement(motion);
        this.move(MoverType.SELF, motion);

        // One temporary waypoint around whatever the line ran into, dropped again the moment the way is clear.
        if (this.detourWaypoint != null)
        {
            this.detourTicks++;

            if (this.detourTicks > 60)
            {
                this.detourWaypoint = null;
                this.detourTicks = 0;
            }
        }
        else if (this.horizontalCollision)
        {
            Vec3 along = new Vec3(-toTarget.z, 0.0D, toTarget.x).normalize().scale(8.0D);
            this.detourWaypoint = this.position().add(along);
            this.detourTicks = 0;
        }
    }

    /** @return the point the soul fire comes out of, which is the muzzle of the model. */
    public Vec3 getMuzzlePosition()
    {
        Vec3 look = this.getLookAngle();

        return this.position().add(look.x * 1.6D, this.getBbHeight() * 0.72D, look.z * 1.6D);
    }

    // ------------------------------------------------------------------------------------------------------------
    // The clock, and the small overrides a unit of this kind needs
    // ------------------------------------------------------------------------------------------------------------

    /**
     * One tick of the unit: the contract clock, and nothing else.
     *
     * <p>The breath is not ticked here: it is ticked by the goal that decided to breathe, so that a breath can never
     * run on its own after the goal that owned it has stopped (for example because the unit was told to march).
     */
    @Override
    public void tick()
    {
        super.tick();

        if (this.level() instanceof ServerLevel server && this.hasContract() && server.getGameTime() >= this.expiresAt)
        {
            // The ten minutes are over: the soul leaves, and the remains it was raised from are laid back down.
            SoulfireRising.expire(this);
        }
    }

    /**
     * A right click on this unit does <b>nothing at all</b> (round: no riding, no grabbing, no feeding).
     *
     * <p>It is deliberately spelled out instead of inherited: {@link TamableAnimal} would otherwise offer taming with
     * food and a sit/stand toggle, and the design of this round is explicit that the only thing a player may do with a
     * soul fire dragon is command it with the sceptre.
     */
    @Override
    public InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand)
    {
        return InteractionResult.PASS;
    }

    /** Nobody rides this unit. There is no seat, no reins and no rider control anywhere in this class. */
    @Override
    protected boolean canAddPassenger(Entity passenger)
    {
        return false;
    }

    /** A skeleton on a payroll does not breed, and this method is the one place that says so. */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner)
    {
        return null;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return !this.isFlying() && !this.isHovering();
    }

    @Override
    @Nullable
    protected net.minecraft.sounds.SoundEvent getAmbientSound()
    {
        return net.minecraft.sounds.SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source)
    {
        return net.minecraft.sounds.SoundEvents.SKELETON_HURT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound()
    {
        return net.minecraft.sounds.SoundEvents.SKELETON_DEATH;
    }

    /** @return how far the breath of this unit really reaches; read by its own breath, and by every test of it. */
    public double breathReach()
    {
        return BREATH_RANGE;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString("Origin", this.getOrigin().name());
        tag.putInt("Stage", this.getStage());
        tag.putInt("Order", this.getOrderMode().ordinal());
        tag.putBoolean("SoulSitting", this.isSitting());
        tag.putInt("CommandVersion", this.commandVersion);

        if (this.ownerUuid != null)
        {
            tag.putUUID("Owner", this.ownerUuid);
        }

        if (this.expiresAt > 0L)
        {
            tag.putLong("ExpiresAt", this.expiresAt);
        }

        if (this.originalBone != null)
        {
            tag.put("OriginalBone", this.originalBone);
        }

        if (this.originalBoneUuid != null)
        {
            tag.putUUID("OriginalBoneId", this.originalBoneUuid);
        }

        if (this.moveTarget != null)
        {
            tag.putDouble("MoveX", this.moveTarget.x);
            tag.putDouble("MoveY", this.moveTarget.y);
            tag.putDouble("MoveZ", this.moveTarget.z);
            tag.putInt("MoveCommand", this.moveCommandId);
        }

        if (this.forcedTargetUuid != null)
        {
            tag.putUUID("ForcedTarget", this.forcedTargetUuid);
        }

        if (this.breathTarget != null)
        {
            tag.putDouble("BreathX", this.breathTarget.x);
            tag.putDouble("BreathY", this.breathTarget.y);
            tag.putDouble("BreathZ", this.breathTarget.z);
        }

        if (this.anchor != null)
        {
            tag.putLong("Anchor", this.anchor.asLong());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setOrigin(Origin.byName(tag.getString("Origin")));
        this.setStage(tag.getInt("Stage") == 0 ? 4 : tag.getInt("Stage"));
        this.entityData.set(DATA_ORDER,
                Math.max(0, Math.min(SpiderOrderMode.values().length - 1, tag.getInt("Order"))));
        this.setSitting(tag.getBoolean("SoulSitting"));
        this.commandVersion = tag.getInt("CommandVersion");
        this.ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.expiresAt = tag.getLong("ExpiresAt");

        // The vanilla owner field is restored with the contract, so a unit that comes back from a save answers the same
        // way it did before (see beginContract).
        this.setOwnerUUID(this.ownerUuid);
        this.originalBone = tag.contains("OriginalBone", net.minecraft.nbt.Tag.TAG_COMPOUND)
                ? tag.getCompound("OriginalBone")
                : null;
        this.originalBoneUuid = tag.hasUUID("OriginalBoneId") ? tag.getUUID("OriginalBoneId") : null;
        this.moveTarget = tag.contains("MoveX")
                ? new Vec3(tag.getDouble("MoveX"), tag.getDouble("MoveY"), tag.getDouble("MoveZ"))
                : null;
        this.moveCommandId = tag.getInt("MoveCommand");
        this.forcedTargetUuid = tag.hasUUID("ForcedTarget") ? tag.getUUID("ForcedTarget") : null;
        this.breathTarget = tag.contains("BreathX")
                ? new Vec3(tag.getDouble("BreathX"), tag.getDouble("BreathY"), tag.getDouble("BreathZ"))
                : null;
        this.anchor = tag.contains("Anchor") ? BlockPos.of(tag.getLong("Anchor")) : null;
    }
}
