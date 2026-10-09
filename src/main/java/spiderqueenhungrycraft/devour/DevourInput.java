package spiderqueenhungrycraft.devour;

import net.minecraft.server.level.ServerPlayer;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.bed.SpiderBedHibernation;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.stomach.StomachRegurgitation;

/**
 * The one place that decides what the devour key means (Phase 8E2A).
 *
 * <p>Nothing about this decision is made on a client. The client only reports the two edges of a physical key -
 * pressed and released - and this class owns every rule of the answer: whether the player is the queen at all,
 * whether she is a beast right now, how long the key was held and whether the action of this press was already
 * spent. That is why a wrong or stale picture on a client can never lock the key: the client has no voice here.
 *
 * <p>The rules in one place:
 *
 * <ul>
 * <li>an ordinary queen regurgitates at the press and can never devour,</li>
 * <li>a beast devours when the key is released before the hold threshold,</li>
 * <li>a beast regurgitates the moment the threshold is reached, and that press is then spent - releasing the
 * key can never devour afterwards,</li>
 * <li>everybody else, and every dead or spectating player, gets nothing at all.</li>
 * </ul>
 *
 * <p>The state of one press lives in the beast state of that player, so it is per player and per server, never a
 * static field somewhere, and it is cleared on every logout, death and dimension change as well as when the
 * queen flag is gone.
 */
public final class DevourInput
{
    private DevourInput()
    {
    }

    /** The key went down. */
    public static void onPress(ServerPlayer player)
    {
        QueenBeastState state = usableState(player);

        if (state == null)
        {
            return;
        }

        long now = gameTime(player);

        if (QueenBeastState.isTransformed(player))
        {
            // A beast starts a press it may still turn into a devour: the answer waits for the release.
            state.setDevourPressed(true);
            state.setDevourPressGameTime(now);
            state.setDevourHoldConsumed(false);
            return;
        }

        // An ordinary queen has no throat for prey. The press is the safety fallback and nothing else, and it
        // happens right now instead of waiting for a release: it is a "get this out of me" button.
        state.setDevourPressed(false);
        state.setDevourPressGameTime(-1L);
        state.setDevourHoldConsumed(true);
        StomachRegurgitation.regurgitateAll(player, StomachRegurgitation.Cause.QUEEN_FALLBACK);
    }
    /** The key went up again. */
    public static void onRelease(ServerPlayer player)
    {
        QueenBeastState state = usableState(player);

        if (state == null)
        {
            return;
        }

        boolean wasPressed = state.isDevourPressed();
        boolean spent = state.isDevourHoldConsumed();
        long start = state.getDevourPressGameTime();

        state.setDevourPressed(false);
        state.setDevourPressGameTime(-1L);
        state.setDevourHoldConsumed(false);

        // A release that belongs to no press of ours, or to a press whose action is already spent, is silence.
        // This is what makes "hold to vomit, then release" unable to devour by accident.
        if (!wasPressed || spent || start < 0L)
        {
            return;
        }

        if (heldTicks(player, start) >= DevourTuning.HOLD_THRESHOLD_TICKS)
        {
            return;
        }

        if (!QueenDataCapability.isQueen(player) || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        DevourHandler.tryDevour(player);
    }

    /** One server tick: a held key turns into the regurgitation the moment it reaches the threshold. */
    public static void tick(ServerPlayer player)
    {
        QueenBeastState state = usableState(player);

        if (state == null)
        {
            return;
        }

        if (!state.isDevourPressed() || state.isDevourHoldConsumed())
        {
            return;
        }

        if (heldTicks(player, state.getDevourPressGameTime()) < DevourTuning.HOLD_THRESHOLD_TICKS)
        {
            return;
        }

        // The attempt is consumed whether or not the stomach had anything to give: a queen who held the key
        // meant to vomit, and the release must not devour a prey she never asked for.
        state.setDevourHoldConsumed(true);

        if (QueenBeastState.isTransformed(player))
        {
            StomachRegurgitation.regurgitateAll(player, StomachRegurgitation.Cause.BEAST_HOLD);
        }
    }

    /** Forgets any press of that player, so a key can never stay held across a death or a world change. */
    public static void clear(ServerPlayer player)
    {
        if (player == null)
        {
            return;
        }

        QueenBeastState.of(player).ifPresent(state ->
        {
            state.setDevourPressed(false);
            state.setDevourPressGameTime(-1L);
            state.setDevourHoldConsumed(false);
        });
    }

    /** @return the state of a player who may use the key at all, or null when the key means nothing to them. */
    private static QueenBeastState usableState(ServerPlayer player)
    {
        if (player == null || player.isRemoved() || !player.isAlive() || player.isSpectator())
        {
            clear(player);
            return null;
        }

        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        // Losing the queen flag ends the meaning of the key, exactly like it ends the beast form. A queen who
        // is hibernating in her spider bed has no key at all, and any half pressed key is forgotten.
        //
        // Since the Skill Progression round the same is true of the Giant Devour node: the throat of the beast is a
        // skill of its own and not a part of the form, so a queen who owns Queen Beast but not Giant Devour has no
        // devour and no regurgitation at all - and, because this is the one gate every edge of the key runs through,
        // her press neither swallows nor vomits nor counts: nothing at all is remembered.
        if (state == null || !QueenDataCapability.isQueen(player) || SpiderBedHibernation.isHibernating(player)
                || !QueenSkills.has(player, QueenSkills.GIANT_DEVOUR))
        {
            clear(player);
            return null;
        }

        return state;
    }

    private static long gameTime(ServerPlayer player)
    {
        return player.level().getGameTime();
    }

    private static long heldTicks(ServerPlayer player, long start)
    {
        return start < 0L ? 0L : Math.max(0L, gameTime(player) - start);
    }
}

