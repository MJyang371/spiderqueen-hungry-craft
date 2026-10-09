package spiderqueenhungrycraft.compat.iceandfire;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.github.alexthe666.iceandfire.entity.EntityDragonBase;
import com.github.alexthe666.iceandfire.entity.EntityIceDragon;
import com.github.alexthe666.iceandfire.entity.EntityLightningDragon;
import com.github.alexthe666.iceandfire.enums.EnumDragonTextures;
import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import spiderqueenhungrycraft.config.HungryCraftConfig;

/**
 * The one and only thing this mod changes about how Ice and Fire draws a dragon (Necromancy IV prototype 2).
 *
 * <p><b>Why it is needed.</b> Ice and Fire decides the whole appearance of a dragon in one place, and that place asks
 * one question twice: a corpse is drawn as bones while {@code getDeathStage() >= getAgeInDays() / 5 / 2} and as a body
 * that still has flesh before that, and both branches hang on {@code isModelDead()}. The very same
 * {@code isModelDead()} is what makes {@code isNoAi()} true and therefore stops every goal of the dragon: the flag
 * that draws bones is the flag that stops the animal.
 *
 * <p>The prototype solves it by taking the flag away - which is what wakes the dragon up - and putting the <b>one</b>
 * consequence of it back: a dragon that HungryCraft has woken is drawn as the skeleton it is. Nothing else is touched.
 * The model, the animations, the flight, the walk cycle, the attack poses and the eye layer stay Ice and Fire's own.
 *
 * <p><b>The condition is the marker, and nothing but the marker.</b> A dragon is forced into bones when
 * {@link NecromancyMarker#isRevived} says so, so an ordinary living dragon is never touched, an ordinary corpse is
 * never touched (its texture is Ice and Fire's own decision either way), and a dragon that some other mod or a data
 * pack left in a strange state is never touched either. But the marker lives in the <b>persistent data</b> of the
 * entity, which Forge writes and reads from disk and never sends to anybody, so the server mirrors its value to the
 * clients that can see the dragon ({@link ClientNecromancyState}). This class therefore asks both: the marker for a
 * server side caller, the mirror for the rendering thread of a client.
 *
 * <p>On top of that it writes the collected evidence of a probe run into the log, once per state change of a dragon
 * and never once per frame: which texture Ice and Fire alone would have asked for, which one a player really sees, and
 * what the eye layer of Ice and Fire is doing - the three things the round asks to be reported about.
 */
public final class IceAndFireTextureOverride
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many dragons are remembered for the log before that memory is dropped. */
    private static final int MAX_REPORTED = 256;

    /** The last state that was reported for each dragon: one log line per change, never one per frame. */
    private static final Map<Integer, String> LAST_REPORTED = new HashMap<>();

    /** Set while this class asks Ice and Fire for its own answer, so the hook stands aside for exactly that call. */
    private static boolean computingNative;

    private IceAndFireTextureOverride()
    {
    }

    /**
     * @return the skeleton texture a <b>woken</b> dragon has to be drawn with, or null when Ice and Fire's own choice
     *         must stand (an ordinary living dragon, an ordinary corpse, or the override switched off by a pack).
     */
    @Nullable
    public static ResourceLocation skeletalTextureFor(EntityDragonBase dragon)
    {
        if (computingNative)
        {
            // Re-entered by the report below, which asks Ice and Fire what it would have answered. The hook has to be
            // transparent for exactly that one call, or it would report its own answer as if it were Ice and Fire's.
            return null;
        }

        boolean marker = NecromancyMarker.isRevived(dragon);
        boolean mirror = ClientNecromancyState.isRevived(dragon.getId());
        boolean forced = (marker || mirror) && !dragon.isModelDead()
                && HungryCraftConfig.iceAndFireSkeletonTexture();
        ResourceLocation skeleton = forced ? skeletonFor(dragon) : null;

        report(dragon, marker, mirror, forced, skeleton);

        return skeleton;
    }

    /**
     * @return true when the eye layer of Ice and Fire has to stay silent for that dragon.
     *
     *         <p>It is asked by the second, equally optional hook of this compatibility, and it answers "no" by
     *         default: Ice and Fire's glowing eyes on the skull of a woken dragon are the intended look, and a pack has
     *         to ask for them to be hidden.
     */
    public static boolean hidesEyesOfRevivedDragon(EntityDragonBase dragon)
    {
        if (HungryCraftConfig.iceAndFireSkeletonGlowingEyes())
        {
            return false;
        }

        return NecromancyMarker.isRevived(dragon) || ClientNecromancyState.isRevived(dragon.getId());
    }

    /** @return the skeleton texture of that dragon, chosen exactly the way Ice and Fire chooses its own. */
    private static ResourceLocation skeletonFor(EntityDragonBase dragon)
    {
        EnumDragonTextures textures = EnumDragonTextures.getDragonEnum(dragon);

        if (dragon instanceof EntityIceDragon)
        {
            return byStage(dragon.getDragonStage(), textures.ICESTAGE1SKELETONTEXTURE, textures.ICESTAGE2SKELETONTEXTURE,
                    textures.ICESTAGE3SKELETONTEXTURE, textures.ICESTAGE4SKELETONTEXTURE,
                    textures.ICESTAGE5SKELETONTEXTURE);
        }

        if (dragon instanceof EntityLightningDragon)
        {
            return byStage(dragon.getDragonStage(), textures.LIGHTNINGSTAGE1SKELETONTEXTURE,
                    textures.LIGHTNINGSTAGE2SKELETONTEXTURE, textures.LIGHTNINGSTAGE3SKELETONTEXTURE,
                    textures.LIGHTNINGSTAGE4SKELETONTEXTURE, textures.LIGHTNINGSTAGE5SKELETONTEXTURE);
        }

        return byStage(dragon.getDragonStage(), textures.FIRESTAGE1SKELETONTEXTURE, textures.FIRESTAGE2SKELETONTEXTURE,
                textures.FIRESTAGE3SKELETONTEXTURE, textures.FIRESTAGE4SKELETONTEXTURE,
                textures.FIRESTAGE5SKELETONTEXTURE);
    }

    /** @return the skeleton texture of that growth stage, the one of stage four when the stage is not 1..5. */
    private static ResourceLocation byStage(int stage, ResourceLocation one, ResourceLocation two, ResourceLocation three,
            ResourceLocation four, ResourceLocation five)
    {
        return switch (stage)
        {
            case 1 -> one;
            case 2 -> two;
            case 3 -> three;
            case 5 -> five;
            default -> four;
        };
    }

    /** @return what Ice and Fire alone would answer for that dragon, asked with the hook standing aside. */
    private static ResourceLocation nativeTextureOf(EntityDragonBase dragon)
    {
        computingNative = true;

        try
        {
            return EnumDragonTextures.getTextureFromDragon(dragon);
        }
        finally
        {
            computingNative = false;
        }
    }

    /**
     * Writes the evidence of one state of one dragon into the log, and only when that state really changed.
     *
     * <p>Three questions are answered in two lines, and they are exactly the three the round asks to be reported:
     * <ol>
     * <li>what Ice and Fire alone would draw (its own skeleton branch, or its living one),</li>
     * <li>what a player really sees (Ice and Fire's answer, or the skeleton this class forced instead),</li>
     * <li>and what the eye layer is doing: whether it would draw at all, and which texture it would use.</li>
     * </ol>
     */
    private static void report(EntityDragonBase dragon, boolean marker, boolean mirror, boolean forced,
            @Nullable ResourceLocation skeleton)
    {
        String signature = dragon.getId() + "|" + dragon.isModelDead() + "|" + forced + "|" + dragon.getDragonStage()
                + "|" + HungryCraftConfig.iceAndFireSkeletonTexture()
                + "|" + HungryCraftConfig.iceAndFireSkeletonGlowingEyes();

        if (signature.equals(LAST_REPORTED.get(dragon.getId())))
        {
            return;
        }

        if (LAST_REPORTED.size() > MAX_REPORTED)
        {
            LAST_REPORTED.clear();
        }

        LAST_REPORTED.put(dragon.getId(), signature);

        ResourceLocation nativeTexture = nativeTextureOf(dragon);

        LOGGER.info("{} texture {} stage={} modelDead={} skeletal={} revived[marker={} clientMirror={}] hook={}"
                + " native={} shown={}", IceAndFireSupport.PROBE_LOG_PREFIX, EntityType.getKey(dragon.getType()),
                dragon.getDragonStage(), dragon.isModelDead(), dragon.isSkeletal(), marker, mirror,
                forced ? "skeleton" : "off", nativeTexture, skeleton == null ? nativeTexture : skeleton);

        LOGGER.info("{} eyes    {} stage={} layer={} shouldRenderEyes(now)={} eyeTexture={}",
                IceAndFireSupport.PROBE_LOG_PREFIX, EntityType.getKey(dragon.getType()), dragon.getDragonStage(),
                HungryCraftConfig.iceAndFireSkeletonGlowingEyes() ? "Ice and Fire's own"
                        : "hidden for a revived dragon",
                dragon.shouldRenderEyes(), EnumDragonTextures.getEyeTextureFromDragon(dragon));
    }
}
