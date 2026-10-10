package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;

/**
 * A development probe that measures the two claims of round 7F <b>on the integrated server of the real game</b>: that
 * the unit is drawn with the bone dragon of Ice and Fire, and that an order which ends in the air really ends on the
 * floor.
 *
 * <p>The game tests of the round run in a flat test arena with no real player and without Ice and Fire; this probe runs
 * with the real world, the real terrain, the real client renderer and a real player, and it prints what it measured. It
 * is switched on with {@code -Dspiderqueen.soulfire.landing=true} and does nothing at all without that switch.
 *
 * <p>It reports, in this order:
 * <ol>
 * <li>which renderer the game chose for a unit, whether the Tabula skeleton of Ice and Fire is really loaded (and how
 * many parts it has), and which texture it is drawn with - the fallback of this mod is reported as a failure, never as a
 * success,</li>
 * <li>a stand-down order from fourteen blocks up: how many ticks it took to stand on the floor,</li>
 * <li>a scout move of sixty blocks: how far from its goal it touched down,</li>
 * <li>a soft rally towards the real player: whether it flew to her and then landed,</li>
 * <li>a unit saved in the middle of a descent and rebuilt: whether it comes back able to land.</li>
 * </ol>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulfireLandingProbe
{
    /** The switch of this probe. Off unless the launcher asks for it. */
    private static final boolean ENABLED = Boolean.getBoolean("spiderqueen.soulfire.landing");

    /** When the whole run starts, in ticks after the world is up. */
    private static final int START_TICK = 100;

    /** How long one order may take before the probe calls it a failure, in ticks. */
    private static final int BUDGET = 700;

    /** How far the errand of the third unit points, in blocks. */
    private static final int ERRAND_DISTANCE = 60;

    /** How high the first unit is put above the player, in blocks. */
    private static final double HOLD_HEIGHT = 14.0D;

    /** How far away the fourth unit is put before it is called home, in blocks. */
    private static final double FOLLOW_START_DISTANCE = 40.0D;

    private static int ticks;
    private static boolean started;

    /** The unit that was spawned only to be looked at: the probe reads the renderer of the client copy of it. */
    private static int modelSubjectId = -1;
    private static boolean modelReported;

    private static SoulfireDragonEntity unit;
    private static String scenario = "";
    private static int scenarioTicks;
    private static double scenarioFloorY;
    private static Vec3 scenarioGoal;
    private static boolean reported;

    private SoulfireLandingProbe()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (!ENABLED || event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.isPaused())
        {
            // The window of a probe run is not focused, so the game pauses itself; the probe takes that back, because a
            // paused world has no ticks and would measure nothing.
            if (minecraft.screen != null)
            {
                minecraft.setScreen(null);
            }

            return;
        }

        if (minecraft.getSingleplayerServer() == null || minecraft.player == null || minecraft.level == null)
        {
            return;
        }

        if (!started)
        {
            started = true;
            SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: armed, the first unit waits {} ticks",
                    START_TICK);
        }

        ticks++;

        if (ticks < START_TICK)
        {
            return;
        }

        if (ticks == START_TICK)
        {
            startHold();
            spawnModelSubject(minecraft);
        }

        // The client copy of that unit only exists after the spawn has travelled to the client, so the renderer is asked
        // on the ticks after it, and never on the same one.
        if (!modelReported && ticks > START_TICK)
        {
            if (!reportRenderer(minecraft))
            {
                if (ticks > START_TICK + 60)
                {
                    modelReported = true;
                    SpiderQueenHungryCraft.LOGGER.error(
                            "[round7F] landing probe: FAILED - the unit never reached the client, so the renderer could "
                                    + "not be asked at all");
                }
            }
            else
            {
                modelReported = true;
                discardModelSubject(minecraft);
            }
        }

        watch();
    }

    /** The unit of the renderer question: it stands still beside the player while the answer is looked up. */
    private static void spawnModelSubject(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        SoulfireDragonEntity subject = spawn(level, minecraft.player.position().add(0.0D, 0.0D, 6.0D));
        modelSubjectId = subject.getId();
    }

    /** Puts the unit of the renderer question away again. */
    private static void discardModelSubject(Minecraft minecraft)
    {
        ServerLevel level = minecraft.getSingleplayerServer().overworld();

        if (level.getEntity(modelSubjectId) instanceof SoulfireDragonEntity subject)
        {
            subject.discard();
        }
    }

    /**
     * What the game really draws a unit with: the renderer, the skeleton behind it, and the sheet of it.
     *
     * @return true once the question was answered, false while the unit is still on its way to the client
     */
    private static boolean reportRenderer(Minecraft minecraft)
    {
        Entity clientSide = minecraft.level.getEntity(modelSubjectId);

        if (!(clientSide instanceof SoulfireDragonEntity shown))
        {
            return false;
        }

        EntityRenderer<? super SoulfireDragonEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(shown);

        SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: the game draws the unit with {} - texture {}",
                renderer.getClass().getName(), renderer.getTextureLocation(shown));

        if (ModList.get().isLoaded("iceandfire") && ModList.get().isLoaded("citadel"))
        {
            if (renderer instanceof spiderqueenhungrycraft.compat.iceandfire.client.SoulfireIafDragonRenderer bone)
            {
                boolean loaded = bone.boneModel().ensureLoaded();
                int parts = 0;

                for (var ignored : bone.boneModel().getAllParts())
                {
                    parts++;
                }

                boolean poses = com.github.alexthe666.iceandfire.client.model.util.DragonAnimationsLibrary.getModel(
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonPoses.GROUND_POSE,
                        com.github.alexthe666.iceandfire.client.model.util.EnumDragonModelTypes.FIRE_DRAGON_MODEL) != null;

                SpiderQueenHungryCraft.LOGGER.info(
                        "[round7F] landing probe: I&F skeleton loaded={} parts={} pose library ready={} - a False here "
                                + "is a FAILURE of the round, not a success",
                        loaded, parts, poses);
            }
            else
            {
                SpiderQueenHungryCraft.LOGGER.error(
                        "[round7F] landing probe: FAILED - Ice and Fire is loaded but the renderer is {}",
                        renderer.getClass().getName());
            }
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7F] landing probe: Ice and Fire is not in this instance, so the bone dragon cannot be asked");
        }

        return true;
    }

    /** Scenario one: the unit is put fourteen blocks above the player and told to hold. */
    private static void startHold()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        Vec3 where = minecraft.player.position();

        unit = spawn(level, where.add(0.0D, HOLD_HEIGHT, 0.0D));
        beginScenario("a stand-down order from " + (int) HOLD_HEIGHT + " blocks up", where.y, null);

        unit.beginContract(minecraft.player.getUUID(), level.getGameTime() + 12000L);
        unit.holdAt(unit.blockPosition());

        SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: {} - the unit is at y={}", scenario,
                (int) unit.getY());
    }

    /** Scenario two: a scout move of sixty blocks, on the real terrain. */
    private static void startErrand()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        Vec3 where = minecraft.player.position();
        BlockPos target = new BlockPos((int) where.x + ERRAND_DISTANCE, (int) where.y, (int) where.z);

        unit = spawn(level, where.add(0.0D, 2.0D, 0.0D));
        beginScenario("a scout move of " + ERRAND_DISTANCE + " blocks", where.y, Vec3.atBottomCenterOf(target));

        unit.beginContract(minecraft.player.getUUID(), level.getGameTime() + 12000L);
        unit.setMoveTo(target, 31);

        SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: {} - sent from y={} to {}", scenario,
                (int) unit.getY(), target);
    }

    /** Scenario three: the soft rally of a queen who is really there, so the unit flies to her and lands beside her. */
    private static void startFollow()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        ServerPlayer queen = minecraft.getSingleplayerServer().getPlayerList().getPlayers().get(0);
        Vec3 where = queen.position();

        unit = spawn(level, where.add(FOLLOW_START_DISTANCE, 4.0D, 0.0D));
        beginScenario("a soft rally towards the queen " + (int) FOLLOW_START_DISTANCE + " blocks away", where.y, where);

        unit.beginContract(queen.getUUID(), level.getGameTime() + 12000L);
        unit.followOrder();

        SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: {} - the unit starts at distance {} and y={}",
                scenario, (int) unit.distanceTo(queen), (int) unit.getY());
    }

    /** Scenario four: a unit saved in the middle of a descent, rebuilt and told to come down. */
    private static void startReload()
    {
        Minecraft minecraft = Minecraft.getInstance();
        ServerLevel level = minecraft.getSingleplayerServer().overworld();
        Vec3 where = minecraft.player.position();

        SoulfireDragonEntity saved = spawn(level, where.add(4.0D, HOLD_HEIGHT, 4.0D));

        saved.beginContract(minecraft.player.getUUID(), level.getGameTime() + 12000L);
        saved.landTo(Vec3.atBottomCenterOf(saved.blockPosition().below(6)), saved.getCommandVersion());

        CompoundTag tag = new CompoundTag();
        saved.saveWithoutId(tag);

        boolean wasLanding = saved.isLanding();
        saved.discard();

        SoulfireDragonEntity reborn = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (reborn == null)
        {
            SpiderQueenHungryCraft.LOGGER.error("[round7F] landing probe: FAILED - the unit could not be rebuilt");

            return;
        }

        reborn.load(tag);
        reborn.setUUID(java.util.UUID.randomUUID());
        level.addFreshEntity(reborn);
        reborn.holdAt(reborn.blockPosition());

        unit = reborn;
        beginScenario("a unit saved while landing, rebuilt and sent down", where.y, null);

        SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: {} - it was landing={} when saved, and it came back "
                + "landing={} with flying={}", scenario, wasLanding, reborn.isLanding(), reborn.isFlying());
    }

    /** Starts one scenario: the bookkeeping every one of them shares. */
    private static void beginScenario(String what, double floorY, Vec3 goal)
    {
        scenario = what;
        scenarioTicks = 0;
        scenarioFloorY = floorY;
        scenarioGoal = goal;
        reported = false;
    }

    /** Watches the unit of the running scenario, and reports each one once. */
    private static void watch()
    {
        if (unit == null || reported)
        {
            return;
        }

        scenarioTicks++;

        if (hasLanded(unit))
        {
            reported = true;

            SpiderQueenHungryCraft.LOGGER.info(
                    "[round7F] landing probe: PASS - {} ended on the floor after {} ticks at y={} (started near y={}){}",
                    scenario, scenarioTicks, (int) unit.getY(), (int) scenarioFloorY,
                    scenarioGoal == null ? ""
                            : "; " + (int) unit.position().distanceTo(scenarioGoal) + " blocks from its goal");

            unit.discard();
            next();

            return;
        }

        if (scenarioTicks >= BUDGET)
        {
            reported = true;

            SpiderQueenHungryCraft.LOGGER.error("[round7F] landing probe: FAILED - {} did not end on the floor in {} "
                    + "ticks: y={} onGround={} flying={} hovering={} landing={} noGravity={} state={}",
                    scenario, scenarioTicks, (int) unit.getY(), unit.onGround(), unit.isFlying(), unit.isHovering(),
                    unit.isLanding(), unit.isNoGravity(), unit.moveState());

            unit.discard();
            next();
        }
    }

    /** Starts the scenario after the one that just ended. */
    private static void next()
    {
        String finished = scenario;

        unit = null;
        scenarioTicks = 0;

        if (finished.startsWith("a stand-down order"))
        {
            startErrand();
        }
        else if (finished.startsWith("a scout move"))
        {
            startFollow();
        }
        else if (finished.startsWith("a soft rally"))
        {
            startReload();
        }
        else
        {
            SpiderQueenHungryCraft.LOGGER.info("[round7F] landing probe: the run is over");
        }
    }

    /** The exact shape of a finished landing, the same rule the game tests ask. */
    private static boolean hasLanded(SoulfireDragonEntity dragon)
    {
        return dragon.onGround() && !dragon.isFlying() && !dragon.isHovering() && !dragon.isLanding()
                && !dragon.isNoGravity();
    }

    /** One unit of this mod, on the server, at that spot. */
    private static SoulfireDragonEntity spawn(ServerLevel level, Vec3 where)
    {
        Entity raw = HungryCraftEntities.SOULFIRE_DRAGON.get().create(level);

        if (!(raw instanceof SoulfireDragonEntity dragon))
        {
            throw new IllegalStateException("the soul fire dragon could not be built");
        }

        dragon.moveTo(where.x, where.y, where.z, 0.0F, 0.0F);
        level.addFreshEntity(dragon);

        return dragon;
    }
}
