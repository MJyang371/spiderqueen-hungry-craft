package spiderqueenhungrycraft.sweatlord;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import spiderqueenhungrycraft.entity.SweatlordSteveEntity;

/**
 * The ability stock of a Sweatlord Steve (Sweatlord round).
 *
 * <p>He has no inventory on purpose. A player-like fighter who is allowed to use a diamond sword, a bow, eggs, a
 * trident, pearls and two kinds of golden apple would need a whole inventory, a hotbar sync and a slot handler - and
 * every one of those would be a second thing that can disagree with the real hands of the entity. Instead he owns
 * <b>the ability to use</b> those items: this class hands him a fresh stack of the wanted item and puts it where the
 * game puts it for a player who selected that slot.
 *
 * <p><b>Everything here is a real hand, never a flag.</b> The main hand and the offhand of the entity are really set,
 * so the vanilla use item state ({@code isUsingItem}, {@code getUsedItemHand}, the draw of a bow, the raised shield),
 * the vanilla damage pipeline and every renderer in the game see the same thing a player would produce. That is what
 * makes the shield of {@link spiderqueenhungrycraft.attack.ShieldProjectileInteraction} work and what makes the bow
 * animation show up without a single custom packet.
 *
 * <p>The stock is virtual, so nothing is ever really consumed and nothing is dropped on death: the item in a hand may
 * disappear with the throw (the game takes the stack of a projectile), and the skill simply equips the next one.
 */
public final class VirtualLoadout
{
    /** The weapon he keeps ready when no other skill needs a hand. */
    public static final Item DEFAULT_MELEE = Items.DIAMOND_SWORD;

    /**
     * The axe that answers a raised shield: golden, and not diamond, for one reason only - a player has to <b>see</b>
     * the switch happen. The blue diamond sword is his normal hand, so the moment the guard of the enemy goes up he
     * pulls out the one weapon of this kit that looks nothing like it, lands the smash, and puts the sword back.
     *
     * <p>That is a visual change and nothing else: the reach check, the line of sight, the "is the shield really up"
     * check and the "was the hit really blocked" check of {@code MeleeSkills.AxeShieldBreakSkill} are untouched, and so
     * is {@code ShieldBreak}, which asks the weapon of the game whether it can disable a shield at all.
     */
    public static final Item SHIELD_BREAKER = Items.GOLDEN_AXE;

    /** The emergency water of this round: poured out, and taken back when it worked. */
    public static final Item WATER_BUCKET = Items.WATER_BUCKET;

    /** What stays in his hand after the pour, which is exactly how a player keeps their bucket. */
    public static final Item EMPTY_BUCKET = Items.BUCKET;

    private final SweatlordSteveEntity mob;

    public VirtualLoadout(SweatlordSteveEntity mob)
    {
        this.mob = mob;
    }

    /** Puts a fresh stack of that item into the main hand. */
    public void equipMain(Item item)
    {
        this.mob.setItemSlot(EquipmentSlot.MAINHAND, stackOf(item));
    }

    /** Puts a fresh stack of that item into the offhand. */
    public void equipOffhand(Item item)
    {
        this.mob.setItemSlot(EquipmentSlot.OFFHAND, stackOf(item));
    }

    /** Empties the main hand, which is what an unarmed swing looks like. */
    public void emptyMain()
    {
        this.mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Empties the offhand, which is what happens to a shield that is put away. */
    public void emptyOffhand()
    {
        this.mob.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    public void equipSword()
    {
        this.equipMain(Items.DIAMOND_SWORD);
    }

    public void equipAxe()
    {
        this.equipMain(SHIELD_BREAKER);
    }

    /** Puts the water of the emergency kit into his hand. */
    public void equipWaterBucket()
    {
        this.equipMain(WATER_BUCKET);
    }

    /** Leaves him with the empty bucket after the pour, which is what a player has left after using theirs. */
    public void equipEmptyBucket()
    {
        this.equipMain(EMPTY_BUCKET);
    }

    public void equipBow()
    {
        this.equipMain(Items.BOW);
    }

    public void equipTrident()
    {
        this.equipMain(Items.TRIDENT);
    }

    public void equipEggs()
    {
        this.equipMain(Items.EGG);
    }

    public void equipEnderPearl()
    {
        this.equipMain(Items.ENDER_PEARL);
    }

    public void equipGoldenApple(boolean enchanted)
    {
        this.equipMain(enchanted ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE);
    }

    /**
     * The grip he fights in when nothing else is going on: the sword in the main hand and the shield ready in the
     * offhand, so raising the shield is one {@code startUsingItem} and never a weapon switch first.
     */
    public void equipCombatGrip()
    {
        this.equipSword();
        this.equipOffhand(Items.SHIELD);
    }

    /** @return true when that item is really in the main hand right now. */
    public boolean mainHandIs(Item item)
    {
        return this.mob.getMainHandItem().is(item);
    }

    /** @return true when a real shield is in the offhand right now. */
    public boolean hasShield()
    {
        return this.mob.getOffhandItem().is(Items.SHIELD);
    }

    /** @return the main hand item of this body, for debug output. */
    public ItemStack mainHand()
    {
        return this.mob.getMainHandItem();
    }

    /** A fresh, single stack of that item: the stock is virtual, so a copy is always available. */
    public static ItemStack stackOf(Item item)
    {
        return new ItemStack(item);
    }
}
