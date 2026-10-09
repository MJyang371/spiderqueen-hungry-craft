package spiderqueenhungrycraft.web;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.player.QueenDataCapability;

/**
 * The single authority on "who is a spider" and "what is a web" (Phase 6-SIMPLE-2).
 *
 * <p>Web natives do not ignore webs, they live in them: light horizontal drag, a limited fall speed and no
 * fall damage. Everything is driven by two datapack tags, so an integration pack only has to add its own
 * spiders or its own web blocks to opt in.
 */
public final class WebAffinity
{
    /** Every block that counts as a web, vanilla cobweb and the queen web by default. */
    public static final TagKey<Block> WEB_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "web_blocks"));

    /** Horizontal speed kept per tick while a web native moves inside a web, the vanilla value is 0.25. */
    public static final double WEB_HORIZONTAL_RETENTION = 0.85D;

    /** Slowest vertical speed a web native may reach inside a web, it must never fall freely in a web. */
    public static final double WEB_MAX_FALL_SPEED = -0.14D;

    /** A web native keeps its movement, so the vanilla stuck multiplier is replaced by this neutral one. */
    public static final Vec3 NATIVE_STUCK_MULTIPLIER = new Vec3(1.0D, 1.0D, 1.0D);

    /** Height samples of the body that are checked for web contact. */
    private static final double[] BODY_SAMPLES = { 0.1D, 0.9D };

    private WebAffinity()
    {
    }

    /** @return true when that entity is a spider in the biological / faction sense (the tag is the rule). */
    public static boolean isSpiderLike(Entity entity)
    {
        return entity.getType().is(QueenWebEntityTags.SPIDER_LIKE);
    }

    /** @return true when that entity can move through webs like a spider: the queen and every spider like. */
    public static boolean isWebNative(Entity entity)
    {
        if (entity instanceof Player player && QueenDataCapability.isQueen(player))
        {
            return true;
        }

        return isSpiderLike(entity);
    }

    /** @return true when that block state is a web, from this mod or from any other mod that joined the tag. */
    public static boolean isWebBlock(BlockState state)
    {
        return state != null && state.is(WEB_BLOCKS);
    }

    /** @return true when that entity touches a web block right now. */
    public static boolean isInWeb(Entity entity)
    {
        return touchedWebBlock(entity) != null;
    }

    /** @return true when that web belongs to an element, so it hurts everybody who is not a web native. */
    public static boolean isElementalWeb(BlockState state)
    {
        return state != null && state.getBlock() instanceof ElementalQueenWebBlock;
    }

    /**
     * @return the web block the entity currently touches, or null when it touches none. An ordinary web is
     *         preferred over an elemental one, because the gentle ordinary web physics only fit that web.
     */
    private static BlockState touchedWebBlock(Entity entity)
    {
        // Sampled on a few heights of the body, webs are entered by the lower body most of the time but a
        // fall from above may only touch the upper body first.
        BlockState elemental = null;

        for (double height : BODY_SAMPLES)
        {
            BlockPos pos = BlockPos.containing(entity.getX(), entity.getY() + height, entity.getZ());
            BlockState state = entity.level().getBlockState(pos);

            if (!isWebBlock(state))
            {
                continue;
            }

            if (isElementalWeb(state))
            {
                elemental = state;
                continue;
            }

            return state;
        }

        return elemental;
    }

    /**
     * The gentle web physics of a web native: a light horizontal drag, a limited fall speed and no fall
     * damage. It is a complete no-op for every other entity and outside of webs, so the vanilla web keeps
     * its full strength for everybody else.
     */
    public static void applyWebPhysics(LivingEntity entity)
    {
        if (!isWebNative(entity))
        {
            return;
        }

        BlockState web = touchedWebBlock(entity);

        if (web == null)
        {
            return;
        }

        if (isElementalWeb(web))
        {
            // An elemental web is completely safe for a web native, not even the gentle drag applies.
            entity.resetFallDistance();
            return;
        }

        Vec3 velocity = entity.getDeltaMovement();
        double x = velocity.x * WEB_HORIZONTAL_RETENTION;
        double z = velocity.z * WEB_HORIZONTAL_RETENTION;
        double y = Math.max(velocity.y, WEB_MAX_FALL_SPEED);

        entity.setDeltaMovement(x, y, z);
        entity.resetFallDistance();
    }
}
