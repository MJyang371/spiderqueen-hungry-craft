package spiderqueenhungrycraft.attack;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.ai.HumanoidFighter;
import spiderqueenhungrycraft.brood.BroodBodyFighter;

/**
 * The axe against the shield of a combatant (round 8, the shield that actually blocks).
 *
 * <p>The game only ever smashes the shield of a <b>player</b>. The whole rule sits in {@code Player#blockUsingShield}:
 * it asks the weapon in the hand of the attacker whether it can disable a shield ({@code ItemStack#canDisableShield},
 * which every axe answers with yes) and then takes the shield of the player away for a hundred ticks. A mob that
 * blocks is never asked that question at all, so a human combatant or an infested Steve could block an axe with its
 * shield forever - exactly what the queen noticed, and what this class fixes. Every body of this mod that fights with
 * a shield is now held to the same rule a player lives by.
 *
 * <p>The hook is {@link LivingAttackEvent} on purpose. It is the first thing the hurt pipeline of the game fires -
 * {@code LivingEntity#hurt} asks the event bus before it even looks at the shield - so the shield is still up and its
 * state is the state before this hit. The hit itself is left alone and is still blocked by the game: exactly like a
 * player, a combatant whose shield is smashed by an axe blocks that very hit and loses the shield afterwards. Only a
 * hit that the shield really blocks counts ({@code isDamageSourceBlocked}), so an axe swung at the back of a
 * combatant neither blocks nor smashes anything - the rule is the vanilla one, not a weaker copy of it.
 *
 * <p>The smashed state lives in the persistent data of the victim for {@link #DISABLED_TICKS}. It needs no field, no
 * interface and no cap anywhere else, it survives a save, and it disappears with the body it belongs to. The brains
 * ask for it through {@code HumanoidFighter#canGuard()} and {@code BroodBodyFighter#canGuard()}.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class ShieldBreak
{
    /** Temporary diagnostic switch of this round: one line per smashed shield, never one per hit. */
    public static final boolean DEBUG = true;

    /** 100 ticks = 5 seconds, the exact time the shield of a player stays down after an axe hit. */
    public static final int DISABLED_TICKS = 100;

    /** The vanilla chance of a hit that went through the shield without an axe. */
    public static final float BASE_CHANCE = 0.25F;

    /** The vanilla bonus of an axe: with it the chance reaches one, so an axe hit never fails to smash. */
    public static final float AXE_BONUS = 0.75F;

    /** The vanilla bonus per level of Efficiency, the same enchantment the game looks at for a player. */
    public static final float EFFICIENCY_BONUS = 0.05F;

    private static final String TAG_UNTIL = SpiderQueenHungryCraft.MODID + ":shield_disabled_until";

    private ShieldBreak()
    {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event)
    {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide || !isFighter(victim) || !victim.isBlocking())
        {
            return;
        }

        if (!(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == victim)
        {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();

        // The forge hook of the game, so a modded axe counts exactly like the vanilla one does.
        if (!weapon.canDisableShield(victim.getUseItem(), victim, attacker))
        {
            return;
        }

        // Everything about the shield is written down from here on, so a shield that does not break can be told
        // apart from a shield that was never really up (round 8 diagnosis, see {@link #DEBUG}).
        boolean holding = victim.isUsingItem();
        boolean blocking = victim.isBlocking();
        boolean front = blocking && victim.isDamageSourceBlocked(event.getSource());

        if (DEBUG)
        {
            SpiderQueenHungryCraft.LOGGER.info(
                    "[Shield] axe hit on {} by {}: holding a shield={}, blocking={}, took it at the front={}",
                    victim.getName().getString(), attacker.getName().getString(), holding, blocking, front);
        }

        // Only a hit the shield really blocked reaches the disabling rule, which is what vanilla does as well.
        if (!front)
        {
            return;
        }

        float chance = BASE_CHANCE + AXE_BONUS + EFFICIENCY_BONUS * (float) EnchantmentHelper.getBlockEfficiency(victim);

        if (victim.getRandom().nextFloat() >= chance)
        {
            return;
        }

        smash(victim, attacker);
    }

    /**
     * Takes the shield of that body away for {@link #DISABLED_TICKS}, the way an axe does it to a player: the use of
     * the shield ends, the marker byte of a broken shield is broadcast and the sound is played for everyone nearby.
     */
    public static void smash(LivingEntity victim, LivingEntity attacker)
    {
        victim.getPersistentData().putLong(TAG_UNTIL, victim.level().getGameTime() + DISABLED_TICKS);
        victim.stopUsingItem();
        victim.level().broadcastEntityEvent(victim, (byte) 30);
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SHIELD_BREAK,
                victim.getSoundSource(), 0.8F, 0.8F + victim.getRandom().nextFloat() * 0.4F);

        if (DEBUG)
        {
            SpiderQueenHungryCraft.LOGGER.info("[Shield] the shield of {} was smashed by {}",
                    victim.getName().getString(), attacker.getName().getString());
        }
    }

    /** @return true while the shield of that body is smashed and may not be raised again. */
    public static boolean isDisabled(LivingEntity victim)
    {
        CompoundTag data = victim.getPersistentData();

        if (!data.contains(TAG_UNTIL))
        {
            return false;
        }

        if (victim.level().getGameTime() >= data.getLong(TAG_UNTIL))
        {
            data.remove(TAG_UNTIL);
            return false;
        }

        return true;
    }

    /** @return true for the bodies of this mod that fight our fights: they are the ones with a shield to smash. */
    private static boolean isFighter(LivingEntity victim)
    {
        return victim instanceof HumanoidFighter || victim instanceof BroodBodyFighter;
    }
}
