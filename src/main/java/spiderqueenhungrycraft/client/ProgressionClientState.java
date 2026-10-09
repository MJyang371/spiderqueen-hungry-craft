package spiderqueenhungrycraft.client;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.progression.ProgressionFeedback;
import spiderqueenhungrycraft.progression.ProgressionSnapshot;

/**
 * The client side of the growth interface (Progression Foundation round): the last picture the server sent, and the
 * last thing it said about a request.
 *
 * <p>It holds <b>no rules</b>. The snapshot is what the server enforced - the maximum, the prices, the products, the
 * level - and nothing here ever computes a price of its own or decides whether a click is allowed. A client that
 * shows a button the server would refuse is a client that is simply about to be told no, which is exactly how a
 * modified client is answered too.
 *
 * <p>A snapshot is also what opens the screen: a full one arrives when the server opens an interface, and a later one
 * (after a level or a purchase) only refreshes a screen that is already open.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class ProgressionClientState
{
    /** How long the answer of the server stays readable on the screen: three seconds. */
    private static final int FEEDBACK_TICKS = 60;

    @Nullable
    private static ProgressionSnapshot snapshot;

    @Nullable
    private static ProgressionFeedback feedback;
    private static int feedbackTicksLeft;

    private ProgressionClientState()
    {
    }

    /** Applies a snapshot: it opens the screen, or refreshes the one that is already open. */
    public static void acceptSnapshot(ProgressionSnapshot incoming)
    {
        snapshot = incoming;

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen instanceof ProgressionScreen open)
        {
            open.refresh(incoming);
        }
        else
        {
            minecraft.setScreen(new ProgressionScreen(incoming));
        }
    }

    /** Applies the verdict of the server and, on a success, plays the one light sound of the interface. */
    public static void acceptFeedback(ProgressionFeedback code)
    {
        feedback = code;
        feedbackTicksLeft = FEEDBACK_TICKS;

        if (code == ProgressionFeedback.UPGRADE_OK || code == ProgressionFeedback.PURCHASE_OK)
        {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
        }
    }

    /** @return the last snapshot, or null while the server never sent one. */
    @Nullable
    public static ProgressionSnapshot snapshot()
    {
        return snapshot;
    }

    /** @return the line to show for the last answer of the server, or null when there is nothing to show any more. */
    @Nullable
    public static Component feedbackMessage()
    {
        if (feedback == null || feedbackTicksLeft <= 0)
        {
            return null;
        }

        return message(feedback);
    }

    /** One step of the feedback timer, called once per frame by the screen while it is open. */
    public static void tickFeedback()
    {
        if (feedbackTicksLeft > 0)
        {
            feedbackTicksLeft--;
        }
    }

    /** Forgets everything, used when a client leaves a world. */
    public static void clear()
    {
        snapshot = null;
        feedback = null;
        feedbackTicksLeft = 0;
    }

    /**
     * The sentence of one verdict.
     *
     * <p>It is the one place the codes of the server become text, and the text itself lives in the language file, so
     * every line can be translated and the two messages a player sees most often can be worded the way this mod wants
     * them to be.
     */
    public static Component message(ProgressionFeedback code)
    {
        return Component.translatable(switch (code)
        {
            case UPGRADE_OK -> "message.spiderqueen_hungrycraft.progression.upgraded";
            case SKILL_OK -> "message.spiderqueen_hungrycraft.progression.skill_unlocked";
            case PURCHASE_OK -> "message.spiderqueen_hungrycraft.progression.purchased";
            case INSUFFICIENT_SOULS -> "message.spiderqueen_hungrycraft.insufficient_souls";
            case NOT_THE_NEXT_LEVEL -> "message.spiderqueen_hungrycraft.progression.not_next_level";
            case ALREADY_MAX_LEVEL -> "message.spiderqueen_hungrycraft.progression.max_level";
            case SKILL_LOCKED -> "message.spiderqueen_hungrycraft.progression.skill_locked";
            case ALREADY_UNLOCKED -> "message.spiderqueen_hungrycraft.progression.skill_already_unlocked";
            case UNKNOWN_SKILL -> "message.spiderqueen_hungrycraft.progression.unknown_skill";
            case UNKNOWN_PRODUCT -> "message.spiderqueen_hungrycraft.progression.unknown_product";
            case DENIED -> "message.spiderqueen_hungrycraft.progression.denied";
        });
    }

    /** A client that leaves a world keeps nothing of it, so the next world starts from its own snapshot. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        clear();
    }
}
