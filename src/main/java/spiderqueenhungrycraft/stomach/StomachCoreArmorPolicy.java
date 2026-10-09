package spiderqueenhungrycraft.stomach;

import javax.annotation.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import spiderqueenhungrycraft.progression.QueenSkills;
import spiderqueenhungrycraft.registry.HungryCraftAttributes;
import spiderqueenhungrycraft.stomach.StomachCell;

/**
 * Where the armor of a stomach core comes from (Phase 8E1-GATE2B).
 *
 * <p>A core no longer copies the outer armor of its owner: the outer armor decides how much an enemy hurts the
 * queen from the outside, while the visceral armor of the owner decides how much a prey hurts her from inside her
 * own stomach. The core therefore wears the visceral armor of its owner and nothing else, and its toughness stays
 * at zero in this version.
 *
 * <p>Without an online owner the wall keeps the default visceral armor, because the visceral armor is a property
 * of the stomach itself and not of the moment somebody happens to be connected.
 */
public final class StomachCoreArmorPolicy
{
    /**
     * What the wall of a stomach is worth without the second visceral enhancement: nothing at all (Skill Progression
     * round). It is a named number and not a bare zero so the rule reads as a rule.
     */
    public static final double NO_VISCERAL_ARMOR = 0.0D;

    private StomachCoreArmorPolicy()
    {
    }

    /** Copies the current visceral armor of the owner onto the core. */
    public static void refresh(StomachCoreEntity core)
    {
        set(core, Attributes.ARMOR, visceralArmorOf(ownerOf(core)));
        set(core, Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    /**
     * Rewrites the wall of the stomach of that player right now.
     *
     * <p>It is called the moment her visceral armor is bought or taken away (Skill Progression round), so the wall she
     * already carries follows the skill without waiting for the next periodic refresh - and without her having to relog.
     * A cell that was never allocated and a cell that is not loaded are both simply left alone: the next
     * {@link #refresh} of that core reads the new value anyway.
     */
    public static void refreshForOwner(@Nullable ServerPlayer owner)
    {
        if (owner == null)
        {
            return;
        }

        MinecraftServer server = owner.getServer();
        ServerLevel stomach = StomachCellManager.stomachLevel(server);

        if (server == null || stomach == null)
        {
            return;
        }

        StomachCell cell = StomachCellManager.existingCell(server, owner.getUUID()).orElse(null);

        if (cell == null)
        {
            return;
        }

        StomachCellManager.coreOf(stomach, cell).ifPresent(StomachCoreArmorPolicy::refresh);
    }

    /**
     * @return the visceral armor of that player, or the default while they are away.
     *
     *         <p><b>The inner armor is the second visceral enhancement</b> (Skill Progression round): a queen who does
     *         not own that node has no visceral armor at all, so a prey that hurts the wall of her stomach really
     *         hurts her - while everything she wears on the outside is untouched by this and belongs to the chitin
     *         shell. The two are separate skills and separate numbers on purpose.
     *
     *         <p>A cell whose owner is offline keeps the default, exactly as before: the visceral armor is a property
     *         of the stomach itself, and the skills of a player can only be read while they are connected. Nothing
     *         about that rule changed in this round.
     */
    public static double visceralArmorOf(@Nullable ServerPlayer owner)
    {
        if (owner == null)
        {
            return HungryCraftAttributes.VISCERAL_ARMOR_DEFAULT;
        }

        if (!QueenSkills.has(owner, QueenSkills.VISCERAL_ENHANCEMENT_II))
        {
            return NO_VISCERAL_ARMOR;
        }

        AttributeInstance instance = owner.getAttribute(HungryCraftAttributes.VISCERAL_ARMOR.get());

        return instance == null ? HungryCraftAttributes.VISCERAL_ARMOR_DEFAULT : instance.getValue();
    }

    /** @return the online player that owns that core, or null while they are offline. */
    @Nullable
    private static ServerPlayer ownerOf(StomachCoreEntity core)
    {
        MinecraftServer server = core.getServer();
        java.util.UUID owner = core.ownerUUID();

        return server == null || owner == null ? null : server.getPlayerList().getPlayer(owner);
    }

    /** Writes one attribute as a base value, so no modifier can be lost or stacked twice. */
    private static void set(LivingEntity entity, Attribute attribute, double value)
    {
        AttributeInstance instance = entity.getAttribute(attribute);

        if (instance != null)
        {
            instance.setBaseValue(Math.max(0.0D, value));
        }
    }
}
