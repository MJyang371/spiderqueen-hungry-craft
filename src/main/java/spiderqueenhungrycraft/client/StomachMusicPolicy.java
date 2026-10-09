package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.Musics;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.registry.HungryCraftSounds;
import spiderqueenhungrycraft.stomach.StomachCellManager;

/**
 * The one background track of a stomach, and the end of every other one (Phase 8E2A).
 *
 * <p>The rumble is owned by this class alone. The music of the stomach biome is deliberately disabled in the data
 * file of that biome, because the ordinary music manager of the game does not respect it: it hands out its
 * creative tracks to a flying player in creative mode and its ordinary game music to a biome without music, and
 * both of those were really heard inside a stomach. Two music systems also mean two rumbles, so there is only
 * one now.
 *
 * <p>So a client inside a stomach does two things every tick: it silences the music manager of the game - the
 * track that is already running is stopped, every later attempt is refused before it is ever heard - and it keeps
 * exactly one looping rumble alive in the sound manager. The rumble is a real {@code SoundSource.MUSIC} sound, so
 * it is the music slider of the player that controls its volume.
 *
 * <p>Leaving a stomach ends both: the loop fades out and is forgotten, the suppression stops, and the ordinary
 * music policy of the game takes over by itself. Nothing here ever picks a specific track for the outside world.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class StomachMusicPolicy
{
    /** Target volume of the rumble. */
    private static final float VOLUME = 0.55F;

    /** Ticks used for the fade in and the fade out. */
    private static final int FADE_TICKS = 20;

    /** How often the suppression repeats while a stomach is inhabited, as a safety net. */
    private static final int SUPPRESSION_INTERVAL_TICKS = 20;

    /** The one loop of this mod, or null while no stomach is inhabited. */
    private static RumbleLoop loop;

    /** The counter of the debug output: how many pieces of foreign music were refused. */
    private static int blockedMusic;

    /** How many vanilla tracks were really stopped because they were running inside a stomach. */
    private static int stoppedMusic;

    /** True while the local player was inside a stomach in the previous tick. */
    private static boolean wasInside;

    private static int suppressionTicker;

    private StomachMusicPolicy()
    {
    }

    /** @return true while the rumble is the track that is really heard right now. */
    public static boolean rumblePlaying()
    {
        Minecraft minecraft = Minecraft.getInstance();

        return loop != null && minecraft.getSoundManager() != null && minecraft.getSoundManager().isActive(loop)
                && loop.audible();
    }

    /** @return how many pieces of foreign music were refused inside a stomach since the start. */
    public static int blockedMusic()
    {
        return blockedMusic;
    }

    /** @return how many vanilla tracks were really stopped inside a stomach since the start. */
    public static int stoppedMusic()
    {
        return stoppedMusic;
    }

    /** @return the volume the loop has right now, for the debug output. */
    public static float loopVolume()
    {
        return loop == null ? 0.0F : loop.currentVolume();
    }

    /** @return the sound event id the rumble is built from, for the debug output. */
    public static ResourceLocation rumbleId()
    {
        return HungryCraftSounds.STOMACH_RUMBLE.getId();
    }

    /**
     * Inside a stomach nothing but the rumble is ever heard: the music of the game is refused here, before the
     * sound manager can play it, whatever biome or game mode asked for it.
     */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event)
    {
        SoundInstance sound = event.getSound();

        if (sound == null || sound.getSource() != SoundSource.MUSIC || sound instanceof RumbleLoop
                || !insideStomach())
        {
            return;
        }

        blockedMusic++;
        event.setSound(null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        boolean inside = insideStomach();

        if (!inside)
        {
            if (wasInside)
            {
                // The suppression ends with the stomach: from here on the game schedules its own music again.
                wasInside = false;
                suppressionTicker = 0;
            }

            stopLoop();
            return;
        }

        suppressVanillaMusic(minecraft, !wasInside);
        wasInside = true;

        if (loop == null || loop.isStopped())
        {
            loop = new RumbleLoop();
            minecraft.getSoundManager().play(loop);
        }

        loop.fadeIn();
    }

    /**
     * Stops the music of the game inside a stomach.
     *
     * <p>Two layers, because one is not enough: the block in {@link #onPlaySound} makes sure a track that is only
     * just starting is never heard, and this one stops a track that was already running - a player who walks into
     * a stomach in the middle of an overworld song would otherwise keep listening to that song.
     *
     * <p>It uses the public API of the music manager only ({@code stopPlaying} and the known situation sets of the
     * game), never a mixin, and it never touches the sound of this mod: the rumble lives in the sound manager as
     * its own looping instance and is not known to the music manager at all.
     *
     * @param firstTickOfTheVisit true on the very first tick inside, when a running track has to go right now
     */
    private static void suppressVanillaMusic(Minecraft minecraft, boolean firstTickOfTheVisit)
    {
        boolean due = firstTickOfTheVisit || ++suppressionTicker % SUPPRESSION_INTERVAL_TICKS == 0;

        if (!due)
        {
            return;
        }

        if (isPlayingVanillaMusic(minecraft))
        {
            stoppedMusic++;
            minecraft.getMusicManager().stopPlaying();
        }
    }

    /** @return true while the music manager of the game is playing one of its own tracks. */
    private static boolean isPlayingVanillaMusic(Minecraft minecraft)
    {
        return isPlaying(minecraft, Musics.GAME) || isPlaying(minecraft, Musics.CREATIVE)
                || isPlaying(minecraft, Musics.UNDER_WATER) || isPlaying(minecraft, Musics.END)
                || isPlaying(minecraft, Musics.END_BOSS) || isPlaying(minecraft, Musics.MENU);
    }

    private static boolean isPlaying(Minecraft minecraft, Music music)
    {
        return minecraft.getMusicManager() != null && minecraft.getMusicManager().isPlayingMusic(music);
    }

    private static boolean insideStomach()
    {
        Minecraft minecraft = Minecraft.getInstance();

        return minecraft.player != null && minecraft.level != null
                && minecraft.level.dimension().equals(StomachCellManager.DIMENSION);
    }

    private static void stopLoop()
    {
        if (loop == null)
        {
            return;
        }

        loop.fadeOut();

        // A dimension change can take the sound out of the sound manager without ever stopping the instance of
        // this mod, so the reference is given up as soon as the engine no longer holds it. Without that, the
        // next visit would find a loop that is silent forever and would never start a new one.
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.getSoundManager() != null && minecraft.getSoundManager().isActive(loop);

        if (loop.isStopped() || !active)
        {
            loop = null;
        }
    }

    /** The single loop of the stomach; it fades itself in and out and never restarts on its own. */
    private static final class RumbleLoop extends AbstractTickableSoundInstance
    {
        private float target;

        private boolean fadingOut;

        private RumbleLoop()
        {
            super(HungryCraftSounds.STOMACH_RUMBLE.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.volume = 0.0F;
            this.pitch = 1.0F;
            this.target = VOLUME;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        private void fadeIn()
        {
            fadingOut = false;
            target = VOLUME;
        }

        private void fadeOut()
        {
            fadingOut = true;
            target = 0.0F;
        }

        /** @return true while this loop is really making a sound. */
        private boolean audible()
        {
            return !isStopped() && volume > 0.0F;
        }

        private float currentVolume()
        {
            return volume;
        }

        @Override
        public boolean isLooping()
        {
            return true;
        }

        @Override
        public SoundInstance.Attenuation getAttenuation()
        {
            return SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent()
        {
            return true;
        }

        @Override
        public void tick()
        {
            // The loop sits on the player, so the sound is always in the middle of the head of the listener.
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.player != null)
            {
                this.x = minecraft.player.getX();
                this.y = minecraft.player.getY();
                this.z = minecraft.player.getZ();
            }

            float step = VOLUME / FADE_TICKS;

            if (volume < target)
            {
                volume = Math.min(target, volume + step);
            }
            else if (volume > target)
            {
                volume = Math.max(target, volume - step);
            }

            if (fadingOut && volume <= 0.001F)
            {
                stop();
            }
        }
    }
}

