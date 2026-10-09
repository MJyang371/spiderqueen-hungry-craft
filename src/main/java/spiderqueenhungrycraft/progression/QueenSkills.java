package spiderqueenhungrycraft.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ability.QueenPhysiology;
import spiderqueenhungrycraft.attack.EmpoweredAttackHandler;
import spiderqueenhungrycraft.beast.QueenBeastController;
import spiderqueenhungrycraft.devour.DevourInput;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.stomach.StomachCoreArmorPolicy;

/**
 * The permission of every bought ability (Skill Progression round): the one place the rest of the mod asks whether a
 * Spider Queen may do something.
 *
 * <p><b>No ability of this mod reads the player data itself.</b> Every gate is a call to {@link #has}, so the rule of
 * "who may do what" lives in one file, and a later round can add an administrator grant, a temporary permission or a
 * wave of the legendary system without touching a single ability implementation again. The ids below are the stable
 * names of the official nodes: they are what a save file holds and what a data pack declares, never a number.
 *
 * <p>The gates themselves are deliberately small and always sit <b>in front of</b> the work: before a hunger cost, a
 * cooldown, a message, a sound, a tap counter or a packet. A locked skill is therefore silent on both sides - the
 * client does not send the request at all, and a forged one is refused by the server without an answer.
 */
public final class QueenSkills
{
    /** The name of this mod as the namespace of every official node. */
    private static final String NAMESPACE = SpiderQueenHungryCraft.MODID;

    /** The node that makes the body immune to poison and to the status effect of hunger. */
    public static final ResourceLocation VISCERAL_ENHANCEMENT = id("visceral_enhancement");

    /** The node of the empowered melee attack. */
    public static final ResourceLocation QUEEN_STANCE = id("queen_stance");

    /** The node of the inherent armor of the queen. */
    public static final ResourceLocation CHITIN_SHELL = id("chitin_shell");

    /** The node of egg laying and of the spider egg of the soul shop. */
    public static final ResourceLocation BROOD_QUEEN = id("brood_queen");

    /** The node of the Queen Beast transformation. */
    public static final ResourceLocation QUEEN_BEAST = id("queen_beast");

    /** The node of the devour and regurgitate key of the beast. */
    public static final ResourceLocation GIANT_DEVOUR = id("giant_devour");

    /** The node of the visceral armor of the stomach wall. */
    public static final ResourceLocation VISCERAL_ENHANCEMENT_II = id("visceral_enhancement_ii");

    /** The node of the sniff. */
    public static final ResourceLocation QUEEN_SNIFF = id("queen_sniff");

    /**
     * The node of 亡灵契约 IV (Necromancy IV round): the contract of a bone dragon of Ice and Fire.
     *
     * <p>It is an <b>optional compatibility</b> skill. Without Ice and Fire the node still exists and is still a node
     * of the tree, but it can never be bought and its key can never do anything - see
     * {@code compat.iceandfire.IceAndFireSupport#present()}.
     */
    public static final ResourceLocation NECROMANCY_IV = id("necromancy_iv");

    private QueenSkills()
    {
    }

    /** @return the official identity of that name. */
    private static ResourceLocation id(String name)
    {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, name);
    }

    /**
     * @return true when that player really owns that skill.
     *
     *         <p>It is deliberately safe for everything: a player without the capability, a null player, a null skill
     *         and a skill that no data pack declares all answer {@code false}. A permission that throws where it is
     *         asked would be worse than a permission that says no.
     */
    public static boolean has(@Nullable Player player, @Nullable ResourceLocation skill)
    {
        if (player == null || skill == null)
        {
            return false;
        }

        return QueenDataCapability.get(player).map(data -> data.isSkillUnlocked(skill)).orElse(false);
    }

    /** @return true when that player owns every one of those skills. An empty list is always true. */
    public static boolean hasAll(@Nullable Player player, List<ResourceLocation> skills)
    {
        for (ResourceLocation skill : skills)
        {
            if (!has(player, skill))
            {
                return false;
            }
        }

        return true;
    }

    /** @return the bought skills of that player, empty when there are none. */
    public static Set<ResourceLocation> of(@Nullable Player player)
    {
        if (player == null)
        {
            return Set.of();
        }

        return QueenDataCapability.get(player).map(data -> data.unlockedSkills()).orElse(Set.of());
    }

    /**
     * Adds one skill to the bought ones of that player.
     *
     * <p>It is the last step of a purchase and nothing else writes a skill that way; the administrator command uses it
     * too, which is why it is public here instead of hidden inside the service.
     *
     * @return true when it was really new.
     */
    public static boolean unlock(Player player, ResourceLocation skill)
    {
        if (player == null || skill == null)
        {
            return false;
        }

        return QueenDataCapability.get(player).map(data -> data.unlockSkill(skill)).orElse(false);
    }

    /** Takes one skill away, used by the administrator command and by nothing else. */
    public static boolean revoke(Player player, ResourceLocation skill)
    {
        if (player == null || skill == null)
        {
            return false;
        }

        return QueenDataCapability.get(player).map(data -> data.revokeSkill(skill)).orElse(false);
    }

    /** @return the state of that node for that player, decided on the server. */
    public static SkillState state(@Nullable Player player, SkillNodeDefinition node)
    {
        return stateOf(QueenLevels.getLevel(player), of(player), node.requiredLevel(), node.id(), node.parents());
    }

    /**
     * @return the state of one node, decided from a level, a set of bought skills and the rules of that node.
     *
     *         <p>This is the shared half of the rule: the server uses it through {@link #state}, the client uses it
     *         with the level and the set the server sent it, and both therefore agree on what "available" means.
     */
    public static SkillState stateOf(int queenLevel, Set<ResourceLocation> unlocked, int requiredLevel,
            ResourceLocation nodeId, List<ResourceLocation> prerequisites)
    {
        if (unlocked.contains(nodeId))
        {
            return SkillState.UNLOCKED;
        }

        if (queenLevel < requiredLevel)
        {
            return SkillState.LOCKED;
        }

        for (ResourceLocation parent : prerequisites)
        {
            if (!unlocked.contains(parent))
            {
                return SkillState.LOCKED;
            }
        }

        return SkillState.AVAILABLE;
    }

    /** @return the prerequisites that are still missing from a set of bought skills, in the order the node names them. */
    public static List<ResourceLocation> missingPrerequisites(Set<ResourceLocation> unlocked,
            List<ResourceLocation> prerequisites)
    {
        List<ResourceLocation> missing = new ArrayList<>();

        for (ResourceLocation parent : prerequisites)
        {
            if (!unlocked.contains(parent))
            {
                missing.add(parent);
            }
        }

        return missing;
    }

    /**
     * Brings the running world in line with the bought skills of that player.
     *
     * <p>It is called the moment a skill is bought or revoked, and it is what makes a passive skill take effect at
     * once instead of after a relog:
     *
     * <ol>
     * <li>the inherent armor of the body is added or taken away right now,</li>
     * <li>the visceral armor of her stomach wall is written to her core right now,</li>
     * <li>a beast form that may no longer be owned is left at once,</li>
     * <li>and a half pressed key or a prepared strike whose skill is gone is forgotten.</li>
     * </ol>
     *
     * <p>Everything here is idempotent, so calling it twice changes nothing the second time.
     */
    public static void refreshRuntime(ServerPlayer player)
    {
        if (player == null)
        {
            return;
        }

        QueenPhysiology.updateArmor(player);
        StomachCoreArmorPolicy.refreshForOwner(player);
        QueenBeastController.reconcile(player);

        if (!has(player, QUEEN_STANCE))
        {
            EmpoweredAttackHandler.clearArmed(player);
        }

        if (!has(player, GIANT_DEVOUR))
        {
            DevourInput.clear(player);
        }
    }
}
