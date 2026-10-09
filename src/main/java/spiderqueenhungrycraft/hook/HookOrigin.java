package spiderqueenhungrycraft.hook;

import javax.annotation.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The one point a spider hook hangs on while the rod is held: the tip of the rod, there where the little
 * web head sits (feature SPIDER-HOOK).
 *
 * <p>Three things have to agree on that point or a hook cannot look like a hook: where the web head leaves
 * the rod when the rod fires, where the silk starts while the web is out, and where a player sees the tip
 * of his own rod in first person. All three ask this class, so they can never drift apart - the web head
 * is born on the tip and the thread that is drawn to it starts on the very same tip.
 *
 * <p>The point is measured from the body of the player, never from his eyes, exactly like the vanilla
 * fishing rod places the end of its line. The first person case is the one exception: there the tip is
 * read from the camera plane, because that is the only way the thread can start exactly on the rod the
 * player sees in his own hand.
 *
 * <p>This is the far end of the very rod the five item models show in the hand: they place the model
 * origin - the handle of the art, which sits in the middle of the sprite sheet - into the fist of the hand
 * model, so the hand holds the handle, and the tip that this class computes is the other end of the same
 * rod. Handle in the fist, tip under the thread: the two halves of the rod stay one object.
 *
 * <p>How far to the side, how far in front and how far below the eyes that tip sits is not hard coded: the
 * numbers live in the configuration of the mod under {@code [spider_hook]}, so the height of a cast can be
 * moved by whoever plays the pack, without a new build.
 */
public final class HookOrigin
{
    /**
     * The field of view the camera plane of the vanilla rod line is measured against. It is the very number
     * the vanilla fishing rod draws its own line with, so the silk of a hook hangs exactly like a line.
     */
    public static final double FOV_SCALE = 960.0D;

    /** A crouching player holds everything a little lower, exactly as much as the vanilla rod line does. */
    private static final double CROUCH_DROP = -0.1875D;

    private HookOrigin()
    {
    }

    /**
     * @return how far to the side of the body the tip of the rod sits, in blocks. It is read from the
     *         configuration every time, so a pack author can move the tip without a new build.
     */
    public static double sideOffset()
    {
        return HungryCraftConfig.hookOriginSide();
    }

    /** @return how far in front of the body the tip of the rod sits, in blocks. */
    public static double forwardOffset()
    {
        return HungryCraftConfig.hookOriginForward();
    }

    /** @return how far below the eyes the tip of the rod sits, in blocks. This is the height of a cast. */
    public static double tipDrop()
    {
        return HungryCraftConfig.hookOriginDrop();
    }

    /** @return first person: how far to the side of the middle of the screen the own tip is taken to be. */
    public static float firstPersonSide()
    {
        return (float) HungryCraftConfig.hookOriginFirstPersonSide();
    }

    /** @return first person: how far below the middle of the screen that same tip is taken to be. */
    public static float firstPersonDown()
    {
        return (float) HungryCraftConfig.hookOriginFirstPersonDown();
    }

    /** @return how far above the tip the silk itself is drawn, in blocks. */
    public static float tipLift()
    {
        return (float) HungryCraftConfig.hookOriginTipLift();
    }

    /** @return the tip of the rod of that player, seen from the outside (server and third person). */
    public static Vec3 rodTip(Player player, float partialTick)
    {
        return rodTip(player, partialTick, null);
    }

    /**
     * @param viewOffset the point on the camera plane where that player sees his own rod tip, or null for
     *        anybody who is looked at from the outside - then the tip is computed from his body.
     * @return the tip of the rod of that player, in world coordinates.
     */
    public static Vec3 rodTip(Player player, float partialTick, @Nullable Vec3 viewOffset)
    {
        double x;
        double y;
        double z;
        double heightOffset;

        if (viewOffset != null)
        {
            // First person: the tip is exactly where the camera sees it, swing and all.
            float swingSin = Mth.sin(Mth.sqrt(player.getAttackAnim(partialTick)) * (float) Math.PI);
            Vec3 tip = viewOffset.yRot(swingSin * 0.5F).xRot(-swingSin * 0.7F);
            x = Mth.lerp(partialTick, player.xo, player.getX()) + tip.x;
            y = Mth.lerp(partialTick, player.yo, player.getY()) + tip.y;
            z = Mth.lerp(partialTick, player.zo, player.getZ()) + tip.z;
            // The camera plane is measured from the eyes, the body is measured from the feet.
            heightOffset = player.getEyeHeight();
        }
        else
        {
            int side = rodSide(player);
            float bodyRot = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180.0F);
            double sin = Mth.sin(bodyRot);
            double cos = Mth.cos(bodyRot);
            double aside = side * sideOffset();
            x = Mth.lerp(partialTick, player.xo, player.getX()) - cos * aside - sin * forwardOffset();
            y = player.yo + player.getEyeHeight() + (player.getY() - player.yo) * partialTick - tipDrop();
            z = Mth.lerp(partialTick, player.zo, player.getZ()) - sin * aside + cos * forwardOffset();
            heightOffset = player.isCrouching() ? CROUCH_DROP : 0.0D;
        }

        return new Vec3(x, y + heightOffset, z);
    }

    /**
     * @return +1 while the rod is in the right hand and -1 while it is in the left one: the side the silk
     *         and the web head leave from, the very rule the vanilla fishing rod uses for its line.
     */
    public static int rodSide(Player player)
    {
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;

        return player.getMainHandItem().getItem() instanceof SpiderHookItem ? side : -side;
    }

    /** @return true while that stack is one of the five hook rods. */
    public static boolean isHookRod(ItemStack stack)
    {
        return stack.getItem() instanceof SpiderHookItem;
    }
}
