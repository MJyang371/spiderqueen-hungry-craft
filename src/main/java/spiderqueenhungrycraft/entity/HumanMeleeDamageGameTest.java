package spiderqueenhungrycraft.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.sweatlord.PlayerLikeActions;

/**
 * The runtime proof that the melee attacks of the humanoid bodies of this mod really remove health (fix round).
 *
 * <p>The report this file is written against was "every humanoid plays the attack animation and deals no damage at
 * all". That sentence has two halves, and they have to be separated before anything can be fixed:
 *
 * <ol>
 * <li><b>the damage path</b> - does one swing of that body take health off a victim that can really be damaged
 * ({@link #meleeSwingsOfEveryHumanoidBodyReallyRemoveHealth} asks every body of the mod directly),</li>
 * <li><b>the fight</b> - does the brain of that body really land that swing while it fights
 * ({@link #theOrdinaryHumanReallyRemovesHealthInARealFight} watches a real fight without touching it).</li>
 * </ol>
 *
 * <p>What this file can not answer, and says so instead of pretending: a <b>player</b> victim can not be used here. A
 * game test has no client, so the only player body it can place is {@code FakePlayer}, and that body answers
 * {@code isInvulnerableTo} with true for every damage source - which is exactly the trap this file guards against with
 * its opening check. The damage path itself is the same vanilla pipeline for every {@link LivingEntity}, so a monster
 * and a human victim are the honest substitute; the remaining candidates for "no damage against a real player" are
 * named in the report of this round: creative or spectator mode, the sixty tick spawn protection of a player, and a
 * victim who holds a shield up.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanMeleeDamageGameTest
{
    /** The site numbers of this round, at its own corner of the test world. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 16;
    private static final int SITE_X = 1536;
    private static final int SITE_Z = 512;

    /** How far a body stands from its victim: inside the melee reach of every body of this mod. */
    private static final double SWING_DISTANCE = 2.2D;

    private HumanMeleeDamageGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "human_melee", timeoutTicks = 300)
    public static void meleeSwingsOfEveryHumanoidBodyReallyRemoveHealth(GameTestHelper helper)
    {
        Site site = openSite(helper);

        HumanCombatantEntity ordinary = spawn(site, HungryCraftEntities.HUMAN_COMBATANT.get(), 0.0D, helper);
        ChadSteveEntity chad = spawn(site, HungryCraftEntities.CHAD_STEVE.get(), 5.0D, helper);
        SweatlordSteveEntity sweatlord = spawn(site, HungryCraftEntities.SWEATLORD_STEVE.get(), 10.0D, helper);
        InfestedSteveEntity brood = spawn(site, HungryCraftEntities.INFESTED_STEVE.get(), 15.0D, helper);

        if (ordinary == null || chad == null || sweatlord == null || brood == null)
        {
            return;
        }

        Zombie monster1 = spawnPassiveZombie(site, SWING_DISTANCE, 0.0D, helper);
        Zombie monster2 = spawnPassiveZombie(site, 5.0D + SWING_DISTANCE, 0.0D, helper);
        Zombie monster3 = spawnPassiveZombie(site, 10.0D + SWING_DISTANCE, 0.0D, helper);
        HumanCombatantEntity humanVictim = spawn(site, HungryCraftEntities.LOW_PREPARED_HUMAN.get(),
                15.0D + SWING_DISTANCE, helper);
        HumanCombatantEntity controlVictim = spawn(site, HungryCraftEntities.HIGH_PREPARED_HUMAN.get(),
                0.0D, SWING_DISTANCE, helper);

        if (monster1 == null || monster2 == null || monster3 == null || humanVictim == null || controlVictim == null)
        {
            return;
        }

        // Every body of this file is switched off on purpose: the question here is the damage path of one swing, not
        // the fight, and a body whose own AI attacked first would blur which swing removed which health. The direct
        // call is the very method the brain of that body calls, so nothing about the production path is skipped.
        List<Mob> bodies = List.of(ordinary, chad, sweatlord, brood);

        for (Mob body : bodies)
        {
            body.setNoAi(true);
        }

        // The human victims are switched off as well: a prepared human with a shield of his own would block the very
        // swing this file wants to measure, which is exactly what the first run of the control swing reported.
        humanVictim.setNoAi(true);
        controlVictim.setNoAi(true);

        List<LivingEntity> victims = List.of(monster1, monster2, monster3, humanVictim, controlVictim);
        List<Swing> swings = new ArrayList<>();

        swings.add(new Swing("the ordinary human on a monster", ordinary, monster1,
                () -> ordinary.performMeleeAttack(monster1)));
        swings.add(new Swing("Chad Steve's hammer on a monster", chad, monster2, chad::performHammerDamage));
        swings.add(new Swing("the Sweatlord on a monster", sweatlord, monster3,
                () -> PlayerLikeActions.meleeAttack(sweatlord, monster3)));
        swings.add(new Swing("the brood body on a human", brood, humanVictim,
                () -> brood.performMeleeAttack(humanVictim)));
        // A second swing of the ordinary human on a second victim: one lucky number must never carry the whole file.
        swings.add(new Swing("the control swing of the ordinary human", ordinary, controlVictim,
                () -> ordinary.performMeleeAttack(controlVictim)));

        helper.onEachTick(new SwingProbe(helper, bodies, victims, swings)::tick);
    }

    /** One measurement of this file: the two bodies of the scenario and the one swing between them. */
    private record Swing(String label, LivingEntity body, LivingEntity victim, Runnable swing)
    {
    }

    /**
     * Waits until the bodies of the scenario are really part of the loaded world, then measures one direct swing per
     * pair and names every number.
     *
     * <p>The wait is the lesson of the first run of this file: an entity that was added in the same tick is not part
     * of the entity index yet, and a level query - which is exactly what the hammer of Chad Steve uses to find its
     * victims - silently returns nothing while that is true. That is a loading condition of the harness and not a
     * damage bug, so the probe waits for the condition with a deadline instead of measuring too early.
     */
    private static final class SwingProbe
    {
        /** Long enough for the entity index of the level to catch up with what this test just placed. */
        private static final int DEADLINE = 200;

        private final GameTestHelper helper;
        private final List<Mob> bodies;
        private final List<LivingEntity> victims;
        private final List<Swing> swings;
        private final List<Vec3> posts = new ArrayList<>();

        private int ticks;
        private boolean judged;

        SwingProbe(GameTestHelper helper, List<Mob> bodies, List<LivingEntity> victims, List<Swing> swings)
        {
            this.helper = helper;
            this.bodies = bodies;
            this.victims = victims;
            this.swings = swings;

            for (Mob body : bodies)
            {
                this.posts.add(body.position());
            }

            for (LivingEntity victim : victims)
            {
                this.posts.add(victim.position());
            }
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            holdEverything();

            for (LivingEntity victim : this.victims)
            {
                // A monster of this world burns in daylight, and a victim that dies of the sun ends a file about melee
                // damage for a reason that has nothing to do with a swing: the bodies then stopped being queryable at
                // all, which is exactly what this test reported twice in a row. Only the flame is closed here - the wound
                // is not, because the wound is the measurement of this file.
                victim.setRemainingFireTicks(0);
            }

            if (!queryable())
            {
                if (this.ticks > DEADLINE)
                {
                    fail("the bodies of this test never became queryable by the level, so nothing could be measured");
                }

                return;
            }

            judge();
        }

        /** Keeps every body where it was placed, so a swing of this file is always inside the reach it needs. */
        private void holdEverything()
        {
            for (int i = 0; i < this.bodies.size(); ++i)
            {
                hold(this.bodies.get(i), this.posts.get(i));
            }

            for (int i = 0; i < this.victims.size(); ++i)
            {
                hold(this.victims.get(i), this.posts.get(this.bodies.size() + i));
            }
        }

        private void hold(Entity entity, Vec3 post)
        {
            if (entity.position().distanceToSqr(post) > 1.0E-4D)
            {
                entity.moveTo(post.x, post.y, post.z, entity.getYRot(), entity.getXRot());
                entity.setDeltaMovement(Vec3.ZERO);
            }
        }

        /** @return true once every victim of this file can be found by a level query around itself. */
        private boolean queryable()
        {
            for (LivingEntity victim : this.victims)
            {
                if (victim.level().getEntitiesOfClass(victim.getClass(), victim.getBoundingBox().inflate(2.0D)).isEmpty())
                {
                    return false;
                }
            }

            return true;
        }

        private void judge()
        {
            this.judged = true;
            StringBuilder report = new StringBuilder();

            for (Swing swing : this.swings)
            {
                if (!assertDamageable(this.helper, swing.label() + " (the victim)", swing.body(), swing.victim()))
                {
                    return;
                }

                float drop = dropAfterSwing(swing.victim(), swing.swing());

                report.append("\n  ").append(swing.label()).append(": ").append(drop).append(" health (hand ")
                        .append(swing.body().getMainHandItem()).append(", victim now ")
                        .append(swing.victim().getHealth()).append("/").append(swing.victim().getMaxHealth())
                        .append(")");

                if (drop <= 0.0F)
                {
                    fail("a melee swing of a humanoid of this mod removed no health from a victim that can really be"
                            + " damaged: " + swing.label() + " removed " + drop + " | victim=" + swing.victim()
                            + ", victimHealth=" + swing.victim().getHealth() + ", hand="
                            + swing.body().getMainHandItem() + ", victimOffhand=" + swing.victim().getOffhandItem()
                            + ", victimUsingItem=" + swing.victim().isUsingItem()
                            + ", victimBlocking=" + swing.victim().isBlocking()
                            + ", distance=" + swing.body().distanceTo(swing.victim()) + diagnostic(swing));
                    return;
                }
            }

            SpiderQueenHungryCraft.LOGGER.info("[MeleeTest] every humanoid really removes health:{}", report);
            this.helper.succeed();
        }

        /** @return the extra state a refused swing needs: the hammer of Chad Steve filters by rule and by area. */
        private String diagnostic(Swing swing)
        {
            if (swing.body() instanceof ChadSteveEntity chad)
            {
                return ", attackableTarget=" + chad.isAttackableTarget(swing.victim())
                        + ", insideHammerArea=" + chad.isTargetInsideHammerArea(swing.victim())
                        + ", dx=" + (swing.victim().getX() - chad.getX())
                        + ", dz=" + (swing.victim().getZ() - chad.getZ())
                        + ", yaw=" + chad.getYRot();
            }

            return "";
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /**
     * The second half of the report: not the damage method, but the real fight. The production brain of the ordinary
     * human drives one swing on a monster that stands inside its reach, and the test only watches the health of that
     * monster. A body that swings and deals nothing fails here with its whole state in the message.
     */
    @GameTest(template = "humanspawn_flat", batch = "human_melee", timeoutTicks = 260)
    public static void theOrdinaryHumanReallyRemovesHealthInARealFight(GameTestHelper helper)
    {
        Site site = openSite(helper);
        HumanCombatantEntity human = spawn(site, HungryCraftEntities.HUMAN_COMBATANT.get(), 0.0D, helper);
        Zombie victim = spawnPassiveZombie(site, SWING_DISTANCE, 0.0D, helper);

        if (human == null || victim == null)
        {
            return;
        }

        if (!assertDamageable(helper, "the victim of the fight", human, victim))
        {
            return;
        }

        helper.onEachTick(new FightProbe(helper, human, victim)::tick);
    }

    /**
     * Watches one real melee fight, tick by tick, and only reads: the brain of the human decides everything itself.
     * The target is held on the victim on purpose - a human whose target is cleared walks away, and this test is about
     * the swing of the brain, not about its targeting.
     */
    private static final class FightProbe
    {
        /** Generous on purpose: a swing lands six ticks after it starts and the brain swings once per second. */
        private static final int DEADLINE = 200;

        private final GameTestHelper helper;
        private final HumanCombatantEntity human;
        private final Zombie victim;
        private final float healthBefore;

        private int ticks;
        private boolean judged;

        FightProbe(GameTestHelper helper, HumanCombatantEntity human, Zombie victim)
        {
            this.helper = helper;
            this.human = human;
            this.victim = victim;
            this.healthBefore = victim.getHealth();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;

            if (this.human.getTarget() != this.victim)
            {
                this.human.setTarget(this.victim);
            }

            float lost = this.healthBefore - this.victim.getHealth();

            if (lost > 0.0F)
            {
                this.judged = true;
                SpiderQueenHungryCraft.LOGGER.info("[MeleeTest] the fight of the ordinary human removed {} health"
                                + " after {} ticks: hand={}, brain={}, victim={}/{}",
                        lost, this.ticks, this.human.getMainHandItem(), this.human.brain().state().id(),
                        this.victim.getHealth(), this.victim.getMaxHealth());
                this.helper.succeed();
                return;
            }

            if (this.ticks > DEADLINE)
            {
                this.judged = true;
                this.helper.fail("the ordinary human fought for " + DEADLINE + " ticks and removed no health from a"
                        + " victim that can really be damaged: brain=" + this.human.brain().state().id()
                        + ", hand=" + this.human.getMainHandItem() + ", target=" + this.human.getTarget()
                        + ", distance=" + this.human.distanceTo(this.victim)
                        + ", lineOfSight=" + this.human.hasLineOfSight(this.victim)
                        + ", victimInvulnerableTime=" + this.victim.invulnerableTime
                        + ", victimHealth=" + this.victim.getHealth() + ", humanUsingItem="
                        + this.human.isUsingItem() + ", humanState=" + this.human.brain().state().id());
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // helpers of this file
    // -------------------------------------------------------------------------------------------------------------

    /** The yaw a body of this file holds: every victim stands on its +x side, so a body faces its victim. */
    private static final float FACING_THE_VICTIM = -90.0F;

    /** The site of this file: the level of the test and the point every body is placed around. */
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

                for (int dy = 0; dy <= 5; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        // A spectator keeps the site ticking without making the surface spawner populate it with other humans.
        ServerPlayer anchor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "melee_anchor"));
        anchor.setGameMode(GameType.SPECTATOR);
        anchor.moveTo(centre.getX() + 0.5D, SITE_FLOOR_Y, centre.getZ() + 0.5D, 0.0F, 0.0F);

        if (!level.players().contains(anchor))
        {
            level.addNewPlayer(anchor);
        }

        return new Site(level, centre);
    }

    @Nullable
    private static <T extends Mob> T spawn(Site site, EntityType<T> type, double offsetX, GameTestHelper helper)
    {
        return spawn(site, type, offsetX, 0.0D, helper);
    }

    /**
     * Places one body of this mod at that offset, through the very factory the game uses, and turns it into its victim:
     * a body that faces away from its victim could never hit it, and a test that measures nothing would then blame the
     * damage of the mod.
     */
    @Nullable
    private static <T extends Mob> T spawn(Site site, EntityType<T> type, double offsetX, double offsetZ,
            GameTestHelper helper)
    {
        BlockPos at = new BlockPos(site.centre().getX() + (int) Math.round(offsetX), SITE_FLOOR_Y,
                site.centre().getZ() + (int) Math.round(offsetZ));
        Mob placed = HumanSpawnFactory.spawn(type, site.level(), at, MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (placed == null || !type.getBaseClass().isInstance(placed))
        {
            helper.fail("the body of this test could not be placed: " + type + " -> " + placed);
            return null;
        }

        // The type was checked above, and the cast is the only way: EntityType#getBaseClass is typed against Entity and
        // not against its own parameter in this version of the game.
        @SuppressWarnings("unchecked")
        T body = (T) placed;
        body.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                FACING_THE_VICTIM, 0.0F);

        return body;
    }

    /** A victim that stands still and never fights back: no AI and persistent, so it can not leave the test. */
    @Nullable
    private static Zombie spawnPassiveZombie(Site site, double offsetX, double offsetZ, GameTestHelper helper)
    {
        Zombie zombie = EntityType.ZOMBIE.create(site.level());

        if (zombie == null)
        {
            helper.fail("the victim of this test could not be created");
            return null;
        }

        zombie.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                -FACING_THE_VICTIM, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        site.level().addFreshEntity(zombie);

        return zombie;
    }

    /**
     * @return true when that victim can really be damaged at all, the control every measurement of this file needs. A
     *         body that answers {@code isInvulnerableTo} with true - the player body of a game test does - would make
     *         every swing of this file look like a broken damage path.
     */
    private static boolean assertDamageable(GameTestHelper helper, String label, LivingEntity attacker,
            LivingEntity victim)
    {
        if (victim.isInvulnerableTo(victim.damageSources().mobAttack(attacker)))
        {
            helper.fail(label + " is invulnerable to the attack of the body under test, so nothing can be measured"
                    + " here: " + victim);
            return false;
        }

        if (!victim.isAlive() || victim.isDeadOrDying() || victim.getHealth() <= 0.0F)
        {
            helper.fail(label + " is not a living victim: health=" + victim.getHealth() + ", alive=" + victim.isAlive());
            return false;
        }

        return true;
    }

    /** @return the health that victim lost to that one swing, as a number a failure message can print. */
    private static float dropAfterSwing(LivingEntity victim, Runnable swing)
    {
        float before = victim.getHealth();
        swing.run();

        return before - victim.getHealth();
    }
}
