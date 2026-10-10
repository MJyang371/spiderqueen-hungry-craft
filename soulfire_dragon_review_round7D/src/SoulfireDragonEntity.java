package spiderqueenhungrycraft.soulfire;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.AABB;
import net.minecraftforge.entity.PartEntity;
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

    /**
     * The five movement states of a unit (round 7E, stage B3).
     *
     * <p><b>It exists so that the two movement systems of this class can never both write.</b> A unit on the wing is
     * steered by its flight executor; a unit on the floor is steered by the ordinary navigation of the game; and the
     * one thing that must never happen is a ground path being written onto a unit that is in the air, because that path
     * is a command the game will never carry out and a decision that looks like it was made.
     *
     * <p>{@link #LANDING} is the bridge between the two: a unit that is told to walk while it is in the air first comes
     * down (<b>in a line</b>, like every other flight of this round) and is only handed to the navigation once it is
     * really standing on a floor.
     */
    public enum MoveState
    {
        GROUNDED, WALKING, FLYING, LANDING, HOVERING
    }

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

    /**
     * The head rotation <b>relative to the body</b>, in degrees, as the server set it (round 7H).
     *
     * <p>A mob's head yaw and pitch are not part of the vanilla entity sync, so a client only ever approximates them from
     * the body yaw and the movement of the unit - which is why a standing unit that turns to look at its queen showed no
     * head turn at all. These two fields carry the truth to the client, where the neck animation reads them.
     */
    private static final EntityDataAccessor<Float> DATA_HEAD_YAW = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEAD_PITCH = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.FLOAT);

    /** How far the current bite has come, in ticks; -1 while there is no bite at all (round 7H). */
    private static final EntityDataAccessor<Integer> DATA_BITE_TICKS = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.INT);

    /** Real server flight heading, rendered body yaw, and turn animation are one synchronized decision. */
    private static final EntityDataAccessor<Float> DATA_BODY_YAW = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_AIR_BANK = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_LANDING = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_LANDING_BLEND = SynchedEntityData.defineId(
            SoulfireDragonEntity.class, EntityDataSerializers.FLOAT);

    /** Degrees a large skeleton dragon can turn per game tick while flying. */
    private static final float MAX_AIR_TURN_DEGREES = 8.0F;

    /** How long one bite lasts, in ticks. */
    public static final int BITE_TICKS = 18;

    /** The tick of a bite at which the jaws close and the damage falls, counted from its first tick. */
    public static final int BITE_HIT_TICK = 9;

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

    /** True only while a valid, physically reachable landing target is being pursued. */
    private boolean landing;

    /** Prevents a blocked descent from holding LANDING forever. */
    private int landingTicks;
    // ------- end state -------

    // ------------------------------------------------------------------------------------------------------------
    // The multi-part hit regions (round 7H). These are hit regions ONLY: the solid hull of the unit stays the single
    // central box, so a spread of wings can still be flown through a forest, and the parts carry no movement, no AI, no
    // contract and no NBT of their own. Nothing here is ever spawned as an independent animal.
    // ------------------------------------------------------------------------------------------------------------

    private SoulfireDragonPart[] dragonParts;
    private SoulfireDragonPart headPart;
    private SoulfireDragonPart neckPart;
    private SoulfireDragonPart bodyPart;
    private SoulfireDragonPart leftWingPart;
    private SoulfireDragonPart rightWingPart;
    private SoulfireDragonPart tailPart;

    public SoulfireDragonEntity(EntityType<? extends SoulfireDragonEntity> type, Level level)
    {
        super(type, level);
        this.setPersistenceRequired();
        this.initSoulfireParts();
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
        this.entityData.define(DATA_HEAD_YAW, 0.0F);
        this.entityData.define(DATA_HEAD_PITCH, 0.0F);
        this.entityData.define(DATA_BITE_TICKS, -1);
        this.entityData.define(DATA_BODY_YAW, 0.0F);
        this.entityData.define(DATA_AIR_BANK, 0.0F);
        this.entityData.define(DATA_LANDING, false);
        this.entityData.define(DATA_LANDING_BLEND, 0.0F);
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

        // The hit regions grow with the animal (round 7H); the solid hull does not change here on purpose, so the flight,
        // the landing and the eighteen block ring that were signed off in 7F/7G keep working exactly as they did.
        this.resizeSoulfireParts();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);

        if (DATA_STAGE.equals(key) && this.dragonParts != null)
        {
            // The client was told about a new stage: its copy of the hit regions follows.
            this.resizeSoulfireParts();
        }
    }

    /**
     * The scale of the <b>hit regions</b> of this unit, by stage: 1 is a hatchling, 5 is the full animal (round 7H).
     *
     * <p>This is not the scale the model is drawn at - that is {@link #stageScale()} - and the two are told apart on
     * purpose: the picture may be any size, while a hit region has to stay a size a player can actually hit.
     */
    public float getSoulScale()
    {
        return switch (this.getStage())
        {
            case 1 -> 0.20F;
            case 2 -> 0.40F;
            case 3 -> 0.6667F;
            case 4 -> 1.0F;
            default -> 1.3333F;
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // The rig itself (round 7H)
    // ------------------------------------------------------------------------------------------------------------

    /** Builds the six hit regions of the unit. The sizes are the base sizes at stage four; the stage scales them. */
    private void initSoulfireParts()
    {
        this.headPart = new SoulfireDragonPart(this, "head", 1.7F, 1.2F);
        this.neckPart = new SoulfireDragonPart(this, "neck", 1.3F, 1.1F);
        this.bodyPart = new SoulfireDragonPart(this, "body", 2.8F, 2.3F);
        this.leftWingPart = new SoulfireDragonPart(this, "left_wing", 2.8F, 0.75F);
        this.rightWingPart = new SoulfireDragonPart(this, "right_wing", 2.8F, 0.75F);
        this.tailPart = new SoulfireDragonPart(this, "tail", 1.7F, 0.9F);
        this.dragonParts = new SoulfireDragonPart[] {
                this.headPart, this.neckPart, this.bodyPart, this.leftWingPart, this.rightWingPart, this.tailPart
        };
        this.resizeSoulfireParts();
        this.updateSoulfireParts();
        this.setPartIds(this.getId());
    }

    /** Grows or shrinks every hit region with the stage of the unit. */
    private void resizeSoulfireParts()
    {
        if (this.dragonParts == null)
        {
            return;
        }

        float scale = this.getSoulScale();

        for (SoulfireDragonPart part : this.dragonParts)
        {
            part.resize(scale);
        }
    }

    /**
     * The identity of the parts follows the identity of the unit, offset by one each - the very scheme the ender dragon
     * of the game uses, and the reason no part of one dragon can ever be mistaken for a part of another.
     */
    @Override
    public void setId(int id)
    {
        super.setId(id);
        this.setPartIds(id);
    }

    private void setPartIds(int id)
    {
        if (this.dragonParts == null)
        {
            return;
        }

        for (int index = 0; index < this.dragonParts.length; index++)
        {
            this.dragonParts[index].setId(id + index + 1);
        }
    }

    @Override
    public boolean isMultipartEntity()
    {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts()
    {
        return this.dragonParts;
    }

    /**
     * Moves the six hit regions to where the animal is, every tick (round 7H).
     *
     * <p>This is an approximate rig on the server, deliberately: the chest of the unit is the anchor, the neck and the
     * head reach forward along the body, the tail trails behind and the wings sit to the sides. The head alone is aimed by
     * the real head rotation of the unit, so a hit on the head lands where the head is actually turned - the picture the
     * client draws and the region the server tests stay in agreement, and neither of them leans on the render matrices of
     * the other.
     */
    private void updateSoulfireParts()
    {
        if (this.dragonParts == null)
        {
            return;
        }

        float scale = this.getSoulScale();
        double yaw = Math.toRadians(this.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 chest = new Vec3(this.getX(), this.getY() + this.getBbHeight() * 0.55D, this.getZ());

        this.positionPart(this.bodyPart, chest);
        this.positionPart(this.neckPart, chest.add(forward.scale(1.25D * scale)).add(0.0D, 0.26D * scale, 0.0D));

        double headYaw = Math.toRadians(this.getYHeadRot());
        Vec3 headAim = new Vec3(-Math.sin(headYaw), 0.0D, Math.cos(headYaw));
        this.positionPart(this.headPart, chest.add(headAim.scale(2.6D * scale)).add(0.0D, 0.52D * scale, 0.0D));

        this.positionPart(this.tailPart, chest.subtract(forward.scale(2.7D * scale)).add(0.0D, -0.22D * scale, 0.0D));
        this.positionPart(this.leftWingPart, chest.subtract(forward.scale(0.2D * scale))
                .add(right.scale(-2.5D * scale)).add(0.0D, 0.40D * scale, 0.0D));
        this.positionPart(this.rightWingPart, chest.subtract(forward.scale(0.2D * scale))
                .add(right.scale(2.5D * scale)).add(0.0D, 0.40D * scale, 0.0D));
    }

    /** Puts one hit region so that its centre is that point. */
    private void positionPart(SoulfireDragonPart part, Vec3 centre)
    {
        part.setPos(centre.x, centre.y - part.getBbHeight() * 0.5D, centre.z);
    }

    /**
     * Makes sure the parts are really in the world on the server (round 7H), and only there: a part that is not in the
     * level can not be hit by anything, no matter what the renderer draws, so an arrow at a wing has to find a region that
     * the level itself knows about. Checked now and then rather than every tick, and never twice for one part - a duplicate
     * would be a ghost that outlives its dragon.
     */
    private void ensurePartsInWorld()
    {
        if (this.level().isClientSide || this.dragonParts == null)
        {
            return;
        }

        for (SoulfireDragonPart part : this.dragonParts)
        {
            if (!part.isAddedToWorld())
            {
                this.level().addFreshEntity(part);
            }
        }
    }

    /** @return a point a bow can aim at: the centre of the head region, or a spot on the unit when there is none. */
    public Vec3 getHeadHitboxCentre()
    {
        return this.headPart == null ? this.position().add(0.0D, this.getBbHeight() * 0.75D, 0.0D)
                : this.headPart.getBoundingBox().getCenter();
    }

    /**
     * The hit regions leave with their dragon (round 7H): whether the contract ran out, the unit was killed and its
     * remains were laid back down, or the chunk simply unloaded, no part of a soul fire dragon is ever left behind as a
     * ghost that can be shot at - and nothing of the rig is ever written to the world, because a part holds no data.
     */
    @Override
    public void remove(Entity.RemovalReason reason)
    {
        if (!this.level().isClientSide && this.dragonParts != null)
        {
            for (SoulfireDragonPart part : this.dragonParts)
            {
                part.discard();
            }
        }

        super.remove(reason);
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

        // Leaving the floor or coming back to it clears the fall of the unit, which is the other half of round 7E A1:
        // a dragon that dives out of the air must not be carrying the height it left behind as a number that means
        // damage the moment anything (a rider, another mod, an explosion) asks the ordinary fall rule about it.
        this.resetFallDistance();
    }

    public boolean isHovering()
    {
        return this.entityData.get(DATA_HOVERING);
    }

    public void setHovering(boolean hovering)
    {
        this.entityData.set(DATA_HOVERING, hovering);
        this.setNoGravity(hovering || this.isFlying());
        this.resetFallDistance();
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

    // ------------------------------------------------------------------------------------------------------------
    // The head, the neck and the bite (round 7H)
    // ------------------------------------------------------------------------------------------------------------

    /** How many ticks pass between two idle glances at the queen. */
    private static final int GLANCE_PERIOD = 140;

    /** How long one idle glance lasts, in ticks. */
    private static final int GLANCE_LENGTH = 50;

    /** How far away a queen can be and still be looked at while the unit has nothing to do, in blocks. */
    private static final double GLANCE_RANGE = 32.0D;

    /** The bite in progress: the tick it is at, and the victim it was aimed at. */
    private int biteTicks = -1;
    private UUID biteVictim;
    private boolean biteDealt;

    /** The countdown to the next idle glance at the queen, in ticks. */
    private int glanceCountdown = GLANCE_PERIOD;
    private int glanceLeft;

    /** @return true while this unit is in the middle of a bite. */
    public boolean isBiting()
    {
        return this.entityData.get(DATA_BITE_TICKS) >= 0;
    }

    /**
     * @return how far the current bite has come, from 0 at its first tick to 1 when it is over; 0 when there is no bite.
     *         This is what the animation of the jaws is drawn from, and it is synced, so client and server agree.
     */
    public float biteProgress()
    {
        int ticks = this.entityData.get(DATA_BITE_TICKS);

        return ticks < 0 ? 0.0F : Mth.clamp(ticks / (float) BITE_TICKS, 0.0F, 1.0F);
    }

    /** @return the head yaw of this unit relative to its body, in degrees, as the server set it. */
    public float syncedHeadYaw()
    {
        return this.entityData.get(DATA_HEAD_YAW);
    }

    /** @return the pitch of the head of this unit, in degrees, as the server set it. */
    public float syncedHeadPitch()
    {
        return this.entityData.get(DATA_HEAD_PITCH);
    }

    /**
     * Starts a bite at that victim (round 7H): the jaws open, the head shoots forward, and the damage falls on
     * {@link #BITE_HIT_TICK} - <b>once</b>. The animation is what the player sees, so a bite that can not be seen is not
     * dealt at all.
     *
     * @return true when the bite was started (false while another bite is still running)
     */
    public boolean startBite(@Nullable LivingEntity victim)
    {
        if (this.level().isClientSide || this.isBiting() || victim == null || !victim.isAlive()
                || !SoulfireTargeting.isHuntable(this, victim))
        {
            return false;
        }

        this.biteTicks = 0;
        this.biteDealt = false;
        this.biteVictim = victim.getUUID();
        this.entityData.set(DATA_BITE_TICKS, 0);

        return true;
    }

    /** Ends a bite early: a new command of the queen cancels the attack it interrupted (round 7H). */
    public void endBite()
    {
        if (this.biteTicks < 0 && this.entityData.get(DATA_BITE_TICKS) < 0)
        {
            return;
        }

        this.biteTicks = -1;
        this.biteDealt = false;
        this.biteVictim = null;
        this.entityData.set(DATA_BITE_TICKS, -1);
    }

    /** Server flight heading; vanilla/client body rotations can otherwise diverge by 50+ degrees. */
    public float syncedBodyYaw()
    {
        return this.entityData.get(DATA_BODY_YAW);
    }

    /** Signed bank angle of the current turn, client uses it for the I&F wing and body animation only. */
    public float syncedAirBank()
    {
        return this.entityData.get(DATA_AIR_BANK);
    }

    /** Smooth approach of the I&F flight/hover poses to its real ground pose. */
    public float landingAnimationProgress()
    {
        return this.entityData.get(DATA_LANDING_BLEND);
    }

    /** The clock of the bite: counted on the server, mirrored to the client, and the damage falls exactly once. */
    private void tickBite()
    {
        if (this.level().isClientSide || this.biteTicks < 0)
        {
            return;
        }

        this.biteTicks++;
        this.entityData.set(DATA_BITE_TICKS, this.biteTicks);

        if (this.biteTicks == BITE_HIT_TICK)
        {
            this.closeJaws();
        }

        if (this.biteTicks >= BITE_TICKS)
        {
            this.endBite();
        }
    }

    /** The one moment of a bite that hurts: the victim is re-checked here, so a bite at a corpse does nothing. */
    private void closeJaws()
    {
        UUID aimed = this.biteVictim;

        if (this.biteDealt || aimed == null)
        {
            return;
        }

        this.biteDealt = true;

        if (!(this.level() instanceof ServerLevel server))
        {
            return;
        }

        if (server.getEntity(aimed) instanceof LivingEntity victim && victim.isAlive()
                && SoulfireTargeting.isHuntable(this, victim) && this.distanceTo(victim) <= MELEE_REACH + 1.5D)
        {
            this.doHurtTarget(victim);
        }
    }

    /**
     * Carries the server side head rotation to the client (round 7H), where the neck animation reads it. Only a real
     * change is written, so a still head costs nothing.
     */
    private void syncHeadRotation()
    {
        float yaw = Mth.wrapDegrees(this.getYHeadRot() - this.yBodyRot);
        float pitch = Mth.wrapDegrees(this.getXRot());

        if (Math.abs(yaw - this.entityData.get(DATA_HEAD_YAW)) > 0.5F)
        {
            this.entityData.set(DATA_HEAD_YAW, yaw);
        }

        if (Math.abs(pitch - this.entityData.get(DATA_HEAD_PITCH)) > 0.5F)
        {
            this.entityData.set(DATA_HEAD_PITCH, pitch);
        }
    }

    /**
     * Now and then a unit with nothing to do turns its head towards its queen (round 7H) - and only its head: the look
     * control is asked to look at her, nothing here touches the body yaw or the navigation, so a standing unit can not
     * be made to turn on the spot by looking around. Between two glances the look is aimed back at its own muzzle, which
     * lets the head return to the front by itself.
     */
    private void tickIdleGlance()
    {
        if (this.level().isClientSide || this.isDeadOrDying())
        {
            return;
        }

        Player queen = this.getOwnerUuid() == null ? null : this.level().getPlayerByUUID(this.getOwnerUuid());
        boolean busy = this.isFlying() || this.isHovering() || this.isLanding() || this.isBiting() || this.isBreathing()
                || this.getTarget() != null;

        if (this.glanceLeft > 0)
        {
            this.glanceLeft--;

            if (queen != null && !busy && this.distanceTo(queen) <= GLANCE_RANGE)
            {
                this.getLookControl().setLookAt(queen, 30.0F, 30.0F);
            }
            else
            {
                this.glanceLeft = 0;
            }

            return;
        }

        this.getLookControl().setLookAt(this.getX() + this.getLookAngle().x * 4.0D, this.getEyeY(),
                this.getZ() + this.getLookAngle().z * 4.0D);

        if (--this.glanceCountdown <= 0)
        {
            this.glanceCountdown = GLANCE_PERIOD;
            this.glanceLeft = GLANCE_LENGTH;
        }
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

        // A bite that was interrupted by a new order is over before it lands (round 7H): the queen's newest command must
        // never be overtaken by the jaws of the one before it.
        this.endBite();
    }

    /** The order key: follow, hold, patrol, free attack, follow ... */
    public void cycleOrder()
    {
        // <b>The order key is a command like every other one.</b> Without this call the standing order of a unit could
        // change while the flight of an older MOVE command kept steering it - a dragon that answers "follow" and keeps
        // flying to an errand that is over.
        this.acceptCommand();

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

    /** The soft rally of the sceptre: this unit becomes a follower of its queen at once (a command, not a mode). */
    public void followOrder()
    {
        this.acceptCommand();
        this.anchor = null;
        this.setOrderMode(SpiderOrderMode.FOLLOW);
    }

    /** The hunt of the sceptre: this unit hunts around where it stands at once (a command, not a mode). */
    public void huntOrder()
    {
        this.acceptCommand();

        if (this.anchor == null)
        {
            this.anchor = this.blockPosition();
        }

        this.setOrderMode(SpiderOrderMode.FREE_ATTACK);
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

        // <b>The old flight dies with the old command.</b> Stopping the flight here clears its target, its version and
        // its detour waypoint, so no path of an errand that is over can steer the unit for even one more tick.
        this.stopFlight();
        this.setLandingFlag(false);
        this.landingTicks = 0;
        this.setTarget(null);
        this.moveTarget = null;
        this.forcedTargetUuid = null;
        this.breathTarget = null;
        this.breathTicks = 0;
        this.setBreathing(false);
        this.setSitting(false);
        this.endBite();
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

    // ------------------------------------------------------------------------------------------------------------
    // The movement state, and the one bridge between the air and the floor (round 7E, stage B3)
    // ------------------------------------------------------------------------------------------------------------

    /** @return the movement state of the unit, derived from its flags and never written by hand. */
    public MoveState moveState()
    {
        if (this.landing)
        {
            return MoveState.LANDING;
        }

        if (this.isHovering())
        {
            return MoveState.HOVERING;
        }

        if (this.isFlying())
        {
            return MoveState.FLYING;
        }

        return this.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4D ? MoveState.WALKING : MoveState.GROUNDED;
    }

    /** Server authoritative landing, readable by CLIENT animation via SynchedEntityData. */
    public boolean isLanding()
    {
        return this.level().isClientSide ? this.entityData.get(DATA_LANDING) : this.landing;
    }

    private void setLandingFlag(boolean value)
    {
        this.landing = value;
        if (!this.level().isClientSide)
        {
            this.entityData.set(DATA_LANDING, value);
            if (!value)
            {
                this.entityData.set(DATA_LANDING_BLEND, 0.0F);
            }
        }
    }

    /**
     * Starts a controlled descent to that spot, in the same straight line every other flight of this round uses.
     *
     * <p>It is the bridge of {@link MoveState#LANDING}: the unit keeps flying until it is really on a floor, and only
     * then does it become a walking animal again.
     */
    public void landTo(Vec3 spot, int version)
    {
        if (version != this.commandVersion || spot == null)
        {
            return;
        }

        // A previous landing is allowed to finish: it must not be reset on every AI tick.
        if (this.onGround())
        {
            this.finishLanding();
            return;
        }

        if (this.landing && this.isFlightActive())
        {
            return;
        }

        this.setLandingFlag(true);
        this.landingTicks = 0;
        this.setHovering(false);
        this.setFlying(true);
        this.flyTo(spot, version);
    }

    /**
     * The ground navigation of this unit, and the <b>only</b> way a path may be written on it.
     *
     * @return true when the path was written, false when the unit is in the air - in which case it wrote nothing at all
     *         and the caller is expected to bring it down with {@link #landTo(Vec3, int)} first
     */
    public boolean requestWalkTo(Vec3 target)
    {
        if (this.isFlying() || this.isHovering() || this.landing)
        {
            // In the air a ground path would be a command this unit could not carry out, and the game would report a
            // navigation that is "running" while the unit flies somewhere else entirely. So nothing is written.
            return false;
        }

        this.getNavigation().moveTo(target.x, target.y, target.z, 1.0D);

        return true;
    }

    /** Ends a landing that has arrived: the unit is on the floor again and the ordinary movement takes over. */
    private void finishLanding()
    {
        this.setLandingFlag(false);
        this.landingTicks = 0;
        this.stopFlight();
        this.setHovering(false);
        this.setFlying(false);
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.25D, 0.0D, 0.25D));
        this.resetFallDistance();
    }

    /**
     * A soul fire dragon <b>never</b> takes falling damage (round 7E, stage A1).
     *
     * <p>It is a creature that lives in the air: it dives out of a hover, it is knocked out of the sky, it comes down
     * from a hundred blocks because its queen pointed at a spot below it - and none of that is a mistake of the player
     * that the game should punish with hearts. The override is deliberately the <b>only</b> thing that makes it immune:
     * the collision of the unit is untouched, nothing is teleported, gravity still applies to a unit that is not
     * flying, and the ordinary landing of the game still runs - the block under it is still told the unit landed on it
     * ({@code stepOn}), so the sound and the particles of a landing are all still there.
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, net.minecraft.world.damagesource.DamageSource source)
    {
        this.resetFallDistance();

        return false;
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

        // Never stop a few centimetres ABOVE the collision surface. A real landing must
        // finish with a downward collision so onGround() can become true on the server.
        if (this.landing && horizontal < 1.2D && Math.abs(toTarget.y) < 0.8D)
        {
            vertical = Math.min(vertical, -0.14D);
        }

        // Slow down approaching the landing position instead of overshooting it endlessly.
        double horizontalSpeed = this.landing ? Math.min(FLY_SPEED, horizontal * 0.22D) : FLY_SPEED;
        // Round 7I: face BEFORE moving. No more sideways/backwards flying: the heading and the velocity agree.
        // This intentionally makes the dragon fly a short arc only while acquiring a new bearing; once facing it,
        // the existing straight-line executor is unchanged. The clamp also prevents instant 180-degree snapping.
        Vec3 motion;
        if (horizontal > 0.05D)
        {
            float desiredYaw = (float) Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
            float oldYaw = this.getYRot();
            float turn = Mth.clamp(Mth.wrapDegrees(desiredYaw - oldYaw),
                    -MAX_AIR_TURN_DEGREES, MAX_AIR_TURN_DEGREES);
            float heading = Mth.wrapDegrees(oldYaw + turn);
            this.setYRot(heading);
            this.yBodyRot = heading;
            this.entityData.set(DATA_BODY_YAW, heading);
            this.entityData.set(DATA_AIR_BANK, Mth.clamp(turn * 1.75F, -14.0F, 14.0F));

            double radians = Math.toRadians(heading);
            // Turn mostly on the spot when the target is behind; never travel backward during a 180-degree order change.
            float remaining = Math.abs(Mth.wrapDegrees(desiredYaw - heading));
            double turnSpeed = remaining > 60.0F ? 0.0D : remaining > 25.0F ? 0.55D : 1.0D;
            motion = new Vec3(-Math.sin(radians) * horizontalSpeed * turnSpeed, vertical,
                    Math.cos(radians) * horizontalSpeed * turnSpeed);
        }
        else
        {
            this.entityData.set(DATA_AIR_BANK, 0.0F);
            motion = new Vec3(0.0D, vertical, 0.0D);
        }

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

    /**
     * The factor every picture of this unit is drawn with, by stage (round 7G). The renderer scales the Ice and Fire
     * skeleton with it and the muzzle of the breath is measured with it, so the one table lives here, where both the
     * server side and the client side can read it: a second copy of this table somewhere else is a copy that drifts.
     */
    public static float stageScale(int stage)
    {
        return switch (stage)
        {
            case 1 -> 0.9F;
            case 2 -> 1.8F;
            case 3 -> 3.0F;
            case 4 -> 4.5F;
            default -> 6.0F;
        };
    }

    /** @return the scale this very unit is drawn with. */
    public float stageScale()
    {
        return stageScale(this.getStage());
    }

    /** How far in front of the unit its mouth is, in model blocks (measured on a live client, round 7G). */
    private static final double MUZZLE_FORWARD = 1.72D;

    /** How far above its feet the mouth is, in model blocks (measured on a live client, round 7G). */
    private static final double MUZZLE_UP = 0.79D;

    /** @return the point the soul fire comes out of, which is the mouth of the model. */
    public Vec3 getMuzzlePosition()
    {
        // Measured, not guessed (round 7G): a development probe walked the real bone chain of the drawn skeleton
        // (BodyUpper, Neck1, Neck2, Neck3, Head) on a live client and read the snout corner out of its head box. In model
        // space the mouth sits 1.72 blocks in front of the unit and 0.79 blocks above its feet, and the whole model is
        // drawn with the stage factor below, so both numbers carry that factor. The estimate before this was a body
        // centred 3.25/2.55 blocks, which put the stream at the chest of a stage four unit.
        float scale = this.stageScale();
        double yaw = Math.toRadians(this.getYHeadRot());
        double pitch = Math.toRadians(this.getXRot());
        Vec3 facing = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch));
        return this.position().add(facing.scale(MUZZLE_FORWARD * scale))
                .add(0.0D, MUZZLE_UP * scale, 0.0D);
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

        if (this.level().isClientSide)
        {
            // Vanilla LivingEntity movement/look code can rotate the client copy independently of the server.
            // The MODEL and the CLIENT part AABBs must both use the server's one true body bearing.
            if (this.isFlying() || this.isHovering() || this.isLanding())
            {
                this.yBodyRotO = this.yBodyRot;
                this.yBodyRot = Mth.rotLerp(0.60F, this.yBodyRot, this.syncedBodyYaw());
                this.setYRot(this.yBodyRot);
            }
        }
        else
        {
            // Server authoritative body yaw for rendering and multipart geometry.
            if (this.isFlying() || this.isHovering() || this.landing)
            {
                this.yBodyRot = this.getYRot();
                float current = this.entityData.get(DATA_BODY_YAW);
                if (Math.abs(Mth.wrapDegrees(this.yBodyRot - current)) > 0.25F)
                {
                    this.entityData.set(DATA_BODY_YAW, this.yBodyRot);
                }
            }
            else
            {
                this.entityData.set(DATA_AIR_BANK, 0.0F);
            }
            if (this.landing && this.flightTarget != null)
            {
                // Blend to I&F's native ground pose during the last ~7 blocks of descent.
                float downDistance = (float) Math.abs(this.getY() - this.flightTarget.y);
                float progress = Mth.clamp((7.0F - downDistance) / 7.0F, 0.0F, 0.96F);
                if (Math.abs(progress - this.entityData.get(DATA_LANDING_BLEND)) > 0.035F)
                {
                    this.entityData.set(DATA_LANDING_BLEND, progress);
                }
            }
        }

        if (this.level() instanceof ServerLevel server && this.hasContract() && server.getGameTime() >= this.expiresAt)
        {
            // The ten minutes are over: the soul leaves, and the remains it was raised from are laid back down.
            SoulfireRising.expire(this);
        }

        // Round 7H: the bite that is in progress, the head rotation the client needs for its neck animation, and the
        // idle glance at the queen. All three are server side state, and none of them touches the navigation.
        this.tickBite();
        this.syncHeadRotation();
        this.tickIdleGlance();

        // Round 7H: the six hit regions follow the animal every tick, and now and then it is made sure that the server
        // really has them, so a shot at a wing or a tail finds a region and not a piece of the picture.
        this.updateSoulfireParts();

        if (this.tickCount % 20 == 0)
        {
            this.ensurePartsInWorld();
        }

        // The end of a descent: a unit that has arrived on the floor is a walking animal again, and the flight is over.
        if (this.landing && this.onGround())
        {
            this.finishLanding();
        }
        else if (!this.level().isClientSide && this.landing && ++this.landingTicks > 180)
        {
            // A roof, unloaded chunk, or bad target must not freeze the dragon in LANDING.
            // Return to controlled hover. The goal will search a new safe place next tick.
            spiderqueenhungrycraft.SpiderQueenHungryCraft.LOGGER.warn(
                    "[Soulfire7F] landing timed out for dragon {} near {} - retrying safe ground search",
                    this.getUUID(), this.blockPosition());
            this.setLandingFlag(false);
            this.landingTicks = 0;
            this.stopFlight();
            this.setHovering(true);
            this.setFlying(false);
            this.setDeltaMovement(Vec3.ZERO);
        }

        // A state that contradicts itself is repaired here rather than left to hang: gravity is derived from the two
        // flight flags and from nothing else, so a unit that is neither flying nor hovering can never keep floating.
        if (!this.isFlying() && !this.isHovering() && this.isNoGravity())
        {
            this.setNoGravity(false);
            this.setLandingFlag(false);
        }

        if ((this.isFlying() || this.isHovering()) && this.fallDistance > 0.0F)
        {
            this.resetFallDistance();
        }
    }

    /**
     * The death of a unit: the remains it was raised from are laid back down right here (round 7E, stage A3).
     *
     * <p>Without this the queen would lose her bone dragon every time one of her units was killed - the contract would
     * simply be over and the corpse would be nowhere. The restore happens <b>before</b> the death of the base class runs,
     * while the unit still exists, and it is told <b>not</b> to remove the unit (the game is about to do that itself).
     */
    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause)
    {
        if (this.level() instanceof ServerLevel && !this.isRemoved())
        {
            SoulfireRising.onDeath(this);
        }

        super.die(cause);
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

        // The movement state travels with the unit (round 7E, stage B4): which of the two movement systems owns it now,
        // and what gravity was, so that a unit saved in the air comes back exactly as it left.
        tag.putBoolean("Flying", this.isFlying());
        tag.putBoolean("Hovering", this.isHovering());
        tag.putBoolean("Landing", this.landing);
        tag.putBoolean("NoGravity", this.isNoGravity());

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

        // The flight is restored as a <b>state</b>, never as a path: the path belonged to a command that is over, so no
        // stale waypoint is resumed - the goal computes a fresh one from the move order or from the standing order on
        // the next tick. Gravity is then derived from the two flags alone by setFlying and setHovering, which is what
        // keeps a unit that was saved in the air from coming back as a creature that neither flies nor falls.
        this.setFlying(tag.getBoolean("Flying"));
        this.setHovering(tag.getBoolean("Hovering"));
        // A saved LANDING has no flightTarget after a reload. Invalidate that transient
        // task so the server-side Goal reselects a valid landing spot on its next tick.
        this.setLandingFlag(false);
        this.landingTicks = 0;
        this.stopFlight();
        this.resetFallDistance();
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
