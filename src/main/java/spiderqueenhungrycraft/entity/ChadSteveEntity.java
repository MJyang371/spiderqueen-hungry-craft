package spiderqueenhungrycraft.entity;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.faction.FactionTargetGoal;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.faction.OwnFactionTargeting;
import spiderqueenhungrycraft.human.TranscendentHuman;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.stomach.StomachCell;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachCoreEntity;

/**
 * Chad Steve (Phase 9A-GAMEPASS1, combat pass 9B, faction pass HF1-VISIBLE): the tall, broad shouldered hammer man
 * of the human faction.
 *
 * <p>He is a plain {@link PathfinderMob} and deliberately <b>not</b> a {@link Monster}. "Attacks monsters" must never
 * make him a monster himself, and two Chad Steves never fight each other because the faction rule says humans are
 * friendly with each other.
 *
 * <p>He belongs to the <b>HUMAN</b> faction, declared in {@code human_faction.json} (Phase HF1-VISIBLE, part H and
 * part N), so two things follow from the faction matrix alone:
 * <ul>
 * <li>a {@link Player} that is a Spider Queen ({@link QueenDataCapability#isQueen}), a monster faction entity and the
 * stomach core he is standing in are hostile to him,</li>
 * <li>a normal player (a human), another human unit, a villager, an iron golem, a guard, an animal or anything else
 * without a faction is never attacked by him.</li>
 * </ul>
 * No target rule is hand written here any more: {@link FactionTargeting#shouldFactionGoalAttack} answers it, exactly
 * like it answers it for a zombie, a golem or a guard. {@code /hungrycraft queen set false} therefore turns his aggro
 * off and {@code true} turns it back on, because the queen flag of that player decides her faction.
 *
 * <p>The hammer always lands exactly on {@link #HAMMER_IMPACT_TICK}, the tick of the {@code impact} marker of
 * the Blockbench {@code hammer_slam} animation. The whole hit - the 3x4x5 area in front of him, the dust and the
 * anvil sound - is resolved on the server in that single tick (see {@link ChadSteveHammerAttackGoal} and
 * {@link #performHammerImpact()}); the client only plays the same animation.
 */
public class ChadSteveEntity extends PathfinderMob implements OwnFactionTargeting, TranscendentHuman
{
    /** 300 health: a heavy, but not yet boss level enemy. */
    public static final double MAX_HEALTH = 300.0D;

    /** 15 armor, straight from the current design numbers. */
    public static final double ARMOR = 15.0D;

    /** 25 damage, handed to every valid target inside the slam area on the impact tick. */
    public static final double ATTACK_DAMAGE = 25.0D;

    /** Vanilla zombie walking speed ({@code Zombie.createAttributes}), so he walks like a normal enemy. */
    public static final double MOVEMENT_SPEED = 0.23D;

    /** Vanilla zombie follow range, a sensible melee enemy range for a target this size. */
    public static final double FOLLOW_RANGE = 35.0D;

    /**
     * Super armor (Phase 9C, part C): a hit may take his health, it may not shove him around. A resistance of one
     * removes the whole knockback of an ordinary attack, and the {@code LivingKnockBackEvent} listener of
     * {@link ChadSteveEvents} covers whatever a knockback does not go through the attribute.
     */
    public static final double KNOCKBACK_RESISTANCE = 1.0D;

    /** Collision box of the first pass. The model is drawn at full size, no renderer scale is used. */
    public static final float COLLISION_WIDTH = 1.2F;
    public static final float COLLISION_HEIGHT = 3.4F;


    /**
     * The tick of the {@code impact} marker of the Blockbench {@code hammer_slam} animation. The hammer head
     * lands on 1.0 second of the two second animation, which is tick 20 at 20 ticks per second. This constant is
     * the single source of truth for the visual impact of the animation.
     */
    public static final int HAMMER_IMPACT_TICK = 20;

    /**
     * How many ticks the damage is pulled forward in front of the visual impact (Phase 9B2, part C).
     *
     * <p>This is a plain tuning value, nothing else reads it, so it can be swept in a second:
     * <pre>
     * 0  -&gt; damage exactly on the impact frame, this is the shipped value
     * 2  -&gt; 0.1 s early      (sub tick fine tuning)
     * 4  -&gt; 0.2 s early
     * 10 -&gt; 0.5 s early
     * 15 -&gt; 0.75 s early
     * 20 -&gt; 1.0 s early, the damage would land on the first tick of the swing
     * </pre>
     *
     * <p><b>Why it is 0 and not 20:</b> the "damage comes a good second after the hammer already hit the
     * ground" that Phase 9B2 was opened for was not a synchronisation offset at all. The goal was simply not
     * ticked every game tick - {@code Goal.requiresUpdateEveryTick()} defaults to {@code false} and
     * {@code Mob.serverAiStep()} only ticks those goals on the odd ticks of the vanilla throttle, so every
     * counter inside the swing ran at half speed and the impact arrived after about two seconds instead of one
     * (see {@link ChadSteveHammerAttackGoal#requiresUpdateEveryTick()}). With that fixed, a lead of 0 lines the
     * damage up with the impact frame exactly, which is what the "1 second earlier" was meant to approximate.
     */
    public static final int HAMMER_DAMAGE_LEAD_TICKS = 0;

    /**
     * The swing tick on which the single slam hit is resolved:
     * {@code HAMMER_IMPACT_TICK - HAMMER_DAMAGE_LEAD_TICKS}, which is 20 with the shipped lead of 0.
     */
    public static final int HAMMER_DAMAGE_TICK = HAMMER_IMPACT_TICK - HAMMER_DAMAGE_LEAD_TICKS;

    /**
     * The swing tick on which the dust and the anvil landing are played (Phase 9B2, part D, plan A: the feedback
     * stays on the visual impact frame while only the damage uses the lead). Set this to
     * {@link #HAMMER_DAMAGE_TICK} to move the feedback along with the damage (plan B).
     */
    public static final int HAMMER_FEEDBACK_TICK = HAMMER_IMPACT_TICK;

    /** The {@code hammer_slam} animation is two seconds long, which is 40 ticks. */
    public static final int HAMMER_ANIMATION_DURATION_TICKS = 40;

    /** The same length in seconds, used by the client animation definition. */
    public static final float HAMMER_ANIMATION_DURATION_SECONDS = 2.0F;

    /**
     * A healthy swing moves its counter on every single tick, so this much silence can only mean that the state is
     * stuck. The watchdog then clears it, so a frozen slam can never hold the move and look flags of the attack goal
     * forever (Phase 9D-hotfix).
     */
    private static final int SLAM_WATCHDOG_TICKS = HAMMER_ANIMATION_DURATION_TICKS + 20;

    /**
     * Slam area in front of him (Phase 9B2, part F): 3 blocks wide, 4 blocks deep, 5 blocks high.
     *
     * <p>The height started at 2 blocks, which turned out to be too flat in practice: anything standing on the
     * slightest elevation in front of him was out of the box and the swing whiffed into thin air. Five blocks
     * cover a target on a small ledge or on top of a single step, while the area is still a hammer hit and not a
     * tower sweep.
     */
    public static final double HAMMER_AOE_HALF_WIDTH = 1.5D;
    public static final double HAMMER_AOE_FORWARD = 4.0D;
    public static final double HAMMER_AOE_HEIGHT = 5.0D;

    /** Rough search box around him before the exact local space filter runs. */
    private static final double HAMMER_AOE_SEARCH_INFLATE = 5.0D;

    /**
     * The dust and the anvil sound come from the spot the hammer head hits, 2 blocks in front of his feet - not
     * at the far edge of the 4 block reach, and not under his own feet either.
     */
    private static final double HAMMER_IMPACT_FORWARD_OFFSET = 2.0D;

    /** A heavy anvil landing, a bit louder and a good deal lower than the vanilla one. */
    private static final float HAMMER_SOUND_VOLUME = 1.4F;
    private static final float HAMMER_SOUND_PITCH = 0.7F;

    /**
     * Set to {@code false} to silence the slam timing log again. It logs exactly four lines per slam - start,
     * damage, impact feedback and the end of the swing - and never anything per tick, see Phase 9B2 part J. It is
     * switched on for this diagnostic round so a "the damage still feels late" report can be checked against the
     * real ticks in {@code logs/latest.log}.
     */
    public static final boolean DEBUG_SLAM_TIMING = true;

    /** True while the server side slam is running. Synced, it is what makes every client play the animation. */
    private static final EntityDataAccessor<Boolean> DATA_SLAM_RUNNING =
            SynchedEntityData.defineId(ChadSteveEntity.class, EntityDataSerializers.BOOLEAN);

    /**
     * Ticks since the swing started, {@code 0} on the very first swing tick. Synced so a client that received a
     * packet late (or not at all) can put the animation back onto the server side timeline.
     */
    private static final EntityDataAccessor<Integer> DATA_SLAM_TICK =
            SynchedEntityData.defineId(ChadSteveEntity.class, EntityDataSerializers.INT);

    /** Client side animation states. They are only ever started or stopped on the client. */
    public final AnimationState idleAnimationState = new AnimationState();
    public final AnimationState walkAnimationState = new AnimationState();
    public final AnimationState slamAnimationState = new AnimationState();

    /** Server side counter of the running slam, the authority for the impact tick. */
    private int serverSlamTick;

    /** Server side only: the last slam counter value the watchdog saw, and how long it has been standing still. */
    private int lastWatchedSlamTick = -1;
    private int slamStallTicks;

    /** Client side only, guards the single "the animation reached the impact frame" log line per swing. */
    private boolean clientImpactFrameLogged;

    public ChadSteveEntity(EntityType<? extends ChadSteveEntity> entityType, Level level)
    {
        super(entityType, level);

        // His name is set as a custom name on purpose (Phase HF0C fix): that is what makes it show above his head in
        // the world, like any other named creature. The text comes from the language file and follows the language of
        // the client, so it reads "Chad Steve" in English and "高人史蒂夫" in Chinese. A named creature also no longer
        // despawns, which is exactly what a test entity of this size wants anyway.
        this.setCustomName(Component.translatable("entity." + SpiderQueenHungryCraft.MODID + ".chad_steve"));
        this.setCustomNameVisible(true);

        if (DEBUG_SLAM_TIMING)
        {
            // One line per spawned or loaded Chad Steve: the name above his head is set from the language file, so a
            // missing translation key would be visible right here.
            SpiderQueenHungryCraft.LOGGER.info("[ChadSteve] name set to {} (visible={})", this.getCustomName(),
                    this.isCustomNameVisible());
        }
    }

    /** The attributes of Chad Steve: exactly the numbers of this round, nothing else. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ARMOR, ARMOR)
                // ATTACK_DAMAGE is not part of the plain pathfinder attribute set, it has to be added here,
                // otherwise doHurtTarget would deal no damage at all.
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                // Super armor (Phase 9C, part C): ordinary attacks no longer knock him around. The attribute is the
                // first layer, ChadSteveEvents holds the second one for knockback that does not use it.
                .add(Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_RESISTANCE);
    }

    /**
     * Chad Steve can not be pushed by other entities (Phase 9C, part D). A player walking into him, a crowd of
     * zombies or a herd of cows can not move him a single block, because the vanilla pushing code only ever moves
     * entities that report themselves as pushable.
     *
     * <p>He is still a completely normal mob: his own pathfinding, walking, chasing, falling, step climbing and
     * block collision are untouched, and nothing here clears his movement or his gravity.
     */
    @Override
    public boolean isPushable()
    {
        return false;
    }

    /**
     * The two hand written target rules of Phase 9B are gone (Phase HF1-VISIBLE, part N): who Chad Steve may attack
     * is now decided by the one faction rule of the mod, exactly like for every other mob.
     *
     * <p>For him that means: a Spider Queen player is hostile, a monster faction entity is hostile, a human (a normal
     * player or another human unit) is friendly, a village is neutral and everything without a faction is ignored. The
     * stomach core of the cell he stands in stays above all of it, see {@link #isAttackableTarget(LivingEntity)}.
     */

    /**
     * @return true when that entity is the stomach core of the cell Chad Steve is standing in right now
     *         (Phase 9C, parts F to H).
     *
     * <p>A core is the wall of the cell he was swallowed into, so this test deliberately asks nothing about
     * collisions, line of sight, reach or the vanilla pick: the core is invisible, has no collision box, can not
     * be selected with a crosshair and its centre is the middle of a whole ten block cell. The only questions are
     * whether he really stands in the stomach dimension and whether the core belongs to the very same cell.
     */
    public boolean isCellCore(@Nullable LivingEntity candidate)
    {
        if (!(candidate instanceof StomachCoreEntity core) || core.isRemoved() || !core.isAlive())
        {
            return false;
        }

        return StomachCellManager.resolveCell(this).filter(core::belongsTo).isPresent();
    }

    /**
     * Everything the hammer of this round is allowed to hit (Phase 9C part M, rewritten in Phase HF1-VISIBLE part N):
     * a Spider Queen player, a monster faction entity, or the stomach core of the cell he is in.
     *
     * <p>The two factions are no longer hand written here - the one faction rule of the mod decides, so Chad Steve can
     * never drift away from what a zombie, a villager or a guard thinks of him. Village is neutral for him, humans are
     * friendly, and everything without a faction is ignored.
     */
    public boolean isAttackableTarget(@Nullable LivingEntity candidate)
    {
        return this.isCellCore(candidate) || FactionTargeting.shouldFactionGoalAttack(this, candidate);
    }

    /** @return the cell Chad Steve is standing in, empty when he is not inside a stomach at all. */
    public Optional<StomachCell> currentStomachCell()
    {
        return StomachCellManager.resolveCell(this);
    }

    @Override
    protected void registerGoals()
    {
        // 0: swim instead of sinking to the bottom.
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // 1: chase the target and swing the hammer exactly on the impact tick.
        this.goalSelector.addGoal(1, new ChadSteveHammerAttackGoal(this));

        // 2: no target, no orders - walk around and look around like any other wandering enemy. No patrol, no
        // village search, no doors, no home position, no formation, all of that is a later phase.
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));

        // The target list of this round, highest priority first (Phase 9C part G, refined in Phase HF1-VISIBLE
        // part N):
        //   0: the stomach core of the cell he is inside, the wall of the stomach he was swallowed into,
        //   1: the faction rule: a Spider Queen player or a monster faction entity.
        // The faction goal is the very same goal the faction system injects into every other mob that takes part in
        // the war, so Chad Steve follows exactly the same rules as everybody else and never hand writes them again.
        this.targetSelector.addGoal(0, new ChadSteveStomachCoreTargetGoal(this));
        this.targetSelector.addGoal(1, new FactionTargetGoal(this));
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SLAM_RUNNING, false);
        this.entityData.define(DATA_SLAM_TICK, 0);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        // The slam is a transient state, it is never written to the save file.
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
    }

    /** True while the server side slam is running. Clients use it to play the {@code hammer_slam} animation. */
    public boolean isSlamRunning()
    {
        return this.entityData.get(DATA_SLAM_RUNNING);
    }

    /** Ticks since the swing started, {@code 0} on the very first swing tick. */
    public int getSlamTick()
    {
        return this.entityData.get(DATA_SLAM_TICK);
    }

    /**
     * Starts the swing on the server: the synced state and the animation of every client begin on this very
     * tick with tick {@code 0}, so the impact of the animation and the damage tick of the server are the same
     * moment. Called by {@link ChadSteveHammerAttackGoal}.
     */
    void serverStartSlam()
    {
        this.serverSlamTick = 0;
        this.entityData.set(DATA_SLAM_TICK, 0);
        this.entityData.set(DATA_SLAM_RUNNING, true);
    }

    /** Server side only, mirrors the running counter into the synced data after every swing tick. */
    void serverSetSlamTick(int tick)
    {
        this.serverSlamTick = tick;
        this.entityData.set(DATA_SLAM_TICK, tick);
    }

    /** Server side only, ends the swing and clears the synced state, so no attack state is left behind. */
    void serverStopSlam()
    {
        this.serverSlamTick = 0;
        this.entityData.set(DATA_SLAM_TICK, 0);
        this.entityData.set(DATA_SLAM_RUNNING, false);
    }

    /** Server side only, the authoritative tick counter of the running slam. */
    int getServerSlamTick()
    {
        return this.serverSlamTick;
    }

    /**
     * A Chad Steve that was placed with a spawn egg never silently disappears: he is a test subject and not part
     * of the world spawn economy.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide)
        {
            this.clientAnimationStates();
        }
        else
        {
            this.watchdogSlamState();
        }
    }

    /**
     * The last line of defence of the slam state (Phase 9D-hotfix).
     *
     * <p>A swing that stands still keeps the attack goal running forever, and that goal holds {@code Goal.Flag.MOVE}
     * and {@code Goal.Flag.LOOK}, so Chad Steve would stand in place with walk and idle suppressed instead of
     * walking and fighting. The counter of a healthy swing moves every tick, so a counter that has not moved for a
     * whole animation plus a second is cleared here and he simply falls back to walking and idling.
     */
    private void watchdogSlamState()
    {
        if (!this.isSlamRunning())
        {
            this.lastWatchedSlamTick = -1;
            this.slamStallTicks = 0;
            return;
        }

        if (this.serverSlamTick == this.lastWatchedSlamTick)
        {
            if (++this.slamStallTicks > SLAM_WATCHDOG_TICKS)
            {
                this.serverStopSlam();
                this.lastWatchedSlamTick = -1;
                this.slamStallTicks = 0;

                if (DEBUG_SLAM_TIMING)
                {
                    SpiderQueenHungryCraft.LOGGER.warn(
                            "[ChadSteve#{}] the slam state stood still for {} ticks and was cleared", this.getId(),
                            SLAM_WATCHDOG_TICKS);
                }
            }

            return;
        }

        this.lastWatchedSlamTick = this.serverSlamTick;
        this.slamStallTicks = 0;
    }

    /**
     * Playback of the three animations. The slam has priority over walking, walking has priority over idling, so
     * the hammer is never mixed with a walking swing.
     *
     * <p>The slam follows the synced state of the server: the animation is started at the swing tick the server
     * named, so the pose on screen is always on the server side timeline and the impact frame of the animation
     * falls on the very tick the server deals its damage. A client never decides on its own that an attack
     * happens.
     */
    private void clientAnimationStates()
    {
        boolean slamming = this.isSlamRunning();

        if (slamming)
        {
            if (!this.slamAnimationState.isStarted())
            {
                // Rewind by the ticks that already passed, so a late or lost packet can not shift the swing.
                this.slamAnimationState.start(this.tickCount - this.getSlamTick());
                this.clientImpactFrameLogged = false;
            }

            this.logImpactFrameOnce();
        }
        else
        {
            this.slamAnimationState.stop();
            this.clientImpactFrameLogged = false;
        }

        boolean moving = !slamming && this.walkAnimation.isMoving();

        this.walkAnimationState.animateWhen(moving, this.tickCount);
        this.idleAnimationState.animateWhen(!slamming && !moving, this.tickCount);
    }

    /**
     * Diagnostic only (Phase 9B2, part J): one single line for the swing on which the played animation reaches the
     * impact frame, so it can be compared with the server side {@code damage at swingTick=} line of the same log.
     * Both should be in the same second; a gap of a whole second would be a real desync.
     */
    private void logImpactFrameOnce()
    {
        if (!DEBUG_SLAM_TIMING || this.clientImpactFrameLogged)
        {
            return;
        }

        if (this.slamAnimationState.getAccumulatedTime() < HAMMER_IMPACT_TICK * 50L)
        {
            return;
        }

        this.clientImpactFrameLogged = true;
        SpiderQueenHungryCraft.LOGGER.info(
                "[ChadSteve#{}/client] hammer_slam reached the impact frame: accumulated={}ms swingTick={}",
                this.getId(), this.slamAnimationState.getAccumulatedTime(), this.getSlamTick());
    }

    /**
     * The single damage moment of a slam, resolved on the server in {@link #HAMMER_DAMAGE_TICK} (Phase 9B2,
     * parts F, G, I).
     *
     * <p>Every valid target inside the 3x4x5 area in front of him takes one normal melee hit through
     * {@code doHurtTarget} with ATTACK_DAMAGE (25), which keeps armour, the hurt animation, death, the combat
     * log and the damage source completely vanilla. This method is called on exactly one swing tick, and the
     * caller guards it with a boolean, so one swing can never hit twice.
     *
     * @return the number of valid targets that were inside the area
     */
    public int performHammerDamage()
    {
        if (this.level().isClientSide)
        {
            // Damage is only ever decided by the server.
            return 0;
        }

        Vec3 forward = this.horizontalForward();
        List<LivingEntity> hits = this.level().getEntitiesOfClass(LivingEntity.class, this.slamSearchBox(),
                candidate -> this.isAttackableTarget(candidate) && this.isInsideSlamArea(candidate, forward));

        for (LivingEntity hit : hits)
        {
            // A normal melee hit with ATTACK_DAMAGE (25), through the vanilla damage pipeline.
            this.doHurtTarget(hit);
        }

        boolean coreHit = this.damageCellCore();

        if (DEBUG_SLAM_TIMING)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[ChadSteve#{}] damage at swingTick={} (impact frame {}) hitCount={} coreHit={}", this.getId(),
                    this.serverSlamTick, HAMMER_IMPACT_TICK, hits.size(), coreHit);
        }

        return hits.size();
    }

    /**
     * The stomach core of the cell is hit directly instead of through the slam box (Phase 9C, parts H to K).
     *
     * <p>The slam box measures from the centre of a target, and the centre of a core is the middle of a whole ten
     * block cell, so the box would only ever cover it from one exact spot. A core is the wall of the stomach, not a
     * creature standing on the floor, so while the current target is that core it takes its own hit - no matter
     * where inside the cell Chad Steve happens to stand, and no matter that the core has no collision box, is not
     * pickable and can not be seen.
     *
     * <p>The hit goes through {@code core.hurt} with a plain mob attack source, which keeps every existing piece of
     * stomach logic untouched: the visceral armor of the owner mitigates, {@code StomachCoreEvents} plays the fleshy
     * honey sound and the damage link carries the amount on to the queen. Nothing here touches the queen directly
     * and nothing plays a second sound.
     *
     * @return true when the core really lost health from this swing
     */
    private boolean damageCellCore()
    {
        if (!(this.getTarget() instanceof StomachCoreEntity core) || !this.isCellCore(core))
        {
            return false;
        }

        DamageSource source = this.damageSources().mobAttack(this);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

        return core.hurt(source, damage);
    }

    /**
     * One burst of dust and one anvil landing per slam, at the impact spot, played once and by the server only
     * (Phase 9B2, parts D, G, H). No real explosion, no block damage, no fire and no knockback.
     */
    public void playImpactFeedback()
    {
        Vec3 impact = this.impactPosition(this.horizontalForward());

        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.POOF, impact.x, impact.y + 0.15D, impact.z, 12, 0.6D, 0.2D, 0.6D,
                0.05D);
        serverLevel.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 0.1D, impact.z, 8, 0.8D, 0.15D, 0.8D,
                0.02D);
        this.level().playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE,
                HAMMER_SOUND_VOLUME, HAMMER_SOUND_PITCH);

        if (DEBUG_SLAM_TIMING)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[ChadSteve#{}] impact feedback at swingTick={} (impact frame {}) x={} y={} z={}", this.getId(),
                    this.serverSlamTick, HAMMER_IMPACT_TICK, String.format("%.2f", impact.x),
                    String.format("%.2f", impact.y), String.format("%.2f", impact.z));
        }
    }

    /** The horizontal direction he faces, length one. Yaw only, so a target above or below him still counts. */
    private Vec3 horizontalForward()
    {
        return Vec3.directionFromRotation(0.0F, this.getYRot());
    }

    /**
     * Turns the whole entity - the yaw the hammer is aimed with <b>and</b> the body - toward a point on the
     * horizontal plane, at a limited speed (Phase 9B3).
     *
     * <p>This method exists because vanilla <b>never</b> turns a standing mob: {@code LookControl} only moves the
     * head, {@code BodyRotationControl} only follows the walk direction and the entity yaw is written by the
     * movement control alone. The slam of this mod measures its 3x4x5 area in that entity yaw
     * ({@link #horizontalForward()}), so without an explicit turn a victim standing behind or beside him could
     * never be hit, however often he swung. The same reason makes the visible body follow the swing, because the
     * renderer draws the body from {@code yBodyRot}.
     *
     * @param maxDegreesPerTick how far he may turn in this one tick, so he pivots instead of snapping around
     */
    void faceTowards(double x, double z, float maxDegreesPerTick)
    {
        double dx = x - this.getX();
        double dz = z - this.getZ();

        if (dx * dx + dz * dz < 1.0E-4D)
        {
            // Standing on top of the victim: there is no direction left to turn to.
            return;
        }

        float wantedYaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float step = Mth.clamp(Mth.degreesDifference(this.getYRot(), wantedYaw), -maxDegreesPerTick,
                maxDegreesPerTick);
        float yaw = Mth.wrapDegrees(this.getYRot() + step);

        this.setYRot(yaw);
        this.setYBodyRot(yaw);
    }

    /**
     * @return true when the slam area of his current facing really covers that victim, which is the question the
     *         attack goal asks before it starts a swing (Phase 9B3).
     */
    boolean isTargetInsideHammerArea(LivingEntity candidate)
    {
        return this.isInsideSlamArea(candidate, this.horizontalForward());
    }

    /** The spot in front of his feet where the hammer head lands: 2 blocks forward, just above the ground. */
    private Vec3 impactPosition(Vec3 forward)
    {
        Vec3 position = this.position();
        return new Vec3(position.x + forward.x * HAMMER_IMPACT_FORWARD_OFFSET, this.getY() + 0.1D,
                position.z + forward.z * HAMMER_IMPACT_FORWARD_OFFSET);
    }

    /** A rough box around him, the exact test is the local space filter of {@link #isInsideSlamArea}. */
    private AABB slamSearchBox()
    {
        return this.getBoundingBox().inflate(HAMMER_AOE_SEARCH_INFLATE, HAMMER_AOE_HEIGHT,
                HAMMER_AOE_SEARCH_INFLATE);
    }

    /**
     * The 3x4x5 area in front of him: up to {@link #HAMMER_AOE_FORWARD} blocks forward, up to
     * {@link #HAMMER_AOE_HALF_WIDTH} blocks to either side, and between his feet and
     * {@link #HAMMER_AOE_HEIGHT} blocks above them. Everything is measured in his own facing, never in world
     * axis, so the area turns with him.
     */
    private boolean isInsideSlamArea(LivingEntity candidate, Vec3 forward)
    {
        double dx = candidate.getX() - this.getX();
        double dz = candidate.getZ() - this.getZ();

        double localForward = dx * forward.x + dz * forward.z;

        if (localForward < 0.0D || localForward > HAMMER_AOE_FORWARD)
        {
            return false;
        }

        // Perpendicular to the forward vector in the XZ plane.
        double localRight = dx * -forward.z + dz * forward.x;

        if (Math.abs(localRight) > HAMMER_AOE_HALF_WIDTH)
        {
            return false;
        }

        AABB box = candidate.getBoundingBox();
        return box.maxY > this.getY() && box.minY < this.getY() + HAMMER_AOE_HEIGHT;
    }

    /**
     * One burst of dust and one anvil landing per slam, at the impact spot, played once and by the server only
     * (Phase 9B, part H). No real explosion, no block damage, no fire and no knockback.
     */
    private void playImpactFeedback(Vec3 impact)
    {
        if (!(this.level() instanceof ServerLevel serverLevel))
        {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.EXPLOSION, impact.x, impact.y, impact.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        serverLevel.sendParticles(ParticleTypes.POOF, impact.x, impact.y + 0.15D, impact.z, 12, 0.6D, 0.2D, 0.6D,
                0.05D);
        serverLevel.sendParticles(ParticleTypes.CLOUD, impact.x, impact.y + 0.1D, impact.z, 8, 0.8D, 0.15D, 0.8D,
                0.02D);
        this.level().playSound(null, impact.x, impact.y, impact.z, SoundEvents.ANVIL_LAND, SoundSource.HOSTILE,
                HAMMER_SOUND_VOLUME, HAMMER_SOUND_PITCH);
    }
}
