package spiderqueenhungrycraft.attack;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * Applies the empowered melee hit (the Venom Strike of the ordinary form). It only changes the damage amount
 * and adds the venom debuffs, which the shared {@link VenomStrike} helper owns.
 *
 * <p>The beast form is excluded on purpose: a beast bite releases a prepared strike itself, once for its
 * whole area of effect, so the ordinary melee path must never touch it. Without this guard the first victim
 * of an area bite would eat the charge and every other victim would only get the plain damage.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class EmpoweredAttackEvents
{
    private EmpoweredAttackEvents()
    {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide)
        {
            return;
        }

        // Only a real melee swing counts, arrows and other projectiles are ignored.
        Entity direct = event.getSource().getDirectEntity();
        if (!(direct instanceof ServerPlayer))
        {
            return;
        }

        ServerPlayer player = (ServerPlayer) direct;
        if (target == player)
        {
            return;
        }

        // The beast bite owns the venom handling of the beast form, this path stays the ordinary melee one.
        if (QueenBeastState.isTransformed(player))
        {
            return;
        }

        if (!EmpoweredAttackHandler.consumeArmed(player))
        {
            return;
        }

        event.setAmount(event.getAmount() * VenomStrike.damageMultiplier());
        VenomStrike.applyEffects(target);
        player.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.empowered_used"), true);
    }
}
