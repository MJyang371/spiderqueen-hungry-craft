package spiderqueenhungrycraft.web;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.beast.QueenBeastConstants;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.player.QueenDataCapability;
import spiderqueenhungrycraft.player.QueenSilk;

/**
 * Server authoritative web shot handling. The client only asks to fire, everything else (queen check,
 * silk or hunger cost, projectile, animation and sound) is decided here from the real player state.
 *
 * <p>The shot is paid from the silk of the queen first, and only while her glands cannot cover the price
 * does the very same amount come out of her hunger bar. The price of a shot therefore never changes, only
 * the resource it is taken from, and the hunger bar stops being the only thing a queen has to spend.
 *
 * <p>In the normal form one shot is one projectile. A queen in her beast form fires a volley of
 * {@link QueenBeastConstants#BEAST_WEB_PROJECTILES} real projectiles instead: the first one follows the
 * crosshair exactly, the others scatter around it, and the whole volley still costs the single ordinary
 * price. What the volley costs must never be sent by the client, and neither must the element or the
 * spread, all of it is read from the real player state and the server random here.
 */
public final class WebShotHandler
{
    /** Silk or hunger cost of one shot in survival and adventure. A whole beast volley costs this once. */
    private static final int HUNGER_COST = 2;

    /** A shot needs either silk or more than this much food. */
    private static final int MIN_FOOD_LEVEL = 2;

    /** Action bar message throttle, in ticks. */
    private static final int STARVING_MESSAGE_COOLDOWN_TICKS = 20;

    private static final float PROJECTILE_SPEED = 2.2F;
    private static final float PROJECTILE_INACCURACY = 0.5F;

    /** The ordinary shot leaves the body a little in front of the eyes, slightly below them. */
    private static final double PROJECTILE_OFFSET = 0.3D;
    private static final double PROJECTILE_OFFSET_DOWN = 0.1D;

    /**
     * A volley projectile flies perfectly straight: the designed scatter of the volley is the only random part
     * of it, so the middle projectile really hits the crosshair and the others really spread by a known angle.
     */
    private static final float VOLLEY_INACCURACY = 0.0F;

    private static final Map<Player, Long> LAST_STARVING_MESSAGE = new WeakHashMap<>();

    private WebShotHandler()
    {
    }

    public static void tryFireWebShot(ServerPlayer player)
    {
        if (!player.isAlive() || player.isSpectator())
        {
            return;
        }
        if (!QueenDataCapability.isQueen(player))
        {
            return;
        }

        FoodData foodData = player.getFoodData();
        boolean creative = player.isCreative();

        // A web is paid from the silk of the queen first. Only when her glands cannot cover the price and
        // her hunger bar is nearly empty as well is the shot refused, with the very same starving message
        // it always used.
        if (!creative && !QueenSilk.hasSilk(player, QueenSilk.WEB_SHOT_COST)
                && foodData.getFoodLevel() <= MIN_FOOD_LEVEL)
        {
            sendStarvingMessage(player);
            return;
        }

        if (QueenBeastState.isTransformed(player))
        {
            spawnBeastVolley(player);
        }
        else
        {
            spawnWebShot(player);
        }

        player.swing(InteractionHand.MAIN_HAND, true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.PLAYERS, 1.0F, 1.0F / (player.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!creative)
        {
            // The silk goes first, the hunger bar pays only while the glands are empty, and both prices are
            // the same number on purpose. Charged once, however many projectiles the shot produced.
            if (!QueenSilk.consumeSilk(player, QueenSilk.WEB_SHOT_COST))
            {
                // Straight food level change, no exhaustion and no saturation buffer.
                foodData.setFoodLevel(Math.max(foodData.getFoodLevel() - HUNGER_COST, 0));
            }
        }
    }

    private static void spawnWebShot(ServerPlayer player)
    {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();
        Vec3 origin = new Vec3(player.getX() + look.x * PROJECTILE_OFFSET, player.getEyeY() - PROJECTILE_OFFSET_DOWN,
                player.getZ() + look.z * PROJECTILE_OFFSET);

        spawnProjectile(level, player, origin, look, PROJECTILE_INACCURACY, WebVolleyTracker.NO_VOLLEY);
    }

    /** The beast volley: several real projectiles, one hunger price, one element for the whole salvo. */
    private static void spawnBeastVolley(ServerPlayer player)
    {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle();

        // The volley leaves the body right in front of the beast eye, which is where the mouth of the giant
        // spider is: never at the feet, never at the abdomen and never in the middle of the model.
        Vec3 origin = player.getEyePosition().add(look.scale(QueenBeastConstants.BEAST_WEB_MUZZLE_OFFSET));

        // One id for the whole salvo. It is only ever used for the pack provocation deduplication.
        int volleyId = WebVolleyTracker.startVolley(level.getGameTime());

        for (int i = 0; i < QueenBeastConstants.BEAST_WEB_PROJECTILES; i++)
        {
            // The first projectile follows the crosshair exactly, the others scatter around it.
            Vec3 direction = i == 0 ? look : spreadDirection(player);

            spawnProjectile(level, player, origin, direction, VOLLEY_INACCURACY, volleyId);
        }
    }

    /** A random direction inside the designed spread cone around where the player looks. */
    private static Vec3 spreadDirection(ServerPlayer player)
    {
        float spread = QueenBeastConstants.BEAST_WEB_SPREAD_DEGREES;
        float yaw = player.getYRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * spread;
        float pitch = player.getXRot() + (player.getRandom().nextFloat() * 2.0F - 1.0F) * spread;

        return Vec3.directionFromRotation(pitch, yaw);
    }

    private static void spawnProjectile(ServerLevel level, ServerPlayer player, Vec3 origin, Vec3 direction,
            float inaccuracy, int volleyId)
    {
        WebShotProjectile shot = new WebShotProjectile(level, player);

        // The element is read from the real effects of the player on the server, a client can never ask for a
        // poison or fire shot on its own. A whole volley always carries the very same element.
        shot.setElement(WebEmpowerment.current(player));
        shot.setVolleyId(volleyId);
        shot.setPos(origin.x, origin.y, origin.z);
        shot.shoot(direction.x, direction.y, direction.z, PROJECTILE_SPEED, inaccuracy);
        level.addFreshEntity(shot);
    }

    private static void sendStarvingMessage(ServerPlayer player)
    {
        long now = player.level().getGameTime();
        Long last = LAST_STARVING_MESSAGE.get(player);

        if (last != null && now - last < STARVING_MESSAGE_COOLDOWN_TICKS)
        {
            return;
        }

        LAST_STARVING_MESSAGE.put(player, now);
        player.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.too_hungry_to_web"), true);
    }
}
