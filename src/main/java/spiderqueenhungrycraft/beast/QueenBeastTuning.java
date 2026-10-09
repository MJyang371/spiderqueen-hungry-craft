package spiderqueenhungrycraft.beast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The datapack tuning of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>Three groups of numbers are no longer baked into the bytecode: the armor bonus of the chitin, the three
 * sizes of the bite volume and the five values of the bite camera impulse. They live in
 * {@code data/spiderqueen_hungrycraft/queen_beast/tuning.json} and a modpack simply ships its own copy of
 * that file. {@code /reload} parses it again, and a beast that is already transformed receives the new armor
 * immediately, without leaving and re-entering the form.
 *
 * <p>Only the server ever reads this class: the armor bonus and the bite volume decide real gameplay, so they
 * must never be taken from a client. The camera values are handed to the clients with a packet and cached
 * there in a separate client object, so one shared static can never mix a client guess into an authoritative
 * hit test.
 *
 * <p>Every field is optional. A file that only changes the armor keeps the default bite and camera values, so
 * a small pack tweak stays a small pack tweak.
 */
public final class QueenBeastTuning
{
    /** Folder the tuning file is read from, inside the data folder of any namespace. */
    public static final String DIRECTORY = "queen_beast";

    /** Id of the file this mod ships and of the file a pack is expected to override. */
    public static final ResourceLocation TUNING_ID =
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "tuning");

    /**
     * One complete set of tunable values. Immutable, so a reload can swap it atomically and a bite that is
     * already running keeps the values it started with.
     *
     * @param armorBonus armor the beast form adds on top of everything else
     * @param biteWidth  width of the bite volume, perpendicular to where the beast looks
     * @param biteHeight height of the bite volume, from the feet of the beast upwards
     * @param biteDepth  depth of the bite volume, from the front edge of the beast body outwards
     * @param shakeTicks duration of the first person bite impulse, in ticks
     * @param shakePitch peak pitch of that impulse, in degrees
     * @param shakeYaw   peak yaw of that impulse, in degrees
     * @param shakeRoll  peak roll of that impulse, in degrees
     * @param shakeFov   field of view the bite pulls in at its peak, in degrees
     */
    public record Values(double armorBonus, double biteWidth, double biteHeight, double biteDepth,
            int shakeTicks, float shakePitch, float shakeYaw, float shakeRoll, double shakeFov,
            Jump jump, Stomp stomp, Charge charge, Breaking breaking)
    {
        /** The values the game uses when no tuning file is present at all. */
        public static Values defaults()
        {
            return new Values(QueenBeastConstants.BEAST_ARMOR_BONUS, QueenBeastConstants.BEAST_BITE_WIDTH,
                    QueenBeastConstants.BEAST_BITE_HEIGHT, QueenBeastConstants.BEAST_BITE_DEPTH,
                    QueenBeastConstants.BEAST_BITE_SHAKE_TICKS, QueenBeastConstants.BEAST_BITE_SHAKE_PITCH,
                    QueenBeastConstants.BEAST_BITE_SHAKE_YAW, QueenBeastConstants.BEAST_BITE_SHAKE_ROLL,
                    QueenBeastConstants.BEAST_BITE_SHAKE_FOV, Jump.defaults(), Stomp.defaults(),
                    Charge.defaults(), Breaking.defaults());
        }
    }

    /**
     * The charged jump of the beast (Phase 8C).
     *
     * @param maxChargeTicks  ticks of held jump key that count as a full charge
     * @param minStompCharge  charge below which the jump stays a hop and never arms a stomp
     * @param minVertical     vertical velocity of the weakest jump, in blocks per tick
     * @param maxVertical     vertical velocity of a fully charged jump, in blocks per tick
     * @param forwardVelocity forward velocity added to every beast jump, in blocks per tick
     */
    public record Jump(int maxChargeTicks, float minStompCharge, double minVertical, double maxVertical,
            double forwardVelocity)
    {
        public static Jump defaults()
        {
            return new Jump(QueenBeastConstants.BEAST_JUMP_MAX_CHARGE_TICKS,
                    QueenBeastConstants.BEAST_JUMP_MIN_STOMP_CHARGE,
                    QueenBeastConstants.BEAST_JUMP_MIN_VERTICAL_VELOCITY,
                    QueenBeastConstants.BEAST_JUMP_MAX_VERTICAL_VELOCITY,
                    QueenBeastConstants.BEAST_JUMP_FORWARD_VELOCITY);
        }
    }

    /**
     * The stomp of the beast (Phase 8C): the area attack of a landing, plus the heavier impulse of the camera
     * of the landing player.
     *
     * @param damage        physical damage against every legal target in the radius
     * @param radius        horizontal radius in blocks
     * @param verticalRange vertical range above and below the landing point, in blocks
     * @param knockUp       upward impulse of every victim
     * @param knockOutward  outward impulse of every victim, away from the landing point
     * @param shakeTicks    duration of the first person impulse in ticks
     * @param shakePitch    peak pitch of that impulse in degrees
     * @param shakeYaw      peak yaw of that impulse in degrees
     * @param shakeRoll     peak roll of that impulse in degrees
     * @param shakeFov      field of view punch of the landing in degrees
     */
    public record Stomp(double damage, double radius, double verticalRange, double knockUp, double knockOutward,
            int shakeTicks, float shakePitch, float shakeYaw, float shakeRoll, double shakeFov)
    {
        public static Stomp defaults()
        {
            return new Stomp(QueenBeastConstants.BEAST_STOMP_DAMAGE, QueenBeastConstants.BEAST_STOMP_RADIUS,
                    QueenBeastConstants.BEAST_STOMP_VERTICAL_RANGE, QueenBeastConstants.BEAST_STOMP_KNOCK_UP,
                    QueenBeastConstants.BEAST_STOMP_KNOCK_OUTWARD, QueenBeastConstants.BEAST_STOMP_SHAKE_TICKS,
                    QueenBeastConstants.BEAST_STOMP_SHAKE_PITCH, QueenBeastConstants.BEAST_STOMP_SHAKE_YAW,
                    QueenBeastConstants.BEAST_STOMP_SHAKE_ROLL, QueenBeastConstants.BEAST_STOMP_SHAKE_FOV);
        }
    }

    /**
     * The continuous charge of the beast (Phase 8D).
     *
     * @param speed          speed of the charge in blocks per tick
     * @param foodInterval   active charge ticks per food interval
     * @param foodCost       food levels per interval, one by default
     * @param damage         physical damage of one charge impact
     * @param knockUp        upward impulse of a charge victim
     * @param knockForward   forward impulse of a charge victim, along the charge direction
     * @param maxTurnPerTick how far the charge direction may turn towards the look per tick, in degrees
     * @param explosionPower power of the real explosion of a hard impact, about one block of TNT
     */
    public record Charge(double speed, int foodInterval, int foodCost, double damage, double knockUp,
            double knockForward, double maxTurnPerTick, double explosionPower)
    {
        public static Charge defaults()
        {
            return new Charge(QueenBeastConstants.BEAST_CHARGE_SPEED,
                    QueenBeastConstants.BEAST_CHARGE_FOOD_INTERVAL_TICKS,
                    QueenBeastConstants.BEAST_CHARGE_FOOD_PER_INTERVAL, QueenBeastConstants.BEAST_CHARGE_DAMAGE,
                    QueenBeastConstants.BEAST_CHARGE_KNOCK_UP, QueenBeastConstants.BEAST_CHARGE_KNOCK_FORWARD,
                    QueenBeastConstants.BEAST_CHARGE_MAX_TURN_PER_TICK,
                    QueenBeastConstants.BEAST_CHARGE_HARD_IMPACT_EXPLOSION_POWER);
        }
    }

    /**
     * One block rule of one ability (Phase 8C / 8D). The very same rule object serves the matcher of every
     * ability, so a pack changes what the clearance, the body crush, the stomp or the charge may destroy in
     * one place instead of four hardcoded copies.
     *
     * @param maxHardness        everything at or below this hardness may be broken
     * @param hardnessReference  a block whose own hardness is the limit instead, usually obsidian, or null
     * @param dropBlocks         whether the broken blocks really drop their loot
     * @param allowBlockEntities whether blocks with stored data (chests, machines) may be broken
     * @param allowBlocks        blocks that may always be broken, whatever the hardness says
     * @param allowTags          tags whose blocks may always be broken
     * @param protectedBlocks    blocks that are never broken, the strongest rule of all
     * @param protectedTags      tags that are never broken
     */
    public record Rule(double maxHardness, Block hardnessReference, boolean dropBlocks,
            boolean allowBlockEntities, Set<Block> allowBlocks, List<TagKey<Block>> allowTags,
            Set<Block> protectedBlocks, List<TagKey<Block>> protectedTags)
    {
    }

    /**
     * The four block rules of the beast (Phase 8C / 8D): the transformation clearance, the body crush of an
     * ordinary walk, the stomp of a landing and the charge of a hard run.
     */
    public record Breaking(Rule transformClearance, Rule bodyCrush, Rule stomp, Rule charge)
    {
        public static Breaking defaults()
        {
            return new Breaking(QueenBeastBlockRules.builtInDefaults(RuleProfile.TRANSFORM_CLEARANCE),
                    QueenBeastBlockRules.builtInDefaults(RuleProfile.BODY_CRUSH),
                    QueenBeastBlockRules.builtInDefaults(RuleProfile.STOMP),
                    QueenBeastBlockRules.builtInDefaults(RuleProfile.CHARGE));
        }
    }

    /**
     * The four abilities that break blocks. The enum is the bridge between the json section and the matcher,
     * so a new ability only has to be added here and in the rule matcher.
     */
    public enum RuleProfile
    {
        TRANSFORM_CLEARANCE("transform_clearance"),
        BODY_CRUSH("body_crush"),
        STOMP("stomp"),
        CHARGE("charge");

        private final String jsonName;

        RuleProfile(String jsonName)
        {
            this.jsonName = jsonName;
        }

        public String jsonName()
        {
            return this.jsonName;
        }
    }

    /** @return the rule of one ability of the current reload. */
    public static Rule rule(RuleProfile profile)
    {
        Breaking breaking = values.breaking();

        return switch (profile)
        {
            case TRANSFORM_CLEARANCE -> breaking.transformClearance();
            case BODY_CRUSH -> breaking.bodyCrush();
            case STOMP -> breaking.stomp();
            case CHARGE -> breaking.charge();
        };
    }

    private static final double MIN_BITE_SIZE = 0.5D;
    private static final int MIN_SHAKE_TICKS = 1;
    private static final int MAX_SHAKE_TICKS = 40;
    private static final int MIN_CHARGE_TICKS = 1;
    private static final int MAX_CHARGE_TICKS = 60;
    private static final double MIN_STOMP_RADIUS = 0.5D;
    private static final double MAX_STOMP_RADIUS = 12.0D;
    private static final double MIN_CHARGE_SPEED = 0.1D;
    private static final double MAX_CHARGE_SPEED = 3.0D;
    private static final double MAX_DAMAGE = 200.0D;
    private static final double MIN_EXPLOSION_POWER = 0.0D;
    private static final double MAX_EXPLOSION_POWER = 16.0D;
    private static final int MIN_FOOD_INTERVAL = 1;
    private static final int MAX_FOOD_INTERVAL = 600;


    private static volatile Values values = Values.defaults();

    private QueenBeastTuning()
    {
    }

    /** @return the server authoritative values of the current reload. */
    public static Values get()
    {
        return values;
    }

    /**
     * Parses every tuning file of the reload and swaps the result in. Missing fields inherit the value of the
     * file before them, and the very first file inherits the defaults of {@link Values#defaults()}.
     *
     * @return how many files were applied
     */
    public static int load(Map<ResourceLocation, JsonElement> files)
    {
        Values next = Values.defaults();
        int applied = 0;

        // A stable order keeps a reload reproducible when more than one pack ships a tuning file.
        List<ResourceLocation> ids = new ArrayList<>(files.keySet());
        ids.sort((left, right) -> left.toString().compareTo(right.toString()));

        for (ResourceLocation id : ids)
        {
            JsonElement element = files.get(id);

            if (element == null || !element.isJsonObject())
            {
                SpiderQueenHungryCraft.LOGGER.warn("Ignoring beast tuning {}: it is not a json object", id);
                continue;
            }

            try
            {
                next = parse(id, element.getAsJsonObject(), next);
                applied++;
            }
            catch (RuntimeException exception)
            {
                // A broken pack file must never break the form: the previous values simply stay.
                SpiderQueenHungryCraft.LOGGER.warn("Ignoring beast tuning {}: {}", id, exception.getMessage());
            }
        }

        values = next;
        return applied;
    }

    /** One file on top of the values parsed so far. */
    private static Values parse(ResourceLocation id, JsonObject json, Values base)
    {
        double armor = GsonHelper.getAsDouble(json, "armor_bonus", base.armorBonus());
        JsonObject bite = child(json, "bite");
        JsonObject camera = child(json, "camera");
        JsonObject biteShake = camera == null ? null : child(camera, "bite_shake");

        double biteWidth = GsonHelper.getAsDouble(bite, "width", base.biteWidth());
        double biteHeight = GsonHelper.getAsDouble(bite, "height", base.biteHeight());
        double biteDepth = GsonHelper.getAsDouble(bite, "depth", base.biteDepth());
        int shakeTicks = GsonHelper.getAsInt(biteShake, "duration_ticks", base.shakeTicks());
        float shakePitch = GsonHelper.getAsFloat(biteShake, "pitch_degrees", base.shakePitch());
        float shakeYaw = GsonHelper.getAsFloat(biteShake, "yaw_degrees", base.shakeYaw());
        float shakeRoll = GsonHelper.getAsFloat(biteShake, "roll_degrees", base.shakeRoll());
        double shakeFov = GsonHelper.getAsDouble(biteShake, "fov_degrees", base.shakeFov());

        Jump jump = parseJump(child(json, "jump"), base.jump());
        Stomp stomp = parseStomp(child(json, "stomp"), base.stomp());
        Charge charge = parseCharge(child(json, "charge"), base.charge());
        Breaking breaking = parseBreaking(child(json, "block_breaking"), base.breaking());

        // Armor is the one value that may be negative: a pack is allowed to build a glass cannon queen.
        // The sizes and impulses, however, are clamped, so a typo can never create an empty bite volume, a
        // jump into orbit, a charge at light speed or a camera that never stops shaking.
        return new Values(armor, Math.max(biteWidth, MIN_BITE_SIZE), Math.max(biteHeight, MIN_BITE_SIZE),
                Math.max(biteDepth, MIN_BITE_SIZE),
                (int) Mth.clamp(shakeTicks, MIN_SHAKE_TICKS, MAX_SHAKE_TICKS), shakePitch, shakeYaw, shakeRoll,
                shakeFov, jump, stomp, charge, breaking);
    }

    private static Jump parseJump(JsonObject json, Jump base)
    {
        if (json == null)
        {
            return base;
        }

        int ticks = GsonHelper.getAsInt(json, "max_charge_ticks", base.maxChargeTicks());
        float minStomp = GsonHelper.getAsFloat(json, "minimum_stomp_charge", base.minStompCharge());
        double minVertical = GsonHelper.getAsDouble(json, "min_vertical_velocity", base.minVertical());
        double maxVertical = GsonHelper.getAsDouble(json, "max_vertical_velocity", base.maxVertical());
        double forward = GsonHelper.getAsDouble(json, "forward_velocity", base.forwardVelocity());

        return new Jump((int) Mth.clamp(ticks, MIN_CHARGE_TICKS, MAX_CHARGE_TICKS),
                Mth.clamp(minStomp, 0.0F, 1.0F), Math.max(minVertical, 0.0D), Math.max(maxVertical, minVertical),
                Mth.clamp(forward, 0.0D, 1.0D));
    }

    private static Stomp parseStomp(JsonObject json, Stomp base)
    {
        if (json == null)
        {
            return base;
        }

        JsonObject shake = child(json, "camera");

        return new Stomp(Mth.clamp(GsonHelper.getAsDouble(json, "damage", base.damage()), 0.0D, MAX_DAMAGE),
                Mth.clamp(GsonHelper.getAsDouble(json, "radius", base.radius()), MIN_STOMP_RADIUS, MAX_STOMP_RADIUS),
                Math.max(GsonHelper.getAsDouble(json, "vertical_range", base.verticalRange()), 0.0D),
                Math.max(GsonHelper.getAsDouble(json, "knock_up", base.knockUp()), 0.0D),
                Math.max(GsonHelper.getAsDouble(json, "knock_outward", base.knockOutward()), 0.0D),
                (int) Mth.clamp(GsonHelper.getAsInt(shake, "duration_ticks", base.shakeTicks()), MIN_SHAKE_TICKS,
                        MAX_SHAKE_TICKS),
                GsonHelper.getAsFloat(shake, "pitch_degrees", base.shakePitch()),
                GsonHelper.getAsFloat(shake, "yaw_degrees", base.shakeYaw()),
                GsonHelper.getAsFloat(shake, "roll_degrees", base.shakeRoll()),
                GsonHelper.getAsDouble(shake, "fov_degrees", base.shakeFov()));
    }

    private static Charge parseCharge(JsonObject json, Charge base)
    {
        if (json == null)
        {
            return base;
        }

        return new Charge(
                Mth.clamp(GsonHelper.getAsDouble(json, "speed", base.speed()), MIN_CHARGE_SPEED, MAX_CHARGE_SPEED),
                (int) Mth.clamp(GsonHelper.getAsInt(json, "food_interval_ticks", base.foodInterval()),
                        MIN_FOOD_INTERVAL, MAX_FOOD_INTERVAL),
                (int) Mth.clamp(GsonHelper.getAsInt(json, "food_cost_per_interval", base.foodCost()), 0, 20),
                Mth.clamp(GsonHelper.getAsDouble(json, "damage", base.damage()), 0.0D, MAX_DAMAGE),
                Math.max(GsonHelper.getAsDouble(json, "knock_up", base.knockUp()), 0.0D),
                Math.max(GsonHelper.getAsDouble(json, "knock_forward", base.knockForward()), 0.0D),
                Mth.clamp(GsonHelper.getAsDouble(json, "max_turn_degrees_per_tick", base.maxTurnPerTick()), 0.0D,
                        45.0D),
                Mth.clamp(GsonHelper.getAsDouble(json, "hard_impact_explosion_power", base.explosionPower()),
                        MIN_EXPLOSION_POWER, MAX_EXPLOSION_POWER));
    }

    private static Breaking parseBreaking(JsonObject json, Breaking base)
    {
        if (json == null)
        {
            return base;
        }

        return new Breaking(parseRule(child(json, RuleProfile.TRANSFORM_CLEARANCE.jsonName()),
                base.transformClearance()), parseRule(child(json, RuleProfile.BODY_CRUSH.jsonName()),
                        base.bodyCrush()),
                parseRule(child(json, RuleProfile.STOMP.jsonName()), base.stomp()),
                parseRule(child(json, RuleProfile.CHARGE.jsonName()), base.charge()));
    }

    /**
     * One rule of one ability. A missing field always keeps the value of the rule before it, so a pack that
     * only wants to allow logs in the body crush writes exactly that one line.
     */
    private static Rule parseRule(JsonObject json, Rule base)
    {
        if (json == null)
        {
            return base;
        }

        double maxHardness = GsonHelper.getAsDouble(json, "max_hardness", base.maxHardness());
        Block reference = base.hardnessReference();
        boolean hasReference = json.has("hardness_reference");

        if (hasReference)
        {
            // A reference block replaces the plain hardness limit, exactly like the charge uses obsidian.
            reference = resolveBlock(GsonHelper.getAsString(json, "hardness_reference", ""));
        }

        boolean dropBlocks = GsonHelper.getAsBoolean(json, "drop_blocks", base.dropBlocks());
        boolean allowBlockEntities = GsonHelper.getAsBoolean(json, "allow_block_entities",
                base.allowBlockEntities());

        Set<Block> allowBlocks = json.has("allow_blocks")
                ? parseBlocks(json.getAsJsonArray("allow_blocks"), "allow_blocks")
                : base.allowBlocks();
        List<TagKey<Block>> allowTags = json.has("allow_tags")
                ? parseTags(json.getAsJsonArray("allow_tags"), "allow_tags")
                : base.allowTags();
        Set<Block> protectedBlocks = json.has("protected_blocks")
                ? parseBlocks(json.getAsJsonArray("protected_blocks"), "protected_blocks")
                : base.protectedBlocks();
        List<TagKey<Block>> protectedTags = json.has("protected_tags")
                ? parseTags(json.getAsJsonArray("protected_tags"), "protected_tags")
                : base.protectedTags();

        // A negative hardness has no meaning in a rule, the matcher refuses those blocks on its own.
        return new Rule(Math.max(maxHardness, 0.0D), hasReference ? reference : base.hardnessReference(),
                dropBlocks, allowBlockEntities, allowBlocks, allowTags, protectedBlocks, protectedTags);
    }

    private static Set<Block> parseBlocks(JsonArray array, String what)
    {
        Set<Block> blocks = new LinkedHashSet<>();

        if (array == null)
        {
            return blocks;
        }

        for (JsonElement element : array)
        {
            Block block = resolveBlock(element.getAsString());

            if (block != null)
            {
                blocks.add(block);
            }
            else
            {
                SpiderQueenHungryCraft.LOGGER.warn("Ignoring unknown block {} in beast tuning {}", what,
                        element.getAsString());
            }
        }

        return blocks;
    }

    private static List<TagKey<Block>> parseTags(JsonArray array, String what)
    {
        List<TagKey<Block>> tags = new ArrayList<>();

        if (array == null)
        {
            return tags;
        }

        for (JsonElement element : array)
        {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());

            if (id == null)
            {
                SpiderQueenHungryCraft.LOGGER.warn("Ignoring malformed tag id {} in beast tuning {}", what,
                        element.getAsString());
                continue;
            }

            // A tag that no pack ever filled simply matches nothing, which is a safe outcome on its own.
            tags.add(TagKey.create(Registries.BLOCK, id));
        }

        return tags;
    }

    private static Block resolveBlock(String id)
    {
        ResourceLocation location = ResourceLocation.tryParse(id);

        if (location == null)
        {
            return null;
        }

        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    private static JsonObject child(JsonObject parent, String name)
    {
        if (parent == null)
        {
            return null;
        }

        JsonElement element = parent.has(name) ? parent.get(name) : null;

        if (element == null || !element.isJsonObject())
        {
            // A missing or malformed section is treated as "present but empty", so every field of it keeps the
            // value it had before: a pack that only writes three keys can never make the parse fail.
            return new JsonObject();
        }

        return element.getAsJsonObject();
    }

    /**
     * Brings every transformed beast in line with the values that were just reloaded (Phase 8B-POLISH-2).
     *
     * <p>An armor change has to reach a beast that is already walking around, otherwise the player would have
     * to leave and re-enter the form after every {@code /reload}. The modifier keeps its fixed id, so the old
     * value is removed and the new one is added instead of stacking on top of it.
     */
    public static void reconcileTransformedBeasts(MinecraftServer server)
    {
        if (server == null)
        {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers())
        {
            if (QueenBeastState.isTransformed(player))
            {
                QueenBeastAttributes.reconcileArmor(player);
            }
        }
    }
}
