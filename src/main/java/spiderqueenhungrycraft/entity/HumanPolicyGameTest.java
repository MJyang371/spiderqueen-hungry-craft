package spiderqueenhungrycraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.faction.FactionId;
import spiderqueenhungrycraft.faction.FactionResolver;
import spiderqueenhungrycraft.faction.FactionTargetGoal;
import spiderqueenhungrycraft.faction.FactionTargeting;
import spiderqueenhungrycraft.faction.OwnFactionTargeting;
import spiderqueenhungrycraft.human.HumanArchetype;
import spiderqueenhungrycraft.human.HumanEntityTypes;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of the human identity policies (human tidy round).
 *
 * <p>It asserts the promises the tidy round had to keep, question by question, so a later change can not quietly turn
 * one of them around:
 *
 * <ol>
 * <li>the three prepared tiers are still ordinary humans with their full natural spawn eligibility, and they are
 * still on the HUMAN faction,</li>
 * <li>Chad Steve is still a human of this mod (faction, population counting) and still transcendent - never a tier of
 * the surface spawner,</li>
 * <li>he still owns his targeting: exactly one faction target goal, never two,</li>
 * <li>a normal human still never treats him as a monster and never attacks him, while a monster is still hunted,</li>
 * <li>and the faction matrix still says that two humans are friendly.</li>
 * </ol>
 *
 * <p>Everything here is deterministic: no waiting, no random spawn, no timing. It only runs in a GameTest run
 * ({@code runGameTestServer} or {@code /test}).
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanPolicyGameTest
{
    /** How far away from the world spawn the entities of this test are placed, clear of every other test site. */
    private static final int SITE_OFFSET_X = 512;
    private static final int SITE_OFFSET_Z = 512;

    private HumanPolicyGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "human_policy", timeoutTicks = 100)
    public static void humanIdentity_policiesStayAsTheyWere(GameTestHelper helper)
    {
        assertOrdinaryHuman(helper, HungryCraftEntities.LOW_PREPARED_HUMAN.get(), "low_prepared_human");
        assertOrdinaryHuman(helper, HungryCraftEntities.MID_PREPARED_HUMAN.get(), "mid_prepared_human");
        assertOrdinaryHuman(helper, HungryCraftEntities.HIGH_PREPARED_HUMAN.get(), "high_prepared_human");

        // The hidden test entity of the older phase is an ordinary human as well - it is simply not listed as a tier
        // of the shipped spawn rules, which is a spawn rule and not an archetype.
        assertOrdinaryHuman(helper, HungryCraftEntities.HUMAN_COMBATANT.get(), "human_combatant");

        EntityType<?> chadType = HungryCraftEntities.CHAD_STEVE.get();

        if (HumanEntityTypes.archetypeOfType(chadType) != HumanArchetype.TRANSCENDENT)
        {
            helper.fail("chad_steve is not a transcendent human: " + HumanEntityTypes.archetypeOfType(chadType));
        }

        if (HumanEntityTypes.isOrdinaryHumanType(chadType))
        {
            helper.fail("chad_steve counts as an ordinary human, the surface spawner would place him");
        }

        if (FactionResolver.factionOfType(chadType) != FactionId.HUMAN)
        {
            helper.fail("chad_steve left the human faction: " + FactionResolver.factionOfType(chadType));
        }

        // ---------------------------------------------------------------------------------------------------------
        // The same questions on the live entities, which is where the population counter and the pack rules look.
        // ---------------------------------------------------------------------------------------------------------
        ServerLevel level = helper.getLevel();

        ChadSteveEntity chad = (ChadSteveEntity) HumanSpawnFactory.spawn(chadType, level, site(level, 0),
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (chad == null)
        {
            helper.fail("chad steve could not be placed");
            return;
        }

        if (!(chad instanceof OwnFactionTargeting))
        {
            helper.fail("chad_steve does not own its faction targeting, the faction system would inject a second goal");
        }

        if (!FactionTargeting.needsFactionGoal(chad))
        {
            helper.fail("chad_steve is no longer part of the faction war");
        }

        int factionGoals = 0;

        for (WrappedGoal wrapped : chad.targetSelector.getAvailableGoals())
        {
            if (wrapped.getGoal() instanceof FactionTargetGoal)
            {
                ++factionGoals;
            }
        }

        if (factionGoals != 1)
        {
            helper.fail("chad_steve holds " + factionGoals + " faction target goals instead of exactly one");
        }

        if (!HumanEntityTypes.isHumanMob(chad))
        {
            helper.fail("chad_steve is no longer a human unit, the population counter would ignore him");
        }

        if (!HumanEntityTypes.isTranscendent(chad))
        {
            helper.fail("chad_steve does not answer as a transcendent human");
        }

        HumanCombatantEntity human = (HumanCombatantEntity) HumanSpawnFactory.spawn(
                HungryCraftEntities.HUMAN_COMBATANT.get(), level, site(level, 8), MobSpawnType.COMMAND,
                HumanSpawnOrigin.COMMAND);

        if (human == null)
        {
            helper.fail("the human combatant could not be placed");
            return;
        }

        Zombie monster = EntityType.ZOMBIE.create(level);

        if (monster == null)
        {
            helper.fail("the zombie of this test could not be created");
            return;
        }

        BlockPos monsterPos = site(level, 16);
        monster.moveTo(monsterPos.getX() + 0.5D, monsterPos.getY(), monsterPos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(monster);

        // ---------------------------------------------------------------------------------------------------------
        // A normal human and the transcendent one never fight, a monster is still hunted.
        // ---------------------------------------------------------------------------------------------------------
        if (HumanCombatantTargeting.isHostileMonster(human, chad))
        {
            helper.fail("a normal human still treats chad_steve as a monster");
        }

        if (HumanCombatantTargeting.isAttackableTarget(human, chad))
        {
            helper.fail("a normal human may still attack chad_steve");
        }

        if (!HumanCombatantTargeting.isHostileMonster(human, monster))
        {
            helper.fail("a normal human no longer hunts a monster, the monster rule of the targeting class broke");
        }

        if (!HumanCombatantTargeting.isAttackableTarget(human, monster))
        {
            helper.fail("a normal human no longer considers a monster an attackable target");
        }

        // ---------------------------------------------------------------------------------------------------------
        // And the faction matrix itself: two humans are friendly, in both directions.
        // ---------------------------------------------------------------------------------------------------------
        if (FactionTargeting.shouldFactionGoalAttack(human, chad))
        {
            helper.fail("the human faction goal attacks a fellow human");
        }

        if (FactionTargeting.shouldFactionGoalAttack(chad, human))
        {
            helper.fail("chad_steve's faction goal attacks a fellow human");
        }

        SpiderQueenHungryCraft.LOGGER.info("[PolicyTest] policy check passed: ordinary tiers={}/{}/{}, chad={} with "
                        + "{} faction goal, human hunts monster={}, human attacks chad={}",
                HumanEntityTypes.isOrdinaryHumanType(HungryCraftEntities.LOW_PREPARED_HUMAN.get()),
                HumanEntityTypes.isOrdinaryHumanType(HungryCraftEntities.MID_PREPARED_HUMAN.get()),
                HumanEntityTypes.isOrdinaryHumanType(HungryCraftEntities.HIGH_PREPARED_HUMAN.get()),
                HumanEntityTypes.archetypeOf(chad).id(), factionGoals,
                HumanCombatantTargeting.isHostileMonster(human, monster),
                HumanCombatantTargeting.isAttackableTarget(human, chad));

        helper.succeed();
    }

    /** Asserts one ordinary human type: archetype, natural spawn eligibility and faction. */
    private static void assertOrdinaryHuman(GameTestHelper helper, EntityType<?> type, String name)
    {
        if (HumanEntityTypes.archetypeOfType(type) != HumanArchetype.STANDARD)
        {
            helper.fail(name + " is no longer an ordinary human: " + HumanEntityTypes.archetypeOfType(type));
        }

        if (!HumanEntityTypes.isOrdinaryHumanType(type))
        {
            helper.fail(name + " lost its natural surface spawn eligibility");
        }

        if (FactionResolver.factionOfType(type) != FactionId.HUMAN)
        {
            helper.fail(name + " left the human faction: " + FactionResolver.factionOfType(type));
        }
    }

    /** A flat, open spot of this test, derived from the world spawn so it stays clear of the other test sites. */
    private static BlockPos site(ServerLevel level, int offset)
    {
        BlockPos spawn = level.getSharedSpawnPos();

        return new BlockPos(spawn.getX() + SITE_OFFSET_X + offset, spawn.getY(), spawn.getZ() + SITE_OFFSET_Z);
    }
}
