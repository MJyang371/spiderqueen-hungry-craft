package spiderqueenhungrycraft.entity;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.ToolActions;
import spiderqueenhungrycraft.ai.HumanCombatBrain;
import spiderqueenhungrycraft.ai.HumanCombatGoal;
import spiderqueenhungrycraft.ai.HumanoidFighter;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.loadout.HumanLoadout;
import spiderqueenhungrycraft.loadout.HumanLoadoutManager;
import spiderqueenhungrycraft.loadout.HumanLoadoutTable;
import spiderqueenhungrycraft.loadout.HumanTier;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The standard human combatant of the Hungry Craft war (Phase HF0A-GAMEPASS1).
 *
 * <p>The measuring stick the later human units are built on: a plain {@link PathfinderMob} with a fixed loadout
 * (full iron armour, iron sword, bow, shield, three golden apples) fighting through one single goal that owns his
 * navigation, look control, both hands and every attack ({@link HumanCombatGoal}, {@link HumanCombatBrain}).
 *
 * <p>He is not a {@code Monster}, does not implement {@code Enemy} and his mob type is {@link MobType#UNDEFINED}.
 * His alignment is the temporary one of this round, see {@link HumanCombatantTargeting}: he is HUMAN and hunts
 * queens (players with the queen flag), never a normal player, villager, golem, animal or his own kind.
 *
 * <p>Nothing spawns him naturally: spawn egg and {@code /summon} are the only ways in.
 */
public class HumanCombatantEntity extends PathfinderMob implements HumanoidFighter
{
    /** 20 health, exactly a player's worth. The armour of the loadout is what makes him tougher. */
    public static final double MAX_HEALTH = 20.0D;

    /**
     * The bare handed damage of this round, deliberately as low as one: the real melee damage comes from the iron
     * sword, because {@link #performMeleeAttack(LivingEntity)} adds the attack damage bonus of the held weapon.
     */
    public static final double ATTACK_DAMAGE = 1.0D;

    /** A slightly faster walk than a zombie, so he can close a gap and still be outrun by a sprinting player. */
    public static final double MOVEMENT_SPEED = 0.26D;

    /** The range at which he notices a queen. */
    public static final double FOLLOW_RANGE = 32.0D;

    /** Player sized collision box, so he fits everywhere a player fits. */
    public static final float COLLISION_WIDTH = 0.6F;
    public static final float COLLISION_HEIGHT = 1.8F;

    /** The fixed loadout of this round: three golden apples, no more and no fewer. */
    public static final int STARTING_GOLDEN_APPLES = 3;

    /**
     * A safety cap for the carried medicine of a tiered human (Phase HF0C). The real limit is the
     * {@code max_count} of the json table, this one only keeps a broken table from handing out a hundred apples.
     */
    public static final int MAX_GOLDEN_APPLES = 8;

    /** The slots the equipment of a dead human is rolled from: the four armour slots and the offhand shield. */
    private static final EquipmentSlot[] DROP_SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

    /**
     * How many wide default skins of the vanilla game this entity can wear. The list itself lives on the client, so
     * the server only ever stores a small index - and it is the wide model that is used, see the renderer.
     */
    public static final int SKIN_VARIANTS = 9;

    private static final String TAG_GOLDEN_APPLES = "GoldenApples";
    private static final String TAG_SKIN_VARIANT = "SkinVariant";
    private static final String TAG_TIER = "Tier";
    private static final String TAG_MELEE_WEAPON = "MeleeWeapon";
    private static final String TAG_BOW_WEAPON = "BowWeapon";
    private static final String TAG_LOADOUT_GENERATED = "LoadoutGenerated";
    private static final String TAG_SPAWN_ORIGIN = "SpawnOrigin";

    /** The skin of this combatant. Synced, so no client ever sees a different face than the others. */
    private static final EntityDataAccessor<Integer> DATA_SKIN_VARIANT =
            SynchedEntityData.defineId(HumanCombatantEntity.class, EntityDataSerializers.INT);

    /** The one brain of this entity, the state machine of {@link HumanCombatGoal}. */
    private final HumanCombatBrain brain = new HumanCombatBrain();

    /** How many golden apples he still carries. It is persisted with him, it is not an inventory. */
    private int goldenApples = STARTING_GOLDEN_APPLES;

    /**
     * The blade of this human (Phase HF0C). It lives beside his slots and is only put into the main hand while he
     * actually fights with it, so the same entity can hold a bow in the hand and a sword in the belt.
     */
    private ItemStack meleeWeapon = ItemStack.EMPTY;

    /** The bow of this human, handled exactly like the blade: stored here, drawn into the hand when needed. */
    private ItemStack bowWeapon = ItemStack.EMPTY;

    /** The tier this human was generated as, as the json key. Empty until the loadout was rolled. */
    private String tierId = "";

    /** True once the loadout of this human was rolled. It is what keeps a reload from dressing him again. */
    private boolean loadoutGenerated;

    /**
     * Where this human came from (Phase HF0D). It decides whether he may despawn and it is saved with him, so the
     * question can still be answered after a reload.
     */
    private HumanSpawnOrigin spawnOrigin = HumanSpawnOrigin.UNKNOWN;

    public HumanCombatantEntity(EntityType<? extends HumanCombatantEntity> entityType, Level level)
    {
        super(entityType, level);
        this.xpReward = 0;
    }

    /** The attributes of this round. ARMOR is deliberately not written here, the iron armour provides it. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /** The one synced value of this entity: the default skin it wears. */
    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_SKIN_VARIANT, 0);
    }

    /**
     * One core goal owns the whole fight (Phase HF0A): navigation, look control, the main hand switching between the
     * sword and the bow, the shield in the offhand, the golden apples and the melee attacks. Stacking several vanilla
     * goals would only make them fight over the same controls.
     */
    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new HumanCombatGoal(this));

        // The post of the pack aggro round: a human who was dragged out of his ground walks back to it, and a human
        // standing in peace outside his old post takes a new one. It only ever moves him while no fight is on.
        this.goalSelector.addGoal(2, new HumanPostGoal(this));

        // Only reached while the fight goal is not running, so an idle combatant walks around like a villager.
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // The two target rules of this round (Phase HF0B), highest priority first:
        //   1: a player who carries the queen flag,
        //   2: the monster faction, which is what a human combatant is for.
        this.targetSelector.addGoal(1, new HumanCombatantTargetGoal(this));

        // A pack author can take the monster hunt away with human_combatant_ignores_monsters, everything else about
        // the combatant stays exactly the same.
        if (!HungryCraftConfig.humanCombatantIgnoresMonsters())
        {
            this.targetSelector.addGoal(2, new HumanCombatantMonsterTargetGoal(this));
        }
    }

    /** @return the brain that runs the fight of this entity. */
    public HumanCombatBrain brain()
    {
        return this.brain;
    }

    /**
     * {@link MobType#UNDEFINED}: he is a human. Not undead, not an arthropod, not an illager, so nothing in the game
     * that checks a mob type reacts to him differently than it would to any ordinary creature.
     */
    @Override
    public MobType getMobType()
    {
        return MobType.UNDEFINED;
    }

    /**
     * The loadout of a freshly spawned human.
     *
     * <p>A tiered human of Phase HF0C rolls his gear from the json table exactly once, and only when he was not
     * dressed before. The hidden test entity of Phase HF0A keeps its fixed iron kit.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
            @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag)
    {
        this.setSkinVariant(this.getRandom().nextInt(SKIN_VARIANTS));
        this.resolveSpawnOrigin(reason);

        if (this.isPreparedHuman())
        {
            this.rollLoadoutOnce();
        }
        else
        {
            this.equipStandardLoadout();
        }

        return super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
    }

    /**
     * Fills in the spawn origin from the reason of the spawn, but only when nothing set it before. A spawner of this
     * mod always names its origin first, this is the safety net for every other way an entity can appear.
     */
    private void resolveSpawnOrigin(MobSpawnType reason)
    {
        if (this.spawnOrigin != HumanSpawnOrigin.UNKNOWN)
        {
            return;
        }

        if (reason == MobSpawnType.SPAWN_EGG)
        {
            this.spawnOrigin = HumanSpawnOrigin.SPAWN_EGG;
        }
        else if (reason == MobSpawnType.COMMAND)
        {
            this.spawnOrigin = HumanSpawnOrigin.COMMAND;
        }
        else if (reason == MobSpawnType.STRUCTURE)
        {
            this.spawnOrigin = HumanSpawnOrigin.VILLAGE_GARRISON;
        }
        else if (reason == MobSpawnType.NATURAL)
        {
            this.spawnOrigin = HumanSpawnOrigin.NATURAL_SURFACE;
        }
    }

    /**
     * Rolls the tier loadout, unless this human already carries one. A human that was loaded from the save file, came
     * through a dimension change or was placed with {@code /summon} keeps exactly the gear he had.
     */
    public void rollLoadoutOnce()
    {
        if (this.loadoutGenerated)
        {
            return;
        }

        HumanTier tier = this.tier();
        HumanLoadout loadout = HumanLoadoutManager.roll(tier, this.getRandom());

        if (loadout == null)
        {
            // No table was read yet: this human keeps the built-in kit instead of walking around naked.
            this.equipStandardLoadout();
            this.loadoutGenerated = true;
            return;
        }

        this.applyTierLoadout(tier, loadout);
    }

    /**
     * The loadout of this round, always the same: iron helmet, chestplate, leggings and boots, an iron sword in the
     * main hand, a shield in the offhand and three golden apples carried along.
     */
    public void equipStandardLoadout()
    {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        // The blade and the bow are kept beside the slots, exactly like the rolled loadout does it, so the fight goal
        // treats both kinds of human the same way.
        this.meleeWeapon = new ItemStack(Items.IRON_SWORD);
        this.bowWeapon = new ItemStack(Items.BOW);
        this.equipMeleeWeapon();
        this.goldenApples = STARTING_GOLDEN_APPLES;
    }

    /** @return true while a shield is still in the offhand. */
    public boolean hasShield()
    {
        return this.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD);
    }

    /** @return true when this human owns a bow, whether it is in his hand right now or not. */
    public boolean hasBow()
    {
        return !this.bowWeapon.isEmpty();
    }

    /** @return true when this human owns a blade. An unarmed human has to punch or keep his distance. */
    public boolean hasMeleeWeapon()
    {
        return !this.meleeWeapon.isEmpty();
    }

    /**
     * @return true when he should walk into melee range at all. A bowman without a blade does not: he keeps the
     *         distance and shoots, that is his whole loadout.
     */
    public boolean canFightInMelee()
    {
        return this.hasMeleeWeapon() || !this.hasBow();
    }

    /** @return the stack this human fights with in melee, may be empty. */
    public ItemStack meleeWeapon()
    {
        return this.meleeWeapon;
    }

    /** @return the bow this human owns, may be empty. */
    public ItemStack bowWeapon()
    {
        return this.bowWeapon;
    }

    /**
     * @return true while this human is really drawing a bow, which is the question the model of this mod asks before
     *         it raises the arms.
     *
     * <p><b>Why this method exists (bow animation fix).</b> A model that decides "is he drawing" from
     * {@code getUseItem()} is wrong for a human of this mod, and it is wrong in a way that only shows up on the
     * client: a human swaps the bow into his main hand in the very tick he starts using it, and the game sends the
     * "using an item" flag <b>before</b> it sends the new equipment (see {@code ServerEntity#sendChanges} /
     * {@code EntityType#updateInterval}). The client then takes its local {@code useItem} from the hand it still sees
     * - the sword - and because that copy does not match the bow it later receives, vanilla stops the local use state
     * and never adopts the bow for that draw. The server draws and shoots normally the whole time; only the
     * animation of that one draw is lost.
     *
     * <p>The two states that are reliably synced are the using flag and the hand it is used in (both are the very
     * same {@code SynchedEntityData} the game uses for the flag), and the main hand item itself is synced equipment.
     * So the question is answered from those three, never from the unsynced copy - and a human that switches from the
     * sword to the bow now shows the draw on every client, on the very first arrow as well.
     *
     * <p>Nothing of the shot depends on this: the draw counter, the shot timing and the arrow are owned by the fight
     * of {@code HumanCombatBrain} on the server, which is untouched by this method.
     */
    public boolean isDrawingBow()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.MAIN_HAND
                && this.getMainHandItem().is(Items.BOW);
    }

    /**
     * The arrow of a human: the ordinary arrow of the game, owned by him. Nothing of the shot itself is decided here -
     * the aim, the power and the crit come from the shared fight of {@link HumanCombatGoal}/{@link HumanCombatBrain}.
     */
    @Override
    public AbstractArrow newArrow()
    {
        return new Arrow(this.level(), this);
    }

    /**
     * The target rule of a human is the one of this round: a Spider Queen player and the monster faction, and nothing
     * else - exactly what {@link HumanCombatantTargeting} has always answered for him.
     */
    @Override
    public boolean mayFight(LivingEntity target)
    {
        return HumanCombatantTargeting.isTargetStillAllowed(this, target);
    }

    /** @return the tier of this human: the saved one when there is one, otherwise the one its entity type means. */
    public HumanTier tier()
    {
        HumanTier saved = HumanTier.byId(this.tierId);

        if (saved != null)
        {
            return saved;
        }

        if (this.getType() == HungryCraftEntities.MID_PREPARED_HUMAN.get())
        {
            return HumanTier.MID;
        }

        if (this.getType() == HungryCraftEntities.HIGH_PREPARED_HUMAN.get())
        {
            return HumanTier.HIGH;
        }

        return HumanTier.LOW;
    }

    /** @return true when this entity is one of the three tiered humans of Phase HF0C. */
    public boolean isPreparedHuman()
    {
        return this.getType() != HungryCraftEntities.HUMAN_COMBATANT.get();
    }

    /** @return true once his loadout was rolled, which is what keeps a reload from dressing him again. */
    public boolean isLoadoutGenerated()
    {
        return this.loadoutGenerated;
    }

    /** @return where this human came from, used for the despawn rule and for debug output. */
    public HumanSpawnOrigin spawnOrigin()
    {
        return this.spawnOrigin;
    }

    /** Writes the spawn origin before the human is placed in the world, see {@code HumanSpawnFactory}. */
    public void setSpawnOrigin(HumanSpawnOrigin origin)
    {
        this.spawnOrigin = origin == null ? HumanSpawnOrigin.UNKNOWN : origin;
    }

    /**
     * Puts a rolled loadout on this human (Phase HF0C).
     *
     * <p>Armour and the shield go into their slots, the blade and the bow are kept beside the slots and only drawn
     * into the hand while they are used, and the golden apples become his carried medicine. The vanilla drop chances
     * are switched off here, because this phase rolls the equipment of its dead humans itself.
     */
    public void applyTierLoadout(HumanTier tier, HumanLoadout loadout)
    {
        this.tierId = tier.id();
        this.loadoutGenerated = true;

        for (Map.Entry<EquipmentSlot, ItemStack> entry : loadout.armor().entrySet())
        {
            this.setItemSlot(entry.getKey(), entry.getValue());
        }

        this.setItemSlot(EquipmentSlot.OFFHAND, loadout.shield());
        this.meleeWeapon = loadout.meleeWeapon();
        this.bowWeapon = loadout.bow();
        this.goldenApples = Mth.clamp(loadout.goldenApples(), 0, MAX_GOLDEN_APPLES);

        // The blade goes into the hand, a human without a blade holds his bow, a human with neither is empty handed.
        if (!this.meleeWeapon.isEmpty())
        {
            this.equipMeleeWeapon();
        }
        else if (!this.bowWeapon.isEmpty())
        {
            this.equipBow();
        }
        else
        {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }

        for (EquipmentSlot slot : DROP_SLOTS)
        {
            this.setDropChance(slot, 0.0F);
        }
    }

    /** @return the index of the default skin this combatant wears, synced to every client. */
    public int skinVariant()
    {
        return this.entityData.get(DATA_SKIN_VARIANT);
    }

    /** Sets the skin variant, clamped to the variants that exist. */
    public void setSkinVariant(int variant)
    {
        this.entityData.set(DATA_SKIN_VARIANT, Mth.clamp(variant, 0, SKIN_VARIANTS - 1));
    }

    /** @return how many golden apples are left, never negative. */
    public int goldenAppleCount()
    {
        return this.goldenApples;
    }

    /** Takes one golden apple out of the carried stock. @return false when there was none left. */
    public boolean consumeGoldenApple()
    {
        if (this.goldenApples <= 0)
        {
            return false;
        }

        --this.goldenApples;
        return true;
    }

    /**
     * One melee swing with whatever is in his hand.
     *
     * <p>{@code Mob#doHurtTarget} only ever uses the attack damage attribute, so a mob holding a real iron sword
     * would still hit like a bare fist. The damage of this round is therefore the attribute plus the attack damage
     * bonus the held weapon declares, which lands an iron sword swing on the six damage of a player.
     */
    public boolean performMeleeAttack(LivingEntity target)
    {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) + this.heldWeaponDamageBonus();
        boolean hurt = target.hurt(this.damageSources().mobAttack(this), damage);

        if (hurt)
        {
            float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
            target.knockback(0.4D, Mth.sin(yaw), -Mth.cos(yaw));
            this.setLastHurtMob(target);
        }

        return hurt;
    }

    /**
     * Swings the main arm on every client as well, so each melee attack is visible like a player's.
     *
     * <p>This is the swing of the game and nothing else (animation round). The swing state is reset first, so the
     * vanilla animation packet is really sent even when another swing was still running - the vanilla code silently
     * swallows a swing that comes too early ({@code LivingEntity#swing}) - and the game then animates the arm that
     * really holds the weapon, the one it picks from {@code swingingArm} and {@code getMainArm()}. The private swing
     * counter of the earlier round is gone, together with the hard coded arm it moved.
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
     * the synced using flag arrived - and it stays false for the first five ticks of a guard, so a shield that has just
     * come up was drawn as if it were not there. The blocking <b>rule</b> of the game is untouched; this is only the
     * pose question of the model.
     */
    public boolean isShieldGuardUp()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND
                && this.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * The sword swing runs on the entity itself, not on the fight goal: a swing that already started is always
     * finished, even when the target dies or the goal stops in the middle of it.
     */
    /**
     * Advances the swing state of the game on this body (animation round).
     *
     * <p>{@code LivingEntity#updateSwingTime} - the vanilla method that turns {@code swinging}/{@code swingTime} into
     * the {@code attackAnim} a renderer draws - is only ever called by {@code Monster#aiStep} and
     * {@code Player#serverAiStep} in this game. A {@code PathfinderMob} that is not a monster, which every human body
     * of this mod is, therefore never advanced it: the swing state froze on the tick it was set, no client could ever
     * draw the swing of the game - and <b>that</b> is why an earlier round drew a cut of its own from a synced counter.
     * With this call the attack of this body is the ordinary swing of the game again, on the arm the game picks for
     * the weapon, and the private counter is gone.
     *
     * <p>It is done in {@code tick()} and not in {@code aiStep()} on purpose: {@code aiStep} of a body that really has
     * no AI (a test body, {@code /data merge ... NoAI:1}) is not reached at all, while the swing of a body that does not
     * move must still run out. Both sides call it, exactly like the game does it for a player.
     */
    @Override
    public void tick()
    {
        super.tick();

        // The swing of the game on this body (animation round): LivingEntity#updateSwingTime is only ever called by
        // Monster#aiStep and Player#serverAiStep in the game, so a PathfinderMob that is not a monster - which every
        // human body of this mod is - would keep its swing state frozen on the tick it was set: swinging stays true,
        // attackAnim stays 0, and no client can ever draw the swing of the game. That is the reason the earlier round
        // drew a cut of its own from a synced counter next to a hard coded arm.
        this.updateSwingTime();

        if (!this.level().isClientSide)
        {
            // Safety net of Phase HF0C: a tiered human that entered the world through a path that never called
            // finalizeSpawn still gets his gear instead of walking around empty handed.
            if (this.isPreparedHuman() && !this.loadoutGenerated)
            {
                this.rollLoadoutOnce();
            }
        }
    }

    /** Puts his own blade in the main hand. An unarmed human simply ends up with an empty hand. */
    public void equipMeleeWeapon()
    {
        if (this.meleeWeapon.isEmpty())
        {
            if (!this.getMainHandItem().isEmpty())
            {
                this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }

            return;
        }

        if (this.getMainHandItem().getItem() != this.meleeWeapon.getItem())
        {
            this.setItemSlot(EquipmentSlot.MAINHAND, this.meleeWeapon.copy());
        }
    }

    /** Puts his own bow in the main hand, without rebuilding the stack while it is already there. */
    public void equipBow()
    {
        if (this.bowWeapon.isEmpty())
        {
            return;
        }

        if (!this.getMainHandItem().is(Items.BOW))
        {
            this.setItemSlot(EquipmentSlot.MAINHAND, this.bowWeapon.copy());
        }
    }

    /** @return the attack damage bonus of the weapon in the main hand, zero for an empty hand. */
    private float heldWeaponDamageBonus()
    {
        double bonus = 0.0D;

        for (AttributeModifier modifier : this.getMainHandItem()
                .getAttributeModifiers(EquipmentSlot.MAINHAND)
                .get(Attributes.ATTACK_DAMAGE))
        {
            bonus += modifier.getAmount();
        }

        return (float) bonus;
    }

    /**
     * The whole loadout of this human travels in the save file (Phase HF0C): the tier he was generated as, his armour
     * and shield through the slot saving of the living entity, his blade and bow through their own tags, his medicine
     * and his face. Nothing of it is rolled again when the entity is loaded.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_GOLDEN_APPLES, this.goldenApples);
        tag.putInt(TAG_SKIN_VARIANT, this.skinVariant());
        tag.putString(TAG_TIER, this.tierId);
        tag.putBoolean(TAG_LOADOUT_GENERATED, this.loadoutGenerated);
        tag.putString(TAG_SPAWN_ORIGIN, this.spawnOrigin.id());

        if (!this.meleeWeapon.isEmpty())
        {
            tag.put(TAG_MELEE_WEAPON, this.meleeWeapon.save(new CompoundTag()));
        }

        if (!this.bowWeapon.isEmpty())
        {
            tag.put(TAG_BOW_WEAPON, this.bowWeapon.save(new CompoundTag()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.contains(TAG_GOLDEN_APPLES))
        {
            this.goldenApples = Mth.clamp(tag.getInt(TAG_GOLDEN_APPLES), 0, MAX_GOLDEN_APPLES);
        }

        // The face of a reloaded combatant is the face it had before the world was closed again.
        if (tag.contains(TAG_SKIN_VARIANT))
        {
            this.setSkinVariant(tag.getInt(TAG_SKIN_VARIANT));
        }

        if (tag.contains(TAG_TIER))
        {
            this.tierId = tag.getString(TAG_TIER);
        }

        if (tag.contains(TAG_LOADOUT_GENERATED))
        {
            this.loadoutGenerated = tag.getBoolean(TAG_LOADOUT_GENERATED);
        }

        if (tag.contains(TAG_SPAWN_ORIGIN))
        {
            this.spawnOrigin = HumanSpawnOrigin.byId(tag.getString(TAG_SPAWN_ORIGIN));
        }

        if (tag.contains(TAG_MELEE_WEAPON))
        {
            this.meleeWeapon = ItemStack.of(tag.getCompound(TAG_MELEE_WEAPON));
        }

        if (tag.contains(TAG_BOW_WEAPON))
        {
            this.bowWeapon = ItemStack.of(tag.getCompound(TAG_BOW_WEAPON));
        }
    }

    /**
     * The equipment a dead human leaves behind (Phase HF0C).
     *
     * <p>Every piece he really owned rolls its own drop chance from the table, and a piece that drops is never brand
     * new: its durability is randomized into the band the table asks for. Looting is deliberately ignored, exactly as
     * the table says, and the golden apples of v0.1 never drop. The main hand is not rolled on its own, it only ever
     * holds a copy of the blade or the bow that are rolled here.
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean hitByPlayer)
    {
        super.dropCustomDeathLoot(source, looting, hitByPlayer);

        if (!this.isPreparedHuman())
        {
            return;
        }

        HumanLoadoutTable table = HumanLoadoutManager.table();

        if (table == null)
        {
            return;
        }

        HumanLoadoutTable.DropRules rules = table.dropRules();

        this.dropEquipmentPiece(this.getItemBySlot(EquipmentSlot.HEAD), rules);
        this.dropEquipmentPiece(this.getItemBySlot(EquipmentSlot.CHEST), rules);
        this.dropEquipmentPiece(this.getItemBySlot(EquipmentSlot.LEGS), rules);
        this.dropEquipmentPiece(this.getItemBySlot(EquipmentSlot.FEET), rules);
        this.dropEquipmentPiece(this.getItemBySlot(EquipmentSlot.OFFHAND), rules);
        this.dropEquipmentPiece(this.meleeWeapon, rules);
        this.dropEquipmentPiece(this.bowWeapon, rules);

        if (this.goldenApples > 0 && rules.medicineDropChance() > 0.0F
                && this.random.nextFloat() < rules.medicineDropChance())
        {
            this.spawnAtLocation(new ItemStack(Items.GOLDEN_APPLE, this.goldenApples));
        }
    }

    /** Rolls one piece of equipment: the table decides whether it drops and in which condition it hits the ground. */
    private void dropEquipmentPiece(ItemStack piece, HumanLoadoutTable.DropRules rules)
    {
        if (piece.isEmpty() || this.random.nextFloat() >= rules.equipmentDropChance())
        {
            return;
        }

        ItemStack drop = piece.copy();
        int maxDamage = drop.getMaxDamage();

        if (maxDamage > 0)
        {
            float remaining = Mth.lerp(this.random.nextFloat(), rules.durabilityMin(), rules.durabilityMax());
            drop.setDamageValue(Mth.clamp((int) ((float) maxDamage * (1.0F - remaining)), 0, maxDamage - 1));
        }

        this.spawnAtLocation(drop);
    }

    /**
     * Only the surface patrol of this mod is allowed to despawn (Phase HF0D). A human that a player placed with an
     * egg or a command, and above all a member of a village garrison, stays where he is: a garrison that quietly
     * vanishes when the player walks away would be no garrison at all.
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return this.spawnOrigin == HumanSpawnOrigin.NATURAL_SURFACE;
    }

    /**
     * Gives each human a small random experience reward on death (Phase HF0D).
     *
     * <p>The reward is a flat random range of 3 to 5 experience points, drawn once per death. The
     * {@code xpReward} field is kept at zero so the vanilla death mechanism drops nothing on its own; this
     * override replaces it entirely without duplicating any existing logic.
     */
    @Override
    public int getExperienceReward()
    {
        return 3 + this.random.nextInt(3);
    }
}

