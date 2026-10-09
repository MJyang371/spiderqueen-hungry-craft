package spiderqueenhungrycraft.client;

import java.util.Collection;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The bought skills of the local player, as the server last reported them (Skill Progression round).
 *
 * <p>It is a plain copy of the list the server sent and <b>no rule of the game is decided here</b>. A client owns
 * exactly one copy of the permission state of its player and uses it for exactly one thing: when the key of a skill her
 * player does not own is pressed, nothing at all happens - no packet, no message, no sound, no action bar and no
 * accumulated input state. The server would refuse the same key anyway, which is why a modified client gains nothing
 * by pretending this list is longer than it is.
 *
 * <p><b>An empty set is the fail-safe direction.</b> It is what a client owns before the server has said anything at
 * all (the moment a world is being joined), and it means "she owns nothing", so the answer to a half known state is
 * silence and never a request.
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class SkillClientState
{
    private static Set<ResourceLocation> unlocked = Set.of();

    private SkillClientState()
    {
    }

    /** Applies the list the server sent, replacing whatever was known before. */
    public static void accept(Collection<ResourceLocation> skills)
    {
        unlocked = Set.copyOf(skills);
    }

    /** @return true when the local player is known to own that skill. */
    public static boolean has(ResourceLocation skill)
    {
        return skill != null && unlocked.contains(skill);
    }

    /** @return the bought skills of the local player, which is what the tree draws its states from. */
    public static Set<ResourceLocation> unlocked()
    {
        return unlocked;
    }

    /** Forgets everything, used when a client leaves a world. */
    public static void clear()
    {
        unlocked = Set.of();
    }

    /** A client that leaves a world keeps nothing of it, so the next world starts from its own list. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        clear();
    }
}
