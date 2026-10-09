package spiderqueenhungrycraft.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/**
 * One skill node of the growth tree (Skill Progression round): the whole of one node, read from one json file.
 *
 * <p>The trunk of the tree is the Queen Level and it is 1 to 200; a node is a branch that hangs off one rung of that
 * trunk. It is anchored at a level for the drawing, it leans to one side, it stands on a lane that decides how far out
 * it is, and it may name other nodes it needs first. What it costs, what it shows and which key of the keyboard it
 * answers to are all part of this record, because all of that is a property of the node and not of the code that reads
 * it.
 *
 * <p><b>The identity is the file it came from</b> ({@code data/<namespace>/progression_skills/<name>.json} becomes
 * {@code <namespace>:<name>}), so a save file holds stable identities and never a number that shifts when a pack
 * inserts a file. The level that opens a node and the level it is drawn at are deliberately two numbers: a node that
 * needs level twenty may hang one rung lower so that two branches of the same level do not overlap.
 *
 * @param id                the identity of the node, taken from the file that declared it
 * @param requiredLevel     the Queen Level a player must have reached before the node may be bought
 * @param soulCost          how many souls one purchase costs, never negative
 * @param anchorLevel       the rung of the trunk the node is drawn at
 * @param side              which side of the trunk it grows to
 * @param lane              how far out it stands: lane 1 is the first ring, lane 2 the second, and so on
 * @param parents           nodes that have to be unlocked before this one may be bought - all of them, never any of them
 * @param icon              the item the node shows, in every state
 * @param nameKey           the translation key of its name
 * @param descriptionKey    the translation key of its effect
 * @param inputHint         which key of the keyboard the skill answers to, or {@link InputHint#NONE} for a passive one
 * @param enabled           false hides the node entirely, for a node a pack switched off
 * @param requiredForLegend whether unlocking it counts towards the legend requirement of the future round
 */
public record SkillNodeDefinition(ResourceLocation id, int requiredLevel, long soulCost, int anchorLevel, Side side,
        int lane, List<ResourceLocation> parents, ResourceLocation icon, String nameKey, String descriptionKey,
        InputHint inputHint, boolean enabled, boolean requiredForLegend)
{
    /** Which side of the main line a branch grows to. */
    public enum Side
    {
        LEFT,
        RIGHT
    }

    /**
     * The key a skill answers to (Skill Progression round).
     *
     * <p>It is deliberately a name of this mod and not a {@code KeyMapping}: this record is read on both sides of the
     * game and a dedicated server has no keyboard and no client class at all. The name says <b>which</b> binding a
     * tooltip has to show; the display of the current key is looked up on the client, from the real binding of that
     * player, and never from a default written down here.
     */
    public enum InputHint
    {
        /** A passive skill: it is always at work and has no key of its own. */
        NONE("none"),

        /** The empowered melee attack. */
        EMPOWERED_ATTACK("empowered_attack"),

        /** The Queen Beast transformation. */
        BEAST_TRANSFORM("beast_transform"),

        /** The devour and regurgitate key of the beast. */
        DEVOUR("devour"),

        /** The sniff of the queen. */
        SNIFF("sniff"),

        /**
         * 亡灵契约 IV (Necromancy IV round): the key that wakes a bone dragon of Ice and Fire up.
         *
         * <p>It is the first hint of this mod that belongs to an <b>optional</b> compatibility rather than to the queen
         * herself, which is why its key is bound to nothing by default like every other brood key of this mod: a pack
         * whose world has no Ice and Fire has nothing for it to do.
         */
        NECROMANCY_IV("necromancy_iv");

        private final String id;

        InputHint(String id)
        {
            this.id = id;
        }

        /** @return the name of this hint as it is written in a json file. */
        public String id()
        {
            return this.id;
        }

        /** @return the hint of that name, or null when no key of this mod answers to it. */
        public static InputHint byId(String name)
        {
            for (InputHint hint : values())
            {
                if (hint.id.equals(name))
                {
                    return hint;
                }
            }

            return null;
        }
    }

    /**
     * The default of {@code required_for_legend} is {@code true}: everything counts towards the legend requirement
     * unless a pack says otherwise, so a node can never be forgotten out of the condition by accident.
     */
    public static final boolean REQUIRED_FOR_LEGEND_DEFAULT = true;

    /** The highest lane a node may stand on, so no file can draw a branch outside the window. */
    public static final int MAX_LANE = 8;

    /**
     * Reads one node out of a json object.
     *
     * <p>A file that can not be read is refused as a whole instead of being repaired silently: a node whose
     * prerequisites or icon do not parse would either lock something for the wrong reason or draw nothing at all, and
     * both are worse than a skipped node with a line in the log. The caller keeps every other node of the same reload.
     *
     * @throws IllegalArgumentException when a value is missing or out of range.
     */
    public static SkillNodeDefinition parse(ResourceLocation id, JsonObject root)
    {
        int requiredLevel = readInt(root, "required_level", 1);
        long soulCost = readLong(root, "soul_cost", 0L);

        JsonObject layout = root.has("layout") && root.get("layout").isJsonObject()
                ? root.getAsJsonObject("layout")
                : new JsonObject();

        int anchorLevel = readInt(layout, "anchor_level", requiredLevel);
        Side side = readSide(layout);
        int lane = readInt(layout, "lane", 1);

        List<ResourceLocation> parents = readIds(root, "prerequisites");
        ResourceLocation icon = readId(root, "icon");
        String nameKey = readText(root, "name");
        String descriptionKey = readText(root, "description");
        InputHint inputHint = readHint(root);

        boolean enabled = !root.has("enabled") || root.get("enabled").getAsBoolean();
        boolean requiredForLegend = !root.has("required_for_legend")
                || root.get("required_for_legend").getAsBoolean();

        if (requiredLevel < 1)
        {
            throw new IllegalArgumentException("required_level must be at least 1");
        }
        if (soulCost < 0L)
        {
            throw new IllegalArgumentException("soul_cost must not be negative");
        }
        if (anchorLevel < 1)
        {
            throw new IllegalArgumentException("anchor_level must be at least 1");
        }
        if (lane < 1 || lane > MAX_LANE)
        {
            throw new IllegalArgumentException("lane must be between 1 and " + MAX_LANE);
        }
        if (parents.contains(id))
        {
            throw new IllegalArgumentException("a node can not be its own prerequisite");
        }

        return new SkillNodeDefinition(id, requiredLevel, soulCost, anchorLevel, side, lane, List.copyOf(parents), icon,
                nameKey, descriptionKey, inputHint, enabled, requiredForLegend);
    }

    /** @return the whole number of that key, or the fallback when it is absent. */
    private static int readInt(JsonObject root, String key, int fallback)
    {
        return (int)readLong(root, key, fallback);
    }

    /**
     * @return the whole number of that key, or the fallback when it is absent.
     * @throws IllegalArgumentException when it is present but not a whole number.
     */
    private static long readLong(JsonObject root, String key, long fallback)
    {
        JsonElement element = root.get(key);

        if (element == null || element.isJsonNull())
        {
            return fallback;
        }

        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
        {
            throw new IllegalArgumentException(key + " must be a whole number");
        }

        double value = element.getAsDouble();

        if (value != Math.floor(value))
        {
            throw new IllegalArgumentException(key + " must be a whole number");
        }

        return (long)value;
    }

    /** @return the text of that key, which is never empty. */
    private static String readText(JsonObject root, String key)
    {
        JsonElement element = root.get(key);

        if (element == null || !element.isJsonPrimitive() || element.getAsString().isBlank())
        {
            throw new IllegalArgumentException(key + " must be a translation key");
        }

        return element.getAsString();
    }

    /** @return the identity of that key, which has to be there. */
    private static ResourceLocation readId(JsonObject root, String key)
    {
        ResourceLocation parsed = ResourceLocation.tryParse(readText(root, key));

        if (parsed == null)
        {
            throw new IllegalArgumentException(key + " must be a namespaced identity");
        }

        return parsed;
    }

    /** @return the list of identities of that key, empty when it is absent. */
    private static List<ResourceLocation> readIds(JsonObject root, String key)
    {
        JsonElement element = root.get(key);

        if (element == null || element.isJsonNull())
        {
            return List.of();
        }

        if (!element.isJsonArray())
        {
            throw new IllegalArgumentException(key + " must be a list");
        }

        JsonArray array = element.getAsJsonArray();
        List<ResourceLocation> ids = new ArrayList<>(array.size());

        for (int index = 0; index < array.size(); index++)
        {
            ResourceLocation parsed = ResourceLocation.tryParse(array.get(index).getAsString());

            if (parsed == null)
            {
                throw new IllegalArgumentException(key + " contains something that is not an identity");
            }

            ids.add(parsed);
        }

        return ids;
    }

    /** @return the side of that layout, left when it says nothing. */
    private static Side readSide(JsonObject layout)
    {
        JsonElement element = layout.get("side");

        if (element == null || element.isJsonNull())
        {
            return Side.LEFT;
        }

        String name = element.getAsString().toLowerCase(Locale.ROOT).trim();

        return switch (name)
        {
            case "left" -> Side.LEFT;
            case "right" -> Side.RIGHT;
            default -> throw new IllegalArgumentException("side must be left or right");
        };
    }

    /** @return the key hint of that node, none when it says nothing. */
    private static InputHint readHint(JsonObject root)
    {
        JsonElement element = root.get("input_hint");

        if (element == null || element.isJsonNull())
        {
            return InputHint.NONE;
        }

        InputHint hint = InputHint.byId(element.getAsString().toLowerCase(Locale.ROOT).trim());

        if (hint == null)
        {
            throw new IllegalArgumentException("input_hint names a key this mod does not have");
        }

        return hint;
    }
}
