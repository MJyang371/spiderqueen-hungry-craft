package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.network.C2SBeastJumpPressPacket;
import spiderqueenhungrycraft.network.C2SBeastJumpReleasePacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;

/**
 * The charged jump input of the local beast (Phase 8C).
 *
 * <p>The space bar stops being a jump button and becomes a charge button: the press and the release are sent as
 * two single messages, and everything between them stays on the client, where only the jump meter reads it. The
 * server measures the real charge from its own clock, so the meter is a picture of the truth and never the
 * truth itself.
 *
 * <p>The vanilla jump of the transformed player is suppressed in the mixin of the form, so a press can never
 * stack the ordinary hop on top of the charged jump.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastJumpClient
{
    private static boolean jumpWasDown;
    private static int chargeTicks;

    private QueenBeastJumpClient()
    {
    }

    /** The charge of the local player as a fraction from zero to one, used by the jump meter. */
    public static float meterFraction()
    {
        int max = QueenBeastClientTuning.jumpMaxChargeTicks();

        return max <= 0 ? 0.0F : Mth.clamp(chargeTicks / (float) max, 0.0F, 1.0F);
    }

    /** True while the local beast charges a jump. */
    public static boolean isCharging()
    {
        return chargeTicks > 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean down = player != null && minecraft.screen == null && QueenBeastState.isTransformed(player)
                && minecraft.options.keyJump.isDown();
        boolean pressed = down && !jumpWasDown;
        boolean released = !down && jumpWasDown;
        jumpWasDown = down;

        if (player == null || !QueenBeastState.isTransformed(player))
        {
            chargeTicks = 0;
            return;
        }

        if (pressed)
        {
            chargeTicks = 1;
            HungryCraftNetwork.sendToServer(new C2SBeastJumpPressPacket());
        }
        else if (down)
        {
            chargeTicks = Math.min(chargeTicks + 1, QueenBeastClientTuning.jumpMaxChargeTicks() + 5);
        }
        else if (released)
        {
            chargeTicks = 0;
            HungryCraftNetwork.sendToServer(new C2SBeastJumpReleasePacket());
        }

        if (!down)
        {
            chargeTicks = 0;
        }
    }
}
