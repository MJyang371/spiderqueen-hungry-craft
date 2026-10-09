package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * The continuous feedback of a running charge (Phase 8D).
 *
 * <p>A charge is a long, heavy run, so its feedback is a low, constant rumble and a slightly widened lens - the
 * opposite of the short punch of a bite or a stomp. The rumble only exists while the server really runs a
 * charge: the client is told when a charge starts and when it ends, so a rejected press never rumbles.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class QueenBeastChargeClient
{
    private static boolean active;
    private static int clientTick;

    private QueenBeastChargeClient()
    {
    }

    /** The server says a charge of the local beast started or ended. */
    public static void acceptState(boolean value)
    {
        active = value;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        clientTick++;

        LocalPlayer player = Minecraft.getInstance().player;

        if (player == null || !QueenBeastState.isTransformed(player) || Minecraft.getInstance().level == null)
        {
            active = false;
        }
    }

    /** A slow, low rumble of the first person camera while the charge runs. */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event)
    {
        if (!active || !canShake())
        {
            return;
        }

        float wave = Mth.sin(clientTick * 0.9F) * 0.5F + Mth.sin(clientTick * 1.7F) * 0.25F;

        event.setPitch(event.getPitch() + QueenBeastConstants.BEAST_CHARGE_RUMBLE_PITCH * wave);
        event.setYaw(event.getYaw() + QueenBeastConstants.BEAST_CHARGE_RUMBLE_YAW * wave * 0.6F);
    }

    /** The lens widens slightly while charging, which is the speed the beast feels. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event)
    {
        if (!active || !canShake())
        {
            return;
        }

        event.setFOV(event.getFOV() + QueenBeastClientTuning.chargeFovWidening());
    }

    private static boolean canShake()
    {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.screen == null && minecraft.options.getCameraType().isFirstPerson();
    }
}
