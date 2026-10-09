package spiderqueenhungrycraft.hook;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.web.WebShotProjectile;

/**
 * The web head of a spider hook (feature SPIDER-HOOK).
 *
 * <p>It is the flying half of the rod: a small web that is thrown at the world, hangs on the first block it
 * touches and then drags its thrower towards that block. It is <b>not</b> a weapon that is thrown away - the
 * rod stays in the hand the whole time, the web is only the part that travels.
 *
 * <p>The state machine stays small: a web either flies, hangs on a block or holds a creature, and a pull
 * either drags its thrower or the being it caught.
 *
 * <pre>
 * FLYING            the web is on its way, it looks for the first block or creature in its path
 * ATTACHED_BLOCK    the web hangs on {@link #hookPos()}, the owner can be pulled towards it
 * PULLING_BLOCK     the web drags its owner towards that anchor, tick by tick
 * HOOKED_ENTITY     the web caught a creature and sits on it, like the bobber of a fishing rod
 * GRAPPLING_ENTITY  the web drags either the caught creature or its own thrower, whoever is lighter
 * </pre>
 *
 * <p>A creature is caught the way a fishing rod catches one: the web sits on it and waits. It only starts to
 * drag when its thrower asks for it a second time, and then a simple rule decides who is moved - a being that
 * cannot be shoved, or that is heavy enough, drags the thrower to itself, a light one is dragged to the
 * thrower. A real player is never caught at all: the web passes straight through him.
 *
 * <p>A web head can never be summoned and never survives without its thrower: the moment its owner is gone
 * from the world the web discards itself, so a web that hangs in a wall can never become a ghost. As long
 * as he is around, an anchor does survive a reload, which is what makes a hanging web worth coming back to.
 *
 * <p>The web knows the rod that fired it ({@link #castId()}), so it can be taken back by itself: the moment
 * that very rod leaves the hand of its owner - a switch to another hook rod included - the web comes home
 * or starts to pull. When it comes home it hands the result back to that rod, which is where a finished
 * cast is counted as one use: only a web that really hung on a block is worth one, a miss is free.
 */
public class SpiderHookEntity extends Projectile
{
    /** The speed the web head leaves the hand with, in blocks per tick. */
    public static final float LAUNCH_SPEED = 1.6F;

    /**
     * How far in front of the surface it hit the web sits. It is half a block, so the cobweb that is drawn
     * there fills exactly the cell next to the block it hangs on - the web looks glued to that surface
     * instead of half sunk into it.
     */
    private static final double SURFACE_OFFSET = 0.5D;

    /** A head that has slowed down to this is never going to hit anything: it is taken back. */
    private static final double MIN_FLYING_SPEED_SQR = 0.01D;

    /** The web debris burst of an anchor, the same particles the web shot leaves behind. */
    private static final int ATTACH_PARTICLES = 12;
    private static final double ATTACH_PARTICLE_SPREAD = 0.15D;
    private static final double ATTACH_PARTICLE_SPEED = 0.03D;

    /** Close enough to the anchor: the owner arrived and the web comes back into his hand. */
    private static final double PULL_ARRIVE_DISTANCE = 2.0D;

    /** The pull is a speed, never a teleport, and it grows with the distance: farther means harder. */
    private static final double PULL_MIN_SPEED = 0.45D;
    private static final double PULL_SPEED_PER_BLOCK = 0.045D;
    private static final double PULL_MAX_SPEED = 1.35D;

    /** A pull that only rubs along the floor feels broken, so a pull off the ground lifts a little first. */
    private static final double PULL_GROUND_LIFT = 0.22D;

    /**
     * A pull has to be able to cover its own silk: how long it may take is derived from the reach of the
     * quality, so a rod with a long tether really arrives instead of letting go halfway. The cap keeps a pull
     * with a very long silk from running away.
     */
    private static final int PULL_TICKS_MARGIN = 20;
    private static final int PULL_MAX_TICKS_CAP = 400;

    /** Where on a caught being the web head sits, as a share of its height. */
    private static final double ENTITY_HOOK_HEIGHT = 0.7D;

    /** Close enough to its catcher: a dragged being has arrived and the web comes home. */
    private static final double ENTITY_ARRIVE_DISTANCE = 1.5D;

    /** How far in front of its catcher a dragged being is brought. */
    private static final double ENTITY_DRAG_GOAL_DISTANCE = 1.5D;

    /**
     * The drag is added on top of whatever the being does itself, never put in place of it, and the sum never
     * grows past this: a caught creature keeps its own feet under itself while the silk reels it in.
     */
    private static final double ENTITY_DRAG_MAX_SPEED = 1.6D;

    private static final String TAG_ATTACHED = "Attached";
    private static final String TAG_HOOK_POS = "HookPos";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_FLIGHT_TICKS = "FlightTicks";
    private static final String TAG_CAST_ID = "CastId";
    private static final String TAG_LANDED = "Landed";

    private SpiderHookTier tier = SpiderHookTier.PLAIN;
    private BlockPos hookPos = BlockPos.ZERO;
    private boolean attached;
    private boolean pulling;
    private int flightTicks;
    private int pullTicks;

    /** The mark of the rod that fired this web, or zero while it was not fired by a rod at all. */
    private long castId;

    /** True once the web hung on a block. Only such a cast is worth a use of the rod, a miss is not. */
    private boolean landed;

    /**
     * How far the web dragged its owner while it pulled, in blocks. It is what the silk is worn by, and it is
     * only ever accumulated on the server: no client needs to know it.
     */
    private double travelledBlocks;

    /** Where the owner stood at the end of the last pull tick, so a pull can measure what it really moved. */
    @Nullable
    private Vec3 lastPullPosition;

    /** The being the web caught, or null while it hangs on a block or is still on its way. Server side only. */
    @Nullable
    private LivingEntity hookedTarget;

    /** True while the web drags a caught being; the block pull uses {@link #pulling} for the same idea. */
    private boolean grappling;

    /** How long a being has been caught, so that a web may not hold one forever. */
    private int holdTicks;

    /** The cast is only ever charged once, whatever ends it. */
    private boolean finished;

    public SpiderHookEntity(EntityType<? extends SpiderHookEntity> type, Level level)
    {
        super(type, level);
    }

    /** The constructor the rod uses: the quality of the rod is handed over to the web head. */
    public SpiderHookEntity(Level level, Player owner, SpiderHookTier tier)
    {
        this(HungryCraftEntities.SPIDER_HOOK.get(), level);
        this.setOwner(owner);
        this.tier = tier;
    }

    /**
     * Synced, so every client knows whether the web is on its way or already glued to a block. Without it a
     * client would have to guess, and a web that hangs on a wall would be drawn like one in flight.
     */
    private static final EntityDataAccessor<Boolean> DATA_ATTACHED =
            SynchedEntityData.defineId(SpiderHookEntity.class, EntityDataSerializers.BOOLEAN);

    /** And whether the web is dragging its owner in, which is the state the third click ends. */
    private static final EntityDataAccessor<Boolean> DATA_PULLING =
            SynchedEntityData.defineId(SpiderHookEntity.class, EntityDataSerializers.BOOLEAN);

    /** The being the web holds, by entity id, so a client can see what it caught. Zero means none. */
    private static final EntityDataAccessor<Integer> DATA_HOOKED_ENTITY =
            SynchedEntityData.defineId(SpiderHookEntity.class, EntityDataSerializers.INT);

    /** And whether that being is the one being dragged, which is what the silk looks taut for. */
    private static final EntityDataAccessor<Boolean> DATA_GRABBING =
            SynchedEntityData.defineId(SpiderHookEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Four facts about a hook travel to the other clients: whether the web hangs on a block, whether it pulls
     * its owner, which being it caught and whether that being is being dragged. Everything else - the anchor,
     * the quality of the rod, the mark of the cast, the distance it already dragged - stays on the server,
     * because the silk is drawn from the hand of the thrower to wherever the server puts the web, so no client
     * needs it to draw the web. Those flags are what tells the states apart for everybody who watches.
     */
    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_ATTACHED, false);
        this.entityData.define(DATA_PULLING, false);
        this.entityData.define(DATA_HOOKED_ENTITY, 0);
        this.entityData.define(DATA_GRABBING, false);
    }

    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide)
        {
            return;
        }

        Player owner = this.getOwningPlayer();

        if (owner == null || !owner.isAlive())
        {
            // The hand that holds the rod is gone, so the web has nothing left to hold on to.
            this.endCast(false);
            this.discard();
            return;
        }

        if (!SpiderHookItem.isCastHeld(owner, this.castId))
        {
            // Any change of the hand takes the web with it, whatever he holds instead - a second hook rod
            // just as much as anything else. A web in the air comes back, a web on a block starts to pull,
            // which is exactly what a fishing rod does when its rod leaves the hand. Only the very rod that
            // fired this web counts as held, which is why the cast carries its mark.
            this.stow();
            return;
        }

        if (this.attached)
        {
            this.tickAttached(owner);
            return;
        }

        if (this.hookedTarget != null)
        {
            this.tickHookedEntity(owner);
            return;
        }

        this.tickFlying(owner);
    }

    /** The web is on its way: it only ever checks blocks and is taken back as soon as it cannot arrive. */
    private void tickFlying(Player owner)
    {
        // A web head may never fly forever. It comes back the moment it left the loaded world, ran out of
        // its rope, hung still in the air or simply flew for too long.
        if (!this.level().hasChunkAt(this.blockPosition()))
        {
            this.recall();
            return;
        }

        if (this.position().distanceTo(owner.position()) > this.tier.maxRange())
        {
            this.recall();
            return;
        }

        if (++this.flightTicks > this.tier.maxFlightTicks())
        {
            this.recall();
            return;
        }

        Vec3 from = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 to = from.add(motion);

        BlockHitResult hit = this.level()
                .clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        EntityHitResult caught = this.findCatch(owner, from, to);

        // Whatever stands in the path is a hit, but only the first of them counts: the nearer one wins, so a
        // creature in front of a wall is caught and not the wall behind it.
        if (caught != null && (hit.getType() != HitResult.Type.BLOCK
                || from.distanceToSqr(caught.getLocation()) <= from.distanceToSqr(hit.getLocation())))
        {
            this.hookToEntity((LivingEntity) caught.getEntity(), caught.getLocation());
            return;
        }

        if (hit.getType() == HitResult.Type.BLOCK)
        {
            this.attachTo(hit);
            return;
        }

        this.setPos(to.x, to.y, to.z);
        this.setDeltaMovement(motion.add(0.0D, -this.tier.gravity(), 0.0D));
        // A projectile only flies smoothly when every client knows its speed as well as its place: this flag
        // turns the new velocity into a packet of its own, the very way a thrown arrow does it.
        this.hasImpulse = true;

        if (this.getDeltaMovement().lengthSqr() < MIN_FLYING_SPEED_SQR)
        {
            this.recall();
            return;
        }

        // The head keeps pointing where it flies, like any thrown weapon.
        this.updateRotation();
    }

    /** The web hangs on a block: it stays where it is and either waits or drags its owner in. */
    private void tickAttached(Player owner)
    {
        this.setDeltaMovement(Vec3.ZERO);

        if (!this.anchorIsThere())
        {
            // The block the web hangs on is gone, so the anchor is gone with it. A cast the world ended
            // like this costs nothing: it did not come home by arriving at its anchor.
            this.release();
            return;
        }

        if (this.tetherIsOutOfReach(owner))
        {
            // The owner walked out of the reach of his own silk, so the web lets go without being asked. The
            // check runs on every tick of a hanging web, whether it pulls or just waits.
            this.severTether();
            return;
        }

        if (!this.pulling)
        {
            return;
        }

        if (++this.pullTicks > this.maxPullTicks())
        {
            this.stopPulling();
            return;
        }

        this.pull(owner);
    }

    /** Hangs the web on the block it hit and leaves it there. */
    private void attachTo(BlockHitResult hit)
    {
        Direction face = hit.getDirection();
        Vec3 offset = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ()).scale(SURFACE_OFFSET);
        Vec3 stuck = hit.getLocation().add(offset);

        this.setPos(stuck.x, stuck.y, stuck.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.hookPos = hit.getBlockPos().immutable();
        this.attached = true;
        this.entityData.set(DATA_ATTACHED, true);
        // The web really hit something, so this cast is a use of the rod - counted when the web comes home.
        this.landed = true;
        this.flightTicks = 0;
        this.pullTicks = 0;

        this.level().playSound(null, stuck.x, stuck.y, stuck.z, SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.7F,
                1.1F);

        // The very same cobweb debris the web shot leaves behind, so an anchor looks like the web it is.
        if (this.level() instanceof ServerLevel serverLevel)
        {
            serverLevel.sendParticles(WebShotProjectile.cobwebParticles(), stuck.x, stuck.y, stuck.z,
                    ATTACH_PARTICLES, ATTACH_PARTICLE_SPREAD, ATTACH_PARTICLE_SPREAD, ATTACH_PARTICLE_SPREAD,
                    ATTACH_PARTICLE_SPEED);
        }
    }

    /**
     * @return the creature the web runs into on this step, or null while there is none. Only living beings are
     *         caught: a real player, the thrower himself and everything that is not a creature stay untouched.
     */
    @Nullable
    private EntityHitResult findCatch(Player owner, Vec3 from, Vec3 to)
    {
        if (!HungryCraftConfig.hookEntityEnabled())
        {
            return null;
        }

        return ProjectileUtil.getEntityHitResult(this.level(), this, from, to,
                this.getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0D),
                entity -> canCatch(entity, owner));
    }

    /**
     * @return true while that entity may be caught by the web of that thrower. A real player is never caught -
     *         the web passes straight through him - and neither is the thrower himself or a being that is gone.
     */
    public static boolean canCatch(Entity entity, @Nullable Entity owner)
    {
        if (entity == owner || !(entity instanceof LivingEntity) || entity.isSpectator())
        {
            return false;
        }

        return !(entity instanceof Player) && entity.isAlive();
    }

    /**
     * The web caught a creature: it sits on it and waits, the way the bobber of a fishing rod sits on the fish
     * it caught. Nobody is moved yet - the thrower decides with his next click who will be.
     */
    private void hookToEntity(LivingEntity target, Vec3 where)
    {
        this.setPos(where.x, where.y, where.z);
        this.setDeltaMovement(Vec3.ZERO);
        this.hookedTarget = target;
        this.entityData.set(DATA_HOOKED_ENTITY, target.getId());
        // The web really caught something, so this cast is a use of the rod - counted when the web comes home.
        this.landed = true;
        this.flightTicks = 0;
        this.pullTicks = 0;
        this.holdTicks = 0;
        this.lastPullPosition = null;

        this.level().playSound(null, where.x, where.y, where.z, SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.7F,
                1.3F);

        // The very same cobweb debris a block anchor leaves behind, so a catch looks like the web it is.
        if (this.level() instanceof ServerLevel serverLevel)
        {
            serverLevel.sendParticles(WebShotProjectile.cobwebParticles(), where.x, where.y, where.z,
                    ATTACH_PARTICLES, ATTACH_PARTICLE_SPREAD, ATTACH_PARTICLE_SPREAD, ATTACH_PARTICLE_SPREAD,
                    ATTACH_PARTICLE_SPEED);
        }
    }

    /**
     * The web holds a creature: it rides on it and waits for the order to drag. Every tick it checks the whole
     * situation the design asks for - the being may die, vanish, be let go by a click or leave the reach of the
     * silk, and it may not stay caught forever either.
     */
    private void tickHookedEntity(Player owner)
    {
        LivingEntity target = this.hookedTarget;

        if (target == null || target.isRemoved() || target.level() != this.level() || !target.isAlive())
        {
            // The catch is gone: the cast still counts, the web really held something for a while.
            this.recall();
            return;
        }

        if (++this.holdTicks > HungryCraftConfig.hookEntityHoldTicks())
        {
            // A web may not hold a being forever, so the catch ends by itself, counted like any other catch.
            this.recall();
            return;
        }

        // The web head rides on the being, so the silk is drawn to wherever the creature went.
        this.setPos(target.getX(), target.getY() + target.getBbHeight() * ENTITY_HOOK_HEIGHT, target.getZ());
        this.setDeltaMovement(Vec3.ZERO);

        if (this.tetherIsOutOfReach(owner))
        {
            // The being ran farther away than the silk reaches: it breaks free, and the cast is counted.
            this.severTether();
            return;
        }

        if (!this.grappling)
        {
            return;
        }

        if (++this.pullTicks > this.maxPullTicks())
        {
            this.stopEntityDrag();
            return;
        }

        this.grapple(owner, target);
    }

    /**
     * One tick of dragging a caught being. A single rule decides who is moved, exactly as the design asks: a
     * being that cannot be shoved, or that is heavy enough, drags its thrower to itself, everything else is
     * dragged to the thrower.
     */
    private void grapple(Player owner, LivingEntity target)
    {
        if (canTargetBeDragged(target))
        {
            this.dragCreature(owner, target);
            return;
        }

        this.pullOwnerTo(owner, target.position());
    }

    /**
     * @return true while the web may move that being. A being a pack marked as immune or heavy is never moved,
     *         and neither is one that cannot be shoved around in the first place - the hook drags its thrower
     *         to such a being instead, which is the first rule of the design.
     */
    public static boolean canTargetBeDragged(LivingEntity target)
    {
        if (target.getType().is(HookEntityTags.HOOK_IMMUNE) || target.getType().is(HookEntityTags.HOOK_HEAVY))
        {
            return false;
        }

        if (!target.isPushable() || target.isPassenger() || target.isVehicle())
        {
            return false;
        }

        return target.getMaxHealth() < HungryCraftConfig.hookHeavyHealth();
    }

    /**
     * Reels a light creature in, towards the point in front of its catcher. The pull is added on top of what the
     * creature does itself, so its own feet stay under it and no AI is fought, and the sum is capped so that
     * nothing is flung through the air.
     */
    private void dragCreature(Player owner, LivingEntity target)
    {
        Vec3 ahead = owner.getLookAngle();
        ahead = new Vec3(ahead.x, 0.0D, ahead.z);

        if (ahead.lengthSqr() < 1.0E-4D)
        {
            ahead = new Vec3(0.0D, 0.0D, 1.0D);
        }

        Vec3 goal = owner.position().add(ahead.normalize().scale(ENTITY_DRAG_GOAL_DISTANCE));
        Vec3 delta = goal.subtract(target.position());
        double distance = delta.length();

        if (distance <= ENTITY_ARRIVE_DISTANCE)
        {
            // It is here: the catch is over and the web comes home, counted and worn like any other cast.
            this.recall();
            return;
        }

        Vec3 motion = delta.normalize().scale(HungryCraftConfig.hookEntityDragSpeed());
        Vec3 sum = target.getDeltaMovement().add(motion);

        if (sum.length() > ENTITY_DRAG_MAX_SPEED)
        {
            sum = sum.normalize().scale(ENTITY_DRAG_MAX_SPEED);
        }

        target.setDeltaMovement(sum);
        // The new speed has to reach the clients, otherwise the drag is never seen.
        target.hurtMarked = true;
        target.hasImpulse = true;
        // Being reeled in is not a fall, so a creature dragged over an edge is not killed by it.
        target.fallDistance = 0.0F;

        // The rod pays for what it really drags: the distance the creature covered counts like the distance of a
        // thrower being pulled towards a block.
        this.noteTravelled(target.position());
    }

    /** Drags the owner towards the anchor. It only ever gives speed, the player is never teleported. */
    private void pull(Player player)
    {
        this.pullOwnerTo(player, this.position());
    }

    /**
     * Dragging the owner towards one point of the world: the anchor of a hanging web, or the being the web
     * caught. It only ever gives speed and never teleports him, and what it moved is what the rod pays for.
     */
    private void pullOwnerTo(Player player, Vec3 anchor)
    {
        this.noteTravelled(player.position());

        // Being pulled is not a fall, and the protection lasts exactly as long as the pull does: a plain jump
        // down never has a pull behind it, so it is never covered by this.
        player.fallDistance = 0.0F;

        Vec3 delta = anchor.subtract(player.position());
        double distance = delta.length();

        if (distance <= PULL_ARRIVE_DISTANCE)
        {
            // He arrived, so the web comes back into his hand on its own.
            this.recall();
            return;
        }

        // The farther away the anchor is, the harder the web pulls.
        double speed = Mth.clamp(PULL_MIN_SPEED + distance * PULL_SPEED_PER_BLOCK, PULL_MIN_SPEED, PULL_MAX_SPEED);
        Vec3 motion = delta.normalize().scale(speed);

        if (player.onGround())
        {
            motion = motion.add(0.0D, PULL_GROUND_LIFT, 0.0D);
        }

        player.setDeltaMovement(motion);
        // The new speed has to reach the client, otherwise the pull is never seen.
        player.hurtMarked = true;
    }

    /**
     * Records where the dragged thing stands now, so the rod pays for the distance it really covered. The first
     * tick of a drag has no earlier place yet, so it is free: nothing ever jumps.
     */
    private void noteTravelled(Vec3 position)
    {
        if (this.lastPullPosition != null)
        {
            this.travelledBlocks += position.distanceTo(this.lastPullPosition);
        }

        this.lastPullPosition = position;
    }

    /**
     * @return true while the owner has moved farther from his own anchor than the silk of his quality may
     *         stretch. It is the limit that keeps a rod from being a free way home from anywhere in the world:
     *         a web that hangs on a block is only good as long as its owner stays inside its reach.
     */
    private boolean tetherIsOutOfReach(Player owner)
    {
        Vec3 anchor = this.anchorPoint();

        if (!HungryCraftConfig.hookTetherEnabled() || anchor == null)
        {
            return false;
        }

        return owner.position().distanceTo(anchor) > this.tier.tetherDistance();
    }

    /**
     * @return the point the silk is tied to: the block the web hangs on, the being it caught, or null while it
     *         is still on its way. A caught being is always measured by the distance its catcher keeps to the
     *         being itself, which is what the design asks for.
     */
    @Nullable
    private Vec3 anchorPoint()
    {
        if (this.hookedTarget != null)
        {
            return this.hookedTarget.position();
        }

        return this.hookPos.equals(BlockPos.ZERO) ? null : Vec3.atCenterOf(this.hookPos);
    }

    /**
     * @return how many ticks a pull of this quality may take. It is derived from the reach of the silk, so a
     *         rod with a long tether may really cover it instead of letting go halfway.
     */
    private int maxPullTicks()
    {
        double needed = Math.ceil(this.tier.tetherDistance() / PULL_MIN_SPEED) + PULL_TICKS_MARGIN;

        return (int) Math.min(PULL_MAX_TICKS_CAP, needed);
    }

    /**
     * The silk is taut and the anchor is out of reach, so the web lets go without being asked. It is a cast
     * that really hung on a block, so it is counted like one and the rod takes its break once its casts in a
     * row are used up. The wear follows the distance the web really dragged: a web that was only carried
     * around and never pulled costs no durability at all.
     */
    private void severTether()
    {
        this.playOwnerSound(SoundEvents.LEASH_KNOT_BREAK, 0.9F, 1.2F);
        this.endCast(true);
        this.discard();
    }

    /** @return true while the block the web hangs on is still there. */
    private boolean anchorIsThere()
    {
        if (!this.level().hasChunkAt(this.hookPos))
        {
            return false;
        }

        return !this.level().getBlockState(this.hookPos).isAir();
    }

    /** @return the player who fired this web, or null while he is gone. */
    @Nullable
    public Player getOwningPlayer()
    {
        return this.getOwner() instanceof Player player ? player : null;
    }

    /** @return true while the web hangs on a block. The flag is synced, so a client sees it too. */
    public boolean isAttached()
    {
        return this.attached || this.entityData.get(DATA_ATTACHED);
    }

    /** @return true while the web holds a creature. The id is synced, so a client sees it too. */
    public boolean isHookedToEntity()
    {
        return this.hookedTarget != null || this.entityData.get(DATA_HOOKED_ENTITY) != 0;
    }

    /** @return true while the web drags a caught being. Synced, like the other state flags. */
    public boolean isGrabbing()
    {
        return this.grappling || this.entityData.get(DATA_GRABBING);
    }

    /** @return the being the web holds, or null while it holds none. Server side only. */
    @Nullable
    public LivingEntity hookedTarget()
    {
        return this.hookedTarget;
    }

    /**
     * The five states of a web head, one word each. It is derived from the synced flags and never stored, so a
     * client reads the same state as the server that owns the web.
     */
    public HookState state()
    {
        if (this.isHookedToEntity())
        {
            return this.isGrabbing() ? HookState.GRAPPLING_ENTITY : HookState.HOOKED_ENTITY;
        }

        if (this.isAttached())
        {
            return this.isPulling() ? HookState.PULLING_BLOCK : HookState.ATTACHED_BLOCK;
        }

        return HookState.FLYING;
    }

    /** The names of the five states of a web head. */
    public enum HookState
    {
        FLYING,
        ATTACHED_BLOCK,
        PULLING_BLOCK,
        HOOKED_ENTITY,
        GRAPPLING_ENTITY
    }

    /**
     * The mark of the rod that fired this web. A web only ever belongs to one rod, and it is taken back the
     * moment that very rod leaves the hand of its owner.
     */
    public void setCastId(long castId)
    {
        this.castId = castId;
    }

    /** @return the mark of the rod that fired this web, or zero while it was not fired by a rod at all. */
    public long castId()
    {
        return this.castId;
    }

    /** @return true while the web drags its owner towards the anchor. Synced, like the anchor flag. */
    public boolean isPulling()
    {
        return this.pulling || this.entityData.get(DATA_PULLING);
    }

    /** @return the block the web hangs on, {@link BlockPos#ZERO} while it is still flying. */
    public BlockPos hookPos()
    {
        return this.hookPos;
    }

    /** Fired at a block again: start dragging the thrower towards the anchor. */
    public void requestPull()
    {
        if (!this.attached || this.pulling)
        {
            return;
        }

        Player owner = this.getOwningPlayer();

        if (owner == null || !owner.isAlive())
        {
            // Nobody is left to pull: the web leaves without being counted.
            this.release();
            return;
        }

        this.pulling = true;
        this.pullTicks = 0;
        // The pull measures what it really moves, so it starts from where the owner stands right now.
        this.lastPullPosition = owner.position();
        this.entityData.set(DATA_PULLING, true);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8F, 1.1F);
    }

    /** Let go of the pull. The web stays where it is, so the owner may keep on climbing on his own. */
    public void stopPulling()
    {
        if (!this.pulling)
        {
            return;
        }

        this.pulling = false;
        this.pullTicks = 0;
        this.entityData.set(DATA_PULLING, false);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6F, 0.9F);
    }

    /**
     * Fired at a caught creature again: start dragging, and let the rule of the design decide who is moved. The
     * web only ever gives speed, so a light being is reeled in while a heavy one reels its catcher in.
     */
    public void requestEntityDrag()
    {
        if (this.hookedTarget == null || this.grappling)
        {
            return;
        }

        Player owner = this.getOwningPlayer();

        if (owner == null || !owner.isAlive())
        {
            // Nobody is left to drag: the web leaves without being counted.
            this.release();
            return;
        }

        this.grappling = true;
        this.pullTicks = 0;
        // The drag measures what it really moves, so it starts from nothing and never counts a jump.
        this.lastPullPosition = null;
        this.entityData.set(DATA_GRABBING, true);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.8F, 1.1F);
    }

    /** Let go of a caught being. The web stays on it, so its catcher may ask for the drag again. */
    public void stopEntityDrag()
    {
        if (!this.grappling)
        {
            return;
        }

        this.grappling = false;
        this.pullTicks = 0;
        this.entityData.set(DATA_GRABBING, false);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6F, 0.9F);
    }

    /**
     * The owner wants the pull to end right now, whether it drags him towards an anchor or drags a caught being:
     * the web is reeled in on the spot instead of being let go, and the cast counts as one use, exactly like a
     * pull that arrived. It is the way out of a pull that got caught on a corner of the world, and it is what a
     * change of the hand does while a pull or a drag runs.
     */
    public void retractFromPull()
    {
        this.pulling = false;
        this.pullTicks = 0;
        this.grappling = false;
        this.entityData.set(DATA_PULLING, false);
        this.entityData.set(DATA_GRABBING, false);

        // The pull is over, so its speed is not kept: otherwise the player would be flung on by a web that is
        // no longer there. Only the last bit of it is taken away, a harmless fall is none of our business.
        Player owner = this.getOwningPlayer();

        if (owner != null)
        {
            owner.setDeltaMovement(owner.getDeltaMovement().scale(0.25D));
            owner.hurtMarked = true;
        }

        this.recall();
    }

    /**
     * The rod was put away. A web that still flies is taken back, one that hangs on a block starts to pull, one
     * that caught a being starts to drag it, and a pull or a drag that already runs ends on the spot. Putting
     * the rod away is the second way to ask for the pull, and the way to stop a running one for good.
     */
    public void stow()
    {
        if (this.pulling || this.grappling)
        {
            this.retractFromPull();
            return;
        }

        if (this.hookedTarget != null)
        {
            this.requestEntityDrag();
            return;
        }

        if (this.attached)
        {
            this.requestPull();
            return;
        }

        this.recall();
    }

    /** Takes the web back into the hand of its owner: the head disappears and the rod is free again. */
    public void recall()
    {
        this.endCast(true);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6F, 1.0F);
        this.discard();
    }

    /**
     * The web leaves without being counted: the world took its anchor away, or its thrower is gone. A cast
     * that did not end by coming home is not a use of the rod, exactly as the design asks.
     */
    private void release()
    {
        this.endCast(false);
        this.playOwnerSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6F, 1.0F);
        this.discard();
    }

    /**
     * Ends the cast and hands the result back to the rod that fired it. A web that really hung on a block
     * counts as one use of the rod, and the rod is free again in either case; a web that was taken back in
     * the air, or that lost its anchor, costs nothing. The rod does not have to be in the hand any more:
     * this is what charges a cast that ended after the player had already switched items.
     *
     * @param charge false for the paths that may never cost a use, such as the world dropping the web
     *        together with its owner.
     */
    private void endCast(boolean charge)
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;

        if (this.castId != 0L && this.getOwningPlayer() instanceof ServerPlayer serverPlayer)
        {
            SpiderHookItem.endCast(serverPlayer, this.castId, this.tier, charge && this.landed, this.travelledBlocks);
        }
    }

    /** The same retrieve sound a fishing rod plays, so taking the web back sounds natural. */
    private void playOwnerSound(SoundEvent sound, float volume, float pitch)
    {
        if (this.level().isClientSide)
        {
            return;
        }

        Player owner = this.getOwningPlayer();
        double x = owner != null ? owner.getX() : this.getX();
        double y = owner != null ? owner.getY() : this.getY();
        double z = owner != null ? owner.getZ() : this.getZ();

        this.level().playSound(null, x, y, z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * The web of one player, if he has one. A player can never have more than one, because the rod refuses
     * to fire a second web while one of his is still out - a second right click pulls it or takes it back.
     */
    @Nullable
    public static SpiderHookEntity findOwned(Player owner)
    {
        if (!(owner.level() instanceof ServerLevel level))
        {
            return null;
        }

        SpiderHookEntity flying = null;

        for (SpiderHookEntity hook : level.getEntitiesOfClass(SpiderHookEntity.class,
                owner.getBoundingBox().inflate(SpiderHookTier.longestReach())))
        {
            if (hook.getOwner() != owner)
            {
                continue;
            }

            if (hook.attached || hook.hookedTarget != null)
            {
                // A web that holds something - a block or a being - is the one that matters.
                return hook;
            }

            flying = hook;
        }

        return flying;
    }

    /**
     * A web head is not saved with the world: like the bobber of a fishing rod it only lives while its
     * thrower is around, which is also the strongest possible guard against a ghost web that hangs in a
     * wall forever. A caught being is deliberately not part of the save either: a web that is loaded again
     * comes home instead of holding on to a creature nobody is looking at.
     */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_ATTACHED, this.attached);
        tag.putInt(TAG_TIER, this.tier.id());
        tag.putInt(TAG_FLIGHT_TICKS, this.flightTicks);
        tag.putLong(TAG_CAST_ID, this.castId);
        tag.putBoolean(TAG_LANDED, this.landed);

        if (this.attached)
        {
            tag.putLong(TAG_HOOK_POS, this.hookPos.asLong());
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.attached = tag.getBoolean(TAG_ATTACHED);
        this.tier = SpiderHookTier.byId(tag.getInt(TAG_TIER));
        this.flightTicks = tag.getInt(TAG_FLIGHT_TICKS);
        this.castId = tag.getLong(TAG_CAST_ID);
        this.landed = tag.getBoolean(TAG_LANDED) || this.attached;
        this.entityData.set(DATA_ATTACHED, this.attached);

        if (tag.contains(TAG_HOOK_POS))
        {
            this.hookPos = BlockPos.of(tag.getLong(TAG_HOOK_POS));
        }
    }

    /** Only blocks are ever hit, exactly as the design asks: no creatures, no players. */
    @Override
    protected boolean canHitEntity(Entity entity)
    {
        return false;
    }
}
