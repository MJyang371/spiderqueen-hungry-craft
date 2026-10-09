package spiderqueenhungrycraft.brood.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import spiderqueenhungrycraft.brood.SkeletonSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderAmmo;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * The ammunition switch of the skeleton spider (Special Spiders round I).
 *
 * <p>It is not an attack. It loads the other kind of shot, so the spider turns from an archer into a web spitter and
 * back: arrow, web, arrow, web, as often as the queen likes. The cooldown is therefore <b>zero</b> and is written down
 * as zero: a switch that had to be waited for would only ever be a nuisance.
 *
 * <p>The key that casts it is the ordinary ability key of the queen sceptre, so this spider needs no key of its own,
 * and the queen gets an action bar line with the ammunition that is loaded afterwards - the same word the inspect HUD
 * shows behind the name of the ability.
 */
public final class AmmoSwitchAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state, the spider holds the ammunition. */
    public static final AmmoSwitchAbility INSTANCE = new AmmoSwitchAbility();

    /** The stable id of this ability. */
    public static final String ID = "ammo_switch";

    /** A switch is free: no rest at all, so the queen can flip through the two kinds of shot as she pleases. */
    public static final int COOLDOWN_TICKS = 0;

    private AmmoSwitchAbility()
    {
    }

    @Override
    public String id()
    {
        return ID;
    }

    @Override
    public Component displayName()
    {
        return Component.translatable("ability.spiderqueen_hungrycraft.ammo_switch");
    }

    /** Nothing is needed: the spider reloads wherever it stands and whatever it is looking at. */
    @Override
    public SpiderAbilityTarget targetRequirement()
    {
        return SpiderAbilityTarget.NONE;
    }

    @Override
    public int cooldownTicks()
    {
        return COOLDOWN_TICKS;
    }

    @Override
    public boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        return spider instanceof SkeletonSpiderEntity;
    }

    /** The word the HUD shows behind "Ammo Switch": the ammunition that is loaded right now. */
    @Override
    public Component stateDetail(SpiderMinionEntity spider)
    {
        return spider instanceof SkeletonSpiderEntity skeleton
                ? Component.translatable(skeleton.getAmmo().translationKey())
                : null;
    }

    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        if (!(spider instanceof SkeletonSpiderEntity skeleton))
        {
            return false;
        }

        SpiderAmmo loaded = skeleton.cycleAmmo();

        // A quiet click of the weapon and an action bar line, so the queen sees and hears what is loaded now. The
        // message is only for her: the whole brood switching at once must not spam the chat of anybody else.
        spider.level().playSound(null, spider.getX(), spider.getY(), spider.getZ(),
                SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.NEUTRAL, 0.6F, 1.4F);

        Player owner = spider.getOwnerPlayer();

        if (owner instanceof ServerPlayer queen && queen.connection != null)
        {
            queen.displayClientMessage(Component.translatable("message.spiderqueen_hungrycraft.ammo_switched",
                    Component.translatable(loaded.translationKey())), true);
        }

        return true;
    }
}
