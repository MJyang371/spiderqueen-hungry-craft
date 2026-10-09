package spiderqueenhungrycraft.devour;

/**
 * Every tunable number of the devour and regurgitate feature (Phase 8E2A, retuned by POLISH-1 and POLISH-2).
 *
 * <p>The input semantics, the two impulses, the release region, the two vanilla sounds and the whole fluid of the
 * vomit are described here and nowhere else, so balancing the feature never means searching for a magic number
 * inside the logic.
 *
 * <p>Phase 8E2A-POLISH-2 only touched the presentation of this class: the volume of the frog, the two camera
 * impulses, the lunge multipliers, the impulse of a vomited thing, the saliva of a devour, the droplets of a
 * digestion and the fluid of a vomit. The hold threshold and the whole gameplay stay exactly as they were.
 */
public final class DevourTuning
{
    // ---------------------------------------------------------------------------------------------
    // Input semantics
    // ---------------------------------------------------------------------------------------------

    /** Server ticks the devour key has to be held before it turns into "regurgitate all". Unchanged. */
    public static final int HOLD_THRESHOLD_TICKS = 10;

    // ---------------------------------------------------------------------------------------------
    // Devour
    // ---------------------------------------------------------------------------------------------

    /**
     * Volume and pitch of the frog eat of a devour. A beast is a heavy animal: swallowing a whole creature has
     * to be heard. A volume above one is deliberate and reaches further than sixteen blocks.
     */
    public static final float DEVOUR_SOUND_VOLUME = 4.8F;
    public static final float DEVOUR_SOUND_PITCH = 0.65F;

    /** How far above the cell centre a swallowed creature is placed, and how wide that ring may be. */
    public static final double CELL_DROP_RING_RADIUS = 2.5D;
    public static final double CELL_DROP_HEIGHT = 0.5D;

    /** Tries of the deterministic ring before a swallowed creature is placed at the cell centre. */
    public static final int CELL_DROP_TRIES = 24;

    /** How much harder than a bite a devour throws the body of the beast forward. */
    public static final float DEVOUR_LUNGE_SCALE = 1.25F;

    // ---------------------------------------------------------------------------------------------
    // Regurgitate
    // ---------------------------------------------------------------------------------------------

    /** Distance in front of the queen the vomit lands, in blocks. */
    public static final double RELEASE_DISTANCE = 2.0D;

    /** How far the release region may be adjusted while a safe spot is searched, in blocks. */
    public static final double RELEASE_FORWARD_ADJUST = 0.5D;
    public static final double RELEASE_HEIGHT_ADJUST = 1.5D;
    public static final double RELEASE_SIDE_SPREAD = 0.8D;

    /** Forward impulse of a vomited thing, in blocks per tick. Heavy since POLISH-2, like a real shove. */
    public static final double FORWARD_SPEED = 2.40D;

    /** Upward impulse of a vomited thing, in blocks per tick. */
    public static final double UP_SPEED = 0.45D;

    /** Sideways jitter of that impulse, in blocks per tick. */
    public static final double SIDE_JITTER = 0.18D;

    /** Volume and pitch of the llama spit of a successful regurgitation. */
    public static final float REGURGITATE_SOUND_VOLUME = 1.6F;
    public static final float REGURGITATE_SOUND_PITCH = 0.75F;

    /** How much harder than a bite a regurgitation throws the body of the beast forward. */
    public static final float REGURGITATE_LUNGE_SCALE = 1.20F;

    // ---------------------------------------------------------------------------------------------
    // Camera impulses of the actions (Phase 8E2A-POLISH-1, strengthened by POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /**
     * The impulse of a devour: the shape of a bite, clearly stronger, because a whole creature is caught. The
     * bite of the beast keeps its own values from the tuning file, this is a second flavour of the very same
     * camera system and not a second camera system.
     */
    public static final int DEVOUR_SHAKE_TICKS = 11;
    public static final float DEVOUR_SHAKE_PITCH = 4.8F;
    public static final float DEVOUR_SHAKE_YAW = 1.7F;
    public static final float DEVOUR_SHAKE_ROLL = 0.70F;
    public static final double DEVOUR_SHAKE_FOV = 4.0D;

    /** The impulse of a regurgitation: a little longer and a little lower than a bite. */
    public static final int REGURGITATE_SHAKE_TICKS = 11;
    public static final float REGURGITATE_SHAKE_PITCH = 3.8F;
    public static final float REGURGITATE_SHAKE_YAW = 1.4F;
    public static final float REGURGITATE_SHAKE_ROLL = 0.6F;
    public static final double REGURGITATE_SHAKE_FOV = 3.6D;

    /** The light kick an ordinary queen feels when she throws her stomach up: no beast impulse, just a nudge. */
    public static final int QUEEN_KICK_TICKS = 6;
    public static final float QUEEN_KICK_PITCH = 2.0F;
    public static final float QUEEN_KICK_YAW = 0.6F;
    public static final float QUEEN_KICK_ROLL = 0.3F;
    public static final double QUEEN_KICK_FOV = 1.4D;

    // ---------------------------------------------------------------------------------------------
    // Digestive fluid
    // ---------------------------------------------------------------------------------------------

    /** The dark green of the fluid. Deliberately not a bright neon green. */
    public static final float FLUID_RED = 0.16F;
    public static final float FLUID_GREEN = 0.44F;
    public static final float FLUID_BLUE = 0.12F;

    /** Size of one droplet of the cloud of a vomit. */
    public static final float FLUID_SCALE = 2.5F;

    /** The bulk of the cloud: dark green droplets that really fly forward along the look direction. */
    public static final int FLUID_PARTICLES_MAIN = 180;

    /** The spray of other vanilla particles, thrown in every direction like a real splash. */
    public static final int FLUID_SPIT_PARTICLES = 24;
    public static final int FLUID_SPLASH_PARTICLES = 24;
    public static final int FLUID_POOF_PARTICLES = 12;

    /** How many batches that cloud is split into, one batch per server tick. */
    public static final int FLUID_BATCHES = 4;

    /** Half angle of the cone of the vomit, in degrees. Nothing ever flies backwards. */
    public static final double FLUID_CONE_HALF_ANGLE = 32.0D;

    /** Speed of one droplet along the cone, in blocks per tick. */
    public static final double FLUID_FORWARD_MIN = 0.25D;
    public static final double FLUID_FORWARD_MAX = 0.80D;

    /** Vertical part of the cone, in blocks per tick, on top of the direction of the cone. */
    public static final double FLUID_UP_MIN = -0.05D;
    public static final double FLUID_UP_MAX = 0.30D;

    /** Sideways jitter of the spray of the ordinary particles of the game. */
    public static final double FLUID_SIDE_JITTER = 0.10D;

    /** Speed of that spray. */
    public static final double FLUID_SPRAY_SPEED = 0.35D;

    /** Height of the mouth above the feet of the queen, in blocks. */
    public static final double MOUTH_HEIGHT = 1.35D;

    /** How far in front of the mouth the droplets of a vomit are born, in blocks. */
    public static final double FLUID_START_MIN = 0.8D;
    public static final double FLUID_START_MAX = 1.6D;

    /** How wide and how high the droplets of a vomit are born around that line, in blocks. */
    public static final double FLUID_BIRTH_SPREAD = 0.45D;
    public static final double FLUID_BIRTH_HEIGHT = 0.30D;

    // ---------------------------------------------------------------------------------------------
    // Saliva of a devour (Phase 8E2A-POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /**
     * The droplets a beast throws out of its mouth when it snaps: a mouthful, not a vomit. They are the same
     * fluid as the cloud of a regurgitation, they are simply fewer, slower and much narrower.
     */
    public static final int SALIVA_COUNT = 70;

    /** Half angle of the saliva cone, in degrees. Narrow: a snap sprays forward, it does not open a cloud. */
    public static final double SALIVA_CONE_HALF_ANGLE = 18.0D;

    /** Speed of one droplet of saliva along the cone, in blocks per tick. */
    public static final double SALIVA_FORWARD_MIN = 0.15D;
    public static final double SALIVA_FORWARD_MAX = 0.40D;

    /** Vertical part of the saliva cone, in blocks per tick. */
    public static final double SALIVA_UP_MIN = 0.05D;
    public static final double SALIVA_UP_MAX = 0.20D;

    /** How far in front of the mouth the saliva is born, in blocks, and how wide around that line. */
    public static final double SALIVA_START_MIN = 0.8D;
    public static final double SALIVA_START_MAX = 1.5D;
    public static final double SALIVA_SPREAD = 0.25D;

    // ---------------------------------------------------------------------------------------------
    // Digestion (Phase 8E2A-POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /** Droplets a victim of the digestive pulse loses per hit. */
    public static final int DIGESTION_DROPLETS = 14;

    /** The acid hiss of one victim: the sound of fire being quenched, at the body of the victim. */
    public static final float DIGESTION_SOUND_VOLUME = 0.9F;
    public static final float DIGESTION_SOUND_PITCH_MIN = 0.85F;
    public static final float DIGESTION_SOUND_PITCH_MAX = 1.05F;

    /** Speed of one droplet of a digestion: it drips off the body, it does not fly. */
    public static final double DIGESTION_SPEED_MIN = 0.02D;
    public static final double DIGESTION_SPEED_MAX = 0.12D;
    public static final double DIGESTION_UP_MIN = 0.0D;
    public static final double DIGESTION_UP_MAX = 0.08D;

    // ---------------------------------------------------------------------------------------------
    // Stomach sound relay (Phase 8E2A-POLISH-2)
    // ---------------------------------------------------------------------------------------------

    /** The owner hears a sound of their stomach at exactly this share of the volume heard inside. */
    public static final float RELAY_VOLUME_FACTOR = 0.50F;

    /** The most sounds one cell may relay to its owner within a single server tick. */
    public static final int RELAY_MAX_PER_CELL_TICK = 8;

    /** Repeats of the same sound of one cell within this many ticks are merged into one relay. */
    public static final int RELAY_DUPLICATE_TICKS = 2;

    private DevourTuning()
    {
    }
}
