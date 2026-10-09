package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.api.distmarker.OnlyIn;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.S2CStomachSoundPacket;

/**
 * The client half of the stomach sound relay (Phase 8E2A-POLISH-2).
 *
 * <p>It takes a sound that happened inside the stomach of the local player and plays the very same sound event
 * again, quietly, right at the body of that player: a noise from inside them. The sound keeps its own category,
 * so the sliders of the player still decide how loud a hostile, a neutral, a block or a player sound is - only
 * the volume of the original is halved.
 *
 * <p>The playback is deliberately relative to the listener: the original direction of a sound across dimensions
 * makes no sense, and a positional copy would be muffled by the distance to a place the player cannot see.
 * Nothing here ever reaches the server again, so a relayed sound can never be relayed a second time.
 */
@OnlyIn(Dist.CLIENT)
public final class StomachSoundClient
{
    private StomachSoundClient()
    {
    }

    /** Plays one relayed sound of the own stomach, after checking that it really is meant for us. */
    public static void accept(S2CStomachSoundPacket packet)
    {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        // The lowest safe boundary: a packet for somebody else is dropped, whatever else it claims.
        if (player == null || packet.owner() == null || !packet.owner().equals(player.getUUID()) || packet.volume() <= 0.0F)
        {
            return;
        }

        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(packet.sound());

        if (sound == null)
        {
            return;
        }

        minecraft.getSoundManager().play(new SimpleSoundInstance(packet.sound(), packet.source(), packet.volume(),
                packet.pitch(), SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0D,
                0.0D, 0.0D, true));

        if (SpiderQueenHungryCraft.LOGGER.isDebugEnabled())
        {
            SpiderQueenHungryCraft.LOGGER.debug("[RELAY_FX] played {} ({}) volume={} pitch={} at the owner",
                    packet.sound(), packet.source(), packet.volume(), packet.pitch());
        }
    }
}
