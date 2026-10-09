package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.devour.DevourTuning;
import spiderqueenhungrycraft.network.S2CDevourFeedbackPacket;

/**
 * Client side of the devour and regurgitate feedback (Phase 8E2A-POLISH-1).
 *
 * <p>The server sends one packet when an action really happened, and this class turns it into the three things a
 * player can feel: the pose of the queen, an impulse of the camera and - for the local player only - the right
 * amount of shake. It owns no timing and no state of its own, it only chooses the flavour and hands the work to
 * the helpers the beast bite already uses.
 *
 * <p>A beast lunges and shakes like it does when it bites, with slightly stronger values. An ordinary queen has
 * no beast body, so she gets no lunge at all, but she does get a light kick: her arm swing alone would leave the
 * player wondering whether anything happened.
 *
 * <p>Only the attacker ever feels the shake. Everybody who can see the attacker gets the pose, so a spectator
 * sees the beast snap forward exactly like they would see it bite.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class DevourFeedbackClient
{
    private static final QueenBeastBiteClient.ShakeFlavour DEVOUR = new QueenBeastBiteClient.ShakeFlavour(
            DevourTuning.DEVOUR_SHAKE_TICKS, DevourTuning.DEVOUR_SHAKE_PITCH, DevourTuning.DEVOUR_SHAKE_YAW,
            DevourTuning.DEVOUR_SHAKE_ROLL, DevourTuning.DEVOUR_SHAKE_FOV);

    private static final QueenBeastBiteClient.ShakeFlavour REGURGITATE = new QueenBeastBiteClient.ShakeFlavour(
            DevourTuning.REGURGITATE_SHAKE_TICKS, DevourTuning.REGURGITATE_SHAKE_PITCH,
            DevourTuning.REGURGITATE_SHAKE_YAW, DevourTuning.REGURGITATE_SHAKE_ROLL, DevourTuning.REGURGITATE_SHAKE_FOV);

    private static final QueenBeastBiteClient.ShakeFlavour QUEEN_KICK = new QueenBeastBiteClient.ShakeFlavour(
            DevourTuning.QUEEN_KICK_TICKS, DevourTuning.QUEEN_KICK_PITCH, DevourTuning.QUEEN_KICK_YAW,
            DevourTuning.QUEEN_KICK_ROLL, DevourTuning.QUEEN_KICK_FOV);

    private DevourFeedbackClient()
    {
    }

    /** The server told us that that player really swallowed something or really threw something up. */
    public static void onFeedback(int actorId, S2CDevourFeedbackPacket.Action action)
    {
        Minecraft minecraft = Minecraft.getInstance();
        Entity actor = minecraft.level == null ? null : minecraft.level.getEntity(actorId);

        // The pose is read from the tracked player itself, so a spectator sees a beast lunge and a human queen
        // swing, exactly like they see a bite. A devour is always done by a beast, so it always lunges.
        boolean beast = actor != null && QueenBeastState.isTransformed(actor);

        if (action == S2CDevourFeedbackPacket.Action.DEVOUR)
        {
            QueenBeastBiteClient.onActionFeedback(actorId, true, DEVOUR, "devour",
                    DevourTuning.DEVOUR_LUNGE_SCALE);
            return;
        }

        // A regurgitation is a lunge for a beast and a light kick for the human shape of the queen.
        QueenBeastBiteClient.onActionFeedback(actorId, beast, beast ? REGURGITATE : QUEEN_KICK, "regurgitate",
                beast ? DevourTuning.REGURGITATE_LUNGE_SCALE : 1.0F);
    }
}
