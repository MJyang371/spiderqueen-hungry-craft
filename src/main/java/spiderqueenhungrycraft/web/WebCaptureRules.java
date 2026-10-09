package spiderqueenhungrycraft.web;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Rules of the capture feature. Who may be captured at all is answered by tags and class checks, how
 * likely a capture is and how nourishing the prey is is answered by plain numbers, so the whole
 * feature stays readable and data pack friendly.
 */
public final class WebCaptureRules
{
    /** Only prey at or below this health can be captured at all. */
    public static final float MAX_CAPTURABLE_HEALTH = 20.0F;

    /** Chance gained per missing health point below {@link #MAX_CAPTURABLE_HEALTH}. */
    public static final double CHANCE_PER_HEALTH_POINT = 0.05D;

    /** Chance gained per Webbed level. Webbed I is amplifier 0, so its bonus is zero. */
    public static final double CHANCE_PER_WEBBED_LEVEL = 0.10D;

    /** Chance never goes above this, even for a nearly dead Webbed IV target. */
    public static final double MAX_CHANCE = 0.95D;

    /** Nutrition per block of bounding box volume. */
    public static final double NUTRITION_PER_VOLUME = 12.0D;

    public static final int MIN_NUTRITION = 2;
    public static final int MAX_NUTRITION = 20;

    private WebCaptureRules()
    {
    }

    public static boolean isSpiderLike(Entity entity)
    {
        return QueenWebRules.isSpiderLike(entity);
    }

    public static boolean isWebImmune(Entity entity)
    {
        return QueenWebRules.isWebImmune(entity);
    }

    public static boolean isUncapturable(Entity entity)
    {
        return entity.getType().is(QueenWebEntityTags.UNCAPTURABLE);
    }

    /** @return true when a data pack opted this entity type into capture without it extending Mob. */
    public static boolean isForceCapturable(Entity entity)
    {
        return entity.getType().is(QueenWebEntityTags.FORCE_CAPTURABLE);
    }

    /**
     * Web affectable targets: what a web shot may slow, nudge, flash and, if the capture rules allow
     * it, capture. Mobs and normal players qualify, plus data pack opted in living entities.
     *
     * <p>Excluded are the Spider Queen herself, everything in the spider_like and web_immune tags, and
     * every living entity that is not a mob, such as an armour stand. Boats, minecarts, item entities
     * and projectiles are not living entities in the first place, so they are refused as well.
     */
    public static boolean isWebAffectable(Entity entity)
    {
        if (!(entity instanceof LivingEntity))
        {
            return false;
        }
        if (entity instanceof ArmorStand)
        {
            return false;
        }
        if (isSpiderLike(entity) || isWebImmune(entity))
        {
            return false;
        }
        if (entity instanceof Player)
        {
            // Normal players can be webbed for future player versus player, queens are already filtered.
            return true;
        }

        return entity instanceof Mob || isForceCapturable(entity);
    }

    /**
     * Permanent capture gate. Players, spider like creatures, web immune creatures, uncapturable
     * entities (bosses) and everything that is not a mob are refused here. Health, Webbed stacks and
     * the random roll are handled by the capture stage.
     */
    public static boolean isCaptureCandidate(Entity entity)
    {
        if (!(entity instanceof LivingEntity))
        {
            return false;
        }
        if (entity instanceof Player)
        {
            return false;
        }
        if (isSpiderLike(entity) || isWebImmune(entity) || isUncapturable(entity))
        {
            return false;
        }

        return entity instanceof Mob || isForceCapturable(entity);
    }

    /**
     * @param currentHealth the health the target has right now, never its maximum health
     * @param webbedAmplifier the Webbed level, -1 when the target is not webbed
     * @return the capture chance in the range 0.0 to {@link #MAX_CHANCE}
     */
    public static double captureChance(float currentHealth, int webbedAmplifier)
    {
        if (currentHealth > MAX_CAPTURABLE_HEALTH)
        {
            return 0.0D;
        }

        double baseChance = ((double) (MAX_CAPTURABLE_HEALTH + 1.0F - currentHealth)) * CHANCE_PER_HEALTH_POINT;
        double webbedBonus = Math.max(webbedAmplifier, 0) * CHANCE_PER_WEBBED_LEVEL;

        return Mth.clamp(baseChance + webbedBonus, 0.0D, MAX_CHANCE);
    }

    /** @return the volume of the entity bounding box in blocks. */
    public static double getBoundingBoxVolume(Entity entity)
    {
        return volume(entity.getBoundingBox());
    }

    /** @return how much hunger eating this prey restores, 1 to 20. */
    public static int nutritionFor(Entity entity)
    {
        return Mth.clamp((int) Math.round(volume(entity.getBoundingBox()) * NUTRITION_PER_VOLUME),
                MIN_NUTRITION, MAX_NUTRITION);
    }

    private static double volume(AABB box)
    {
        return (double) box.getXsize() * (double) box.getYsize() * (double) box.getZsize();
    }
}
