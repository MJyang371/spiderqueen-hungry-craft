package spiderqueenhungrycraft.soul;

import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * What a death does to the souls of a player (Soul Foundation V1): the Dark Souls order, in one place.
 *
 * <p>The order is fixed and it is the whole design:
 * <ol>
 * <li>the remnant of the previous, never collected death loses its record for good - wherever it stands and whether
 * its chunk is loaded or not,</li>
 * <li>what the player really carried is read,</li>
 * <li>the souls of this life leave the player,</li>
 * <li>and a new remnant is created at the place of death - but only when there was something to leave.</li>
 * </ol>
 *
 * <p>It is deliberately driven by the death of the player and not by the respawn: a death invalidates the old
 * remnant at the very moment it happens, so a spider that walks past it while its queen lies dead can never bring
 * the previous life back.
 */
public final class SoulDeathDrop
{
    private SoulDeathDrop()
    {
    }

    /**
     * Runs the soul half of one death of one player.
     *
     * <p>Does nothing at all while the world spares souls ({@code /gamerule keepSpiderQueenSouls true}): the souls
     * stay on the player, no remnant is created and the remnant of an earlier death is left exactly as it was.
     */
    public static void onPlayerDeath(ServerPlayer player)
    {
        MinecraftServer server = player.getServer();

        if (server == null || SoulRules.keepsSouls(server))
        {
            return;
        }

        SoulRemnantData data = SoulRemnantData.get(server);

        // A. The remnant of the previous life loses its record for good, wherever its chunk may be. This is the rule
        // that makes a hundred deaths leave one remnant and not a hundred, and it never loads a single chunk.
        data.remove(player.getUUID());

        // B. What the player really carried at the moment of death.
        long carried = Souls.getSouls(player);

        // C. The souls of this life leave the body. This also rolls the number on the client down to zero.
        Souls.setSouls(player, 0L);

        // D/E. A remnant is only created for a life that was really worth something: no empty remnants, ever.
        if (carried <= 0L)
        {
            return;
        }

        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        UUID token = UUID.randomUUID();
        SoulRemnantEntity remnant = SoulRemnantEntity.createAt(level, pos, player.getUUID(), token, carried);

        if (remnant == null)
        {
            return;
        }

        // The record is written before the entity is added to the world: the very first tick of a remnant asks
        // SoulRemnantData whether it is real, and an entity that arrived before its record would remove itself.
        data.put(player.getUUID(),
                new SoulRemnantData.ActiveRemnant(token, carried, level.dimension(), pos.x, pos.y, pos.z));
        level.addFreshEntity(remnant);
    }
}
