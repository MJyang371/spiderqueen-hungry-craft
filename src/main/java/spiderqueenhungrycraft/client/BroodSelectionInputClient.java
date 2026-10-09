package spiderqueenhungrycraft.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.compat.iceandfire.ClientNecromancyState;
import spiderqueenhungrycraft.network.C2SBroodSelectionPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * Client side of the brood keys (Brood Selection + Inspect HUD round, reworked by the Brood Command Group round).
 *
 * <p>Every key is read with {@code consumeClick}, so one physical press is one request and holding a key changes
 * nothing. Only the view selection has anything to work out on this side: which of the queen's spiders are actually on
 * screen. That is done at the moment of the press - a walk through the spiders the client has loaded, a frustum test and
 * a line of sight check per spider - and never per tick.
 *
 * <p>Every key is silent without the sceptre in a hand, so an ordinary walk through the world cannot reorder a queen's
 * brood by accident. What a key does to the selection is decided on the server alone; this side only asks.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class BroodSelectionInputClient
{
    private BroodSelectionInputClient()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        while (HungryCraftKeyMappings.SELECT_VISIBLE.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.SELECT_VISIBLE, visibleCandidates());
            }
        }

        while (HungryCraftKeyMappings.RALLY_HOME.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.RALLY, NO_CANDIDATES);
            }
        }

        while (HungryCraftKeyMappings.SELECT_SAME_TYPES.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.SELECT_SAME_TYPES, NO_CANDIDATES);
            }
        }

        while (HungryCraftKeyMappings.CYCLE_ORDER.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.CYCLE_ORDER, NO_CANDIDATES);
            }
        }

        while (HungryCraftKeyMappings.FREE_ATTACK.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.FREE_ATTACK, NO_CANDIDATES);
            }
        }

        while (HungryCraftKeyMappings.HOLD_SELECTED.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.HOLD_SELECTED, NO_CANDIDATES);
            }
        }

        while (HungryCraftKeyMappings.FOLLOW_SELECTED.consumeClick())
        {
            if (canCommand())
            {
                sendSelection(C2SBroodSelectionPacket.Action.FOLLOW_SELECTED, NO_CANDIDATES);
            }
        }
    }

    private static final int[] NO_CANDIDATES = new int[0];

    /**
     * Sends one brood action. The command wheel of the sceptre uses this very method (Command Wheel round), so a command
     * cast from the wheel and the same command cast from its key are one and the same packet.
     */
    static void sendSelection(C2SBroodSelectionPacket.Action action, int[] candidates)
    {
        HungryCraftNetwork.sendToServer(new C2SBroodSelectionPacket(action, candidates));
    }

    /** @return true when this player may command at all: in the world, no menu open and sceptre in hand. */
    static boolean canCommand()
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null || minecraft.getConnection() == null || minecraft.screen != null)
        {
            return false;
        }

        for (ItemStack stack : player.getHandSlots())
        {
            if (stack.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * @return the ids of the queen's own spiders the client believes are on screen right now: inside the camera frustum
     *         and, when the check is cheap, actually in sight of the player. The server verifies every id again.
     */
    static int[] visibleCandidates()
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null)
        {
            return NO_CANDIDATES;
        }

        Frustum frustum = minecraft.levelRenderer.getFrustum();
        List<Integer> ids = new ArrayList<>();

        for (Entity entity : level.entitiesForRendering())
        {
            if (entity instanceof SpiderMinionEntity spider && spider.isOwnedBy(player)
                    && frustum.isVisible(spider.getBoundingBox()) && player.hasLineOfSight(spider))
            {
                ids.add(spider.getId());
            }
        }

        // And the contract dragons of Ice and Fire (Necromancy IV round 5). A client can not know who owns a dragon -
        // the answer of the server about that is sent to the owner alone - so the only dragons this list can honestly
        // propose are the ones the server has already told this client are <b>hers</b>
        // ({@link ClientNecromancyState.Mirror#mine()}), and they are proposed on the very same terms as the spiders:
        // inside the camera frustum and in sight of the player. The server verifies every id again, so a client that
        // lied here would still be refused.
        for (Integer id : ClientNecromancyState.snapshot().keySet())
        {
            ClientNecromancyState.Mirror mirror = ClientNecromancyState.of(id);
            Entity entity = level.getEntity(id);

            if (mirror == null || !mirror.mine() || entity == null)
            {
                continue;
            }

            if (frustum.isVisible(entity.getBoundingBox()) && player.hasLineOfSight(entity))
            {
                ids.add(entity.getId());
            }
        }

        int[] result = new int[ids.size()];

        for (int index = 0; index < ids.size(); index++)
        {
            result[index] = ids.get(index);
        }

        return result;
    }
}
