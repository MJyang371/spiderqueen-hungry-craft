package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.C2SDevourInputPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * Client side of the devour key (Phase 8E2A).
 *
 * <p>It reports the two edges of one physical key and nothing else. It never asks whether the player is the
 * queen or a beast, and it never decides what the key means: that is exactly the mistake that would make the
 * key depend on a cached client state. The server decides everything, the client only refuses to send while a
 * screen is open, which keeps the key out of chat and menus.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class DevourInputClient
{
    /** Press edge memory of the key, so one physical press is exactly one press message. */
    private static boolean wasDown;

    private DevourInputClient()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null || minecraft.getConnection() == null)
        {
            wasDown = false;
            return;
        }

        // The throat of the beast is a bought skill (Skill Progression round): while the player does not own it, the
        // key is a silent no-op - no press, no release, no message, nothing. The press edge memory is forgotten with
        // it, so a press that was never sent can never turn into a release message later either.
        if (!SkillClientState.has(QueenSkills.GIANT_DEVOUR))
        {
            wasDown = false;
            return;
        }

        boolean down = minecraft.screen == null && HungryCraftKeyMappings.DEVOUR_REGURGITATE.isDown();

        if (down == wasDown)
        {
            return;
        }

        wasDown = down;
        HungryCraftNetwork.sendToServer(new C2SDevourInputPacket(
                down ? C2SDevourInputPacket.Action.PRESS : C2SDevourInputPacket.Action.RELEASE));
    }
}
