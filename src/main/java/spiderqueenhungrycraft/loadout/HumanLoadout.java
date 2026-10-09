package spiderqueenhungrycraft.loadout;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * What one prepared human carries (Phase HF0C). It is rolled exactly once, when the human first enters the world, and
 * from then on it lives in the nbt of that entity.
 *
 * <p>Empty pieces are normal and on purpose: a human may have no shield, no bow, no blade or no golden apple at all.
 */
public final class HumanLoadout
{
    private final Map<EquipmentSlot, ItemStack> armor;
    private final ItemStack shield;
    private final ItemStack meleeWeapon;
    private final ItemStack bow;
    private final int goldenApples;

    public HumanLoadout(Map<EquipmentSlot, ItemStack> armor, ItemStack shield, ItemStack meleeWeapon, ItemStack bow,
            int goldenApples)
    {
        this.armor = armor;
        this.shield = shield;
        this.meleeWeapon = meleeWeapon;
        this.bow = bow;
        this.goldenApples = goldenApples;
    }

    /** @return the armour pieces by slot, always the four armour slots at most. */
    public Map<EquipmentSlot, ItemStack> armor()
    {
        return this.armor;
    }

    public ItemStack shield()
    {
        return this.shield;
    }

    public ItemStack meleeWeapon()
    {
        return this.meleeWeapon;
    }

    public ItemStack bow()
    {
        return this.bow;
    }

    public int goldenApples()
    {
        return this.goldenApples;
    }

    /** @return an empty loadout, used when no table could be read at all. */
    public static HumanLoadout empty()
    {
        return new HumanLoadout(new EnumMap<>(EquipmentSlot.class), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                0);
    }
}
