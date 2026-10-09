package spiderqueenhungrycraft.brood.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.DrownedSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderTrident;

/**
 * The giant thunder trident of the drowned spider (Drowned Spider round): the one ability of that species, and the only
 * way it ever fires the big one.
 *
 * <p>It is cast through the ability system of the sceptre that every other spider of the brood already uses: the queen
 * looks at a place, the server casts its own command ray and hands the position to the spiders that answer, and the
 * spider fires from where it stands at exactly that place. There is no second aiming system in here and nothing of the
 * aim is ever sent by a client - the position comes from the server's own ray.
 *
 * <p><b>The range of an ordinary shot does not apply.</b> The trident flies as far as the point the queen picked, which
 * is how far the command ray of the sceptre itself reaches. The ability does not teleport anything to the target and
 * does not spawn anything at the target: the trident really travels, so the shot can be seen leaving the spider.
 *
 * <p>It is a <b>player command</b>, never a behaviour of the AI: a drowned spider fires its ordinary tridents on its own
 * and this one only when its queen asks for it.
 */
public final class GiantTridentAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state, the spider holds the cooldown. */
    public static final GiantTridentAbility INSTANCE = new GiantTridentAbility();

    /** The stable id of this ability. */
    public static final String ID = "giant_trident";

    /** Ten seconds of rest after a cast: it is the heaviest thing a spider of the brood can do. */
    public static final int COOLDOWN_TICKS = 200;

    /** The pitch of the mortar throw of the giant trident: steep enough to be a lob, flat enough to be aimed. */
    public static final double LAUNCH_PITCH_RAD = Math.toRadians(55.0D);

    /** The gravity a thrown trident of the game falls with, in blocks per tick per tick. */
    public static final double GRAVITY = 0.05D;

    /**
     * The drag of a projectile of the game in the air: it loses one percent of its speed every tick.
     *
     * <p>This is the number that the old closed formula ignored, and it is the whole reason the giant trident kept landing
     * short of the place the queen had picked - the longer the flight, the more of the range the air eats, so the error
     * grew from eight percent at twenty blocks to more than twenty percent at sixty four.
     */
    public static final double AIR_DRAG = 0.99D;

    /** The fastest and the slowest a giant trident is ever thrown with. */
    public static final double MIN_SPEED = 0.35D;
    public static final double MAX_SPEED = 4.0D;

    /** How fine the launch speed is searched for, and how long one simulated flight may take. */
    public static final int SPEED_STEPS = 30;
    public static final int MAX_FLIGHT_TICKS = 600;

    private GiantTridentAbility()
    {
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public Component displayName()
    {
        return Component.translatable("ability.spiderqueen_hungrycraft.giant_trident");
    }

    /** It needs a place: the spot the sceptre of the queen points at. */
    @Override
    public SpiderAbilityTarget targetRequirement()
    {
        return SpiderAbilityTarget.POSITION;
    }

    @Override
    public int cooldownTicks()
    {
        return COOLDOWN_TICKS;
    }

    @Override
    public boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        return spider instanceof DrownedSpiderEntity && context.targetPosition() != null;
    }

    /** Fires one giant trident from the mouth of that spider at the place its queen picked, on a mortar arc. */
    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        if (!(spider instanceof DrownedSpiderEntity) || context.targetPosition() == null)
        {
            return false;
        }

        Vec3 from = new Vec3(spider.getX(), spider.getEyeY() - 0.1D, spider.getZ());
        Vec3 aim = context.targetPosition();
        double dx = aim.x - from.x;
        double dz = aim.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        double height = aim.y - from.y;

        if (flat < 0.5D)
        {
            // The queen pointed at the spider itself: no shot, and no cooldown spent on nothing.
            return false;
        }

        double speed = launchSpeed(flat, height);

        if (speed <= 0.0D)
        {
            // Even a mortar cannot reach this: the ability is not spent on a shot that would only fall short.
            return false;
        }

        // The horizontal part of the unit vector points at the place, the vertical part is the pitch of the throw. The
        // speed solved above is what makes that arc arrive there - a lob that lands on the spot instead of a flat throw
        // that has to be aimed high and hope, which is what kept missing.
        double horizontal = Math.cos(LAUNCH_PITCH_RAD);
        double vertical = Math.sin(LAUNCH_PITCH_RAD);

        SpiderTrident trident = new SpiderTrident(spider.level(), spider, SpiderTrident.Mode.GIANT);
        trident.setPos(from.x, from.y, from.z);
        trident.shoot(dx / flat * horizontal, vertical, dz / flat * horizontal, (float) speed, 0.0F);
        spider.level().addFreshEntity(trident);

        spider.level().playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THROW, SoundSource.HOSTILE, 1.0F,
                0.8F);

        return true;
    }

    /**
     * @return the launch speed with which the giant trident really lands on that place, or 0 when even the mortar cannot
     *         reach it
     *
     * <p><b>It is not a closed formula any more, and that is the fix.</b> The flight of a projectile of the game is
     * discrete: it loses one percent of its speed to the air every tick and its gravity is applied at the end of a tick
     * rather than continuously. Both cost range, and the longer the flight the more they cost - which is why the giant
     * trident used to land eight percent short at twenty blocks, thirteen at thirty five and twenty one percent at sixty
     * four: the ordinary textbook solution knows nothing about any of it.
     *
     * <p>So the flight is <b>flown</b> here instead of solved: the same throw is simulated with the very same gravity and
     * drag the entity will use a moment later, over and over with a different speed, until the arc comes down exactly on
     * the place the queen picked. The search is a bisection because a faster throw always lands further at a fixed pitch.
     */
    private static double launchSpeed(double distance, double height)
    {
        // The slowest throw overshoots nothing and the fastest one still falls short: this place is out of reach, and the
        // ability spends nothing on a shot that could only land somewhere else.
        if (landingDistance(MAX_SPEED, height) < distance)
        {
            return 0.0D;
        }

        double low = MIN_SPEED;
        double high = MAX_SPEED;

        for (int step = 0; step < SPEED_STEPS; step++)
        {
            double middle = (low + high) * 0.5D;

            if (landingDistance(middle, height) < distance)
            {
                low = middle;
            }
            else
            {
                high = middle;
            }
        }

        return (low + high) * 0.5D;
    }

    /**
     * @return how far one such throw flies before it comes back down to the height it was aimed at
     *
     * <p>One tick at a time, in the order the game does it: the speed loses its drag, the trident moves, and the gravity
     * is taken off the speed for the next tick. The flight is done when the arc is on its way down and has reached the
     * height of the place it was aimed at - a mortar lands on the way down, never on the way up.
     */
    private static double landingDistance(double speed, double height)
    {
        double horizontal = speed * Math.cos(LAUNCH_PITCH_RAD);
        double vertical = speed * Math.sin(LAUNCH_PITCH_RAD);
        double travelled = 0.0D;
        double climbed = 0.0D;

        for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++)
        {
            horizontal *= AIR_DRAG;
            vertical *= AIR_DRAG;
            travelled += horizontal;
            climbed += vertical;
            vertical -= GRAVITY;

            if (climbed <= height && vertical < 0.0D)
            {
                return travelled;
            }
        }

        return travelled;
    }
}
