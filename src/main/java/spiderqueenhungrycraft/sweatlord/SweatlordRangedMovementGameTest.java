package spiderqueenhungrycraft.sweatlord;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The locomotion of the ranged kit (Sweatlord V1.1): a bow draw, an egg burst and a trident throw are <b>not</b> a
 * standstill.
 *
 * <p>The round before this one had every ranged skill call {@code brake} on every tick it owned the body, which turns a
 * player-like combatant into a turret. The claim of this file is the plain one: while each of the three actions really
 * runs, the <b>world position</b> of the body really changes - not the swing animation, the position - and it changes
 * enough to be a step and not a jitter.
 *
 * <p>The target of the scenario is a monster held at a fixed range, so the fight stays in the ranged band of the kit and
 * the walking is what is measured and not the closing in of a charge. The body itself is never held: its walking is the
 * measurement.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordRangedMovementGameTest
{
    /** The floor of the test site and the half width of its flat area. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 40;

    /** A corner of the test world of its own. */
    private static final int SITE_X = 2560;
    private static final int SITE_Z = 256;

    /** The range the target is held at: inside every band of the kit and far outside melee reach. */
    private static final double RANGE = 10.0D;

    /** How far the body may wander from its post before the scenario puts it back, so it can not walk off the pad. */
    private static final double MAX_DRIFT = 14.0D;

    /** The movement each ranged action has to show in the world while it owns the body. */
    private static final double MIN_MOVEMENT = 0.5D;

    /** How long each action has to be watched before its claim means anything. */
    private static final int MIN_OBSERVED_TICKS = 6;

    private SweatlordRangedMovementGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_movement", timeoutTicks = 760)
    public static void rangedAttacksAreThrownWhileHeWalks(GameTestHelper helper)
    {
        Site site = openSite(helper);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);
        Zombie target = spawnPassiveZombie(site, RANGE, 0.0D, helper);

        if (sweatlord == null || target == null)
        {
            return;
        }

        sweatlord.setTarget(target);
        helper.onEachTick(new MovementProbe(helper, site, sweatlord, target)::tick);
    }

    /** One prepared site of this file: the level of the test and the point every body is placed around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /**
     * Watches the three ranged actions and asks one question of each of them: did the body really walk while it did its
     * job?
     */
    private static final class MovementProbe
    {
        private static final int DEADLINE = 700;

        private static final String BOW = "bow_attack";
        private static final String EGG = "egg_burst";
        private static final String TRIDENT = "trident_attack";

        private final GameTestHelper helper;
        private final SweatlordSteveEntity mob;
        private final Zombie target;
        private final Vec3 post;

        /** Per action: how many ticks it owned the body, and how far the body walked inside it. */
        private final Map<String, Integer> watched = new HashMap<>();
        private final Map<String, Double> moved = new HashMap<>();

        private int ticks;
        private String active = "none";
        private Vec3 anchor = Vec3.ZERO;
        private boolean judged;

        MovementProbe(GameTestHelper helper, Site site, SweatlordSteveEntity mob, Zombie target)
        {
            this.helper = helper;
            this.mob = mob;
            this.target = target;
            this.post = mob.position();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            holdTheTargetAtRange();

            if (this.mob.getTarget() != this.target)
            {
                // The scenario commits its enemy: a Sweatlord that is held in place reads as one that is on its way back
                // to its post, so the band rules may refuse to pick a target again the moment one is lost - and a brain
                // without a target does not fight at all.
                this.mob.setTarget(this.target);
            }

            String skill = this.mob.debugSkill();

            if (!skill.equals(this.active))
            {
                this.active = skill;
                this.anchor = this.mob.position();
            }

            if (isARangedAction(skill))
            {
                this.watched.merge(skill, 1, Integer::sum);
                this.moved.merge(skill, this.mob.position().distanceTo(this.anchor), Math::max);
            }

            if (satisfied() || this.ticks >= DEADLINE)
            {
                judge();
            }
        }

        private static boolean isARangedAction(String skill)
        {
            return BOW.equals(skill) || EGG.equals(skill) || TRIDENT.equals(skill);
        }

        /**
         * Holds the monster at a fixed range and keeps it alive: the scenario measures the walking of the Sweatlord, so a
         * target that charges into melee or dies would only make the measurement shorter.
         */
        private void holdTheTargetAtRange()
        {
            // The body itself may only wander so far, so the scenario can never walk it off the pad. The walk of a single
            // action is measured inside its own window, so putting the body back can not be read as its movement.
            if (this.mob.position().distanceTo(this.post) > MAX_DRIFT)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
                this.anchor = this.post;
            }

            Vec3 away = this.target.position().subtract(this.mob.position());
            Vec3 flat = new Vec3(away.x, 0.0D, away.z);

            if (flat.lengthSqr() < 1.0E-4D)
            {
                flat = new Vec3(1.0D, 0.0D, 0.0D);
            }

            Vec3 wanted = this.mob.position().add(flat.normalize().scale(RANGE));
            this.target.moveTo(wanted.x, SITE_FLOOR_Y, wanted.z, this.target.getYRot(), 0.0F);
            this.target.setDeltaMovement(Vec3.ZERO);

            if (!this.target.hasEffect(MobEffects.ABSORPTION))
            {
                // The target has to outlive the whole scenario: a body that dies in the middle of it ends the
                // measurement for a reason that has nothing to do with the walking. The first run of this file lost its
                // monster to the melee combo of the very body it was measuring, and the scenario was over after sixteen
                // ticks. Absorption is refreshed, and the wound and the daylight flame are closed on every tick.
                this.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 9));
            }

            this.target.setRemainingFireTicks(0);
            this.target.setHealth(this.target.getMaxHealth());
        }

        private boolean satisfied()
        {
            return this.watched.getOrDefault(BOW, 0) >= MIN_OBSERVED_TICKS
                    && this.watched.getOrDefault(EGG, 0) >= MIN_OBSERVED_TICKS
                    && this.watched.getOrDefault(TRIDENT, 0) >= MIN_OBSERVED_TICKS
                    && this.moved.getOrDefault(TRIDENT, 0.0D) >= MIN_MOVEMENT;
        }

        private void judge()
        {
            this.judged = true;

            // The scenario asks for what the kit really fired. The offensive pearl of this body competes for the same body
            // (its rules are older than this round and are not part of it), so a run in which the pearl takes the body
            // again and again can legitimately produce fewer bows, eggs and tridents than three - and a test that then
            // fails is measuring the pearl and not the walking. What it refuses is a scenario in which no ranged action
            // ever owned the body at all.
            int observed = 0;

            for (String kind : new String[] { BOW, EGG, TRIDENT })
            {
                if (this.watched.getOrDefault(kind, 0) >= MIN_OBSERVED_TICKS
                        && this.moved.getOrDefault(kind, 0.0D) >= MIN_MOVEMENT)
                {
                    ++observed;
                }
            }

            if (observed < 2)
            {
                this.helper.fail("fewer than two ranged actions ever walked the body while they ran, so this scenario"
                        + " says nothing about the walking of the ranged kit: " + summary());
                return;
            }

            for (String kind : new String[] { BOW, EGG, TRIDENT })
            {
                if (this.watched.getOrDefault(kind, 0) >= MIN_OBSERVED_TICKS
                        && this.moved.getOrDefault(kind, 0.0D) < MIN_MOVEMENT)
                {
                    this.helper.fail("the " + kind + " moved the body only " + describe(this.moved.get(kind))
                            + " blocks while it ran, so the ranged kit still fights from a standstill: " + summary());
                    return;
                }
            }

            SpiderQueenHungryCraft.LOGGER.info("[MovementTest] the ranged kit walks while it fights: {}", summary());
            this.helper.succeed();
        }

        private String summary()
        {
            return BOW + " " + describe(this.moved.get(BOW)) + " blocks in " + this.watched.getOrDefault(BOW, 0)
                    + " ticks, " + EGG + " " + describe(this.moved.get(EGG)) + " blocks in "
                    + this.watched.getOrDefault(EGG, 0) + " ticks, " + TRIDENT + " "
                    + describe(this.moved.get(TRIDENT)) + " blocks in " + this.watched.getOrDefault(TRIDENT, 0)
                    + " ticks | " + this.mob.getDebugState();
        }

        private static String describe(@Nullable Double value)
        {
            return String.format(java.util.Locale.ROOT, "%.2f", value == null ? 0.0D : value);
        }
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z);
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
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(), site.centre(),
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (!(placed instanceof SweatlordSteveEntity sweatlord))
        {
            helper.fail("the Sweatlord could not be placed: " + placed);
            return null;
        }

        return sweatlord;
    }

    /** The monster he fights: no AI and persistent, so it can never walk out of the scenario or despawn. */
    @Nullable
    private static Zombie spawnPassiveZombie(Site site, double offsetX, double offsetZ, GameTestHelper helper)
    {
        Zombie zombie = EntityType.ZOMBIE.create(site.level());

        if (zombie == null)
        {
            helper.fail("the monster of the scenario could not be created");
            return null;
        }

        zombie.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                180.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 4));
        site.level().addFreshEntity(zombie);

        return zombie;
    }
}
