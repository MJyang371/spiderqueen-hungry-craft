package spiderqueenhungrycraft.beast;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CBeastHurtFeedbackPacket;

/**
 * The incoming damage feedback of the Queen Beast form (Phase 8B-POLISH-2).
 *
 * <p>{@link LivingDamageEvent} is the only Forge hook that sees the real health loss: the vanilla pipeline
 * runs the armor absorb, the magic and resistance absorb and the absorption hearts first, and the amount that
 * arrives here is exactly what is subtracted from the health bar afterwards. Judging the small hit here means
 * judging what the beast really lost, not what a zombie swung at it on paper.
 *
 * <p>A small hit is never cancelled: the damage, the invulnerability frames, the death, the retaliation and
 * the pack aggro all stay untouched, and only the hurt camera of the player is asked to stay calm. That
 * suppression is a client decision, so the server sends its verdict and lets the client apply it.
 *
 * <p>The beast itself is the victim here. When the beast bites somebody, the entity of this event is that
 * victim, so the shake of the own attack can never be eaten by this filter.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class QueenBeastDamageFeedback
{
    private QueenBeastDamageFeedback()
    {
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !QueenBeastState.isTransformed(player))
        {
            return;
        }

        float finalDamage = event.getAmount();
        boolean smallHit = finalDamage > 0.0F && finalDamage <= QueenBeastConstants.BEAST_SMALL_HIT_THRESHOLD;

        HungryCraftNetwork.sendToPlayer(new S2CBeastHurtFeedbackPacket(player.getId(), smallHit, finalDamage),
                player);
    }
}
