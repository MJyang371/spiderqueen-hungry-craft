package spiderqueenhungrycraft.stomach;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.AABB;
import spiderqueenhungrycraft.brood.SpiderRewards;

/**
 * The meal a death inside a stomach gives to its owner (Phase 8E1-GATE2B).
 *
 * <p>Presence is the truth: only the position of the victim at the moment of death decides which stomach digested
 * it, and the meal is as large as the creature really was - measured on its own death-time hitbox, so a baby is
 * worth a baby, a scaled giant is worth a giant and a modded whale is worth a whale. No creature table, no
 * swallowed flag, no history of how the victim got there.
 *
 * <p>A core is never a meal, an owner never feeds on their own death, and a cell whose owner is away stays empty
 * instead of feeding somebody else.
 */
public final class StomachNutrition
{
    /** The multiplier that turns the volume of a death into bites of food. */
    public static final double NUTRITION_PER_VOLUME = 3.0D;

    /** The hunger bar of vanilla, which is never exceeded. */
    public static final int FOOD_CAP = 20;

    private StomachNutrition()
    {
    }

    /**
     * Feeds the owner of the stomach the victim died in, and pays what that death is worth.
     *
     * <p>Two things are given here and they are not the same thing: the <b>meal</b>, a number of bites measured on the
     * body of the victim, and the <b>payment</b> of a digested prey, which is the very same reward a cocoon pays when
     * the queen eats it by hand ({@link SpiderRewards}): the normal experience of the victim as real orbs and, one time
     * in four, exactly one spider egg.
     *
     * <p>Both are handed over where the queen really is - in the world she stands in, at her own feet. The victim died
     * in the cavity of a stomach dimension that only the cells of this system know anything about, and the queen is
     * almost always somewhere else entirely; an orb or an egg created in the stomach at the coordinates of her
     * outside-world position would fall into an empty chunk and nobody would ever see it.
     *
     * <p>Every creature pays that price, even one whose body is too small to fill a single bite of hunger: a chicken
     * fills nobody, but it is still a prey and still leaves what it is worth. The bites are gated, the payment is not.
     *
     * @return the amount of food that was given, or 0 when the death gives nothing to eat
     */
    public static int grant(LivingEntity victim)
    {
        if (!(victim.level() instanceof ServerLevel level) || victim instanceof StomachCoreEntity)
        {
            return 0;
        }

        StomachCell cell = StomachCellManager.resolveCell(victim).orElse(null);

        // The owner dying inside their own stomach is no meal at all.
        if (cell == null || cell.owner().equals(victim.getUUID()))
        {
            return 0;
        }

        ServerPlayer owner = StomachOwnership.onlineOwnerOf(level.getServer(), cell);

        if (owner == null)
        {
            return 0;
        }

        // The payment of the prey, in the world and at the feet of the queen (this round's breeding pass). It runs
        // exactly once per death, because the death hook of the stomach only feeds once.
        if (owner.level() instanceof ServerLevel ownerLevel)
        {
            SpiderRewards.grant(ownerLevel, owner.position(), victim, owner);
        }

        int nutrition = nutritionOf(victim);

        if (nutrition <= 0)
        {
            return 0;
        }

        FoodData food = owner.getFoodData();
        food.setFoodLevel(Math.min(FOOD_CAP, food.getFoodLevel() + nutrition));

        return nutrition;
    }

    /**
     * @return the meal of that victim: the volume of its hitbox at the moment of death, times
     *         {@link #NUTRITION_PER_VOLUME}, rounded to whole bites.
     */
    public static int nutritionOf(LivingEntity victim)
    {
        AABB box = victim.getBoundingBox();
        double volume = box.getXsize() * box.getYsize() * box.getZsize();

        return (int) Math.round(volume * NUTRITION_PER_VOLUME);
    }
}
