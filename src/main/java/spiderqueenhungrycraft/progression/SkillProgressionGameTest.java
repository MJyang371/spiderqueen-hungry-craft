package spiderqueenhungrycraft.progression;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ability.QueenImmunity;
import spiderqueenhungrycraft.ability.QueenPhysiology;
import spiderqueenhungrycraft.ability.QueenSniff;
import spiderqueenhungrycraft.attack.EmpoweredAttackHandler;
import spiderqueenhungrycraft.beast.QueenBeastController;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.brood.SpiderRewards;
import spiderqueenhungrycraft.devour.DevourHandler;
import spiderqueenhungrycraft.devour.DevourInput;
import spiderqueenhungrycraft.movement.SimpleWallClimbController;
import spiderqueenhungrycraft.player.QueenData;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.registry.HungryCraftAttributes;
import spiderqueenhungrycraft.registry.HungryCraftBlocks;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.soul.Souls;
import spiderqueenhungrycraft.stomach.StomachCoreArmorPolicy;

/**
 * The runtime proof of the skill tree (Skill Progression round): the official nodes, the permission of every
 * ability they gate, and the rules of buying one.
 *
 * <p>Everything is asked through the very entry points the server uses - {@link ProgressionService#unlockSkill},
 * {@link SoulShopService#purchase} and the gates of the abilities themselves ({@link QueenImmunity},
 * {@link EmpoweredAttackHandler}, {@link QueenBeastController}, {@link DevourInput}, {@link QueenSniff},
 * {@link SpiderRewards}, {@link StomachCoreArmorPolicy}) - so nothing here imitates the system; it drives it.
 *
 * <p><b>Nothing in here depends on a lucky roll.</b> The one random path (the egg of a digested prey) is measured in the
 * direction that is deterministic: a queen who does not own the node can roll as often as she likes and gets nothing at
 * all.
 */
@GameTestHolder(SpiderQueenHungryCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SkillProgressionGameTest
{
    /** Everything here is synchronous, so a few ticks are plenty. */
    private static final int TIMEOUT_TICKS = 40;

    /** Where the core of a test stands and where the player using it stands, like in the growth test. */
    private static final BlockPos CORE_POS = new BlockPos(2, 1, 2);

    /** How often the reward of a meal is rolled by the test of the egg. */
    private static final int EGG_ROLLS = 256;

    /** One official node of this round: its identity, the level it asks for and what it costs. */
    private record Official(ResourceLocation id, int level, long cost)
    {
    }

    /** The official nodes this round ships, with the numbers the design table names. */
    private static final List<Official> OFFICIAL = List.of(
            new Official(QueenSkills.VISCERAL_ENHANCEMENT, 1, 250L),
            new Official(QueenSkills.QUEEN_STANCE, 5, 1000L),
            new Official(QueenSkills.CHITIN_SHELL, 13, 500L),
            new Official(QueenSkills.BROOD_QUEEN, 15, 1000L),
            new Official(QueenSkills.QUEEN_BEAST, 20, 1000L),
            new Official(QueenSkills.GIANT_DEVOUR, 20, 1500L),
            new Official(QueenSkills.VISCERAL_ENHANCEMENT_II, 20, 2000L),
            new Official(QueenSkills.QUEEN_SNIFF, 30, 2000L),
            // 亡灵契约 IV (Necromancy IV round). Its two numbers are PROVISIONAL: the round has not decided a real
            // level or a real price for the contract yet, and the node file says so as well.
            new Official(QueenSkills.NECROMANCY_IV, 1, 0L));

    private SkillProgressionGameTest()
    {
    }

    // ------------------------------------------------------------------ the furniture of these tests

    /** @return one fake player of this test, deliberately never put into the level (see the growth test). */
    private static ServerPlayer fakePlayer(GameTestHelper helper, String name)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CORE_POS);
        ServerPlayer player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), name));

        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        QueenDataCapability.setQueen(player, true);

        return player;
    }

    /** Puts a real territory core down and opens its interface for that player. */
    private static BlockPos openCore(GameTestHelper helper, ServerPlayer player)
    {
        helper.setBlock(CORE_POS, HungryCraftBlocks.TERRITORY_CORE.get());

        BlockPos pos = helper.absolutePos(CORE_POS);

        player.moveTo(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, 0.0F, 0.0F);
        ProgressionSessions.open(player, pos);

        return pos;
    }

    /** Closes the interface of that player. */
    private static void closeCore(ServerPlayer player)
    {
        ProgressionSessions.close(player.getUUID());
    }

    /** Fails the test on the promise of the rule that did not hold. */
    private static void expect(GameTestHelper helper, String what, boolean condition)
    {
        if (!condition)
        {
            helper.fail("SKILLS: " + what);
        }
    }

    /** @return the definition of that official node, or null when the data pack did not load it. */
    @Nullable
    private static SkillNodeDefinition node(ResourceLocation id)
    {
        return SkillNodes.find(id).orElse(null);
    }

    /** Writes a level and a purse and asks for one node, the way a request of a client is answered. */
    private static ProgressionFeedback buy(ServerPlayer player, BlockPos core, ResourceLocation skill)
    {
        return ProgressionService.unlockSkill(player, core, skill);
    }

    /** @return the eggs that lie around a spot, which is the evidence of one reward. */
    private static int countEggs(ServerLevel level, BlockPos pos)
    {
        return level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(8.0D),
                entity -> entity.getItem().is(HungryCraftItems.SPIDER_EGG.get())).size();
    }

    /** Sweeps the eggs of a test away, so one test can not see the litter of another. */
    private static void clearEggs(ServerLevel level, BlockPos pos)
    {
        for (ItemEntity egg : level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos)
                .inflate(8.0D), entity -> entity.getItem().is(HungryCraftItems.SPIDER_EGG.get())))
        {
            egg.discard();
        }
    }

    /** @return how many pieces of that item the player carries, in any slot. */
    private static int countInInventory(ServerPlayer player, net.minecraft.world.item.Item item)
    {
        int found = 0;

        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
        {
            ItemStack stack = player.getInventory().getItem(slot);

            if (stack.is(item))
            {
                found += stack.getCount();
            }
        }

        return found;
    }

    // ------------------------------------------------------------------ TEST A, TEST B and TEST W: the tree itself

    /** <b>TEST A: a new player owns no skill at all, and an old save is exactly as empty.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void anewplayerownsnoSkill(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_new_player");

        expect(helper, "a player who never bought a node owns none", QueenSkills.of(player).isEmpty());
        expect(helper, "and her data holds an empty set",
                QueenDataCapability.get(player).map(data -> data.unlockedSkills().isEmpty()).orElse(false));

        // The same on a rebuilt data object: what a save written before this round reads is the empty set, so no old
        // player is handed a single ability.
        QueenData reloaded = new QueenData();
        reloaded.deserializeNBT(new CompoundTag());

        expect(helper, "an old save file starts with no skill at all", reloaded.unlockedSkills().isEmpty());

        helper.succeed();
    }

    /** <b>TEST B: the two innate abilities are not gated by the tree at all.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void theinnateabilitiesareneverlocked(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_innate");

        expect(helper, "the wall climb mode of a queen without a single node starts switched off",
                !SimpleWallClimbController.isEnabled(player));

        SimpleWallClimbController.setEnabled(player, true);

        expect(helper, "and it can be switched on without owning anything", SimpleWallClimbController.isEnabled(player));

        // Nothing in the tree covers the two innate abilities, which is what makes them innate.
        expect(helper, "the tree holds no node for the wall climb",
                node(ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "wall_crawl")) == null);
        expect(helper, "and none for the web shot either",
                node(ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "web_shot")) == null);

        SimpleWallClimbController.setEnabled(player, false);
        helper.succeed();
    }

    /** <b>TEST W: the tree of this round is exactly the official nodes, and every one of them counts.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thetreeholdsthenineofficialnodes(GameTestHelper helper)
    {
        expect(helper, "the data pack loaded exactly the official nodes, found "
                + SkillNodes.definitions().size(), SkillNodes.definitions().size() == OFFICIAL.size());

        boolean complete = true;

        for (Official official : OFFICIAL)
        {
            SkillNodeDefinition definition = node(official.id());

            if (definition == null)
            {
                expect(helper, "the node " + official.id() + " is loaded", false);
                complete = false;
                continue;
            }

            expect(helper, "the node " + official.id() + " asks for level " + official.level,
                    definition.requiredLevel() == official.level);
            expect(helper, "the node " + official.id() + " costs " + official.cost + " souls",
                    definition.soulCost() == official.cost);
            expect(helper, "the node " + official.id() + " is enabled and counts for the legend of a later round",
                    definition.enabled() && definition.requiredForLegend());
        }

        // The one node with prerequisites of this round names all three of them, and all of them are required.
        SkillNodeDefinition visceralTwo = node(QueenSkills.VISCERAL_ENHANCEMENT_II);

        expect(helper, "the second visceral enhancement needs all three of its prerequisites",
                visceralTwo != null && visceralTwo.parents().size() == 3
                        && visceralTwo.parents().contains(QueenSkills.QUEEN_BEAST)
                        && visceralTwo.parents().contains(QueenSkills.GIANT_DEVOUR)
                        && visceralTwo.parents().contains(QueenSkills.VISCERAL_ENHANCEMENT));
        expect(helper, "the devour hangs off the form of the beast",
                node(QueenSkills.GIANT_DEVOUR) != null
                        && node(QueenSkills.GIANT_DEVOUR).parents().contains(QueenSkills.QUEEN_BEAST));

        // The completion condition of the future legendary round reads exactly those nodes: nothing of this round
        // unlocks the legend, but the question it will ask already has its answer (TEST: the readiness of the legend).
        ServerPlayer player = fakePlayer(helper, "skills_legend_ready");

        expect(helper, "a queen without a single node has not completed the tree",
                !SkillNodes.allRequiredForLegendUnlocked(player));
        expect(helper, "and the legendary system of this round stays locked",
                !LegendaryProgression.isEligible(player));

        for (Official official : OFFICIAL)
        {
            QueenSkills.unlock(player, official.id());
        }

        expect(helper, "with all eight nodes the skill half of the completion is done",
                SkillNodes.allRequiredForLegendUnlocked(player));
        expect(helper, "and the level half is still missing, so the legend stays out of reach",
                !LegendaryProgression.isEligible(player));

        if (complete)
        {
            helper.succeed();
            return;
        }

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST C, TEST D and TEST E: the passives and the stance

    /** <b>TEST C: the visceral enhancement is the whole of the poison and hunger immunity.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thevisceralimmunityisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_immunity");

        expect(helper, "a queen without the node is not immune to poison",
                !QueenImmunity.isImmune(player, MobEffects.POISON));
        expect(helper, "and not immune to the status effect of hunger either",
                !QueenImmunity.isImmune(player, MobEffects.HUNGER));

        // The darkness of the light belongs to the base body of the queen and is never bought.
        expect(helper, "the darkness a bright light causes stays a part of her body, node or not",
                QueenImmunity.isImmune(player, MobEffects.DARKNESS));

        // The refusal is the real path of the game: an effect that is applied really lands, or really does not.
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));

        expect(helper, "a poison really lands on a queen without the node", player.hasEffect(MobEffects.POISON));

        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100));

        expect(helper, "and so does a status effect of hunger", player.hasEffect(MobEffects.HUNGER));

        QueenSkills.unlock(player, QueenSkills.VISCERAL_ENHANCEMENT);

        expect(helper, "the node makes her immune to both",
                QueenImmunity.isImmune(player, MobEffects.POISON)
                        && QueenImmunity.isImmune(player, MobEffects.HUNGER));

        // A poison that was applied before the node was bought is taken away at once, and a new one never lands.
        QueenImmunity.clearManagedEffects(player);
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100));

        expect(helper, "an old poison is gone and a new one never lands", !player.hasEffect(MobEffects.POISON));
        expect(helper, "the same for the status effect of hunger", !player.hasEffect(MobEffects.HUNGER));

        // And none of this may ever touch the hunger bar itself.
        player.getFoodData().setFoodLevel(17);
        QueenImmunity.clearManagedEffects(player);

        expect(helper, "the hunger bar of the queen is not what this node is about",
                player.getFoodData().getFoodLevel() == 17);

        helper.succeed();
    }

    /** <b>TEST D: the empowered attack is the stance of the queen, and a revoked stance takes the charge with it.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void theempoweredattackisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_stance");

        player.getFoodData().setFoodLevel(20);
        EmpoweredAttackHandler.tryArm(player);

        expect(helper, "a queen without the stance arms nothing", !EmpoweredAttackHandler.isArmed(player));
        expect(helper, "and the request cost her no hunger", player.getFoodData().getFoodLevel() == 20);

        QueenSkills.unlock(player, QueenSkills.QUEEN_STANCE);
        EmpoweredAttackHandler.tryArm(player);

        expect(helper, "with the stance the strike is armed", EmpoweredAttackHandler.isArmed(player));
        expect(helper, "and it was paid for out of the hunger bar", player.getFoodData().getFoodLevel() < 20);

        QueenSkills.revoke(player, QueenSkills.QUEEN_STANCE);
        QueenSkills.refreshRuntime(player);

        expect(helper, "a charge of a revoked stance is worth nothing", !EmpoweredAttackHandler.isArmed(player));
        expect(helper, "and the node is really gone", !QueenSkills.has(player, QueenSkills.QUEEN_STANCE));

        helper.succeed();
    }

    /** <b>TEST E: the plating of the body is the chitin shell, and the inner armor never leaks into it.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thechitinshellholdsthearmor(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_chitin");

        QueenPhysiology.updateArmor(player);

        expect(helper, "without the node the body has no plating of its own", player.getArmorValue() == 0);

        QueenSkills.unlock(player, QueenSkills.CHITIN_SHELL);
        QueenSkills.refreshRuntime(player);

        expect(helper, "with the node the seven points of leather are there", player.getArmorValue() == 7);

        // The inner armor is a node of its own and has nothing to do with what she wears.
        QueenSkills.unlock(player, QueenSkills.VISCERAL_ENHANCEMENT_II);
        QueenSkills.refreshRuntime(player);

        expect(helper, "the visceral armor never shows up as outer armor", player.getArmorValue() == 7);

        QueenSkills.revoke(player, QueenSkills.CHITIN_SHELL);
        QueenSkills.refreshRuntime(player);

        expect(helper, "taking the node back takes the outer plating with it", player.getArmorValue() == 0);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST F: the eggs of the brood queen

    /** <b>TEST F: the laying of an egg belongs to the brood queen and to nothing else.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void theeggneedsabroodqueen(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_brood_egg");
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(CORE_POS).above();
        net.minecraft.world.phys.Vec3 spot = net.minecraft.world.phys.Vec3.atCenterOf(pos);

        clearEggs(level, pos);

        // A queen without the node: a meal pays its experience and never leaves an egg, however often it is rolled.
        // This is the deterministic half of the rule, which is why the test can not be flaky.
        for (int roll = 0; roll < EGG_ROLLS; roll++)
        {
            SpiderRewards.grant(level, spot, 0, player);
        }

        expect(helper, "a queen without the brood queen lays no egg in " + EGG_ROLLS + " meals",
                countEggs(level, pos) == 0);

        // The probe of the rate is the one path without a permission, and it is not a path a player walks: it is what
        // makes the rate of the reward measurable at all.
        clearEggs(level, pos);

        for (int roll = 0; roll < EGG_ROLLS; roll++)
        {
            SpiderRewards.grantForProbe(level, spot, 0);
        }

        expect(helper, "the probe of the rate really rolls the reward, eggs found " + countEggs(level, pos),
                countEggs(level, pos) > 0);

        // With the node the very same meals do lay them.
        clearEggs(level, pos);
        QueenSkills.unlock(player, QueenSkills.BROOD_QUEEN);

        for (int roll = 0; roll < EGG_ROLLS; roll++)
        {
            SpiderRewards.grant(level, spot, 0, player);
        }

        expect(helper, "a brood queen really lays eggs, found " + countEggs(level, pos), countEggs(level, pos) > 0);

        // And a revoked node stops it again, because the gate is read on every reward.
        clearEggs(level, pos);
        QueenSkills.revoke(player, QueenSkills.BROOD_QUEEN);

        for (int roll = 0; roll < EGG_ROLLS; roll++)
        {
            SpiderRewards.grant(level, spot, 0, player);
        }

        expect(helper, "a queen whose node was taken back lays nothing again", countEggs(level, pos) == 0);

        clearEggs(level, pos);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST G and TEST H: the shop and the brood queen

    /** <b>TEST G and TEST H: the spider egg of the shop exists for a brood queen and for nobody else.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thespidereggoftheshopneedsabroodqueen(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_shop_brood");
        BlockPos core = openCore(helper, player);
        ResourceLocation product = ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "spider_egg");

        Souls.setSouls(player, 1000L);

        expect(helper, "a queen without the brood queen does not even see the product",
                !ProgressionSnapshot.of(player, core).shop().stream()
                        .anyMatch(entry -> entry.id().equals(product)));

        // The forged request of a modified client is answered exactly like a product that was never there.
        expect(helper, "and a forged purchase of it is refused",
                SoulShopService.purchase(player, core, product) == ProgressionFeedback.UNKNOWN_PRODUCT);
        expect(helper, "it cost her not one soul", Souls.getSouls(player) == 1000L);
        expect(helper, "and she holds no egg",
                countInInventory(player, HungryCraftItems.SPIDER_EGG.get()) == 0);

        QueenSkills.unlock(player, QueenSkills.BROOD_QUEEN);

        expect(helper, "with the node the product really appears in her shop",
                ProgressionSnapshot.of(player, core).shop().stream()
                        .anyMatch(entry -> entry.id().equals(product)));

        expect(helper, "the purchase is accepted",
                SoulShopService.purchase(player, core, product) == ProgressionFeedback.PURCHASE_OK);
        expect(helper, "it still costs a hundred souls", Souls.getSouls(player) == 900L);
        expect(helper, "and she really holds the egg",
                countInInventory(player, HungryCraftItems.SPIDER_EGG.get()) == 1);

        // And a revoked node takes the product away with it, without touching what she already bought.
        QueenSkills.revoke(player, QueenSkills.BROOD_QUEEN);

        expect(helper, "a revoked brood queen does not see the product again",
                !ProgressionSnapshot.of(player, core).shop().stream()
                        .anyMatch(entry -> entry.id().equals(product)));
        expect(helper, "and the egg she already bought stays hers",
                countInInventory(player, HungryCraftItems.SPIDER_EGG.get()) == 1);

        closeCore(player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST I and TEST J: the beast form

    /** <b>TEST I and TEST J: the form of the beast is bought, and the tap counter never even starts without it.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thebeastformisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_beast");
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        expect(helper, "the beast state of a player is there", state != null);

        if (state == null)
        {
            helper.succeed();
            return;
        }

        player.getFoodData().setFoodLevel(20);

        for (int tap = 0; tap < 3; tap++)
        {
            QueenBeastController.onTransformKeyPressed(player);
        }

        expect(helper, "a queen without the node does not transform", !QueenBeastState.isTransformed(player));
        expect(helper, "and not a single tap was counted", state.getTransformTapCount() == 0);

        QueenSkills.unlock(player, QueenSkills.QUEEN_BEAST);

        for (int tap = 0; tap < 3; tap++)
        {
            QueenBeastController.onTransformKeyPressed(player);
        }

        expect(helper, "with the node three taps turn her into the beast", QueenBeastState.isTransformed(player));
        expect(helper, "and the taps are spent with the transformation", state.getTransformTapCount() == 0);

        // Leaving the form is always allowed and stays allowed, whatever she owns.
        QueenBeastController.onTransformKeyPressed(player);

        expect(helper, "one press leaves the form again", !QueenBeastState.isTransformed(player));

        // A form that predates a revocation is left at once, without a relog.
        for (int tap = 0; tap < 3; tap++)
        {
            QueenBeastController.onTransformKeyPressed(player);
        }

        expect(helper, "she is a beast again", QueenBeastState.isTransformed(player));

        QueenSkills.revoke(player, QueenSkills.QUEEN_BEAST);
        QueenSkills.refreshRuntime(player);

        expect(helper, "losing the node ends the form at once", !QueenBeastState.isTransformed(player));

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST K and TEST L: the throat of the beast

    /** <b>TEST K and TEST L: the devour is a node of its own and not a part of the form.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thedevourisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_devour");
        QueenBeastState state = QueenBeastState.of(player).orElse(null);

        expect(helper, "the beast state of a player is there", state != null);

        if (state == null)
        {
            helper.succeed();
            return;
        }

        QueenSkills.unlock(player, QueenSkills.QUEEN_BEAST);
        player.getFoodData().setFoodLevel(20);

        for (int tap = 0; tap < 3; tap++)
        {
            QueenBeastController.onTransformKeyPressed(player);
        }

        expect(helper, "the beast form of this test was entered", QueenBeastState.isTransformed(player));

        // Queen Beast without Giant Devour: the key is nothing at all, and not even the press is remembered.
        DevourInput.onPress(player);

        expect(helper, "a beast without the throat does not remember a press", !state.isDevourPressed());

        DevourInput.onRelease(player);
        DevourInput.tick(player);

        expect(helper, "and it swallows nothing", DevourHandler.tryDevour(player) == 0);
        expect(helper, "and it still remembers nothing afterwards", !state.isDevourPressed());

        QueenSkills.unlock(player, QueenSkills.GIANT_DEVOUR);
        DevourInput.onPress(player);

        expect(helper, "with the node the very same press is remembered", state.isDevourPressed());

        DevourInput.clear(player);

        // And a press that is no longer covered is forgotten the moment the node is taken back.
        DevourInput.onPress(player);
        QueenSkills.revoke(player, QueenSkills.GIANT_DEVOUR);
        QueenSkills.refreshRuntime(player);

        expect(helper, "losing the node forgets a half pressed key at once", !state.isDevourPressed());

        QueenBeastController.forceUntransform(player, QueenBeastController.RevertCause.RECONCILE);
        DevourInput.clear(player);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST M and TEST N: all three prerequisites

    /** <b>TEST M and TEST N: the second visceral enhancement needs all three of its prerequisites, never any of them.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thevisceraltwoneedsallthreeparents(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_visceral_two");
        BlockPos core = openCore(helper, player);

        QueenLevels.setLevel(player, 20);
        Souls.setSouls(player, 10_000L);

        expect(helper, "with no prerequisite at all it can not be bought",
                buy(player, core, QueenSkills.VISCERAL_ENHANCEMENT_II) == ProgressionFeedback.SKILL_LOCKED);
        expect(helper, "and nothing was spent", Souls.getSouls(player) == 10_000L);

        QueenSkills.unlock(player, QueenSkills.QUEEN_BEAST);
        QueenSkills.unlock(player, QueenSkills.GIANT_DEVOUR);

        expect(helper, "two of the three prerequisites are still not enough",
                buy(player, core, QueenSkills.VISCERAL_ENHANCEMENT_II) == ProgressionFeedback.SKILL_LOCKED);
        expect(helper, "and still nothing was spent", Souls.getSouls(player) == 10_000L);

        QueenSkills.unlock(player, QueenSkills.VISCERAL_ENHANCEMENT);

        expect(helper, "with all three of them, the level and the souls it is bought",
                buy(player, core, QueenSkills.VISCERAL_ENHANCEMENT_II) == ProgressionFeedback.SKILL_OK);
        expect(helper, "it cost exactly its price", Souls.getSouls(player) == 8_000L);
        expect(helper, "and she really owns it", QueenSkills.has(player, QueenSkills.VISCERAL_ENHANCEMENT_II));

        closeCore(player);
        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST O: the visceral armor

    /** <b>TEST O: the inner armor of the stomach wall is the second visceral enhancement, and nothing else.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thevisceralarmorisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_visceral_armor");

        expect(helper, "without the node the wall of her stomach has no inner armor at all",
                StomachCoreArmorPolicy.visceralArmorOf(player) == StomachCoreArmorPolicy.NO_VISCERAL_ARMOR);

        QueenSkills.unlock(player, QueenSkills.VISCERAL_ENHANCEMENT_II);

        expect(helper, "with the node it is the iron worth of the default",
                StomachCoreArmorPolicy.visceralArmorOf(player) == HungryCraftAttributes.VISCERAL_ARMOR_DEFAULT);
        expect(helper, "and the outer armor of her body is untouched by it", player.getArmorValue() == 0);

        QueenSkills.revoke(player, QueenSkills.VISCERAL_ENHANCEMENT_II);

        expect(helper, "taking the node back takes the inner armor with it",
                StomachCoreArmorPolicy.visceralArmorOf(player) == StomachCoreArmorPolicy.NO_VISCERAL_ARMOR);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST P: the sniff

    /** <b>TEST P: the sniff is bought, and a locked key costs nothing at all - not even hunger.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void thesniffisbought(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_sniff");
        LivingEntity prey = helper.spawn(EntityType.CHICKEN, CORE_POS);

        player.getFoodData().setFoodLevel(20);

        QueenSniff.cast(player);

        expect(helper, "a queen without the node pays nothing for a sniff", player.getFoodData().getFoodLevel() == 20);
        expect(helper, "and nothing around her is outlined", !prey.hasEffect(MobEffects.GLOWING));

        QueenSkills.unlock(player, QueenSkills.QUEEN_SNIFF);
        QueenSniff.cast(player);

        expect(helper, "with the node the sniff costs its hunger", player.getFoodData().getFoodLevel() == 15);
        expect(helper, "and the creature beside her wears the outline", prey.hasEffect(MobEffects.GLOWING));

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST Q, R, S and T: the rules of a purchase

    /** <b>TEST Q, TEST R, TEST S and TEST T: one refusal at a time, and never a soul for a node that was refused.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void therulesofapurchase(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_purchase_rules");
        BlockPos core = openCore(helper, player);

        // TEST R: the level is missing, whatever the purse says.
        QueenLevels.setLevel(player, 4);
        Souls.setSouls(player, 10_000L);

        expect(helper, "a node above her level can not be bought even with all the souls in the world",
                buy(player, core, QueenSkills.QUEEN_STANCE) == ProgressionFeedback.SKILL_LOCKED);
        expect(helper, "and it cost nothing", Souls.getSouls(player) == 10_000L);

        // TEST Q: the purse is missing, which is the one refusal a click of an available node really answers.
        QueenLevels.setLevel(player, 10);
        Souls.setSouls(player, 999L);

        expect(helper, "a node she can not pay for is refused for the souls",
                buy(player, core, QueenSkills.QUEEN_STANCE) == ProgressionFeedback.INSUFFICIENT_SOULS);
        expect(helper, "and not one soul was taken", Souls.getSouls(player) == 999L);
        expect(helper, "and the node is still not hers", !QueenSkills.has(player, QueenSkills.QUEEN_STANCE));

        // The purchase itself, and the second request that must never be charged twice (TEST T).
        Souls.setSouls(player, 2_500L);

        expect(helper, "the request is answered with the bought node",
                buy(player, core, QueenSkills.QUEEN_STANCE) == ProgressionFeedback.SKILL_OK);
        expect(helper, "and it cost exactly its price", Souls.getSouls(player) == 1_500L);

        expect(helper, "a second request is answered as already owned",
                buy(player, core, QueenSkills.QUEEN_STANCE) == ProgressionFeedback.ALREADY_UNLOCKED);
        expect(helper, "and it did not cost a second soul", Souls.getSouls(player) == 1_500L);

        // A node that no data pack declares is not a node, and the request is refused as such.
        expect(helper, "a node that no data pack declares is refused",
                buy(player, core, ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "no_such_skill"))
                        == ProgressionFeedback.UNKNOWN_SKILL);

        // And nothing of this works without a real open core.
        closeCore(player);

        expect(helper, "a request without an open core is refused",
                buy(player, core, QueenSkills.CHITIN_SHELL) == ProgressionFeedback.DENIED);
        expect(helper, "and it did not cost anything either", Souls.getSouls(player) == 1_500L);

        helper.succeed();
    }

    // ------------------------------------------------------------------ TEST U and TEST V: what a node survives

    /** <b>TEST U and TEST V: a bought node survives the save file and the clone, and a revoke reaches the body.</b> */
    @GameTest(template = "humanspawn_flat", batch = "skill_progression", timeoutTicks = TIMEOUT_TICKS)
    public static void aboughtnodesurvivesthesave(GameTestHelper helper)
    {
        ServerPlayer player = fakePlayer(helper, "skills_persistence");
        BlockPos core = openCore(helper, player);

        QueenLevels.setLevel(player, 30);
        Souls.setSouls(player, 5_000L);

        expect(helper, "the stance is bought", buy(player, core, QueenSkills.QUEEN_STANCE)
                == ProgressionFeedback.SKILL_OK);
        expect(helper, "the sniff is bought", buy(player, core, QueenSkills.QUEEN_SNIFF)
                == ProgressionFeedback.SKILL_OK);
        expect(helper, "and they cost what they cost", Souls.getSouls(player) == 2_000L);

        // The save file: what a restart reads.
        CompoundTag saved = QueenDataCapability.get(player).map(QueenData::serializeNBT).orElseGet(CompoundTag::new);
        QueenData reloaded = new QueenData();
        reloaded.deserializeNBT(saved);

        expect(helper, "both nodes are written to the save file and read back",
                reloaded.isSkillUnlocked(QueenSkills.QUEEN_STANCE)
                        && reloaded.isSkillUnlocked(QueenSkills.QUEEN_SNIFF));

        // And the clone: what a death, a respawn and a dimension change copy.
        QueenData clone = new QueenData();
        QueenDataCapability.get(player).ifPresent(data -> clone.copyFrom(data));

        expect(helper, "a rebuilt player keeps her nodes", clone.isSkillUnlocked(QueenSkills.QUEEN_STANCE)
                && clone.isSkillUnlocked(QueenSkills.QUEEN_SNIFF));

        // TEST V: a revocation brings the running body in line at once, without a relog.
        player.getFoodData().setFoodLevel(20);
        EmpoweredAttackHandler.tryArm(player);

        expect(helper, "the stance really arms a strike", EmpoweredAttackHandler.isArmed(player));

        QueenSkills.revoke(player, QueenSkills.QUEEN_STANCE);
        QueenSkills.refreshRuntime(player);

        expect(helper, "the revoked stance can not keep a charge", !EmpoweredAttackHandler.isArmed(player));
        expect(helper, "and the revoked node is gone from her data",
                !QueenSkills.has(player, QueenSkills.QUEEN_STANCE));

        QueenData afterRevoke = new QueenData();
        QueenDataCapability.get(player).ifPresent(data -> afterRevoke.copyFrom(data));
        CompoundTag written = afterRevoke.serializeNBT();
        QueenData reread = new QueenData();
        reread.deserializeNBT(written);

        expect(helper, "the revoked node is not written to the save again either",
                !reread.isSkillUnlocked(QueenSkills.QUEEN_STANCE)
                        && reread.isSkillUnlocked(QueenSkills.QUEEN_SNIFF));

        closeCore(player);
        helper.succeed();
    }
}
