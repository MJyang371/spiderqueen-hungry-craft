package spiderqueenhungrycraft.stomach;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The feedback and the pain of a living stomach (Phase 8E1-GATE2A, extended in 8E1-GATE2B).
 *
 * <p>A core that really lost health makes the fleshy "pia" of a honey block, and the very same post mitigation
 * amount is carried on to the owner of the cell through the damage link. The listener sees the amount after the
 * visceral armor of the core was applied, so a hit that was completely eaten by the wall is neither heard nor
 * felt, while a hit that did get through is reported to its owner one to one.
 *
 * <p>The second listener keeps the wall where it is: no knockback of any source may push a core around.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class StomachCoreEvents
{
    private StomachCoreEvents()
    {
    }

    /** The fleshy hit of the wall, and the pain of its owner. */
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event)
    {
        if (!(event.getEntity() instanceof StomachCoreEntity core) || event.getAmount() <= 0.0F
                || !(core.level() instanceof ServerLevel level))
        {
            return;
        }

        StomachDamageLink.forward(level, core, event.getSource(), event.getAmount());

        BlockPos center = core.cell().map(StomachCell::center).orElse(core.blockPosition());

        level.playSound(null, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, StomachConstants.CORE_HIT_SOUND_VOLUME,
                StomachConstants.CORE_HIT_SOUND_PITCH);
    }

    /** A core is nailed to its cell, whatever hits it. */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event)
    {
        if (event.getEntity() instanceof StomachCoreEntity)
        {
            event.setCanceled(true);
        }
    }
}
