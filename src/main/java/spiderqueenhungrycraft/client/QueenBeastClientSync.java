package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.beast.QueenBeastState;

/**
 * Client side application of the beast state packet (Phase 8A).
 *
 * <p>The client never decides the state, it only stores what the server sent into the client entity, which is
 * the same capability the renderer and the size hook read. Without this step a client would keep its default
 * state and a beast would look like a normal player.
 */
public final class QueenBeastClientSync
{
    private QueenBeastClientSync()
    {
    }

    /** Applies the state of one player entity, and lets the body and the eye height follow it. */
    public static void apply(int entityId, boolean transformed, boolean compressed)
    {
        Level level = Minecraft.getInstance().level;

        if (level == null)
        {
            return;
        }

        Entity entity = level.getEntity(entityId);

        if (!(entity instanceof Player player))
        {
            return;
        }

        QueenBeastState.of(player).ifPresent(state ->
        {
            state.setTransformed(transformed);
            state.setCompressed(compressed);
        });

        // The body is recomputed from the new state on the client, so the local player gets the very same
        // collision box and eye height the server uses.
        player.refreshDimensions();
    }
}
