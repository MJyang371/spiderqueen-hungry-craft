package spiderqueenhungrycraft.soul;

import java.util.Optional;

/**
 * The canonical families of structure a soul container can belong to (Soul Consumable round).
 *
 * <p><b>A family is what a structure <i>is</i>, never where it stands.</b> A jungle fortress is a
 * {@link #NETHER_FORTRESS} and an underground stronghold of a nether mod is a {@link #STRONGHOLD}: the family is
 * decided from the structure that really owns the container - through the aliases of
 * {@link SoulLootRules} - and not from the biome, the dimension or a word inside a loot table name.
 *
 * <p>The set is fixed on purpose. The worth of a soul is designed per family (a dungeon is worth a benevolent soul,
 * an end city is worth a hero's soul) and a family that nobody declared can therefore not exist: a pack adds a
 * <i>structure</i> to a family, it never invents a sixteenth tier. What a family is worth in souls is data like every
 * other alias, so a pack may re-pose a family to another soul without touching this list.
 */
public enum SoulFamily
{
    /** Every village of the game, including the ones a village mod rewrites. */
    VILLAGE("village"),

    /** Monster rooms - the one family that is not always a registered structure at all. */
    DUNGEON("dungeon"),

    MINESHAFT("mineshaft"),

    SHIPWRECK("shipwreck"),

    BURIED_TREASURE("buried_treasure"),

    DESERT_PYRAMID("desert_pyramid"),

    JUNGLE_TEMPLE("jungle_temple"),

    PILLAGER_OUTPOST("pillager_outpost"),

    WOODLAND_MANSION("woodland_mansion"),

    ANCIENT_CITY("ancient_city"),

    STRONGHOLD("stronghold"),

    RUINED_PORTAL("ruined_portal"),

    NETHER_FORTRESS("nether_fortress"),

    BASTION("bastion"),

    END_CITY("end_city");

    private final String id;

    SoulFamily(String id)
    {
        this.id = id;
    }

    /** The name this family is written as in a rule file. */
    public String id()
    {
        return this.id;
    }

    /** @return the family of that name, or an empty optional when a file names one that does not exist. */
    public static Optional<SoulFamily> byId(String id)
    {
        for (SoulFamily family : values())
        {
            if (family.id.equals(id))
            {
                return Optional.of(family);
            }
        }

        return Optional.empty();
    }
}
