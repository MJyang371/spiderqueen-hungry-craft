package spiderqueenhungrycraft.brood.ability;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * One ability of one kind of spider (Brood Ability framework).
 *
 * <p>The queen sceptre never knows what a spider can do. It only tells every follower "try your ability" and each kind
 * of spider answers with its own implementation of this interface, so the next special spiders (a creeper spider, an
 * ender spider, a skeleton spider) are one new class each - never another branch inside the sceptre.
 *
 * <p>An implementation is stateless: everything that has to be remembered between two casts (the cooldown) lives on the
 * spider itself, because that is the thing that is saved, carried and loaded.
 */
public interface SpiderAbility
{
    /** @return the stable id of this ability, used by the network and by the HUD of the next round. */
    String id();

    /** @return the name shown to the player. */
    Component displayName();

    /** @return what this ability needs from the world before it can be cast. */
    SpiderAbilityTarget targetRequirement();

    /** @return how long this ability rests after a successful cast, in ticks. */
    int cooldownTicks();

    /** @return true when this spider may cast right now, besides the cooldown that is checked by the framework. */
    boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context);

    /**
     * @return true when the effect of this ability is on the spider right now, used by the inspect HUD to tell
     *         "COOLDOWN" from "ACTIVE".
     *
     * <p>It is read on the client as well, so it must only look at what a client already knows - the effects of a living
     * entity are synchronised to everybody who can see it.
     */
    default boolean isActive(SpiderMinionEntity spider)
    {
        return false;
    }

    /**
     * Casts the ability. Called exactly once per cast attempt, and never while the cooldown runs.
     *
     * @return true when the ability really happened. A false means that nothing at all was spent - the framework does
     *         not start the cooldown - so a cast that turned out to be impossible (a place the spider cannot land on,
     *         for example) costs the queen nothing.
     */
    boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context);

    /**
     * @return the extra word the inspect HUD of the queen shows behind the name of this ability, for example the
     *         ammunition that is loaded right now. Null when this ability has nothing to add.
     *
     * <p>It is read on the client as well, so it must only look at data a client already has - synced entity data and
     * effects, never at the save file.
     */
    @Nullable
    default Component stateDetail(SpiderMinionEntity spider)
    {
        return null;
    }
}
