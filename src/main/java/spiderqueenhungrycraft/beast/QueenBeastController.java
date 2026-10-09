package spiderqueenhungrycraft.beast;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.movement.SimpleWallClimbController;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CQueenBeastStatePacket;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.stomach.StomachRegurgitation;

/**
 * The server authoritative logic of the Queen Beast form (Phase 8A).
 *
 * <p>Every entry into and every exit out of the form goes through this class, so exactly one place sets the
 * state, hands out the attributes, clears the room, changes the body size and syncs the result. The client
 * only asks for a transformation with a single key press; how many presses are needed, whether the player is
 * allowed and what happens afterwards is decided here from the real player state.
 *
 * <p>The transformation never fails because of the environment: the room around the player is cleared after
 * the state changed, and when protected or unbreakable blocks still block the full body, the beast simply
 * stays compressed until the player made room with its own claws.
 */
public final class QueenBeastController
{
    /** Why a beast form ended, used for the exit feedback. */
    public enum RevertCause
    {
        /** The player pressed the transform key once while transformed. */
        MANUAL,
        /** The hunger bar reached zero, the beast form ran out of fuel. */
        STARVING,
        /** The player died, the next life always starts in the normal form. */
        DEATH,
        /** The player is not the Spider Queen any more, for example after a command. */
        NOT_QUEEN,
        /** Housekeeping while the form is brought in line with the real player state. */
        RECONCILE
    }

    private static final String MESSAGE_TAP = "message.spiderqueen_hungrycraft.beast_tap";
    private static final String MESSAGE_REQUIRES_HUNGER = "message.spiderqueen_hungrycraft.beast_requires_full_hunger";
    private static final String MESSAGE_ON = "message.spiderqueen_hungrycraft.beast_on";
    private static final String MESSAGE_OFF = "message.spiderqueen_hungrycraft.beast_off";

    /** Transform burst: a handful of big explosions plus a dust cloud, all of it purely cosmetic. */
    private static final int TRANSFORM_EMITTER_PARTICLES = 1;
    private static final int TRANSFORM_BURST_PARTICLES = 10;
    private static final double TRANSFORM_BURST_SPREAD = 1.2D;
    private static final int TRANSFORM_CLOUD_PARTICLES = 20;
    private static final double TRANSFORM_CLOUD_SPREAD = 0.9D;

    /** Revert burst, deliberately lighter than the transformation. */
    private static final int REVERT_PUFF_PARTICLES = 20;
    private static final double REVERT_PUFF_SPREAD = 0.7D;
    private static final int REVERT_SMOKE_PARTICLES = 10;

    private QueenBeastController()
    {
    }

    /**
     * Client request: the transform key was pressed once. Nothing about the counting arrives from the client,
     * the whole triple tap runs here.
     */
    public static void onTransformKeyPressed(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null || !QueenDataCapability.isQueen(player))
        {
            if (state != null)
            {
                state.resetTransformTaps();
            }
            return;
        }

        if (!player.isAlive() || player.isSpectator())
        {
            state.resetTransformTaps();
            return;
        }

        // Leaving the form is always allowed and never needs three presses or a full hunger bar.
        if (state.isTransformed())
        {
            untransform(player);
            return;
        }

        // The form itself is the Queen Beast node (Skill Progression round). The gate sits in front of the tap counter
        // and in front of every line of feedback, so a locked key is completely silent: it does not count a first or a
        // second tap, it never asks for a full hunger bar and it never transforms. Leaving a form that is already
        // entered stays above this check, so a queen whose node was taken away can always step out of it.
        if (!QueenSkills.has(player, QueenSkills.QUEEN_BEAST))
        {
            state.resetTransformTaps();
            return;
        }

        if (player.isSleeping() || player.isFallFlying())
        {
            state.resetTransformTaps();
            return;
        }

        countTap(player, state);
    }

    /** The player asked to leave the beast form. */
    public static void untransform(ServerPlayer player)
    {
        revert(player, RevertCause.MANUAL);
    }

    /** A forced exit: no food left, death, or the queen flag is gone. */
    public static void forceUntransform(ServerPlayer player, RevertCause cause)
    {
        revert(player, cause);
    }

    /** @return true when that player is a beast right now. */
    public static boolean isTransformed(ServerPlayer player)
    {
        return QueenBeastState.isTransformed(player);
    }

    /**
     * Brings the real world in line with the state: used after a login, a dimension change and a respawn.
     * Everything here is idempotent, so a reconcile never stacks a modifier and never re-clears a room.
     */
    public static void reconcile(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        // A queen without the node can never be a beast: a form that was entered before the skill was taken away is
        // left here at once, which is what makes a revocation (and an administrator who takes the node back) take
        // effect on the body without a relog (Skill Progression round).
        if (!QueenDataCapability.isQueen(player) || !player.isAlive() || player.isSpectator()
                || !QueenSkills.has(player, QueenSkills.QUEEN_BEAST))
        {
            if (state.isTransformed())
            {
                forceUntransform(player, RevertCause.RECONCILE);
            }
            return;
        }

        if (!state.isTransformed())
        {
            QueenBeastAttributes.remove(player);
            state.setCompressed(false);
            player.refreshDimensions();
            return;
        }

        QueenBeastAttributes.apply(player);

        // The dimension the player arrives in decides the body again, but the room is never cleared a second
        // time: from here on the queen has to make room with her claws.
        state.setCompressed(!hasRoom(player));
        player.refreshDimensions();
        sync(player);
    }

    /** Sends the current form state to everyone who can see that player, including the player itself. */
    public static void sync(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        HungryCraftNetwork.sendToTrackingAndSelf(
                new S2CQueenBeastStatePacket(player.getId(), state.isTransformed(), state.isCompressed()), player);
    }

    /** Sends the current form state to one specific player, used when somebody starts tracking a beast. */
    public static void syncTo(ServerPlayer target, ServerPlayer receiver)
    {
        QueenBeastState state = QueenBeastState.of(target).orElse(null);

        if (state == null)
        {
            return;
        }

        HungryCraftNetwork.sendToPlayer(
                new S2CQueenBeastStatePacket(target.getId(), state.isTransformed(), state.isCompressed()), receiver);
    }

    /** One server tick of a player that might be a beast. Server only, never per tick on a client. */
    public static void tick(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        // Losing the queen flag always ends the form, whoever removed it.
        if (!QueenDataCapability.isQueen(player))
        {
            if (state.isTransformed())
            {
                forceUntransform(player, RevertCause.NOT_QUEEN);
            }
            return;
        }

        if (!state.isTransformed())
        {
            return;
        }

        // An empty hunger bar ends the form right away, that is the price of being a beast.
        if (player.getFoodData().getFoodLevel() <= 0)
        {
            forceUntransform(player, RevertCause.STARVING);
            return;
        }

        // A beast does not sprint: its own movement attribute already carries the whole pace, and the ordinary
        // sprint bonus on top of it would turn the giant spider into a race car.
        if (player.isSprinting())
        {
            player.setSprinting(false);
        }

        tickRegeneration(player, state);
        tickCompressed(player, state);

        // Phase 8C / 8D: the landing of a charged jump, the continuous charge and the soft obstacles a walk
        // pushes aside. All three are server side and all three are driven by the state of this form.
        QueenBeastStomp.tick(player, state);
        QueenBeastCharge.tick(player, state);
        QueenBeastBodyCrush.tick(player, state);
    }

    /** Server authoritative triple tap counting. Only the server ever decides how many presses happened. */
    private static void countTap(ServerPlayer player, QueenBeastState state)
    {
        long now = player.level().getGameTime();

        if (now > state.getTransformTapDeadline())
        {
            state.resetTransformTaps();
        }

        state.setTransformTapCount(state.getTransformTapCount() + 1);
        state.setTransformTapDeadline(now + QueenBeastConstants.TRANSFORM_TAP_WINDOW_TICKS);

        if (state.getTransformTapCount() < QueenBeastConstants.TRANSFORM_TAPS_REQUIRED)
        {
            player.displayClientMessage(Component.translatable(MESSAGE_TAP, state.getTransformTapCount()), true);
            return;
        }

        state.resetTransformTaps();

        if (player.getFoodData().getFoodLevel() < QueenBeastConstants.FULL_FOOD_LEVEL)
        {
            // A full hunger bar is the condition of the form, it is never a cost. The taps are thrown away so
            // the player has to mean it again once the bar is full.
            player.displayClientMessage(Component.translatable(MESSAGE_REQUIRES_HUNGER), true);
            return;
        }

        transform(player);
    }

    /**
     * Enters the beast form. There is deliberately no room check before the state changes: the environment
     * gives way to the queen. The clearance runs afterwards and only decides how large the body may be.
     */
    public static void transform(ServerPlayer player)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null || state.isTransformed() || !canTransform(player))
        {
            return;
        }

        state.setTransformed(true);
        state.setCompressed(false);
        state.resetTransientData();

        // The beast cannot climb walls, so the wall climb mode of the ordinary queen is really switched off and
        // synchronised, not merely ignored. Leaving the form never turns it back on: a player who wants to
        // climb again presses the key again.
        SimpleWallClimbController.setEnabled(player, false);

        // 1) The beast arrives at full health, exactly once per transformation. This heal is deliberately not
        // part of the regeneration accounting, it is free.
        player.setHealth(player.getMaxHealth());

        // 2) The body follows right away, the attributes are what the rest of the game reads.
        QueenBeastAttributes.apply(player);

        if (player.level() instanceof ServerLevel level)
        {
            // 3) The visual explosion: particles and a sound only, a real explosion is never used.
            playTransformFx(level, player);

            // 4) Clear the room, then decide whether the full body fits. The player is never teleported and
            // the transformation is never refused, worst case the beast stays compressed for a while.
            AABB box = QueenBeastDimensions.beastBox(player);
            QueenBeastClearance.clear(level, player, box);
            state.setCompressed(!QueenBeastClearance.hasRoom(level, box));
        }

        player.refreshDimensions();
        sync(player);
        player.displayClientMessage(Component.translatable(MESSAGE_ON), true);
    }

    private static boolean canTransform(ServerPlayer player)
    {
        // The second gate of the form (Skill Progression round): the key path above already asks, and the public
        // transform is asked here as well, so no other caller and no command can put a queen without the node into the
        // body of the beast.
        return QueenSkills.has(player, QueenSkills.QUEEN_BEAST) && QueenDataCapability.isQueen(player)
                && player.isAlive() && !player.isSpectator() && !player.isSleeping() && !player.isFallFlying();
    }

    /**
     * The single cleanup path of the form: every exit runs through here, so no exit can forget a modifier, a
     * body size or the client state. It is idempotent on purpose, reconciling a normal player changes nothing.
     */
    private static void revert(ServerPlayer player, RevertCause cause)
    {
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        if (state == null)
        {
            return;
        }

        boolean wasTransformed = state.isTransformed();

        // A running charge must die with the form: nobody keeps charging as a human.
        QueenBeastCharge.cancel(player);

        // The stomach of a beast is emptied before the human shape comes back: the throat of a giant spider
        // carries far more than the one of a person, so nothing may stay inside once the body shrinks again.
        // A death is the one exception - a dying queen releases her stomach at the place of her death, which is
        // the job of the release of phase 8E1-GATE2B, not of a vomit in front of her.
        if (wasTransformed && player.isAlive() && cause != RevertCause.DEATH)
        {
            StomachRegurgitation.regurgitateAll(player, StomachRegurgitation.Cause.BEAST_REVERT);
        }

        state.setTransformed(false);
        state.setCompressed(false);
        state.resetTransientData();
        QueenBeastAttributes.remove(player);

        if (wasTransformed && player.level() instanceof ServerLevel level)
        {
            playRevertFx(level, player);
        }

        player.refreshDimensions();

        if (!wasTransformed)
        {
            return;
        }

        sync(player);

        if (cause != RevertCause.DEATH)
        {
            player.displayClientMessage(Component.translatable(MESSAGE_OFF), true);
        }
    }

    /**
     * The beast regeneration of this phase: one health point per second while the hunger bar is not empty,
     * independent of saturation and of the vanilla regeneration rule. Every four health points it really healed
     * cost one food point, and running out of food ends the form in the same tick.
     */
    private static void tickRegeneration(ServerPlayer player, QueenBeastState state)
    {
        if (player.tickCount % QueenBeastConstants.BEAST_REGEN_INTERVAL_TICKS != 0)
        {
            return;
        }

        FoodData foodData = player.getFoodData();

        if (foodData.getFoodLevel() <= 0 || player.getHealth() >= player.getMaxHealth())
        {
            return;
        }

        float before = player.getHealth();
        player.heal(QueenBeastConstants.BEAST_REGEN_AMOUNT);
        float healed = player.getHealth() - before;

        if (healed <= 0.0F)
        {
            return;
        }

        state.setRegenHealAccumulator(state.getRegenHealAccumulator() + healed);

        while (state.getRegenHealAccumulator() >= QueenBeastConstants.BEAST_REGEN_HEAL_PER_FOOD)
        {
            state.setRegenHealAccumulator(
                    state.getRegenHealAccumulator() - QueenBeastConstants.BEAST_REGEN_HEAL_PER_FOOD);
            foodData.setFoodLevel(Math.max(foodData.getFoodLevel() - 1, 0));

            if (foodData.getFoodLevel() <= 0)
            {
                forceUntransform(player, RevertCause.STARVING);
                return;
            }
        }
    }

    /**
     * A compressed beast only watches for the moment the room around it became large enough. It never clears
     * the room a second time: from the transformation on the queen digs herself free with her own claws.
     */
    private static void tickCompressed(ServerPlayer player, QueenBeastState state)
    {
        if (!state.isCompressed()
                || player.tickCount % QueenBeastConstants.COMPRESSED_EXPAND_CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }

        if (!hasRoom(player))
        {
            return;
        }

        state.setCompressed(false);
        player.refreshDimensions();
        sync(player);
    }

    private static boolean hasRoom(ServerPlayer player)
    {
        return QueenBeastClearance.hasRoom(player.level(), QueenBeastDimensions.beastBox(player));
    }

    /**
     * The visual explosion of a transformation. It is a burst of particles and a sound, a real explosion is
     * never called, so no damage, no knockback, no fire and no destroyed block can ever come out of it.
     */
    private static void playTransformFx(ServerLevel level, ServerPlayer player)
    {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double center = y + QueenBeastConstants.BEAST_HEIGHT * 0.5D;

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, center, z, TRANSFORM_EMITTER_PARTICLES, 0.0D, 0.0D,
                0.0D, 0.0D);
        level.sendParticles(ParticleTypes.EXPLOSION, x, center, z, TRANSFORM_BURST_PARTICLES, TRANSFORM_BURST_SPREAD,
                TRANSFORM_BURST_SPREAD, TRANSFORM_BURST_SPREAD, 0.0D);
        level.sendParticles(ParticleTypes.POOF, x, y + 0.2D, z, TRANSFORM_CLOUD_PARTICLES, TRANSFORM_CLOUD_SPREAD,
                TRANSFORM_CLOUD_SPREAD * 0.5D, TRANSFORM_CLOUD_SPREAD, 0.05D);
        level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    /** Light revert feedback: a puff of smoke and a low hiss. No damage and no debuff, the hunger cost is enough. */
    private static void playRevertFx(ServerLevel level, ServerPlayer player)
    {
        double x = player.getX();
        double y = player.getY() + 0.4D;
        double z = player.getZ();

        level.sendParticles(ParticleTypes.POOF, x, y, z, REVERT_PUFF_PARTICLES, REVERT_PUFF_SPREAD,
                REVERT_PUFF_SPREAD * 0.5D, REVERT_PUFF_SPREAD, 0.02D);
        level.sendParticles(ParticleTypes.SMOKE, x, y, z, REVERT_SMOKE_PARTICLES, REVERT_PUFF_SPREAD,
                REVERT_PUFF_SPREAD * 0.5D, REVERT_PUFF_SPREAD, 0.01D);
        level.playSound(null, x, player.getY(), z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 0.6F);
    }
}
