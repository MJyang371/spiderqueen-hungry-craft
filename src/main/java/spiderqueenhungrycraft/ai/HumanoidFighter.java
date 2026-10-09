package spiderqueenhungrycraft.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import spiderqueenhungrycraft.attack.ShieldBreak;

/**
 * The seam of the human fight (Infested Steve round).
 *
 * <p>The whole fight of a human body - when to walk in, when to raise the shield, when to draw the bow, when to bite
 * into a golden apple, when to fall back - is one shared state machine: {@link HumanCombatBrain} driven by
 * {@link HumanCombatGoal}. That state machine is written against this interface, so <b>exactly the same fight runs in
 * every body that answers these thirteen questions and lends out the ordinary body calls below</b>. Nothing about the
 * fight is copied for a second kind of body, and no second fight exists.
 *
 * <p>The interface is deliberately nothing more than a view of an entity: every method below is already inherited by
 * any {@code PathfinderMob} - a hitting hand, the two hands, the navigation, the look control, the health - so a body
 * that implements this interface adds no code for the body calls at all. Only the thirteen questions of the equipment
 * and the hands ({@code hasBow}, {@code equipMeleeWeapon}, {@code consumeGoldenApple} ...) are answered per body,
 * because those are what the gear of that body is.
 */
public interface HumanoidFighter
{
    /** @return the one brain of the fight of this body, the state machine of {@link HumanCombatGoal}. */
    HumanCombatBrain brain();

    /** @return true while a real shield is still carried in the offhand. */
    boolean hasShield();

    /**
     * @return true while this body may raise its shield at all. A shield that an axe smashed stays down for five
     *         seconds, exactly as long as the shield of a player, so this is {@link #hasShield()} minus the smashed
     *         state - see {@code attack/ShieldBreak}. Every decision about the shield asks this, never
     *         {@code hasShield()} alone, so no brain ever fakes a guard with a shield that was taken away.
     */
    default boolean canGuard()
    {
        return this.hasShield() && !ShieldBreak.isDisabled(this.asLivingEntity());
    }

    /** @return true when this body owns a bow, whether it is in its hand right now or not. */
    boolean hasBow();

    /** @return true when this body owns a blade. An unarmed body has to punch or keep its distance. */
    boolean hasMeleeWeapon();

    /** @return true when this body should walk into melee range at all: a bowman without a blade does not. */
    boolean canFightInMelee();

    /** @return the stack this body fights with in melee, may be empty. */
    ItemStack meleeWeapon();

    /** @return the bow this body owns, may be empty. */
    ItemStack bowWeapon();

    /** Puts the bow this body owns into its main hand. */
    void equipBow();

    /** Puts the blade this body owns into its main hand, an unarmed body ends up with an empty hand. */
    void equipMeleeWeapon();

    /** Swings the main arm, so the swing is visible on every client. */
    void swingMainHand();

    /**
     * One melee swing with whatever is in the main hand: the attack damage of this body plus the attack damage bonus of
     * the weapon it is holding.
     *
     * @return true when the victim was really hurt.
     */
    boolean performMeleeAttack(LivingEntity target);

    /** @return how many golden apples this body still carries, 0 when it has none. */
    int goldenAppleCount();

    /** @return true when one golden apple was really eaten by this call. */
    boolean consumeGoldenApple();

    /**
     * @return true when that victim may still be fought by <b>this body</b>, judged by the rule of its own kind.
     *
     * <p>This is the one gate that is deliberately <b>not</b> shared: who a prepared human may attack is the human
     * question of this round (a queen player and the monster faction), while who a soldier of the queen may attack is
     * the brood question of the faction system (whatever the queen side is at war with). A fight that ran on the rule
     * of the wrong kind would look exactly like a soldier who refuses to fight (Infested Steve round: this was the bug
     * that made the infested Steve ignore villagers, illagers and humans).
     */
    boolean mayFight(LivingEntity target);

    /**
     * @return the arrow this body shoots, already owning this body as its shooter.
     *
     * <p>It is the one thing about the shot that is not the same for every human body: a prepared human shoots the
     * ordinary arrow of the game, and a soldier of the queen shoots the arrow of the brood, which flies straight through
     * its own kind instead of hurting it (Infested Steve round). Everything else about the shot - the draw, the aim, the
     * bow range, the cooldown - stays in the shared fight and is therefore identical for both.
     */
    AbstractArrow newArrow();

    // ---------------------------------------------------------------------------------------------------------------
    // The body itself, and the one view that is deliberately not a list of game methods.
    //
    // A mod interface must never declare a member that carries the name of a game method. A built jar is reobfuscated:
    // inside it the very call the game knows as getNavigation() is m_21573_, and the members of a mod interface are not
    // treated like the members of a game class there - one side of a call can be renamed while the other is left
    // alone, and which side that is is not even stable between two builds of the same source. The result is a
    // NoSuchMethodError (an AbstractMethodError while the declaration was abstract) that exists <b>only</b> in a built
    // jar, never in the development environment - which is how it stays invisible until a player runs the mod.
    //
    // So this interface declares exactly one thing about the entity it is: body(), a name of this mod that the
    // reobfuscation has no mapping for and therefore never touches. Everything the fight wants from the game is asked
    // on the result - mob.body().getNavigation(), mob.body().getHealth(), ... - and the calls inside body() are
    // written on a vanilla type, so they are renamed exactly like every other call into the game, on both sides.
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * @return this fighter as the mob it is. Every body that implements this interface is a {@code PathfinderMob}, so
     *         this is a plain view of the same object and never a copy.
     *
     * <p>The name belongs to this mod on purpose, see the note above: a member of a mod interface that carries a game
     * name may be renamed on one side of a call at most, which turns a working fight into a {@code NoSuchMethodError}
     * for players while the development environment stays perfectly healthy.
     */
    default Mob body()
    {
        return (Mob) this;
    }

    /**
     * @return this fighter as the living being it is. Every body that implements this interface is a {@code Mob} and
     *         therefore a living entity, so this is only the view the game itself needs in the few places where a
     *         projectile asks for its shooter or a victim for its attacker.
     */
    default LivingEntity asLivingEntity()
    {
        return this.body();
    }
}
