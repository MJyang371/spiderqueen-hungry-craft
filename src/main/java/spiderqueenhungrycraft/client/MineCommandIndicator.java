package spiderqueenhungrycraft.client;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.QueenCommandRay;
import spiderqueenhungrycraft.brood.SpiderMineTargeting;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * The mines a click of the sceptre would fire right now, on the client of the queen (Spider Trigger round follow-up).
 *
 * <p>A queen who aims at her own floor has no way of knowing that a mine is hiding under the block she is pointing at,
 * and the command that fires it is the widest of the sceptre - four blocks of aim. So once per tick this class asks the
 * very same question the server asks for a real click ({@link SpiderMineTargeting#minesAt}) and hands the answer to
 * {@link MineCommandIndicatorRenderer}, which marks every one of those mines where it stands.
 *
 * <p>It is purely local: not one packet is sent, nothing is synchronised, and no other player sees a thing. Only the
 * mines the crosshair is <b>about to fire</b> are marked, which is exactly the information a queen needs before she
 * presses the button.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MineCommandIndicator
{
    /** The mines of the current tick, nearest first. */
    private static List<BlockPos> mines = List.of();

    /** The level those mines belong to, so a mark is never drawn in the next world. */
    @Nullable
    private static ClientLevel trackedLevel;

    private MineCommandIndicator()
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
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null || minecraft.screen != null || !holdsScepter(player))
        {
            forget();

            return;
        }

        // The point the crosshair indicates, from the very ray every command of the sceptre is decided with.
        Vec3 point = QueenCommandRay.cursorPoint(player);

        mines = SpiderMineTargeting.minesAt(level, point);
        trackedLevel = level;
    }

    /** @return the mines to mark right now, nearest first, or an empty list when there is nothing to mark. */
    public static List<BlockPos> minesToMark(@Nullable ClientLevel level)
    {
        return level == null || level != trackedLevel ? List.of() : mines;
    }

    /** Forgets everything, used when the sceptre leaves the hand, a screen opens or the world goes away. */
    public static void forget()
    {
        mines = List.of();
        trackedLevel = null;
    }

    /** @return true when the sceptre is in one of the hands of that player: the indicator is about her command. */
    private static boolean holdsScepter(LocalPlayer player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack held = player.getItemInHand(hand);

            if (held.is(HungryCraftItems.QUEEN_SCEPTER.get()))
            {
                return true;
            }
        }

        return false;
    }
}
