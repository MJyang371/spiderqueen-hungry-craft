package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.faction.QueenColonyRelations;

/**
 * The arrow of a skeleton spider (Special Spiders round I).
 *
 * <p>It is a vanilla {@link Arrow} - same flight, same gravity, same ground behaviour, same look, the vanilla arrow
 * renderer and the vanilla arrow entity of the game. The one difference is who it may hit:
 *
 * <p><b>It flies straight through the allies of its own queen.</b> Not "hits them for zero damage" - it never
 * registers them as a hit at all, so it keeps flying, keeps its speed and can still strike an enemy standing behind
 * them.
 *
 * <p>The ally test is not a list of spider classes. It asks the colony relations of this mod
 * ({@link QueenColonyRelations#isQueenSideFriendly}), which is the very same answer the faction AI uses: the spiders of
 * the same queen and the queen herself. Everything else - an enemy of the brood, a neutral creature, a block or a wall -
 * is hit exactly like a vanilla arrow would hit it.
 */
public class SpiderArrow extends Arrow
{
    public SpiderArrow(EntityType<? extends SpiderArrow> entityType, Level level)
    {
        super(entityType, level);
    }

    public SpiderArrow(Level level, LivingEntity shooter)
    {
        super(level, shooter);
    }

    /**
     * The pass-through rule of this arrow.
     *
     * <p>The vanilla arrow finds its victim with this very method, so returning false here means the allied creature is
     * not a hit at all: no damage, no knockback, no stuck arrow, no sound and no lost speed. The search simply carries
     * on and the first real enemy in the flight line is hit.
     */
    @Override
    protected boolean canHitEntity(Entity target)
    {
        return super.canHitEntity(target) && !isQueenAlly(target);
    }

    /** @return true when that creature stands on the side of the queen that owns this arrow. */
    private boolean isQueenAlly(@Nullable Entity target)
    {
        return QueenColonyRelations.isQueenSideFriendly(this.getOwner(), target);
    }
}
