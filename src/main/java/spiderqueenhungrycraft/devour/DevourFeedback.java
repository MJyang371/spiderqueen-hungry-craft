package spiderqueenhungrycraft.devour;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.network.S2CDevourFeedbackPacket;

/**
 * The one feedback of a successful devour or regurgitation (Phase 8E2A-POLISH-1).
 *
 * <p>Both actions need the same three things on the screen of a player: the pose of the queen, an impulse of the
 * camera and a vanilla sound. This class owns that triple, so the two callers - {@code DevourHandler} and
 * {@code StomachRegurgitation} - only have to say which action happened, and so both actions can never drift
 * apart in their presentation.
 *
 * <p>It is deliberately a separate packet from the bite feedback: a bite is a bite, and a throat is not a jaw.
 * The client answers this packet with the very same lunge and camera helpers the bite uses, so the two actions
 * share one implementation and two flavours.
 *
 * <p>The sound is played by the server at the body of the queen in the world she really stands in, so the queen
 * and everybody near her hear it. It is never played inside a stomach, and never played by a client alone.
 */
public final class DevourFeedback
{
    private DevourFeedback()
    {
    }

    /** The queen opened her jaws: pose, impulse, frog and the saliva that leaves her mouth. */
    public static void devour(ServerPlayer beast, Vec3 forward)
    {
        send(beast, S2CDevourFeedbackPacket.Action.DEVOUR);
        play(beast, SoundEvents.FROG_EAT, DevourTuning.DEVOUR_SOUND_VOLUME, DevourTuning.DEVOUR_SOUND_PITCH);
        DevourFluid.saliva(beast, forward);

        SpiderQueenHungryCraft.LOGGER.debug("[DEVOUR_FX] devour feedback sent actor={} pos={}",
                beast.getId(), format(beast.position()));
    }

    /** The queen throws her stomach up: pose, impulse, llama spit and the first wave of fluid. */
    public static void regurgitate(ServerPlayer owner, Vec3 forward)
    {
        send(owner, S2CDevourFeedbackPacket.Action.REGURGITATE);
        play(owner, SoundEvents.LLAMA_SPIT, DevourTuning.REGURGITATE_SOUND_VOLUME,
                DevourTuning.REGURGITATE_SOUND_PITCH);
        DevourFluid.start(owner, forward);
        SpiderQueenHungryCraft.LOGGER.debug("[REGURG_FX] regurgitate feedback sent actor={} beast={} pos={}",
                owner.getId(), spiderqueenhungrycraft.beast.QueenBeastState.isTransformed(owner),
                format(owner.position()));
    }

    /** The rest of the fluid cloud, one batch per server tick, right after the contents left the mouth. */
    public static void regurgitateFollowUp(ServerPlayer owner, Vec3 forward)
    {
        DevourFluid.followUp(owner, forward);
    }

    private static void send(ServerPlayer actor, S2CDevourFeedbackPacket.Action action)
    {
        HungryCraftNetwork.sendToTrackingAndSelf(new S2CDevourFeedbackPacket(actor.getId(), action), actor);
    }

    /** A sound of the queen herself, in the world she stands in, heard by her and by everybody near her. */
    private static void play(ServerPlayer actor, SoundEvent sound, float volume, float pitch)
    {
        if (!(actor.level() instanceof ServerLevel level))
        {
            return;
        }

        double x = actor.getX();
        double y = actor.getY() + DevourTuning.MOUTH_HEIGHT * 0.5D;
        double z = actor.getZ();
        SoundSource source = SoundSource.NEUTRAL;

        level.playSound(null, x, y, z, sound, source, volume, pitch);

        if (SpiderQueenHungryCraft.LOGGER.isDebugEnabled())
        {
            int listeners = 0;

            for (ServerPlayer nearby : level.players())
            {
                if (nearby.distanceToSqr(x, y, z) <= 32.0D * 32.0D)
                {
                    listeners++;
                }
            }

            SpiderQueenHungryCraft.LOGGER.debug("[DEVOUR_FX] sound {} at {} volume={} pitch={} source={} listeners={}",
                    sound.getLocation(), format(new Vec3(x, y, z)), volume, pitch, source, listeners);
        }
    }

    private static String format(Vec3 pos)
    {
        return "(" + Math.round(pos.x * 10.0D) / 10.0D + "," + Math.round(pos.y * 10.0D) / 10.0D + ","
                + Math.round(pos.z * 10.0D) / 10.0D + ")";
    }
}
