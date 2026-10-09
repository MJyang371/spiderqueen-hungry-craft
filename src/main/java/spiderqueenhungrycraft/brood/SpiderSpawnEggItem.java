package spiderqueenhungrycraft.brood;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.ForgeSpawnEggItem;

/**
 * The spawn eggs of the brood (Brood Command AI).
 *
 * <p>They are ordinary spawn eggs with exactly one difference: the spider that appears belongs to the player who used
 * the egg, so the test entrance of this feature produces a real minion instead of a spider without a queen. The owner
 * is taken from the player who clicked - never guessed from "some player standing nearby" - and if the egg is used by
 * something without a player (a dispenser, a command), the spider simply has no owner.
 */
public class SpiderSpawnEggItem extends ForgeSpawnEggItem
{
    public SpiderSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> typeSupplier, int backgroundColor,
            int highlightColor, Item.Properties properties)
    {
        super(typeSupplier, backgroundColor, highlightColor, properties);
    }

    /**
     * The vanilla placement of a spawn egg (the clicked block when it is not solid, otherwise the block in front of
     * the clicked face), plus the ownership of the spider that really appeared.
     */
    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Level level = context.getLevel();

        if (!(level instanceof ServerLevel serverLevel))
        {
            return InteractionResult.SUCCESS;
        }

        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);
        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty() ? clicked : clicked.relative(face);

        Entity spawned = this.getType(stack.getTag()).spawn(serverLevel, stack, player, spawnPos,
                MobSpawnType.SPAWN_EGG, true, !clicked.equals(spawnPos) && face == Direction.UP);

        if (spawned == null)
        {
            return InteractionResult.FAIL;
        }

        if (player != null && spawned instanceof SpiderMinionEntity spider)
        {
            // The queen who used the egg is the queen of that spider, and a fresh spider always follows.
            spider.setOwnerUUID(player.getUUID());
            spider.setOrderMode(SpiderOrderMode.FOLLOW);
        }

        if (player != null)
        {
            if (!player.getAbilities().instabuild)
            {
                stack.shrink(1);
            }

            player.awardStat(Stats.ITEM_USED.get(this));
        }

        level.gameEvent(player, GameEvent.ENTITY_PLACE, spawned.position());
        level.playSound(null, spawned.getX(), spawned.getY(), spawned.getZ(), SoundEvents.SPIDER_AMBIENT,
                SoundSource.NEUTRAL, 0.8F, 0.9F);

        return InteractionResult.sidedSuccess(false);
    }
}
