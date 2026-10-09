package spiderqueenhungrycraft.config;

import java.util.List;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Common configuration of the mod. Everything here is meant to be tweaked by modpack authors.
 */
public final class HungryCraftConfig
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue START_NEW_WORLD_AT_MIDNIGHT;
    public static final ForgeConfigSpec.IntValue INITIAL_OVERWORLD_TIME;

    public static final ForgeConfigSpec.BooleanValue VILLAGE_HOSTILITY_ENABLED;
    public static final ForgeConfigSpec.DoubleValue VILLAGE_DEFENDER_ATTACK_RANGE;
    public static final ForgeConfigSpec.DoubleValue VILLAGER_FEAR_RANGE;
    public static final ForgeConfigSpec.IntValue VILLAGER_FEAR_MEMORY_TICKS;
    public static final ForgeConfigSpec.BooleanValue INITIAL_VILLAGER_REPUTATION_PENALTY_ENABLED;
    public static final ForgeConfigSpec.IntValue INITIAL_VILLAGER_NEGATIVE_GOSSIP_VALUE;
    public static final ForgeConfigSpec.BooleanValue HERO_OF_THE_VILLAGE_DISABLES_VILLAGE_HOSTILITY;
    public static final ForgeConfigSpec.BooleanValue PLAYER_CREATED_GOLEMS_ATTACK_SPIDER_QUEEN;
    public static final ForgeConfigSpec.BooleanValue BROOD_SPIDERS_IGNORE_VILLAGERS;

    public static final ForgeConfigSpec.BooleanValue HUMAN_COMBATANT_IGNORES_MONSTERS;

    public static final ForgeConfigSpec.BooleanValue HUMAN_BAND_AGGRO;
    public static final ForgeConfigSpec.DoubleValue HUMAN_BAND_RADIUS;
    public static final ForgeConfigSpec.IntValue HUMAN_BAND_MAX_RESPONDERS;
    public static final ForgeConfigSpec.IntValue HUMAN_BAND_LOCK_TICKS;
    public static final ForgeConfigSpec.DoubleValue HUMAN_BAND_SWITCH_MARGIN;
    public static final ForgeConfigSpec.DoubleValue HUMAN_BAND_LEASH_RADIUS;
    public static final ForgeConfigSpec.IntValue HUMAN_BAND_PEACE_TICKS;
    public static final ForgeConfigSpec.IntValue HUMAN_BAND_PROVOKE_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.BooleanValue HUMAN_BAND_ANSWER_PROJECTILES;

    public static final ForgeConfigSpec.IntValue INFESTED_STEVE_GEAR_PICKUP_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.DoubleValue INFESTED_STEVE_OUT_OF_COMBAT_HEAL_RATIO;
    public static final ForgeConfigSpec.IntValue INFESTED_STEVE_OUT_OF_COMBAT_HEAL_COOLDOWN_TICKS;

    public static final ForgeConfigSpec.BooleanValue CAPTURE_ENABLED;
    public static final ForgeConfigSpec.IntValue COCOON_HITS_TO_PACK;

    public static final ForgeConfigSpec.BooleanValue EMPOWERED_ATTACK_ENABLED;
    public static final ForgeConfigSpec.IntValue EMPOWERED_ATTACK_WINDOW_TICKS;
    public static final ForgeConfigSpec.DoubleValue EMPOWERED_ATTACK_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue EMPOWERED_ATTACK_POISON_DURATION_TICKS;
    public static final ForgeConfigSpec.IntValue EMPOWERED_ATTACK_WITHER_DURATION_TICKS;

    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_SIDE;
    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_FORWARD;
    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_DROP;
    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_FIRST_PERSON_SIDE;
    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_FIRST_PERSON_DOWN;
    public static final ForgeConfigSpec.DoubleValue HOOK_ORIGIN_TIP_LIFT;
    public static final ForgeConfigSpec.IntValue HOOK_BREAK_TICKS;

    public static final ForgeConfigSpec.BooleanValue HOOK_TETHER_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HOOK_TETHER_DISTANCES;
    public static final ForgeConfigSpec.BooleanValue HOOK_DURABILITY_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> HOOK_DURABILITIES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HOOK_BLOCKS_PER_DAMAGE;

    public static final ForgeConfigSpec.BooleanValue HOOK_ENTITY_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HOOK_HEAVY_HEALTH;
    public static final ForgeConfigSpec.IntValue HOOK_ENTITY_HOLD_TICKS;
    public static final ForgeConfigSpec.DoubleValue HOOK_ENTITY_DRAG_SPEED;

    /**
     * Whether a dragon that HungryCraft has revived from an Ice and Fire bone dragon is still drawn with the skeleton
     * texture of that mod (Necromancy IV prototype 1). It is the one visual decision of the compatibility and the one
     * switch that turns its client hook off, which is also what makes the third probe of the round possible: with this
     * off, a revived dragon looks exactly like Ice and Fire alone would draw it.
     */
    public static final ForgeConfigSpec.BooleanValue ICE_AND_FIRE_SKELETON_TEXTURE;

    /**
     * Whether a dragon that HungryCraft has woken keeps the glowing eyes of Ice and Fire (Necromancy IV prototype 2).
     *
     * <p>Ice and Fire draws a glow-in-the-dark eye layer over the head of every dragon that is not a corpse, and it has
     * no separate skeleton eye texture - a woken dragon is therefore a skull with the living eyes of its stage on it.
     * That is the default on purpose: it is what makes a necromancy dragon read as an undead creature rather than as a
     * prop, and the prototype keeps it until somebody looks at it and says otherwise.
     *
     * <p>Set this to false to see the other answer: the eye layer then stands aside for a dragon this mod has woken,
     * and for nothing else, so a bare skull can be compared with a glowing one without rebuilding anything.
     */
    public static final ForgeConfigSpec.BooleanValue ICE_AND_FIRE_SKELETON_GLOWING_EYES;

    /**
     * Whether the dragon compatibility writes its diagnostics into the log (Necromancy IV round 5).
     *
     * <p>Off by default, and that is the whole point of it: a normal game stays silent, and a developer who is chasing
     * "why did my skeleton do that" turns this on, reads a handful of lines with the
     * {@link spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport#PROBE_LOG_PREFIX} marker in front of them, and
     * turns it off again. It is deliberately one line per <b>event</b> - a contract, a goal installation, an order, a
     * selection, a change of flight or sleep state - and never one line per tick; the state lines are written when the
     * state really changes and at no other time (see
     * {@link spiderqueenhungrycraft.compat.iceandfire.NecromancyDiagnostics}).
     */
    public static final ForgeConfigSpec.BooleanValue NECROMANCY_DIAGNOSTICS;

    public static final ForgeConfigSpec SPEC;

    static
    {
        BUILDER.comment("World rules of Spider Queen: Hungry Craft").push("world");
        START_NEW_WORLD_AT_MIDNIGHT = BUILDER
                .comment("Start a newly created Overworld at initial_overworld_time instead of the vanilla start time.",
                        "Only brand new worlds are touched, existing worlds and later time changes are never modified.")
                .define("start_new_world_at_midnight", true);
        INITIAL_OVERWORLD_TIME = BUILDER
                .comment("Day time of a new Overworld. 0 is sunrise, 18000 is midnight.")
                .defineInRange("initial_overworld_time", 18000, 0, 23999);
        BUILDER.pop();

        BUILDER.comment("How villages react to the Spider Queen").push("village");
        VILLAGE_HOSTILITY_ENABLED = BUILDER
                .comment("Master switch for village hostility: villager fear, village defenders and the initial",
                        "trading penalty. Turning this off leaves every vanilla village behaviour untouched.")
                .define("village_hostility_enabled", true);
        VILLAGE_DEFENDER_ATTACK_RANGE = BUILDER
                .comment("How far away a village defender (iron golem, guard, priest, ...) notices the Spider Queen.")
                .defineInRange("village_defender_attack_range", 24.0D, 1.0D, 128.0D);
        VILLAGER_FEAR_RANGE = BUILDER
                .comment("How far away a villager notices the Spider Queen.")
                .defineInRange("villager_fear_range", 12.0D, 1.0D, 64.0D);
        VILLAGER_FEAR_MEMORY_TICKS = BUILDER
                .comment("How long the vanilla panic memory of a scared villager stays valid. It is refreshed while",
                        "the Spider Queen stays close.")
                .defineInRange("villager_fear_memory_ticks", 100, 20, 6000);
        INITIAL_VILLAGER_REPUTATION_PENALTY_ENABLED = BUILDER
                .comment("Give every villager one negative gossip the first time it meets a Spider Queen without",
                        "Hero of the Village. Each villager and player pair is only handled once.")
                .define("initial_villager_reputation_penalty_enabled", true);
        INITIAL_VILLAGER_NEGATIVE_GOSSIP_VALUE = BUILDER
                .comment("Value of that gossip. Vanilla major negative gossip counts -5 reputation per point,",
                        "so the default of 10 means -50 reputation and therefore clearly higher prices.")
                .defineInRange("initial_villager_negative_gossip_value", 10, 1, 100);
        HERO_OF_THE_VILLAGE_DISABLES_VILLAGE_HOSTILITY = BUILDER
                .comment("Hero of the Village temporarily suppresses all Hungry Craft village hostility.",
                        "It never removes existing gossip and never blocks the vanilla reputation system.")
                .define("hero_of_the_village_disables_village_hostility", true);
        PLAYER_CREATED_GOLEMS_ATTACK_SPIDER_QUEEN = BUILDER
                .comment("Whether iron golems built by a player ignore the Spider Queen.",
                        "Vanilla player built golems never attack players, but the village is hostile to the queen,",
                        "so the faction design defaults to true here: a player built golem fights her like every",
                        "other village defender. Set it to false to keep the vanilla look of a calm golem.")
                .define("player_created_golems_attack_spider_queen", true);
        BROOD_SPIDERS_IGNORE_VILLAGERS = BUILDER
                .comment("Whether the spiders of a queen leave ordinary villagers alone on their own (Brood Villager",
                        "Neutrality pass). The villager stays a member of the village faction - this only takes it out of",
                        "the natural targeting of the brood, so an order of the sceptre, the help of a follower while its",
                        "queen fights a villager, and the self defence of a spider all still work. On by default, which is",
                        "what keeps the villager breeder and the raid farm of a redstone builder alive; turn it off and the",
                        "spiders hunt villagers like every other enemy of the queen.")
                .define("brood_spiders_ignore_villagers", true);
        BUILDER.pop();

        BUILDER.comment("Human units of the Hungry Craft war (Phase HF0A)").push("humans");
        HUMAN_COMBATANT_IGNORES_MONSTERS = BUILDER
                .comment("Take the monster hunt away from the standard human combatant. It is off by default, which",
                        "is what the design asks for: a human combatant hunts the Spider Queen and the monster",
                        "faction, and only ever leaves a normal player, a villager, a golem or his own kind alone.")
                .define("human_combatant_ignores_monsters", false);
        HUMAN_BAND_AGGRO = BUILDER
                .comment("Master switch of the shared anger of the human units (human pack aggro round). On by",
                        "default: when one human is hit - by a sword, an arrow or a web shot - the humans of his",
                        "group in reach answer, one enemy at a time. Off restores exactly the behaviour of the",
                        "rounds before this one, where every human decided his own target and nothing else.")
                .define("human_band_aggro", true);
        HUMAN_BAND_RADIUS = BUILDER
                .comment("How far the pack alert reaches around the human that was hit, in blocks. Only the",
                        "comrades inside this ball are written into, and only they may be armed with the enemy.")
                .defineInRange("human_band_radius", 16.0D, 1.0D, 64.0D);
        HUMAN_BAND_MAX_RESPONDERS = BUILDER
                .comment("How many comrades at most are armed with one provocation, nearest first. The rest are",
                        "alerted only: they turn, raise the shield and hold their ground. This is the number that",
                        "keeps two archers from dragging a whole field around; 0 arms nobody and leaves the victim",
                        "alone with his attacker.")
                .defineInRange("human_band_max_responders", 5, 0, 32);
        HUMAN_BAND_LOCK_TICKS = BUILDER
                .comment("How long an armed human stays committed to the enemy he was given, in ticks (20 ticks are",
                        "one second). Ten seconds by default: inside that window a new provocation is only recorded",
                        "and never moves him.")
                .defineInRange("human_band_lock_ticks", 200, 20, 72000);
        HUMAN_BAND_SWITCH_MARGIN = BUILDER
                .comment("How much more threatening a new attacker has to be than the one a human is already",
                        "fighting before that human turns around. 1.4 means 40% more threat. A melee attacker in his",
                        "face always wins, whatever this number says.")
                .defineInRange("human_band_switch_margin", 1.4D, 1.0D, 10.0D);
        HUMAN_BAND_LEASH_RADIUS = BUILDER
                .comment("How far from his post a human may be dragged before the fight ends for him, in blocks. He",
                        "drops the target, takes a short rest and walks back to his post. This is what turns a",
                        "kiting archer into a nuisance instead of a shepherd; 0 switches the leash off.")
                .defineInRange("human_band_leash_radius", 12.0D, 0.0D, 128.0D);
        HUMAN_BAND_PEACE_TICKS = BUILDER
                .comment("How long a human looks for no new enemy after a fight, in ticks. Only a remembered",
                        "attacker may start the next one inside this window.")
                .defineInRange("human_band_peace_ticks", 40, 0, 1200);
        HUMAN_BAND_PROVOKE_COOLDOWN_TICKS = BUILDER
                .comment("How often the same attacker may alert the pack of the same victim, in ticks. A volley of",
                        "arrows or webs is one event, not ten.")
                .defineInRange("human_band_provoke_cooldown_ticks", 40, 0, 1200);
        HUMAN_BAND_ANSWER_PROJECTILES = BUILDER
                .comment("Whether an arrow or a web shot from afar may pull free humans into a fight at all. On by",
                        "default - a soldier answers a shot when he has nothing else to do - and off is the choice of",
                        "a pack whose soldiers should only hold the line against archers.")
                .define("human_band_answer_projectiles", true);
        BUILDER.pop();

        BUILDER.comment("The infested Steve, the body of a digested human (gear loop + golden apple round)")
                .push("infested_steve");
        INFESTED_STEVE_GEAR_PICKUP_COOLDOWN_TICKS = BUILDER
                .comment("How long one place of gear refuses a new pickup after a piece was really put there, in ticks",
                        "(20 ticks are one second). Two seconds by default. A body that drops the piece it replaced would",
                        "otherwise re-take it for ever, because the dropped piece lands inside its own reach; the rule",
                        "that truly ends that loop is the vanilla 'only a better piece replaces a worn one' comparison,",
                        "and this cooldown is the second belt that keeps a pile of equal pieces from making it flicker.",
                        "0 switches the cooldown off, which restores exactly the vanilla pickup delay of ten ticks.")
                .defineInRange("gear_pickup_cooldown_ticks", 40, 0, 1200);
        INFESTED_STEVE_OUT_OF_COMBAT_HEAL_RATIO = BUILDER
                .comment("Below this share of its maximum health a body that is NOT fighting eats one of the golden apples",
                        "it carries, without anybody telling it to. 0.5 is half of its health, which is what lets a body",
                        "walk away from a fight and patch itself up on its own. The fight of the body keeps its own lower",
                        "thresholds (the shared human fight eats below 35% and, at a critical 20%, even while standing).",
                        "Set it to 0.0 to switch the out of combat medicine off completely.")
                .defineInRange("out_of_combat_heal_ratio", 0.5D, 0.0D, 1.0D);
        INFESTED_STEVE_OUT_OF_COMBAT_HEAL_COOLDOWN_TICKS = BUILDER
                .comment("How long the body waits after a bite before it may top itself up again, in ticks. Ten seconds by",
                        "default, the same rest the fight of a human body takes between two golden apples.")
                .defineInRange("out_of_combat_heal_cooldown_ticks", 200, 0, 72000);
        BUILDER.pop();

        BUILDER.comment("Capture, cocoons and the empowered melee attack").push("hunting");
        CAPTURE_ENABLED = BUILDER
                .comment("Master switch for capturing weakened, webbed prey with a web shot.")
                .define("capture_enabled", true);
        COCOON_HITS_TO_PACK = BUILDER
                .comment("How many player attacks turn a cocoon entity into a cocoon item. This is a",
                        "packing counter, it never deals damage to the creature inside.")
                .defineInRange("cocoon_hits_to_pack", 3, 1, 20);
        EMPOWERED_ATTACK_ENABLED = BUILDER
                .comment("Allow the Spider Queen to arm one empowered melee attack.")
                .define("empowered_attack_enabled", true);
        EMPOWERED_ATTACK_WINDOW_TICKS = BUILDER
                .comment("How long the armed empowered attack waits for the next melee hit.")
                .defineInRange("empowered_attack_window_ticks", 100, 10, 1200);
        EMPOWERED_ATTACK_DAMAGE_MULTIPLIER = BUILDER
                .comment("Damage multiplier of the empowered melee hit.")
                .defineInRange("empowered_attack_damage_multiplier", 2.0D, 1.0D, 10.0D);
        EMPOWERED_ATTACK_POISON_DURATION_TICKS = BUILDER
                .comment("Poison duration of the empowered hit, amplifier is always Poison II.")
                .defineInRange("empowered_attack_poison_duration_ticks", 100, 20, 1200);
        EMPOWERED_ATTACK_WITHER_DURATION_TICKS = BUILDER
                .comment("Wither duration of the empowered hit, amplifier is always Wither II.")
                .defineInRange("empowered_attack_wither_duration_ticks", 100, 20, 1200);
        BUILDER.pop();

        BUILDER.comment("The spider hook rod (spider_hook, iron_spider_hook, golden_spider_hook, diamond_spider_hook,",
                        "netherite_spider_hook). The three origin values place the tip of the rod in the world: the",
                        "web head leaves from it, the silk is drawn from it, and it is the point the rod is held by.",
                        "Lower origin_drop to fire lower, raise it to fire higher.")
                .push("spider_hook");
        HOOK_ORIGIN_SIDE = BUILDER
                .comment("How far to the side of the body the rod tip sits, in blocks. Positive is to the side of",
                        "the hand that holds the rod.")
                .defineInRange("origin_side", 0.35D, 0.0D, 2.0D);
        HOOK_ORIGIN_FORWARD = BUILDER
                .comment("How far in front of the body the rod tip sits, in blocks.")
                .defineInRange("origin_forward", 0.8D, 0.0D, 3.0D);
        HOOK_ORIGIN_DROP = BUILDER
                .comment("How far below the eyes the rod tip sits, in blocks. This is the height the web head",
                        "leaves the hand at.")
                .defineInRange("origin_drop", 0.30D, -0.5D, 2.0D);
        HOOK_ORIGIN_FIRST_PERSON_SIDE = BUILDER
                .comment("First person only: how far to the side of the middle of the screen the holder takes his",
                        "own rod tip to be, measured on the near plane of his camera. This is where the silk",
                        "starts in his own hand, so it has to meet the rod the item model shows there.")
                .defineInRange("origin_first_person_side", 0.525D, 0.0D, 2.0D);
        HOOK_ORIGIN_FIRST_PERSON_DOWN = BUILDER
                .comment("First person only: how far below the middle of the screen that same tip is taken to be.",
                        "Lower this number to let the silk start lower on the rod of the holder.")
                .defineInRange("origin_first_person_down", -0.25D, -2.0D, 2.0D);
        HOOK_ORIGIN_TIP_LIFT = BUILDER
                .comment("How far above the rod tip the silk is drawn, in blocks. The vanilla fishing rod uses",
                        "0.25 for its line, which lets the thread hang a little above the tip; zero makes the",
                        "silk leave exactly at the tip.")
                .defineInRange("origin_tip_lift", 0.10D, -0.5D, 1.0D);
        HOOK_BREAK_TICKS = BUILDER
                .comment("The break a rod takes once its casts in a row are used up, in ticks. The break is the",
                        "same for all five qualities; how many casts a rod takes in a row is the part that",
                        "differs between them. 20 ticks are one second, so 50 is two and a half seconds.")
                .defineInRange("break_ticks", 50, 0, 2400);
        HOOK_TETHER_ENABLED = BUILDER
                .comment("Master switch for the reach of the silk. With this off a web may be dragged in from any",
                        "distance, which is fun but leaves the five rods far behind what they are priced at.")
                .define("tether_enabled", true);
        HOOK_TETHER_DISTANCES = BUILDER
                .comment("How far the owner may get from his own anchor before the silk snaps, one value per",
                        "quality, in the order plain, iron, golden, diamond, netherite. A web that is out of",
                        "reach lets go by itself, and that finished cast still counts as one use of the rod.")
                .defineList("tether_distances", List.of(24.0D, 40.0D, 64.0D, 96.0D, 128.0D),
                        value -> value instanceof Double distance && distance > 0.0D && distance <= 256.0D);
        HOOK_DURABILITY_ENABLED = BUILDER
                .comment("Master switch for the wear of the silk. With this off a rod never takes any damage.")
                .define("durability_enabled", true);
        HOOK_DURABILITIES = BUILDER
                .comment("Durability of the five rods, in the order plain, iron, golden, diamond, netherite.")
                .defineList("durabilities", List.of(128, 256, 128, 512, 1024),
                        value -> value instanceof Integer durability && durability > 0);
        HOOK_BLOCKS_PER_DAMAGE = BUILDER
                .comment("How many blocks of being dragged cost one point of durability, one value per quality.",
                        "The wear of a finished pull is ceil(travelled blocks / this), so a cast that never hung",
                        "on a block and a web that was never pulled cost nothing at all.")
                .defineList("blocks_per_damage", List.of(3.0D, 5.0D, 8.0D, 12.0D, 16.0D),
                        value -> value instanceof Double blocks && blocks > 0.0D);
        HOOK_ENTITY_ENABLED = BUILDER
                .comment("Master switch for catching creatures with the web head. With this off the hook only",
                        "ever catches blocks, exactly as it did before the creatures were added.")
                .define("entity_hook_enabled", true);
        HOOK_HEAVY_HEALTH = BUILDER
                .comment("From which maximum health on a caught being counts as heavy: the web drags its thrower",
                        "to a heavy being and drags a light one to the thrower. 30 puts an iron golem and every",
                        "boss on the heavy side and a zombie, a creeper or a cow on the light one.")
                .defineInRange("heavy_health", 30.0D, 0.0D, 1024.0D);
        HOOK_ENTITY_HOLD_TICKS = BUILDER
                .comment("How long a caught being may stay caught before the web comes home on its own, in ticks.")
                .defineInRange("entity_hold_ticks", 1200, 20, 24000);
        HOOK_ENTITY_DRAG_SPEED = BUILDER
                .comment("How fast a light being is dragged towards its catcher, in blocks per tick. The pull is",
                        "added on top of what the being does itself and the sum is capped, so nothing is flung.")
                .defineInRange("entity_drag_speed", 0.6D, 0.05D, 4.0D);
        BUILDER.pop();

        // Optional compatibility (Necromancy IV prototype 1). Its own section, because everything in it is only ever
        // read in a game that has another mod installed.
        BUILDER.comment("Optional compatibility with other mods. Nothing here does anything while the mod it speaks about",
                        "is not installed.").push("compat");
        ICE_AND_FIRE_SKELETON_TEXTURE = BUILDER
                .comment("Ice and Fire only. When HungryCraft wakes a bone dragon up, Ice and Fire would draw it as a",
                        "living dragon again, because the very flag that draws bones is the flag that stops the animal.",
                        "This switch puts the skeleton texture back on a dragon that HungryCraft has revived, so that a",
                        "woken dragon still looks like the bones it is (the model, the animations and the flight stay",
                        "Ice and Fire's own).",
                        "Turn it off to see, and to test, what Ice and Fire alone would draw.")
                .define("ice_and_fire_skeleton_texture", true);
        ICE_AND_FIRE_SKELETON_GLOWING_EYES = BUILDER
                .comment("Ice and Fire only. That mod draws a glow-in-the-dark eye layer over the head of every dragon",
                        "that is not a corpse, and it has no separate skeleton eye texture, so a dragon HungryCraft has",
                        "woken is a skull with the living, glowing eyes of its growth stage on it.",
                        "true (the default) keeps them: that is what makes a necromancy dragon read as an undead",
                        "creature rather than as a prop, and it is what Ice and Fire itself would draw.",
                        "false hides the eye layer for a dragon HungryCraft has woken - and for nothing else - so the",
                        "other answer can be compared without rebuilding anything.")
                .define("ice_and_fire_skeleton_glowing_eyes", true);
        NECROMANCY_DIAGNOSTICS = BUILDER
                .comment("Ice and Fire only. Writes a few lines per event about the dragons under contract into the",
                        "log, all of them marked with [HungryCraft/IceFireProbe]: contract created, goals installed,",
                        "orders written (MOVE, HOLD, BOMBARD), how a HOLD interrupted an older errand, how many units",
                        "a selection found and how many it took, and every CHANGE of flight/hover/sleep state of a",
                        "skeleton.",
                        "Off by default. It never writes a line per tick - only when something really happened - so it",
                        "is safe to leave on while reproducing a bug, and it is the switch a developer should turn on",
                        "before reporting one.")
                .define("necromancy_diagnostics", false);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private HungryCraftConfig()
    {
    }

    public static boolean startNewWorldAtMidnight()
    {
        return START_NEW_WORLD_AT_MIDNIGHT.get();
    }

    public static int initialOverworldTime()
    {
        return INITIAL_OVERWORLD_TIME.get();
    }

    public static boolean villageHostilityEnabled()
    {
        return VILLAGE_HOSTILITY_ENABLED.get();
    }

    public static double villageDefenderAttackRange()
    {
        return VILLAGE_DEFENDER_ATTACK_RANGE.get();
    }

    public static double villagerFearRange()
    {
        return VILLAGER_FEAR_RANGE.get();
    }

    public static int villagerFearMemoryTicks()
    {
        return VILLAGER_FEAR_MEMORY_TICKS.get();
    }

    public static boolean initialVillagerReputationPenaltyEnabled()
    {
        return INITIAL_VILLAGER_REPUTATION_PENALTY_ENABLED.get();
    }

    public static int initialVillagerNegativeGossipValue()
    {
        return INITIAL_VILLAGER_NEGATIVE_GOSSIP_VALUE.get();
    }

    public static boolean heroOfTheVillageDisablesVillageHostility()
    {
        return HERO_OF_THE_VILLAGE_DISABLES_VILLAGE_HOSTILITY.get();
    }

    public static boolean playerCreatedGolemsAttackSpiderQueen()
    {
        return PLAYER_CREATED_GOLEMS_ATTACK_SPIDER_QUEEN.get();
    }

    /**
     * Master switch of the villager neutrality of the brood (Brood Villager Neutrality pass). On by default: a spider of
     * the queen leaves an ordinary villager alone on its own and only ever attacks one when it was told to. Turning it
     * off restores the plain faction behaviour - the queen side is at war with the village, so a spider hunts a villager
     * like any other creature of the village.
     */
    public static boolean broodSpidersIgnoreVillagers()
    {
        return BROOD_SPIDERS_IGNORE_VILLAGERS.get();
    }

    /**
     * Master switch of the monster hunt of a human combatant (Phase HF0B). Off by default, which means a combatant
     * hunts the monster faction; turning it on leaves him with the queen as his only target.
     */
    public static boolean humanCombatantIgnoresMonsters()
    {
        return HUMAN_COMBATANT_IGNORES_MONSTERS.get();
    }

    /** @return true while the shared anger of the human units is switched on (human pack aggro round). */
    public static boolean humanBandAggro()
    {
        return HUMAN_BAND_AGGRO.get();
    }

    /** @return how far a pack alert reaches around the human that was hit, in blocks. */
    public static double humanBandRadius()
    {
        return HUMAN_BAND_RADIUS.get();
    }

    /** @return how many comrades at most are armed with one provocation, nearest first. */
    public static int humanBandMaxResponders()
    {
        return HUMAN_BAND_MAX_RESPONDERS.get();
    }

    /** @return how long an armed human stays committed to his enemy, in ticks. */
    public static int humanBandLockTicks()
    {
        return HUMAN_BAND_LOCK_TICKS.get();
    }

    /** @return how much more threatening a new attacker has to be before a human turns around. */
    public static double humanBandSwitchMargin()
    {
        return HUMAN_BAND_SWITCH_MARGIN.get();
    }

    /** @return how far a human may be dragged from his post before the fight ends for him, 0 for no leash. */
    public static double humanBandLeashRadius()
    {
        return HUMAN_BAND_LEASH_RADIUS.get();
    }

    /** @return how long a human looks for no new enemy after a fight, in ticks. */
    public static int humanBandPeaceTicks()
    {
        return HUMAN_BAND_PEACE_TICKS.get();
    }

    /** @return how often the same attacker may alert the pack of the same victim, in ticks. */
    public static int humanBandProvokeCooldownTicks()
    {
        return HUMAN_BAND_PROVOKE_COOLDOWN_TICKS.get();
    }

    /** @return true while arrows and web shots from afar may pull free humans into a fight. */
    public static boolean humanBandAnswerProjectiles()
    {
        return HUMAN_BAND_ANSWER_PROJECTILES.get();
    }

    /**
     * @return the ticks one place of gear of an infested Steve refuses a new pickup after a piece was put there (gear
     *         loop fix). Two seconds by default.
     */
    public static int infestedSteveGearPickupCooldownTicks()
    {
        return INFESTED_STEVE_GEAR_PICKUP_COOLDOWN_TICKS.get();
    }

    /**
     * @return the share of its maximum health below which an infested Steve that is not fighting eats one of its own
     *         golden apples (golden apple round). Half of its health by default, 0.0 for off.
     */
    public static float infestedSteveOutOfCombatHealRatio()
    {
        return INFESTED_STEVE_OUT_OF_COMBAT_HEAL_RATIO.get().floatValue();
    }

    /** @return the ticks an infested Steve waits after a bite before it may top itself up again. Ten seconds. */
    public static int infestedSteveOutOfCombatHealCooldownTicks()
    {
        return INFESTED_STEVE_OUT_OF_COMBAT_HEAL_COOLDOWN_TICKS.get();
    }

    public static boolean captureEnabled()
    {
        return CAPTURE_ENABLED.get();
    }

    public static int cocoonHitsToPack()
    {
        return COCOON_HITS_TO_PACK.get();
    }

    public static boolean empoweredAttackEnabled()
    {
        return EMPOWERED_ATTACK_ENABLED.get();
    }

    public static int empoweredAttackWindowTicks()
    {
        return EMPOWERED_ATTACK_WINDOW_TICKS.get();
    }

    public static double empoweredAttackDamageMultiplier()
    {
        return EMPOWERED_ATTACK_DAMAGE_MULTIPLIER.get();
    }

    public static int empoweredAttackPoisonDurationTicks()
    {
        return EMPOWERED_ATTACK_POISON_DURATION_TICKS.get();
    }

    public static int empoweredAttackWitherDurationTicks()
    {
        return EMPOWERED_ATTACK_WITHER_DURATION_TICKS.get();
    }

    /** @return how far to the side of the body the tip of a held spider hook sits, in blocks. */
    public static double hookOriginSide()
    {
        return HOOK_ORIGIN_SIDE.get();
    }

    /** @return how far in front of the body the tip of a held spider hook sits, in blocks. */
    public static double hookOriginForward()
    {
        return HOOK_ORIGIN_FORWARD.get();
    }

    /** @return how far below the eyes the tip of a held spider hook sits, in blocks. */
    public static double hookOriginDrop()
    {
        return HOOK_ORIGIN_DROP.get();
    }

    /** @return first person: how far to the side of the middle of the screen the own rod tip is taken to be. */
    public static double hookOriginFirstPersonSide()
    {
        return HOOK_ORIGIN_FIRST_PERSON_SIDE.get();
    }

    /** @return first person: how far below the middle of the screen that same tip is taken to be. */
    public static double hookOriginFirstPersonDown()
    {
        return HOOK_ORIGIN_FIRST_PERSON_DOWN.get();
    }

    /** @return how far above the rod tip the silk is drawn, in blocks. */
    public static double hookOriginTipLift()
    {
        return HOOK_ORIGIN_TIP_LIFT.get();
    }

    /** @return the length of the break of a spider hook rod, in ticks. */
    public static int hookBreakTicks()
    {
        return HOOK_BREAK_TICKS.get();
    }

    /** @return true while a web may be dragged in from any distance the owner likes. */
    public static boolean hookTetherEnabled()
    {
        return !SPEC.isLoaded() || HOOK_TETHER_ENABLED.get();
    }

    /**
     * @return how far the owner may get from his anchor before the silk snaps, in blocks. The list in the
     *         configuration wins; a list that is too short falls back to the table of the qualities.
     */
    public static double hookTetherDistance(int ordinal, double fallback)
    {
        if (!SPEC.isLoaded())
        {
            return fallback;
        }

        List<? extends Double> list = HOOK_TETHER_DISTANCES.get();

        if (ordinal < 0 || ordinal >= list.size())
        {
            return fallback;
        }

        Double value = list.get(ordinal);

        return value == null || value <= 0.0D ? fallback : value.doubleValue();
    }

    /** @return true while a rod takes damage for what it drags. */
    public static boolean hookDurabilityEnabled()
    {
        return !SPEC.isLoaded() || HOOK_DURABILITY_ENABLED.get();
    }

    /** @return the durability of one quality, read from the list, with the table as fallback. */
    public static int hookDurability(int ordinal, int fallback)
    {
        if (!SPEC.isLoaded())
        {
            return fallback;
        }

        List<? extends Integer> list = HOOK_DURABILITIES.get();

        if (ordinal < 0 || ordinal >= list.size())
        {
            return fallback;
        }

        Integer value = list.get(ordinal);

        return value == null || value <= 0 ? fallback : value.intValue();
    }

    /** @return how many blocks of being dragged cost one point of durability, table as fallback. */
    public static double hookBlocksPerDamage(int ordinal, double fallback)
    {
        if (!SPEC.isLoaded())
        {
            return fallback;
        }

        List<? extends Double> list = HOOK_BLOCKS_PER_DAMAGE.get();

        if (ordinal < 0 || ordinal >= list.size())
        {
            return fallback;
        }

        Double value = list.get(ordinal);

        return value == null || value <= 0.0D ? fallback : value.doubleValue();
    }

    /** @return true while the web head may catch a creature and not only a block. */
    public static boolean hookEntityEnabled()
    {
        return !SPEC.isLoaded() || HOOK_ENTITY_ENABLED.get();
    }

    /** @return the maximum health from which on a caught being is heavy and drags its thrower instead. */
    public static double hookHeavyHealth()
    {
        return !SPEC.isLoaded() ? 30.0D : HOOK_HEAVY_HEALTH.get();
    }

    /** @return how long a caught being may stay caught before the web comes home on its own, in ticks. */
    public static int hookEntityHoldTicks()
    {
        return !SPEC.isLoaded() ? 1200 : HOOK_ENTITY_HOLD_TICKS.get();
    }

    /** @return how fast a light being is dragged towards its catcher, in blocks per tick. */
    public static double hookEntityDragSpeed()
    {
        return !SPEC.isLoaded() ? 0.6D : HOOK_ENTITY_DRAG_SPEED.get();
    }

    /**
     * @return true while a dragon that HungryCraft revived is drawn with the skeleton texture of Ice and Fire.
     *
     *         <p>It is asked by the client hook of the compatibility, so it is the client's own configuration file that
     *         decides - which is exactly what a purely visual switch should be. An unloaded configuration answers true,
     *         because "no configuration file" is the normal state of a first launch and the interesting behaviour is
     *         the one that keeps a woken dragon looking like bones.
     */
    public static boolean iceAndFireSkeletonTexture()
    {
        return !SPEC.isLoaded() || ICE_AND_FIRE_SKELETON_TEXTURE.get();
    }

    /**
     * @return true while a dragon that HungryCraft revived keeps the glowing eye layer of Ice and Fire.
     *
     *         <p>It is the second visual switch of the compatibility and it is <b>true</b> by default, because that is
     *         the look Ice and Fire itself produces for a living dragon and the one a necromancer most likely wants:
     *         two burning eyes in a skull. A pack that prefers a bare skull turns it off and gets the other answer for
     *         exactly the same dragons.
     */
    public static boolean iceAndFireSkeletonGlowingEyes()
    {
        return !SPEC.isLoaded() || ICE_AND_FIRE_SKELETON_GLOWING_EYES.get();
    }

    /**
     * @return true while the dragon compatibility should write its diagnostics.
     *
     *         <p>It is the one accessor of this class that answers <b>false</b> for a configuration that is not loaded
     *         yet, and deliberately so: a missing configuration file is the normal state of a first launch, and the
     *         quiet answer is the right one for a log. A developer who wants the lines turns the switch on and gets
     *         them at the next moment when a dragon does something.
     */
    public static boolean necromancyDiagnostics()
    {
        return SPEC.isLoaded() && NECROMANCY_DIAGNOSTICS.get();
    }
}
