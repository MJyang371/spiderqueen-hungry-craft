package spiderqueenhungrycraft.ability;

import java.util.UUID;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * Basic Spider Queen physiology that has to be refreshed by the server:
 * <ul>
 *     <li>light dependent vision: short hidden night vision in the dark, nothing in between</li>
 *     <li>the light sensitivity of a bright light, a slow ladder of debuffs that is owned by
 *     {@link QueenLightSensitivity}</li>
 * <li>inherent armor: a full set of leather worth, always in place and always on top of what she wears</li>
 * </ul>
 * All of them are driven by a single check every {@link #CHECK_INTERVAL_TICKS} ticks, never per tick, and all
 * of them are only handed out while the player is the Spider Queen.
 *
 * <p>Since feature LIGHT-SENSITIVITY the queen is no longer blinded the instant she steps into the light:
 * this class only keeps the dark side (the hidden night vision) and hands the light the queen stands in to
 * {@link QueenLightSensitivity}, which starts the countdown the queen reads on her HUD.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenPhysiology
{
    /** How often a player is checked. All of this only needs to react within a second or so. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    /**
     * Duration of the refreshed effects. Kept above 200 ticks on purpose: night vision starts pulsing
     * on the client once the remaining duration drops below 200 ticks.
     */
    private static final int EFFECT_DURATION_TICKS = 400;

    /** An effect that is still around is refreshed once its remaining duration drops below this. */
    private static final int EFFECT_REFRESH_BELOW_TICKS = 300;

    // The strong light of the world is no longer handed out by this class as a blindness: since feature
    // LIGHT-SENSITIVITY the light is handed to QueenLightSensitivity, which keeps the countdown and the
    // ladder of debuffs that comes with it.

    /** Light levels 0..7 count as dark. */
    public static final int DARKEST_LIGHT_LEVEL = 7;

    /** Light levels 12..15 count as bright, the levels the light sensitivity of the queen starts at. */
    public static final int BRIGHTEST_LIGHT_LEVEL = 12;

    private static final UUID INHERENT_ARMOR_MODIFIER_ID =
            UUID.fromString("7d4f0d2a-0e64-4a2f-9c3c-6b4f1f2a5e10");
    private static final String INHERENT_ARMOR_MODIFIER_NAME = "spider_queen_inherent_armor";
    private static final double INHERENT_ARMOR = 7.0D;

    private QueenPhysiology()
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

        if (QueenDataCapability.isQueen(player))
        {
            int lightLevel = lightLevelAt(player);
            updateLightVision(player, lightLevel);
            QueenLightSensitivity.update(player, lightLevel);
            updateArmor(player);

            // The visceral enhancement of the queen (Skill Progression round): a queen who owns it can never be
            // poisoned and never suffers the status effect of hunger, and both are removed here as well, so a poison
            // that was applied before the node was bought does not linger for its full duration. Re-applying is
            // impossible while she owns the node, because the application itself is refused (QueenImmunity), so this
            // can not flicker.
            QueenImmunity.clearManagedEffects(player);
        }
        else
        {
            clear(player);
        }
    }

    private static int lightLevelAt(Player player)
    {
        return player.level().getMaxLocalRawBrightness(player.blockPosition());
    }

    /**
     * @param lightLevel the light the player stands in
     * @return the effect this light level calls for, or null when nothing should be granted
     */
    public static MobEffect lightVisionEffect(int lightLevel)
    {
        return lightLevel <= DARKEST_LIGHT_LEVEL ? MobEffects.NIGHT_VISION : null;
    }

    /**
     * Applies the night vision of the dark and drops the managed night vision that no longer belongs.
     * Re-applying is safe: an effect that was cleared in between (milk) is granted again.
     *
     * <p>The bright side of the light is not handled here any more. Since feature LIGHT-SENSITIVITY it is the
     * countdown of {@link QueenLightSensitivity} that decides what a bright light does to the queen, and the
     * potion of day vision is read there as well.
     */
    public static void updateLightVision(Player player, int lightLevel)
    {
        MobEffect wanted = lightVisionEffect(lightLevel);
        discardUnwantedEffect(player, wanted, MobEffects.NIGHT_VISION);

        if (wanted == MobEffects.NIGHT_VISION)
        {
            refreshEffect(player, wanted, EFFECT_DURATION_TICKS, EFFECT_REFRESH_BELOW_TICKS);
        }
    }

    /**
     * Keeps the inherent armor of the queen in place: her body alone is worth a full set of leather armor (seven points)
     * and it is <b>always</b> there, whatever she wears over it.
     *
     * <p>Since the Skill Progression round it is the <b>chitin shell</b> of the queen: the whole of this method is
     * handed out to a queen who owns that node and taken away from one who does not, so the seven points are a bought
     * part of her body and no longer a gift of the queen flag itself.
     *
     * <p>It is one addition on the armor attribute, so everything she puts on comes <b>on top of it</b>: a leather helmet
     * makes it eight, a full set of leather fourteen, a full set of iron twenty two, a full set of diamond twenty seven -
     * and a queen in nothing but her own skin has her seven. That is what "inherent" means here.
     *
     * <p>It used to mean the opposite: the modifier was only handed out while <b>not a single</b> armor piece was worn,
     * so a queen who put on one pair of boots lost the whole seven points of her own body and was thinner than she was
     * before she dressed. The condition is gone, and the armor of a queen therefore never depends on her equipment.
     *
     * <p>Adding it is idempotent: the modifier of this class is looked up by its id first, so this can run on every
     * check interval for the life of the world without ever stacking a second copy of itself.
     */
    public static void updateArmor(Player player)
    {
        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);

        if (armor == null)
        {
            return;
        }

        // The plating of her own body is the chitin shell of the queen (Skill Progression round): without that node
        // she has none of it, and the modifier is taken away again the moment she loses the node. That check runs on
        // the same one-second rhythm as everything else here, so a purchase or a revocation takes effect right away
        // and never needs a relog.
        if (!QueenSkills.has(player, QueenSkills.CHITIN_SHELL))
        {
            if (armor.getModifier(INHERENT_ARMOR_MODIFIER_ID) != null)
            {
                armor.removeModifier(INHERENT_ARMOR_MODIFIER_ID);
            }

            return;
        }

        if (armor.getModifier(INHERENT_ARMOR_MODIFIER_ID) != null)
        {
            return;
        }

        armor.addTransientModifier(new AttributeModifier(INHERENT_ARMOR_MODIFIER_ID, INHERENT_ARMOR_MODIFIER_NAME,
                INHERENT_ARMOR, AttributeModifier.Operation.ADDITION));
    }

    /**
     * Removes everything this class hands out, and everything the light sensitivity of the queen handed out
     * with it. Used the moment a player stops being the Spider Queen, so neither the modifier nor the vision
     * effects nor the marker of the light are left behind. Effects granted by other sources are untouched.
     */
    public static void clear(Player player)
    {
        removeManagedEffect(player, MobEffects.NIGHT_VISION);
        QueenLightSensitivity.clear(player);

        AttributeInstance armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.getModifier(INHERENT_ARMOR_MODIFIER_ID) != null)
        {
            armor.removeModifier(INHERENT_ARMOR_MODIFIER_ID);
        }
    }

    private static void discardUnwantedEffect(Player player, MobEffect wanted, MobEffect managed)
    {
        if (managed == wanted)
        {
            return;
        }
        removeManagedEffect(player, managed);
    }

    private static void refreshEffect(Player player, MobEffect effect, int durationTicks, int refreshBelowTicks)
    {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && isManagedEffect(current, effect) && !current.endsWithin(refreshBelowTicks))
        {
            return;
        }
        player.addEffect(hiddenEffectInstance(effect, durationTicks));
    }

    private static void removeManagedEffect(Player player, MobEffect effect)
    {
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && isManagedEffect(current, effect))
        {
            player.removeEffect(effect);
        }
    }

    private static MobEffectInstance hiddenEffectInstance(MobEffect effect, int durationTicks)
    {
        // ambient = false, particles = false, HUD icon = false
        return new MobEffectInstance(effect, durationTicks, 0, false, false, false);
    }

    /** @return true if the instance is one of the invisible instances this class grants itself. */
    private static boolean isManagedEffect(MobEffectInstance instance, MobEffect effect)
    {
        return instance.getEffect() == effect
                && !instance.isAmbient()
                && !instance.isVisible()
                && !instance.showIcon()
                && instance.getAmplifier() == 0;
    }
}
