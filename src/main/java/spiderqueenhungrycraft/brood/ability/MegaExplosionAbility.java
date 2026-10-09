package spiderqueenhungrycraft.brood.ability;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import spiderqueenhungrycraft.brood.CreeperSpiderEntity;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;

/**
 * The mega blast of a creeper spider (Phase X): the ability that makes this species worth its cocoon.
 *
 * <p>It is a <b>manual ability of the queen</b>, exactly like the rage of a zombie spider and the ammunition switch of a
 * skeleton spider: she selects the spiders she means (a drag of the right button, J for what she can see, K for a species)
 * and presses the ability key of her sceptre (Z by default). Nothing about this ability is automatic - a creeper spider
 * that was not ordered to never blows itself up, it just fights like every other spider of the brood.
 *
 * <p><b>What the cast does</b> is light the fuse of the spider ({@link CreeperSpiderEntity#prime()}): the hiss of a
 * creeper, a swelling and blinking spider for {@link CreeperSpiderEntity#FUSE_TICKS} ticks, and then the blast itself -
 * twice the power of TNT, block breaking, and the spider consumed by its own explosion. The fuse runs on the spider, so
 * it keeps burning while the spider walks, bites or stands at its post.
 *
 * <p><b>No rest.</b> The cooldown of this ability is deliberately zero: the spider does not survive its own cast, so a
 * cooldown would be a number nobody could ever wait for. A spider whose fuse is already burning cannot be primed twice,
 * which is what {@link #canActivate} is for - the second press is simply wasted, not a second blast.
 */
public final class MegaExplosionAbility implements SpiderAbility
{
    /** The single instance of this ability: it holds no state, the spider holds its fuse. */
    public static final MegaExplosionAbility INSTANCE = new MegaExplosionAbility();

    /** The stable id of this ability. */
    public static final String ID = "mega_explosion";

    /** No rest at all: see the class documentation - this ability ends its spider. */
    public static final int COOLDOWN_TICKS = 0;

    private MegaExplosionAbility()
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
        return Component.translatable("ability.spiderqueen_hungrycraft.mega_explosion");
    }

    /** Nothing is needed: the spider blows up wherever it stands, whoever is in front of the queen. */
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

    /** A spider whose fuse is already burning has nothing left to light. */
    @Override
    public boolean canActivate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        return spider instanceof CreeperSpiderEntity creeper && !creeper.isPrimed() && !creeper.isRemoved();
    }

    /** The inspect HUD of the queen tells "COOLDOWN" from "ACTIVE" with this, and the fuse is exactly the active state. */
    @Override
    public boolean isActive(SpiderMinionEntity spider)
    {
        return spider instanceof CreeperSpiderEntity creeper && creeper.isPrimed();
    }

    /** Behind the name of the ability the HUD shows that the fuse is already burning, if it is. */
    @Nullable
    @Override
    public Component stateDetail(SpiderMinionEntity spider)
    {
        return this.isActive(spider)
                ? Component.translatable("ability.spiderqueen_hungrycraft.mega_explosion.primed")
                : null;
    }

    @Override
    public boolean activate(SpiderMinionEntity spider, SpiderAbilityContext context)
    {
        if (!(spider instanceof CreeperSpiderEntity creeper) || creeper.isPrimed())
        {
            return false;
        }

        creeper.prime();

        return creeper.isPrimed();
    }
}
