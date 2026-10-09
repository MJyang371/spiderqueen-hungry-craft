package spiderqueenhungrycraft.ability;

import java.util.UUID;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * Predator body: a bare handed Spider Queen fights like she carries a stone sword and mines like she
 * carries stone tools. Everything here only applies while the player is the queen and the main hand is
 * empty, and the attack modifiers are refreshed on a timer so they never stack.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBody
{
    /** How often the unarmed attack modifiers are checked. One second, never per tick. */
    private static final int CHECK_INTERVAL_TICKS = 20;

    /**
     * Vanilla stone sword values: {@code new SwordItem(Tiers.STONE, 3, -2.4F)}. A stone sword adds
     * 3 + 1.0 (stone tier bonus) attack damage and -2.4 attack speed, so the queen ends up with the
     * same 5.0 damage / 1.6 attack speed a stone sword gives.
     */
    private static final double UNARMED_ATTACK_DAMAGE = 4.0D;
    private static final double UNARMED_ATTACK_SPEED = -2.4D;

    private static final String UNARMED_MODIFIER_NAME = "spider_queen_unarmed";
    private static final UUID UNARMED_DAMAGE_MODIFIER_ID = UUID.fromString("6f1f2b6c-9a3d-4f8b-9c2e-1d5a7b3e4f60");
    private static final UUID UNARMED_SPEED_MODIFIER_ID = UUID.fromString("2c8d4e71-5b6a-4c3d-8e9f-a0b1c2d3e4f5");

    /** {@code Tiers.STONE} speed, which is what a stone pickaxe and a stone axe use. */
    private static final float STONE_TOOL_SPEED = 4.0F;

    private QueenBody()
    {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        Player player = event.player;
        if (event.phase != TickEvent.Phase.END || player.level().isClientSide())
        {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }

        updateUnarmedCombat(player);
    }

    /** Adds or removes the bare handed attack modifiers, never stacking them. */
    public static void updateUnarmedCombat(Player player)
    {
        boolean bareHandedQueen = QueenDataCapability.isQueen(player) && player.getMainHandItem().isEmpty();

        setModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), UNARMED_DAMAGE_MODIFIER_ID,
                UNARMED_ATTACK_DAMAGE, bareHandedQueen);
        setModifier(player.getAttribute(Attributes.ATTACK_SPEED), UNARMED_SPEED_MODIFIER_ID,
                UNARMED_ATTACK_SPEED, bareHandedQueen);
    }

    @SubscribeEvent
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event)
    {
        if (event.canHarvest() || !canUseStoneTools(event.getEntity(), event.getTargetBlock()))
        {
            return;
        }

        // The queen harvests what a stone pickaxe / stone axe would harvest, nothing above that.
        event.setCanHarvest(true);
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        if (!canUseStoneTools(event.getEntity(), event.getState()))
        {
            return;
        }

        if (event.getNewSpeed() < STONE_TOOL_SPEED)
        {
            event.setNewSpeed(STONE_TOOL_SPEED);
        }
    }

    /**
     * @return true when the bare handed queen may work on this block with stone tool level, which is
     *         everything a stone pickaxe or stone axe can do and nothing above it.
     */
    public static boolean canUseStoneTools(Player player, BlockState state)
    {
        if (!QueenDataCapability.isQueen(player) || !player.getMainHandItem().isEmpty())
        {
            return false;
        }
        if (!state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(BlockTags.MINEABLE_WITH_AXE))
        {
            return false;
        }

        // Obsidian needs a diamond tool, diamond ore needs an iron tool: out of reach for bare hands.
        return !state.is(BlockTags.NEEDS_IRON_TOOL) && !state.is(BlockTags.NEEDS_DIAMOND_TOOL);
    }

    private static void setModifier(AttributeInstance attribute, UUID id, double amount, boolean wanted)
    {
        if (attribute == null)
        {
            return;
        }

        AttributeModifier existing = attribute.getModifier(id);
        if (wanted)
        {
            if (existing == null)
            {
                attribute.addTransientModifier(new AttributeModifier(id, UNARMED_MODIFIER_NAME, amount,
                        AttributeModifier.Operation.ADDITION));
            }
        }
        else if (existing != null)
        {
            attribute.removeModifier(id);
        }
    }
}
