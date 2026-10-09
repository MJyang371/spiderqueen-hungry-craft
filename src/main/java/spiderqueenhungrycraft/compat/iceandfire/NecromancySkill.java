package spiderqueenhungrycraft.compat.iceandfire;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.BroodSelection;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireRising;

/**
 * 亡灵契约 IV (Necromancy IV round): the formal skill that wakes a bone dragon of Ice and Fire up and binds it for ten
 * minutes.
 *
 * <p>It is an active skill of the queen, it costs no item, and it is server authoritative from beginning to end: the
 * client only says "I pressed the key", the ray is cast here from the real eyes and the real view vector of the real
 * player, and not one word of what a client claims about its target is believed.
 *
 * <p><b>Refusals are quiet.</b> A player who is not the queen, who has not bought the node, or who is in a world
 * without Ice and Fire gets nothing at all - no lightning, no cost, no cooldown and no line, exactly like the sniff of
 * the queen. A refusal that is worth saying out loud (nothing under the crosshair, a living dragon, a corpse that
 * somebody else already holds) is said in the action bar, and it costs nothing either.
 *
 * <p>The node, its level and its price are <b>provisional</b>: see
 * {@code data/spiderqueen_hungrycraft/progression_skills/necromancy_iv.json}.
 */
public final class NecromancySkill
{
    /**
     * The cooldown of the skill, in ticks.
     *
     * <p><b>PROVISIONAL.</b> Zero, because the round has not decided a real one and a cooldown that does not exist is
     * the easiest state to test in. It is one named constant so that the decision later is one edit.
     */
    public static final int COOLDOWN_TICKS = 0;

    /** Player to game time at which the next contract may be made. Runtime only, like every cooldown of a body. */
    private static final Map<Player, Long> READY_AT = new WeakHashMap<>();

    private NecromancySkill()
    {
    }

    /** One press of the key of that player, in the shape the server decides it. */
    public static void cast(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator() || !QueenDataCapability.isQueen(player))
        {
            return;
        }

        if (!QueenSkills.has(player, QueenSkills.NECROMANCY_IV))
        {
            // Locked means silent: no line, no lightning, no cooldown and no cost.
            return;
        }

        if (!IceAndFireSupport.present())
        {
            tell(player, "message.spiderqueen_hungrycraft.necromancy.no_support");
            return;
        }

        long now = player.level().getGameTime();
        Long readyAt = READY_AT.get(player);

        if (readyAt != null && readyAt > now)
        {
            long seconds = (readyAt - now + 19L) / 20L;
            player.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.sniff_cooldown", seconds), true);
            return;
        }

        Entity pointed = ProbeRay.entityUnderCrosshair(player, ProbeRay.RANGE);

        if (pointed == null)
        {
            tell(player, "message.spiderqueen_hungrycraft.necromancy.no_target");
            return;
        }

        if (NecromancyContract.isContracted(pointed))
        {
            tell(player, "message.spiderqueen_hungrycraft.necromancy.already_contracted");
            return;
        }

        if (!IceAndFireSupport.isBoneDragon(pointed))
        {
            tell(player, "message.spiderqueen_hungrycraft.necromancy.not_a_bone_dragon");
            return;
        }

        SoulfireRising.Rising rising = SoulfireRising.raise(player, pointed);
        ProbeReport report = rising.report();

        for (String line : report.lines())
        {
            SpiderQueenHungryCraft.LOGGER.info("{} {}", IceAndFireSupport.PROBE_LOG_PREFIX, line);
        }

        if (!report.ok() || rising.dragon() == null)
        {
            tell(player, "message.spiderqueen_hungrycraft.necromancy.not_a_bone_dragon");
            return;
        }

        READY_AT.put(player, now + COOLDOWN_TICKS);

        // The unit is offered to the queen the way the sceptre offers a unit - but only while she is holding no group at
        // all (Necromancy IV round 5). Clicking one unit with the sceptre replaces a selection by design
        // (BroodSelection#selectSingle); a skill press is not a click on a unit, so quietly throwing away a command
        // group with it would be exactly the thing this round forbids. An empty selection is filled, an existing one is
        // left alone, and both answers are said out loud.
        SoulfireDragonEntity dragon = rising.dragon();
        boolean selected = selectIfNothingSelected(player, dragon);

        NecromancyDiagnostics.log("skill cast: remains=" + pointed.getUUID() + " -> soulfire dragon=" + dragon.getUUID()
                + " owner=" + player.getUUID() + " selected=" + selected
                + "; the ten minute contract is already written on the unit");

        tell(player, selected ? "message.spiderqueen_hungrycraft.necromancy.contracted_selected"
                : "message.spiderqueen_hungrycraft.necromancy.contracted_kept");
    }

    /**
     * Makes the freshly bound skeleton the selection of its queen, but <b>only</b> when she is holding no group.
     *
     * @return true when the dragon became the selection, false when her existing group was kept
     */
    private static boolean selectIfNothingSelected(ServerPlayer player, Entity dragon)
    {
        QueenData data = QueenDataCapability.get(player).orElse(null);

        if (data == null || !data.broodSelection().isEmpty())
        {
            return false;
        }

        data.broodSelection().selectOnly(List.of(dragon.getUUID()));

        // And her client is told, so the green outline of the new unit is there at once - the same echo every other
        // selection sends, so nothing new had to be invented for this.
        BroodSelection.sendSelection(player);

        return true;
    }

    /** Action bar feedback, and nothing for a player without a connection (a fake player of a test). */
    private static void tell(ServerPlayer player, String key)
    {
        if (player.connection != null)
        {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }
}
