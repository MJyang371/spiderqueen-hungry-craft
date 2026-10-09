package spiderqueenhungrycraft.ability;

import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * The runtime proof of the sniff of the queen (Spider Trigger round follow-up).
 *
 * <p>One test, and it is synchronous: a fake queen with a full hunger bar and one quiet creature beside her presses
 * the key - twice in a row, and then once more while she is starving. What is asserted is exactly the rules a player
 * would notice if they were broken: a queen <b>without</b> the node of the sniff pays nothing at all, the cast of a
 * queen with it costs five points of hunger and outlines what is around her, the second press does nothing at all
 * because the skill is still resting, and a queen below five points of hunger can not cast it in the first place.
 *
 * <p>The outline is asserted through the game's own effect API and nothing else: it is the glowing effect, which is
 * what the renderer of a creature turns into the outline of its model.
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class QueenSniffGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the fake queen stands: on the floor block of the test structure. */
    private static final BlockPos QUEEN_POS = new BlockPos(2, 1, 2);

    /** Where the creature she should find stands. */
    private static final BlockPos CREATURE_POS = new BlockPos(3, 1, 2);

    /** A full hunger bar, so the first cast has something to pay from. */
    private static final int FULL_FOOD = 20;

    /** And a bar too empty for the skill: one point below the cost of it. */
    private static final int STARVING_FOOD = 4;

    private QueenSniffGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "queen_sniff", timeoutTicks = TIMEOUT_TICKS)
    public static void sniffoutlinesandchargeshunger(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper, FULL_FOOD);
        ArmorStand creature = placeCreature(helper);

        expect(helper, "the creature of this test was really built", creature != null);

        // The sniff is a bought node (Skill Progression round): a queen without it gets nothing at all from the key,
        // not even a hunger cost - which is asserted here before the node is bought.
        QueenSniff.cast(queen);

        expect(helper, "a queen without the node pays nothing for a sniff",
                queen.getFoodData().getFoodLevel() == FULL_FOOD);
        expect(helper, "and nothing around her is outlined",
                creature == null || !creature.hasEffect(MobEffects.GLOWING));

        QueenSkills.unlock(queen, QueenSkills.QUEEN_SNIFF);

        // One cast: five points of hunger and an outline on everything around the queen.
        QueenSniff.cast(queen);

        expect(helper, "a sniff costs five points of hunger",
                queen.getFoodData().getFoodLevel() == FULL_FOOD - QueenSniff.HUNGER_COST);
        expect(helper, "a sniff outlines the creature next to the queen",
                creature != null && creature.hasEffect(MobEffects.GLOWING));

        // The cooldown: the second press must do nothing, which shows itself in the hunger that is not paid twice and
        // in a creature that is not outlined again.
        removeEffect(creature);
        QueenSniff.cast(queen);

        expect(helper, "a sniff inside its cooldown costs nothing",
                queen.getFoodData().getFoodLevel() == FULL_FOOD - QueenSniff.HUNGER_COST);
        expect(helper, "a sniff inside its cooldown does not outline anything",
                creature == null || !creature.hasEffect(MobEffects.GLOWING));

        // And the hungry queen: below the cost, the key is refused out loud and nothing happens.
        queen.getFoodData().setFoodLevel(STARVING_FOOD);
        removeEffect(creature);
        QueenSniff.cast(queen);

        expect(helper, "a starving queen can not sniff", queen.getFoodData().getFoodLevel() == STARVING_FOOD);
        expect(helper, "a starving sniff outlines nothing", creature == null || !creature.hasEffect(MobEffects.GLOWING));

        discard(creature);
        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of this test

    /**
     * @return a fake queen of this mod with that much food in her bar.
     *
     * <p>She is deliberately NOT put into the level: this test runs in the same world as every other test of the mod,
     * and a stray player standing in a test structure is an answer every other test's AI would suddenly have to
     * consider.
     */
    private static ServerPlayer placeQueen(GameTestHelper helper, int food)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(QUEEN_POS);
        ServerPlayer queen = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "queen_sniff_test"));
        queen.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        queen.setGameMode(GameType.SURVIVAL);
        queen.getFoodData().setFoodLevel(food);

        return queen;
    }

    /** @return the quiet creature the sniff should find: an armour stand, which no other test has an opinion about. */
    @Nullable
    private static ArmorStand placeCreature(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CREATURE_POS);
        ArmorStand creature = EntityType.ARMOR_STAND.create(level);

        if (creature == null)
        {
            return null;
        }

        creature.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        level.addFreshEntity(creature);

        return creature;
    }

    /** Takes the outline of the previous cast off again, so the next cast can be judged on its own. */
    private static void removeEffect(@Nullable LivingEntity creature)
    {
        if (creature != null)
        {
            creature.removeEffect(MobEffects.GLOWING);
        }
    }

    /** Takes the helper creature of this test out of the world again, which it shares with every other test. */
    private static void discard(@Nullable LivingEntity creature)
    {
        if (creature != null)
        {
            creature.discard();
        }
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("QUEEN-SNIFF: " + what);
        }
    }
}
