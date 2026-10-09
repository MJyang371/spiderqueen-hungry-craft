package spiderqueenhungrycraft.cocoon;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;
import spiderqueenhungrycraft.brood.SpiderRewards;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * One generic cocoon for every captured creature. It is a plain {@link Entity} on purpose: it must not
 * run AI, pathfind, fight, drink potions or be handled as a normal mob by other mods.
 *
 * <p>The full prey data lives on the server side and in the save file. Only the small visual subset,
 * the size and the nutrition, is synced to clients.
 *
 * <p>On top of the prey, a cocoon can carry one spider egg (this round's breeding pass). While an egg
 * incubates inside, the cocoon is drawn slightly green, it counts down ten seconds and it can no longer be
 * eaten - and when the time is up, the cocoon is gone and one {@link BroodSpiderEntity brood spider} stands
 * in its place. The incubation travels with the cocoon: packed into an item and placed again, it continues
 * where it stopped.
 *
 * <p>There are three ways to open a cocoon, and they are deliberately different things. The queen may eat it,
 * which turns the prey into hunger and experience forever. Three hits pack it into an item, which carries the
 * whole prey data along. And since feature COCOON-RELEASE any player may cut it open with a pair of shears:
 * the silk is destroyed, the prey is let out exactly as it was wrapped up and the shears pay for the cut.
 */
public class CocoonEntity extends Entity
{
    /**
     * A cocoon has to look like the whole creature wrapped in silk, so it is deliberately bigger than
     * the captured bounding box. Width and depth grow more than the height, because humanoid hitboxes
     * are narrow and the result would otherwise look like a thin white pillar.
     */
    private static final float WIDTH_FACTOR = 1.60F;
    private static final float WIDTH_OFFSET = 0.25F;
    private static final float HEIGHT_FACTOR = 1.20F;
    private static final float HEIGHT_OFFSET = 0.25F;

    /** Visual size range, also used as the interaction size. */
    public static final float MIN_VISUAL_WIDTH = 0.95F;
    public static final float MIN_VISUAL_HEIGHT = 1.25F;
    public static final float MAX_VISUAL_WIDTH = 4.00F;
    public static final float MAX_VISUAL_HEIGHT = 5.00F;

    /** Client side shaking animation after a hit. */
    private static final int WOBBLE_TICKS = 10;

    /**
     * How long a spider egg inside a cocoon needs to hatch (this round's breeding pass): ten seconds, the
     * very same time a spider egg on the ground needs.
     */
    public static final int HATCH_TICKS = 200;

    /** Physics tuning: heavier than a feather, a cocoon drops and settles like a real object. */
    private static final double GRAVITY_PER_TICK = 0.05D;
    private static final double AIR_DRAG = 0.98D;
    private static final double GROUND_FRICTION = 0.75D;

    /** Fall speed cap, so a cocoon can never tunnel through a floor while still dropping fast. */
    private static final double MAX_FALL_SPEED = 1.5D;

    /**
     * Horizontal speed cap. A cocoon is heavy silk wrapped prey: a light touch only scoots it a little,
     * but pushing it along keeps up with a walking player instead of feeling like dragging a balloon.
     */
    private static final double MAX_HORIZONTAL_SPEED = 0.25D;

    /** Velocities below this are snapped to zero, so a resting cocoon never micro jitters. */
    private static final double VELOCITY_EPSILON = 0.003D;

    /**
     * What one cut with a pair of shears costs (feature COCOON-RELEASE). One point is the vanilla price of
     * shearing, and it is charged whether the cocoon still held prey or was only an empty shell.
     */
    private static final int CUT_OPEN_SHEARS_DAMAGE = 1;

    /**
     * The ring of cobweb shards a cut throws up: this many points around the silk, each of them throwing
     * {@link #CUT_OPEN_RING_BURST} of the vanilla cobweb block particles.
     */
    private static final int CUT_OPEN_RING_POINTS = 24;
    private static final int CUT_OPEN_RING_BURST = 2;

    /** How far outside the silk that ring stands. */
    private static final double CUT_OPEN_RING_MARGIN = 0.15D;

    private static final byte EVENT_HIT = 2;

    private static final EntityDataAccessor<Float> DATA_WIDTH =
            SynchedEntityData.defineId(CocoonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT =
            SynchedEntityData.defineId(CocoonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DEPTH =
            SynchedEntityData.defineId(CocoonEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_NUTRITION =
            SynchedEntityData.defineId(CocoonEntity.class, EntityDataSerializers.INT);

    /**
     * Whether a spider egg incubates in this cocoon (this round's breeding pass). It is synced, because it
     * is what colours the cocoon green on the client.
     */
    private static final EntityDataAccessor<Boolean> DATA_SPIDER_EGG =
            SynchedEntityData.defineId(CocoonEntity.class, EntityDataSerializers.BOOLEAN);

    /** Server side only, the client renders from the synced subset. */
    @Nullable
    private CocoonData data;

    /** How often a player already hit this cocoon, counting towards unpacking. */
    private int hitsTaken;

    /**
     * The remaining countdown of the spider egg inside (this round's breeding pass), 0 while the cocoon
     * holds no egg. It is mirrored into {@link CocoonData} when the cocoon is packed into an item, so the
     * incubation continues instead of starting over.
     */
    private int hatchTicks;

    private int wobbleTicks;

    /**
     * Client side position interpolation, mirroring the vanilla minecart. The server owns the real
     * position and velocity, the client glides towards each synced target instead of snapping to it.
     */
    private int lSteps;
    private double lx;
    private double ly;
    private double lz;
    private double lyr;
    private double lxr;

    public CocoonEntity(EntityType<? extends CocoonEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    public CocoonEntity(Level level)
    {
        this(HungryCraftEntities.COCOON.get(), level);
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_WIDTH, MIN_VISUAL_WIDTH);
        this.entityData.define(DATA_HEIGHT, MIN_VISUAL_HEIGHT);
        this.entityData.define(DATA_DEPTH, MIN_VISUAL_WIDTH);
        this.entityData.define(DATA_NUTRITION, 1);
        this.entityData.define(DATA_SPIDER_EGG, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);

        if (DATA_WIDTH.equals(key) || DATA_HEIGHT.equals(key) || DATA_DEPTH.equals(key))
        {
            this.refreshDimensions();
        }
    }

    /** Applies a prey to this cocoon: size, nutrition, shown name and hitbox. */
    public void setCocoonData(CocoonData data)
    {
        this.data = data;
        this.entityData.set(DATA_WIDTH, visualWidth(data.getWidth()));
        this.entityData.set(DATA_HEIGHT, visualHeight(data.getHeight()));
        this.entityData.set(DATA_DEPTH, visualDepth(data.getDepth()));
        this.entityData.set(DATA_NUTRITION, data.getNutrition());
        this.setCustomName(data.getDisplayName());
        this.setCustomNameVisible(true);

        // A cocoon that was picked up while its egg was incubating continues exactly where it stopped.
        this.entityData.set(DATA_SPIDER_EGG, data.hasSpiderEgg());
        this.hatchTicks = data.hasSpiderEgg() ? data.getHatchTicks() : 0;

        this.refreshDimensions();
    }

    /** @return the visual width of a cocoon around prey of the given captured width. */
    public static float visualWidth(float capturedWidth)
    {
        return Mth.clamp(capturedWidth * WIDTH_FACTOR + WIDTH_OFFSET, MIN_VISUAL_WIDTH, MAX_VISUAL_WIDTH);
    }

    /** @return the visual height of a cocoon around prey of the given captured height. */
    public static float visualHeight(float capturedHeight)
    {
        return Mth.clamp(capturedHeight * HEIGHT_FACTOR + HEIGHT_OFFSET, MIN_VISUAL_HEIGHT, MAX_VISUAL_HEIGHT);
    }

    /** @return the visual depth of a cocoon around prey of the given captured depth. */
    public static float visualDepth(float capturedDepth)
    {
        return Mth.clamp(capturedDepth * WIDTH_FACTOR + WIDTH_OFFSET, MIN_VISUAL_WIDTH, MAX_VISUAL_WIDTH);
    }

    @Nullable
    public CocoonData getCocoonData()
    {
        return this.data;
    }

    public float getVisualWidth()
    {
        return this.entityData.get(DATA_WIDTH);
    }

    public float getVisualHeight()
    {
        return this.entityData.get(DATA_HEIGHT);
    }

    public float getVisualDepth()
    {
        return this.entityData.get(DATA_DEPTH);
    }

    public int getNutrition()
    {
        return this.entityData.get(DATA_NUTRITION);
    }

    /** @return true when a spider egg incubates in this cocoon, which makes it a nest and not a meal. */
    public boolean hasSpiderEgg()
    {
        return this.entityData.get(DATA_SPIDER_EGG);
    }

    /** @return the ticks the egg inside still needs, 0 when the cocoon holds no egg. */
    public int getHatchTicks()
    {
        return this.hatchTicks;
    }

    public int getWobbleTicks()
    {
        return this.wobbleTicks;
    }

    /**
     * The interaction box follows the visual size, so attacking and eating a cocoon matches what the
     * player sees. The vanilla AABB cannot be deeper than it is wide, so depth shares the width value.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return EntityDimensions.scalable(Math.max(this.getVisualWidth(), MIN_VISUAL_WIDTH),
                Math.max(this.getVisualHeight(), MIN_VISUAL_HEIGHT));
    }

    /** A plain entity is not clickable by default, but a cocoon has to be. */
    @Override
    public boolean isPickable()
    {
        return true;
    }

    /** A cocoon is a real world object: players and mobs can nudge it around like a boat or an item. */
    @Override
    public boolean isPushable()
    {
        return true;
    }

    /** A cocoon must never hard block a player, otherwise it could not be pushed at all. */
    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    /** A cocoon is made of web, it must never be slowed down by its own material. */
    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier)
    {
        // Intentionally empty.
    }

    /**
     * The client receives low frequency position updates and glides towards them, exactly like a
     * minecart. The {@code interpolate} flag is ignored on purpose: relative move packets still need
     * smoothing, not a hard snap.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean interpolate)
    {
        this.lx = x;
        this.ly = y;
        this.lz = z;
        this.lyr = yaw;
        this.lxr = pitch;

        // Interpolate over the steps the server asked for, so motion keeps up with the server instead
        // of smoothly trailing it like a floating balloon.
        this.lSteps = Math.max(1, steps);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide)
        {
            this.tickLerp();

            if (this.wobbleTicks > 0)
            {
                this.wobbleTicks--;
            }

            // The server owns the physics, the client only interpolates and animates.
            return;
        }

        this.applyPhysics();

        // While a spider egg incubates, the cocoon counts down; at zero the child steps out.
        if (this.hasSpiderEgg() && --this.hatchTicks <= 0)
        {
            this.hatch();
        }
    }

    /**
     * The egg inside opens: the cocoon is gone and one ordinary brood spider stands in its place, owned by
     * the player who pushed the egg in. Nothing else is left behind - no cocoon item, no loot and no
     * experience, because the prey of this cocoon was digested long ago.
     */
    private void hatch()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }

        SpiderMinionEntity spider = this.hatchling(level);

        if (spider != null)
        {
            spider.setOwnerUUID(this.data == null ? null : this.data.getEggOwner());
            spider.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);

            // The gear of a digested human fights on (Infested Steve round): the soldier that hatches here takes over
            // the armour, the shield, the blade and the bow of the human it grew out of, so nothing a fallen soldier
            // carried is lost with him.
            if (spider instanceof InfestedSteveEntity infested && this.data != null)
            {
                infested.inheritGearFrom(this.data.copyEntityNbt());
            }

            level.addFreshEntity(spider);
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TURTLE_EGG_HATCH,
                SoundSource.NEUTRAL, 0.9F, 1.1F);
        spawnWebParticles();

        this.discard();
    }

    /**
     * @return the child of this cocoon (Brood Ability framework, extended by the Special Spiders round I and by phase X):
     *         a zombie spider when the prey that was digested here was exactly {@code minecraft:zombie}, a skeleton
     *         spider for exactly {@code minecraft:skeleton}, a creeper spider for exactly {@code minecraft:creeper}, and
     *         an ordinary brood spider for every other prey.
     *
     * <p>Only those two preys count - a husk, a drowned, a zombie villager, a wither skeleton and a stray are
     * deliberately not one of them here, they keep hatching what they hatched before this round. A cocoon without prey
     * data (an old save, a broken item) also keeps hatching the ordinary spider. There is no second hatching system:
     * this one method stays the single map from a digested prey to the spider that comes out.
     */
    @Nullable
    private SpiderMinionEntity hatchling(ServerLevel level)
    {
        if (this.data == null)
        {
            return HungryCraftEntities.BROOD_SPIDER.get().create(level);
        }

        ResourceLocation prey = this.data.getEntityTypeId();

        if (EntityType.getKey(EntityType.ZOMBIE).equals(prey))
        {
            return HungryCraftEntities.ZOMBIE_SPIDER.get().create(level);
        }

        if (EntityType.getKey(EntityType.SKELETON).equals(prey))
        {
            return HungryCraftEntities.SKELETON_SPIDER.get().create(level);
        }

        // A stray and a husk are the desert kind of their species (Stray and Husk Spider round): the same spider, bred
        // from the prey of that name.
        if (EntityType.getKey(EntityType.STRAY).equals(prey))
        {
            return HungryCraftEntities.STRAY_SPIDER.get().create(level);
        }

        if (EntityType.getKey(EntityType.HUSK).equals(prey))
        {
            return HungryCraftEntities.HUSK_SPIDER.get().create(level);
        }

        if (EntityType.getKey(EntityType.DROWNED).equals(prey))
        {
            return HungryCraftEntities.DROWNED_SPIDER.get().create(level);
        }

        // A creeper is digested into the living blast of the brood (Phase X): the creeper spider. It is deliberately not
        // the creeper itself that steps out of the cocoon and never a second hatching system - this one line is the whole
        // entrance of the species, and a creeper cocoon is the only way to breed it.
        if (EntityType.getKey(EntityType.CREEPER).equals(prey))
        {
            return HungryCraftEntities.CREEPER_SPIDER.get().create(level);
        }

        // A human body is digested into a soldier of the queen that still wears it (Infested Steve round): the standard
        // combatant and all three prepared humans hatch the infested Steve - the same body with the fight of a human and
        // with the gear it carried - and nothing of that is guessed here: the list of human types is the one the mod
        // registers its humans with, so a fourth kind of human would be covered by this line as well.
        for (RegistryObject<EntityType<HumanCombatantEntity>> human : HungryCraftEntities.HUMAN_TYPES)
        {
            if (EntityType.getKey(human.get()).equals(prey))
            {
                return HungryCraftEntities.INFESTED_STEVE.get().create(level);
            }
        }

        // The end of the queue: the prey that is not of this world hatches the spider that is not of this time either
        // (Drowned Spider round: the missing mapping of the chrono spider, found while auditing the whole table).
        if (EntityType.getKey(EntityType.ENDERMAN).equals(prey))
        {
            return HungryCraftEntities.CHRONO_SPIDER.get().create(level);
        }

        // A zombie villager is deliberately not a species of its own: it hatches the very same zombie spider, because it
        // is the same rotten prey in a different coat.
        if (EntityType.getKey(EntityType.ZOMBIE_VILLAGER).equals(prey))
        {
            return HungryCraftEntities.ZOMBIE_SPIDER.get().create(level);
        }

        return HungryCraftEntities.BROOD_SPIDER.get().create(level);
    }

    /** Client only: move one step towards the last synced position instead of teleporting. */
    private void tickLerp()
    {
        if (this.lSteps <= 0)
        {
            return;
        }

        double x = this.getX() + (this.lx - this.getX()) / (double) this.lSteps;
        double y = this.getY() + (this.ly - this.getY()) / (double) this.lSteps;
        double z = this.getZ() + (this.lz - this.getZ()) / (double) this.lSteps;
        double yaw = Mth.wrapDegrees(this.lyr - (double) this.getYRot());
        double pitch = Mth.wrapDegrees(this.lxr - (double) this.getXRot());

        this.setRot((float) (this.getYRot() + yaw / (double) this.lSteps),
                (float) (this.getXRot() + pitch / (double) this.lSteps));
        this.lSteps--;
        this.setPos(x, y, z);
    }

    /**
     * Simple, server authoritative physics. Gravity is applied every tick and cancelled by the floor
     * on the same tick, so a resting cocoon stays perfectly still while it still starts falling the
     * instant the block under it is removed.
     */
    private void applyPhysics()
    {
        Vec3 velocity = this.getDeltaMovement();

        // A cocoon is heavy and sticky, it never shoots away from a light touch.
        velocity = new Vec3(Mth.clamp(velocity.x, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED), velocity.y,
                Mth.clamp(velocity.z, -MAX_HORIZONTAL_SPEED, MAX_HORIZONTAL_SPEED));

        if (!this.isNoGravity())
        {
            velocity = velocity.add(0.0D, -GRAVITY_PER_TICK, 0.0D);
        }

        if (velocity.y < -MAX_FALL_SPEED)
        {
            velocity = new Vec3(velocity.x, -MAX_FALL_SPEED, velocity.z);
        }

        // The velocity must be written back before moving. Without this, gravity and the speed caps
        // would never accumulate, and the cocoon would drift down at a constant crawl like a balloon.
        this.setDeltaMovement(velocity);
        this.move(MoverType.SELF, velocity);

        if (this.onGround())
        {
            BlockPos below = this.getBlockPosBelowThatAffectsMyMovement();
            double friction = this.level().getBlockState(below).getFriction(this.level(), below, this) * GROUND_FRICTION;
            this.setDeltaMovement(this.getDeltaMovement().multiply(friction, 1.0D, friction));
        }
        else
        {
            this.setDeltaMovement(this.getDeltaMovement().scale(AIR_DRAG));
        }

        this.setDeltaMovement(this.deadZone(this.getDeltaMovement()));
    }

    /** Kills sub-visible leftover velocity, a cocoon on the floor has no net vertical speed at all. */
    private Vec3 deadZone(Vec3 velocity)
    {
        double x = Math.abs(velocity.x) < VELOCITY_EPSILON ? 0.0D : velocity.x;
        double z = Math.abs(velocity.z) < VELOCITY_EPSILON ? 0.0D : velocity.z;
        double y = this.onGround() ? 0.0D
                : (Math.abs(velocity.y) < VELOCITY_EPSILON ? 0.0D : velocity.y);

        return new Vec3(x, y, z);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putInt("HitsTaken", this.hitsTaken);

        if (this.data != null)
        {
            this.data.save(tag);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.hitsTaken = tag.getInt("HitsTaken");

        CocoonData loaded = CocoonData.load(tag);
        if (loaded != null)
        {
            this.setCocoonData(loaded);
        }
    }

    @Override
    public void handleEntityEvent(byte id)
    {
        if (id == EVENT_HIT)
        {
            this.wobbleTicks = WOBBLE_TICKS;
        }
        else
        {
            super.handleEntityEvent(id);
        }
    }

    /**
     * Only the Spider Queen may eat a cocoon, everybody else just gets a hint. Two other clicks work for
     * every player: pushing a spider egg in, and cutting the silk open with a pair of shears.
     */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand)
    {
        // A spider egg in the hand is pushed into a plain cocoon, which turns it into a nest (this round's
        // breeding pass). Any player may do that, not only a queen.
        if (this.tryInsertSpiderEgg(player, hand))
        {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        // A pair of shears cuts the whole cocoon open and lets the prey out (feature COCOON-RELEASE). Any
        // player may do that as well, and it is deliberately checked before the queen rule below.
        if (this.tryCutOpen(player, hand))
        {
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (!QueenDataCapability.isQueen(player))
        {
            if (!this.level().isClientSide)
            {
                player.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.not_queen_cannot_eat"), true);
            }

            return InteractionResult.PASS;
        }

        // A cocoon with a child in it is not food, not even for its own mother.
        if (this.hasSpiderEgg())
        {
            if (!this.level().isClientSide)
            {
                player.displayClientMessage(
                        Component.translatable("message.spiderqueen_hungrycraft.cocoon_incubating"), true);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }

        if (this.level() instanceof ServerLevel)
        {
            this.eat(player);
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    /**
     * Pushes a spider egg of the hand into this cocoon, which starts its countdown and consumes the egg.
     *
     * <p>The client only decides that a held egg on a cocoon is an interaction at all - it knows about the
     * egg already inside through the synced flag - while the server really inserts it, and only then uses
     * the item up.
     *
     * @return true when this click was about inserting an egg
     */
    private boolean tryInsertSpiderEgg(Player player, InteractionHand hand)
    {
        if (this.hasSpiderEgg() || !player.getItemInHand(hand).is(HungryCraftItems.SPIDER_EGG.get()))
        {
            return false;
        }

        if (this.level().isClientSide)
        {
            return true;
        }

        // Defensive: a cocoon without prey data can never hold an egg, and a refused click costs no item.
        if (this.data == null)
        {
            return false;
        }

        this.data = this.data.withSpiderEgg(player.getUUID(), HATCH_TICKS);
        this.hatchTicks = HATCH_TICKS;
        this.entityData.set(DATA_SPIDER_EGG, true);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOL_PLACE,
                SoundSource.NEUTRAL, 0.8F, 1.2F);
        spawnWebParticles();

        if (!player.isCreative())
        {
            player.getItemInHand(hand).shrink(1);
        }

        return true;
    }

    /**
     * Cuts this cocoon open with a pair of shears (feature COCOON-RELEASE).
     *
     * <p>The prey comes back exactly as it was wrapped up - see {@link CocoonRelease} - and the shears pay
     * {@link #CUT_OPEN_SHEARS_DAMAGE} point of durability for the cut, in creative mode not at all.
     *
     * <p>The order is deliberate: the creature is rebuilt first, so a cocoon whose prey can no longer be built
     * (the mod that registered that species is gone) says so and stays standing - a cut that ended in nothing
     * would have destroyed a cocoon for free. A spider egg that incubates inside dies with the silk, cutting a
     * nest open simply ends the clutch.
     *
     * @return true when this click was about a pair of shears at all, whether the cut went through or not
     */
    private boolean tryCutOpen(Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!stack.is(Items.SHEARS))
        {
            return false;
        }

        if (!(this.level() instanceof ServerLevel level))
        {
            // The client only has to know that a pair of shears on a cocoon is an interaction at all.
            return true;
        }

        if (this.data != null && CocoonRelease.release(level, this.data, this.getX(), this.getY(), this.getZ(),
                this.getYRot()) == null)
        {
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.cocoon_release_failed"), true);
            return true;
        }

        if (!player.isCreative())
        {
            stack.hurtAndBreak(CUT_OPEN_SHEARS_DAMAGE, player, broken -> player.broadcastBreakEvent(hand));
        }

        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL,
                0.9F, 0.8F);
        this.spawnCutOpenRing();

        this.discard();

        return true;
    }

    /**
     * The ring of cobweb shards a cut throws up: the vanilla cobweb block particles in one circle around the
     * silk, at the height the shears went in. It is the same particle the cocoon already throws on a hit.
     */
    private void spawnCutOpenRing()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }

        BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
        double radius = Math.max(this.getVisualWidth(), this.getVisualDepth()) * 0.5D + CUT_OPEN_RING_MARGIN;
        double y = this.getY() + this.getBbHeight() * 0.5D;

        for (int point = 0; point < CUT_OPEN_RING_POINTS; point++)
        {
            double angle = Math.PI * 2.0D * point / CUT_OPEN_RING_POINTS;

            level.sendParticles(shard, this.getX() + Math.cos(angle) * radius, y,
                    this.getZ() + Math.sin(angle) * radius, CUT_OPEN_RING_BURST, 0.05D, 0.1D, 0.05D, 0.02D);
        }
    }

    private void eat(Player player)
    {
        FoodData foodData = player.getFoodData();

        // No saturation, excess nutrition is simply wasted, eating at full hunger is allowed.
        foodData.setFoodLevel(Math.min(20, foodData.getFoodLevel() + this.getNutrition()));

        // Prefers the death sound of the prey, falls back to the eating sound, never throws.
        if (this.level() instanceof ServerLevel)
        {
            CocoonSoundResolver.playEatSound((ServerLevel) this.level(), this);
        }

        spawnWebParticles();

        // The meal is paid for twice (this round's breeding pass): the normal experience of the prey is
        // dropped as real orbs right here, and one time in four a spider egg is left behind as well.
        if (this.data != null && this.level() instanceof ServerLevel level)
        {
            SpiderRewards.grant(level, this.position(), this.data.getExperienceReward(), player);
        }

        this.discard();
    }

    /**
     * Player attacks only pack the cocoon, they never harm the creature inside and never release it.
     * Everything that is not a player attack is ignored in this first version.
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.level().isClientSide || this.isRemoved())
        {
            return false;
        }
        if (!(source.getEntity() instanceof Player))
        {
            return false;
        }

        this.hitsTaken++;
        this.level().broadcastEntityEvent(this, EVENT_HIT);
        spawnWebParticles();

        if (this.hitsTaken >= HungryCraftConfig.cocoonHitsToPack())
        {
            this.packIntoItem();
        }
        else
        {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOL_HIT,
                    SoundSource.NEUTRAL, 1.0F, 0.8F);
        }

        return true;
    }

    /** Turns the cocoon into a single, non stackable item that carries the whole prey data. */
    private void packIntoItem()
    {
        if (this.data == null)
        {
            // Defensive: a cocoon without data must never duplicate anything, it just disappears.
            this.discard();
            return;
        }

        // A cocoon that is picked up while its egg incubates takes the remaining time with it, so it
        // continues instead of starting its ten seconds over.
        this.data = this.data.withHatchTicks(this.hatchTicks);

        ItemStack stack = new ItemStack(HungryCraftItems.COCOON.get());
        this.data.writeTo(stack);
        this.spawnAtLocation(stack);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WOOL_BREAK,
                SoundSource.NEUTRAL, 1.0F, 0.8F);

        this.discard();
    }

    private void spawnWebParticles()
    {
        if (this.level() instanceof ServerLevel)
        {
            ((ServerLevel) this.level()).sendParticles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
                    this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.05D);
        }
    }
}
