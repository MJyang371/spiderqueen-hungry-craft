package spiderqueenhungrycraft.faction;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.world.QueenEntityTags;

/**
 * The single question of the faction system: which faction is this, and how do two factions stand towards each other
 * (Phase HF1-VISIBLE, parts E and I).
 *
 * <p>Everything that wants to know a faction or a faction relation goes through this class, so the old scattered
 * checks and the new system can never drift apart.
 *
 * <p><b>Faction and colony are two layers (Queen Colony framework).</b> This class only answers the faction
 * question: which big side an entity stands on. It deliberately has no opinion about the inside of the queen side -
 * two queens with their own broods are both {@code QUEEN}, and only {@link QueenColonyRelations#isSameColony}
 * (the owner UUID of a minion, the player UUID of a queen) can tell their colonies apart.
 *
 * <p>The player is the one entity whose faction is not read from a tag: one entity type can not tell a Spider Queen
 * from a normal human, so {@link QueenDataCapability#isQueen(Player)} is asked every single time. Nothing here caches
 * a player faction, so {@code /hungrycraft queen set} changes the faction of that player immediately, and dying,
 * respawning or changing the dimension changes nothing.
 */
public final class FactionResolver
{
    /** Entity types that match several faction tags, warned about exactly once. */
    private static final Set<EntityType<?>> WARNED_MULTI_TAG = ConcurrentHashMap.newKeySet();

    private FactionResolver()
    {
    }

    /** @return the faction of that entity, never null. */
    public static FactionId getFaction(@Nullable Entity entity)
    {
        if (!(entity instanceof LivingEntity living))
        {
            return FactionId.NONE;
        }

        if (living instanceof Player player)
        {
            return QueenDataCapability.isQueen(player) ? FactionId.QUEEN : FactionId.HUMAN;
        }

        return factionOfType(living.getType());
    }

    /**
     * @return the faction of that entity type, read from the json tags only.
     *
     * <p>Priority: {@code no_faction} wins over everything, then {@code queen_faction}, {@code human_faction},
     * {@code village_faction}, {@code illager_faction}, {@code nether_fortress_faction}, {@code piglin_faction} and
     * {@code monster_faction}. An entity type in no tag has no faction at all - including a {@code Monster}, which is
     * never filed into {@code monster_faction} automatically. A type that ends up in several faction tags is reported
     * once, so a pack author can see which one won.
     */
    public static FactionId factionOfType(EntityType<?> type)
    {
        if (type.is(FactionTags.NO_FACTION))
        {
            return FactionId.NONE;
        }

        boolean queen = type.is(FactionTags.QUEEN_FACTION);
        boolean human = type.is(FactionTags.HUMAN_FACTION);
        boolean village = type.is(FactionTags.VILLAGE_FACTION);
        boolean illager = type.is(FactionTags.ILLAGER_FACTION);
        boolean fortress = type.is(FactionTags.NETHER_FORTRESS_FACTION);
        boolean piglin = type.is(FactionTags.PIGLIN_FACTION);
        boolean monster = type.is(FactionTags.MONSTER_FACTION);

        FactionId found = queen ? FactionId.QUEEN
                : human ? FactionId.HUMAN
                        : village ? FactionId.VILLAGE
                                : illager ? FactionId.ILLAGER
                                        : fortress ? FactionId.NETHER_FORTRESS
                                                : piglin ? FactionId.PIGLIN : monster ? FactionId.MONSTER
                                                        // A defending unit that no faction tag names is a village unit
                                                        // all the same, so a pack author can simply put an id into
                                                        // village_defenders.json and get a defender that hunts the
                                                        // queen. An explicit faction tag always wins over this fallback.
                                                        : type.is(FactionTags.VILLAGE_DEFENDERS) ? FactionId.VILLAGE
                                                                : FactionId.NONE;

        int matches = (queen ? 1 : 0) + (human ? 1 : 0) + (village ? 1 : 0) + (illager ? 1 : 0) + (fortress ? 1 : 0)
                + (piglin ? 1 : 0) + (monster ? 1 : 0);

        if (matches > 1 && WARNED_MULTI_TAG.add(type))
        {
            SpiderQueenHungryCraft.LOGGER.warn(
                    "[Faction] {} is listed in {} faction tags, the priority order decided for {} - see FactionTags",
                    EntityType.getKey(type), matches, found.id());
        }

        return found;
    }

    /** @return true when that entity type is one of the fighting village units. */
    public static boolean isVillageDefender(EntityType<?> type)
    {
        return type.is(FactionTags.VILLAGE_DEFENDERS);
    }

    /** @return how the faction of the source stands towards the faction of the target. */
    public static FactionRelation resolve(@Nullable LivingEntity source, @Nullable LivingEntity target)
    {
        if (source == null || target == null)
        {
            return FactionRelation.PASS_THROUGH;
        }

        if (source == target)
        {
            return FactionRelation.FRIENDLY;
        }

        return withLegacyOverrides(source, getFaction(target));
    }

    /** Same as {@link #resolve(LivingEntity, LivingEntity)} but against a faction that has no entity yet. */
    public static FactionRelation relationTowards(@Nullable Mob source, FactionId targetFaction)
    {
        return source == null ? FactionRelation.PASS_THROUGH : withLegacyOverrides(source, targetFaction);
    }

    /**
     * @return the pure faction matrix, without any entity specific override.
     *
     * <p>Read it as three blocks. The <b>queen</b> is attacked by humans, villages, illagers and the nether
     * fortress, while ordinary monsters and the piglin ecology are neutral towards her. <b>Humans and villages</b>
     * fight ordinary monsters, illagers and the nether fortress, leave each other alone, and have no opinion at all
     * about the piglins. The <b>hostile ecologies</b> (monsters, illagers, nether fortress) mirror that: monsters
     * hunt humans, illagers and the fortress hunt humans, villages and the queen, and the piglin ecology is neutral
     * towards the queen and has no opinion about anybody else - their own vanilla AI decides every fight of theirs.
     *
     * <p>Inside one faction nothing is decided here: two monsters, two illagers or two villagers may still start a
     * fight in the vanilla game, so {@code MONSTER} and the hostile ecologies are {@link FactionRelation#PASS_THROUGH}
     * towards their own kind. {@code QUEEN} is treated exactly like that, and for a second reason: the queen side is
     * split into colonies by owner UUID, which this matrix can not see. Use
     * {@link QueenColonyRelations#isSameColony} / {@link QueenColonyRelations#isQueenSideFriendly} for those pairs.
     */
    public static FactionRelation relationBetween(FactionId source, FactionId target)
    {
        if (source == FactionId.NONE || target == FactionId.NONE)
        {
            return FactionRelation.PASS_THROUGH;
        }

        if (source == target)
        {
            // A skeleton that hits another skeleton may still start a fight: the inside of a hostile faction is not
            // ours, and the same is true for the two nether ecologies.
            //
            // QUEEN is on that list as well (Queen Colony framework). The queen side is not one family by faction
            // alone: two queens with their own broods are all QUEEN, so calling that pair friendly would lock every
            // future colony war forever. Who is a friend is decided by the colony instead - see
            // QueenColonyRelations.isQueenSideFriendly - and PASS_THROUGH here means exactly "the matrix has no
            // opinion about the queen side", which is what lets that layer answer it.
            return source == FactionId.MONSTER || source == FactionId.ILLAGER || source == FactionId.NETHER_FORTRESS
                    || source == FactionId.PIGLIN || source == FactionId.QUEEN ? FactionRelation.PASS_THROUGH
                            : FactionRelation.FRIENDLY;
        }

        if (source == FactionId.QUEEN)
        {
            // Ordinary monsters and the piglin ecology are neutral towards the queen, the illagers and the nether
            // fortress hunt her, everything else on the list is an enemy of the queen as well.
            return target == FactionId.MONSTER || target == FactionId.PIGLIN ? FactionRelation.NEUTRAL
                    : FactionRelation.HOSTILE;
        }

        if (source == FactionId.HUMAN)
        {
            // Humans leave the villages alone and have no opinion about the piglins; monsters, illagers, the nether
            // fortress and queens are fought.
            if (target == FactionId.VILLAGE)
            {
                return FactionRelation.NEUTRAL;
            }

            return target == FactionId.PIGLIN ? FactionRelation.PASS_THROUGH : FactionRelation.HOSTILE;
        }

        if (source == FactionId.VILLAGE)
        {
            // The village defends itself against the queen, ordinary monsters, the illagers and the nether fortress.
            // Humans and piglins it leaves alone, which never suppresses the vanilla behaviour of a villager, a golem
            // or a modded guard.
            if (target == FactionId.QUEEN || target == FactionId.MONSTER || target == FactionId.ILLAGER
                    || target == FactionId.NETHER_FORTRESS)
            {
                return FactionRelation.HOSTILE;
            }

            return target == FactionId.PIGLIN ? FactionRelation.PASS_THROUGH : FactionRelation.NEUTRAL;
        }

        if (source == FactionId.ILLAGER || source == FactionId.NETHER_FORTRESS)
        {
            // Both ecologies hunt the queen, the humans and the villages, and care about nothing else.
            if (target == FactionId.QUEEN || target == FactionId.HUMAN || target == FactionId.VILLAGE)
            {
                return FactionRelation.HOSTILE;
            }

            return FactionRelation.NEUTRAL;
        }

        if (source == FactionId.PIGLIN)
        {
            // The piglin ecology is neutral towards the queen and has no faction opinion anywhere else: this mod never
            // starts a fight for a piglin, a hoglin or one of their undead kin.
            return target == FactionId.QUEEN ? FactionRelation.NEUTRAL : FactionRelation.PASS_THROUGH;
        }

        // MONSTER: humans are prey, the queen, the villages and the other ecologies are neutral.
        return target == FactionId.HUMAN ? FactionRelation.HOSTILE : FactionRelation.NEUTRAL;
    }

    /**
     * Applies the one compatibility layer of the old design: the {@code always_hostile_to_spider_queen} tag keeps
     * entities of the <b>ordinary monster faction</b> hostile to the queen, which the plain matrix would call neutral.
     *
     * <p>The tag is a modifier on {@code monster_faction}, not a second faction: it is only consulted for a type whose
     * faction really is {@link FactionId#MONSTER}. The illager and nether fortress factions do not need it at all -
     * they are hostile to the queen in the matrix itself - and the piglin ecology stays neutral however the tag is
     * written, so a pack author can never accidentally turn the nether into a war by editing this one file.
     */
    private static FactionRelation withLegacyOverrides(LivingEntity source, FactionId targetFaction)
    {
        FactionId mine = getFaction(source);
        FactionRelation relation = relationBetween(mine, targetFaction);

        if (relation == FactionRelation.NEUTRAL && targetFaction == FactionId.QUEEN
                && mine == FactionId.MONSTER
                && source.getType().is(QueenEntityTags.ALWAYS_HOSTILE_TO_SPIDER_QUEEN))
        {
            return FactionRelation.HOSTILE;
        }

        return relation;
    }
}
