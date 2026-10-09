package spiderqueenhungrycraft.sweatlord;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.hostility.HumanThreatMemory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The fight the Sweatlord keeps, and the one he forgets (PvP round, the "lost AI" report).
 *
 * <p><b>The report of the client.</b> The state changes are not smooth, he seems to lose his AI after finishing a
 * ranged or a melee round, and being hit brings him back.
 *
 * <p><b>The reason this file exists.</b> The alignment of the prepared humans treats an ordinary player as one of their
 * own: a normal (not queen) player is only a legal enemy <b>while the grudge memory of
 * {@link HumanThreatMemory#MEMORY_TICKS} ticks is still running</b>. That is right for a village soldier and wrong for
 * this body, which is a PvP opponent: the moment the memory runs out - four hundred ticks, which is exactly the length
 * of a few ranged rounds - the brain drops the player it is standing in front of, stops wanting the body at all and
 * stands there. The next hit puts the player back on the list, which is the "a hit brings him back" of the report.
 *
 * <p>The scenario therefore fights a <b>normal player</b> (no queen flag) who stands in front of him and stops
 * attacking, and it demands that the fight he is in survives the grudge memory: he keeps the enemy while he can still
 * see him and he goes on fighting.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordEngagementGameTest
{
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 40;

    /** A corner of the world of its own, far from every other test site of this round. */
    private static final int SITE_X = 4352;
    private static final int SITE_Z = 512;

    /** Where the player enemy stands: inside the ranged band of the body, so the fight is a fight. */
    private static final double PLAYER_DISTANCE = 12.0D;

    /** The tick the player hits him, which is what puts a normal player on his list at all. */
    private static final int PROVOKE_TICK = 20;

    /** How long after that hit the scenario judges: past the grudge memory, still in the same fight. */
    private static final int JUDGE_TICK = HumanThreatMemory.MEMORY_TICKS + 120;

    private SweatlordEngagementGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_engagement", timeoutTicks = 700)
    public static void aFightWithANormalPlayerOutlivesTheGrudgeMemory(GameTestHelper helper)
    {
        Site site = openSite(helper, 0);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (sweatlord == null)
        {
            return;
        }

        ServerPlayer player = placePlayer(site, helper);

        if (player == null)
        {
            return;
        }

        helper.onEachTick(new EngagementProbe(helper, sweatlord, player)::tick);
    }

    /** One prepared site of this file. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

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

                for (int dy = 0; dy <= 5; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return new Site(level, centre);
    }

    @Nullable
    private static SweatlordSteveEntity spawnSweatlord(Site site, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(),
                new BlockPos(site.centre().getX(), SITE_FLOOR_Y, site.centre().getZ()), MobSpawnType.COMMAND,
                HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    /**
     * The enemy of this file: a <b>normal player</b>, without the queen flag, who stands in the ranged band and does
     * nothing but hit the body once.
     */
    @Nullable
    private static ServerPlayer placePlayer(Site site, GameTestHelper helper)
    {
        ServerPlayer player;

        try
        {
            player = net.minecraftforge.common.util.FakePlayerFactory.get(site.level(),
                    new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "sweatlord_duelist"));
        }
        catch (RuntimeException error)
        {
            helper.fail("the player of the scenario could not be created: " + error);
            return null;
        }

        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(site.centre().getX() + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + PLAYER_DISTANCE + 0.5D,
                180.0F, 0.0F);

        if (!site.level().players().contains(player))
        {
            site.level().addNewPlayer(player);
        }

        // The scenario is about the ordinary player: a queen is a legal enemy of every human by alignment, and this file
        // would measure nothing at all with one.
        QueenDataCapability.setQueen(player, false);

        return player;
    }

    /**
     * Watches one fight against a normal player: whether the body takes him as its enemy at all (the grudge of the hit),
     * whether it keeps him, and whether it goes on doing something of its own.
     */
    private static final class EngagementProbe
    {
        /** How many ticks of the fight with the player are needed before the premise counts as proven. */
        private static final int PREMISE_TICKS = 30;

        /** How long the body may do nothing of its own before the fight counts as lost. */
        private static final int IDLE_TOLERANCE_TICKS = 120;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity mob;
        private final ServerPlayer player;
        private final Vec3 post;

        private int ticks;
        private int targetTicks;
        private int skillTicks;
        private int lastSkillTick = -1;
        private int lastTargetTick = -1;
        private boolean provoked;
        private boolean judged;

        EngagementProbe(GameTestHelper helper, SweatlordSteveEntity mob, ServerPlayer player)
        {
            this.helper = helper;
            this.mob = mob;
            this.player = player;
            this.post = mob.position();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            clearThePad();
            holdPost();

            if (this.ticks == PROVOKE_TICK)
            {
                // One real hit from the player: that is the only reason a normal player is on the list of a human at all,
                // and it is what the client report says "brings him back".
                this.provoked = this.mob.hurt(this.player.damageSources().playerAttack(this.player), 1.0F);
            }

            // The measurement starts after the hit: before it the body has no reason to look at a normal player at all.
            if (this.ticks > PROVOKE_TICK + 5 && this.mob.getTarget() == this.player)
            {
                ++this.targetTicks;
                this.lastTargetTick = this.ticks;
            }

            String skill = this.mob.debugSkill();

            if (!"none".equals(skill) && !InstantProjectileGuard.NAME.equals(skill))
            {
                ++this.skillTicks;
                this.lastSkillTick = this.ticks;
            }

            if (this.ticks >= JUDGE_TICK)
            {
                judge();
            }
        }

        /**
         * Removes the monsters that wander into the scenario.
         *
         * <p>Not tidiness: the test site of this file stands in the open, and the first run of this file was decided by a
         * skeleton that spawned next to it and took the fight over - the body simply targeted the nearer monster and never
         * looked at the player at all.
         */
        private void clearThePad()
        {
            for (LivingEntity stray : this.mob.level().getEntitiesOfClass(LivingEntity.class,
                    this.mob.getBoundingBox().inflate(40.0D)))
            {
                if (stray != this.mob && stray != this.player && CombatContext.isValidEnemy(this.mob, stray))
                {
                    stray.discard();
                }
            }
        }

        /** Keeps the body at its post, so the fight is measured and not the walking. */
        private void holdPost()
        {
            if (this.mob.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        private void judge()
        {
            this.judged = true;

            if (!this.provoked)
            {
                fail("the player could not hurt the body at all, so the scenario never started a fight: "
                        + this.mob.getDebugState());
                return;
            }

            if (this.targetTicks < PREMISE_TICKS)
            {
                fail("the body never took the player that attacked it as its enemy, so this scenario is not measuring an"
                        + " ongoing fight at all: targetTicks=" + this.targetTicks + " | " + this.mob.getDebugState());
                return;
            }

            if (this.mob.getTarget() != this.player)
            {
                fail("the body forgot the player it was fighting: the target was lost " + (this.ticks - this.lastTargetTick)
                        + " ticks ago although that player is still standing in front of it, on flat ground, in line of"
                        + " sight - a normal player is only a legal enemy while the grudge memory of "
                        + HumanThreatMemory.MEMORY_TICKS + " ticks is running, so a PvP fight ends by itself in the"
                        + " middle of the field | " + this.mob.getDebugState());
                return;
            }

            int sinceSkill = this.ticks - this.lastSkillTick;

            if (this.lastSkillTick < 0 || sinceSkill > IDLE_TOLERANCE_TICKS)
            {
                fail("the body kept the enemy but did nothing of its own for " + sinceSkill + " ticks, so the fight is"
                        + " not running any more | " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[Engagement] the fight with a normal player outlived the grudge memory:"
                            + " targetTicks={}, skillTicks={}, idleFor={} ticks | {}", this.targetTicks, this.skillTicks,
                    sinceSkill, this.mob.getDebugState());
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }
}
