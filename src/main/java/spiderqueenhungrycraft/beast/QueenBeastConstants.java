package spiderqueenhungrycraft.beast;

/**
 * Every tunable number of the Queen Beast form (Phase 8A), in one place on purpose: the controller, the
 * renderer, the camera hook and the clearance all read these constants, so balancing never means searching
 * for a magic number inside the logic.
 *
 * <p>The visual scale and the physical size are deliberately independent. The model is drawn four times as
 * large as a vanilla spider, while the collision box only grows to a size that still fits through a cleared
 * tunnel, and the eye height grows far less than the model: a giant spider is low and wide, not a player that
 * became four times as tall.
 */
public final class QueenBeastConstants
{
    // ---------------------------------------------------------------------------------------------
    // Transform input
    // ---------------------------------------------------------------------------------------------

    /** How many times the transform key has to be pressed to become a beast. */
    public static final int TRANSFORM_TAPS_REQUIRED = 3;

    /** The taps have to happen within this many ticks of each other. */
    public static final int TRANSFORM_TAP_WINDOW_TICKS = 30;

    /** Hunger level a player needs to be able to transform. Starting the form costs no food. */
    public static final int FULL_FOOD_LEVEL = 20;

    // ---------------------------------------------------------------------------------------------
    // Physical size and presentation
    // ---------------------------------------------------------------------------------------------

    /** Width of the full beast collision box in blocks. */
    public static final float BEAST_WIDTH = 3.0F;

    /** Height of the full beast collision box in blocks. */
    public static final float BEAST_HEIGHT = 2.1F;

    /**
     * Eye height of the beast. Only a little above the vanilla player eye height, because the first person
     * camera sits in the mouth area of a low and wide creature instead of on top of a four times taller one.
     */
    public static final float BEAST_EYE_HEIGHT = 1.87F;

    /** How much larger than a vanilla spider the beast is drawn. Visual only, never the collision box. */
    public static final float BEAST_RENDER_SCALE = 4.0F;

    /** Distance multiplier of the third person camera while transformed. */
    public static final double BEAST_CAMERA_DISTANCE_MULTIPLIER = 1.9D;

    // ---------------------------------------------------------------------------------------------
    // Transformation clearance
    // ---------------------------------------------------------------------------------------------

    /** Blocks up to this hardness are cleared when they really overlap the beast body. */
    public static final float TRANSFORM_CLEAR_HARDNESS = 3.0F;

    /**
     * The beast box is shrunk by this much before it is tested against block shapes. Touching a floor with a
     * bounding box face must never count as an obstruction, only a real volume overlap does.
     */
    public static final double CLEARANCE_EPSILON = 1.0E-3D;

    /** A compressed beast only checks every this many ticks whether it fits again. */
    public static final int COMPRESSED_EXPAND_CHECK_INTERVAL_TICKS = 5;

    // ---------------------------------------------------------------------------------------------
    // Attributes
    // ---------------------------------------------------------------------------------------------

    /** Armor the beast form adds on top of everything else. */
    public static final double BEAST_ARMOR_BONUS = 30.0D;

    /** Extra block reach of the beast. The Forge base is 4.5 blocks. */
    public static final double BEAST_BLOCK_REACH_BONUS = 2.0D;

    /** Extra entity reach of the beast. The Forge base is 3.0 blocks. */
    public static final double BEAST_ENTITY_REACH_BONUS = 1.5D;

    // ---------------------------------------------------------------------------------------------
    // Hunger metabolism and regeneration
    // ---------------------------------------------------------------------------------------------

    /** Food the ordinary queen burns per metabolism cycle. */
    public static final int NORMAL_METABOLISM_FOOD = 2;

    /** Food the beast burns per metabolism cycle. It replaces the ordinary amount, it never adds to it. */
    public static final int BEAST_METABOLISM_FOOD = 3;

    /** How often the beast regeneration heals. */
    public static final int BEAST_REGEN_INTERVAL_TICKS = 20;

    /** Health the beast regeneration heals per interval. */
    public static final float BEAST_REGEN_AMOUNT = 1.0F;

    /** Really healed health per additional food point the beast regeneration costs. */
    public static final float BEAST_REGEN_HEAL_PER_FOOD = 4.0F;

    // ---------------------------------------------------------------------------------------------
    // Queen claw mining
    // ---------------------------------------------------------------------------------------------

    /** Dig speed of the claws. Independent of the held item, so a pickaxe is never needed. */
    public static final float BEAST_CLAW_DIG_SPEED = 20.0F;

    // ---------------------------------------------------------------------------------------------
    // Beast web volley
    // ---------------------------------------------------------------------------------------------

    /** Projectiles of one beast web shot. The whole volley costs a single ordinary hunger price. */
    public static final int BEAST_WEB_PROJECTILES = 5;

    /** Maximum random deviation of the outer projectiles around the crosshair, in degrees. */
    public static final float BEAST_WEB_SPREAD_DEGREES = 7.0F;

    /** How far in front of the beast eye the volley leaves the body, so it looks like it comes from the mouth. */
    public static final double BEAST_WEB_MUZZLE_OFFSET = 0.35D;

    // ---------------------------------------------------------------------------------------------
    // Beast bite (Phase 8B2)
    // ---------------------------------------------------------------------------------------------

    /** Shortest time between two accepted beast bites, in ticks. Half a second. */
    public static final int BEAST_BITE_COOLDOWN_TICKS = 10;

    /** Physical damage of one beast bite against every legal target in the bite volume. */
    public static final float BEAST_BITE_DAMAGE = 8.0F;

    /** Damage multiplier of a bite that releases a prepared venom strike. Eight becomes sixteen. */
    public static final float BEAST_BITE_VENOM_MULTIPLIER = 2.0F;

    /** Width of the bite volume, perpendicular to where the beast looks. */
    public static final float BEAST_BITE_WIDTH = 3.0F;

    /** Height of the bite volume, from the feet of the beast upwards. */
    public static final float BEAST_BITE_HEIGHT = 2.0F;

    /** Depth of the bite volume, from the front edge of the beast body outwards. */
    public static final float BEAST_BITE_DEPTH = 2.0F;

    /** How long the head lunge of a bite lasts, in ticks. */
    public static final int BEAST_BITE_ANIMATION_TICKS = 8;

    /** How far the spider head pushes forward at the peak of the lunge, in model units. */
    public static final float BEAST_BITE_HEAD_LUNGE = 3.0F;

    /** How far the spider head presses down at the peak of the lunge, in degrees. */
    public static final float BEAST_BITE_HEAD_PITCH_DEGREES = 22.0F;

    /** How far the thorax lunges forward with the head, in model units. */
    public static final float BEAST_BITE_BODY_LUNGE = 1.2F;

    /** How far the whole body sinks towards the ground during the lunge, in model units (+Y is down). */
    public static final float BEAST_BITE_BODY_DROP = 0.8F;

    /** How far the thorax pitches down with the head, in degrees. */
    public static final float BEAST_BITE_BODY_PITCH_DEGREES = 8.0F;

    /** The abdomen follows the lunge, but only with this fraction of it, so the mass stays together. */
    public static final float BEAST_BITE_ABDOMEN_FOLLOW = 0.55F;

    /** How far the legs brace at the peak of the lunge, in degrees. */
    public static final float BEAST_BITE_LEG_BRACE_DEGREES = 9.0F;

    /** How far the whole model dips into the ground at the peak, in blocks. */
    public static final float BEAST_BITE_BODY_DIP = 0.07F;

    /**
     * Strength of the lunge per animation tick: a tiny anticipation, the lunge itself and a soft return.
     * The renderer only reads it, so tweaking the feel of the bite never touches the renderer code.
     */
    public static final float[] BEAST_BITE_LUNGE_PROFILE = { -0.12F, -0.05F, 0.55F, 1.0F, 0.8F, 0.48F, 0.2F, 0.04F };

    /** Columns and rows of the ground impact grid across the bite footprint. */
    public static final int BEAST_BITE_IMPACT_COLUMNS = 5;
    public static final int BEAST_BITE_IMPACT_ROWS = 3;

    /** Ground debris particles per sampled spot of the impact. */
    public static final int BEAST_BITE_DEBRIS_PARTICLES = 4;

    /** How many dust puffs and ground flashes the impact spread over the footprint. */
    public static final int BEAST_BITE_DUST_PUFFS = 16;
    public static final int BEAST_BITE_IMPACT_FLASHES = 3;

    /** How far below the beast feet the ground of the impact is searched. */
    public static final int BEAST_BITE_GROUND_SEARCH_DEPTH = 3;

    // ---------------------------------------------------------------------------------------------
    // Beast locomotion (Phase 8B2)
    // ---------------------------------------------------------------------------------------------

    /**
     * Movement speed added to the base attribute while transformed. MULTIPLY_BASE with +1.0 doubles the
     * vanilla base of 0.1 into 0.2, which is the pace of a normal, slightly quick horse.
     */
    public static final double BEAST_MOVEMENT_SPEED_MULTIPLIER = 1.0D;

    /**
     * Knockback resistance the beast form adds. The attribute runs from zero to one, so a full point makes
     * the ordinary knockback of a melee hit, an arrow or a shield bash fade out completely: a giant spider is
     * never shoved around by a single zombie. Real impulses such as an explosion are a different code path and
     * still toss the beast, exactly like physics should.
     */
    public static final double BEAST_KNOCKBACK_RESISTANCE_BONUS = 1.0D;

    /** How fast a raw movement input is reached, per tick. A full input is reached in eight ticks. */
    public static final float BEAST_INPUT_ACCEL_PER_TICK = 0.125F;

    /** How fast a released movement input fades out, per tick. A full stop takes about six ticks. */
    public static final float BEAST_INPUT_DECEL_PER_TICK = 0.17F;

    // ---------------------------------------------------------------------------------------------
    // Super armor and hurt feedback (Phase 8B-POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /**
     * Final health damage at or below this counts as a small hit against the beast (8B-POLISH-2).
     *
     * <p>The comparison happens against the real, fully mitigated health loss: armor, resistance,
     * absorption, enchantments and every Forge damage modifier are already applied when this number is
     * judged. A zombie that scratches the chitin for half a heart still deals its damage, keeps the
     * invulnerability frames, the retaliation and the aggro, but it is not allowed to throw the head of
     * a four times enlarged spider around any more.
     */
    public static final float BEAST_SMALL_HIT_THRESHOLD = 1.0F;

    /**
     * How long a suppressed hurt bob stays suppressed after a small hit, in ticks. Slightly longer than
     * the vanilla hurt animation of ten ticks, so the whole shake of that one hit is really covered.
     */
    public static final int BEAST_SMALL_HIT_SUPPRESSION_TICKS = 12;

    /**
     * Sound of an incoming attack on the beast (8B-POLISH-2): the hit sound of a netherite block, which
     * reads as a heavy metal clang instead of a human outcry. Only entity caused attacks, arrows,
     * projectiles and explosions use it; drowning, starving, freezing and every other environmental hurt
     * keeps the ordinary player sound, because a beast does not cry out about weather.
     */
    public static final float BEAST_HURT_SOUND_VOLUME = 0.9F;

    /** Pitch of that hit sound. Deep and dull on purpose, never a high pitched clank. */
    public static final float BEAST_HURT_SOUND_PITCH = 0.82F;

    // ---------------------------------------------------------------------------------------------
    // First person bite feedback (Phase 8B2, strengthened by 8B-POLISH-1 and 8B-POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /**
     * How long the first person bite impulse decays, in ticks (8B-POLISH-2 default).
     *
     * <p>The armor bonus of the form, the three bite range sizes and the five camera values below are the
     * defaults of the datapack tuning file {@code data/spiderqueen_hungrycraft/queen_beast/tuning.json}.
     * A pack that ships its own copy of that file overrides them without recompiling anything; these
     * constants are what the game uses when no file is present at all.
     */
    public static final int BEAST_BITE_SHAKE_TICKS = 8;

    /** Peak pitch of the bite impulse in degrees. A short, heavy kick, not an earthquake. */
    public static final float BEAST_BITE_SHAKE_PITCH = 3.5F;

    /** Peak yaw of the bite impulse in degrees. */
    public static final float BEAST_BITE_SHAKE_YAW = 1.2F;

    /** Peak roll of the bite impulse in degrees, only used as flavour. */
    public static final float BEAST_BITE_SHAKE_ROLL = 0.55F;

    /** Field of view the bite pulls in at its peak, in degrees. The lens punch that sells the impact. */
    public static final double BEAST_BITE_SHAKE_FOV = 3.0D;

    /**
     * Shape of the camera impulse, sampled over the whole duration: a hard first frame, a fast decay and a
     * small settle that leans back at the end. It is a normalized shape on purpose, so only the duration in
     * the tuning file has to be changed to stretch or shorten the kick.
     */
    public static final float[] BEAST_BITE_SHAKE_PROFILE =
            { 1.0F, 0.90F, 0.72F, 0.52F, 0.34F, 0.18F, 0.04F, -0.07F };

    /**
     * Shape of the stomp impulse (Phase 8C): the same idea as the bite, but heavier, longer and with a real
     * settle at the end, because the whole body of the beast arrives with the landing.
     */
    public static final float[] BEAST_STOMP_SHAKE_PROFILE =
            { 1.0F, 0.92F, 0.78F, 0.62F, 0.46F, 0.31F, 0.18F, 0.08F, -0.04F, -0.08F };

    // ---------------------------------------------------------------------------------------------
    // Charged jump, stomp, body crush and charge (Phase 8C / 8D)
    // ---------------------------------------------------------------------------------------------

    /**
     * Every value below is only the built in default. The datapack file
     * {@code data/spiderqueen_hungrycraft/queen_beast/tuning.json} overrides them, so a pack retunes the
     * jump, the stomp, the charge and every block rule without touching the mod. They are what the game uses
     * when no file is present at all, exactly like the armor and the bite defaults above.
     */

    /** Ticks of held space that count as a full charged jump. */
    public static final int BEAST_JUMP_MAX_CHARGE_TICKS = 20;

    /** Charge below which a jump is a hop that never arms a stomp. */
    public static final float BEAST_JUMP_MIN_STOMP_CHARGE = 0.35F;

    /** Vertical velocity of the weakest beast jump, in blocks per tick. */
    public static final double BEAST_JUMP_MIN_VERTICAL_VELOCITY = 0.55D;

    /** Vertical velocity of a fully charged beast jump. Roughly four to five blocks of height. */
    public static final double BEAST_JUMP_MAX_VERTICAL_VELOCITY = 1.05D;

    /** Forward velocity added to a jump, so a running beast carries its momentum over a gap. */
    public static final double BEAST_JUMP_FORWARD_VELOCITY = 0.22D;

    /** Physical damage of a stomp landing against every legal target in its radius. */
    public static final float BEAST_STOMP_DAMAGE = 15.0F;

    /** Horizontal radius of the stomp in blocks. */
    public static final double BEAST_STOMP_RADIUS = 4.0D;

    /** Vertical range of the stomp above and below the landing point, in blocks. */
    public static final double BEAST_STOMP_VERTICAL_RANGE = 2.5D;

    /** Upward impulse every stomp victim receives. */
    public static final double BEAST_STOMP_KNOCK_UP = 0.9D;

    /** Outward impulse every stomp victim receives, away from the landing point. */
    public static final double BEAST_STOMP_KNOCK_OUTWARD = 0.35D;

    /** First person impulse of a stomp landing, heavier than the bite impulse on purpose. */
    public static final int BEAST_STOMP_SHAKE_TICKS = 10;
    public static final float BEAST_STOMP_SHAKE_PITCH = 5.0F;
    public static final float BEAST_STOMP_SHAKE_YAW = 1.5F;
    public static final float BEAST_STOMP_SHAKE_ROLL = 0.7F;
    public static final double BEAST_STOMP_SHAKE_FOV = 4.0D;

    /** Ground debris particles of a stomp, spread over the whole footprint. Much heavier than a bite. */
    public static final int BEAST_STOMP_DEBRIS_PARTICLES = 3;
    public static final int BEAST_STOMP_DEBRIS_SPOTS = 26;
    public static final int BEAST_STOMP_DUST_PUFFS = 30;
    public static final int BEAST_STOMP_IMPACT_FLASHES = 6;

    /** How far below the landing point the stomp searches for the ground it erupted from. */
    public static final int BEAST_STOMP_GROUND_SEARCH_DEPTH = 3;

    /** Hard cap of blocks one stomp may destroy, so one landing can never rewrite a whole hillside. */
    public static final int BEAST_STOMP_MAX_BLOCKS = 128;

    /** Speed of the continuous charge in blocks per tick. */
    public static final double BEAST_CHARGE_SPEED = 0.85D;

    /** Active charge ticks per food point the charge costs. */
    public static final int BEAST_CHARGE_FOOD_INTERVAL_TICKS = 20;

    /** Food levels charged per interval. The old one-shot price of four is gone for good. */
    public static final int BEAST_CHARGE_FOOD_PER_INTERVAL = 1;

    /** Physical damage of one charge impact. */
    public static final float BEAST_CHARGE_DAMAGE = 20.0F;

    /** Upward impulse of a charge victim. */
    public static final double BEAST_CHARGE_KNOCK_UP = 0.8D;

    /** Forward impulse of a charge victim, in the direction of the charge. */
    public static final double BEAST_CHARGE_KNOCK_FORWARD = 1.0D;

    /** How far the charge direction may turn towards the look direction per tick, in degrees. */
    public static final double BEAST_CHARGE_MAX_TURN_PER_TICK = 3.0D;

    /** Power of the real explosion of a hard impact. The same as a block of TNT. */
    public static final double BEAST_CHARGE_HARD_IMPACT_EXPLOSION_POWER = 4.0D;

    /** Hard cap of blocks one charge tick may destroy. */
    public static final int BEAST_CHARGE_MAX_BLOCKS_PER_TICK = 128;

    /** Hard cap of blocks the body crush may destroy per tick while simply walking. */
    public static final int BEAST_BODY_CRUSH_MAX_BLOCKS_PER_TICK = 64;

    /**
     * How far above the feet plane a collision shape may still count as the floor that carries the beast
     * (Phase 8D-POLISH-1). A block whose shape only reaches up to the feet, or a hair above it, is a support
     * surface and never a corridor obstacle, so a charge can not dig the ground out from under itself.
     */
    public static final double BEAST_FLOOR_EPSILON = 0.02D;

    /**
     * Side clearance of the destruction corridor of the beast, in blocks. The corridor is the full body plus
     * this small margin, never a narrow line in the middle of the creature.
     */
    public static final double BEAST_CORRIDOR_MARGIN_HORIZONTAL = 0.12D;

    /** Head clearance of the destruction corridor of the beast, in blocks. The bottom margin is always zero. */
    public static final double BEAST_CORRIDOR_MARGIN_TOP = 0.08D;

    /** How far in front of the body the body crush predicts, so the obstacle is caught before it stops it. */
    public static final double BEAST_BODY_CRUSH_LOOKAHEAD = 0.35D;

    /** Strength of the continuous charge rumble, and how much the lens widens while charging. */
    public static final float BEAST_CHARGE_RUMBLE_PITCH = 0.6F;
    public static final float BEAST_CHARGE_RUMBLE_YAW = 0.35F;
    public static final double BEAST_CHARGE_FOV_WIDENING = 2.5D;

    private QueenBeastConstants()

    {
    }
}
