package spiderqueenhungrycraft.ability;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * The runtime proof of the inherent armor of the queen (the anatomy of a queen always counts).
 *
 * <p>The promise of this round is one sentence: the body of a queen is worth a full set of leather armor, and that armor
 * is <b>always</b> hers - what she puts on comes on top of it instead of replacing it. The old rule was the opposite
 * ("inherent armor while no armor piece is worn"), so the test pins down both halves of it: the trigger of the old bug
 * (a single armor piece in the slots) and the sum itself (a whole set of armor adds to the seven points).
 *
 * <p>It only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server never
 * executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class QueenInherentArmorGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** The armor of a full set of leather, which is what the body of a queen is worth. */
    private static final int INHERENT = 7;

    /** A full set of diamond armor, the way the game adds it up: helmet 3, chestplate 6, leggings 8 and boots 3. */
    private static final double DIAMOND_SET = 20.0D;

    /** The modifier of this test that stands in for that set of armor on the attribute of the queen. */
    private static final UUID TEST_ARMOR_ID = UUID.fromString("2f6a1c58-6f31-4a6b-9f2d-2b7c4a1d9e42");

    private QueenInherentArmorGameTest()
    {
    }

    /**
     * <b>The inherent armor belongs to the chitin shell of the queen, and worn armor adds to it.</b>
     *
     * <p>Since the Skill Progression round the seven points are a bought node and no longer a gift of the queen flag
     * itself, so the test starts with the locked side: a queen without that node has none of it. Then the node is
     * bought - and from there on the promises of the anatomy round hold unchanged: the seven points without any
     * equipment, the same seven after a second check, the same seven with an armor piece in the slots (which used to
     * take all seven away), twenty seven with a whole set worn over it, seven again when that set is gone, none at all
     * for a queen whose node was taken back, and none for a player who is not the queen any more.
     */
    @GameTest(template = "humanspawn_flat", batch = "queen_inherent_armor", timeoutTicks = TIMEOUT_TICKS)
    public static void theinherentarmorisalwaysinplace(GameTestHelper helper)
    {
        ServerPlayer queen = placeQueen(helper);

        QueenPhysiology.updateArmor(queen);

        expect(helper, "a queen who never bought the chitin shell has no inherent armor",
                queen.getArmorValue() == 0);

        QueenSkills.unlock(queen, QueenSkills.CHITIN_SHELL);
        QueenPhysiology.updateArmor(queen);

        expect(helper, "a queen without a single armor piece has the seven points of her own body",
                queen.getArmorValue() == INHERENT);

        QueenPhysiology.updateArmor(queen);

        expect(helper, "checking again never stacks a second copy of the inherent armor",
                queen.getArmorValue() == INHERENT);

        // The trigger of the old rule: as much as one armor piece used to take the whole physiology of the queen away.
        // The helmet itself is not counted yet, and that is what makes this assertion sharp: a player who is not in the
        // world is never ticked, so the armor the game would add for that helmet is not applied - what is measured here
        // is only what the physiology of the queen hands out, and the old code dropped all seven of it at this moment.
        queen.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        QueenPhysiology.updateArmor(queen);

        expect(helper, "a queen who wears a helmet keeps the armor of her own body",
                queen.getArmorValue() == INHERENT);

        // And the promise of this round: what she wears adds ON TOP of the seven points.
        AttributeInstance armor = queen.getAttribute(Attributes.ARMOR);
        armor.addTransientModifier(new AttributeModifier(TEST_ARMOR_ID, "test_worn_armor", DIAMOND_SET,
                AttributeModifier.Operation.ADDITION));
        QueenPhysiology.updateArmor(queen);

        expect(helper, "a full set of armor worn over the body of a queen adds to it and does not replace it",
                queen.getArmorValue() == INHERENT + (int) DIAMOND_SET);

        armor.removeModifier(TEST_ARMOR_ID);
        QueenPhysiology.updateArmor(queen);

        expect(helper, "the inherent armor is still there after the worn armor is gone",
                queen.getArmorValue() == INHERENT);

        // The rule belongs to the node and to the queen, never to the armor: a queen whose node was taken back loses the
        // seven points again, dressed or not, and the same call brings a revoked skill back in line (Skill Progression
        // round).
        QueenSkills.revoke(queen, QueenSkills.CHITIN_SHELL);
        QueenSkills.refreshRuntime(queen);

        expect(helper, "a queen whose chitin shell was taken back keeps no inherent armor",
                queen.getArmorValue() == 0);

        // And a player who stops being the queen loses it as well, whatever she wears.
        QueenDataCapability.setQueen(queen, false);
        QueenPhysiology.clear(queen);

        expect(helper, "a player who is not the queen keeps no inherent armor", queen.getArmorValue() == 0);

        helper.succeed();
    }

    // ------------------------------------------------------------------ the furniture of this test

    /** @return a fake queen of this mod: a player of a test server with the queen flag of this mod set. */
    private static ServerPlayer placeQueen(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));
        ServerPlayer queen = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "inherent_armor_test"));
        queen.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        queen.setGameMode(GameType.SURVIVAL);
        QueenDataCapability.setQueen(queen, true);

        // The queen is deliberately NOT put into the level, like in every other game test of this mod: the world is
        // shared with all the other tests, and a stray player standing in a test structure is an answer their AI would
        // suddenly have to consider. Everything this test needs lives on the entity itself.
        return queen;
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("QUEEN-INHERENT-ARMOR: " + what);
        }
    }
}
