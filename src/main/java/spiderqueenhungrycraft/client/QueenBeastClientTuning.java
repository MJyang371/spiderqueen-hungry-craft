package spiderqueenhungrycraft.client;

import spiderqueenhungrycraft.beast.QueenBeastConstants;

/**
 * The client cache of the beast tuning (Phase 8B-POLISH-2).
 *
 * <p>The server owns the tuning file and sends the values with {@code S2CBeastTuningPacket} on login and after
 * every {@code /reload}; this class only stores them. It is deliberately a separate object from the server
 * tuning, so the camera of a client can never be decided by a stale or guessed server value and the servers hit
 * test can never be decided by a client. Both caches start from the same constants and are then updated by
 * their own side alone.
 *
 * <p>The armor bonus and the bite sizes are kept for debug overlays and for a pack author who wants to check
 * what the server really loaded. Nothing in this class is ever read to decide gameplay: the reach of a bite and
 * the armor of the shell are always computed on the server, from the server value of that moment.
 */
public final class QueenBeastClientTuning
{
    /**
     * One snapshot of the synced values.
     *
     * @param shakeTicks duration of the bite impulse in ticks
     * @param shakePitch peak pitch in degrees
     * @param shakeYaw   peak yaw in degrees
     * @param shakeRoll  peak roll in degrees
     * @param shakeFov   field of view punch in degrees
     */
    public record Shake(int shakeTicks, float shakePitch, float shakeYaw, float shakeRoll, double shakeFov)
    {
        /** The values of the built in constants, used until the first packet arrives. */
        public static Shake defaults()
        {
            return new Shake(QueenBeastConstants.BEAST_BITE_SHAKE_TICKS, QueenBeastConstants.BEAST_BITE_SHAKE_PITCH,
                    QueenBeastConstants.BEAST_BITE_SHAKE_YAW, QueenBeastConstants.BEAST_BITE_SHAKE_ROLL,
                    QueenBeastConstants.BEAST_BITE_SHAKE_FOV);
        }
    }

    private static volatile Shake shake = Shake.defaults();
    private static volatile Shake stompShake = new Shake(QueenBeastConstants.BEAST_STOMP_SHAKE_TICKS,
            QueenBeastConstants.BEAST_STOMP_SHAKE_PITCH, QueenBeastConstants.BEAST_STOMP_SHAKE_YAW,
            QueenBeastConstants.BEAST_STOMP_SHAKE_ROLL, QueenBeastConstants.BEAST_STOMP_SHAKE_FOV);
    private static volatile int jumpMaxChargeTicks = QueenBeastConstants.BEAST_JUMP_MAX_CHARGE_TICKS;
    private static volatile double chargeFovWidening = QueenBeastConstants.BEAST_CHARGE_FOV_WIDENING;
    private static volatile double armorBonus = QueenBeastConstants.BEAST_ARMOR_BONUS;
    private static volatile double biteWidth = QueenBeastConstants.BEAST_BITE_WIDTH;
    private static volatile double biteHeight = QueenBeastConstants.BEAST_BITE_HEIGHT;
    private static volatile double biteDepth = QueenBeastConstants.BEAST_BITE_DEPTH;

    private QueenBeastClientTuning()
    {
    }

    /** The camera values the bite impulse uses, already stretched to the synced duration. */
    public static Shake shake()
    {
        return shake;
    }

    /** The camera values the stomp impulse uses, heavier than the bite on purpose. */
    public static Shake stompShake()
    {
        return stompShake;
    }

    /** How many ticks of held jump key count as a full charge, used by the jump meter. */
    public static int jumpMaxChargeTicks()
    {
        return jumpMaxChargeTicks;
    }

    /** How far the lens widens while a charge runs. */
    public static double chargeFovWidening()
    {
        return chargeFovWidening;
    }

    /** Debug value: the armor the server currently applies to a beast. */
    public static double armorBonus()
    {
        return armorBonus;
    }

    /** Debug value: the width of the bite volume the server currently uses. */
    public static double biteWidth()
    {
        return biteWidth;
    }

    /** Debug value: the height of the bite volume the server currently uses. */
    public static double biteHeight()
    {
        return biteHeight;
    }

    /** Debug value: the depth of the bite volume the server currently uses. */
    public static double biteDepth()
    {
        return biteDepth;
    }

    /** Called by the tuning packet, client side only. */
    public static void apply(double armorBonus, float biteWidth, float biteHeight, float biteDepth,
            int shakeTicks, float shakePitch, float shakeYaw, float shakeRoll, float shakeFov,
            int jumpMaxChargeTicks, int stompShakeTicks, float stompShakePitch, float stompShakeYaw,
            float stompShakeRoll, float stompShakeFov, float chargeFovWidening)
    {
        QueenBeastClientTuning.armorBonus = armorBonus;
        QueenBeastClientTuning.biteWidth = biteWidth;
        QueenBeastClientTuning.biteHeight = biteHeight;
        QueenBeastClientTuning.biteDepth = biteDepth;
        shake = new Shake(Math.max(shakeTicks, 1), shakePitch, shakeYaw, shakeRoll, shakeFov);
        stompShake = new Shake(Math.max(stompShakeTicks, 1), stompShakePitch, stompShakeYaw, stompShakeRoll,
                stompShakeFov);
        QueenBeastClientTuning.jumpMaxChargeTicks = Math.max(jumpMaxChargeTicks, 1);
        QueenBeastClientTuning.chargeFovWidening = chargeFovWidening;
    }
}
