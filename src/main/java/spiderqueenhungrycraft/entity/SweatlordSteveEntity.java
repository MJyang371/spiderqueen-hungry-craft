package spiderqueenhungrycraft.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ToolActions;
import spiderqueenhungrycraft.faction.OwnFactionTargeting;
import spiderqueenhungrycraft.human.TranscendentHuman;
import spiderqueenhungrycraft.sweatlord.MovingTargetBallistics;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatBrain;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatGoal;
import spiderqueenhungrycraft.sweatlord.SweatlordCombatState;
import spiderqueenhungrycraft.sweatlord.VirtualLoadout;

/**
 * Sweatlord Steve, the first player-like human of this mod (Sweatlord round).
 *
 * <p>He is a human like any other: filed into {@code human_faction}, a {@link TranscendentHuman} (never part of the
 * ordinary surface population, never placed by the surface spawner) and an {@link OwnFactionTargeting} body (he owns
 * his targeting, so the faction system injects nothing into him). What makes him special is that his fight is a
 * <b>player's</b> fight: a real bow with a real draw, eggs in a burst, a trident, an ender pearl used as a jump, a
 * shield that really covers his front against arrows and web, a sword with player-like crits and an axe against a
 * raised shield.
 *
 * <p><b>He is not an overpowered body.</b> His strength is his skill selection, his rhythm, his gear switching, his
 * defense and his pearls - not a huge health pool. His health is a little above a player's and nothing more.
 *
 * <p>The fight lives in {@link SweatlordCombatBrain} and its skills; this class is the body: attributes, the synced
 * debug state, the gear that is really in its hands and the one goal that drives the brain.
 */
public class SweatlordSteveEntity extends PathfinderMob implements TranscendentHuman, OwnFactionTargeting
{
    /** A little more than a player, nowhere near a boss: this round refuses a 300 health cheater. */
    public static final double MAX_HEALTH = 40.0D;

    /** The bare handed damage of this round: the real damage comes from the weapon in the hand. */
    public static final double ATTACK_DAMAGE = 1.0D;

    /** Faster than a player's walk, which is what makes closing the distance work at all. */
    public static final double MOVEMENT_SPEED = 0.32D;

    /** He sees as far as his fight needs: the bow range plus a margin. */
    public static final double FOLLOW_RANGE = 48.0D;

    /** A player's reach, the number every melee decision of this entity is measured against. */
    public static final double MELEE_REACH = 3.0D;

    public static final float COLLISION_WIDTH = 0.6F;
    public static final float COLLISION_HEIGHT = 1.8F;

    /** The vanilla wide skins of the player model, the same nine the ordinary humans wear. */
    public static final int SKIN_VARIANTS = 9;

    /** The slots he may never drop: his gear is an ability, not loot. */
    private static final EquipmentSlot[] DROP_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST,
            EquipmentSlot.LEGS, EquipmentSlot.FEET };

    private static final EntityDataAccessor<Integer> DATA_SKIN_VARIANT =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.INT);

    /** The debug state of the brain, synced so any debug tool can read it without a special packet. */
    private static final EntityDataAccessor<String> DATA_STATE =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<String> DATA_SKILL =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<Boolean> DATA_ENCOUNTER =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_ENC_GAPPLE_USED =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> DATA_PROJECTILE_THREAT =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<String> DATA_PEARL_MODE =
            SynchedEntityData.defineId(SweatlordSteveEntity.class, EntityDataSerializers.STRING);

    private final VirtualLoadout loadout = new VirtualLoadout(this);

    private final SweatlordCombatBrain brain = new SweatlordCombatBrain(this);

    /** How many critical hits this body has landed, kept for the debug output and the tests. */
    private int crits;

    /** The reason of the last state transition, kept for the debug output. */
    private String lastTransitionReason = "spawned";

    /**
     * The own projectiles that may still be nudged in flight (see
     * {@link spiderqueenhungrycraft.sweatlord.MovingTargetBallistics.GuidedShot}).
     *
     * <p>Server side only, and never saved: a shot in the air does not survive a reload as a guided shot, which is
     * exactly right - the correction is a tick by tick thing of the flight, not a property of the weapon.
     */
    private final List<MovingTargetBallistics.GuidedShot> guidedShots = new ArrayList<>();

    public SweatlordSteveEntity(EntityType<? extends SweatlordSteveEntity> entityType, Level level)
    {
        super(entityType, level);
        this.xpReward = 5;

        for (EquipmentSlot slot : DROP_SLOTS)
        {
            this.setDropChance(slot, 0.0F);
        }
    }

    /** The attributes of this body: a human, not a boss. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                // ATTACK_DAMAGE is not part of the plain pathfinder set, so it has to be added here or every swing
                // would deal nothing at all.
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.1D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN_VARIANT, 0);
        this.entityData.define(DATA_STATE, SweatlordCombatState.IDLE.id());
        this.entityData.define(DATA_SKILL, "none");
        this.entityData.define(DATA_ENCOUNTER, false);
        this.entityData.define(DATA_ENC_GAPPLE_USED, false);
        this.entityData.define(DATA_PROJECTILE_THREAT, false);
        this.entityData.define(DATA_PEARL_MODE, "none");
    }

    /**
     * One goal owns the whole fight, exactly like the ordinary human's brain does.
     *
     * <p>There is no target goal on purpose: this body implements {@link OwnFactionTargeting}, and the brain asks the
     * shared faction rules itself ({@code CombatContext#isValidEnemy}), so an encounter and a target are one decision
     * and can never disagree with each other.
     */
    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SweatlordCombatGoal(this));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
    }

    /** {@link MobType#UNDEFINED}: he is a human, not undead, not an arthropod, not an illager. */
    @Override
    public MobType getMobType()
    {
        return MobType.UNDEFINED;
    }

    /**
     * Advances the swing state of the game on this body (animation round).
     *
     * <p>{@code LivingEntity#updateSwingTime} - the vanilla method that turns {@code swinging}/{@code swingTime} into
     * the {@code attackAnim} a renderer draws - is only ever called by {@code Monster#aiStep} and
     * {@code Player#serverAiStep} in this game. A {@code PathfinderMob} that is not a monster, which this body is,
     * therefore never advanced it: the swing state froze on the tick it was set, no client could ever draw the swing of
     * the game, and an earlier round answered that with a cut of its own on a hard coded arm - the sword hand looked
     * unused and the shield arm looked like the one attacking. With this call the attack is the ordinary swing of the
     * game again.
     */
    @Override
    public void aiStep()
    {
        this.updateSwingTime();
        super.aiStep();
    }

    @Override
    public void tick()
    {
        super.tick();

        // The swing of the game on this body (animation round): LivingEntity#updateSwingTime is only ever called by
        // Monster#aiStep and Player#serverAiStep in the game, so a PathfinderMob that is not a monster - which this body
        // is - would keep its swing state frozen on the tick it was set: swinging stays true, attackAnim stays 0, and
        // no client can ever draw a swing. That is the reason the earlier round drew a cut of its own, on a hard coded
        // arm - which is why the sword hand looked unused and the shield arm looked like the attacking one.
        this.updateSwingTime();

        if (!this.level().isClientSide)
        {
            // The tiny in-flight correction of the shots of this body, ticked here because a projectile that is in the
            // air is not owned by a skill any more.
            MovingTargetBallistics.tickGuidance(this);

            // And the ranged rounds of this body, ticked here for the same reason: a round is judged on the projectiles
            // it fired, and those outlive the skill that fired them.
            this.brain.rangedRounds().tick(this);
        }
    }

    /**
     * The one exception of this body in the shield rules of the game.
     *
     * <p>The vanilla question is asked first and answers everything it always did: a shield that has been up for five
     * ticks blocks, a hit in the back does not, {@code BYPASSES_SHIELD} damage goes through. Only when the game says no
     * does the instant projectile guard of this body get a say - and it says yes for exactly one situation: a
     * <b>projectile</b> that is really on a collision course, arriving at the half of the world this body is facing,
     * while he really holds a shield up because of it. That is what lets him answer a shot that arrives before the
     * wind up is over, which no other body of the game can do.
     */
    @Override
    public boolean isDamageSourceBlocked(DamageSource source)
    {
        return super.isDamageSourceBlocked(source) || this.brain.instantGuard().allowsInstantBlock(this, source);
    }

    /**
     * @return true while the instant projectile guard of this body is answering a real shot with a real shield, so a
     *         block is allowed from the tick the shield came up (see {@code ShieldProjectileInteraction#holdsUpAShield}).
     */
    public boolean reflexGuardReady()
    {
        return this.brain.instantGuard().isActive() && PlayerLikeActions.holdsShieldInUse(this);
    }

    /** @return the own projectiles that may still be nudged in flight, oldest first. */
    public List<MovingTargetBallistics.GuidedShot> guidedShots()
    {
        return this.guidedShots;
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag)
    {
        this.setSkinVariant(this.getRandom().nextInt(SKIN_VARIANTS));
        this.loadout.equipCombatGrip();

        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("SkinVariant", this.skinVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setSkinVariant(tag.getInt("SkinVariant"));
    }

    /** He is a test subject and a named fighter, never part of the world spawn economy: he never despawns. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    /** Nothing of his gear is loot; the round only forbids dropping duplicated items on death. */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer)
    {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);
    }

    /** @return the ability stock of this body: what it may really put into its hands. */
    public VirtualLoadout loadout()
    {
        return this.loadout;
    }

    /** @return the fight of this body. */
    public SweatlordCombatBrain brain()
    {
        return this.brain;
    }

    /** @return the real reach of his weapon, the number every melee decision is measured against. */
    public double attackReach()
    {
        return MELEE_REACH;
    }

    /**
     * The attack damage bonus of the weapon in the main hand.
     *
     * <p>{@code Mob#doHurtTarget} only ever uses the attribute, so a mob with a real diamond sword would still hit
     * like a bare fist. The damage of a swing is therefore the attribute plus this bonus, exactly like the ordinary
     * human of this mod does it.
     */
    public float heldWeaponDamageBonus()
    {
        float bonus = 0.0F;

        for (var modifier : this.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE))
        {
            bonus += (float) modifier.getAmount();
        }

        return bonus;
    }

    /**
     * Swings the main arm on every client as well, so each melee attack is visible like a player's.
     *
     * <p>This is the swing of the game and nothing else (animation round): the swing state is reset first, so the
     * vanilla animation packet is really sent even when another swing was still running - the vanilla code silently
     * swallows a swing that comes too early ({@code LivingEntity#swing}) - and the game then animates the arm that
     * really holds the weapon, the one it picks from {@code swingingArm} and {@code getMainArm()}. The private swing
     * counter of the earlier round is gone: it drew a second, longer cut on a hard coded arm, which is why this body
     * with a sword in the main hand and a shield in the offhand looked as if it attacked with the shield hand.
     */
    public void swingMainHand()
    {
        this.swinging = false;
        this.swingTime = 0;
        this.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * @return true while this body is really holding its shield up in front, judged from <b>synced state only</b>: the
     *         using flag, the hand it is used in and the offhand item itself.
     *
     * <p>The renderer asks this for the raised shield pose, and it is deliberately not {@code LivingEntity#isBlocking}:
     * that one reads the local {@code useItem} copy - which a client only ever guesses from whatever the hand held when
     * the synced using flag arrived - and it stays false for the first five ticks of a guard. The <b>rule</b> of the
     * game is untouched: this is only the question of the pose, and the instant guard of this body keeps asking the
     * vanilla one where blocking really matters.
     */
    public boolean isShieldGuardUp()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND
                && this.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /** Counts a landed critical hit, for the debug output and the tests. */
    public void recordCrit(boolean crit)
    {
        if (crit)
        {
            ++this.crits;
        }
    }

    /** @return how many critical hits this body has landed since it was spawned. */
    public int crits()
    {
        return this.crits;
    }

    /**
     * @return true while this body is really drawing a bow: the using flag, the used hand and the main hand item, all
     *         three of them synced, so the model of this mod can trust the answer the same way the ordinary human's
     *         model does.
     */
    public boolean isDrawingBow()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.MAIN_HAND
                && this.getMainHandItem().is(Items.BOW);
    }

    /** @return the index of the wide player skin this body wears, synced to every client. */
    public int skinVariant()
    {
        return this.entityData.get(DATA_SKIN_VARIANT);
    }

    public void setSkinVariant(int variant)
    {
        this.entityData.set(DATA_SKIN_VARIANT, Mth.clamp(variant, 0, SKIN_VARIANTS - 1));
    }

    /**
     * Publishes the debug state of the brain to every client. It is called by the brain, and only when something
     * really changed, so a debug overlay can read the state of a Sweatlord without a special packet of its own.
     */
    public void syncDebugState(SweatlordCombatState state, String skill, boolean encounter,
            boolean enchantendGappleUsed, boolean projectileThreat, String pearlMode)
    {
        this.entityData.set(DATA_STATE, state.id());
        this.entityData.set(DATA_SKILL, skill);
        this.entityData.set(DATA_ENCOUNTER, encounter);
        this.entityData.set(DATA_ENC_GAPPLE_USED, enchantendGappleUsed);
        this.entityData.set(DATA_PROJECTILE_THREAT, projectileThreat);
        this.entityData.set(DATA_PEARL_MODE, pearlMode);
    }

    public String debugState()
    {
        return this.entityData.get(DATA_STATE);
    }

    public String debugSkill()
    {
        return this.entityData.get(DATA_SKILL);
    }

    public boolean debugEncounter()
    {
        return this.entityData.get(DATA_ENCOUNTER);
    }

    public boolean debugEnchantedGappleUsed()
    {
        return this.entityData.get(DATA_ENC_GAPPLE_USED);
    }

    public boolean debugProjectileThreat()
    {
        return this.entityData.get(DATA_PROJECTILE_THREAT);
    }

    public String debugPearlMode()
    {
        return this.entityData.get(DATA_PEARL_MODE);
    }

    /** Remembers why the last state transition happened, for {@link #getDebugState()}. */
    public void markTransition(String reason)
    {
        this.lastTransitionReason = reason;
    }

    public String lastTransitionReason()
    {
        return this.lastTransitionReason;
    }

    /**
     * @return the whole debug picture of this body in one readable block: the state, the running skill, the encounter
     *         and every cooldown of the brain. This is the answer to "why is he standing there", which is the one
     *         question a state machine of this size can not answer from the outside by itself.
     */
    public String getDebugState()
    {
        return "Sweatlord[" + this.getId() + "] state=" + this.debugState()
                + " skill=" + this.debugSkill()
                + " encounter=" + this.debugEncounter()
                + " encGappleUsed=" + this.debugEnchantedGappleUsed()
                + " projectileThreat=" + this.debugProjectileThreat()
                + " pearlMode=" + this.debugPearlMode()
                + " main=" + this.getMainHandItem().getItem()
                + " off=" + this.getOffhandItem().getItem()
                + " hp=" + String.format(java.util.Locale.ROOT, "%.1f", this.getHealth()) + "/" + this.getMaxHealth()
                + " crits=" + this.crits
                + " lastTransition=" + this.lastTransitionReason
                + " lastPearlRejection=" + this.brain.lastPearlRejection()
                + " target=" + this.getTarget()
                + " targetDistance=" + (this.getTarget() == null ? "-"
                        : String.format(java.util.Locale.ROOT, "%.1f", this.distanceTo(this.getTarget())))
                + " usingItem=" + this.isUsingItem()
                + " blocking=" + this.isBlocking()
                + " guard=" + this.brain.instantGuard().describe()
                + " ranged=" + this.brain.rangedRounds().describe()
                + " envDanger=" + spiderqueenhungrycraft.sweatlord.EnvironmentSafety.isInDangerousFluid(this)
                + " cooldowns={" + this.brain.describeCooldowns() + "}";
    }
}
