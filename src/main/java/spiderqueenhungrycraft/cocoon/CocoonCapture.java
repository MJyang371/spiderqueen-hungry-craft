package spiderqueenhungrycraft.cocoon;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.web.WebCaptureRules;

/**
 * Server side capture: the permanent rules, the random roll and the hand over from prey to cocoon.
 * The roll always happens here, never on a client.
 */
public final class CocoonCapture
{
    private CocoonCapture()
    {
    }

    /**
     * @param webbedAmplifier the Webbed level the target has right now, -1 when it is not webbed
     * @return true when the prey was captured and replaced by a cocoon
     */
    public static boolean tryCapture(ServerLevel level, LivingEntity target, int webbedAmplifier)
    {
        if (!HungryCraftConfig.captureEnabled())
        {
            return false;
        }
        if (!WebCaptureRules.isCaptureCandidate(target))
        {
            return false;
        }

        double chance = WebCaptureRules.captureChance(target.getHealth(), webbedAmplifier);
        if (chance <= 0.0D || level.getRandom().nextDouble() >= chance)
        {
            return false;
        }

        return capture(level, target);
    }

    private static boolean capture(ServerLevel level, LivingEntity target)
    {
        CocoonData data = CocoonData.capture(target, WebCaptureRules.nutritionFor(target));

        CocoonEntity cocoon = new CocoonEntity(level);
        cocoon.moveTo(target.getX(), target.getY(), target.getZ(), target.getYRot(), 0.0F);
        cocoon.setCocoonData(data);

        // The cocoon is spawned first, the prey is only removed once that really worked.
        if (!level.addFreshEntity(cocoon))
        {
            return false;
        }

        target.discard();
        level.playSound(null, cocoon.getX(), cocoon.getY(), cocoon.getZ(), SoundEvents.WOOL_PLACE,
                SoundSource.NEUTRAL, 0.8F, 0.7F);

        return true;
    }
}
