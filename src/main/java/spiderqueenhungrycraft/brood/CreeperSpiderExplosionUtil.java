package spiderqueenhungrycraft.brood;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.faction.QueenColonyRelations;
import spiderqueenhungrycraft.faction.QueenOwnerProtection;

/**
 * The two blasts of a creeper spider (Phase X), and the one question they both have to answer: who is allowed to be hurt.
 *
 * <p><b>The explosion itself is the explosion of the game.</b> The falloff, the knockback, the vanilla emitter, the
 * vanilla sound, the damage source and the block damage are all vanilla, so a blast of this spider feels exactly like a
 * blast of the game and every other mod's explosion handling keeps working. Only the membership of it is decided here.
 *
 * <p><b>The size of the flash is added on top</b> (Explosion FX round). The game draws one emitter of one fixed size
 * whatever the power of a blast is, so the <b>look</b> of both blasts is scaled by {@link CreeperSpiderExplosionFx}
 * after the explosion has already happened - a mega blast that digs a hole twice as wide now also flashes twice as wide.
 * Nothing about the explosion itself is touched by it.
 *
 * <p><b>Friends are removed, not reduced.</b> The game fires {@link ExplosionEvent.Detonate} with the list of entities
 * it is about to hurt, right before any damage is applied, and Forge offers that list to be edited on purpose. So the
 * whole friendly protection of this species is {@link #isFriendly} plus that one removal: the queen of this spider, the
 * spiders of her colony and the spider itself are gone from the list before the first point of damage exists. That is
 * stronger than a damage multiplier of zero - a removed entity is not damaged, not knocked back and not flung into a
 * wall, either.
 *
 * <p><b>Two sizes.</b> The death blast is a small one - the power of a block of TNT, no block damage
 * ({@link Level.ExplosionInteraction#NONE} is the {@code KEEP} rule of the game). The mega blast is twice as strong and
 * breaks blocks through the ordinary mob rule of the game ({@link Level.ExplosionInteraction#MOB}, which the
 * {@code mobGriefing} gamerule and every protection mod can still refuse).
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class CreeperSpiderExplosionUtil
{
    /** The radius of the blast a dying spider leaves: the 4.0 of a block of TNT, as the design asks. */
    public static final float DEATH_RADIUS = 4.0F;

    /** The power of the mega blast: twice the power of a block of TNT. */
    public static final float MEGA_POWER = 8.0F;

    private CreeperSpiderExplosionUtil()
    {
    }

    /**
     * @return true when that entity belongs to the side this spider fights for and must survive its blast
     *
     * <p>Three answers, all of them already written in this mod and none of them new: the spider itself, the queen the
     * spider belongs to together with every creature of that queen (the owner protection of the brood), and any living
     * creature this spider may not attack at all. Two colonies of two queens are deliberately <b>not</b> friends here:
     * the same two creatures that are allowed to fight each other are allowed to be blown up by each other.
     */
    public static boolean isFriendly(CreeperSpiderEntity spider, Entity target)
    {
        if (target == spider)
        {
            return true;
        }

        if (!QueenOwnerProtection.mayAttack(spider, target))
        {
            return true;
        }

        if (target instanceof LivingEntity living)
        {
            if (!SpiderMinionTargeting.isAttackable(spider, living))
            {
                return true;
            }
        }

        return QueenColonyRelations.isQueenSideFriendly(spider, target);
    }

    /** The small blast of a dying spider: it hurts, it knocks back, it breaks nothing. */
    public static void detonateSmall(CreeperSpiderEntity spider)
    {
        blast(spider, DEATH_RADIUS, Level.ExplosionInteraction.NONE, false);
    }

    /** The mega blast of a spider that counted to zero: twice the TNT, and a hole in the ground. */
    public static void detonateMega(CreeperSpiderEntity spider)
    {
        blast(spider, MEGA_POWER, Level.ExplosionInteraction.MOB, true);
    }

    /**
     * Fires one explosion of that power from the feet of that spider, with the spider as its source - which is what the
     * death message and the filter below read, and what makes the blast the work of this spider and of nobody else - and
     * then draws the flash of that blast (Explosion FX round).
     *
     * <p>The ground is read <b>before</b> the blast runs: afterwards the crater is already there and the block that was
     * torn up is gone, so the debris of the flash would have nothing left to be made of.
     */
    private static void blast(CreeperSpiderEntity spider, float power, Level.ExplosionInteraction interaction,
            boolean mega)
    {
        if (!(spider.level() instanceof ServerLevel level))
        {
            return;
        }

        // The 0.0625 is where a creeper of the game explodes: at its feet, not at its back.
        double x = spider.getX();
        double y = spider.getY(0.0625D);
        double z = spider.getZ();
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.25D, z));

        level.explode(spider, x, y, z, power, false, interaction);

        if (mega)
        {
            CreeperSpiderExplosionFx.megaBlast(level, x, y, z, power, ground);
        }
        else
        {
            CreeperSpiderExplosionFx.deathBlast(level, x, y, z, power, ground);
        }
    }

    /**
     * The one filter of the two blasts: a blast of a creeper spider loses every friend before it hurts anybody.
     *
     * <p>It only ever touches explosions this species made - a vanilla creeper, a block of TNT or another mod's
     * explosion is not affected in any way, because the source of those is not a creeper spider.
     */
    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event)
    {
        if (!(event.getExplosion().getIndirectSourceEntity() instanceof CreeperSpiderEntity spider))
        {
            return;
        }

        event.getAffectedEntities().removeIf(entity -> isFriendly(spider, entity));
    }
}
