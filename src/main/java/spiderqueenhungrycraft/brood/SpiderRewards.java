package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * What a digested prey leaves behind (this round's breeding pass).
 *
 * <p>It is the very same reward for both ways in which the queen eats: a cocoon she eats with her own
 * hands, and a creature that dies inside her stomach and feeds her there. In both cases the prey leaves
 * its normal experience and, one time in four, exactly one spider egg.
 *
 * <p><b>Where the reward lands is part of the contract.</b> The caller hands over the world and the spot the reward
 * belongs to - the feet of the queen - and never the place of the death, because the two are not always the same: a
 * cocoon is eaten where it lies, but a creature usually dies in the stomach dimension while its owner walks somewhere
 * else entirely. Orbs and an egg created in a stomach cavity, or in an empty chunk of the stomach dimension at the
 * coordinates of an outside-world position, would be lost for everybody.
 *
 * <p>The experience is always dropped as real {@link ExperienceOrb}s, never handed to a player as points:
 * the orbs belong to the world of the eater and behave like every other orb of the game. The amount is the
 * normal reward of that creature, asked through the Forge experience hook so another mod may still change it.
 *
 * <p>An egg that is really laid is announced in the action bar of the eater, so the one-in-four of a prey is something
 * a queen sees instead of something she has to notice on the ground.
 */
public final class SpiderRewards
{
    /** Chance that a digested prey leaves one spider egg behind: one in four. */
    public static final double SPIDER_EGG_CHANCE = 0.25D;

    private SpiderRewards()
    {
    }

    /**
     * The reward of a prey that is still there, used by a death inside a stomach.
     *
     * @param level the world the reward belongs to and is created in - the world of the eater
     * @param pos   where the reward appears - the feet of the eater
     * @param eater the player who ate it, may be null when nobody can be named
     */
    public static void grant(ServerLevel level, Vec3 pos, LivingEntity prey, @Nullable Player eater)
    {
        grant(level, pos, ForgeEventFactory.getExperienceDrop(prey, eater, prey.getExperienceReward()), eater);
    }

    /**
     * The reward of a prey that is already gone, used by a cocoon: the experience was read when the prey was
     * captured, so it is handed over here as a plain number.
     *
     * @param level the world the reward belongs to and is created in - the world of the eater
     * @param pos   where the reward appears - the feet of the eater
     * @param eater the player who ate it, may be null when nobody can be named
     */
    public static void grant(ServerLevel level, Vec3 pos, int experience, @Nullable Player eater)
    {
        if (experience > 0)
        {
            ExperienceOrb.award(level, pos, experience);
        }

        // The egg is the laying of a brood queen (Skill Progression round): the experience of a meal is paid to every
        // queen, the egg only to one who owns that node. The chance itself is untouched - it is still one in four - and
        // nothing else about a meal changes.
        if (eater == null || !QueenSkills.has(eater, QueenSkills.BROOD_QUEEN))
        {
            return;
        }

        rollEgg(level, pos, eater);
    }

    /**
     * The plain reward of a meal, <b>without</b> the permission of the brood queen.
     *
     * <p>It exists for one caller only: the debug probe of the laying rate ({@code /hungrycraft reward eggprobe}), which
     * measures how often the roll of a reward really lays an egg. That probe belongs to nobody and hands nothing to a
     * player, so gating it would only make the rate of the reward unmeasurable. It is deliberately not the method any
     * gameplay path calls.
     */
    public static void grantForProbe(ServerLevel level, Vec3 pos, int experience)
    {
        if (experience > 0)
        {
            ExperienceOrb.award(level, pos, experience);
        }

        rollEgg(level, pos, null);
    }

    /** The one roll of a reward: one egg in four, and a line for the queen who laid it. */
    private static void rollEgg(ServerLevel level, Vec3 pos, @Nullable Player eater)
    {
        if (level.random.nextDouble() < SPIDER_EGG_CHANCE)
        {
            level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z,
                    new ItemStack(HungryCraftItems.SPIDER_EGG.get())));
            tellEater(eater);
        }
    }

    /** One quiet line in the action bar of the eater, and nothing for a player without a connection or a name. */
    private static void tellEater(@Nullable Player eater)
    {
        if (eater instanceof ServerPlayer queen && queen.connection != null)
        {
            queen.displayClientMessage(
                    Component.translatable("message.spiderqueen_hungrycraft.spider_egg_laid"), true);
        }
    }
}
