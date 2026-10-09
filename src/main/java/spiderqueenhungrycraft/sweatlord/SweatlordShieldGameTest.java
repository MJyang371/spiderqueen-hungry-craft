package spiderqueenhungrycraft.sweatlord;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.attack.ShieldBreak;
import spiderqueenhungrycraft.attack.ShieldBlockFeedback;
import spiderqueenhungrycraft.attack.ShieldProjectileInteraction;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;
import spiderqueenhungrycraft.human.spawn.HumanSpawnFactory;
import spiderqueenhungrycraft.human.spawn.HumanSpawnOrigin;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.sweatlord.VirtualLoadout;
import spiderqueenhungrycraft.sweatlord.InstantProjectileGuard;
import spiderqueenhungrycraft.web.WebShotProjectile;

/**
 * The runtime proof of the shield reaction of the Sweatlord (Sweatlord fix round).
 *
 * <p>Every test of this file watches <b>one real fight</b> - a real body, a real bow, real arrows, the production
 * {@link SweatlordCombatBrain} - and asks the question a player asks: does he stop the shot and keep his health?
 *
 * <p><b>The two numbers that make this a fight and not a wish.</b> The vanilla game only lets a shield block once it
 * has been in use for five ticks ({@code LivingEntity#isBlocking} asks {@code useDuration - useItemRemaining >= 5}),
 * and an arrow of this mod leaves the bow with a speed of three blocks per tick. A reaction that only starts once the
 * arrow is already in the air therefore has at most eight ticks of warning, and the shot a player really takes - the
 * one fired from close range - gives four ticks or fewer. The interesting requirement is therefore not "he blocks when
 * he is lucky" but "he has the shield up <b>before the arrow leaves the bow</b>".
 *
 * <p>{@link #shieldReaction_isRaisedWhileTheShooterIsStillAiming} is the decisive test of that requirement, and it
 * fails on the round this file was written for. The other two tests are the guard rails around it: the long shot and
 * the volley are the cases the old reaction could sometimes win.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SweatlordShieldGameTest
{
    /** The floor of the test sites and the half width of their flat area, the same numbers the other sites use. */
    private static final int SITE_FLOOR_Y = 70;
    private static final int SITE_HALF_WIDTH = 44;

    /** Clear of every other site of this test world. */
    private static final int SITE_X = 1024;
    private static final int SITE_Z = 512;

    /**
     * The four corners of the pad, one per test of this file, and the shooter of a long shot stands across from his own
     * corner.
     *
     * <p>Every test of a game test run shares one world, and the bodies of an earlier test are still standing there when
     * the next one starts. Thirty blocks apart is more than the aim radius of {@link ProjectileThreatDetector} and it is
     * the same order as its scan radius, so the guard of one scenario can not be raised by the shooter of another one -
     * which is exactly what happened while all four tests used the very same spot.
     */
    private static final int CORNER_A = -20;
    private static final int CORNER_B = 20;

    /**
     * The dedicated pads of the V1.1 scenarios, two hundred blocks apart and far outside the reach of everything else.
     *
     * <p>The first version of these tests shared the middle of this file's own pad, and every one of them paid for it.
     * {@link ProjectileThreatDetector#SCAN_RADIUS} is forty blocks and the aim of a shooter who is drawn at this body is
     * read within thirty-two, so a scenario thirty blocks away raised the guard of its neighbour - and a scenario that
     * sat exactly on the site of another test file made that file's own population census find four bodies where it
     * expected one. Two hundred blocks is not "a bit more room": it is more than a monster can walk in the ticks of
     * these tests, and five times the longest reach any of these scenarios reads.
     */
    private static final int PAD_AIMING = -200;
    private static final int PAD_WINDUP = -400;
    private static final int PAD_HOLD = -600;
    private static final int PAD_FRONT = -800;
    private static final int PAD_AXE = -1000;

    /**
     * A long shot: the distance an arrow needs to give the reaction more than the five tick shield wind up.
     *
     * <p>Thirty-six blocks is about twelve ticks of flight for an arrow of this mod, which is the comfortable side of
     * the seven tick budget of a shield reaction (see {@link ProjectileThreatDetector#SCAN_RADIUS}). The shooter stands
     * on the far side of the same pad, so the scenario needs no second site.
     */
    private static final double SHOOTER_OFFSET = 36.0D;

    /** The monster the Sweatlord fights: far enough that the brain is really in its ranged fight. */
    private static final int ENEMY_DISTANCE = 14;

    /** How long the queen of the aiming test really holds a drawn bow before she releases. */
    private static final int AIM_TICKS = 24;

    private SweatlordShieldGameTest()
    {
    }

    // -------------------------------------------------------------------------------------------------------------
    // 1) the shot that is already in the air
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 200)
    public static void shieldReaction_stopsAnArrowOnACollisionCourse(GameTestHelper helper)
    {
        Site site = openSite(helper, CORNER_A, CORNER_A);
        ServerPlayer shooter = placePlayer(site, GameType.SPECTATOR, SHOOTER_OFFSET, 0.0D, "sweatlord_shooter");
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (shooter == null || sweatlord == null)
        {
            return;
        }

        // The monster is put to the side on purpose: the shots of this test fly along x, and a body in that line
        // would eat the arrow before it ever reaches the Sweatlord.
        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);

        // The shot leaves on tick 30, so the fight goal of the body is really running before the arrow exists - and it
        // leaves from the eye of the shooter, which is where the game really spawns the arrow of a real body.
        ShieldProbe probe = new ShieldProbe(helper, site.level(), sweatlord, shooter,
                List.of(new Shot(30, eyeOf(shooter), PlayerLikeActions.ARROW_SPEED)));
        helper.onEachTick(probe::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 2) the shot that has not been fired yet - the decisive requirement of this round
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The bug of this round, asked in one test: a queen who <b>draws a bow and never lets go</b> may not control the
     * body. A held bow is not a projectile, so the instant guard of the Sweatlord has to stay down for the whole aim -
     * and the body has to go on with its own fight instead of standing there behind a shield nobody can see an arrow of.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 200)
    public static void shieldReaction_isRaisedWhileTheShooterIsStillAiming(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_AIMING);
        // A real shooter and no spectator: only a body that can really shoot may be expected to provoke the guard.
        ServerPlayer queen = placePlayer(site, GameType.SURVIVAL, 6.0D, 0.0D, "sweatlord_queen");

        if (queen == null)
        {
            return;
        }

        QueenDataCapability.setQueen(queen, true);
        queen.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));

        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);

        ShieldProbe probe = new ShieldProbe(helper, site.level(), sweatlord, queen,
                List.of(new Shot(AIM_TICKS + 1, eyeOf(queen), PlayerLikeActions.ARROW_SPEED)));
        probe.aimWithTheBow(AIM_TICKS, queen);
        helper.onEachTick(probe::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 3) the volley: the guard has to survive the whole salvo, not only its first arrow
    // -------------------------------------------------------------------------------------------------------------

    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 240)
    public static void shieldReaction_holdsThroughAVolley(GameTestHelper helper)
    {
        Site site = openSite(helper, CORNER_A, CORNER_B);
        ServerPlayer shooter = placePlayer(site, GameType.SPECTATOR, SHOOTER_OFFSET, 0.0D, "sweatlord_volley");
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (shooter == null || sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);

        // Three shots four ticks apart: a guard that drops the moment the first one is gone eats the other two.
        Vec3 from = eyeOf(shooter);
        ShieldProbe probe = new ShieldProbe(helper, site.level(), sweatlord, shooter, List.of(
                new Shot(30, from, PlayerLikeActions.ARROW_SPEED),
                new Shot(34, from, PlayerLikeActions.ARROW_SPEED),
                new Shot(38, from, PlayerLikeActions.ARROW_SPEED)));
        helper.onEachTick(probe::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 4) the wind up of a real shield: a shot that arrives too early is NOT blocked
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The instant half of the shield rule of this body: a shot from four blocks arrives in about two ticks, which no
     * vanilla wind up can win - and the Sweatlord still stops it, because his instant projectile guard may block from
     * the tick his shield comes up.
     *
     * <p>The test demands the whole chain and not just "no damage": the shot really was on a collision course and came
     * from the front (so the geometry of the game allows a block), the shield was <b>not</b> up when the arrow left (so
     * the vanilla wind up can not explain anything), the body really raised it, the hit was really blocked, and it was
     * blocked by the <b>shield path of the game</b> - a {@code ShieldBlockEvent} happened, which is also what plays the
     * vanilla block sound. A body of this mod that cancelled the damage by hand would fail this test.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 120)
    public static void shieldReaction_aShotThatArrivesBeforeTheWindUpIsNotBlocked(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_WINDUP);
        ServerPlayer shooter = placePlayer(site, GameType.SPECTATOR, 4.0D, 0.0D, "sweatlord_toolate");
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (shooter == null || sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);

        helper.onEachTick(new TooLateProbe(helper, site.level(), sweatlord, shooter)::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 5) the hold window: long enough to be seen, and never permanent
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The visibility requirement of the round, measured: after the last threat the guard stays up for at least
     * {@link InstantProjectileGuard#MIN_HOLD_TICKS} ticks (a flicker is not a shield raise a player can read), and
     * it really comes down again - a body parked behind a shield for ever would be the other failure of this file.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 240)
    public static void shieldReaction_holdsVisiblyThenReleases(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_HOLD);
        ServerPlayer shooter = placePlayer(site, GameType.SPECTATOR, SHOOTER_OFFSET, 0.0D, "sweatlord_hold");
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (shooter == null || sweatlord == null)
        {
            return;
        }

        Zombie enemy = spawnPassiveZombie(site, 0.0D, ENEMY_DISTANCE, helper);
        sweatlord.setTarget(enemy);

        ShieldProbe probe = new ShieldProbe(helper, site.level(), sweatlord, shooter,
                List.of(new Shot(30, eyeOf(shooter), PlayerLikeActions.ARROW_SPEED)));
        probe.expectingAVisibleHoldWindow();
        helper.onEachTick(probe::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 6) the front rule of the game, and the web projectile that must keep using it
    // -------------------------------------------------------------------------------------------------------------

    /**
     * A shield covers the <b>front</b> and nothing else, and this is the rule every projectile of this mod asks
     * ({@code ShieldProjectileInteraction}). Two claims, both about the real rule and not about a copy of it:
     *
     * <ol>
     * <li>with the shield really up, a shot from the front is blocked and a shot from behind is not - the shield is not
     * a bubble,</li>
     * <li>and the web shot of this mod (which deals no damage at all and applies its effect by hand) goes through the
     * very same rule, so a raised shield stops a web and a web from behind still lands.</li>
     * </ol>
     *
     * <p>The guard interrupt is deliberately not involved here: the arrows of this test are not threats, so nothing
     * turns the body and the answer is purely the geometry of the game.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 160)
    public static void shieldReaction_theFrontRuleGovernsArrowsAndWebShots(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_FRONT);
        ServerPlayer shooter = placePlayer(site, GameType.SPECTATOR, 6.0D, 0.0D, "sweatlord_front");
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);

        if (shooter == null || sweatlord == null)
        {
            return;
        }

        // A held guard, put up by the test the way a player holds the right mouse button: the rule is what is measured
        // here, and a rule can be asked of a body without a threat existing.
        sweatlord.setItemSlot(EquipmentSlot.OFFHAND, VirtualLoadout.stackOf(Items.SHIELD));

        if (!sweatlord.isUsingItem())
        {
            sweatlord.startUsingItem(InteractionHand.OFF_HAND);
        }

        helper.onEachTick(new FrontRuleProbe(helper, site.level(), sweatlord, shooter)::tick);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 7) the read: the axe against a raised shield, and the sword again afterwards
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The other half of the round: a target that really holds a shield up inside his reach has to be answered with the
     * axe, that swing has to really smash the shield, and the sword has to be back in the hand afterwards.
     *
     * <p>The victim is a monster with a shield and no AI, held at its post every tick: that is a player who keeps the
     * right mouse button down and stands still, which is exactly the situation the rule is written for. A shield that
     * is not held is not a shield, so the test would prove nothing without that.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 200)
    public static void axeShieldBreak_switchesToTheAxeAndBreaksTheShield(GameTestHelper helper)
    {
        Site site = openSite(helper, CORNER_B, CORNER_B);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);
        Zombie blocker = spawnShieldBlocker(site, 2.5D, helper);

        if (sweatlord == null || blocker == null)
        {
            return;
        }

        sweatlord.setTarget(blocker);
        helper.onEachTick(new AxeProbe(helper, sweatlord, blocker)::tick);
    }

    /**
     * The guard rail of the golden axe: no shield, no axe. A target that never raises a shield may only ever see the
     * diamond sword - the switch is the read of a guard and never his default hand.
     */
    @GameTest(template = "humanspawn_flat", batch = "sweatlord_shield", timeoutTicks = 200)
    public static void axeShieldBreak_isNeverDrawnWithoutAShield(GameTestHelper helper)
    {
        Site site = openSite(helper, 0, PAD_AXE);
        SweatlordSteveEntity sweatlord = spawnSweatlord(site, helper);
        Zombie blocker = spawnPassiveZombie(site, 2.5D, 0.0D, helper);

        if (sweatlord == null || blocker == null)
        {
            return;
        }

        // A body that can take the whole scenario of sword hits: the question here is which weapon is drawn, not how
        // fast a zombie dies.
        if (blocker.getAttribute(Attributes.MAX_HEALTH) != null)
        {
            blocker.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500.0D);
            blocker.setHealth(500.0F);
        }

        sweatlord.setTarget(blocker);
        helper.onEachTick(new AxeProbe(helper, sweatlord, blocker, true)::tick);
    }

    /**
     * Holds the blocking victim at its post and watches the hands of the Sweatlord: axe seen, shield smashed, sword
     * back. Every promise is a flag, so a failure names the one that never came true instead of a timeout.
     *
     * <p><b>Two phases, because that is how the gap was found.</b> In the first phase the victim does not block and
     * the Sweatlord is already swinging his sword - his melee skill of the moment holds the body. Only then does the
     * victim raise the shield, which is the real situation of a player who blocks in the middle of a fight: the axe
     * has to win the body from a running skill, and a rule that only ever picks the axe while nothing is running can
     * never answer it.
     */
    private static final class AxeProbe
    {
        /** Long enough for the sword to be the running skill, and for the shield to be up afterwards. */
        private static final int DEADLINE = 160;

        /** How long the victim fights without a shield first, so the sword really owns the body. */
        private static final int UNGUARDED_TICKS = 40;

        /** Long enough for a body that never raises a shield to prove that the axe is never chosen either. */
        private static final int UNGUARDED_ONLY_TICKS = 140;

        private final GameTestHelper helper;
        private final SweatlordSteveEntity mob;
        private final Zombie blocker;

        /** True for the scenario of "no shield anywhere: the golden axe must never appear". */
        private final boolean withoutAShield;

        /** The post the victim is held at: a player who blocks stands still, so the dummy does too. */
        private final Vec3 post;

        private int ticks;
        private boolean swordHeld;
        private boolean shieldWasUp;
        private boolean axeSeen;

        /** True once the axe was in his hand while the victim really held its shield up: the read, and not the hand. */
        private boolean armedWhileBlocking;
        private boolean smashed;
        private boolean swordBack;

        AxeProbe(GameTestHelper helper, SweatlordSteveEntity mob, Zombie blocker)
        {
            this(helper, mob, blocker, false);
        }

        AxeProbe(GameTestHelper helper, SweatlordSteveEntity mob, Zombie blocker, boolean withoutAShield)
        {
            this.helper = helper;
            this.mob = mob;
            this.blocker = blocker;
            this.post = blocker.position();
            this.withoutAShield = withoutAShield;
        }

        void tick()
        {
            ++this.ticks;

            this.swordHeld |= "sword_melee".equals(this.mob.debugSkill());
            clearThePad(this.mob, this.blocker);

            if (this.mob.getTarget() != this.blocker)
            {
                // The blocker of this scenario stays the enemy of this scenario: it is a monster like any other, and a
                // body that is held at its post may read as one that is on its way back to it and stop picking a target.
                this.mob.setTarget(this.blocker);
            }

            boolean guarded = !this.withoutAShield && this.ticks > UNGUARDED_TICKS;
            holdTheVictimAtItsPost(guarded);

            boolean holdingAShieldUp = guarded && ShieldProjectileInteraction.holdsUpAShield(this.blocker);

            if (holdingAShieldUp)
            {
                this.shieldWasUp = true;
            }

            // The weapon of the read: golden, and nothing else. {@link VirtualLoadout#SHIELD_BREAKER} is the single
            // source of truth of that item, so this assertion can not drift away from the code it tests.
            boolean axe = this.mob.getMainHandItem().is(VirtualLoadout.SHIELD_BREAKER);
            this.axeSeen |= axe;

            // The evidence that the smash really came from a swing at a real guard: the axe was in his hand while the
            // victim really held its shield up in his reach.
            this.armedWhileBlocking |= axe && holdingAShieldUp;

            if (this.armedWhileBlocking && ShieldBreak.isDisabled(this.blocker))
            {
                this.smashed = true;
            }

            if (this.axeSeen && !axe && this.mob.getMainHandItem().is(Items.DIAMOND_SWORD))
            {
                this.swordBack = true;
            }

            if (this.withoutAShield)
            {
                judgeWithoutAShield();
                return;
            }

            if (this.swordHeld && this.shieldWasUp && this.axeSeen && this.smashed && this.swordBack)
            {
                SpiderQueenHungryCraft.LOGGER.info("[ShieldTest] axe shield break passed after {} ticks: {}",
                        this.ticks, this.mob.getDebugState());
                this.helper.succeed();
                return;
            }

            if (this.ticks > DEADLINE)
            {
                this.helper.fail("the axe never answered the raised shield within " + DEADLINE + " ticks: "
                        + "swordHeldTheBody=" + this.swordHeld + ", shieldEverUp=" + this.shieldWasUp
                        + ", axeSeen=" + this.axeSeen + ", armedWhileBlocking=" + this.armedWhileBlocking
                        + ", shieldSmashed=" + this.smashed
                        + ", swordBack=" + this.swordBack + ", main=" + this.mob.getMainHandItem()
                        + ", skill=" + this.mob.debugSkill() + ", distance=" + this.mob.distanceTo(this.blocker));
            }
        }

        /**
         * The other half of the visual rule: a target that <b>never</b> raises a shield is never answered with the
         * golden axe. The switch exists for the read of a guard and for nothing else, so a body that never guards may
         * only ever see the diamond sword.
         */
        private void judgeWithoutAShield()
        {
            if (this.axeSeen)
            {
                this.helper.fail("the golden axe was drawn although the target never raised a shield, so the switch is"
                        + " not the read of a guard any more: main=" + this.mob.getMainHandItem()
                        + ", skill=" + this.mob.debugSkill() + ", ticks=" + this.ticks);
                return;
            }

            if (this.ticks >= UNGUARDED_ONLY_TICKS && this.swordHeld)
            {
                SpiderQueenHungryCraft.LOGGER.info("[ShieldTest] no shield, no axe: the sword kept the body for {}"
                        + " ticks | {}", this.ticks, this.mob.getDebugState());
                this.helper.succeed();
            }
        }

        /**
         * Keeps the victim where it was put, facing him, and - from the second phase on - with the shield really up:
         * a dummy that never breaks the scenario.
         */
        private void holdTheVictimAtItsPost(boolean withShield)
        {
            if (this.blocker.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.blocker.moveTo(this.post.x, this.post.y, this.post.z, this.blocker.getYRot(), 0.0F);
                this.blocker.setDeltaMovement(Vec3.ZERO);
            }

            faceEachOther();

            // The dummy of this scenario must not die: it is a body that keeps the right mouse button down, and a shield
            // that is not held is not a shield. A diamond sword of this body heals away in a single swing, so the wound
            // is closed on every tick instead of letting the blocker be killed before it ever raised its guard - which is
            // exactly how this scenario first failed, with "shieldEverUp=false" and a brain that had nothing left to read.
            this.blocker.setHealth(this.blocker.getMaxHealth());

            if (withShield && !this.blocker.isUsingItem())
            {
                this.blocker.startUsingItem(InteractionHand.OFF_HAND);
            }
        }

        /** Turns the dummy into the Sweatlord, so the shield really covers the side the axe comes from. */
        private void faceEachOther()
        {
            double dx = this.mob.getX() - this.blocker.getX();
            double dz = this.mob.getZ() - this.blocker.getZ();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            this.blocker.setYRot(yaw);
            this.blocker.setYBodyRot(yaw);
            this.blocker.setYHeadRot(yaw);
            this.blocker.setXRot(0.0F);
        }
    }

    /**
     * The instant guard, measured on the hardest shot there is: a shot from four blocks is fired at tick
     * {@link #SHOT_TICK} with the shield deliberately lowered right before it, and this probe demands that the body
     * <b>blocks</b> it anyway.
     *
     * <p>Three things are proven at once, and all three matter: the scenario is real (the shot was on a collision course
     * and it came from the front, so the rule of the game would let a ready shield stop it), the shield was really down
     * when the arrow left (so a vanilla wind up can not explain the block), and the block itself went through
     * {@code LivingEntity#hurt} and its {@link ShieldBlockFeedback} - the reflex window is a permission for this body
     * and not a cancelled damage of this mod.
     */
    private static final class TooLateProbe
    {
        private static final int SHOT_TICK = 20;
        private static final int FLIGHT_TICKS = 4;
        private static final int JUDGE_TICK = SHOT_TICK + FLIGHT_TICKS + 30;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;
        private final ServerPlayer shooter;
        private final Vec3 post;

        private int ticks;
        private float healthBefore = -1.0F;
        private float healthAfter;
        private int blockingTicksDuringFlight;
        private boolean guardWasRaised;
        private boolean blockingBeforeTheShot;
        private boolean shotWasARealThreat;
        private boolean frontRuleHeld;
        private boolean shotFired;
        private boolean judged;

        /** The enemy of this scenario, which is never cleared off the pad. */
        @Nullable
        private final LivingEntity enemy;

        TooLateProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity mob, ServerPlayer shooter)
        {
            this.helper = helper;
            this.level = level;
            this.mob = mob;
            this.shooter = shooter;
            this.post = mob.position();
            this.enemy = mob.getTarget();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            clearThePad(this.mob, this.enemy);
            holdAtPost();

            if (this.enemy != null && this.mob.getTarget() != this.enemy)
            {
                // The scenario commits its enemy. The band rules of the human units are written for a body on patrol, and
                // a body that is held at a post reads as one that is on its way back to it - so a brain that lost its
                // hand-set enemy for a single tick would legitimately pick nobody again and the whole scenario would
                // measure an idle body instead of a shield reaction.
                this.mob.setTarget(this.enemy);
            }

            // The body is turned to the shooter for the whole scenario: this test measures the wind up of a shield and
            // not the turn into a threat, which the other tests of this file already own.
            PlayerLikeActions.faceTowards(this.mob, this.shooter.position());

            if (this.healthBefore < 0.0F)
            {
                this.healthBefore = this.mob.getHealth();
            }

            if (this.ticks == SHOT_TICK - 1)
            {
                // The guard is lowered right before the shot, so this scenario asks the wind up of a shield of a body that
                // really starts without one.
                PlayerLikeActions.stopUsing(this.mob);
            }

            if (this.ticks == SHOT_TICK)
            {
                // The shield is put <b>down</b> on the tick before the shot, so the scenario really asks the wind up of a
                // shield: the body starts the shot with no guard at all, and whatever raised it before (a monster of the
                // world aiming at him, a leftover of another scenario) can not be the answer any more.
                this.blockingBeforeTheShot = ShieldProjectileInteraction.holdsUpAShield(this.mob);
                fire();
            }

            boolean blocking = ShieldProjectileInteraction.holdsUpAShield(this.mob);

            if (blocking)
            {
                this.guardWasRaised = true;
            }

            if (this.shotFired && this.ticks <= SHOT_TICK + FLIGHT_TICKS && blocking)
            {
                ++this.blockingTicksDuringFlight;
            }

            if (this.ticks >= JUDGE_TICK)
            {
                judge();
            }
        }

        private void holdAtPost()
        {
            if (this.mob.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        /** Fires one real arrow at the body from four blocks, on the solved arc of the shooter. */
        private void fire()
        {
            this.shotFired = true;
            Vec3 from = eyeOf(this.shooter);
            Vec3 aim = this.mob.position().add(0.0D, this.mob.getBbHeight() * 0.6D, 0.0D);
            Vec3 velocity = aimedVelocity(from, aim, PlayerLikeActions.ARROW_SPEED);
            Arrow arrow = new Arrow(this.level, this.shooter);
            arrow.shoot(velocity.x, velocity.y, velocity.z, PlayerLikeActions.ARROW_SPEED, 0.0F);
            this.level.addFreshEntity(arrow);

            this.shotWasARealThreat = ProjectileThreatDetector.isThreatTo(this.mob, arrow);
            this.frontRuleHeld = ShieldProjectileInteraction.approachesFromTheFront(this.mob, arrow);
        }

        private void judge()
        {
            this.judged = true;
            this.healthAfter = this.mob.getHealth();

            if (!this.shotWasARealThreat || !this.frontRuleHeld)
            {
                fail("the shot of this scenario was not a real one - on a collision course=" + this.shotWasARealThreat
                        + ", from the front=" + this.frontRuleHeld + " - so a non block would prove nothing");
                return;
            }

            if (this.blockingBeforeTheShot)
            {
                fail("the shield was already up when the shot left, so this scenario can not ask the instant guard of"
                        + " the Sweatlord at all - the vanilla wind up would have been over | " + this.mob.getDebugState());
                return;
            }

            if (!this.guardWasRaised)
            {
                fail("the body never raised the guard at all, so this test would also pass for a body with no shield"
                        + " reaction at all | " + this.mob.getDebugState());
                return;
            }

            if (this.healthAfter < this.healthBefore)
            {
                fail("a shot from four blocks got through the instant projectile guard of this body, so the reflex"
                        + " window of this round does not reach the hurt pipeline: health " + this.healthBefore + " -> "
                        + this.healthAfter + " | " + this.mob.getDebugState());
                return;
            }

            if (ShieldBlockFeedback.vanillaBlocks(this.mob) < 1 && this.blockingTicksDuringFlight < 1)
            {
                fail("the shot was neither blocked by the shield path of the game nor by a shield that was ever seen"
                        + " up while it flew: vanillaBlocks=" + ShieldBlockFeedback.vanillaBlocks(this.mob)
                        + ", feedbacks=" + ShieldBlockFeedback.feedbacks(this.mob) + ", blockingTicksDuringFlight="
                        + this.blockingTicksDuringFlight + ", health " + this.healthBefore + " -> " + this.healthAfter
                        + " | " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[ShieldTest] the instant guard of the Sweatlord stopped a shot that"
                            + " arrived before the wind up: health {} -> {}, vanillaBlocks={}, blockingTicksDuringFlight={} | {}",
                    this.healthBefore, this.healthAfter, ShieldBlockFeedback.vanillaBlocks(this.mob),
                    this.blockingTicksDuringFlight, this.mob.getDebugState());
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /**
     * The front rule of the game, asked of a body that really holds a shield up.
     *
     * <p>A projectile that is not a threat is placed one block in front of the body and one block behind it, and the two
     * answers of {@code ShieldProjectileInteraction} are demanded to differ - and the same two questions are asked of the
     * web projectile of this mod, which deals no damage at all and applies its effect by hand. That is the regression
     * guard of the unified shield rule: a web that a raised shield can not stop is a bug only this test would catch.
     *
     * <p>Neither projectile is ever added to the level: they are placed and asked, so the check has no side effect on
     * the scenario and no web can ever land.
     */
    private static final class FrontRuleProbe
    {
        private static final int SETTLE_TICKS = 12;
        private static final double OFFSET = 1.0D;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;
        private final ServerPlayer shooter;
        private final Vec3 post;

        private int ticks;
        private boolean judged;

        /** The enemy of this scenario, which is never cleared off the pad. */
        @Nullable
        private final LivingEntity enemy;

        FrontRuleProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity mob, ServerPlayer shooter)
        {
            this.helper = helper;
            this.level = level;
            this.mob = mob;
            this.shooter = shooter;
            this.post = mob.position();
            this.enemy = mob.getTarget();
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            clearThePad(this.mob, this.enemy);

            if (this.mob.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
            }

            // A stable facing, and never steered by the guard: the projectiles of this test are not threats, so nothing
            // may turn the body while the rule is asked.
            PlayerLikeActions.faceTowards(this.mob, this.mob.position().add(1.0D, 0.0D, 0.0D));

            // The shield is put up and kept up the way a player holds the right mouse button, on every tick of the settle
            // window: a use state that a body loses for a reason of its own is not the rule of the game, and the rule is
            // what this scenario asks. Once it is really up - which is what the wind up of a shield really means - the
            // two answers of the unified rule are demanded of it.
            PlayerLikeActions.raiseShield(this.mob);

            if (this.ticks < SETTLE_TICKS)
            {
                return;
            }

            if (!ShieldProjectileInteraction.holdsUpAShield(this.mob))
            {
                this.judged = true;
                this.helper.fail("the body never really held a shield up, so the front rule can not be asked of it:"
                        + " usingItem=" + this.mob.isUsingItem() + ", useItem=" + this.mob.getUseItem()
                        + ", offhand=" + this.mob.getOffhandItem() + " | " + this.mob.getDebugState());
                return;
            }

            judge();
        }

        private void judge()
        {
            this.judged = true;
            Arrow arrow = new Arrow(this.level, this.shooter);
            WebShotProjectile web = new WebShotProjectile(this.level, this.shooter);

            boolean arrowFromTheFront = blockedAt(arrow, OFFSET);
            boolean arrowFromBehind = blockedAt(arrow, -OFFSET);
            boolean webFromTheFront = blockedAt(web, OFFSET);
            boolean webFromBehind = blockedAt(web, -OFFSET);

            if (!arrowFromTheFront || arrowFromBehind)
            {
                this.helper.fail("the shield of a body that really holds it up did not follow the front rule of the"
                        + " game: arrowFromTheFront=" + arrowFromTheFront + ", arrowFromBehind=" + arrowFromBehind
                        + " | " + this.mob.getDebugState());
                return;
            }

            if (!webFromTheFront || webFromBehind)
            {
                this.helper.fail("the web projectile of this mod does not use the same shield rule: webFromTheFront="
                        + webFromTheFront + ", webFromBehind=" + webFromBehind + " - a web that a raised shield can"
                        + " not stop is a regression of the unified rule | " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[ShieldTest] the front rule governs arrows and web shots alike:"
                    + " front={}/{}, behind={}/{} | {}", arrowFromTheFront, webFromTheFront, arrowFromBehind,
                    webFromBehind, this.mob.getDebugState());
            this.helper.succeed();
        }

        /** @return the answer of the unified rule for that projectile placed that far in front of the body. */
        private boolean blockedAt(net.minecraft.world.entity.Entity projectile, double offsetX)
        {
            projectile.setPos(this.mob.getX() + offsetX, this.mob.getY() + 0.6D, this.mob.getZ());

            return ShieldProjectileInteraction.blocksProjectile(this.mob, projectile, this.shooter);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // helpers of this file
    // -------------------------------------------------------------------------------------------------------------

    /** One prepared site of this file: the level of the test and the point every body is placed around. */
    private record Site(ServerLevel level, BlockPos centre)
    {
    }

    /** Builds the flat stone pad of this file, keeps its chunks loaded and returns it. */
    private static Site openSite(GameTestHelper helper, int offsetX, int offsetZ)
    {
        ServerLevel level = helper.getLevel();
        BlockPos spawn = level.getSharedSpawnPos();
        BlockPos centre = new BlockPos(spawn.getX() + SITE_X + offsetX, SITE_FLOOR_Y, spawn.getZ() + SITE_Z + offsetZ);

        for (int cx = -2; cx <= 2; ++cx)
        {
            for (int cz = -2; cz <= 2; ++cz)
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

        return new Site(level, centre);
    }

    /**
     * Places a real player body at that offset: the shooter of a scenario and (in spectator mode) the anchor that keeps
     * the site of this test ticking. It is added to the player list as well, because a body the server does not tick
     * would answer no question this file asks.
     */
    @Nullable
    private static ServerPlayer placePlayer(Site site, GameType gameType, double offsetX, double offsetZ, String name)
    {
        ServerPlayer player = FakePlayerFactory.get(site.level(), new GameProfile(UUID.randomUUID(), name));
        player.setGameMode(gameType);
        player.moveTo(site.centre().getX() + offsetX + 0.5D, SITE_FLOOR_Y, site.centre().getZ() + offsetZ + 0.5D,
                180.0F, 0.0F);

        if (!site.level().players().contains(player))
        {
            site.level().addNewPlayer(player);
        }

        return player;
    }

    @Nullable
    private static SweatlordSteveEntity spawnSweatlord(Site site, GameTestHelper helper)
    {
        Mob placed = HumanSpawnFactory.spawn(HungryCraftEntities.SWEATLORD_STEVE.get(), site.level(),
                site.centre(), MobSpawnType.COMMAND, HumanSpawnOrigin.COMMAND);

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
        site.level().addFreshEntity(zombie);

        return zombie;
    }

    /**
     * The victim of the axe test: a monster with a real shield in its offhand, no AI and a rotation that faces the
     * Sweatlord, so the shield really covers the side the axe comes from.
     */
    @Nullable
    private static Zombie spawnShieldBlocker(Site site, double distance, GameTestHelper helper)
    {
        Zombie blocker = spawnPassiveZombie(site, distance, 0.0D, helper);

        if (blocker == null)
        {
            return null;
        }

        blocker.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        blocker.startUsingItem(InteractionHand.OFF_HAND);

        return blocker;
    }

    /**
     * Clears the pad of everything that is not part of the scenario.
     *
     * <p>Every test of a run shares one world: monsters of the game spawn on the flat pads of this file as well, and the
     * leftovers of an earlier scenario can still be walking around in them. Neither is part of a measurement about
     * arrows, and a wandering spider that bites the victim in the middle of one is not a shield failure either - this
     * file has reported exactly that, an unexplained two points of health twice, from an enemy that no scenario of it
     * ever spawned.
     *
     * <p><b>The bodies the scenario was given are never touched.</b> The first version of this helper only spared the
     * body the brain happened to target at that very moment, and the axe scenario paid for it: the shield blocker of the
     * test is a monster like any other, so the moment the brain looked somewhere else for a single tick the blocker was
     * deleted and the scenario had nothing left to break. The caller therefore says what belongs to it.
     */
    private static void clearThePad(SweatlordSteveEntity mob, @Nullable LivingEntity keep)
    {
        // Eighteen blocks and not twenty-eight: the pads of this file are forty blocks apart, and the monsters of the
        // neighbouring scenarios stand up to twenty-six blocks away from the body of this one - a radius that reached
        // them deleted the enemy of another running scenario, which is exactly what the collision scenario then reported
        // with a target that was "removed=DISCARDED". Eighteen still catches the wandering monster that bites the victim
        // at arm's length, which is what this helper exists for.
        for (Monster other : mob.level().getEntitiesOfClass(Monster.class, mob.getBoundingBox().inflate(18.0D)))
        {
            if (other != keep && other != mob.getTarget())
            {
                other.discard();
            }
        }
    }

    /** One shot of a scenario: the tick it leaves the bow on, the eye it leaves from and the speed it leaves with. */
    private record Shot(int tick, Vec3 from, float speed)
    {
    }

    /**
     * Fires the shots of a scenario at their own ticks and judges the one claim of a shield reaction: the body keeps
     * every point of its health, because every shot of that scenario was on a collision course with it.
     *
     * <p>It also proves that the scenario was real. A shot that is not a threat to the body, a body that never raised
     * the shield, and a block that happened while the body faced away are all failures of the test: a mis-aimed or
     * unreadable scenario must never look like a working shield.
     */
    private static final class ShieldProbe
    {
        /** How long the probe watches after the last shot of the scenario before it judges. */
        private static final int TAIL_TICKS = 25;

        private final GameTestHelper helper;
        private final ServerLevel level;
        private final SweatlordSteveEntity mob;
        private final LivingEntity shooter;
        private final List<Shot> shots;

        /** The post of the body: the geometry of a shot is what this file measures, never the walk of the body. */
        private final Vec3 post;

        @Nullable
        private ServerPlayer aimingShooter;
        private int aimTicks;

        private int ticks;
        private float healthBefore = -1.0F;
        private int blockingTicks;
        private int threatTicks;
        private int facingTicks;
        private int aimFacingTicks;

        /** Ticks of the aiming window in which the brain really ran an action of its own, the anti-freeze count. */
        private int fightingTicksWhileAimed;

        /** Ticks the instant guard of the body was up, which is the proof that it turned into the shot it answered. */
        private int guardUpTicks;
        private int firedShots;
        private int provenShots;
        private int firstWarning = -1;
        private boolean aimedLongEnough;
        private boolean judged;

        /** The tail of this probe, and the visible hold window it may also be asked to measure. */
        private int tailTicks = TAIL_TICKS;
        private boolean checkHoldWindow;
        private boolean guardWasUp;
        private int ticksSinceLastThreat;
        private int holdTicksAfterThreat;
        private boolean guardCameDown;

        /** The enemy of this scenario, which is never cleared off the pad. */
        @Nullable
        private final LivingEntity enemy;

        ShieldProbe(GameTestHelper helper, ServerLevel level, SweatlordSteveEntity mob, LivingEntity shooter,
                List<Shot> shots)
        {
            this.helper = helper;
            this.level = level;
            this.mob = mob;
            this.shooter = shooter;
            this.shots = shots;
            this.post = mob.position();
            this.enemy = mob.getTarget();
        }

        /** Keeps the body at its post: a reaction that is measured on a walking body measures the walk as well. */
        private void holdPost()
        {
            if (this.mob.position().distanceToSqr(this.post) > 1.0E-4D)
            {
                this.mob.moveTo(this.post.x, this.post.y, this.post.z, this.mob.getYRot(), this.mob.getXRot());
                this.mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        /** Makes that player hold a drawn bow aimed at the Sweatlord, for that many ticks before the first shot. */
        void aimWithTheBow(int ticks, ServerPlayer shooter)
        {
            this.aimingShooter = shooter;
            this.aimTicks = ticks;
        }

        /**
         * Asks this probe to measure the <b>visible hold window</b> as well: after the last threat the guard has to stay
         * up for at least {@link InstantProjectileGuard#MIN_HOLD_TICKS} ticks, and it has to come down again.
         *
         * <p>The tail is stretched for it, because a probe that judges before the guard is allowed to come down could
         * never tell a released shield from a parked one.
         */
        void expectingAVisibleHoldWindow()
        {
            this.checkHoldWindow = true;
            this.tailTicks = InstantProjectileGuard.MAX_HOLD_TICKS + 30;
        }

        void tick()
        {
            if (this.judged)
            {
                return;
            }

            ++this.ticks;
            clearThePad(this.mob, this.enemy);
            holdPost();

            if (this.enemy != null && this.mob.getTarget() != this.enemy)
            {
                // The scenario commits its enemy. The band rules of the human units are written for a body on patrol, and
                // a body that is held at a post reads as one that is on its way back to it - so a brain that lost its
                // hand-set enemy for a single tick would legitimately pick nobody again, stop wanting the body at all and
                // leave the arrow to a body that is standing still. That is how this scenario failed three runs in a row.
                this.mob.setTarget(this.enemy);
            }

            if (this.healthBefore < 0.0F)
            {
                this.healthBefore = this.mob.getHealth();
            }

            boolean blocking = ShieldProjectileInteraction.holdsUpAShield(this.mob);

            if (this.aimingShooter != null && this.firedShots == 0)
            {
                aim(this.aimingShooter);

                // The requirement of this round, and the exact opposite of the last one: a drawn bow pointed at him is
                // <b>not</b> a threat. A player who holds a bow and never lets go may not park this body behind a
                // shield, so the guard has to stay <b>down</b> for as long as the arrow does not exist.
                if (blocking)
                {
                    fail("a shooter aimed a drawn bow at him and the shield is already up: a bow that never shoots is"
                            + " still controlling the body, which is exactly the bug this round removes | "
                            + this.mob.getDebugState());
                    return;
                }

                if (this.ticks == this.aimTicks)
                {
                    this.aimedLongEnough = true;
                }

                // And he goes on with his own fight instead of standing there: a body that reacts to a bow it can not
                // see the arrow of is a body that stopped fighting.
                String skill = this.mob.debugSkill();

                if (!"none".equals(skill) && !InstantProjectileGuard.NAME.equals(skill))
                {
                    ++this.fightingTicksWhileAimed;
                }
            }

            if (blocking)
            {
                ++this.blockingTicks;
            }

            if (this.mob.brain().instantGuard().isActive())
            {
                ++this.guardUpTicks;
            }

            ProjectileThreatDetector.Threat threat = ProjectileThreatDetector.worst(this.mob);

            if (threat != null)
            {
                ++this.threatTicks;
                this.ticksSinceLastThreat = 0;

                if (this.firstWarning < 0)
                {
                    this.firstWarning = threat.ticksToImpact();
                }

                if (faces(threat.projectile()))
                {
                    // The turn is the reaction itself and not the block: it is counted from the tick the shot is seen.
                    ++this.facingTicks;
                }
            }
            else if (this.firedShots > 0)
            {
                ++this.ticksSinceLastThreat;
            }

            if (this.checkHoldWindow)
            {
                this.trackHoldWindow(blocking);
            }

            for (Shot shot : this.shots)
            {
                if (shot.tick() == this.ticks)
                {
                    fire(shot);
                }
            }

            if (this.firedShots >= this.shots.size() && this.ticks >= lastShotTick() + this.tailTicks)
            {
                judge();
            }
        }

        /**
         * Counts the two things the visible hold window is made of: how long the guard stayed up after the last threat,
         * and whether it really came down again.
         */
        private void trackHoldWindow(boolean blocking)
        {
            if (blocking)
            {
                this.guardWasUp = true;

                if (this.ticksSinceLastThreat > 0)
                {
                    ++this.holdTicksAfterThreat;
                }
            }
            else if (this.guardWasUp && this.ticksSinceLastThreat > 1)
            {
                this.guardCameDown = true;
            }
        }

        /** @return the tick the last shot of the scenario leaves on. */
        private int lastShotTick()
        {
            int last = 0;

            for (Shot shot : this.shots)
            {
                last = Math.max(last, shot.tick());
            }

            return last;
        }

        /** Fires one real arrow of that scenario and proves that it really was on a collision course. */
        private void fire(Shot shot)
        {
            Vec3 velocity = aimedVelocity(shot.from(), aimPoint(), shot.speed());
            Arrow arrow = new Arrow(this.level, this.shooter);
            arrow.shoot(velocity.x, velocity.y, velocity.z, shot.speed(), 0.0F);
            arrow.setCritArrow(true);
            this.level.addFreshEntity(arrow);
            ++this.firedShots;

            if (!ProjectileThreatDetector.isThreatTo(this.mob, arrow))
            {
                fail("the shot of this test is not on a collision course with the body, so the scenario proves"
                        + " nothing: from=" + shot.from() + ", aim=" + aimPoint() + ", velocity=" + velocity
                        + ", distance=" + this.mob.distanceTo(this.shooter));
                return;
            }

            ++this.provenShots;
        }

        /** @return the chest of the body, the point a player aims at. */
        private Vec3 aimPoint()
        {
            return this.mob.position().add(0.0D, this.mob.getBbHeight() * 0.6D, 0.0D);
        }

        /** @return true when the body looks at that projectile: the block of a shield only covers the front. */
        private boolean faces(net.minecraft.world.entity.Entity projectile)
        {
            Vec3 view = this.mob.getViewVector(1.0F);
            Vec3 toShot = projectile.position().subtract(this.mob.position());
            Vec3 flatView = new Vec3(view.x, 0.0D, view.z);
            Vec3 flatTo = new Vec3(toShot.x, 0.0D, toShot.z);

            if (flatView.lengthSqr() < 1.0E-6D || flatTo.lengthSqr() < 1.0E-4D)
            {
                return false;
            }

            return flatTo.normalize().dot(flatView.normalize()) > 0.5D;
        }

        /** Keeps the bow of that shooter drawn and pointed at the Sweatlord, the way a player holds a shot. */
        private void aim(ServerPlayer player)
        {
            double dx = this.mob.getX() - player.getX();
            double dz = this.mob.getZ() - player.getZ();
            double dy = this.mob.getEyeY() - player.getEyeY();
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            player.setYRot(yaw);
            player.setYBodyRot(yaw);
            player.setYHeadRot(yaw);
            player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.max(0.05D, Math.sqrt(dx * dx + dz * dz)))));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));

            if (!player.isUsingItem())
            {
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
        }

        private void judge()
        {
            this.judged = true;
            float lost = this.healthBefore - this.mob.getHealth();

            if (this.provenShots < this.shots.size())
            {
                fail("only " + this.provenShots + " of the " + this.shots.size() + " shots of this scenario were on a"
                        + " collision course with the body, so the test proves nothing | " + this.mob.getDebugState());
                return;
            }

            if (lost > 0.0F)
            {
                fail("the Sweatlord lost " + lost + " health although the reaction was expected to stop every shot"
                        + " of this scenario: blockingTicks=" + this.blockingTicks + ", threatTicks=" + this.threatTicks
                        + ", facingTicks=" + this.facingTicks + ", firstWarning=" + this.firstWarning
                        + " ticks | " + this.mob.getDebugState());
                return;
            }

            if (this.blockingTicks == 0)
            {
                fail("the shield was never raised although " + this.threatTicks + " ticks saw a threat on a collision"
                        + " course | " + this.mob.getDebugState());
                return;
            }

            if (this.facingTicks == 0 && this.aimFacingTicks == 0 && this.guardUpTicks == 0)
            {
                fail("the shield was raised but the body never faced the shot or the shooter, so the block can only"
                        + " have been luck | " + this.mob.getDebugState());
                return;
            }

            if (this.checkHoldWindow)
            {
                if (this.holdTicksAfterThreat < InstantProjectileGuard.MIN_HOLD_TICKS - 2)
                {
                    fail("the guard was only up for " + this.holdTicksAfterThreat + " ticks after the last threat,"
                            + " which is a flicker and not a hold window a player can read (the minimum of this round"
                            + " is " + InstantProjectileGuard.MIN_HOLD_TICKS + " ticks) | "
                            + this.mob.getDebugState());
                    return;
                }

                if (!this.guardCameDown)
                {
                    fail("the guard never came down again after the threat was gone, so the shield is parked and not"
                            + " raised | " + this.mob.getDebugState());
                    return;
                }
            }

            if (this.aimingShooter != null && this.aimedLongEnough && this.fightingTicksWhileAimed < 5)
            {
                fail("the body ran an action of its own on only " + this.fightingTicksWhileAimed + " of the "
                        + this.aimTicks + " ticks a bow was aimed at it, so the held bow is still what he is reacting"
                        + " to | " + this.mob.getDebugState());
                return;
            }

            SpiderQueenHungryCraft.LOGGER.info("[ShieldTest] passed: {} shot(s), health kept at {}, blockingTicks={},"
                            + " threatTicks={}, facingTicks={}/{}, firstWarning={} ticks, guardStayedDownWhileAiming={},"
                            + " fightingTicksWhileAimed={} | {}",
                    this.shots.size(), this.healthBefore, this.blockingTicks, this.threatTicks, this.facingTicks,
                    this.aimFacingTicks, this.firstWarning, this.aimedLongEnough, this.fightingTicksWhileAimed,
                    this.mob.getDebugState());
            this.helper.succeed();
        }

        private void fail(String message)
        {
            this.judged = true;
            this.helper.fail(message);
        }
    }

    /**
     * @return the point an arrow of that body really leaves from, gravity and drag included, so that the shot of this
     *         file is never aimed from the feet while the game spawns it at the eye - which is exactly what made the
     *         first run of this file report a shot that "was not on a collision course".
     */
    private static Vec3 eyeOf(ServerPlayer shooter)
    {
        return new Vec3(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    /**
     * @return the velocity of a shot of that speed that really reaches that point, gravity and drag included.
     *
     * <p>This is the aim of the shooter of this test, and it is solved instead of guessed: the real trajectory of the
     * game is simulated with {@link ProjectileBallistics#sample} and the aim is corrected by the error of the last
     * pass, so an arrow of a scenario is never a shot that misses by accident.
     */
    private static Vec3 aimedVelocity(Vec3 from, Vec3 destination, float speed)
    {
        Vec3 aim = destination;

        for (int pass = 0; pass < 6; ++pass)
        {
            Vec3 direction = aim.subtract(from);

            if (direction.lengthSqr() < 1.0E-6D)
            {
                return Vec3.ZERO;
            }

            int flight = Math.max(1, (int) Math.ceil(direction.length() / speed));
            Vec3 landed = ProjectileBallistics.sample(from, direction.normalize().scale(speed),
                    ProjectileBallistics.ARROW_GRAVITY, flight);
            aim = aim.add(destination.subtract(landed));
        }

        return aim.subtract(from).normalize().scale(speed);
    }
}
