package spiderqueenhungrycraft.brood;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.ability.GiantTridentAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;

/**
 * The drowned spider (Drowned Spider round): the heavy ranged spider of the brood.
 *
 * <p>Hatched from a cocoon that digested a {@code minecraft:drowned}. It is a spider of the queen like every other one -
 * same owner, same sceptre commands, same follow, guard and patrol, same temporary attack order, same revenge for its
 * queen, same help when its queen fights, and the same faction answers for friend and foe. None of that is written here:
 * all of it comes from {@link SpiderMinionEntity} and from the ranged spider it is built on, and this class only says
 * what makes it a drowned spider.
 *
 * <p><b>Its weapon is a trident.</b> It fires a real, flying trident of the game - not an arrow with another texture -
 * every {@value #ATTACK_INTERVAL_TICKS} ticks, six points of damage, and it does it from up to
 * {@value #ATTACK_RANGE} blocks away. Its trident flies straight through the allies of its own queen, so a spider
 * standing in the line of fire is never in the way.
 *
 * <p><b>Its ability is the giant one</b> ({@link GiantTridentAbility}): on the command of its queen it throws a trident
 * three times the size at the place she picked, twenty points of damage and a clap of thunder where it lands. The AI
 * never casts it on its own.
 */
public class DrownedSpiderEntity extends SkeletonSpiderEntity
{
    /** Thirty five health: tougher than a skeleton spider, less than a chrono spider. */
    public static final double MAX_HEALTH = 35.0D;

    /** The damage of one thrown trident: six, and the same at any range. */
    public static final double ATTACK_DAMAGE = 6.0D;

    /** Two points of armour, the only spider of the brood that wears anything. */
    public static final double ARMOR = 2.0D;

    /**
     * The reach of its trident and of its eyes: thirty five blocks, which is also the range at which it finds an enemy
     * on its own. A target the queen ordered by hand is never dropped for distance - that rule lives in the base class.
     */
    public static final double FOLLOW_RANGE = 35.0D;

    /** One trident every twenty ticks: one per second, faster than the bow of its skeleton cousin. */
    public static final int ATTACK_INTERVAL_TICKS = 20;

    /** The range at which it stops walking and throws. */
    public static final float ATTACK_RANGE = 35.0F;

    /** The speed of its throw, and the spread it is thrown with. */
    public static final float TRIDENT_SPEED = 2.5F;
    public static final float TRIDENT_INACCURACY = 1.0F;

    /**
     * The gravity a thrown trident of the game falls with, in blocks per tick per tick.
     *
     * <p>It is the same 0.05 the arrows of the game use, and it is written down here because the aim of this spider has to
     * solve for it: see {@link #dropOver(double)}.
     */
    public static final double TRIDENT_GRAVITY = 0.05D;

    /** The 1.4 x 0.9 hitbox of a vanilla spider, like every spider of the brood. */
    public static final float COLLISION_WIDTH = SkeletonSpiderEntity.COLLISION_WIDTH;
    public static final float COLLISION_HEIGHT = SkeletonSpiderEntity.COLLISION_HEIGHT;

    public DrownedSpiderEntity(EntityType<? extends DrownedSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** Thirty five health, six damage, two armour and thirty five blocks of reach. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE)
                .add(Attributes.ARMOR, ARMOR);
    }

    /** One throw per second, and thirty five blocks away. */
    @Override
    protected int attackIntervalTicks()
    {
        return ATTACK_INTERVAL_TICKS;
    }

    @Override
    protected float attackRange()
    {
        return ATTACK_RANGE;
    }

    /** The giant thunder trident: a command of the queen, never something this spider does on its own. */
    @Override
    public SpiderAbility getAbility()
    {
        return GiantTridentAbility.INSTANCE;
    }

    /**
     * Throws one trident at that victim.
     *
     * <p>It replaces the arrow and the web shot of its skeleton cousin completely: a drowned spider has one weapon and
     * no ammunition to switch, so there is nothing to branch on here.
     */
    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor)
    {
        Vec3 from = new Vec3(this.getX(), this.getEyeY() - 0.1D, this.getZ());
        SpiderTrident trident = new SpiderTrident(this.level(), this, SpiderTrident.Mode.NORMAL);
        trident.setPos(from.x, from.y, from.z);

        double dx = target.getX() - from.x;
        double dy = target.getY(1.0D / 3.0D) - from.y;
        double dz = target.getZ() - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        // The aim of a throwing spider (Drowned Spider round, aim fix): a trident of the game falls on its way, so the
        // throw has to be raised by exactly the height it will have lost when it arrives. The number is solved for the
        // distance of this very throw instead of borrowed from the bow of a skeleton: the old constant of 0.2 is tuned
        // for an arrow that flies at 1.6 and therefore lifts this trident far above anything it aims at, which is why the
        // shots kept sailing over their victims.
        trident.shoot(dx, dy + dropOver(flat), dz, TRIDENT_SPEED, TRIDENT_INACCURACY);

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TRIDENT_THROW,
                SoundSource.HOSTILE, 0.8F, 1.0F);
        this.level().addFreshEntity(trident);
    }

    /**
     * @return how far below the straight line a trident falls over that distance, in blocks
     *
     * <p>Half of the gravity times the flight time squared, with the flight time being the distance divided by the speed
     * of the throw. The value the game needs in the aim vector is the same number: the flight time of a straight shot is
     * distance over speed, so raising the aim by exactly this much cancels the fall out at the moment of arrival.
     */
    public static double dropOver(double distance)
    {
        double flightTicks = distance / (double) TRIDENT_SPEED;

        return 0.5D * TRIDENT_GRAVITY * flightTicks * flightTicks;
    }
}
