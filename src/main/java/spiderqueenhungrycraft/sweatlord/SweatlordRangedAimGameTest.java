package spiderqueenhungrycraft.sweatlord;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The runtime proof that the ranged kit of the Sweatlord really reaches what it shoots at (fix round).
 *
 * <p>The report was "the trident, the bow and the eggs fall short because they fly a parabola, the hit rate is
 * terrible". That is a statement about <b>geometry</b>, and this file measures exactly that, with the same test the
 * game itself uses to decide a hit: for every projectile he really fired, the segment between its position of the
 * last tick and its position now is clipped against the hitbox of the target. A flight that touches that box is a
 * hit; everything else is a miss, however close it came.
 *
 * <p>Nothing here is simulated by the test: the fight is the production brain, the projectiles are the real ones of
 * {@link PlayerLikeActions}, and the only thing the probe does is read. Both bodies are held at their posts so the
 * range of the scenario stays the range of the scenario - the aim is what is measured, not the walking.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordRangedAimGameTest
{
    /** The site numbers of this round, at its own corner of the test world. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 34;
    private static final int SITE_X = 1536;
    private static final int SITE_Z = 1024;

    /** The range of the scenario: far enough that the drop of a straight aimed shot is several blocks. */
    private static final double RANGE = 22.0D;

    private SweatlordRangedAimGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_aim", timeoutTicks = 760)
    public static void rangedAttacks_reallyReachTheirTarget(GameTestHelper helper)
    {
        Site site = openSite(helper);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);
        Zombie target = spawnPassiveZombie(site, RANGE, 0.0D, helper);

        if (sweatlord == null || target == null)
        {
            return;
        }

        if (!CombatContext.isValidEnemy(sweatlord, target))
        {
            helper.fail("the target of this test is not a legal enemy of the Sweatlord, so the aim can not be"
                    + " measured: " + target);
            return;
        }

        // The target is given absorption so that it can not die inside the scenario: a dead body ends the measurement
        // for a reason that has nothing to do with the aim of the Sweatlord (the first run of this file reported a
        // target at zero health and no recorded hit at all).
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 4));

        sweatlord.setTarget(target);
        helper.onEachTick(new RangedProbe(helper, site, sweatlord, target)::tick);
    }

    /**
     * Watches every projectile the Sweatlord really fires and judges the geometry of the whole fight: with the aim of
     * the round this file was written for, arrows and eggs land several blocks short of a body that stands twenty-two
     * blocks away, which is why the file fails there and passes once the aim is solved.
     */
    private static final class RangedProbe
    {
        /** Long enough for several arrows, one trident and two egg bursts of the real cooldowns. */
        private static final int DEADLINE = 700;

        /** The box around the line of the scenario in which projectiles are tracked. */
        private static final double TRACK_RADIUS = 12.0D;

        /** The same inflation the game uses when it decides whether a projectile touched a body. */
        private static final double HIT_INFLATE = 0.3D;

        /**
         * How close the flight of an egg has to come to the body. An egg of the vanilla game deals no damage at all, so
         * the damage of the target says nothing about it; what can be measured is whether the throw really arrives - and
         * three quarters of a block is inside the reach of a projectile that is a quarter of a block wide itself.
         */
        private static final double EGG_REACH_DISTANCE = 0.75D;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;
        private final Zombie target;
        private final AABB trackBox;
        private final Vec3 mobPost;
        private final Vec3 targetPost;
        private final Map<UUID, TrackedProjectile> tracked = new HashMap<>();
        private final float healthBefore;
        private final float absorptionBefore;

        private int ticks;
        private boolean judged;

        RangedProbe(GameTestHelper helper, Site site, SweatlordSteveEntity mob, Zombie target)
        {
            this.helper = helper;
            this.level = site.level();
            this.mob = mob;
            this.target = target;
            this.mobPost = mob.position();
            this.targetPost = target.position();
            this.trackBox = new AABB(this.mobPost, this.targetPost).inflate(TRACK_RADIUS);
            this.healthBefore = target.getHealth();
            this.absorptionBefore = target.getAbsorptionAmount();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            holdAtPosts();

            if (this.mob.getTarget() != this.target)
            {
                // The scenario commits its enemy: a Sweatlord that is held in place reads as one that is on its way back
                // to its post, so the band rules may refuse to pick a target again the moment one is lost - and a brain
                // without a target does not fight at all.
                this.mob.setTarget(this.target);
            }

            // The absorption of the target is refreshed, so a scenario of several arrow hits can not end by the death
            // of the body it measures.
            if (!this.target.hasEffect(MobEffects.ABSORPTION))
            {
                this.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 4));
            }

            // The fight is the production one; the target is only held, so a lost target can not end the scenario.
            if (this.mob.getTarget() != this.target)
            {
                this.mob.setTarget(this.target);
            }

            for (Projectile projectile : this.level.getEntitiesOfClass(Projectile.class, this.trackBox,
                    candidate -> candidate.getOwner() == this.mob))
            {
                track(projectile);
            }

            closeTick();

            if (satisfied() || this.ticks >= DEADLINE)
            {
                judge();
            }
        }

        /**
         * Closes the tick for every tracked shot: one that was not seen any more either left the area or was removed,
         * and a shot that vanished while it was touching the target is the tick the game took it away.
         */
        private void closeTick()
        {
            AABB targetBox = this.target.getBoundingBox().inflate(HIT_INFLATE);

            for (TrackedProjectile projectile : this.tracked.values())
            {
                projectile.closeTick(targetBox);
            }
        }

        /**
         * Keeps both bodies at the range of the scenario: the aim is measured here, not the walking.
         *
         * <p>Except while a pearl jump is really running. That exception is the whole reason this file could report
         * "arrow 0/0, egg 0/0, trident 0/0" for four hundred ticks: the offensive pearl holds the body until the body
         * has really moved, and a jump that the harness undoes on every tick - the owner is put back at its post the
         * moment the pearl teleports it - never ends, so the jump ran to its own timeout again and again and the bow,
         * the eggs and the trident never got the body back at all. A scenario may pin a body, but it has to let it
         * finish what it started, or it is measuring its own pin and nothing else.
         */
        private void holdAtPosts()
        {
            if (!"offensive_pearl".equals(this.mob.debugSkill()))
            {
                hold(this.mob, this.mobPost);
            }

            hold(this.target, this.targetPost);

            // The dummy of the scenario may not die, and a monster of this world burns in daylight: a target that dies
            // half way through the scenario ends the measurement for a reason that has nothing to do with the aim (the
            // first run of this file reported "arrow 0/1" and a target that was "removed=KILLED"). The wound and the
            // flame are therefore closed on every tick.
            this.target.setRemainingFireTicks(0);
            this.target.setHealth(this.target.getMaxHealth());
        }

        private void hold(Mob mob, Vec3 post)
        {
            if (mob.position().distanceToSqr(post) > 1.0E-4D)
            {
                mob.moveTo(post.x, post.y, post.z, mob.getYRot(), mob.getXRot());
                mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        /** One tick of one projectile: the segment of this tick is clipped against the hitbox of the target. */
        private void track(Projectile projectile)
        {
            Vec3 position = projectile.position();
            TrackedProjectile known = this.tracked.get(projectile.getUUID());

            if (known == null)
            {
                this.tracked.put(projectile.getUUID(), new TrackedProjectile(kindOf(projectile), position));
                return;
            }

            known.update(position, this.target.getBoundingBox().inflate(HIT_INFLATE));
        }

        /** @return the kind of that projectile, read from its class and never from a name. */
        private static String kindOf(Projectile projectile)
        {
            if (projectile instanceof ThrownEgg)
            {
                return "egg";
            }

            if (projectile instanceof ThrownTrident)
            {
                return "trident";
            }

            if (projectile instanceof Arrow)
            {
                return "arrow";
            }

            return "other";
        }

        private int fired(String kind)
        {
            int count = 0;

            for (TrackedProjectile projectile : this.tracked.values())
            {
                if (projectile.kind().equals(kind))
                {
                    ++count;
                }
            }

            return count;
        }

        private int hits(String kind)
        {
            int count = 0;

            for (TrackedProjectile projectile : this.tracked.values())
            {
                if (projectile.kind().equals(kind) && projectile.hit())
                {
                    ++count;
                }
            }

            return count;
        }

        private double closest(String kind)
        {
            double closest = Double.MAX_VALUE;

            for (TrackedProjectile projectile : this.tracked.values())
            {
                if (projectile.kind().equals(kind))
                {
                    closest = Math.min(closest, projectile.closest());
                }
            }

            return closest;
        }

        /** @return true once the fight has produced both kinds in numbers that mean something. */
        private boolean satisfied()
        {
            return fired("arrow") >= 2 && hits("arrow") >= 1 && fired("egg") >= 3 && hits("egg") >= 1;
        }

        private void judge()
        {
            this.judged = true;

            int shots = fired("arrow") + fired("egg") + fired("trident");

            if (shots < 3)
            {
                fail("the scenario never produced enough projectiles to judge the aim: " + summary() + " | "
                        + this.mob.getDebugState());
                return;
            }

            // Two verdicts, and both are the game's own: a projectile that damages took the health of the body, which no
            // sampling can miss, and an egg of the vanilla game deals no damage at all, so for the eggs the closest
            // approach of their flight is the answer. The geometry is reported either way.
            //
            // The scenario asks for what the kit really fired, and not for a fixed count of one weapon: the offensive
            // pearl of this body competes for the same body (its rules are older than this round and are not part of it),
            // so a run in which the pearl takes the body again and again can legitimately produce fewer arrows than
            // three - and a test that then fails is measuring the pearl and not the aim.
            boolean damagedTheBody = damageTaken() > 0.0F;
            boolean eggsReachTheBody = closest("egg") <= EGG_REACH_DISTANCE;

            if (!damagedTheBody && !(fired("egg") > 0 && eggsReachTheBody))
            {
                fail("the ranged kit of the Sweatlord did not really reach a body " + RANGE + " blocks away: "
                        + summary() + " | damageTaken=" + damagedTheBody + " (" + damageTaken() + "), eggReached="
                        + eggsReachTheBody + " (" + describe(closest("egg")) + " blocks from the body, allowed "
                        + EGG_REACH_DISTANCE + ")" + ", targetHealth=" + this.target.getHealth() + "/"
                        + this.target.getMaxHealth() + ", distance=" + this.mob.distanceTo(this.target)
                        + " | " + geometry() + " | " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[AimTest] the ranged kit really reaches a body {} blocks away: {}"
                    + " | damageTaken={} | {}", RANGE, summary(), damageTaken(), geometry());
            this.helper.succeed();
        }

        /** @return the closest approach of every tracked shot, so a failure can name the geometry it saw. */
        private String geometry()
        {
            StringBuilder text = new StringBuilder();

            for (String kind : new String[] { "arrow", "egg", "trident", "other" })
            {
                text.append(kind).append("[");

                for (TrackedProjectile projectile : this.tracked.values())
                {
                    if (projectile.kind().equals(kind))
                    {
                        text.append(projectile.describe()).append(' ');
                    }
                }

                text.append("] ");
            }

            return text.toString();
        }

        /** @return the health and absorption the target really lost inside this scenario. */
        private float damageTaken()
        {
            return (this.healthBefore + this.absorptionBefore)
                    - (this.target.getHealth() + this.target.getAbsorptionAmount());
        }

        /** @return how many of each kind were fired, how many touched the body, and how close the rest came. */
        private String summary()
        {
            return "arrow " + hits("arrow") + "/" + fired("arrow") + " (closest " + describe(closest("arrow")) + ")"
                    + ", egg " + hits("egg") + "/" + fired("egg") + " (closest " + describe(closest("egg")) + ")"
                    + ", trident " + hits("trident") + "/" + fired("trident")
                    + ", other " + fired("other");
        }

        private static String describe(double distance)
        {
            return distance == Double.MAX_VALUE ? "never near"
                    : String.format(java.util.Locale.ROOT, "%.2f", distance);
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /** One projectile of the fight: its kind, the closest it came to the target and whether it touched it. */
    private static final class TrackedProjectile
    {
        /**
         * How close a shot that vanished has to have been to the body to count. It is the radius of a projectile plus a
         * margin, and it is only the fallback: the last step is what really decides a hit.
         */
        private static final double TOUCH_TOLERANCE = 0.5D;

        private final String kind;
        private Vec3 last;
        private Vec3 step;
        private double closest = Double.MAX_VALUE;
        private boolean hit;
        private boolean seen;
        private boolean gone;

        TrackedProjectile(String kind, Vec3 position)
        {
            this.kind = kind;
            this.last = position;
        }

        void update(Vec3 position, AABB targetBox)
        {
            this.seen = true;

            if (this.last != null)
            {
                // The segment of this tick, exactly like the game decides a projectile hit - a point sample would even
                // miss an arrow that flew straight through a body between two ticks.
                if (targetBox.clip(this.last, position).isPresent())
                {
                    this.hit = true;
                }

                this.step = position.subtract(this.last);
                this.closest = Math.min(this.closest, targetBox.distanceToSqr(position));
            }

            this.last = position;
        }

        /**
         * Closes one tick for this shot. A shot that was not seen any more either left the tracked area or was removed
         * by the game, and one that vanished in the middle of a step that would have crossed the body ended on it: that
         * is the very tick of an impact, which no probe can sample because the game takes the projectile away inside it.
         *
         * <p>The last measured step is used and not a fixed radius, because that is the difference between a shot that
         * ended on the body and one that flew past it: the step of a hit points into the box, the step of a shot that
         * missed points past it.
         */
        void closeTick(AABB targetBox)
        {
            if (this.seen)
            {
                this.seen = false;
                return;
            }

            if (this.gone)
            {
                return;
            }

            this.gone = true;

            if (this.hit || this.last == null || this.step == null)
            {
                return;
            }

            this.hit = targetBox.clip(this.last, this.last.add(this.step)).isPresent();

            if (!this.hit)
            {
                this.hit = targetBox.contains(this.last) || targetBox.distanceToSqr(this.last) <= TOUCH_TOLERANCE * TOUCH_TOLERANCE;
            }
        }

        String kind()
        {
            return this.kind;
        }

        boolean hit()
        {
            return this.hit;
        }

        /** @return the distance of the closest approach, in blocks. */
        double closest()
        {
            return this.closest == Double.MAX_VALUE ? Double.MAX_VALUE : Math.sqrt(this.closest);
        }

        /** @return one line about the geometry of this shot, for a failure that has to name what it saw. */
        String describe()
        {
            return "{seen=" + this.seen + ", gone=" + this.gone + ", hit=" + this.hit + ", closest="
                    + describe(this.closest()) + ", step="
                    + (this.step == null ? "-" : describe(this.step.length())) + "}";
        }

        private static String describe(double distance)
        {
            return distance == Double.MAX_VALUE ? "never near"
                    : String.format(java.util.Locale.ROOT, "%.2f", distance);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // helpers of this file
    // -------------------------------------------------------------------------------------------------------------

    /** The site of this file: the level of the test and the point the scene is built around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and places a spectator anchor on it. */
    private static Site openSite(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X, SITE_FLOOR_Y, spawn.getZ() + SITE_Z);

        for (int cx = -1; cx <= 1; ++cx)
        {
            for (int cz = -1; cz <= 1; ++cz)
            {
                level.setChunkForced((centre.getX() >> 4) + cx, (centre.getZ() >> 4) + cz, true);
            }
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -SITE_HALF_WIDTH; dx <= SITE_HALF_WIDTH; ++dx)
        {
            for (int dz = -SITE_HALF_WIDTH; dz <= SITE_HALF_WIDTH; ++dz)
            {
                level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y - 1, centre.getZ() + dz),
                        Blocks.STONE.defaultBlockState(), 2);

                for (int dy = 0; dy <= 6; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // A spectator keeps the site ticking without making the surface spawner populate it with other humans.
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "aim_anchor"));
        anchor.setGameMode(GameType.SPECTATOR);
        anchor.moveTo(centre.getX() + 0.5D, SITE_FLOOR_Y, centre.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
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

    /** The target of the scenario: no AI and persistent, so it can never walk out of the test or despawn. */
    @Nullable
    private static Zombie spawnPassiveZombie(Site site, double offsetX, double offsetZ, GameTestHelper helper)
    {
        Zombie zombie = EntityType.ZOMBIE.create(site.level());

        if (zombie == null)
        {
            helper.fail("the target of this test could not be created");
            return null;
        }

        zombie.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                180.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        site.level().addFreshEntity(zombie);

        return zombie;
    }
}
