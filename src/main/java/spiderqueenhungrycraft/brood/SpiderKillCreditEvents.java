package spiderqueenhungrycraft.brood;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * The kill of a spider of the brood counts as the kill of its queen (Brood Kill Credit pass).
 *
 * <p>A queen commands her brood, so a kill of a spider is a kill she made: the experience orbs belong to her, the
 * statistics of the game count it as hers, the advancements that ask for a kill see hers, and the two rewards of a raid
 * - Bad Omen from a patrol leader and Hero of the Village from the raiders of an active raid - are hers as well. It is
 * an army, and the game should say so wherever it asks who killed something.
 *
 * <p><b>One line does most of it.</b> The whole feature writes the queen into the kill credit of the victim
 * ({@code LivingEntity#setLastHurtByPlayer}), and the death that follows reads that field exactly as if she had swung
 * the last blow herself: the experience orbs are dropped, {@code getKillCredit} hands her the kill score, the
 * scoreboard mob counter and the {@code player_killed_entity} advancement, and the loot of the dead creature is rolled
 * with her as the last damage player, luck included. Nothing else is invented here, and nothing is granted twice: the
 * experience orbs are the ordinary reward of that creature, and the rank experience of the spider
 * ({@link SpiderVeterancyEvents}) is a separate counter that this class never touches.
 *
 * <p>It is the rule the game already has for a tamed animal - a wolf whose owner is known hands that owner the kill,
 * {@code LivingEntity#hurt} does exactly this for a {@code TamableAnimal} - and this class only says that a spider of
 * the queen is such a creature. Ranged kills need no special case either, because who the shooter was is answered by
 * {@link SpiderKillCredit} for both the rank of the spider and the credit of the queen.
 *
 * <p><b>The one block that has to be mirrored.</b> {@code Raider#die} pays its two raid rewards before the death event
 * of this mod is fired at all, and it reads the causing entity of the damage source, which for a spider kill is the
 * spider and not its queen; vanilla looks at the owner of a tamed wolf in that same place and would know nothing about
 * a brood. So that block is written here again, line for line and with the numbers of the game: a raider that dies in a
 * running raid makes its killer a hero of the village, and a patrol leader that dies away from any raid gives the Bad
 * Omen (two minutes, one level per captain up to the maximum of the game, refused while raids are disabled). It can
 * never be granted twice, because vanilla's own branch requires the killer to be a player or the owner of a tamed wolf.
 *
 * <p><b>What this deliberately does not do.</b> No Looting: vanilla reads the weapon of the killer for that, and a
 * hunting spider with a better drop is supposed to be the way to more loot, not the queen who happened to own the
 * spider. No change of the death message: the game keeps saying that a spider killed the creature, which is what
 * happened. No credit at all while the queen is not in this world, because a spider whose queen is not reachable has
 * nobody to hand the kill to. And a killed <b>player</b> is credited like any other victim: two queens may fight a
 * brood war, and the kill then belongs to the queen whose spider made it.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID)
public final class SpiderKillCreditEvents
{
    /** The two minutes of Bad Omen the game hands out for a patrol leader, copied from {@code Raider#die}. */
    private static final int BAD_OMEN_TICKS = 120000;

    /** The highest level Bad Omen reaches by killing patrol leaders, copied from {@code Raider#die}. */
    private static final int MAX_BAD_OMEN_LEVEL = 4;

    private SpiderKillCreditEvents()
    {
    }

    /** The last blow of a spider of the brood hands the kill to the queen that spider belongs to. */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        LivingEntity victim = event.getEntity();

        if (victim.level().isClientSide || !victim.isDeadOrDying())
        {
            return;
        }

        SpiderMinionEntity killer = SpiderKillCredit.killerOf(event.getSource());

        if (killer == null)
        {
            return;
        }

        Player queen = killer.getOwnerPlayer();

        // A spider whose queen is not in this world has nobody to hand the kill to, and a queen can never be the victim
        // of a spider of her own brood (QueenOwnerProtection), so the second half is a belt to the braces of the first.
        if (queen == null || queen == victim)
        {
            return;
        }

        // From this line on the game treats the death as her kill: everything the death does afterwards - the
        // experience orbs, the mob kill counter, the advancement, the last damage player of the loot - reads this field.
        victim.setLastHurtByPlayer(queen);

        // The statistics screen counts a kill for the causing entity of the damage source, and for a spider kill that is
        // the spider, which keeps no statistics at all. The entry "killed <creature>" is therefore written here by hand;
        // the other two counters (mobs killed and the scoreboard kill count) come from the kill credit and need no help.
        if (victim.level() instanceof ServerLevel level)
        {
            queen.killedEntity(level, victim);
        }

        mirrorRaiderRewards(victim, queen);
    }

    /**
     * The two raid rewards of {@code Raider#die}, mirrored for a kill that a spider of the queen made.
     *
     * <p>Hero of the Village first: vanilla hands it to every player who kills a raider of a running raid, and the raid
     * pays it out at its own end, so nothing else has to be done here. Bad Omen second, and only for the one case the
     * game defines: a patrol leader with its ominous banner still on its head, dying outside the reach of every raid.
     */
    private static void mirrorRaiderRewards(LivingEntity victim, Player queen)
    {
        if (!(victim instanceof Raider raider) || !(victim.level() instanceof ServerLevel level))
        {
            return;
        }

        Raid raid = raider.getCurrentRaid();

        if (raid != null)
        {
            raid.addHeroOfTheVillage(queen);

            return;
        }

        if (!raider.isPatrolLeader() || level.getRaidAt(victim.blockPosition()) != null)
        {
            return;
        }

        ItemStack banner = raider.getItemBySlot(EquipmentSlot.HEAD);

        if (banner.isEmpty() || !ItemStack.matches(banner, Raid.getLeaderBannerInstance()))
        {
            return;
        }

        // The stacking rule of the game: an Omen that is already carried is raised by one level and a first Omen starts
        // at level I, and the fifth captain can not stack beyond the maximum of the game.
        MobEffectInstance carried = queen.getEffect(MobEffects.BAD_OMEN);
        int amplifier = 1;

        if (carried != null)
        {
            amplifier += carried.getAmplifier();
            queen.removeEffectNoUpdate(MobEffects.BAD_OMEN);
        }
        else
        {
            --amplifier;
        }

        amplifier = Mth.clamp(amplifier, 0, MAX_BAD_OMEN_LEVEL);

        if (!level.getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS))
        {
            queen.addEffect(new MobEffectInstance(MobEffects.BAD_OMEN, BAD_OMEN_TICKS, amplifier, false, false, true));
        }
    }
}
