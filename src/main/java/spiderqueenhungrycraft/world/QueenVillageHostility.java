package spiderqueenhungrycraft.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.config.HungryCraftConfig;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The village rules of the Spider Queen (Phase 8H): village defenders (iron golems, guards, priests, ...) treat the
 * Spider Queen as a threat. Everything is driven by the {@code village_defenders} entity type tag, so third party mods
 * only need a tag entry instead of a hard dependency. Hero of the Village suspends all of it temporarily.
 *
 * <p>Since Phase HF1-VISIBLE the village is a faction of its own and the targeting itself is done by the one faction
 * target goal ({@code FactionTargetGoal} in the {@code faction} package), which asks the rules below through
 * {@code FactionTargeting#villageMayHuntQueen}. This class therefore subscribes to nothing any more - it only owns
 * the tuning of the village side, and the villager fear still uses its helpers.
 */
public final class QueenVillageHostility
{
    private QueenVillageHostility()
    {
    }

    /** Player built iron golems never attack players in vanilla, so they keep ignoring the queen by default. */
    public static boolean ignoresSpiderQueen(Mob mob)
    {
        return mob instanceof IronGolem && ((IronGolem) mob).isPlayerCreated()
                && !HungryCraftConfig.playerCreatedGolemsAttackSpiderQueen();
    }

    /** @return true while this player counts as the Spider Queen threat for villages. */
    public static boolean isActiveQueen(Player player)
    {
        return QueenDataCapability.isQueen(player) && player.isAlive()
                && !player.isCreative() && !player.isSpectator();
    }

    /** Hero of the Village is a temporary pass for the queen, it never rewrites existing reputation. */
    public static boolean isExemptByHero(Player player)
    {
        return HungryCraftConfig.heroOfTheVillageDisablesVillageHostility()
                && player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    /**
     * @param ignoreHeroExemption true to also find a queen that is currently protected by Hero of the Village
     * @return the closest Spider Queen this entity reacts to, or null
     */
    public static Player findNearestQueen(ServerLevel level, LivingEntity observer, double range, boolean ignoreHeroExemption)
    {
        double bestDistance = range * range;
        Player found = null;

        for (ServerPlayer candidate : level.players())
        {
            if (!isActiveQueen(candidate))
            {
                continue;
            }
            if (!ignoreHeroExemption && isExemptByHero(candidate))
            {
                continue;
            }
            if (!observer.hasLineOfSight(candidate))
            {
                continue;
            }

            double distance = observer.distanceToSqr(candidate);
            if (distance <= bestDistance)
            {
                bestDistance = distance;
                found = candidate;
            }
        }

        return found;
    }
}
