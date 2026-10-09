package spiderqueenhungrycraft.human;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.RegistryObject;
import spiderqueenhungrycraft.entity.HumanCombatantEntity;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * Who is a human of this mod (Phase HF0D, split into clear questions in the human tidy round).
 *
 * <p>This class used to answer one question - {@code isHumanMob} - that several rules asked, each of them meaning
 * something slightly different: the spawn population counter, the pack rules (comrade, provocation) and the monster
 * rule of the targeting class. The question is now split by meaning, and every caller keeps asking exactly the
 * question it really has:
 *
 * <ul>
 * <li>{@link #archetypeOfType(EntityType)} / {@link #archetypeOf(Entity)} - "which kind of human is this",</li>
 * <li>{@link #isHumanMob(Entity)} - "is this a human unit of this mod at all" (the question the population counter
 * and the pack rules ask; the answer is unchanged for every entity),</li>
 * <li>{@link #isOrdinaryHumanType(EntityType)} - "may the surface patrol place a mob of this type" (the spawn
 * eligibility question).</li>
 * </ul>
 *
 * <p>What is <b>not</b> asked here: which faction a human stands on (that is
 * {@link spiderqueenhungrycraft.faction.FactionResolver}) and who owns the target goal of a human (that is
 * {@link spiderqueenhungrycraft.faction.OwnFactionTargeting}). The answer is still made by the java classes of this
 * mod - never by a registry name and never by an {@code instanceof} of a foreign mod - so a pack author can not break
 * it by renaming anything.
 */
public final class HumanEntityTypes
{
    private HumanEntityTypes()
    {
    }

    /**
     * @return the archetype of that entity type, or null when it is no human of this mod.
     *
     * <p>The question is answered from the registration of this mod, because an entity type does not know the class it
     * creates: {@code EntityType#getBaseClass()} returns {@code Entity.class} in this version of the game, so a
     * class based answer is simply not available on a type. {@link HungryCraftEntities#HUMAN_TYPES} already is the one
     * list of the ordinary humans of this mod (it drives their attributes and their renderer), so it is the same list
     * here.
     *
     * <p>A type that is <b>not</b> in these lists has no archetype and is therefore never placed by the surface
     * patrol - the conservative direction on purpose: a new special human is special until it is named, never the other
     * way round.
     */
    @Nullable
    public static HumanArchetype archetypeOfType(@Nullable EntityType<?> type)
    {
        if (type == null)
        {
            return null;
        }

        if (type == HungryCraftEntities.CHAD_STEVE.get())
        {
            return HumanArchetype.TRANSCENDENT;
        }

        for (RegistryObject<EntityType<HumanCombatantEntity>> ordinary : HungryCraftEntities.HUMAN_TYPES)
        {
            if (type == ordinary.get())
            {
                return HumanArchetype.STANDARD;
            }
        }

        return null;
    }

    /**
     * @return the archetype of that entity, or null when it is no human of this mod.
     *
     * <p>On a life entity the marker interface is asked first, so a future special human is picked up by implementing
     * {@link TranscendentHuman} - without a single line in this class.
     */
    @Nullable
    public static HumanArchetype archetypeOf(@Nullable Entity entity)
    {
        if (entity instanceof TranscendentHuman)
        {
            return HumanArchetype.TRANSCENDENT;
        }

        if (entity instanceof HumanCombatantEntity)
        {
            return HumanArchetype.STANDARD;
        }

        return null;
    }

    /**
     * @return true when that entity is a human unit of this mod: an ordinary prepared human or a transcendent one.
     *         Players are never counted, a player is not a "naturally existing humanoid monster" even when he is a
     *         human.
     *
     * <p>This is the population/pack question, so it answers {@code true} for Chad Steve as well - a Chad Steve that
     * stands in the ring of a player counts towards that ring exactly like he did before this round.
     */
    public static boolean isHumanMob(@Nullable Entity entity)
    {
        return archetypeOf(entity) != null;
    }

    /** @return true when a mob of that type is an ordinary human, which is the only kind the surface patrol places. */
    public static boolean isOrdinaryHumanType(@Nullable EntityType<?> type)
    {
        return archetypeOfType(type) == HumanArchetype.STANDARD;
    }

    /** @return true when that entity is a special (transcendent) human of this mod. */
    public static boolean isTranscendent(@Nullable Entity entity)
    {
        return archetypeOf(entity) == HumanArchetype.TRANSCENDENT;
    }

    /**
     * @return true when that human counts towards the <b>surface population</b>: the number the surface spawner keeps
     *         topped up around a player and the number its per chunk and local caps are about.
     *
     * <p>Only ordinary humans do (Sweatlord round). A transcendent human is still a human unit for the faction rules,
     * for the pack rules and for {@link #isHumanMob}, but it is not part of that population - a Chad Steve or a
     * Sweatlord standing in the ring must not silently take a slot away from the ordinary survivors of the world.
     */
    public static boolean countsTowardSurfacePopulation(@Nullable Entity entity)
    {
        HumanArchetype archetype = archetypeOf(entity);

        return archetype != null && archetype.countsTowardSurfacePopulation();
    }
}
