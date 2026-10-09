package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.C2SStomachWallAttackPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.stomach.StomachCellManager;
import spiderqueenhungrycraft.stomach.StomachConstants;

/**
 * The client half of "attack the stomach wall" (Phase 8E1-GATE2A, repaired in 8E1-GATE2A-POLISH-1).
 *
 * <p>Inside a stomach the left button is the wall. The input is taken from the very event the vanilla client
 * fires before it acts on the left button, so a swing counts for the empty air, for the water of the cavity and
 * for the stomach wall itself.
 *
 * <p>The pace of the wall is kept by the clock of the client and not by the charge of the swing: the vanilla
 * client resets that charge on every tick in which the button is not held, so a perfectly normal click would
 * always arrive almost uncharged and be thrown away. One swing of the weapon is one request, no swing of a held
 * button can become one request per tick, and the server checks the same rhythm again with its own clock.
 *
 * <p>A creature in the crosshair always keeps the ordinary attack, and outside a stomach nothing of this class
 * reacts at all, so the beast controls and the ordinary game stay exactly as they were.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class StomachWallAttackClient
{
    /** The client tick of the last request, so the wall has exactly the rhythm of the held weapon. */
    private static long lastWallAttackTick = -1L;

    private StomachWallAttackClient()
    {
    }

    @SubscribeEvent
    public static void onAttackKey(InputEvent.InteractionKeyMappingTriggered event)
    {
        if (!event.isAttack())
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.level == null || minecraft.screen != null || minecraft.getConnection() == null)
        {
            return;
        }

        // Everywhere but inside a stomach the mouse buttons are none of this class' business.
        if (!minecraft.level.dimension().equals(StomachCellManager.DIMENSION))
        {
            return;
        }

        // A creature in the crosshair is an ordinary attack: the wall never steals it.
        if (minecraft.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity)
        {
            return;
        }

        // Whatever the swing landed on - wall, water or nothing - it is a hit on the wall and never on a block.
        event.setCanceled(true);
        event.setSwingHand(true);

        long now = minecraft.level.getGameTime();
        int minimumGap = Math.max(1,
                Mth.ceil(player.getCurrentItemAttackStrengthDelay() * StomachConstants.WALL_ATTACK_MIN_CHARGE));
        long sinceLast = lastWallAttackTick < 0L ? Long.MAX_VALUE : now - lastWallAttackTick;

        if (sinceLast < (long) minimumGap)
        {
            return;
        }

        lastWallAttackTick = now;
        player.resetAttackStrengthTicker();
        HungryCraftNetwork.sendToServer(new C2SStomachWallAttackPacket());
    }
}
