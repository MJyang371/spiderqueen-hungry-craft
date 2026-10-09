package spiderqueenhungrycraft.entity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The animation contract of the humanoid bodies of this mod (animation round).
 *
 * <p>The report this file is written against has three sentences, and all three are about the same seam: what the
 * renderer of a body is allowed to ask, and where the answer comes from.
 *
 * <ol>
 * <li><b>"the attack is not the swing of the game"</b> - an earlier round drew a sword cut of its own from a synced
 * counter of the body, twelve ticks long where the swing of the game lasts six, and it always moved a hard coded arm.
 * {@link #aSwingIsTheSwingOfTheGameAndItMovesTheWeaponArm} refuses that counter by name and then watches a real swing of
 * every body of this mod: it has to be the swing of the game, on the arm the game itself picks for the weapon.</li>
 * <li><b>"the shield hand attacks"</b> - the same hard coded arm was the offhand one on a body that carries its shield
 * in the offhand, which is a Sweatlord. With the swing of the game this is impossible by construction: the game asks
 * {@code getAttackArm} = the main arm for a main hand swing, and this test proves the main arm of every body is the
 * right one, the arm the sword is drawn on.</li>
 * <li><b>"the draw and the guard are sometimes not drawn at all"</b> - the models of this mod used to ask the unsynced
 * copies ({@code getUseItem()} for the bow, {@code isBlocking()} for the shield).
 * {@link #theDrawAndTheGuardAreAnsweredFromSyncedState} pins the two questions every model must ask instead, and the
 * shield half of it shows the difference the old question made: the game's own {@code isBlocking} answers a guard only
 * after five ticks of use, so the arm of a body that had just raised its shield was drawn as if the shield were not
 * there. Add to that the ordering a client can really be in - the synced using flag arrives in a different packet than
 * the equipment it belongs to, and the unsynced copy is then guessed from the hand that still held the old item - and
 * the draw of a bow could be missing for its whole length while the server drew and shot it normally.</li>
 * </ol>
 *
 * <p>What this file can not do, and says so instead of pretending: it can not render. It measures the state the
 * renderer reads, which is where all three defects live, and it can not reproduce the packet ordering of a client -
 * a game test has no client at all. The two models are therefore not asked directly either: they are client classes
 * that a dedicated server must never load.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanoidAnimationGameTest
{
    /** The site numbers of this round, at its own corner of the test world. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 10;
    private static final int SITE_X = 5120;
    private static final int SITE_Z = 512;

    /**
     * The length of the swing of the game: six ticks (see {@code LivingEntity#updateSwingTime}), without haste and
     * without slowness. A swing of this mod that is longer than this is a private animation again.
     */
    private static final int VANILLA_SWING_TICKS = 6;

    /** How long the probe waits before it touches anything: an entity added this tick is not in the index yet. */
    private static final int SETTLE_TICKS = 3;

    private HumanoidAnimationGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "humanoid_animation", timeoutTicks = 300)
    public static void aSwingIsTheSwingOfTheGameAndItMovesTheWeaponArm(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        List<LivingEntity> bodies = placeBodies(level, openSite(level), helper);

        if (bodies == null)
        {
            return;
        }

        // The private animation by name first: a body that counts a swing of its own is a body whose attack is not the
        // swing of the game, and that counter is exactly what made a hard coded arm beat the arm of the weapon.
        for (LivingEntity body : bodies)
        {
            for (String name : List.of("swingAnimTicks", "setSwingAnimTicks"))
            {
                if (declares(body.getClass(), name))
                {
                    helper.fail("the body " + body.getType() + " still declares " + name + ": the attack animation of"
                            + " this mod has to be the swing of the game and not a counter of its own");
                    return;
                }
            }

            // The weapon of the scenario is in the main hand, so the arm the game animates is the arm of the weapon.
            body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }

        helper.onEachTick(new SwingProbe(helper, bodies)::tick);
    }

    /**
     * Watches one real swing of every body of this mod: whether the swing of the game really runs, how long it runs,
     * and whether the arm it moves is the arm that holds the weapon - and never the arm of the shield.
     */
    private static final class SwingProbe
    {
        private final GameTestHelper helper;
        private final List<LivingEntity> bodies;
        private final List<Integer> swingTicks = new ArrayList<>();
        private final List<Float> peakAttackAnim = new ArrayList<>();
        private final List<Boolean> offhandSwung = new ArrayList<>();
        private int wait = SETTLE_TICKS;
        private int measured;
        private boolean started;
        private boolean judged;

        SwingProbe(GameTestHelper helper, List<LivingEntity> bodies)
        {
            this.helper = helper;
            this.bodies = bodies;
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            if (!this.started)
            {
                if (--this.wait > 0)
                {
                    return;
                }

                this.started = true;

                for (LivingEntity body : this.bodies)
                {
                    this.swingTicks.add(0);
                    this.peakAttackAnim.add(0.0F);
                    this.offhandSwung.add(false);
                    swingMainHand(body);
                }

                return;
            }

            for (int i = 0; i < this.bodies.size(); ++i)
            {
                LivingEntity body = this.bodies.get(i);

                if (!body.swinging)
                {
                    continue;
                }

                this.swingTicks.set(i, this.swingTicks.get(i) + 1);
                this.peakAttackAnim.set(i, Math.max(this.peakAttackAnim.get(i), body.getAttackAnim(0.0F)));

                if (body.swingingArm != InteractionHand.MAIN_HAND)
                {
                    this.offhandSwung.set(i, true);
                }
            }

            if (++this.measured <= VANILLA_SWING_TICKS + 4)
            {
                return;
            }

            this.judged = true;
            judge();
        }

        private void judge()
        {
            StringBuilder report = new StringBuilder();

            for (int i = 0; i < this.bodies.size(); ++i)
            {
                LivingEntity body = this.bodies.get(i);
                int ticks = this.swingTicks.get(i);
                float peak = this.peakAttackAnim.get(i);

                report.append("\n  ").append(body.getType()).append(": ").append(ticks).append(" ticks of swing, peak"
                        + " attack animation ").append(peak).append(", swingTime ").append(body.swingTime)
                        .append(", swinging ").append(body.swinging).append(", main arm ").append(body.getMainArm())
                        .append(", swinging arm ").append(body.swingingArm).append(", main hand ")
                        .append(body.getMainHandItem().getItem()).append(", offhand ")
                        .append(body.getOffhandItem().getItem());

                if (ticks <= 0)
                {
                    this.helper.fail("the body " + body.getType() + " never swung, so nothing was measured" + report);
                    return;
                }

                if (Boolean.TRUE.equals(this.offhandSwung.get(i)))
                {
                    this.helper.fail("the body " + body.getType() + " swung its offhand, which on this body is the arm"
                            + " of the shield" + report);
                    return;
                }

                if (ticks > VANILLA_SWING_TICKS + 2)
                {
                    this.helper.fail("the swing of " + body.getType() + " lasted " + ticks + " ticks where the swing"
                            + " of the game lasts " + VANILLA_SWING_TICKS + ": a private animation again" + report);
                    return;
                }

                if (peak <= 0.0F)
                {
                    this.helper.fail("the vanilla attack animation of " + body.getType() + " never moved, so the swing"
                            + " a client is shown is not the swing of the game" + report);
                    return;
                }

                if (body.getMainArm() != HumanoidArm.RIGHT || !body.getMainHandItem().is(Items.DIAMOND_SWORD))
                {
                    this.helper.fail("the weapon of " + body.getType() + " is not on the arm the game animates"
                            + report);
                    return;
                }
            }

            System.out.println("[HumanoidAnimation] swings: " + report);
            this.helper.succeed();
        }
    }

    /**
     * Pins the two questions every model of a humanoid body of this mod has to ask for a pose, and proves why the
     * vanilla questions it replaced are not good enough:
     *
     * <ul>
     * <li>the bow: {@code isDrawingBow()} answers from the synced using flag, the synced used hand and the synced main
     * hand item - never from {@code getUseItem()}, the copy a client only guesses from the hand it saw when the flag
     * arrived,</li>
     * <li>the shield: {@code isShieldGuardUp()} answers on the very tick the guard goes up, while the game's own
     * {@code isBlocking()} is false for the first five ticks of it - the assertion below holds that difference, so this
     * test can not silently start passing for the wrong reason.</li>
     * </ul>
     */
    @GameTest(template = "humanspawn_flat", batch = "humanoid_animation", timeoutTicks = 300)
    public static void theDrawAndTheGuardAreAnsweredFromSyncedState(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        List<LivingEntity> bodies = placeBodies(level, openSite(level), helper);

        if (bodies == null)
        {
            return;
        }

        StringBuilder report = new StringBuilder();

        for (LivingEntity body : bodies)
        {
            body.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            body.startUsingItem(InteractionHand.MAIN_HAND);
            boolean drawing = isDrawingBow(body);
            body.stopUsingItem();

            body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            body.startUsingItem(InteractionHand.OFF_HAND);
            boolean guardUp = isShieldGuardUp(body);
            boolean vanillaBlocking = body.isBlocking();
            body.stopUsingItem();

            report.append("\n  ").append(body.getType()).append(": isDrawingBow=").append(drawing)
                    .append(", isShieldGuardUp=").append(guardUp)
                    .append(", isBlocking on the first tick of the guard=").append(vanillaBlocking);

            if (!drawing)
            {
                helper.fail("the body " + body.getType() + " does not answer 'drawing a bow' from synced state, so a"
                        + " client can be shown an arrow out of a bow nobody ever pulled" + report);
                return;
            }

            if (!guardUp)
            {
                helper.fail("the body " + body.getType() + " does not answer 'shield up' from synced state" + report);
                return;
            }

            if (vanillaBlocking)
            {
                helper.fail("the game's own isBlocking already answered on the first tick of the guard of "
                        + body.getType() + ", so this test no longer shows the difference the synced question makes"
                        + report);
                return;
            }
        }

        System.out.println("[HumanoidAnimation] synced pose questions: " + report);
        helper.succeed();
    }

    /**
     * Places one body of every humanoid kind of this mod that has a fight: the standard human combatant, a prepared
     * human, the Sweatlord and an infested Steve. Every one of them is switched off, because this file measures the
     * state a renderer reads and not a fight - a brain that starts a draw or a guard of its own would move the hands
     * under the test.
     */
    @Nullable
    private static List<LivingEntity> placeBodies(ServerLevel level, BlockPos centre, GameTestHelper helper)
    {
        List<EntityType<?>> types = List.of(
                HungryCraftEntities.HUMAN_COMBATANT.get(),
                HungryCraftEntities.HIGH_PREPARED_HUMAN.get(),
                HungryCraftEntities.SWEATLORD_STEVE.get(),
                HungryCraftEntities.INFESTED_STEVE.get());
        List<LivingEntity> bodies = new ArrayList<>();

        for (int i = 0; i < types.size(); ++i)
        {
            Entity placed = place(level, types.get(i), centre, i * 4, 0);

            if (!(placed instanceof Mob body))
            {
                helper.fail("the body " + types.get(i) + " could not be placed: " + placed);
                return null;
            }

            body.setNoAi(true);
            bodies.add(body);
        }

        return bodies;
    }

    /** Builds the flat pad of this file at its own corner of the test world. */
    private static BlockPos openSite(ServerLevel level)
    {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // The chunks have to be forced, or the bodies of this file are only loaded and never ticked: a body that is not
        // ticked never advances the swing of the game, and this file would measure its own placement instead of the
        // animation. (The first run of this file did exactly that: swingTime stayed -1 and the swing never ran out.)
        for (int cx = -2; cx <= 2; ++cx)
        {
            for (int cz = -2; cz <= 2; ++cz)
            {
                level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, true);
            }
        }

        for (int dx = -SITE_HALF_WIDTH; dx <= SITE_HALF_WIDTH; ++dx)
        {
            for (int dz = -SITE_HALF_WIDTH; dz <= SITE_HALF_WIDTH; ++dz)
            {
                level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y - 1, centre.getZ() + dz),
                        Blocks.STONE.defaultBlockState(), 2);

                for (int dy = 0; dy <= 4; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return centre;
    }

    /** Places one body of that type on the pad, without running the spawn logic of the world. */
    @Nullable
    private static Entity place(ServerLevel level, EntityType<?> type, BlockPos centre, int dx, int dz)
    {
        Entity entity = type.create(level);

        if (entity == null)
        {
            return null;
        }

        entity.moveTo(centre.getX() + dx + 0.5D, SITE_FLOOR_Y, centre.getZ() + dz + 0.5D, 0.0F, 0.0F);

        if (entity instanceof Mob mob)
        {
            mob.setPersistenceRequired();
        }

        level.addFreshEntity(entity);

        return entity;
    }

    /** @return true when that class declares a method of that name, which is how a private animation is caught. */
    private static boolean declares(Class<?> type, String name)
    {
        for (Method method : type.getDeclaredMethods())
        {
            if (method.getName().equals(name))
            {
                return true;
            }
        }

        return false;
    }

    /** Swings the main arm of that body through the one call its own fight uses. */
    private static void swingMainHand(LivingEntity body)
    {
        if (body instanceof HumanCombatantEntity human)
        {
            human.swingMainHand();
            return;
        }

        if (body instanceof SweatlordSteveEntity sweatlord)
        {
            sweatlord.swingMainHand();
            return;
        }

        if (body instanceof InfestedSteveEntity brood)
        {
            brood.swingMainHand();
        }
    }

    /** @return the synced draw question of that body, false for a body that has no bow of its own. */
    private static boolean isDrawingBow(LivingEntity body)
    {
        if (body instanceof HumanCombatantEntity human)
        {
            return human.isDrawingBow();
        }

        if (body instanceof SweatlordSteveEntity sweatlord)
        {
            return sweatlord.isDrawingBow();
        }

        if (body instanceof InfestedSteveEntity brood)
        {
            return brood.isDrawingBow();
        }

        return false;
    }

    /** @return the synced guard question of that body. */
    private static boolean isShieldGuardUp(LivingEntity body)
    {
        if (body instanceof HumanCombatantEntity human)
        {
            return human.isShieldGuardUp();
        }

        if (body instanceof SweatlordSteveEntity sweatlord)
        {
            return sweatlord.isShieldGuardUp();
        }

        if (body instanceof InfestedSteveEntity brood)
        {
            return brood.isShieldGuardUp();
        }

        return false;
    }
}
