package spiderqueenhungrycraft.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.attack.ShieldBlockFeedback;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.web.WebShotProjectile;

/**
 * The shield block <b>feedback</b> of an ordinary body (PvP round): does a successful block of a human really go through
 * the shield path of the game, and does that produce exactly one block sound.
 *
 * <p>The question of this file comes from a client report ("a human blocks and nothing is heard"), and it is answered by
 * measuring the pipeline instead of guessing: {@code LivingEntity#hurt} is the only thing in the game that blocks with a
 * shield, it fires {@link net.minecraftforge.event.entity.living.ShieldBlockEvent} when it does, and it then broadcasts
 * entity event 29, which is what the client turns into {@code SoundEvents.SHIELD_BLOCK}. {@link ShieldBlockFeedback}
 * records both, so "did the vanilla path run" and "how many sounds is that" are numbers in a test.
 *
 * <p>Three claims, and the last two are the ones a mod can get wrong:
 *
 * <ul>
 * <li>a real block of an ordinary body <b>is</b> the vanilla path: no damage, a {@code ShieldBlockEvent}, one feedback,</li>
 * <li>the five tick wind up of a shield is <b>not</b> bypassed for an ordinary body - the same hit that a shield which has
 * been up for ten ticks blocks goes through a shield that came up one tick ago. That is also the proof that the instant
 * projectile guard is the Sweatlord's alone,</li>
 * <li>and an impact the game can not block at all (a web shot, which deals no damage) is answered by this mod with
 * <b>exactly one</b> feedback: not none, and never two.</li>
 * </ul>
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class HumanShieldFeedbackGameTest
{
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 40;

    /** A corner of the world of its own, far from every other test site of this round. */
    private static final int SITE_X = 3840;
    private static final int SITE_Z = 512;

    /** How far the attacker of a scenario stands from the guard, in front of it. */
    private static final double ATTACKER_DISTANCE = 2.0D;

    /** The damage of the hit of this file: six is well above the three a shield really spends on a block. */
    private static final float HIT_DAMAGE = 6.0F;

    private HumanShieldFeedbackGameTest()
    {
    }

    @GameTest(template = "humanspawn_flat", batch = "human_shield", timeoutTicks = 120)
    public static void aHumanBlockIsTheVanillaPathWithOneFeedback(GameTestHelper helper)
    {
        run(helper, 0, Mode.BLOCK_AFTER_WINDUP);
    }

    @GameTest(template = "humanspawn_flat", batch = "human_shield", timeoutTicks = 120)
    public static void anOrdinaryBodyKeepsTheVanillaWindUp(GameTestHelper helper)
    {
        run(helper, 120, Mode.NO_WINDUP);
    }

    @GameTest(template = "humanspawn_flat", batch = "human_shield", timeoutTicks = 160)
    public static void aWebShotIsBlockedWithExactlyOneFeedback(GameTestHelper helper)
    {
        run(helper, 240, Mode.WEB_SHOT);
    }

    /** What the one scenario of this file measures. */
    private enum Mode
    {
        /** The shield has been up for ten ticks: the hit has to be blocked by the game, with one feedback. */
        BLOCK_AFTER_WINDUP,
        /** The shield comes up one tick before the hit: the vanilla wind up refuses the block and the body is hurt. */
        NO_WINDUP,
        /** A real web shot flies at a raised shield: blocked, with exactly one feedback and no web effect. */
        WEB_SHOT
    }

    private static void run(GameTestHelper helper, int offsetZ, Mode mode)
    {
        Site site = openSite(helper, 0, offsetZ);
        LivingEntity guard = spawnGuard(site, helper);
        Zombie attacker = spawnAttacker(site, helper);

        if (guard == null || attacker == null)
        {
            return;
        }

        helper.onEachTick(new ShieldProbe(helper, site, guard, attacker, mode)::tick);
    }

    /** One prepared site of this file. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /**
     * Drives one scenario and judges the pipeline: the hit of the melee modes, or the flight of the web shot.
     */
    private static final class ShieldProbe
    {
        /** The tick the hit of a melee scenario lands on: the shield has had its wind up by then (or has not). */
        private static final int HIT_TICK = 12;

        /** The tick the web shot of that scenario leaves, and how long it may take. */
        private static final int WEB_TICK = 12;
        private static final int WEB_TAIL_TICKS = 40;

        /** How far in front of the guard the web shot starts. */
        private static final double WEB_DISTANCE = 2.5D;

        /** The speed of the web shot of this mod, which flies straight instead of falling. */
        private static final double WEB_SPEED = 1.6D;

        private final GameTestHelper helper;
        private final Site site;
        private final LivingEntity guard;
        private final Zombie attacker;
        private final Mode mode;
        private final Vec3 post;

        @Nullable
        private WebShotProjectile web;

        private int ticks;
        private float healthBefore = -1.0F;
        private boolean judged;

        ShieldProbe(GameTestHelper helper, Site site, LivingEntity guard, Zombie attacker, Mode mode)
        {
            this.helper = helper;
            this.site = site;
            this.guard = guard;
            this.attacker = attacker;
            this.mode = mode;
            this.post = guard.position();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            keepPost();
            faceEachOther(this.guard, this.attacker);

            if (this.healthBefore < 0.0F)
            {
                this.healthBefore = this.guard.getHealth();
            }

            if (this.mode == Mode.WEB_SHOT)
            {
                this.tickWebShot();
                return;
            }

            keepTheShieldUp();

            if (this.mode == Mode.NO_WINDUP && this.ticks == HIT_TICK - 1)
            {
                // The shield is dropped and picked up again in one tick, so the body meets the hit with a shield that has
                // been up for a single tick - nowhere near the five the game demands.
                this.guard.stopUsingItem();
                this.guard.startUsingItem(InteractionHand.OFF_HAND);
            }

            if (this.ticks == HIT_TICK)
            {
                judgeMeleeHit(this.guard.isBlocking());
            }
        }

        /** Makes the real hit of the melee scenarios and judges what the pipeline did with it. */
        private void judgeMeleeHit(boolean blockingBefore)
        {
            this.guard.hurt(this.guard.damageSources().mobAttack(this.attacker), HIT_DAMAGE);
            this.judged = true;

            float after = this.guard.getHealth();
            int vanilla = ShieldBlockFeedback.vanillaBlocks(this.guard);
            int feedbacks = ShieldBlockFeedback.feedbacks(this.guard);

            if (this.mode == Mode.BLOCK_AFTER_WINDUP)
            {
                if (!blockingBefore)
                {
                    fail("the guard was not blocking although its shield had been up for " + HIT_TICK + " ticks, so this"
                            + " scenario can not say anything about a block");
                    return;
                }

                if (after < this.healthBefore)
                {
                    fail("a shield that was really up did not block the hit of an ordinary body: health "
                            + this.healthBefore + " -> " + after);
                    return;
                }

                if (vanilla < 1)
                {
                    fail("the hit was stopped without a ShieldBlockEvent, so the body does not use the shield path of"
                            + " the game at all - and the client is never told to play the block sound");
                    return;
                }

                if (feedbacks != 1)
                {
                    fail("one real block produced " + feedbacks + " block feedbacks instead of exactly one, so the"
                            + " shield sound of this body is either missing or doubled");
                    return;
                }

                SpiderQueenHungryCraft.LOGGER.info("[HumanShield] the vanilla block path ran and played once: health"
                        + " kept at {}, vanillaBlocks={}, feedbacks={}", this.healthBefore, vanilla, feedbacks);
                this.cleanUp();
                this.helper.succeed();
                return;
            }

            if (blockingBefore)
            {
                fail("the shield was already blocking after a single tick of use, which the wind up of the game makes"
                        + " impossible");
                return;
            }

            if (after >= this.healthBefore)
            {
                fail("a shield that had been up for one tick blocked a hit: an ordinary body of this mod is cheating the"
                        + " wind up of the game, which only the Sweatlord may do and only for projectiles");
                return;
            }

            if (vanilla != 0 || feedbacks != 0)
            {
                fail("a hit inside the wind up of a shield still produced a block event or a feedback:"
                        + " vanillaBlocks=" + vanilla + ", feedbacks=" + feedbacks);
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[HumanShield] the vanilla wind up was respected: health {} -> {},"
                    + " vanillaBlocks=0, feedbacks=0", this.healthBefore, after);
            this.cleanUp();
            this.helper.succeed();
        }

        /** Fires the real web shot of that scenario and waits for it to be answered. */
        private void tickWebShot()
        {
            if (this.ticks == WEB_TICK)
            {
                keepTheShieldUp();

                if (!this.guard.isBlocking())
                {
                    fail("the shield of the web scenario was not up before the web shot left, so the scenario can not"
                            + " measure a block at all");
                    return;
                }

                Vec3 view = this.guard.getViewVector(1.0F);
                Vec3 flat = new Vec3(view.x, 0.0D, view.z).normalize();
                Vec3 origin = this.guard.position().add(flat.scale(WEB_DISTANCE))
                        .add(0.0D, this.guard.getBbHeight() * 0.5D, 0.0D);
                WebShotProjectile shot = new WebShotProjectile(this.site.level(), this.attacker);
                shot.setPos(origin);
                shot.setDeltaMovement(flat.scale(-WEB_SPEED));
                this.site.level().addFreshEntity(shot);
                this.web = shot;
                return;
            }

            if (this.web == null)
            {
                keepTheShieldUp();
                return;
            }

            if (this.web.isRemoved())
            {
                judgeWebShot();
                return;
            }

            if (this.ticks > WEB_TICK + WEB_TAIL_TICKS)
            {
                fail("the web shot never reached the raised shield of this scenario");
            }
        }

        private void judgeWebShot()
        {
            this.judged = true;
            int feedbacks = ShieldBlockFeedback.feedbacks(this.guard);
            int vanilla = ShieldBlockFeedback.vanillaBlocks(this.guard);

            if (feedbacks != 1)
            {
                fail("a web shot that a raised shield stopped produced " + feedbacks + " block feedbacks instead of"
                        + " exactly one (0 is a silent shield, 2 is a doubled sound)");
                return;
            }

            if (vanilla != 0)
            {
                fail("the web shot was answered by the damage pipeline, which can not block an impact that deals no"
                        + " damage at all");
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[HumanShield] the web shot was blocked with exactly one feedback:"
                    + " feedbacks={}, vanillaBlocks={}", feedbacks, vanilla);
            this.cleanUp();
            this.helper.succeed();
        }

        /**
         * Takes the bodies of the scenario out of the world again.
         *
         * <p>Not for tidiness: the population census of this mod counts the ordinary humans of the world, and a human that
         * a test forgot about is a census that fails in another file.
         */
        private void cleanUp()
        {
            this.guard.discard();
            this.attacker.discard();

            if (this.web != null)
            {
                this.web.discard();
            }
        }

        private void keepTheShieldUp()
        {
            if (!this.guard.isUsingItem())
            {
                this.guard.startUsingItem(InteractionHand.OFF_HAND);
            }
        }

        private void keepPost()
        {
            if (this.guard.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.guard.moveTo(this.post.x, this.post.y, this.post.z, this.guard.getYRot(), 0.0F);
                this.guard.setDeltaMovement(Vec3.ZERO);
            }
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X + offsetX, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);
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

                for (int dy = 0; dy <= 4; ++dy)
                {
                    level.setBlock(cursor.set(centre.getX() + dx, SITE_FLOOR_Y + dy, centre.getZ() + dz),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }

        return new Site(level, centre);
    }

    /**
     * The guard of every scenario: a real human combatant of this mod, with no AI and a real shield in the offhand.
     *
     * <p>No AI on purpose: the brain of a human raises and lowers its own shield, and this file measures the shield rule
     * of the game and not the decisions of a brain.
     */
    @Nullable
    private static LivingEntity spawnGuard(Site site, GameTestHelper helper)
    {
        BlockPos at = new BlockPos(site.centre().getX(), SITE_FLOOR_Y, site.centre().getZ());
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.HUMAN_COMBATANT.get(), site.level(), at,
                MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

        if (placed == null)
        {
            helper.fail("the human of the scenario could not be placed at all");
            return null;
        }

        LivingEntity guard = placed;
        placed.setNoAi(true);
        placed.setPersistenceRequired();
        guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        guard.startUsingItem(InteractionHand.OFF_HAND);

        return guard;
    }

    /** The attacker of every scenario: a monster that stands in front of the guard and never moves. */
    @Nullable
    private static Zombie spawnAttacker(Site site, GameTestHelper helper)
    {
        Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(site.level());

        if (zombie == null)
        {
            helper.fail("the attacker of the scenario could not be created");
            return null;
        }

        // In front of the guard, which is the +Z of the test world: the shield of the game only covers the front.
        zombie.moveTo(site.centre().getX() + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + ATTACKER_DISTANCE + 0.5D,
                0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        site.level().addFreshEntity(zombie);

        return zombie;
    }

    /** Turns the guard to the attacker, once per tick, so the front rule of the shield really applies. */
    private static void faceEachOther(LivingEntity guard, LivingEntity attacker)
    {
        Vec3 toAttacker = attacker.position().subtract(guard.position());
        float yaw = (float) Math.toDegrees(Math.atan2(toAttacker.z, toAttacker.x)) - 90.0F;

        guard.setYRot(yaw);
        guard.setYBodyRot(yaw);
        guard.setYHeadRot(yaw);
    }
}
