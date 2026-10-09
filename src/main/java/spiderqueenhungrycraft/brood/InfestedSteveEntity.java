package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.ToolActions;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.SelfSlaughterAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;
import spiderqueenhungrycraft.brood.goal.BroodBodyCombatGoal;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The infested Steve (Infested Steve round): the body of a dead human, driven by the spiders inside it.
 *
 * <p><b>It is a spider of the brood.</b> Not by its looks - it wears the body, the animation and the sounds of a human
 * - but by everything that makes a creature a soldier of the queen: the owner, the sceptre commands, follow, hold and
 * patrol, the temporary command of a move or a forced attack, the rally, the selection, the escort relocation, the
 * roster, the veterancy and the faction answers all come from {@link SpiderMinionEntity}, exactly as they do for every
 * other species of the brood. A body of a human is a body, not a faction.
 *
 * <p><b>It cannot climb.</b> That is the one thing about the spider body this creature deliberately loses: the flag the
 * spider body sets from its own wall collisions is swallowed here, so no wall can be climbed. Everything else about its
 * movement stays the ordinary ground navigation of the brood, so an order to walk, hold, patrol or attack is carried out
 * exactly like it is by a spider.
 *
 * <p>Its size is the size of the body it wears: the hitbox of a human, and the eye height of a human, so that the human
 * model stands in the world the way it looks like it stands.
 *
 * <p><b>How it takes gear and medicine (gear loop fix).</b> Three rules stand between this body and the loot around it:
 * it never rummages while it fights ({@link SpiderMinionEntity#isInCombat()}), a place of gear that just changed its
 * piece refuses the next one for two seconds, and - the rule that really ends the loop - a filled place only ever takes
 * a <b>better</b> piece, exactly the comparison the game's own zombies make before they swap anything. Golden apples are
 * never gated by any of it: they are medicine, not gear. Out of a fight a hurt body also tops itself up on its own once
 * its health drops below {@code infested_steve.out_of_combat_heal_ratio} of its maximum.
 */
public class InfestedSteveEntity extends SpiderMinionEntity implements BroodBodyFighter
{
    /** How many imps crawl out of the body when it falls (Infested Steve round). */
    public static final int BROOD_ON_DEATH = 3;

    /** How far from the corpse the brood appears, in blocks. */
    public static final double BROOD_SPAWN_RADIUS = 0.85D;

    /** The health of an ordinary human body, which is what this is. */
    public static final double MAX_HEALTH = 20.0D;

    /** The damage of its fist, for a body that has not picked up a weapon yet. */
    public static final double ATTACK_DAMAGE = 4.0D;

    /**
     * The numbers of the gear and medicine rules of this body live in the common configuration
     * ({@code HungryCraftConfig}, section {@code infested_steve}), so a pack author can tune them:
     * {@code gear_pickup_cooldown_ticks}, {@code out_of_combat_heal_ratio} and
     * {@code out_of_combat_heal_cooldown_ticks}. What used to be the constants of this class is now the default of
     * those three entries.
     */

    /** The hitbox of a human body. */
    public static final float COLLISION_WIDTH = 0.6F;
    public static final float COLLISION_HEIGHT = 1.8F;

    /** The eye height of a human body, so the human head sits where a human head sits. */
    public static final float EYE_HEIGHT = 1.62F;

    /** The belt of this body: the blade and the bow it owns, saved under the very names a human saves its belt. */
    private static final String TAG_MELEE_WEAPON = "MeleeWeapon";
    private static final String TAG_BOW_WEAPON = "BowWeapon";
    private static final String TAG_GOLDEN_APPLES = "GoldenApples";

    /** The two lists the game writes the worn gear of any living being into, read when a digested human is inherited. */
    private static final String TAG_ARMOR_ITEMS = "ArmorItems";
    private static final String TAG_HAND_ITEMS = "HandItems";

    /** The one brain of this body, the state machine of {@link BroodBodyCombatGoal} - the fight of a brood body. */
    private final BroodBodyBrain brain = new BroodBodyBrain();

    /**
     * The blade of this body, kept beside the slots exactly like a human keeps its own: it is only drawn into the main
     * hand while the fight really uses it, so the same body can carry a bow in the belt at the same time.
     */
    private ItemStack meleeWeapon = ItemStack.EMPTY;

    /** The bow of this body, handled exactly like the blade. */
    private ItemStack bowWeapon = ItemStack.EMPTY;

    /** How many golden apples this body still carries. It is what it ate, not an inventory. */
    private int goldenApples;

    /**
     * The stock of medicine of this body, synchronised to every client that can see it.
     *
     * <p>The gear a body wears shows itself, the medicine does not: nothing about the model of a body tells the queen
     * whether the apple she dropped was swallowed, chewed on, or never taken at all. This one number is what the inspect
     * HUD shows; the field {@link #goldenApples} stays the truth on the server, and this value is written only when it
     * really changed.
     */
    private static final EntityDataAccessor<Integer> DATA_GOLDEN_APPLES = SynchedEntityData
            .defineId(InfestedSteveEntity.class, EntityDataSerializers.INT);

    /**
     * The places a piece of gear can sit on this body: the four armour slots, the offhand and the two hooks of the belt.
     *
     * <p>The cooldown of {@code infested_steve.gear_pickup_cooldown_ticks} is kept per place, so putting a helmet on
     * never stops this body from putting a chestplate on in the same second. The belt is two places on purpose: a bow
     * and a blade live beside each other there, exactly like they do on a human.
     */
    private enum GearPlace
    {
        HEAD,
        CHEST,
        LEGS,
        FEET,
        OFFHAND,
        BELT_BOW,
        BELT_MELEE
    }

    /** How many ticks each place of gear still refuses a new pickup. Indexed by {@link GearPlace#ordinal()}. */
    private final int[] gearCooldowns = new int[GearPlace.values().length];

    /** Which tick of the recovery bite this body is in, -1 while it is not biting into an apple of its own stock. */
    private int recoveryBiteTicks = -1;

    /** How long this body still waits before it tops itself up again. */
    private int recoveryCooldownTicks;

    public InfestedSteveEntity(EntityType<? extends InfestedSteveEntity> entityType, Level level)
    {
        super(entityType, level);

        // The body walks like a body: the climbing movement control of the spider body is replaced by the ordinary one.
        this.moveControl = new MoveControl(this);
    }

    /**
     * The goals of a soldier of the brood.
     *
     * <p>The brood is asked first (the orders, the help for the queen, the self defence, the faction and the inspect
     * HUD all come from {@link SpiderMinionEntity} and are untouched here), and then the fangs are exchanged for a
     * weapon: the leap and the vanilla melee goal of the spider body are removed and the one fight goal of a brood body
     * takes their place (split round: the fight of the human combatant is a system of its own and is not this one). That
     * goal owns the navigation, the look, both hands and the whole fight, and it steps aside by
     * itself while the queen has ordered this body to walk somewhere - an order of the sceptre wins over a fight, which
     * is the rule every spider of the brood lives by.
     */
    @Override
    protected void registerGoals()
    {
        super.registerGoals();

        this.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal || goal instanceof LeapAtTargetGoal);
        this.goalSelector.addGoal(1, new BroodBodyCombatGoal(this));
    }

    /** A body on two legs: no wall can be climbed, so the climbing navigator of the spider body is not used. */
    @Override
    protected PathNavigation createNavigation(Level level)
    {
        return new GroundPathNavigation(this, level);
    }

    /**
     * The server side tick of this body: the gear cooldowns of the gear loop fix run down here, and so does the
     * medicine a body takes while it is not fighting (golden apple round). Both are the body's own business and never
     * touch the fight of {@link BroodBodyCombatGoal}: the cooldowns are only read by the pickup rules, and the recovery bite
     * steps aside the moment the fight takes the body over.
     */
    @Override
    public void tick()
    {
        super.tick();

        if (this.level().isClientSide)
        {
            return;
        }

        this.tickGearCooldowns();
        this.syncGoldenApples();
        this.tickRecovery();
    }

    /**
     * The medicine of this body travels to every client that can see it, so the inspect HUD of the queen can show it.
     * Written only when the number really changed, exactly like the owner, the order and the errand of a spider.
     */
    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.getEntityData().define(DATA_GOLDEN_APPLES, 0);
    }

    /** Keeps the stock of medicine a client sees in step with the real one. */
    private void syncGoldenApples()
    {
        if (this.getEntityData().get(DATA_GOLDEN_APPLES) != this.goldenApples)
        {
            this.getEntityData().set(DATA_GOLDEN_APPLES, this.goldenApples);
        }
    }

    /** Counts every pickup cooldown of the gear places down, one tick at a time. */
    private void tickGearCooldowns()
    {
        for (int place = 0; place < this.gearCooldowns.length; ++place)
        {
            if (this.gearCooldowns[place] > 0)
            {
                --this.gearCooldowns[place];
            }
        }
    }

    /**
     * The voice of a human body. A player has no idle sound in this game and neither has this creature, but a hit and a
     * death are the two sounds a body makes, and they are the two sounds of a player.
     */
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return null;
    }

    /** The attributes of a spider of the brood with the body of a human. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE);
    }

    /**
     * Never climbing, whatever the body collides with.
     *
     * <p>The spider body of this mod sets the climbing flag from its horizontal collisions on its own, every tick. This
     * creature has no claws to climb with, so the flag is simply swallowed here - one method, and it stays a walker.
     */
    @Override
    public void setClimbing(boolean climbing)
    {
        super.setClimbing(false);
    }

    /** The climbing question of the game itself, answered no as well: this body never holds on to a wall. */
    @Override
    public boolean onClimbable()
    {
        return false;
    }

    /** The eyes of a human body, not the eyes of a spider. */
    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions)
    {
        return EYE_HEIGHT;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The fight of this body. The decisions of the fight - when to walk in, when to raise the shield, when to draw the
    // bow, when to bite into an apple, when to fall back - live in the fight of a brood body, BroodBodyBrain together
    // with BroodBodyCombatGoal, which belongs to this body alone (split round). This class answers only what that fight
    // asks about the body - which gear it owns and which hand holds what.
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    public BroodBodyBrain brain()
    {
        return this.brain;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The ability of this body (LEEROY JENKINS!! round).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The ability of a brood body: 「自刎归天」/ LEEROY JENKINS!! - it falls on its own blade for its queen.
     *
     * <p>It is the only place where this body and the ability framework meet, exactly like the mega blast of a creeper
     * spider: the sceptre tells every <b>selected</b> spider "try your ability", and this body answers with
     * {@link SelfSlaughterAbility}. Everything about that ability - who may cast it, what it does, how long it rests -
     * is answered inside that class and nowhere else.
     */
    @Override
    public SpiderAbility getAbility()
    {
        return SelfSlaughterAbility.INSTANCE;
    }

    /**
     * Cuts its own throat, here and now: the cast of {@link SelfSlaughterAbility}.
     *
     * <p>The death is the death this body always had - it is wounded by {@link InfestedSteveDamageTypes#selfSlaughter}
     * with just enough to be lethal, and the {@code die()} of this class does the rest: every piece of gear and every
     * golden apple of its pouch on the ground, and the three imp spiders walking out of the corpse. Nothing of that
     * payload is written a second time for this ability.
     *
     * <p>The wound carries <b>no attacker</b>, so nothing is blamed and no pack is provoked, and the tags of the type
     * make armour, enchantments, a shield and the invulnerability frames irrelevant - an order to die is not a hit that
     * a helmet may argue with.
     *
     * @return true when the body really died of it. A body on the client, one that is removed already and one that is
     *         dying anyway all refuse, and such a cast costs the queen nothing (the framework starts no cooldown for an
     *         ability that never happened).
     */
    public boolean slaughterItself()
    {
        if (!(this.level() instanceof ServerLevel level) || this.isRemoved() || this.isDeadOrDying())
        {
            return false;
        }

        // The last sound of the act itself, played before the death sound of the body covers it: one swing of a blade.
        level.playSound(null, this.getX(), this.getEyeY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                this.getSoundSource(), 1.0F, 1.0F);

        float lethal = Math.max(1.0F, this.getHealth() + this.getAbsorptionAmount() + 1.0F);

        return this.hurt(InfestedSteveDamageTypes.selfSlaughter(level), lethal);
    }

    @Override
    public boolean hasShield()
    {
        return this.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD);
    }

    @Override
    public boolean hasBow()
    {
        return !this.bowWeapon.isEmpty();
    }

    @Override
    public boolean hasMeleeWeapon()
    {
        return !this.meleeWeapon.isEmpty();
    }

    @Override
    public boolean canFightInMelee()
    {
        return this.hasMeleeWeapon() || !this.hasBow();
    }

    @Override
    public ItemStack meleeWeapon()
    {
        return this.meleeWeapon;
    }

    @Override
    public ItemStack bowWeapon()
    {
        return this.bowWeapon;
    }

    /**
     * The arrow of a soldier of the queen (Infested Steve round): the arrow of the brood, which flies straight through
     * the allies of its queen instead of hurting them - the very same ammunition the skeleton spiders shoot, so a volley
     * of the brood never kills its own. The aim, the power, the crit and the cooldown of the shot stay in the fight of
     * this body ({@link BroodBodyBrain}), which is this body's own since the split round.
     */
    @Override
    public AbstractArrow newArrow()
    {
        return new SpiderArrow(this.level(), this);
    }

    /**
     * The target rule of a soldier of the queen (Infested Steve round).
     *
     * <p>It is the rule of the brood, not the rule of a human: whatever the target goals of the brood have picked - a
     * villager, an illager, a prepared human, a monster, or a victim the queen forced on this body with her sceptre - is
     * a legal target as long as it is alive, in this world and not somebody of this queen. {@link SpiderMinionTargeting}
     * is the very gate every other spider of the brood uses, so this creature accepts exactly what a spider accepts.
     *
     * <p>This is what the split round made explicit: the rule of this body belongs to this body. Before the split the
     * body was handed targets by the brood and then refused every one of them, because the fight of a human combatant
     * only ever allows a queen player and the monster faction.
     */
    @Override
    public boolean mayFight(LivingEntity target)
    {
        return SpiderMinionTargeting.isAttackable(this, target);
    }

    /** Draws the blade of this body, exactly the way a human draws its own. */
    @Override
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

    /** Draws the bow of this body, without rebuilding the stack while it is already there. */
    @Override
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

    /** Swings the main arm on every client as well, so every cut is visible like the cut of a player. */
    @Override
    public void swingMainHand()
    {
        this.swinging = false;
        this.swingTime = 0;
        this.swing(InteractionHand.MAIN_HAND, true);
    }

    /**
     * @return true while this body is really drawing a bow, asked from <b>synced state only</b>: the using flag, the
     *         hand it is used in and the main hand item.
     *
     * <p>Deliberately not {@code getUseItem()} (animation round). That copy is filled on the server and only ever
     * guessed on a client from whatever the hand held when the synced using flag arrived - and the flag and the
     * equipment travel in different packets. A client that received the flag first therefore runs the whole draw with
     * an empty or stale use copy, so a model that asked it drew a body that shoots a bow it never seems to pull. The
     * question below reads the synced flag, the synced hand and the synced main hand instead, exactly like the
     * ordinary human's model does.
     */
    public boolean isDrawingBow()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.MAIN_HAND
                && this.getMainHandItem().is(Items.BOW);
    }

    /**
     * @return true while the visible guard of this body is really up, asked from <b>synced state only</b>.
     *
     * <p>The same reason as above, plus one of its own: {@code LivingEntity#isBlocking} answers a shield only after
     * five ticks of use, so the arm of a body that just raised its shield was drawn as if the shield were not there.
     * The blocking <b>rule</b> of the game is not touched by this method; it is the pose question of the renderer.
     */
    public boolean isShieldGuardUp()
    {
        return this.isUsingItem() && this.getUsedItemHand() == InteractionHand.OFF_HAND
                && this.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    /**
     * One melee swing with whatever is in the main hand: the attack damage of this body plus the attack damage bonus of
     * the weapon it holds, so a real blade in the hand hits harder than a fist. A veteran or elite body hits harder as
     * well, because its rank raises the very attribute this line reads (Brood Veterancy round).
     */
    @Override
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

    @Override
    public int goldenAppleCount()
    {
        return this.goldenApples;
    }

    /**
     * @return the stock of golden apples as every client knows it, which is what the inspect HUD of the queen shows.
     *         The server keeps it in step with the real stock once per tick, and only when it changed.
     */
    public int visibleGoldenAppleCount()
    {
        return this.getEntityData().get(DATA_GOLDEN_APPLES);
    }

    @Override
    public boolean consumeGoldenApple()
    {
        if (this.goldenApples <= 0)
        {
            return false;
        }

        --this.goldenApples;
        return true;
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

    // ---------------------------------------------------------------------------------------------------------------
    // The gear of a digested human (Infested Steve round).
    // ---------------------------------------------------------------------------------------------------------------

    /** A soldier of the queen is never switched off from looting: this body always looks for gear. */
    @Override
    public boolean canPickUpLoot()
    {
        return true;
    }

    /**
     * @return true for everything this body can really use - the armour of a human, a shield, a bow, a weapon and the
     *         golden apples a human carried. Everything else stays where it lies, exactly as it does for a human.
     *
     * <p><b>Two rules of the gear loop fix stand here</b>, because this is the one gate the loot scan of the game asks
     * before it hands a piece over ({@code Mob#aiStep}): the body never rummages for gear while it fights, and a place
     * of gear that has just changed its piece refuses the next one until
     * {@code infested_steve.gear_pickup_cooldown_ticks} has run down. Medicine is never gated by either of them - a
     * body that is hurt has to be able to take the apple it is standing on, in a fight and outside of one.
     */
    @Override
    public boolean wantsToPickUp(ItemStack stack)
    {
        if (isGoldenApple(stack))
        {
            return true;
        }

        GearPlace place = placeOf(stack);

        if (place == null)
        {
            return false;
        }

        return !this.isInCombat() && this.gearCooldowns[place.ordinal()] <= 0;
    }

    /**
     * The better gear wins.
     *
     * <p>One piece is taken off the ground and put into the place it belongs to. Whatever was in that place before is
     * dropped at the feet of this body instead of vanishing, and the rest of the stack stays on the ground, so a second
     * blade or a pile of apples is never swallowed by a body that can only use what it can wear.
     *
     * <p><b>A filled place only takes a better piece</b> ({@link #wantsToWear}), which is what ends the loop the queen
     * saw: two pieces of one place lying beside the body used to be put on and taken off again for ever, because every
     * pickup dropped the other piece right back into the reach of the body.
     */
    @Override
    protected void pickUpItem(ItemEntity itemEntity)
    {
        ItemStack stack = itemEntity.getItem();

        if (stack.isEmpty() || !this.wantsToPickUp(stack))
        {
            return;
        }

        // A golden apple is not worn, it is carried: the whole stack goes into the pouch of this body.
        if (isGoldenApple(stack))
        {
            this.goldenApples += stack.getCount();
            itemEntity.discard();
            return;
        }

        ItemStack piece = stack.copy();
        piece.setCount(1);
        EquipmentSlot armour = armourSlotOf(piece);

        if (armour != null)
        {
            if (!this.wantsToWear(piece, this.getItemBySlot(armour)))
            {
                return;
            }

            this.wearPiece(armour, piece);
        }
        else if (piece.is(Items.SHIELD))
        {
            if (!this.wantsToWear(piece, this.getItemBySlot(EquipmentSlot.OFFHAND)))
            {
                return;
            }

            this.wearPiece(EquipmentSlot.OFFHAND, piece);
        }
        else if (piece.getItem() instanceof BowItem)
        {
            if (!this.wantsToWear(piece, this.bowWeapon))
            {
                return;
            }

            this.hangOnBelt(true, piece);
        }
        else
        {
            if (!this.wantsToWear(piece, this.meleeWeapon))
            {
                return;
            }

            this.hangOnBelt(false, piece);
        }

        stack.shrink(1);

        if (stack.isEmpty())
        {
            itemEntity.discard();
        }
    }

    /**
     * The vanilla rule of a body that finds gear: an empty place always takes the piece, a filled place only takes a
     * better one.
     *
     * <p>It is {@code Mob#canReplaceCurrentItem}, the very comparison the game's own zombies make before they swap
     * anything - a blade against a blade by its damage, armour by its defence and toughness, everything else by its
     * enchantments and its durability. Copying no rule of our own here is the point: the body judges gear exactly the
     * way the game does, and a body that stands in a pile of equal pieces simply keeps the one it has.
     */
    private boolean wantsToWear(ItemStack piece, ItemStack worn)
    {
        return this.canReplaceCurrentItem(piece, worn);
    }

    /** Wears one piece: it goes into its slot, and whatever was there is dropped at the feet of this body. */
    private void wearPiece(EquipmentSlot slot, ItemStack piece)
    {
        ItemStack previous = this.getItemBySlot(slot);
        this.setItemSlot(slot, piece);
        this.armGearCooldown(placeOf(slot));

        if (!previous.isEmpty())
        {
            this.spawnAtLocation(previous);
        }
    }

    /** Hangs a blade or a bow on the belt of this body, dropping the piece that hung there before. */
    private void hangOnBelt(boolean bow, ItemStack piece)
    {
        ItemStack previous = bow ? this.bowWeapon : this.meleeWeapon;

        if (bow)
        {
            this.bowWeapon = piece;
        }
        else
        {
            this.meleeWeapon = piece;
            this.equipMeleeWeapon();
        }

        this.armGearCooldown(bow ? GearPlace.BELT_BOW : GearPlace.BELT_MELEE);

        if (!previous.isEmpty())
        {
            this.spawnAtLocation(previous);
        }
    }

    /** Sends one place of gear into its pickup cooldown, after a piece was really put there. */
    private void armGearCooldown(@Nullable GearPlace place)
    {
        if (place != null)
        {
            this.gearCooldowns[place.ordinal()] = HungryCraftConfig.infestedSteveGearPickupCooldownTicks();
        }
    }

    /** @return the armour slot that piece belongs into, null when it is not armour. */
    @Nullable
    private static EquipmentSlot armourSlotOf(ItemStack piece)
    {
        if (piece.getItem() instanceof ArmorItem armour)
        {
            EquipmentSlot slot = armour.getEquipmentSlot();
            return slot.getType() == EquipmentSlot.Type.ARMOR ? slot : null;
        }

        return null;
    }

    /** @return true for a weapon: an item that really declares attack damage for the hand. */
    private static boolean isWeapon(ItemStack piece)
    {
        return !piece.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }

    /** @return the place of gear that slot is, null for a slot this body never fills from the ground. */
    @Nullable
    private static GearPlace placeOf(EquipmentSlot slot)
    {
        return switch (slot)
        {
            case HEAD -> GearPlace.HEAD;
            case CHEST -> GearPlace.CHEST;
            case LEGS -> GearPlace.LEGS;
            case FEET -> GearPlace.FEET;
            case OFFHAND -> GearPlace.OFFHAND;
            default -> null;
        };
    }

    /**
     * @return the place of gear that piece belongs into, null when this body cannot use the piece at all. The armour
     *         slot of the piece decides for armour, so no piece is ever mapped to two places at once.
     */
    @Nullable
    private static GearPlace placeOf(ItemStack piece)
    {
        EquipmentSlot armour = armourSlotOf(piece);

        if (armour != null)
        {
            return placeOf(armour);
        }

        if (piece.is(Items.SHIELD))
        {
            return GearPlace.OFFHAND;
        }

        if (piece.getItem() instanceof BowItem)
        {
            return GearPlace.BELT_BOW;
        }

        return isWeapon(piece) ? GearPlace.BELT_MELEE : null;
    }

    private static boolean isGoldenApple(ItemStack piece)
    {
        return piece.is(Items.GOLDEN_APPLE);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The medicine of a body that is not fighting (golden apple round).
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Tops this body up with the golden apples it carries, while it is not fighting.
     *
     * <p><b>The rule.</b> Out of a fight, with a golden apple in its stock and its health below
     * {@code infested_steve.out_of_combat_heal_ratio} of its maximum, the body takes one out and bites into it - without
     * anybody ordering it and without a victim in sight. That is the whole difference to the fight of
     * {@link BroodBodyCombatGoal}, which only ever eats below {@link BroodBodyBrain#LOW_HEALTH_RATIO} and only while it
     * has somebody to fight: a body that walked away from a fight now patches itself up on its own, where before it sat
     * on its medicine until it died.
     *
     * <p><b>Who owns the hands.</b> The recovery bite only ever runs while nothing of the fight uses this body
     * ({@link #fightHoldsTheBody()}). The moment a fight starts, the bite is closed the vanilla way instead of being
     * waited out: the apple is never wasted, the hand never keeps a golden apple that nothing chews on, and the fight
     * gets a clean body. <b>Movement is never stopped for the bite</b> - a body under an order keeps walking and eats on
     * the way, exactly like a human who eats on the road; only the fight of this body stands still to eat.
     *
     * <p>Nothing of this is saved: a bite lasts at most a second and a half, and a half finished bite in a save file is
     * not worth a tag.
     */
    private void tickRecovery()
    {
        if (this.recoveryCooldownTicks > 0)
        {
            --this.recoveryCooldownTicks;
        }

        if (this.recoveryBiteTicks >= 0)
        {
            this.tickRecoveryBite();
            return;
        }

        if (this.canStartRecoveryBite())
        {
            this.startRecoveryBite();
        }
    }

    /**
     * @return true when this body has both the reason and the chance to patch itself up: alive, hurt below the out of
     *         combat ratio, carrying medicine, not already chewing on something and not held by a fight.
     */
    private boolean canStartRecoveryBite()
    {
        return this.isAlive() && this.goldenApples > 0 && this.recoveryCooldownTicks <= 0
                && !this.isUsingItem() && !this.fightHoldsTheBody()
                && this.getHealth() < this.getMaxHealth() * HungryCraftConfig.infestedSteveOutOfCombatHealRatio();
    }

    /**
     * @return true while a fight really holds this body: the one fight goal is running (a victim, a hit it may not
     *         answer, or a bite of the fight itself), or the body counts as fighting ({@link #isInCombat()}, which also
     *         keeps the three seconds after a fight in it). Every rule of the recovery above hangs on this.
     */
    private boolean fightHoldsTheBody()
    {
        if (this.goalSelector.getRunningGoals()
                .anyMatch(running -> running.getGoal() instanceof BroodBodyCombatGoal))
        {
            return true;
        }

        return this.isInCombat();
    }

    /** Takes one apple out of the stock and starts the vanilla bite with it. */
    private void startRecoveryBite()
    {
        if (!this.consumeGoldenApple())
        {
            return;
        }

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_APPLE));
        this.startUsingItem(InteractionHand.MAIN_HAND);
        this.recoveryBiteTicks = 0;
    }

    /**
     * Runs the bite. The vanilla use of the item does everything by itself - the animation, the effects, the shrinking
     * of the apple - and this ends the bite at the latest after {@link BroodBodyBrain#GOLDEN_APPLE_USE_TICKS} ticks,
     * the very rhythm the fight of this body eats by.
     */
    private void tickRecoveryBite()
    {
        ++this.recoveryBiteTicks;

        if (this.fightHoldsTheBody() || this.recoveryBiteTicks >= BroodBodyBrain.GOLDEN_APPLE_USE_TICKS
                || !this.isUsingItem())
        {
            this.finishRecoveryBite();
        }
    }

    /**
     * Closes the bite the vanilla way, exactly like the fight of this body closes its own: the game's
     * {@code finishUsingItem} plays the last bite, applies the effects of the apple and shrinks the stack, and the blade
     * of this body comes back into the hand afterwards. No health is ever set by hand here.
     */
    private void finishRecoveryBite()
    {
        this.recoveryBiteTicks = -1;
        this.recoveryCooldownTicks = HungryCraftConfig.infestedSteveOutOfCombatHealCooldownTicks();

        if (this.isUsingItem())
        {
            this.stopUsingItem();
        }

        ItemStack apple = this.getMainHandItem();

        if (apple.is(Items.GOLDEN_APPLE))
        {
            ItemStack rest = apple.finishUsingItem(this.level(), this);
            this.setItemSlot(EquipmentSlot.MAINHAND, rest);
        }

        this.equipMeleeWeapon();
    }

    /**
     * Takes over the gear of the human this creature grew out of (Infested Steve round).
     *
     * <p>The cocoon calls this right after the creature is created and hands over the saved prey data of the human it
     * digested, so the armour, the shield, the blade and the bow of a fallen human keep fighting instead of disappearing.
     *
     * <p>It is the same reader as {@link #readCarriedGear} (Carrier Gear round) and not a second one: a human in a
     * cocoon and a spider in a carrier hand their gear over in the same shape, so there is one description of "the gear
     * of this body" in this class and not two that could drift apart.
     */
    public void inheritGearFrom(CompoundTag preyNbt)
    {
        this.applyGearFrom(preyNbt);
    }

    /**
     * The gear of this body travels with a carrier (Carrier Gear round).
     *
     * <p>It is written in the very shape the game saves the worn gear of a living being in - the four armour slots in
     * their order in {@code ArmorItems}, the two hands in their order in {@code HandItems} - plus the belt and the
     * apples under the names a human uses for them. So the tag of a packed spider and the tag of a digested human are
     * interchangeable, and {@link #inheritGearFrom} of the cocoon can read what a carrier wrote without a translation.
     */
    @Override
    public void writeCarriedGear(CompoundTag tag)
    {
        ListTag armour = new ListTag();
        armour.add(this.getItemBySlot(EquipmentSlot.FEET).save(new CompoundTag()));
        armour.add(this.getItemBySlot(EquipmentSlot.LEGS).save(new CompoundTag()));
        armour.add(this.getItemBySlot(EquipmentSlot.CHEST).save(new CompoundTag()));
        armour.add(this.getItemBySlot(EquipmentSlot.HEAD).save(new CompoundTag()));
        tag.put(TAG_ARMOR_ITEMS, armour);

        // The hands are written the way the game writes them, main hand first - but the main hand is deliberately left
        // empty: while this body fights, that hand only ever holds a working copy of a piece of the belt (see die()),
        // and the belt below is what that copy is made of. Writing both would turn one sword into two.
        ListTag hands = new ListTag();
        hands.add(ItemStack.EMPTY.save(new CompoundTag()));
        hands.add(this.getItemBySlot(EquipmentSlot.OFFHAND).save(new CompoundTag()));
        tag.put(TAG_HAND_ITEMS, hands);

        if (!this.meleeWeapon.isEmpty())
        {
            tag.put(TAG_MELEE_WEAPON, this.meleeWeapon.save(new CompoundTag()));
        }

        if (!this.bowWeapon.isEmpty())
        {
            tag.put(TAG_BOW_WEAPON, this.bowWeapon.save(new CompoundTag()));
        }

        tag.putInt(TAG_GOLDEN_APPLES, this.goldenApples);
    }

    /** Puts the gear of a carrier back on this body: it comes back armed, armoured and with its medicine. */
    @Override
    public void readCarriedGear(CompoundTag tag)
    {
        this.applyGearFrom(tag);
    }

    /**
     * Reads a gear tag - of a digested human, or of a carrier - onto this body.
     *
     * <p>The armour and the shield come from the saved gear of the body, the blade and the bow from its belt. The main
     * hand is deliberately <b>not</b> read: a body keeps a working copy of its blade in the hand while it fights, and
     * taking both would turn one sword into two. The gear of the world stays countable, and one digested human can only
     * ever breed one soldier.
     */
    private void applyGearFrom(CompoundTag gear)
    {
        this.setItemSlot(EquipmentSlot.FEET, listStack(gear, TAG_ARMOR_ITEMS, 0));
        this.setItemSlot(EquipmentSlot.LEGS, listStack(gear, TAG_ARMOR_ITEMS, 1));
        this.setItemSlot(EquipmentSlot.CHEST, listStack(gear, TAG_ARMOR_ITEMS, 2));
        this.setItemSlot(EquipmentSlot.HEAD, listStack(gear, TAG_ARMOR_ITEMS, 3));
        this.setItemSlot(EquipmentSlot.OFFHAND, listStack(gear, TAG_HAND_ITEMS, 1));

        if (gear.contains(TAG_MELEE_WEAPON))
        {
            this.meleeWeapon = ItemStack.of(gear.getCompound(TAG_MELEE_WEAPON));
        }

        if (gear.contains(TAG_BOW_WEAPON))
        {
            this.bowWeapon = ItemStack.of(gear.getCompound(TAG_BOW_WEAPON));
        }

        if (gear.contains(TAG_GOLDEN_APPLES))
        {
            this.goldenApples = Math.max(0, gear.getInt(TAG_GOLDEN_APPLES));
        }

        this.equipMeleeWeapon();
    }

    /** @return one entry of one of the two gear lists of a saved living being, empty when it is not there. */
    private static ItemStack listStack(CompoundTag nbt, String key, int index)
    {
        if (!nbt.contains(key, Tag.TAG_LIST))
        {
            return ItemStack.EMPTY;
        }

        ListTag list = nbt.getList(key, Tag.TAG_COMPOUND);

        return index < list.size() ? ItemStack.of(list.getCompound(index)) : ItemStack.EMPTY;
    }

    /**
     * The belt of this body travels with it (Infested Steve round). The armour, the hands and the shield are saved by
     * the game itself, the blade and the bow are saved here - under the same two names a human uses for its own belt, so
     * the cocoon can hand the gear of a digested human over without translating a single key.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        if (!this.meleeWeapon.isEmpty())
        {
            tag.put(TAG_MELEE_WEAPON, this.meleeWeapon.save(new CompoundTag()));
        }

        if (!this.bowWeapon.isEmpty())
        {
            tag.put(TAG_BOW_WEAPON, this.bowWeapon.save(new CompoundTag()));
        }

        tag.putInt(TAG_GOLDEN_APPLES, this.goldenApples);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.contains(TAG_MELEE_WEAPON))
        {
            this.meleeWeapon = ItemStack.of(tag.getCompound(TAG_MELEE_WEAPON));
        }

        if (tag.contains(TAG_BOW_WEAPON))
        {
            this.bowWeapon = ItemStack.of(tag.getCompound(TAG_BOW_WEAPON));
        }

        this.goldenApples = Math.max(0, tag.getInt(TAG_GOLDEN_APPLES));
    }

    /**
     * Everything this body carries lands on the ground - all of it (Infested Steve round).
     *
     * <p>The gear is taken out of the body and dropped here, which is what makes the drop complete, and it is what keeps
     * the drop honest: the chance roll of the game that runs a moment later finds empty places, so a sword that existed
     * once can never be dropped twice. A working copy of a belt piece that happens to hang in the hand while the body
     * dies is cleared instead of dropped, for exactly the same reason.
     */
    @Override
    public void die(DamageSource source)
    {
        if (!this.level().isClientSide)
        {
            ItemStack beltMelee = this.meleeWeapon;
            ItemStack beltBow = this.bowWeapon;
            this.meleeWeapon = ItemStack.EMPTY;
            this.bowWeapon = ItemStack.EMPTY;

            this.dropSlot(EquipmentSlot.HEAD);
            this.dropSlot(EquipmentSlot.CHEST);
            this.dropSlot(EquipmentSlot.LEGS);
            this.dropSlot(EquipmentSlot.FEET);
            this.dropSlot(EquipmentSlot.OFFHAND);

            ItemStack inHand = this.getItemBySlot(EquipmentSlot.MAINHAND);

            if (!inHand.isEmpty())
            {
                boolean copyOfTheBelt = (!beltMelee.isEmpty() && ItemStack.isSameItemSameTags(inHand, beltMelee))
                        || (!beltBow.isEmpty() && ItemStack.isSameItemSameTags(inHand, beltBow));

                if (copyOfTheBelt)
                {
                    this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                else
                {
                    this.dropSlot(EquipmentSlot.MAINHAND);
                }
            }

            this.dropPiece(beltMelee);
            this.dropPiece(beltBow);

            if (this.goldenApples > 0)
            {
                this.dropPiece(new ItemStack(Items.GOLDEN_APPLE, this.goldenApples));
                this.goldenApples = 0;
            }

            // The body is done, the brood inside it is not (Infested Steve round).
            this.spawnBroodOfTheCorpse();
        }

        super.die(source);
    }

    /** Drops whatever is in that place, all of it, and empties the place. */
    private void dropSlot(EquipmentSlot slot)
    {
        ItemStack piece = this.getItemBySlot(slot);

        if (!piece.isEmpty())
        {
            this.setItemSlot(slot, ItemStack.EMPTY);
            this.dropPiece(piece);
        }
    }

    private void dropPiece(ItemStack piece)
    {
        if (!piece.isEmpty())
        {
            this.spawnAtLocation(piece);
        }
    }

    /**
     * The brood crawls out of the corpse (Infested Steve round).
     *
     * <p>When the body falls, the spiders that drove it leave it: {@value #BROOD_ON_DEATH} imp spiders appear in a small
     * ring around it, and they are soldiers of the queen in their own right rather than a pile of loot - each of them
     * takes over the owner, the rank and everything the body had been told to do:
     *
     * <ul>
     * <li>the owner, so they answer to the same queen,</li>
     * <li>the veterancy, so three veterans stand up where one veteran fell,</li>
     * <li>the long term order with its anchor, so a body that was told to hold a position leaves three spiders holding
     * it, and</li>
     * <li>the task, so a body that was walking somewhere or had a victim forced on it hands that same walk and that same
     * victim to the brood.</li>
     * </ul>
     *
     * <p>An order outlives the one who received it - that is the whole point of this creature.
     */
    private void spawnBroodOfTheCorpse()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }

        for (int i = 0; i < BROOD_ON_DEATH; ++i)
        {
            ImpSpiderEntity imp = HungryCraftEntities.IMP_SPIDER.get().create(level);

            if (imp == null)
            {
                continue;
            }

            double angle = (Math.PI * 2.0D / (double) BROOD_ON_DEATH) * (double) i;

            imp.moveTo(this.getX() + Math.cos(angle) * BROOD_SPAWN_RADIUS, this.getY(),
                    this.getZ() + Math.sin(angle) * BROOD_SPAWN_RADIUS, this.getYRot(), 0.0F);

            imp.setOwnerUUID(this.getOwnerUUID());
            imp.readVeterancyTag(this.writeVeterancyTag());
            imp.setOrderMode(this.getOrderMode());

            if (this.getOrderAnchor() != null)
            {
                imp.holdAt(this.getOrderAnchor());
            }

            if (this.getForcedTarget() != null)
            {
                imp.setForcedTarget(this.getForcedTarget());
            }
            else if (this.getTemporaryCommand() == SpiderTemporaryCommand.MOVE_TO && this.getMoveAnchor() != null)
            {
                imp.setMoveTo(this.getMoveAnchor(), this.getMoveCommandId());
            }

            level.addFreshEntity(imp);
        }
    }

}
