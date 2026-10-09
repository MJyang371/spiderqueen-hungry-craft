package spiderqueenhungrycraft.brood.ability;

import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.brood.ChronoSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.goal.SpiderFollowOwnerGoal;

/**
 * The chrono shift of the chrono spider (Special Spiders round I): the one and only way a chrono spider ever teleports.
 *
 * <p>The spider teleports <b>wherever the queen points</b>, not at random, not away from a fight and never on its own:
 * the position comes from the command ray of the sceptre, the very same ray the sceptre already casts for its other
 * commands - there is no second raycast anywhere. A chrono spider that is not ordered around just walks like every
 * other spider of the brood.
 *
 * <p><b>Nothing happens at all if the spider cannot land there.</b> The destination has to pass the placement rule of
 * the whole brood (free space, ground underneath, no liquid, inside the world border) plus a free collision box for the
 * spider itself, and the neighbourhood of the aimed spot is searched a little before the cast is called impossible.
 * And because the cast only reports true when the spider really jumped, a queen who aimed at a bad place loses
 * nothing: no cooldown, no spider out of position.
 *
 * <p><b>The jump is also an order.</b> A chrono shift leaves the spider exactly the order the sceptre gives when the
 * queen right clicks a block: it belongs at the place it was sent to. That is what keeps the two mechanics from fighting
 * each other - a spider that is on a move is under the sceptre and not under its follow goal, so the emergency
 * relocation of a follower that fell more than thirty two blocks behind can never drag it home again in the tick it
 * arrived. The queen sends it there by teleport instead of on foot, and there it stays until she says otherwise.
 */
public final class ChronoShiftAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state, the spider holds the cooldown. */
    public static final ChronoShiftAbility INSTANCE = new ChronoShiftAbility();

    /** The stable id of this ability. */
    public static final String ID = "chrono_shift";

    /** Six seconds of rest after a successful jump. */
    public static final int COOLDOWN_TICKS = 120;

    /**
     * The command ids of the jumps of this ability.
     *
     * <p>They are deliberately counted <b>down</b> from minus one, because the move ids of the sceptre count up from
     * zero: a marker of the sceptre must never be cleared by a jump, and a jump must never clear a marker it did not
     * place. Nothing else depends on the value - it exists so that the two kinds of move stay apart.
     */
    private static final AtomicInteger NEXT_SHIFT_ID = new AtomicInteger(-1);

    /** How many spots around the aimed position are tried before the jump is called impossible. */
    private static final int DESTINATION_ATTEMPTS = 12;

    /** The portal burst of an enderman jump, at both ends. */
    private static final int TELEPORT_PARTICLES = 40;
    private static final double PARTICLE_SPREAD = 0.5D;

    private ChronoShiftAbility()
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
        return Component.translatable("ability.spiderqueen_hungrycraft.chrono_shift");
    }

    /** This ability needs a place: the block the sceptre of the queen points at. */
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
        return spider instanceof ChronoSpiderEntity && context.targetPosition() != null;
    }

    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        if (!(spider instanceof ChronoSpiderEntity) || !(spider.level() instanceof ServerLevel level)
                || context.targetPosition() == null)
        {
            return false;
        }

        BlockPos destination = destination(level, spider, context.targetPosition());

        if (destination == null)
        {
            // Nowhere to stand. The spider does not move and the queen is told why, so a bad aim costs her nothing.
            tellFailure(spider, "message.spiderqueen_hungrycraft.chrono_shift_failed");

            return false;
        }

        Vec3 from = spider.position();
        double x = destination.getX() + 0.5D;
        double y = destination.getY();
        double z = destination.getZ() + 0.5D;

        // Both ends of the jump: the portal burst and the teleport sound of the game, sent by the server, so every
        // player around the old and the new place sees and hears them - not only the queen who pressed the key.
        jumpEffects(level, from.x, from.y + spider.getBbHeight() * 0.5D, from.z);
        spider.getNavigation().stop();
        spider.teleportTo(x, y, z);
        jumpEffects(level, x, y + spider.getBbHeight() * 0.5D, z);

        // The same order the sceptre gives when the queen right clicks the ground: this is where the spider belongs now.
        // It stays there instead of walking home, and, just as important, the follow goal keeps its hands off it - a
        // spider that is on a move belongs to the sceptre, so the emergency relocation of a follower that fell more than
        // thirty two blocks behind can never grab it again in the very tick it arrived.
        spider.setMoveTo(destination, NEXT_SHIFT_ID.getAndDecrement());

        return true;
    }

    /** One end of the jump: the portal particles and the teleport sound of an enderman. */
    private static void jumpEffects(ServerLevel level, double x, double y, double z)
    {
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, TELEPORT_PARTICLES, PARTICLE_SPREAD, PARTICLE_SPREAD,
                PARTICLE_SPREAD, 0.05D);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 1.0F);
    }

    /** Action bar feedback for the queen of that spider, and nothing for a spider without a queen. */
    private static void tellFailure(SpiderMinionEntity spider, String messageKey)
    {
        Player owner = spider.getOwnerPlayer();

        if (owner instanceof ServerPlayer queen && queen.connection != null)
        {
            queen.displayClientMessage(Component.translatable(messageKey), true);
        }
    }

    /**
     * @return a block this spider may really be set down on near the aimed spot, or null when there is none.
     *
     * <p>It uses the one placement rule of the whole brood ({@link SpiderFollowOwnerGoal#findSafeSpot}: free space,
     * ground underneath, no liquid, inside the world border) plus one check that the follow goal never needs, because
     * that one never lands right next to a wall on purpose: the collision box of this very spider has to be free, so a
     * jump can never end inside a fence, a wall or iron bars.
     *
     * <p>The aimed block is tried from above first, because a queen usually aims at a floor. Only then is the small
     * neighbourhood of the aimed spot searched, and never a large one: this is a placement, not a journey.
     */
    @Nullable
    private static BlockPos destination(ServerLevel level, SpiderMinionEntity spider, Vec3 aim)
    {
        BlockPos aimed = BlockPos.containing(aim);

        if (accepts(level, spider, aimed.above()))
        {
            return aimed.above();
        }

        for (int attempt = 0; attempt < DESTINATION_ATTEMPTS; attempt++)
        {
            BlockPos spot = SpiderFollowOwnerGoal.findSafeSpot(level, aimed);

            if (spot != null && accepts(level, spider, spot))
            {
                return spot;
            }
        }

        return null;
    }

    /** @return true when this spider may stand there: the safe spot rule of the brood, loaded, and a free body. */
    private static boolean accepts(ServerLevel level, SpiderMinionEntity spider, BlockPos pos)
    {
        return SpiderFollowOwnerGoal.isSafeSpot(level, pos) && level.isLoaded(pos)
                && level.noCollision(spider, bodyAt(spider, pos));
    }

    /** @return the collision box that spider would have if it stood on that block. */
    private static AABB bodyAt(SpiderMinionEntity spider, BlockPos pos)
    {
        Vec3 center = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);

        return spider.getBoundingBox().move(center.subtract(spider.position()));
    }
}
