package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.territory.TerritoryCoreIndex;

/**
 * The range a territory core is currently showing, on the client (Phase LAIR-CORE-1).
 *
 * <p>One box at a time is enough: a player looks at the range of the core he just clicked. The box is kept with the
 * level it belongs to and an expiry game time, so it survives walking away, it is replaced by the next core a player
 * clicks and it disappears on its own - and walking into another dimension drops it, because then it belongs to a
 * level that is not the current one any more.
 */
public final class TerritoryCoreRangeClient
{
    /** How long a shown range stays on screen: five seconds. */
    public static final int SHOW_TICKS = 100;

    @Nullable
    private static AABB box;
    @Nullable
    private static ClientLevel level;
    private static long expiresAt;

    private TerritoryCoreRangeClient()
    {
    }

    /** Shows the range of that core, or restarts the five seconds when the same core is clicked again. */
    public static void show(BlockPos corePos)
    {
        ClientLevel current = Minecraft.getInstance().level;

        if (current == null)
        {
            return;
        }

        box = TerritoryCoreIndex.protectedBox(corePos);
        level = current;
        expiresAt = current.getGameTime() + SHOW_TICKS;
    }

    /** @return the box to draw right now, or null when nothing is shown, the time is up or the level changed. */
    @Nullable
    public static AABB visibleBox(@Nullable ClientLevel current)
    {
        if (box == null || current == null || current != level || current.getGameTime() > expiresAt)
        {
            return null;
        }

        return box;
    }

    /** Drops the shown range, used when a client leaves a world. */
    public static void clear()
    {
        box = null;
        level = null;
        expiresAt = 0L;
    }
}
