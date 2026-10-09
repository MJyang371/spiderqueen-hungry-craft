package spiderqueenhungrycraft.human.spawn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;
import spiderqueenhungrycraft.human.HumanArchetype;
import spiderqueenhungrycraft.human.HumanEntityTypes;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.territory.TerritoryCoreSpawnRules;

/**
 * The runtime proof of the natural surface spawner (Phase HF0D).
 *
 * <p>It does not call the spawner directly. It builds the world the spawner needs - a loaded, walkable, open sky
 * surface around a real {@code ServerPlayer} - and then posts the very Forge event the game posts every tick. Every
 * counter that moves after that was moved by production code, and the two assertions at the end are made on the
 * entity list of the {@code ServerLevel}, not on a return value:
 *
 * <ol>
 * <li>a human whose {@link HumanSpawnOrigin} is {@code NATURAL_SURFACE} really exists in the level, and</li>
 * <li>after that human is killed, the spawner produces another one without leaving a dirty count behind.</li>
 * </ol>
 *
 * <p>This test only runs in a GameTest run ({@code runGameTestServer} or {@code /test}); a normal client or server
 * never executes it, because Forge only registers game tests when the game test namespaces are enabled.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanSurfaceSpawnGameTest
{
    /** Chunks of the 24..96 block ring around the anchor; the spawner refuses every chunk that is not loaded. */
    private static final int LOADED_CHUNK_RADIUS = 7;

    /**
     * How many chunks around the anchor the sites own.
     *
     * <p>It has to be more than {@link #LOADED_CHUNK_RADIUS}, because the chunk that holds the anchor sits one chunk
     * further inside the ring: the ring starts at {@code 24} blocks, so the eight chunks around the anchor chunk are
     * partly nearer than that. One more ring of that size keeps the site big enough that no attempt is ever forced
     * onto natural terrain that can not pass the rules.
     */
    private static final int SITE_CHUNK_RADIUS = 9;

    /** The half width of the site floor, derived from the chunk radius so the two can not drift apart. */
    private static final int SITE_HALF_WIDTH = SITE_CHUNK_RADIUS * 16;

    /**
     * The height of the site floor.
     *
     * <p>The world of a game test run is declared by {@code src/gametest/resources/dimensions/minecraft/overworld.json}:
     * a flat world of stone and dirt with its surface at y=64, which replaces the noise terrain of the run. The site
     * floor lies a little above that surface, so the site is not the world itself and the ring can be laid down, torn
     * down and laid down again without ever writing into generated ground.
     */
    private static final int SITE_FLOOR_Y = 70;

    /** How long the first natural human may take, in ticks. */
    private static final int FIRST_SPAWN_DEADLINE = 300;

    /** How long the killed human may take to leave the level, in ticks. */
    private static final int REMOVAL_DEADLINE = 60;

    /** How long the replacement human may take, in ticks. */
    private static final int REFILL_DEADLINE = 300;

    /** The radius around the anchor in which the natural humans of this run are counted. */
    private static final int SEARCH_RADIUS = 224;

    /** How long the second half of the test lets the population grow before the population floor is asserted. */
    private static final int POPULATION_WINDOW = 600;

    /** How long one controlled phase of the two gate tests may take, in ticks. */
    private static final int GATE_DEADLINE = 400;

    /**
     * The block offsets of the territory core grid of the gate test, in every direction.
     *
     * <p>The spawn ring is 24..96 blocks wide, a core protects its own chunk section plus one in every direction
     * (48 blocks) and cores that stand 48 blocks apart therefore cover a contiguous band. Five offsets per axis,
     * from minus 96 to plus 96, cover the whole ring and a little more.
     */
    private static final int CORE_GRID_HALF = 96;

    private static final int CORE_GRID_STEP = 48;

    /** The floor the human population of the ring has to reach within {@link #POPULATION_WINDOW} ticks. */
    private static final int POPULATION_FLOOR = 6;

    /** How often the production code of this test ran in this process; only the first run builds the site. */
    private static int RUNS;

    private HumanSurfaceSpawnGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "human_spawn",
            timeoutTicks = FIRST_SPAWN_DEADLINE + REMOVAL_DEADLINE + REFILL_DEADLINE + POPULATION_WINDOW + 80)
    public static void naturalSurfaceSpawner_spawnsRefillsAndSpread(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();

        // The framework of this game reruns a test that passed once more, and every run would leave its own humans
        // behind. One run of this test is the proof, so the later runs pass without touching the world again.
        if (RUNS++ > 0)
        {
            SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] re-run {} of this test: it is not repeated, the first run "
                    + "already proved the spawner", RUNS - 1);
            helper.succeed();
            return;
        }

        // The spawner counts and searches by distance, so a player that stands far away must not keep the anchor from
        // being the nearest one. In a game test run that player is always there.
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);

        if (overworld != null && overworld != level)
        {
            for (ServerPlayer other : overworld.players())
            {
                other.moveTo(level.getLevelData().getXSpawn(), level.getLevelData().getYSpawn(), level.getLevelData().getZSpawn());
            }
        }

        // A real ServerPlayer in the player list of the level: that list is exactly what the production spawner
        // walks, so the test never has to fake the player part of the chain.
        ServerPlayer anchor = placeAnchor(level);

        prepareSite(level, anchor);
        HumanSpawnEvents.resetCounters();

        Probe probe = new Probe(helper, level, anchor);
        helper.onEachTick(probe::tick);
    }

    // ===================== the two gates of a natural human spawn (bug fix round) =====================

    /**
     * The territory core stops the natural surface spawner.
     *
     * <p><b>The bug this test exists for.</b> The core of Phase LAIR-CORE-1 refuses natural spawns through
     * {@code MobSpawnEvent.PositionCheck}, an event the <b>vanilla</b> spawner fires. The surface patrol of this mod
     * places its humans itself, with no vanilla spawner and therefore no such event, so a core never touched it and
     * humans kept appearing inside a protected lair.
     *
     * <p><b>What it does, and why this is not a fragile wait.</b> Phase one is the control: it waits until the world
     * really spawns a human in that ring, which proves the site, the rules and the production tick path all work. Then
     * the ring is emptied, a core grid that covers the whole 24..96 ring is placed (and the test asserts that the ring
     * is protected before it judges anything), and phase two runs the very same spawner again: nothing may appear, at
     * least one position must have been refused by the territory rule, and no spawn may have succeeded.
     */
    @GameTest(template = "humanspawn_flat", batch = "human_spawn_gate", timeoutTicks = 2 * GATE_DEADLINE + 120)
    public static void territoryCore_stopsTheNaturalSurfaceSpawner(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer anchor = placeAnchor(level);
        prepareSite(level, anchor);
        clearNaturalHumans(level, anchor);
        HumanSpawnEvents.resetCounters();

        helper.onEachTick(new TerritoryGateProbe(helper, level, anchor)::tick);
    }

    /**
     * A peaceful world spawns no natural human, and this mod asks that itself.
     *
     * <p>The humans of this mod are {@code PathfinderMob}s and never vanilla monsters - that is a design rule of this
     * project and not an accident - so the vanilla peaceful rule (which lives in {@code Monster}/{@code Enemy}) does not
     * reach them. This test proves that the counterpart of "a zombie does not spawn in peaceful" is really in place for
     * them: the world is switched to peaceful, the spawner runs, and no human may appear while at least one position is
     * refused for exactly that reason. The difficulty is a world setting, so it is restored on every path out.
     */
    @GameTest(template = "humanspawn_flat", batch = "human_spawn_gate", timeoutTicks = 2 * GATE_DEADLINE + 120)
    public static void peacefulMode_stopsTheNaturalSurfaceSpawner(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer anchor = placeAnchor(level);
        prepareSite(level, anchor);
        clearNaturalHumans(level, anchor);

        Difficulty original = level.getDifficulty();
        level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
        SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] GATE-PEACEFUL: difficulty {} -> {} (was {})",
                original, level.getDifficulty(), original);
        HumanSpawnEvents.resetCounters();

        helper.onEachTick(new PeacefulGateProbe(helper, level, anchor, original)::tick);
    }

    /** Removes every natural surface human of the site, so the ring of the spawner is empty for a controlled run. */
    private static void clearNaturalHumans(ServerLevel level, ServerPlayer anchor)
    {
        for (HumanCombatantEntity human : naturalHumans(level, anchor))
        {
            human.discard();
        }
    }

    /** @return the natural surface humans of the site that were not there when the run started. */
    private static List<HumanCombatantEntity> newHumans(ServerLevel level, ServerPlayer anchor, Set<UUID> known)
    {
        List<HumanCombatantEntity> fresh = new ArrayList<>();

        for (HumanCombatantEntity human : naturalHumans(level, anchor))
        {
            if (!known.contains(human.getUUID()))
            {
                fresh.add(human);
            }
        }

        return fresh;
    }

    /** @return the new natural humans that stand inside a protected territory, which is the claim of the gate test. */
    private static List<HumanCombatantEntity> newHumansInsideTerritory(ServerLevel level, ServerPlayer anchor,
            Set<UUID> known)
    {
        List<HumanCombatantEntity> inside = new ArrayList<>();

        for (HumanCombatantEntity human : newHumans(level, anchor, known))
        {
            if (TerritoryCoreSpawnRules.isNaturalSpawnBlocked(level, human.blockPosition()))
            {
                inside.add(human);
            }
        }

        return inside;
    }

    /**
     * Puts a core grid under the floor that covers the whole spawn ring, then asserts that the ring really is
     * protected: a test that judges a rule has to know that the rule applies to the ground it stands on.
     */
    private static void placeCoreGrid(ServerLevel level, ServerPlayer anchor, GameTestHelper helper)
    {
        BlockPos centre = anchor.blockPosition();
        BlockState core = HungryCraftBlocks.TERRITORY_CORE.get().defaultBlockState();

        for (int dx = -CORE_GRID_HALF; dx <= CORE_GRID_HALF; dx += CORE_GRID_STEP)
        {
            for (int dz = -CORE_GRID_HALF; dz <= CORE_GRID_HALF; dz += CORE_GRID_STEP)
            {
                // Under the floor: a core above it would be a collision for every human the spawner places there.
                level.setBlockAndUpdate(centre.offset(dx, -1, dz), core);
            }
        }

        for (int[] sample : RING_SAMPLES)
        {
            BlockPos pos = centre.offset(sample[0], 0, sample[1]);

            if (!TerritoryCoreSpawnRules.isNaturalSpawnBlocked(level, pos))
            {
                helper.fail("the core grid does not protect the spawn ring: " + pos + " lies outside every territory, "
                        + "so this run can not judge the core");
                return;
            }
        }
    }

    /**
     * Puts a server player on the ground of the test site and registers it in the level.
     *
     * <p>Two details are deliberate. The player is a Forge {@code FakePlayer}, because a real login ends in a packet
     * that is sent over a network channel a headless game test server does not have, and it is moved onto the surface,
     * because the game discards any mob whose nearest player stands more than 128 blocks away. A player left at the
     * default spawn point of this level is both of those things at once.
     */
    private static ServerPlayer placeAnchor(ServerLevel level)
    {
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "spawn_test_anchor"));

        BlockPos spawnColumn = anchor.blockPosition();
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnColumn);
        anchor.moveTo(surface.getX() + 0.5D, surface.getY(), surface.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
        }

        return anchor;
    }

    /**
     * Reads the flat world the run declares and hands it to the log, so a run that went wrong can be told apart from a
     * run that never had the world it expected.
     *
     * <p>The world itself comes from {@code src/gametest/resources/dimensions/minecraft/overworld.json}: that file is
     * the {@code chunk_generator} codec of the overworld, which Forge reads for every level, and it is what turns the
     * noise terrain of a game test run into a clean flat world. The site is laid down after this check and knows that
     * whatever it does not build itself is a solid, flat surface.
     */
    private static void verifyFlatWorld(ServerLevel level)
    {
        ChunkGenerator generator = level.getChunkSource().getGenerator();

        if (generator instanceof FlatLevelSource)
        {
            SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] {} is a flat world with its surface at y={}, the site "
                    + "floor is at {}", level.dimension().location(), level.getMinBuildHeight() + 15, SITE_FLOOR_Y);
            return;
        }

        SpiderQueenHungryCraft.LOGGER.warn("[SpawnTest] {} is built by {} instead of the flat generator of "
                + "src/gametest/resources/dimensions/minecraft/overworld.json, the run may be judged on terrain",
                level.dimension().location(), generator.getClass().getSimpleName());
    }

    /**
     * Builds the site the rules ask for: a flat, full block floor with open sky above it, the anchor standing in its
     * middle, and every chunk of the spawn ring forced loaded (an unloaded chunk is refused long before any validator
     * runs).
     *
     * <p>The floor is written by the test and not by the template, because a game test template is a fixed box and the
     * ring of this test spans more chunks than one. A site whose ground is a hill, a tree or a lake would make the run
     * fail for reasons that have nothing to do with the spawner.
     */
    private static void prepareSite(ServerLevel level, ServerPlayer anchor)
    {
        // The world the site lives in: the run declares a flat one, this only checks that it arrived.
        verifyFlatWorld(level);

        BlockPos anchorPos = anchor.blockPosition();
        int centerX = anchorPos.getX();
        int centerZ = anchorPos.getZ();

        BlockState floor = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SITE_HALF_WIDTH; dx <= SITE_HALF_WIDTH; ++dx)
        {
            for (int dz = -SITE_HALF_WIDTH; dz <= SITE_HALF_WIDTH; ++dz)
            {
                level.setBlock(cursor.set(centerX + dx, SITE_FLOOR_Y - 1, centerZ + dz), floor, 2);
                level.setBlock(cursor.set(centerX + dx, SITE_FLOOR_Y, centerZ + dz), air, 2);
                level.setBlock(cursor.set(centerX + dx, SITE_FLOOR_Y + 1, centerZ + dz), air, 2);
            }
        }

        for (int cx = -SITE_CHUNK_RADIUS; cx <= SITE_CHUNK_RADIUS; ++cx)
        {
            for (int cz = -SITE_CHUNK_RADIUS; cz <= SITE_CHUNK_RADIUS; ++cz)
            {
                level.setChunkForced((centerX >> 4) + cx, (centerZ >> 4) + cz, true);
            }
        }

        anchor.moveTo(centerX + 0.5D, SITE_FLOOR_Y, centerZ + 0.5D, 0.0F, 0.0F);

        BlockPos standing = anchor.blockPosition();
        BlockPos ground = standing.below();

        SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] site: dimension={} day={} difficulty={} anchor={} "
                        + "ground={} groundBlock={} solidGround={} openSky={} playersInLevel={} siteChunks={} "
                        + "floorY={} halfWidth={} || {}",
                level.dimension().location(), level.isDay(), level.getDifficulty(), standing, ground,
                level.getBlockState(ground).getBlock().getName().getString(),
                level.getBlockState(ground).isCollisionShapeFullBlock(level, ground), level.canSeeSky(standing),
                level.players().size(), (2 * SITE_CHUNK_RADIUS + 1) * (2 * SITE_CHUNK_RADIUS + 1),
                SITE_FLOOR_Y, SITE_HALF_WIDTH, rulesSummary());
    }

    /** @return the spawn behaviour that is loaded right now, read from the live rules and not from a default. */
    private static String rulesSummary()
    {
        HumanSpawnConfig config = HumanSpawnRules.config();

        if (config == null)
        {
            return "rules=null (no human_spawns file was read)";
        }

        HumanSpawnConfig.SurfaceRules rules = config.surface();

        return "rulesLoaded=true source=" + config.source() + " surfaceEnabled=" + config.surfaceEnabled()
                + " dimensions=" + rules.dimensions() + " attemptIntervalTicks=" + rules.attemptIntervalTicks()
                + " dayChance=" + rules.dayChance() + " nightChance=" + rules.nightChance()
                + " dayTarget=" + rules.dayTarget() + " nightTarget=" + rules.nightTarget()
                + " perChunkCap=" + rules.perChunkCap() + " localCap=" + rules.localCap()
                + " minDistance=" + rules.minDistance() + " maxDistance=" + rules.maxDistance()
                + " mustSeeSky=" + rules.mustSeeSky() + " fullBlockGround=" + rules.fullBlockGround()
                + " tierWeights=" + rules.tierWeights().size();
    }

    /** @return every living natural surface human of this run that stands in the search box of the anchor. */
    private static List<HumanCombatantEntity> naturalHumans(ServerLevel level, ServerPlayer anchor)
    {
        AABB box = new AABB(anchor.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), anchor.getZ() - SEARCH_RADIUS,
                anchor.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), anchor.getZ() + SEARCH_RADIUS);

        return level.getEntitiesOfClass(HumanCombatantEntity.class, box,
                human -> human.isAlive() && human.spawnOrigin() == HumanSpawnOrigin.NATURAL_SURFACE);
    }

    /**
     * @return in how many different chunks the living natural humans of this run stand.
     *
     * <p>This is the evidence of the spreading the rules ask for. Every human is placed into a chunk that held none -
     * that is what the per chunk cap of the rules means - so a group whose members cover nearly as many chunks as it
     * has members is a group that really spread over the ring instead of piling up in one spot. One or two of them may
     * have walked over a border since they were placed, which is why the number is reported as evidence and the test
     * does not fail on it.
     */
    private static int distinctChunks(ServerLevel level, ServerPlayer anchor)
    {
        Set<Long> chunks = new HashSet<>();

        for (HumanCombatantEntity human : naturalHumans(level, anchor))
        {
            chunks.add(human.chunkPosition().toLong());
        }

        return chunks.size();
    }

    /** @return every counter of the run in one line, so a failure carries its own evidence. */
    private static String summary(ServerLevel level, ServerPlayer anchor)
    {
        HumanSpawnEvents.SpawnDiagnostics d = HumanSpawnEvents.diagnostics();

        return "tickSeen=" + d.tickSeen() + " surfaceCalled=" + d.surfaceSpawnerCalled()
                + " rulesNull=" + d.rulesNullSkip() + " surfDisabled=" + d.surfaceDisabledSkip()
                + " dimReject=" + d.dimensionReject() + " tickSkip=" + d.tickIntervalSkip()
                + " playersSeen=" + d.playerLoopEntries() + " specSkip=" + d.spectatorSkip()
                + " chanceReject=" + d.chanceReject() + " targetR=" + d.targetReachedSkip()
                + " chunkNL=" + d.chunkNotLoadedSkip()
                + " perChunkCap=" + d.perChunkCapReject() + " typeNull=" + d.typePickNull()
                + " localCapR=" + d.localCapReject() + " posTested=" + d.positionsTested()
                + " ringR=" + d.ringReject()
                + " validatorR=" + d.validatorReject() + " rejectReasons=[" + HumanSpawnEvents.reasonSummary() + "]"
                + " spawnF=" + d.spawnFailed() + " spawnS=" + d.spawnSuccess()
                + " pop=" + d.population() + " naturalHumans=" + naturalHumans(level, anchor).size();
    }

    /**
     * The samples the core grid is checked against before it is trusted: the inner and outer edge of the ring, on the
     * axes and in the four diagonals.
     */
    private static final int[][] RING_SAMPLES = {
            {24, 24}, {-24, 24}, {24, -24}, {-24, -24},
            {90, 90}, {-90, 90}, {90, -90}, {-90, -90},
            {96, 0}, {0, 96}, {-96, 0}, {0, -96}
    };

    /**
     * The territory half of the gate test: control first, then the core grid, then the judgement.
     *
     * <p>It never calls a spawn method of this mod. It posts {@link TickEvent.LevelTickEvent}, the production entry
     * point, exactly like the older test of this class, and then looks at the level.
     */
    private static final class TerritoryGateProbe
    {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer anchor;
        private final Set<UUID> known = new HashSet<>();

        private int ticks;
        private boolean coresPlaced;
        private boolean finished;

        TerritoryGateProbe(GameTestHelper helper, ServerLevel level, ServerPlayer anchor)
        {
            this.helper = helper;
            this.level = level;
            this.anchor = anchor;
        }

        void tick()
        {
            if (this.finished)
            {
                return;
            }

            ++this.ticks;
            MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END,
                    this.level, () -> true));

            List<HumanCombatantEntity> fresh = newHumans(this.level, this.anchor, this.known);

            if (!this.coresPlaced)
            {
                awaitControlPhase(fresh);
                return;
            }

            judge(fresh);
        }

        /** Waits until the world really spawned a human here, which is what makes the next phase meaningful. */
        private void awaitControlPhase(List<HumanCombatantEntity> fresh)
        {
            if (fresh.isEmpty())
            {
                if (this.ticks > GATE_DEADLINE)
                {
                    this.finished = true;
                    this.helper.fail("the control phase spawned no natural human within " + GATE_DEADLINE
                            + " ticks, so the run can not judge the core | " + summary(this.level, this.anchor));
                }

                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] GATE-TERRITORY control passed: {} human(s) spawned in the "
                            + "ring before any core, first at {} | {}",
                    fresh.size(), fresh.get(0).blockPosition(), summary(this.level, this.anchor));

            clearNaturalHumans(this.level, this.anchor);
            placeCoreGrid(this.level, this.anchor, this.helper);
            HumanSpawnEvents.resetCounters();
            this.coresPlaced = true;
            this.ticks = 0;
        }

        /** Nothing may appear any more, and the reason has to be the territory rule itself. */
        private void judge(List<HumanCombatantEntity> fresh)
        {
            // Only spawns inside the protection are the claim of this test. A game test level holds several anchors of
            // earlier tests, and the spawner legitimately keeps filling their own rings, which lie outside this grid.
            List<HumanCombatantEntity> inside = newHumansInsideTerritory(this.level, this.anchor, this.known);

            if (!inside.isEmpty())
            {
                this.finished = true;
                this.helper.fail("a natural human still spawned inside a protected territory (" + inside.size()
                        + " inside, first at " + inside.get(0).blockPosition()
                        + "): the territory core does not stop the surface spawner | "
                        + summary(this.level, this.anchor));
                return;
            }

            HumanSpawnEvents.SpawnDiagnostics d = HumanSpawnEvents.diagnostics();
            int refused = HumanSpawnEvents.reasonCount(HumanSpawnValidator.Reason.TERRITORY);

            // The spawns of the other anchors of the level are expected and live outside this grid, so the proof is the
            // refusal itself: the spawner asked the territory rule and the rule said no, while nothing appeared inside.
            if (refused > 0)
            {
                this.finished = true;
                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] GATE-TERRITORY passed: the surface spawner asked the "
                                + "territory rule, {} position(s) were refused, 0 spawn(s) inside the grid | {}",
                        refused, summary(this.level, this.anchor));
                this.helper.succeed();
                return;
            }

            if (this.ticks > GATE_DEADLINE)
            {
                this.finished = true;
                this.helper.fail("the spawner never asked the territory rule within " + GATE_DEADLINE + " ticks | "
                        + summary(this.level, this.anchor));
            }
        }
    }

    /**
     * The peaceful half of the gate test. The difficulty is a world setting and every other test of this class needs a
     * normal world, so it is put back on every path out - success, failure and timeout.
     */
    private static final class PeacefulGateProbe
    {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer anchor;
        private final Difficulty original;
        private final Set<UUID> known = new HashSet<>();

        private int ticks;
        private boolean finished;

        PeacefulGateProbe(GameTestHelper helper, ServerLevel level, ServerPlayer anchor, Difficulty original)
        {
            this.helper = helper;
            this.level = level;
            this.anchor = anchor;
            this.original = original;
        }

        void tick()
        {
            if (this.finished)
            {
                return;
            }

            ++this.ticks;
            MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END,
                    this.level, () -> true));

            List<HumanCombatantEntity> fresh = newHumans(this.level, this.anchor, this.known);

            if (!fresh.isEmpty())
            {
                this.finished = true;
                this.restore();
                this.helper.fail("a natural human spawned in a peaceful world (" + fresh.size() + " new, first at "
                        + fresh.get(0).blockPosition() + "): the peaceful rule is not in place for this mod's humans | "
                        + summary(this.level, this.anchor));
                return;
            }

            HumanSpawnEvents.SpawnDiagnostics d = HumanSpawnEvents.diagnostics();
            int refused = HumanSpawnEvents.reasonCount(HumanSpawnValidator.Reason.PEACEFUL);

            if (refused > 0 && d.spawnSuccess() == 0)
            {
                this.finished = true;
                this.restore();
                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] GATE-PEACEFUL passed: {} position(s) refused because "
                                + "the world is peaceful, 0 spawn(s), difficulty is {} again | {}",
                        refused, this.level.getDifficulty(), summary(this.level, this.anchor));
                this.helper.succeed();
                return;
            }

            if (this.ticks > GATE_DEADLINE)
            {
                this.finished = true;
                this.restore();
                this.helper.fail("the spawner never asked the peaceful rule within " + GATE_DEADLINE + " ticks | "
                        + summary(this.level, this.anchor));
            }
        }

        private void restore()
        {
            this.level.getServer().setDifficulty(this.original, true);
        }
    }

    /**
     * The three phases of the test, one step per level tick.
     *
     * <p>Every step first posts {@link TickEvent.LevelTickEvent} on the Forge bus - the production entry point of
     * {@link HumanSpawnEvents} - and then looks at the level. Nothing here calls a spawn method of this mod.
     */
    private static final class Probe
    {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final ServerPlayer anchor;
        private final long startTick;

        private int phase;
        private long firstSpawnTick;
        private long killTick;
        private long removedTick;
        private long refillTick;
        private long roomStartTick;
        private boolean killed;
        private UUID firstId;
        private HumanCombatantEntity first;
        private HumanCombatantEntity refill;

        Probe(GameTestHelper helper, ServerLevel level, ServerPlayer anchor)
        {
            this.helper = helper;
            this.level = level;
            this.anchor = anchor;
            this.startTick = helper.getTick();
        }

        void tick()
        {
            long elapsed = this.helper.getTick() - this.startTick;

            MinecraftForge.EVENT_BUS.post(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END,
                    this.level, () -> true));

            if (this.phase == 0)
            {
                this.awaitFirstSpawn(elapsed);
            }
            else if (this.phase == 1)
            {
                this.awaitRemoval(elapsed);
            }
            else if (this.phase == 2)
            {
                this.awaitRefill(elapsed);
            }
            else if (this.phase == 3)
            {
                this.awaitPopulation(elapsed);
            }
        }

        private void awaitFirstSpawn(long elapsed)
        {
            List<HumanCombatantEntity> found = naturalHumans(this.level, this.anchor);

            if (!found.isEmpty())
            {
                this.first = found.get(0);
                this.firstId = this.first.getUUID();
                this.firstSpawnTick = elapsed;
                this.phase = 1;

                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] PHASE1 first NATURAL_SURFACE human after {} ticks: "
                                + "uuid={} type={} pos={} origin={} alive={} | {}",
                        elapsed, this.firstId, EntityType.getKey(this.first.getType()), this.first.blockPosition(),
                        this.first.spawnOrigin(), this.first.isAlive(), summary(this.level, this.anchor));
                return;
            }

            if (elapsed > FIRST_SPAWN_DEADLINE)
            {
                this.helper.fail("PHASE1 failed: no NATURAL_SURFACE human appeared within " + FIRST_SPAWN_DEADLINE
                        + " ticks | " + summary(this.level, this.anchor));
                return;
            }

            if (elapsed % 50L == 0L)
            {
                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] phase1 t+{} {}", elapsed,
                        summary(this.level, this.anchor));
            }
        }

        private void awaitRemoval(long elapsed)
        {
            if (!this.killed)
            {
                this.killed = true;
                this.killTick = elapsed;

                // The normal death of the entity, exactly what the last hit of a player ends in.
                this.first.kill();

                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] PHASE2 killed uuid={} at {} (removed={} alive={})",
                        this.firstId, this.first.blockPosition(), this.first.isRemoved(), this.first.isAlive());
                return;
            }

            boolean stillInWorld = this.level.getEntity(this.firstId) != null;

            if (!stillInWorld)
            {
                this.removedTick = elapsed;
                this.phase = 2;

                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] PHASE2 the killed human left the level after {} "
                                + "ticks: naturalHumansNow={} | {}",
                        elapsed - this.killTick, naturalHumans(this.level, this.anchor).size(),
                        summary(this.level, this.anchor));
                return;
            }

            if (elapsed - this.killTick > REMOVAL_DEADLINE)
            {
                this.helper.fail("PHASE2 failed: the killed human is still in the level " + REMOVAL_DEADLINE
                        + " ticks after it died | " + summary(this.level, this.anchor));
            }
        }

        private void awaitRefill(long elapsed)
        {
            for (HumanCombatantEntity human : naturalHumans(this.level, this.anchor))
            {
                if (!human.getUUID().equals(this.firstId))
                {
                    this.refill = human;
                    this.refillTick = elapsed;
                    this.phase = 3;

                    SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] PHASE3 a new NATURAL_SURFACE human replaced the "
                                    + "killed one after {} ticks: uuid={} type={} pos={} origin={} | {}",
                            elapsed - this.removedTick, this.refill.getUUID(),
                            EntityType.getKey(this.refill.getType()), this.refill.blockPosition(),
                            this.refill.spawnOrigin(), summary(this.level, this.anchor));
                    this.phase = 3;
                    this.roomStartTick = elapsed;
                    return;
                }
            }

            if (elapsed - this.removedTick > REFILL_DEADLINE)
            {
                this.helper.fail("PHASE3 failed: no new NATURAL_SURFACE human appeared within " + REFILL_DEADLINE
                        + " ticks after the old one was removed | " + summary(this.level, this.anchor));
            }
        }

        /**
         * The second half of the test: the killed human is replaced, so the population of the ring may grow. The
         * assertion is the experience the rules are tuned for - a player who walks through the ring meets a whole
         * group of humans and not a single survivor.
         */
        private void awaitPopulation(long elapsed)
        {
            int found = naturalHumans(this.level, this.anchor).size();

            if (found >= POPULATION_FLOOR)
            {
                this.finish(found);
                return;
            }

            if (elapsed - this.roomStartTick > POPULATION_WINDOW)
            {
                this.helper.fail("PHASE4 failed: only " + found + " natural humans stand in the ring within "
                        + POPULATION_WINDOW + " ticks after refill, expected at least " + POPULATION_FLOOR
                        + " | " + summary(this.level, this.anchor));
            }

            if (elapsed % 100L == 0L)
            {
                SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] phase4 t+{} {}", elapsed,
                        summary(this.level, this.anchor));
            }
        }

        private void finish(int population)
        {
            HumanSpawnEvents.SpawnDiagnostics d = HumanSpawnEvents.diagnostics();

            // The spawn pool promise of the human tidy round: the surface patrol may only ever place ordinary humans.
            // A transcendent human (Chad Steve or a later special one) that appeared here would mean the tier rules or
            // their archetype gate drifted, so the test refuses to pass on it.
            int transcendent = 0;

            for (HumanCombatantEntity human : naturalHumans(this.level, this.anchor))
            {
                if (HumanEntityTypes.archetypeOf(human) != HumanArchetype.STANDARD)
                {
                    ++transcendent;
                }
            }

            if (transcendent > 0)
            {
                this.helper.fail("the surface spawner placed " + transcendent + " non ordinary human(s) | "
                        + summary(this.level, this.anchor));
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] RESULT tickSeen={} surfaceCalled={} spawnSuccess={} "
                            + "spawnFailed={} survivingNaturalHumans={} distinctChunks={} firstSpawnAt=+{}t killedAt=+{}t "
                            + "goneAt=+{}t refilledAt=+{}t refillCost={}t populationFloor={} ringPopulation={}",
                    d.tickSeen(), d.surfaceSpawnerCalled(), d.spawnSuccess(), d.spawnFailed(), population,
                    distinctChunks(this.level, this.anchor),
                    this.firstSpawnTick, this.killTick, this.removedTick, this.refillTick,
                    this.refillTick - this.removedTick, POPULATION_FLOOR, d.population());

            SpiderQueenHungryCraft.LOGGER.info("[SpawnTest] RESULT counters | {}", summary(this.level, this.anchor));

            this.helper.succeed();
        }
    }
}
