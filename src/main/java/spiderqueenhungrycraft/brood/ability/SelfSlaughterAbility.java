package spiderqueenhungrycraft.brood.ability;

import net.minecraft.network.chat.Component;
import spiderqueenhungrycraft.brood.InfestedSteveEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * 「自刎归天」/ LEEROY JENKINS!! - the ability of the infested Steve (LEEROY JENKINS!! round).
 *
 * <p>It is a <b>manual ability of the queen</b>, exactly like the mega blast of a creeper spider, the rage of a zombie
 * spider and the ammunition switch of a skeleton spider: she selects the bodies she means (a drag of the right button, J
 * for what she can see, K for a species) and presses the ability key of her sceptre (Z by default).
 *
 * <p><b>What the cast does</b> is literally what its name says: the body cuts its own throat where it stands. It dies of
 * {@code spiderqueenhungrycraft:self_slaughter}, which no armour, no shield and no invulnerability frame can stop, and
 * its death is the death this body already had before this round existed:
 *
 * <ul>
 * <li>every piece of gear it wears or carries is dropped where it fell - the armour, the shield of the offhand, the blade
 * and the bow of its belt, and the golden apples that are still in its pouch, all of them as ordinary items on the
 * ground, so nothing a digested human ever carried is lost with it,</li>
 * <li>and the spiders that drove the body walk out of it: {@link InfestedSteveEntity#BROOD_ON_DEATH} imp spiders appear
 * in a small ring around the corpse, each of them taking over the owner, the rank, the order and the victim of the body
 * (see {@link InfestedSteveEntity#spawnBroodOfTheCorpse()}).</li>
 * </ul>
 *
 * <p><b>No rest.</b> The cooldown of this ability is deliberately zero, like the fuse of the creeper spider: the body
 * does not survive its own cast, so a cooldown would be a number nobody could ever wait for. A body that is already dying
 * cannot be ordered to die again, which is what {@link #canActivate} is for.
 *
 * <p>Nothing about this is automatic. A body that was never ordered to tends to its fight, its orders and its medicine
 * like every other soldier of the queen - it never cuts its own throat on its own.
 */
public final class SelfSlaughterAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state at all. */
    public static final SelfSlaughterAbility INSTANCE = new SelfSlaughterAbility();

    /** The stable id of this ability, used by the network and by the inspect HUD. */
    public static final String ID = "self_slaughter";

    /** No rest at all: see the class documentation - this ability ends its body. */
    public static final int COOLDOWN_TICKS = 0;

    private SelfSlaughterAbility()
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
        return Component.translatable("ability.spiderqueen_hungrycraft.self_slaughter");
    }

    /** Nothing is needed: the body dies wherever it stands, whoever is in front of the queen. */
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

    /** Only a body that is still standing can be ordered to fall on its own blade. */
    @Override
    public boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        return spider instanceof InfestedSteveEntity body && !body.isRemoved() && !body.isDeadOrDying();
    }

    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        if (!(spider instanceof InfestedSteveEntity body))
        {
            return false;
        }

        return body.slaughterItself();
    }
}
