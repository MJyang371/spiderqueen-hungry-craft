package spiderqueenhungrycraft.entity;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ai.HumanCombatBrain;
import spiderqueenhungrycraft.faction.FactionId;
import spiderqueenhungrycraft.faction.FactionResolver;
import spiderqueenhungrycraft.human.HumanArchetype;
import spiderqueenhungrycraft.human.HumanEntityTypes;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof of the bow use of an ordinary human (bow animation fix).
 *
 * <p>It watches one real fight - a human and a monster, driven by the production {@link HumanCombatBrain} - and
 * asserts the whole lifecycle of a shot in order:
 *
 * <pre>
 * NOT_USING -&gt; USING_BOW -&gt; the arrow leaves -&gt; NOT_USING
 * </pre>
 *
 * <p>The failure this test is written against is {@code NOT_USING -> arrow -> NOT_USING}, and the second one is a use
 * state whose model question ({@link HumanCombatantEntity#isDrawingBow()}) is false: both mean "he shot without
 * drawing", which is what the players saw on the client.
 *
 * <p>What it can and can not prove: it runs on the server, where the use state was always correct - so it verifies
 * the state machine, the shot timing (the draw is held for {@link HumanCombatBrain#BOW_DRAW_TICKS} ticks, not
 * released early) and the question the model now asks. It can <b>not</b> reproduce the client side packet order that
 * broke the animation, because that race exists only in the client's copy of the use state; that part is covered by
 * the decision the model makes (which no longer reads the unsynced {@code useItem} copy) and has to be confirmed on a
 * real client.
 *
 * <p>Nothing of the fight is changed by this test: it only reads state.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanBowUseGameTest
{
    /** The half width of the stone site, so the human and the monster both stand on solid, open ground. */
    private static final int SITE_HALF_WIDTH = 14;

    /** How far away from the world spawn this test builds its site: outside the 96 block ring of the spawn test. */
    private static final int SITE_OFFSET_X = 192;
    private static final int SITE_OFFSET_Z = 0;

    /** The floor of the site, the same height the spawn test uses: above the flat surface of the test world. */
    private static final int SITE_FLOOR_Y = 70;

    /** The distance between the human and its victim: inside the bow band of the brain (6 to 18 blocks). */
    private static final int TARGET_DISTANCE = 12;

    /** How long the human may take to start drawing at all. */
    private static final int DRAW_DEADLINE = 240;

    /** How long the whole shot may take, draw included. */
    private static final int ARROW_DEADLINE = 300;

    /** How long the draw may at least / at most be held before the arrow leaves, around the brain's draw time. */
    private static final int MIN_HELD_TICKS = HumanCombatBrain.BOW_DRAW_TICKS - 4;
    private static final int MAX_HELD_TICKS = HumanCombatBrain.BOW_DRAW_TICKS + 10;

    /** The box around the human in which the arrow of this test is looked for. */
    private static final int ARROW_SEARCH_RADIUS = 24;

    private HumanBowUseGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "human_bow", timeoutTicks = DRAW_DEADLINE + ARROW_DEADLINE)
    public static void ordinaryHuman_drawsTheBowBeforeTheArrowLeaves(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos site = buildSite(level);
        placeAnchor(level, site);

        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.HUMAN_COMBATANT.get(), level, site,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof HumanCombatantEntity human))
        {
            helper.fail("the human combatant could not be placed: " + placed);
            return;
        }

        // The identity promises of the tidy round, asked on a live ordinary human: still a human unit of the HUMAN
        // faction, still an ordinary one, and still carrying a bow.
        if (FactionResolver.getFaction(human) != FactionId.HUMAN)
        {
            helper.fail("the ordinary human left the human faction: " + FactionResolver.getFaction(human));
        }

        if (HumanEntityTypes.archetypeOf(human) != HumanArchetype.STANDARD)
        {
            helper.fail("the ordinary human is not set up as a standard human: " + HumanEntityTypes.archetypeOf(human));
        }

        if (!human.hasBow())
        {
            helper.fail("the human was placed without a bow, the fight has nothing to draw");
            return;
        }

        Zombie victim = EntityType.ZOMBIE.create(level);

        if (victim == null)
        {
            helper.fail("the monster of this test could not be created");
            return;
        }

        BlockPos victimPos = site.offset(TARGET_DISTANCE, 0, 0);
        victim.moveTo(victimPos.getX() + 0.5D, victimPos.getY(), victimPos.getZ() + 0.5D, 180.0F, 0.0F);

        // The monster has to stay for the whole shot, wait or no wait: a vanilla monster despawns when the nearest
        // player is more than 128 blocks away, and this site is built outside that distance on purpose.
        victim.setPersistenceRequired();
        level.addFreshEntity(victim);

        if (!HumanCombatantTargeting.isAttackableTarget(human, victim))
        {
            helper.fail("the human does not consider the monster a target - is human_combatant_ignores_monsters on?");
            return;
        }

        human.setTarget(victim);

        SpiderQueenHungryCraft.LOGGER.info("[BowTest] human at {} with bow={}, blade={}, drawTime={}t against {} at "
                        + "distance {}", human.blockPosition(), human.hasBow(), human.hasMeleeWeapon(),
                HumanCombatBrain.BOW_DRAW_TICKS, victim.getType().toShortString(), TARGET_DISTANCE);

        helper.onEachTick(new Probe(helper, level, human, victim)::tick);
    }

    /**
     * A player that keeps the site ticking, and nothing else.
     *
     * <p>It is a <b>spectator</b> on purpose: this test only needs the chunks of its site to tick their entities, but
     * a normal player would make the surface spawner of this mod populate the ring around it, and those new humans
     * would walk into a test that is about one single human. A spectator is skipped by that spawner (and by the
     * village garrison) and can not be attacked by anything, so the site stays quiet.
     */
    private static ServerPlayer placeAnchor(ServerLevel level, BlockPos site)
    {
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "bow_test_anchor"));
        anchor.setGameMode(GameType.SPECTATOR);
        anchor.moveTo(site.getX() + 0.5D, site.getY(), site.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
        }

        return anchor;
    }

    /**
     * Builds a flat, open stone site near the world spawn of the test level.
     *
     * <p>It is written by the test and not by the template, because the two entities of this test need twelve blocks of
     * clear ground between them. The chunks of the site are forced loaded before a single block is written: a chunk
     * that is not loaded does not tick its entities, and a human that does not tick never draws a bow.
     *
     * @return the position one block above the floor, where a mob can stand.
     */
    private static BlockPos buildSite(ServerLevel level)
    {
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_OFFSET_X, SITE_FLOOR_Y, spawn.getZ() + SITE_OFFSET_Z);
        int chunkX = centre.getX() >> 4;
        int chunkZ = centre.getZ() >> 4;

        for (int cx = -1; cx <= 1; ++cx)
        {
            for (int cz = -1; cz <= 1; ++cz)
            {
                level.setChunkForced(chunkX + cx, chunkZ + cz, true);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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

    /**
     * Watches the shot of that human, tick by tick, and asserts the three promises of the bow fix.
     *
     * <p>It never drives the fight: the human decides on its own, exactly as it does in the game. The probe only reads
     * the use state, the question the model asks and the arrow that really left the bow.
     */
    private static final class Probe
    {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final HumanCombatantEntity human;
        private final Zombie victim;
        private final AABB search;

        private int elapsed;
        private int drawStartedAt = -1;
        private boolean finished;

        Probe(GameTestHelper helper, ServerLevel level, HumanCombatantEntity human, Zombie victim)
        {
            this.helper = helper;
            this.level = level;
            this.human = human;
            this.victim = victim;
            this.search = human.getBoundingBox().inflate(ARROW_SEARCH_RADIUS);
        }

        void tick()
        {
            if (this.finished)
            {
                return;
            }

            ++this.elapsed;

            if (this.elapsed % 60 == 0)
            {
                // A human that never draws is either not ticking at all or refusing the target, and those two look
                // exactly the same from the outside. One line per second separates them.
                SpiderQueenHungryCraft.LOGGER.info("[BowTest] t+{} humanTick={} pos={} target={} brain={} using={} "
                                + "zombie={} zombieTick={}",
                        this.elapsed, this.human.tickCount, this.human.blockPosition(), this.human.getTarget(),
                        this.human.brain().state().id(), this.human.isUsingItem(), this.victim.blockPosition(),
                        this.victim.tickCount);
            }

            // 1) The question the model asks must never be false while the human is using an item. That is the state
            //    "he holds a bow and the game thinks he is using something else", which is what hid the draw pose.
            if (this.human.isUsingItem() && !this.human.isDrawingBow())
            {
                this.fail("the human uses an item while the bow pose question is false: used="
                        + this.human.getUseItem() + ", hand=" + this.human.getUsedItemHand()
                        + ", mainHand=" + this.human.getMainHandItem()
                        + ", brain=" + this.human.brain().state().id() + ", t+" + this.elapsed);
                return;
            }

            // 2) An arrow in the air ends the shot - and it must have been drawn before it.
            AbstractArrow arrow = this.findArrow();

            if (arrow != null)
            {
                this.finish(arrow);
                return;
            }

            if (this.human.isDrawingBow())
            {
                if (this.drawStartedAt < 0)
                {
                    this.drawStartedAt = this.elapsed;
                    SpiderQueenHungryCraft.LOGGER.info("[BowTest] the draw started at +{}t (mainHand={}, brain={})",
                            this.elapsed, this.human.getMainHandItem(), this.human.brain().state().id());
                }

                if (this.elapsed - this.drawStartedAt > MAX_HELD_TICKS)
                {
                    this.fail("the draw is held for more than " + MAX_HELD_TICKS + " ticks without an arrow");
                }

                return;
            }

            // A draw that ended without an arrow is not a failure of this test yet (he may have lost sight of the
            // monster and picked the fight up again), but it must not silently reset the clock forever.
            this.drawStartedAt = -1;

            if (this.elapsed > DRAW_DEADLINE)
            {
                this.fail("no bow was drawn within " + DRAW_DEADLINE + " ticks: brain="
                        + this.human.brain().state().id() + ", bow=" + this.human.hasBow() + ", target="
                        + this.human.getTarget() + ", distance=" + this.human.distanceTo(this.human.getTarget()));
            }
        }

        /** The shot left the bow: assert the draw came before it, in the right length, and that it is over. */
        private void finish(AbstractArrow arrow)
        {
            this.finished = true;

            if (this.drawStartedAt < 0)
            {
                this.fail("an arrow left the bow without a single tick of drawing before it");
                return;
            }

            int held = this.elapsed - this.drawStartedAt;

            if (held < MIN_HELD_TICKS)
            {
                this.fail("the arrow left after only " + held + " ticks of drawing, the charge time changed");
                return;
            }

            if (this.human.isDrawingBow() || this.human.isUsingItem())
            {
                this.fail("the use state of the human did not end with the shot: using=" + this.human.isUsingItem()
                        + ", drawing=" + this.human.isDrawingBow());
                return;
            }

            if (arrow.getOwner() != this.human)
            {
                this.fail("the arrow of this test belongs to somebody else: " + arrow.getOwner());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[BowTest] RESULT draw at +{}t, arrow at +{}t, held {}t (expected "
                            + "{}t), using={}, drawing={}, arrowCrit={}, arrowVelocity={}",
                    this.drawStartedAt, this.elapsed, held, HumanCombatBrain.BOW_DRAW_TICKS,
                    this.human.isUsingItem(), this.human.isDrawingBow(), arrow.isCritArrow(),
                    arrow.getDeltaMovement().length());

            this.helper.succeed();
        }

        /** @return the arrow this human shot, or null while none is in the air yet. */
        @Nullable
        private AbstractArrow findArrow()
        {
            for (AbstractArrow arrow : this.level.getEntitiesOfClass(AbstractArrow.class, this.search,
                    candidate -> candidate.getOwner() == this.human))
            {
                return arrow;
            }

            return null;
        }

        private void fail(String message)
        {
            this.finished = true;
            this.helper.fail(message);
        }
    }
}
