package spiderqueenhungrycraft.brood;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import spiderqueenhungrycraft.faction.FactionAggroMemory;
import spiderqueenhungrycraft.faction.QueenColonyRelations;

/**
 * Who a hit of a spider of the brood blames (Special Spiders web shot fix).
 *
 * <p>An arrow of a spider blames the spider: the game writes the shooter into the last hurt by mob of the victim and
 * the prey turns on the one that shot at it. A web shot of the same spider used to go through the provocation of the
 * queen instead - one web projectile, one rule for every shooter, and that rule is written for the queen - so the prey
 * turned around and went after the queen player, who may be a hundred blocks away and never touched it. This class is
 * the one place that says what such a hit means: the spider is blamed, exactly like its arrow, and the prey itself
 * answers.
 *
 * <p>It is deliberately <b>not</b> {@link spiderqueenhungrycraft.hostility.QueenProvocation}: nothing here wakes up the
 * pack of the victim, because an arrow does not wake up a pack either, and nothing here marks the queen as the attacker
 * of that creature. The provocation of the queen stays exactly what it is for the shots the queen really fired herself.
 *
 * <p>The memory that is written here is the ordinary faction memory ({@link FactionAggroMemory}), so the neutrality
 * guard of the faction system sees the attack as well: a web shot deals no damage at all, and without that memory a
 * neutral monster would lose the spider as its target again within a second.
 */
public final class SpiderProvocation
{
    private SpiderProvocation()
    {
    }

    /** Makes that victim blame that spider: it remembers the attack and, if it is a mob, it turns on the spider. */
    public static void provoke(SpiderMinionEntity spider, LivingEntity victim)
    {
        if (victim.level().isClientSide() || victim == spider || !victim.isAlive())
        {
            return;
        }

        // The allies of the shooter are never blamed. An arrow of a spider of the brood does not even register one of its
        // queen's creatures as a hit ({@code SpiderArrow#canHitEntity}), so the one case this rule can meet here is the
        // queen herself standing in the line of fire: she is webbed like any creature, but her own spider is not written
        // into her last hurt by mob for it.
        if (QueenColonyRelations.isQueenSideFriendly(victim, spider))
        {
            return;
        }

        // The memory first, for the same reason the provocation of the queen writes it first: the neutrality guard of
        // the faction system only lets a neutral creature keep a target that it remembers being hit by.
        FactionAggroMemory.remember(victim, spider);
        victim.setLastHurtByMob(spider);

        if (victim instanceof Mob mob && !mob.isRemoved())
        {
            mob.setTarget(spider);
        }
    }
}
