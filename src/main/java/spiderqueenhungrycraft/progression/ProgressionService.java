package spiderqueenhungrycraft.progression;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CProgressionSnapshotPacket;
import spiderqueenhungrycraft.network.S2CSkillStatePacket;
import spiderqueenhungrycraft.soul.Souls;

/**
 * What the growth of a queen really does on the server (Progression Foundation round): open the interface, raise a
 * level, and hand the answer back.
 *
 * <p><b>Nothing here trusts a client.</b> A request says which core it came from and which level it wants, and
 * everything else - whether that core is really open for that player, whether that player is really standing at it,
 * what the next level is, what it costs and whether the player can pay - is decided here, from the state of the
 * server. The client of an honest player and the client of a modified one are answered by the very same checks.
 *
 * <p>The price comes from the definition the server holds, never from a number that arrived over the wire, and a
 * level is only ever raised by exactly one step: a large pile of souls can not skip the line.
 */
public final class ProgressionService
{
    private ProgressionService()
    {
    }

    /**
     * Opens the growth interface of that core for that player: it remembers the session and sends the first snapshot,
     * which is what makes the client draw the screen.
     */
    public static void openInterface(ServerPlayer player, BlockPos corePos)
    {
        ProgressionSessions.open(player, corePos);
        sendSnapshot(player, corePos);
    }

    /** Sends the current state of the growth system to that player, used when opening and after every change. */
    public static void sendSnapshot(ServerPlayer player, BlockPos corePos)
    {
        HungryCraftNetwork.sendToPlayer(new S2CProgressionSnapshotPacket(
                ProgressionSnapshot.of(player, corePos)), player);
    }

    /**
     * Sends the bought skills of that player to their client (Skill Progression round).
     *
     * <p>It is deliberately <b>not</b> part of the snapshot: a client has to know what its player owns while she runs
     * around in the world and not only while an interface is open, because that set is what keeps a locked key silent.
     * It is therefore sent on a login, on a respawn and on a dimension change like the other runtime state of a queen,
     * and again whenever a skill is bought or revoked.
     */
    public static void sendSkillState(ServerPlayer player)
    {
        HungryCraftNetwork.sendToPlayer(new S2CSkillStatePacket(QueenSkills.of(player)), player);
    }

    /**
     * The one way a skill node is bought (Skill Progression round).
     *
     * <p>The order of the checks is the order of the rules, and every one of them is on the server:
     * <ol>
     * <li>the player is alive and really has that core open, still stands at it, and the core is still there,</li>
     * <li>the node exists and is switched on in the data pack of this world,</li>
     * <li>the player does not own it already (a second request is never charged twice),</li>
     * <li>her Queen Level is at least the level the node asks for,</li>
     * <li>every prerequisite is unlocked - all of them, never any of them,</li>
     * <li>and she really holds the souls.</li>
     * </ol>
     *
     * <p>A refusal changes nothing at all: no soul is taken and no skill is written. Only a request that passes every
     * step spends the souls, writes the skill and brings the running world in line with it - which is what makes a
     * passive skill take effect at once instead of after a relog.
     *
     * @return the code the client has to show.
     */
    public static ProgressionFeedback unlockSkill(ServerPlayer player, BlockPos corePos, ResourceLocation skillId)
    {
        if (!ProgressionSessions.isValid(player, corePos))
        {
            return ProgressionFeedback.DENIED;
        }

        SkillNodeDefinition node = SkillNodes.find(skillId).orElse(null);

        if (node == null || !node.enabled())
        {
            return ProgressionFeedback.UNKNOWN_SKILL;
        }

        SkillState state = QueenSkills.state(player, node);

        if (state == SkillState.UNLOCKED)
        {
            return ProgressionFeedback.ALREADY_UNLOCKED;
        }

        if (state == SkillState.LOCKED)
        {
            return ProgressionFeedback.SKILL_LOCKED;
        }

        if (!Souls.spendSouls(player, node.soulCost()))
        {
            return ProgressionFeedback.INSUFFICIENT_SOULS;
        }

        QueenSkills.unlock(player, node.id());
        QueenSkills.refreshRuntime(player);

        return ProgressionFeedback.SKILL_OK;
    }

    /**
     * The one way a Queen Level is raised.
     *
     * <p>The order of the checks is the order of the rules, and every one of them is on the server:
     * <ol>
     * <li>the player is alive and really has that core open, still stands at it, and the core is still there
     * ({@link ProgressionSessions#isValid}),</li>
     * <li>the level that was asked for is exactly the next one,</li>
     * <li>it is not past the maximum of the definition in force,</li>
     * <li>the price of that step is the price of the definition the server holds,</li>
     * <li>and the player really holds that many souls.</li>
     * </ol>
     *
     * <p>A refusal changes nothing at all: no soul is taken and no level is written. Only the very last step of a
     * successful request touches the two permanent values.
     *
     * @return the code the client has to show.
     */
    public static ProgressionFeedback upgrade(ServerPlayer player, BlockPos corePos, int targetLevel)
    {
        if (!ProgressionSessions.isValid(player, corePos))
        {
            return ProgressionFeedback.DENIED;
        }

        ProgressionDefinition definition = ProgressionDefinitions.current();
        int current = QueenLevels.getLevel(player);

        if (targetLevel != current + 1)
        {
            return ProgressionFeedback.NOT_THE_NEXT_LEVEL;
        }

        if (targetLevel > definition.maxLevel())
        {
            return ProgressionFeedback.ALREADY_MAX_LEVEL;
        }

        long cost = definition.costToReach(targetLevel);

        if (!Souls.spendSouls(player, cost))
        {
            return ProgressionFeedback.INSUFFICIENT_SOULS;
        }

        QueenLevels.setLevel(player, targetLevel);
        return ProgressionFeedback.UPGRADE_OK;
    }
}
