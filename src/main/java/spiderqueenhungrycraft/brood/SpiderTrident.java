package spiderqueenhungrycraft.brood;

import javax.annotation.Nullable;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.faction.QueenColonyRelations;

/**
 * The trident of a drowned spider (Drowned Spider round): the combat projectile of the brood, built on the trident of
 * the game so its flight, its spin, its model and its renderer are the real thing - and nothing else about it is.
 *
 * <p>The trident of a player is an item: it can be picked up again, it wears out, it comes back with loyalty and it is
 * thrown out of an inventory. <b>None of that is true here.</b> This one is fired by a spider, out of nothing, it never
 * becomes an item, it can never be picked up, it has no loyalty and no durability, and it disappears when it lands.
 * What it keeps is the part that makes a trident a trident: the look, the sound, the throw and the hit.
 *
 * <p><b>It flies through the allies of its queen.</b> Not "hits them for nothing" - it never registers them as a hit at
 * all, so it keeps its speed, keeps its flight and can still strike the enemy behind them. The rule is asked of
 * {@link QueenColonyRelations}, the very same colony answer the faction AI of this mod uses, so the queen who owns the
 * spider and every spider of her brood are transparent for it without a single class name in here.
 *
 * <p>Two modes share this class: the ordinary shot of a drowned spider, six points of damage, and the giant trident of
 * its ability, twenty points and a bolt of lightning where it lands. They differ in one enum and in the scale its
 * renderer draws them at - not in behaviour, so there is one projectile and not two.
 */
public class SpiderTrident extends ThrownTrident
{
    /** What this trident is: the ordinary shot of the spider, or the giant one of its ability. */
    public enum Mode
    {
        /** The everyday shot: six points of damage, the size of a trident. */
        NORMAL(6.0D, 1.0D, false),

        /** The trident of the ability: twenty points of damage, drawn three times as big, and a bolt where it lands. */
        GIANT(20.0D, 3.0D, true);

        private final double damage;
        private final double scale;
        private final boolean lightning;

        Mode(double damage, double scale, boolean lightning)
        {
            this.damage = damage;
            this.scale = scale;
            this.lightning = lightning;
        }

        /** @return the damage this trident deals to whatever it catches. */
        public double damage()
        {
            return this.damage;
        }

        /** @return how large its renderer draws it. */
        public double scale()
        {
            return this.scale;
        }

        /** @return true when this trident calls a bolt of lightning down where it lands. */
        public boolean lightning()
        {
            return this.lightning;
        }
    }

    private final Mode mode;

    public SpiderTrident(EntityType<? extends SpiderTrident> entityType, Level level, Mode mode)
    {
        super(entityType, level);
        this.mode = mode;
    }

    /** Fired by that creature, in the direction it is aimed at, by the caller. */
    public SpiderTrident(Level level, LivingEntity shooter, Mode mode)
    {
        // A plain trident of the game as the "item" behind the flight: it carries no enchantment, so there is no loyalty
        // to bring this projectile back and no durability that could ever be spent.
        super(level, shooter, new ItemStack(Items.TRIDENT));
        this.mode = mode;
    }

    /** @return what this trident is: ordinary shot or the giant one of the ability. */
    public Mode mode()
    {
        return this.mode;
    }

    /**
     * The ally rule of the brood: a creature of the same colony is not a hit for this trident, it is not there at all.
     *
     * <p>The search of the game asks exactly this before it accepts a victim, so returning false means the ally is
     * skipped and the search carries on to whatever stands behind it - no damage, no knockback, no stuck trident and no
     * lost speed.
     */
    @Override
    protected boolean canHitEntity(Entity target)
    {
        return super.canHitEntity(target) && !isQueenAlly(target);
    }

    /** @return true when that creature stands on the side of the queen that owns this trident. */
    private boolean isQueenAlly(@Nullable Entity target)
    {
        return QueenColonyRelations.isQueenSideFriendly(this.getOwner(), target);
    }

    /** It is never an item: nothing about it may ever end up in an inventory. */
    @Override
    public void playerTouch(Player player)
    {
        // A trident of the brood is a weapon of the brood, not loot.
    }

    /**
     * The damage of a trident of the game grows and shrinks with the speed it still carries. This one is a weapon of the
     * brood and deals the damage of its mode, whatever is left of its speed, so the base damage is levelled against the
     * impact and then read by the ordinary hit handling of the game.
     */
    @Override
    public double getBaseDamage()
    {
        double speed = this.getDeltaMovement().length();

        return speed < 1.0E-4D ? this.mode.damage() : this.mode.damage() / speed;
    }

    /** The hit on a creature: the ordinary hit of the game, and the bolt of the ability where it landed. */
    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        Vec3 where = result.getLocation();
        super.onHitEntity(result);
        strike(where);
    }

    /** The hit on a block: the ordinary hit of the game, and the bolt of the ability where it landed. */
    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);
        strike(result.getLocation());
    }

    /**
     * The bolt of the giant trident.
     *
     * <p>It is deliberately a <b>visual only</b> bolt of the game: the damage of this ability is the twenty points the
     * trident itself deals, and the lightning is the thunderclap that goes with it. A real bolt would damage everything
     * within three blocks of the impact - the queen standing behind her own spider included - and no rule of this mod
     * could take that back afterwards. A visual bolt keeps the flash and the sound, sets no fire and hurts nobody, so
     * the allies of the brood can never be killed by the ability of one of their own.
     */
    private void strike(Vec3 where)
    {
        if (!this.mode.lightning() || !(this.level() instanceof net.minecraft.server.level.ServerLevel level))
        {
            return;
        }

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);

        if (bolt == null)
        {
            return;
        }

        bolt.moveTo(where.x, where.y, where.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
        level.playSound(null, where.x, where.y, where.z, SoundEvents.TRIDENT_THUNDER, SoundSource.WEATHER, 4.0F, 1.0F);
    }
}
