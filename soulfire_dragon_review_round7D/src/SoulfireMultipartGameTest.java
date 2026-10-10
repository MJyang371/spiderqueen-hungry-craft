package spiderqueenhungrycraft.soulfire;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of round 7H: a soul fire dragon is really made of several hit regions, and one blow is one blow.
 *
 * <p>Both of these are asked through the ordinary game ({@code hurt} on a region, {@code startBite} on a victim), never
 * through a private path, so a passing test is a statement about the game and not about the test.
 *
 * <p>It only runs in a game test run ({@code runGameTestServer} or {@code /test}).
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SoulfireMultipartGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the unit of these tests stands: on the floor block of the test structure. */
    private static final BlockPos DRAGON_POS = new BlockPos(2, 1, 2);

    /** The six regions the unit must carry, by name. */
    private static final Set<String> EXPECTED_REGIONS = Set.of("head", "neck", "body", "left_wing", "right_wing", "tail");

    private SoulfireMultipartGameTest()
    {
    }

    private static void expect(GameTestHelper helper, String what, boolean ok)
    {
        if (!ok)
        {
            helper.fail(what);
        }
    }

    /** The rig itself: six named regions, each with an identity of its own, on a unit that says it has parts. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_multipart", timeoutTicks = TIMEOUT_TICKS)
    public static void theunitcarriessixnamedhitregions(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        PartEntity<?>[] parts = dragon.getParts();

        expect(helper, "the unit says it is a multi-part entity", dragon.isMultipartEntity());
        expect(helper, "and it carries six hit regions", parts != null && parts.length == 6);

        if (parts == null || parts.length != 6)
        {
            return;
        }

        Set<String> names = new HashSet<>();

        for (PartEntity<?> part : parts)
        {
            expect(helper, "every region is a region of this mod", part instanceof SoulfireDragonPart);
            expect(helper, "and every region belongs to this unit", part.getParent() == dragon);

            if (part instanceof SoulfireDragonPart one)
            {
                names.add(one.getPartName());
            }
        }

        expect(helper, "the regions are head, neck, body, both wings and tail: " + names, names.equals(EXPECTED_REGIONS));

        for (int index = 0; index < parts.length; index++)
        {
            expect(helper, "region " + index + " has an identity of its own, one above the unit and never the same twice",
                    parts[index].getId() == dragon.getId() + index + 1);
        }

        helper.succeed();
    }

    /** The regions travel with the animal: move the dragon and the regions are found around its new place. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_multipart", timeoutTicks = TIMEOUT_TICKS)
    public static void thehitregionsfollowtheunit(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        PartEntity<?>[] parts = dragon.getParts();

        expect(helper, "the unit carries its regions", parts != null && parts.length == 6);

        if (parts == null || parts.length != 6)
        {
            return;
        }

        double startX = dragon.getX();
        BlockPos moved = helper.absolutePos(DRAGON_POS).offset(12, 0, 0);
        dragon.teleportTo(moved.getX() + 0.5D, dragon.getY(), moved.getZ() + 0.5D);

        int[] ticks = {0};

        helper.onEachTick(() ->
        {
            ticks[0]++;

            if (ticks[0] == 3)
            {
                Vec3 body = parts[2].getBoundingBox().getCenter();

                expect(helper, "the body region stands with its dragon: " + body.x + " against " + dragon.getX(),
                        Math.abs(body.x - dragon.getX()) < 1.5D && Math.abs(body.z - dragon.getZ()) < 1.5D);
                expect(helper, "and it really travelled the twelve blocks the dragon travelled",
                        Math.abs(body.x - startX) > 8.0D);
                helper.succeed();
            }
        });
    }

    /** One hit on a wing is one hit on the dragon: the health pool is shared, and it is not drained six times over. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_multipart", timeoutTicks = TIMEOUT_TICKS)
    public static void ahittothewingreachesitsdragon(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        PartEntity<?>[] parts = dragon.getParts();
        float before = dragon.getHealth();

        expect(helper, "the unit carries its regions", parts != null && parts.length == 6);

        if (parts == null || parts.length != 6)
        {
            return;
        }

        boolean struck = parts[3].hurt(helper.getLevel().damageSources().generic(), 5.0F);

        expect(helper, "a blow at the left wing is not swallowed", struck);
        expect(helper, "and the dragon itself lost exactly those five points, once: " + before + " -> "
                + dragon.getHealth(), Math.abs(before - dragon.getHealth() - 5.0F) < 0.001F);

        float afterFirst = dragon.getHealth();

        // The other wing is a second region of the same animal: a blow that arrives in the same moment must not be counted
        // twice just because there is more than one region to hit.
        parts[4].hurt(helper.getLevel().damageSources().generic(), 5.0F);

        expect(helper, "a second region hit in the same moment does not drain the animal twice: " + afterFirst + " -> "
                + dragon.getHealth(), Math.abs(dragon.getHealth() - afterFirst) < 0.001F);

        helper.succeed();
    }

    /** No region outlives its dragon: the whole rig goes when the animal goes. */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_multipart", timeoutTicks = TIMEOUT_TICKS)
    public static void thehitregionsleavethedragon(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);
        PartEntity<?>[] parts = dragon.getParts();

        expect(helper, "the unit carries its regions", parts != null && parts.length == 6);

        if (parts == null || parts.length != 6)
        {
            return;
        }

        dragon.discard();

        for (PartEntity<?> part : parts)
        {
            expect(helper, "region " + part.getId() + " left with its dragon", part.isRemoved());
        }

        helper.succeed();
    }

    /**
     * The bite of 7H: the jaws close once, the blow falls once, and the clock ends the bite by itself. A second bite can
     * not be started while the first is still running, which is the other half of "one bite is one blow".
     */
    @GameTest(template = "humanspawn_flat", batch = "soulfire_multipart", timeoutTicks = 200)
    public static void abitefallsandonceonly(GameTestHelper helper)
    {
        SoulfireDragonEntity dragon = helper.spawn(HungryCraftEntities.SOULFIRE_DRAGON.get(), DRAGON_POS);

        // A husk stands in front of it: it is a stranger, so it may be hunted, and being undead it does not burn in the
        // daylight of the open test platform, which would otherwise take health the bite never took.
        Mob victim = helper.spawn(EntityType.HUSK, DRAGON_POS.offset(2, 0, 0));
        victim.setNoAi(true);

        float before = victim.getHealth();
        boolean inReach = dragon.distanceTo(victim) <= SoulfireDragonEntity.MELEE_REACH + 1.5D;

        expect(helper, "the victim of this test stands in reach and may be hunted: d=" + dragon.distanceTo(victim)
                + " huntable=" + SoulfireTargeting.isHuntable(dragon, victim) + " reach=" + inReach
                + " victimRemoved=" + victim.isRemoved() + " dragonRemoved=" + dragon.isRemoved(),
                inReach && SoulfireTargeting.isHuntable(dragon, victim));
        expect(helper, "a unit with an enemy in reach starts a bite", dragon.startBite(victim));
        expect(helper, "and the bite is running", dragon.isBiting());
        expect(helper, "and no second bite can be started while the first is still running",
                !dragon.startBite(victim));

        int[] ticks = {0};
        int[] drops = {0};
        float[] last = {before};

        // The clock of a game test and the clock of the unit are not asked to agree tick for tick: what is counted here is
        // how often the health of the victim falls at all, and that the biting clock ends by itself. One bite, one drop.
        helper.onEachTick(() ->
        {
            ticks[0]++;
            float now = victim.getHealth();

            if (now < last[0] - 0.001F)
            {
                drops[0]++;
                expect(helper, "the bite takes health before it is over: " + last[0] + " -> " + now + " at tick "
                        + ticks[0], ticks[0] <= SoulfireDragonEntity.BITE_TICKS + 6);
                last[0] = now;
            }

            if (ticks[0] == SoulfireDragonEntity.BITE_TICKS + 25)
            {
                expect(helper, "the victim of the bite lost health exactly once: " + drops[0] + " drop(s), " + before
                        + " -> " + now, drops[0] == 1);
                expect(helper, "and the bite ended by itself, with nothing left running", !dragon.isBiting());
                helper.succeed();
            }
        });
    }
}
