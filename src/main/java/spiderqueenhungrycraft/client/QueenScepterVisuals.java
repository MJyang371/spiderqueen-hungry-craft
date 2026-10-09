package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.brood.QueenCommandRay;
import spiderqueenhungrycraft.network.S2CQueenScepterPacket;
import spiderqueenhungrycraft.registry.HungryCraftItems;

/**
 * Everything the queen sees of her sceptre, on her own client (Queen Scepter command patch).
 *
 * <p>Three things, all of them purely local:
 * <ul>
 * <li><b>the hover outline</b>: once per tick the ray of {@link QueenCommandRay} is cast locally and whatever creature
 * the crosshair points at is outlined. Not a single packet is sent for this, and a queen's own spider is outlined too -
 * it is just never commanded at,</li>
 * <li><b>the attack confirmation</b>: {@link #ATTACK_CONFIRM_TICKS} ticks of outline on the victim the server really
 * accepted an order for,</li>
 * <li><b>the command marker</b>: the spot of the last accepted scout move, drawn by {@link QueenCommandMarkerRenderer}.
 * </li>
 * </ul>
 *
 * <p>The outline is a drawing of this client and nothing else: no glowing flag, no potion, no entity data, no server
 * state is ever touched for it, so no other player can see this preview and no glowing state of the game or of another
 * mod is disturbed. What the crosshair points at is remembered here and painted by
 * {@link QueenScepterOutlineRenderer}.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class QueenScepterVisuals
{
    /** How long an accepted attack order keeps its extra outline: four seconds. */
    public static final int ATTACK_CONFIRM_TICKS = 80;

    /** How long a command marker stays on the ground before it gives up: thirty seconds. */
    public static final int MARKER_TICKS = 600;

    @Nullable
    private static Entity hovered;
    @Nullable
    private static Entity confirmed;
    private static long confirmUntil;

    @Nullable
    private static ClientLevel markerLevel;
    @Nullable
    private static BlockPos markerPos;
    private static int markerCommandId;
    private static long markerUntil;

    @Nullable
    private static ClientLevel trackedLevel;

    private QueenScepterVisuals()
    {
    }

    /** The per tick preview: one ray, no packets, and borrowed flags returned as soon as they are not needed. */
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

        if (player == null || level == null)
        {
            releaseAll();

            return;
        }

        if (level != trackedLevel)
        {
            // Another world or another dimension: nothing of the old one may be drawn any more.
            releaseAll();
            trackedLevel = level;
        }

        hovered = holdingScepter(player) ? rayTarget(player) : null;

        if (confirmed != null && (!confirmed.isAlive() || level.getGameTime() > confirmUntil))
        {
            confirmed = null;
        }

        if (markerPos != null && (markerLevel != level || level.getGameTime() > markerUntil))
        {
            markerPos = null;
            markerLevel = null;
        }
    }

    /** @return what the command ray of the queen points at right now, or null when it points at nothing alive. */
    @Nullable
    private static Entity rayTarget(LocalPlayer player)
    {
        QueenCommandRay.Hit hit = QueenCommandRay.cast(player);

        return hit.hasEntity() ? hit.entity() : null;
    }

    /** @return true when the sceptre is really in one of the hands of that player. */
    private static boolean holdingScepter(LocalPlayer player)
    {
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
     * @return the creature the crosshair of the queen points at, which is outlined right now, or null when nothing is
     *         pointed at, the sceptre is not in her hand, or the preview belongs to another level.
     */
    @Nullable
    public static Entity hoveredTarget(@Nullable ClientLevel level)
    {
        return hovered != null && level == trackedLevel && hovered.level() == level ? hovered : null;
    }

    /**
     * @return the creature the server really accepted an attack order for, which keeps its extra outline for the four
     *         seconds of the confirmation, or null when there is no such creature any more.
     */
    @Nullable
    public static Entity confirmedTarget(@Nullable ClientLevel level)
    {
        if (confirmed == null || level == null || confirmed.level() != level || !confirmed.isAlive()
                || level.getGameTime() > confirmUntil)
        {
            return null;
        }

        return confirmed;
    }

    /** Forgets every preview and marker, used when the world goes away. */
    private static void releaseAll()
    {
        hovered = null;
        confirmed = null;
        confirmUntil = 0L;
        markerPos = null;
        markerLevel = null;
        markerCommandId = 0;
        markerUntil = 0L;
        trackedLevel = null;
    }

    /**
     * The answer of the server: only what it really accepted becomes visible, and a new command always clears the
     * visuals of the old one.
     */
    public static void onFeedback(S2CQueenScepterPacket packet)
    {
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null)
        {
            return;
        }

        switch (packet.kind())
        {
            case MOVE_ACCEPTED:
                // A move clears the old fight confirmation, exactly like an attack clears the old marker.
                confirmed = null;
                confirmUntil = 0L;

                markerLevel = level;
                markerPos = packet.markerPos();
                markerCommandId = packet.commandId();
                markerUntil = level.getGameTime() + MARKER_TICKS;
                break;

            case ATTACK_ACCEPTED:
                markerPos = null;
                markerLevel = null;

                confirmed = level.getEntity(packet.targetEntityId());
                confirmUntil = level.getGameTime() + ATTACK_CONFIRM_TICKS;
                break;

            case MOVE_ARRIVED:
                // The first spider of that command got there: the marker of that command disappears, the others walk on.
                if (markerPos != null && markerCommandId == packet.commandId())
                {
                    markerPos = null;
                    markerLevel = null;
                }

                break;

            case RALLY_ACCEPTED:
                markerPos = null;
                markerLevel = null;
                confirmed = null;
                confirmUntil = 0L;
                break;

            default:
                break;
        }
    }

    /** @return the marker to draw right now, or null when there is none, it expired or the level changed. */
    @Nullable
    public static BlockPos markerToDraw(@Nullable ClientLevel level)
    {
        if (markerPos == null || level == null || level != markerLevel)
        {
            return null;
        }

        return level.getGameTime() > markerUntil ? null : markerPos;
    }

    /** Drops everything a leaving client should not carry into the next world. */
    public static void clear()
    {
        releaseAll();
    }
}
