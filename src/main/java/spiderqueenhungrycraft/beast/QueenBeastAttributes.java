package spiderqueenhungrycraft.beast;

import java.util.UUID;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

/**
 * The attribute modifiers of the Queen Beast form (Phase 8A, extended by the locomotion and the weight of
 * phase 8B2 / 8B-POLISH-1): the armor of the chitin shell, the longer reach of the claws, the heavy but fast
 * pace of a giant spider and the sheer mass that ignores ordinary knockback.
 *
 * <p>Every modifier has a fixed id and is transient, so applying it twice can never stack it, a login, a
 * respawn or a dimension change reconciles instead of duplicating, and leaving the form removes exactly the
 * modifiers this class handed out. Nothing else is touched: no armor toughness, no resistance and no damage
 * reduction are added, and speed potions, slowness or modifiers of other mods keep working normally.
 */
public final class QueenBeastAttributes
{
    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADDITION;
    private static final AttributeModifier.Operation MULTIPLY_BASE = AttributeModifier.Operation.MULTIPLY_BASE;

    private static final UUID ARMOR_MODIFIER_ID = UUID.fromString("1b2f6a41-6a4c-4c0e-9f3a-7c5d0a1e2b30");
    private static final String ARMOR_MODIFIER_NAME = "spider_queen_beast_armor";

    private static final UUID BLOCK_REACH_MODIFIER_ID = UUID.fromString("2c3a7b52-7b5d-4d1f-8e4b-8d6e1b2f3c41");
    private static final String BLOCK_REACH_MODIFIER_NAME = "spider_queen_beast_block_reach";

    private static final UUID ENTITY_REACH_MODIFIER_ID = UUID.fromString("3d4b8c63-8c6e-4e20-9f5c-9e7f2c3a4d52");
    private static final String ENTITY_REACH_MODIFIER_NAME = "spider_queen_beast_entity_reach";

    private static final UUID MOVEMENT_MODIFIER_ID = UUID.fromString("4e5c9d74-9d7f-4f31-a06d-af803d4b5e63");
    private static final String MOVEMENT_MODIFIER_NAME = "spider_queen_beast_movement_speed";

    private static final UUID KNOCKBACK_MODIFIER_ID = UUID.fromString("5f6dae85-ae80-4a42-b17e-bf914e5c6f74");
    private static final String KNOCKBACK_MODIFIER_NAME = "spider_queen_beast_knockback_resistance";

    private QueenBeastAttributes()
    {
    }

    /** Applies the beast modifiers. Safe to call again at any time, existing modifiers are never stacked. */
    public static void apply(Player player)
    {
        add(player, Attributes.ARMOR, ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME, QueenBeastTuning.get().armorBonus(),
                ADD);
        add(player, ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_MODIFIER_ID, BLOCK_REACH_MODIFIER_NAME,
                QueenBeastConstants.BEAST_BLOCK_REACH_BONUS, ADD);
        add(player, ForgeMod.ENTITY_REACH.get(), ENTITY_REACH_MODIFIER_ID, ENTITY_REACH_MODIFIER_NAME,
                QueenBeastConstants.BEAST_ENTITY_REACH_BONUS, ADD);
        add(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER_ID, MOVEMENT_MODIFIER_NAME,
                QueenBeastConstants.BEAST_MOVEMENT_SPEED_MULTIPLIER, MULTIPLY_BASE);
        add(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER_ID, KNOCKBACK_MODIFIER_NAME,
                QueenBeastConstants.BEAST_KNOCKBACK_RESISTANCE_BONUS, ADD);
    }

    /**
     * Re-applies the armor of the current datapack tuning (Phase 8B-POLISH-2), used after a {@code /reload}.
     *
     * <p>The old modifier is removed before the new one is added and both share the very same id, so a relic
     * armor value can never survive next to the new one: a beast that is already transformed simply has the
     * new armor a moment later, without leaving and re-entering the form.
     */
    public static void reconcileArmor(Player player)
    {
        remove(player, Attributes.ARMOR, ARMOR_MODIFIER_ID);
        add(player, Attributes.ARMOR, ARMOR_MODIFIER_ID, ARMOR_MODIFIER_NAME, QueenBeastTuning.get().armorBonus(),
                ADD);
    }

    /** Removes the beast modifiers. Safe to call for a player who never had them. */
    public static void remove(Player player)
    {
        remove(player, Attributes.ARMOR, ARMOR_MODIFIER_ID);
        remove(player, ForgeMod.BLOCK_REACH.get(), BLOCK_REACH_MODIFIER_ID);
        remove(player, ForgeMod.ENTITY_REACH.get(), ENTITY_REACH_MODIFIER_ID);
        remove(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER_ID);
        remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER_ID);
    }

    private static void add(Player player, Attribute attribute, UUID id, String name, double amount,
            AttributeModifier.Operation operation)
    {
        AttributeInstance instance = player.getAttribute(attribute);

        if (instance == null || instance.getModifier(id) != null)
        {
            return;
        }

        instance.addTransientModifier(new AttributeModifier(id, name, amount, operation));
    }

    private static void remove(Player player, Attribute attribute, UUID id)
    {
        AttributeInstance instance = player.getAttribute(attribute);

        if (instance != null && instance.getModifier(id) != null)
        {
            instance.removeModifier(id);
        }
    }
}
