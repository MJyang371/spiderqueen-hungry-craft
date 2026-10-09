package spiderqueenhungrycraft.brood;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.AmmoSwitchAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;
import spiderqueenhungrycraft.brood.goal.SpiderRangedAttackGoal;
import spiderqueenhungrycraft.web.WebElement;
import spiderqueenhungrycraft.web.WebShotProjectile;
import spiderqueenhungrycraft.web.WebVolleyTracker;

/**
 * The skeleton spider (Special Spiders round I): the ranged spider of the brood.
 *
 * <p>Hatched from a cocoon that digested a {@code minecraft:skeleton}. It is a {@link SpiderMinionEntity} like every
 * other spider of the brood - same ownership, same orders, same commands, same faction, same carrier - and it is
 * deliberately <b>not</b> a skeleton: no bony AI, no sun damage, no skeleton loot, no bow. Only its colours and its
 * ammunition are borrowed from the skeleton it came from.
 *
 * <p><b>It shoots, it never melees.</b> Its melee goal is removed and replaced by the vanilla ranged attack goal, so
 * the spider keeps its distance and fires one projectile every {@value #ATTACK_INTERVAL_TICKS} ticks.
 *
 * <p><b>Two ranges, two rules.</b> The twenty blocks of {@link #ATTACK_RANGE} are the range at which this spider
 * <i>finds</i> an enemy on its own, and they are the follow range of the game. A target the queen ordered by hand (the
 * forced attack of the sceptre) is never dropped for distance: the spider walks to it, exactly like the ranged attack
 * goal of the game walks to its victim, and starts firing the moment it is close enough.
 */
public class SkeletonSpiderEntity extends SpiderMinionEntity implements RangedAttackMob
{
    /** The health of a skeleton: this spider is a ranged attacker, not a brawler. */
    public static final double MAX_HEALTH = 20.0D;

    /** The damage of one shot: five, whatever the spider is holding. */
    public static final double ATTACK_DAMAGE = 5.0D;

    /**
     * The automatic target acquisition range: twenty blocks. This is the range at which the spider searches for an
     * enemy by itself, which is the follow range of the game - and nothing else.
     */
    public static final double FOLLOW_RANGE = 20.0D;

    /** One shot every thirty ticks: one and a half seconds, for both kinds of ammunition. */
    public static final int ATTACK_INTERVAL_TICKS = 30;

    /** The range at which this spider stops walking and starts firing. */
    public static final float ATTACK_RANGE = 20.0F;

    /** The speed of an arrow of the game, and the speed of a web shot of the queen. */
    public static final float ARROW_SPEED = 1.6F;
    public static final float WEB_SPEED = 2.2F;

    /**
     * The ammunition of this species, in the order its switch cycles through it (Stray and Husk Spider round).
     *
     * <p>The list belongs to the species and not to the enum: a stray spider fires the very same web as its skeleton
     * cousin, but its arrow is a chilled one, so its cycle is a different one. A species with one kind of shot only has
     * to return a list of one entry and the switch becomes a no-op.
     */
    private static final SpiderAmmo[] AMMO_MODES = { SpiderAmmo.ARROW, SpiderAmmo.WEB };

    /**
     * The spread of a shot, in degrees.
     *
     * <p>Deliberately small (accuracy pass): a skeleton of the game fires with a spread of ten to fourteen degrees, which
     * is fine for a mob that shoots at a player right in front of it, but this spider fires from up to twenty blocks
     * away and has to be worth the twenty blocks. Two degrees still leave a hit at range to chance without ever making
     * the spider miss a standing target in front of it.
     */
    public static final float ARROW_INACCURACY = 1.5F;
    public static final float WEB_INACCURACY = 0.5F;

    /** The 1.4 x 0.9 hitbox of a vanilla spider: it is a spider first. */
    public static final float COLLISION_WIDTH = 1.4F;
    public static final float COLLISION_HEIGHT = 0.9F;

    /** NBT key of the loaded ammunition. */
    private static final String KEY_AMMO = "SpiderAmmo";

    /** The ammunition this spider has loaded, synchronised so the HUD of the queen can name it. */
    private static final EntityDataAccessor<Integer> DATA_AMMO = SynchedEntityData
            .defineId(SkeletonSpiderEntity.class, EntityDataSerializers.INT);

    public SkeletonSpiderEntity(EntityType<? extends SkeletonSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The attributes of a spider of the brood, with twenty blocks of automatic target range. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE).add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    /** The ammunition switch of this spider: the ability key of the queen loads the other kind of shot. */
    @Override
    public SpiderAbility getAbility()
    {
        return AmmoSwitchAbility.INSTANCE;
    }

    /**
     * The walking of a spider that shoots: it climbs, but it does not charge.
     *
     * <p>A vanilla spider walks with {@code WallClimberNavigation}, whose fallback does not give up when it finds no
     * path: it pushes straight at its victim, tick after tick, through gaps, ponds and walls. That fallback is what
     * makes a spider climb a wall (the body only climbs while it is pressed against one) - and it is also what kept
     * this spider walking into melee range no matter what its shooting goal wanted. A plain {@code GroundPathNavigation}
     * (the previous pass) stopped the charge and took the climbing with it.
     *
     * <p>{@link SpiderClimbNavigation} keeps both: ordinary ground pathing whenever a path exists, the climbing push
     * only where it is really a climb (the victim above the spider, or the spider already at a wall), and nothing at all
     * on open ground where the spider should instead stand still and shoot like the skeleton it was bred from.
     */
    @Override
    protected PathNavigation createNavigation(Level level)
    {
        return new SpiderClimbNavigation(this, level);
    }

    /**
     * The shot of this spider replaces the melee of a spider, and the pounce of a spider goes with it.
     *
     * <p>Two things of the vanilla spider had to go for this one to behave like the skeleton of the game:
     *
     * <ul>
     * <li><b>The melee goal</b> (and with it the leap attack of the spider), because this spider fights at range.</li>
     * <li><b>The pounce itself.</b> {@code LeapAtTargetGoal} throws the spider at its victim with the jump flag
     * <i>and</i> the move flag, and it sits at the same priority as the shooting below. Because it is registered first,
     * it wins the move flag every time it fires: a spider that stops to shoot would still be thrown forward, hop by hop,
     * until it stood in melee range - which is exactly the "it keeps closing in instead of shooting from a distance"
     * this pass removes. A shooting spider does not pounce.</li>
     * </ul>
     *
     * <p>What shoots in its place is {@link SpiderRangedAttackGoal} on a better priority than every move goal of the
     * brood, so nothing can pull the spider away while it aims: it walks towards the victim while it is out of range,
     * stands still once it is inside {@value #ATTACK_RANGE} blocks and fires every {@value #ATTACK_INTERVAL_TICKS}
     * ticks - from a distance, never in melee, and without the eleven block vision limit of the senses of a mob, which
     * would otherwise make it walk into the face of its victim before it could ever shoot.
     */
    @Override
    protected void registerGoals()
    {
        super.registerGoals();

        this.goalSelector.removeAllGoals(goal -> goal instanceof MeleeAttackGoal);
        this.goalSelector.removeAllGoals(goal -> goal instanceof LeapAtTargetGoal);
        this.goalSelector.addGoal(2, new SpiderRangedAttackGoal(this, 1.0D, this.attackIntervalTicks(), this.attackRange()));
    }

    /**
     * @return the ticks between two shots of this species.
     *
     * <p>Overridable because the numbers of a spider are not what makes it a species but they are what a species is
     * allowed to differ in (Drowned Spider round): a drowned spider fires every second, its skeleton cousin every one
     * and a half.
     */
    protected int attackIntervalTicks()
    {
        return ATTACK_INTERVAL_TICKS;
    }

    /** @return the range at which this species stops walking and starts firing. */
    protected float attackRange()
    {
        return ATTACK_RANGE;
    }

    // ------------------------------------------------------------------ ammunition

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.getEntityData().define(DATA_AMMO, this.defaultAmmo().ordinal());
    }

    /** @return the ammunition this spider has loaded right now, on both sides. */
    public SpiderAmmo getAmmo()
    {
        return SpiderAmmo.byId(this.getEntityData().get(DATA_AMMO));
    }

    /** Loads that ammunition. */
    public void setAmmo(SpiderAmmo ammo)
    {
        this.getEntityData().set(DATA_AMMO, ammo.ordinal());
    }

    /**
     * Loads the next kind of shot of the cycle of this species.
     *
     * @return the ammunition that is loaded afterwards - the one the queen has to be told about
     */
    public SpiderAmmo cycleAmmo()
    {
        SpiderAmmo next = SpiderAmmo.next(this.getAmmo(), this.ammoModes());
        this.setAmmo(next);

        return next;
    }

    /** @return the ammunition of this species, in the order its switch cycles through it. */
    protected SpiderAmmo[] ammoModes()
    {
        return AMMO_MODES;
    }

    /** @return the ammunition a freshly hatched spider of this species loads first. */
    protected SpiderAmmo defaultAmmo()
    {
        return SpiderAmmo.DEFAULT;
    }

    /**
     * Prepares one arrow of this spider before it is shot (Stray and Husk Spider round).
     *
     * <p>Empty for this species: a skeleton spider fires a plain arrow of the game and nothing else. A species whose
     * arrow carries something - the chill of a stray spider - only has to add it here, and the shot itself, the aim and
     * the rhythm stay exactly the same.
     */
    protected void prepareArrow(SpiderArrow arrow)
    {
        // A plain arrow: nothing to prepare for this species.
    }

    /**
     * The ammunition of this spider is gear, and it travels with a carrier (Carrier Gear round).
     *
     * <p>A skeleton spider that was switched to web and then packed away used to come back with a plain arrow loaded,
     * which is the same bug the armour of an infested Steve had: the carrier kept the spider and forgot what the spider
     * had chosen. The ammunition is written under the very key this spider already uses in its own save file, so the
     * carrier tag and the save file of the game speak the same language.
     */
    @Override
    public void writeCarriedGear(CompoundTag tag)
    {
        tag.putString(KEY_AMMO, this.getAmmo().name());
    }

    /** Loads the ammunition a carrier kept for this spider. */
    @Override
    public void readCarriedGear(CompoundTag tag)
    {
        if (tag.contains(KEY_AMMO))
        {
            this.setAmmo(SpiderAmmo.byName(tag.getString(KEY_AMMO)));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(KEY_AMMO, this.getAmmo().name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.contains(KEY_AMMO))
        {
            this.setAmmo(SpiderAmmo.byName(tag.getString(KEY_AMMO)));
        }
    }

    // ------------------------------------------------------------------ shooting

    /** One attack of this spider: an arrow or a web, depending on what is loaded. */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor)
    {
        if (this.getAmmo() == SpiderAmmo.WEB)
        {
            this.fireWebShot(target);
            return;
        }

        this.fireArrow(target);
    }

    /**
     * A vanilla arrow, aimed the way a skeleton of the game aims.
     *
     * <p>No arrow item is added to the mod and no new arrow texture either: the arrow entity of the game is used as it
     * is and drawn by the vanilla arrow renderer, and the only thing this spider adds to it is the flight through its
     * own allies (see {@link SpiderArrow}).
     *
     * <p>The damage of the shot is read from the <b>live attack damage of this spider</b> and divided by the launch
     * speed. Two things follow from that, and both are wanted:
     *
     * <ul>
     * <li>An arrow of the game scales its damage with the speed it still carries on impact, so dividing by the launch
     * speed is what makes a shot at full speed deal exactly the attack damage of this spider (five, at rank one), and
     * a shot that has already lost speed to gravity deals a little less - the ordinary behaviour of a vanilla arrow.
     * <li>Reading the attribute instead of a constant is what lets a <b>veteran or elite skeleton spider shoot harder
     * as well</b> (Brood Veterancy round): its rank raises its attack damage, and this line is where that rank reaches
     * the arrow. A constant here would have left the rank of a ranged spider with no effect at all.
     * </ul>
     */
    private void fireArrow(LivingEntity target)
    {
        SpiderArrow arrow = new SpiderArrow(this.level(), this);

        // The one hook a species uses to load something into its arrow before it leaves the bow.
        this.prepareArrow(arrow);

        double dx = target.getX() - this.getX();
        double dy = target.getY(1.0D / 3.0D) - arrow.getY();
        double dz = target.getZ() - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);

        arrow.setBaseDamage(this.getAttributeValue(Attributes.ATTACK_DAMAGE) / (double) ARROW_SPEED);
        arrow.shoot(dx, dy + flat * 0.2D, dz, ARROW_SPEED, ARROW_INACCURACY);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE, 0.8F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.9F));
        this.level().addFreshEntity(arrow);
    }

    /**
     * The ordinary web shot of the spider queen, fired by this spider.
     *
     * <p>It is the same entity and therefore the same rules: the capture roll, Webbed, the temporary web block, the
     * pack provocation. Not a single line of that is copied for this spider and no new ammunition item exists for it
     * either.
     *
     * <p>The aim is the reason this is not a copy of the arrow below: a web shot of the queen has <b>no gravity at
     * all</b>, so it flies in a straight line. Any "aim a little higher for the drop" term would therefore make it fly
     * over the victim, which is exactly what ruined the aim of web mode. It is aimed straight at the chest.
     */
    private void fireWebShot(LivingEntity target)
    {
        WebShotProjectile shot = new WebShotProjectile(this.level(), this);

        shot.setElement(WebElement.NORMAL);
        shot.setVolleyId(WebVolleyTracker.NO_VOLLEY);
        shot.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double dx = target.getX() - shot.getX();
        double dy = target.getY(1.0D / 3.0D) - shot.getY();
        double dz = target.getZ() - shot.getZ();

        shot.shoot(dx, dy, dz, WEB_SPEED, WEB_INACCURACY);

        this.level().addFreshEntity(shot);
    }
}
