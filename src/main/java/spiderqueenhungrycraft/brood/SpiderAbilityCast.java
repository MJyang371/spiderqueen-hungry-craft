package spiderqueenhungrycraft.brood;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import spiderqueenhungrycraft.brood.ability.SpiderAbilityContext;
import spiderqueenhungrycraft.compat.iceandfire.IceAndFireSupport;
import spiderqueenhungrycraft.compat.iceandfire.NecromancyOrders;
import spiderqueenhungrycraft.registry.HungryCraftItems;
import spiderqueenhungrycraft.soulfire.SoulfireDragonEntity;
import spiderqueenhungrycraft.soulfire.SoulfireOrders;

/**
 * The broadcast of the brood ability key (Brood Ability framework), and the only place where that key means anything.
 *
 * <p>What this class does <b>not</b> do is as important as what it does: it never asks what kind of spider answers, and
 * it never decides what an ability is. It only builds one context from the command ray of the sceptre and tells the
 * spiders the queen has <b>selected</b> to try their own ability (see {@link SpiderMinionEntity#tryActivateAbility}).
 *
 * <p>An ability is an <b>event</b> and not an order: a cast never touches the persistent order of a spider, never drops a
 * scout move and never ends a forced fight. A zombie spider that was sent at an enemy goes berserk in the middle of that
 * fight and keeps hitting the same victim afterwards.
 */
public final class SpiderAbilityCast
{
    private SpiderAbilityCast()
    {
    }

    /**
     * Casts the ability of every unit that answers: same dimension, loaded, owned by this queen and <b>selected by
     * her</b>.
     *
     * <p>The key obeys the selection exactly like every other command of the sceptre (Brood Command Group round), and a
     * spider she picked answers whatever its persistent order is - a garrison that was selected goes berserk where it
     * stands, and nothing else moves. With nothing selected there is nobody to ask, and that is said out loud instead of
     * being guessed at: a queen is never left wondering whether her key is broken.
     *
     * <p><b>The contract dragons answer it as well</b> (Necromancy IV round): the ability of a skeleton is the
     * <b>bombardment</b> - it flies to the place the sceptre of its queen points at and breathes on it until she gives
     * it another order. A skeleton that is too young to breathe simply does not answer, exactly like a spider without an
     * ability, and its inspect panel says "Skill: None" for the same reason an imp spider does.
     *
     * <p>Nothing is force loaded, nothing is searched every tick and no unit outside the loaded world is touched: the
     * group is resolved exactly once, at the moment the key was pressed.
     *
     * @return how many units really cast an ability
     */
    public static int cast(ServerPlayer queen)
    {
        // The sceptre is the voice of the queen: without it in one of her hands the key is simply silent.
        if (!holdsScepter(queen))
        {
            return 0;
        }

        List<SpiderMinionEntity> brood = SpiderCommand.commanded(queen);
        List<Entity> dragons = NecromancyOrders.commanded(queen);
        List<SoulfireDragonEntity> soulfires = SoulfireOrders.commanded(queen);

        if (brood.isEmpty() && dragons.isEmpty() && soulfires.isEmpty())
        {
            BroodSelection.tellNobodyAnswered(queen);

            return 0;
        }

        SpiderAbilityContext context = SpiderAbilityContext.of(queen);
        int cast = 0;
        int bombarding = 0;

        for (SpiderMinionEntity spider : brood)
        {
            if (spider.tryActivateAbility(context))
            {
                cast++;
            }
        }

        for (Entity dragon : dragons)
        {
            if (IceAndFireSupport.canBreathe(dragon) && context.hasPosition() && context.targetPosition() != null)
            {
                NecromancyOrders.bombard(dragon, BlockPos.containing(context.targetPosition()));
                bombarding++;
            }
        }

        for (SoulfireDragonEntity dragon : soulfires)
        {
            // The soul fire dragon of round 7C answers the very same key, and its ability is the breath: the place the
            // sceptre of the server pointed at becomes the place it sprays, until another order arrives.
            if (context.hasPosition() && context.targetPosition() != null
                    && SoulfireOrders.breathAt(dragon, BlockPos.containing(context.targetPosition())))
            {
                bombarding++;
            }
        }

        if (cast > 0)
        {
            queen.level().playSound(null, queen.getX(), queen.getY(), queen.getZ(), SoundEvents.SPIDER_AMBIENT,
                    SoundSource.PLAYERS, 1.0F, 0.6F);
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.brood_ability_cast", cast));
        }

        if (bombarding > 0)
        {
            tell(queen, Component.translatable("message.spiderqueen_hungrycraft.necromancy.command_bombard"));
        }

        // A key press that reached nobody stays quiet on purpose: units without an ability are not failures.
        return cast + bombarding;
    }

    /** @return true when the sceptre is in one of the hands of that queen. */
    private static boolean holdsScepter(ServerPlayer queen)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = queen.getItemInHand(hand);

            if (held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }

    /** Action bar feedback, and nothing for a player without a connection (a fake player of a test). */
    private static void tell(ServerPlayer queen, Component message)
    {
        if (queen.connection != null)
        {
            queen.displayClientMessage(message, true);
        }
    }
}
