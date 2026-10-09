package spiderqueenhungrycraft.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Key bindings of the mod. Client only, so this class is never loaded on a dedicated server.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HungryCraftKeyMappings
{
    public static final String CATEGORY = "key.categories." + SpiderQueenHungryCraft.MODID;

    public static final KeyMapping WEB_SHOT = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".web_shot",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_V,
            CATEGORY);

    public static final KeyMapping EMPOWERED_ATTACK = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".empowered_attack",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    public static final KeyMapping WALL_CRAWL = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".wall_crawl",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_G,
            CATEGORY);

    /**
     * The Queen Beast form key. V is already the web shot of this mod and R and G are taken as well, so the
     * free key X next to the others is used. Like every binding it can be changed by the player.
     */
    public static final KeyMapping BEAST_TRANSFORM = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".beast_transform",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_X,
            CATEGORY);

    /**
     * The devour / regurgitate key of the queen. Every letter of the other bindings is taken (V, R and G) as is
     * the X of the beast form, and C belongs to the vanilla hotbar save, so the free B next to them is used.
     * Like every binding it can be changed by the player.
     */
    public static final KeyMapping DEVOUR_REGURGITATE = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".devour_regurgitate",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_B,
            CATEGORY);

    /**
     * The sniff of the queen (Spider Trigger round follow-up): one press outlines every living creature within sixty
     * four blocks of her for five seconds.
     *
     * <p>V, R, G, X and B are taken by the other skills of this mod, so the free N next to them is used - and like
     * every binding it can be changed by the player in the controls screen.
     */
    public static final KeyMapping SNIFF = new KeyMapping(
            "key." + SpiderQueenHungryCraft.MODID + ".sniff",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_N,
            CATEGORY);

    /**
     * The brood keys of the queen (Brood Selection + Inspect HUD round, regrouped by the Brood Command Group round,
     * unbound by default since the Command Wheel round).
     *
     * <p><b>Every one of these keys is an optional shortcut now.</b> The command wheel of the sceptre (hold Shift) is the
     * entrance of the brood commands that is always there, and it prints the key of every command underneath it - so the
     * queen sees "Not bound" until she binds one here, and nothing in the mod requires a single one of them.
     *
     * <p>Two of them were the entrance of a command before this round and are nothing but a shortcut now: the soft rally
     * was the U key, and the hard rally was the shift and right click of the sceptre, which this round took away for the
     * wheel to use Shift.
     */
    public static final KeyMapping SELECT_VISIBLE = unbound("select_visible");
    public static final KeyMapping RALLY_HOME = unbound("rally_home");
    public static final KeyMapping FOLLOW_SELECTED = unbound("follow_selected");
    public static final KeyMapping SELECT_SAME_TYPES = unbound("select_same");
    public static final KeyMapping CYCLE_ORDER = unbound("cycle_order");
    public static final KeyMapping HOLD_SELECTED = unbound("hold_selected");
    public static final KeyMapping BROOD_ABILITY = unbound("brood_ability");

    /**
     * The free attack key (free attack round): the selected spiders switch their hunt on and attack everything around
     * them that is not of their own side.
     *
     * <p>It is the one command of the brood that is not part of the order cycle and therefore has a key of its own, and
     * like every other brood key it is unbound until the queen gives it one - the command wheel of the sceptre is the
     * entrance that is always there, and it prints this key underneath the command like it does for all the others.
     */
    public static final KeyMapping FREE_ATTACK = unbound("free_attack");

    /**
     * 亡灵契约 IV (Necromancy IV round): the key that wakes a bone dragon of Ice and Fire up.
     *
     * <p>Unbound by default, like every brood key of this mod: the skill belongs to an optional compatibility, so a
     * world without Ice and Fire has nothing for it to do, and a queen who does own the node and the mod gives it a key
     * of her own in the controls screen. The tooltip of the node shows whatever she chose.
     */
    public static final KeyMapping NECROMANCY_IV = unbound("necromancy_iv");

    /**
     * @return a binding that has no key at all until the player gives it one. This is the vanilla way of saying
     *         "unbound": the key code of {@link InputConstants#UNKNOWN}, which the controls screen shows as an empty
     *         button, and which no key press can ever match.
     */
    private static KeyMapping unbound(String id)
    {
        return new KeyMapping("key." + SpiderQueenHungryCraft.MODID + "." + id,
                InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY);
    }

    private HungryCraftKeyMappings()
    {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(WEB_SHOT);
        event.register(EMPOWERED_ATTACK);
        event.register(WALL_CRAWL);
        event.register(BEAST_TRANSFORM);
        event.register(DEVOUR_REGURGITATE);
        event.register(SNIFF);
        event.register(BROOD_ABILITY);
        event.register(SELECT_VISIBLE);
        event.register(RALLY_HOME);
        event.register(SELECT_SAME_TYPES);
        event.register(CYCLE_ORDER);
        event.register(HOLD_SELECTED);
        event.register(FOLLOW_SELECTED);
        event.register(FREE_ATTACK);
        event.register(NECROMANCY_IV);
    }
}
