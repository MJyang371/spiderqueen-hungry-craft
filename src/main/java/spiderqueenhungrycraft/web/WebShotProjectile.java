package spiderqueenhungrycraft.web;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.attack.ShieldProjectileInteraction;
import spiderqueenhungrycraft.brood.SpiderMinionEntity;
import spiderqueenhungrycraft.brood.SpiderProvocation;
import spiderqueenhungrycraft.cocoon.CocoonCapture;
import spiderqueenhungrycraft.faction.QueenAllyUnits;
import spiderqueenhungrycraft.registry.HungryCraftEffects;
import spiderqueenhungrycraft.registry.HungryCraftEntities;
import spiderqueenhungrycraft.sound.LivingEntitySounds;

/**
 * The projectile fired by the spider queen. The ordinary shot never deals damage: hitting a living entity
 * wraps it in web and gives only cosmetic hit feedback, hitting a block leaves a temporary queen web behind,
 * and then the shot vanishes.
 *
 * <p>An elemental shot (Phase 7A) carries its {@link WebElement} on the entity data, so the server and every
 * client always agree on the trail, the impact and the block that is left behind. A poison shot deals real
 * damage and poisons, a fire shot deals real damage and burns, and neither of them ever rolls a capture. The
 * element is saved in the NBT as well, so a shot keeps its element even if the empowerment of its shooter
 * runs out while it flies.
 *
 * <p>A beast volley (Phase 8A) is a group of these projectiles fired at once. Every single one of them is a
 * real, independent shot: the same victim can be webbed, captured, damaged, poisoned and set on fire once per
 * projectile without any deduplication. The only thing the volley shares is the pack provocation, which runs
 * at most once per victim per volley.
 *
 * <p><b>Who a hit blames.</b> The projectile is one class for every shooter, but the blame is not shared: a shot of the
 * queen herself provokes her own design ({@code QueenProvocation}: the victim and its pack may fight her), while a shot
 * of a spider of the brood blames that spider, exactly like its arrow
 * ({@link spiderqueenhungrycraft.brood.SpiderProvocation}). An elemental shot needs neither branch - it deals real
 * damage through the ordinary pipeline, which names its shooter by itself.
 *
 * <p><b>What it never touches (web shot fix round).</b> The creatures of the shooter's own - its colony and its own
 * tames ({@link spiderqueenhungrycraft.faction.QueenAllyUnits}) - are not a hit at all: the shot flies straight through
 * them, keeps its speed and can still strike an enemy standing behind them. This is the same pass-through rule the
 * arrow and the trident of a spider of the brood already fly by, and it is the reason a web shot at one of her own
 * spiders can no longer turn that spider against its queen: nothing of this class ever runs for such a creature, not the
 * elemental damage, not the provocation, not the web and not the capture roll.
 */
public class WebShotProjectile extends ThrowableItemProjectile
{
    /** No gravity at all, the shot flies as straight as the player aims. */
    private static final float GRAVITY = 0.0F;

    /**
     * The hard limit of the life of every shot of this kind: sixty ticks, three seconds.
     *
     * <p>Normally a shot ends on the first thing it touches - a creature, a block, a wall. This limit is not part of
     * that behaviour, it is the safety net under it (Special Spiders round I): a shot that somehow never touches
     * anything, that gets stuck at zero speed in mid air or that survives an odd corner of the world simply stops
     * existing after three seconds instead of hanging in the sky forever. It covers the ordinary web shot and both
     * elemental ones, because all three are this one class.
     */
    public static final int MAX_LIFETIME_TICKS = 60;

    /** Very light nudge, along the flight direction. */
    private static final double HIT_KNOCKBACK = 0.25D;

    /** Cobweb debris burst on a hit. */
    private static final int BREAK_PARTICLES = 16;
    private static final double BREAK_PARTICLE_SPREAD = 0.2D;
    private static final double BREAK_PARTICLE_SPEED = 0.05D;

    /** Cobweb debris trail particles per tick, client side only. */
    private static final int TRAIL_PARTICLES = 2;
    private static final double TRAIL_PARTICLE_SPREAD = 0.1D;

    /** Impact burst of an elemental shot. */
    private static final int ELEMENT_IMPACT_PARTICLES = 10;
    private static final double ELEMENT_IMPACT_SPREAD = 0.3D;
    private static final double ELEMENT_IMPACT_SPEED = 0.02D;

    /** The element of the shot. Server authoritative, synced to the client and saved with the entity. */
    private static final EntityDataAccessor<String> DATA_ELEMENT =
            SynchedEntityData.defineId(WebShotProjectile.class, EntityDataSerializers.STRING);

    private static final String TAG_ELEMENT = "WebElement";

    /** The volley the shot belongs to, saved so a reloaded shot keeps its place in the salvo. */
    private static final String TAG_VOLLEY_ID = "WebVolleyId";

    /**
     * The volley this shot was fired in, {@link WebVolleyTracker#NO_VOLLEY} for an ordinary single shot. It is
     * server side bookkeeping for the pack provocation only and is never synced to a client.
     */
    private int volleyId = WebVolleyTracker.NO_VOLLEY;

    public WebShotProjectile(EntityType<? extends WebShotProjectile> entityType, Level level)
    {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public WebShotProjectile(Level level, LivingEntity owner)
    {
        super(HungryCraftEntities.WEB_SHOT.get(), owner, level);
        this.setNoGravity(true);
    }

    /** The element of this shot, {@link WebElement#NORMAL} for the ordinary web shot. */
    public WebElement element()
    {
        return WebElement.byId(this.entityData.get(DATA_ELEMENT));
    }

    /** Sets the element of this shot. Decided by the server, synced to every client. */
    public void setElement(WebElement element)
    {
        this.entityData.set(DATA_ELEMENT, element.id());
    }

    /** The volley this shot belongs to, {@link WebVolleyTracker#NO_VOLLEY} when it is not part of one. */
    public int volleyId()
    {
        return this.volleyId;
    }

    /** Sets the volley of this shot. Decided by the server when the shot is created. */
    public void setVolleyId(int volleyId)
    {
        this.volleyId = volleyId;
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ELEMENT, WebElement.NORMAL.id());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ELEMENT, this.element().id());
        tag.putInt(TAG_VOLLEY_ID, this.volleyId);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setElement(WebElement.byId(tag.getString(TAG_ELEMENT)));
        this.setVolleyId(tag.getInt(TAG_VOLLEY_ID));
    }

    @Override
    protected Item getDefaultItem()
    {
        // Reuses a plain vanilla item as the visual, no custom model or texture needed.
        return Items.WHITE_WOOL;
    }

    @Override
    protected float getGravity()
    {
        return GRAVITY;
    }

    @Override
    public void tick()
    {
        super.tick();

        // The safety net of the whole shot family (Special Spiders round I): three seconds and it is gone, whatever it
        // is doing. A shot that hit something never reaches this, because a hit still discards it at once.
        if (!this.level().isClientSide && this.tickCount >= MAX_LIFETIME_TICKS)
        {
            this.discard();
            return;
        }

        if (this.level().isClientSide)
        {
            spawnTrailParticles();
        }
    }

    /** The trail of the flight: cobweb debris, poison dust or flame. Client side, no particle packets. */
    private void spawnTrailParticles()
    {
        ParticleOptions particles = this.element().particle();

        for (int i = 0; i < TRAIL_PARTICLES; i++)
        {
            this.level().addParticle(particles,
                    this.getX() + (this.random.nextDouble() - 0.5D) * TRAIL_PARTICLE_SPREAD,
                    this.getY() + (this.random.nextDouble() - 0.5D) * TRAIL_PARTICLE_SPREAD,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * TRAIL_PARTICLE_SPREAD,
                    0.0D, -0.01D, 0.0D);
        }
    }

    /**
     * Who this shot may hit at all.
     *
     * <p><b>It flies through the shooter's own.</b> The layer above this class only ever asks this question before it
     * reports a hit, so returning false here means the allied creature is not a hit in any sense: no damage (not even
     * for a poison or fire shot), no web, no capture roll, no provocation, no knockback, no hit sound and no lost
     * speed. The flight simply carries on and the first real enemy in the line is hit.
     *
     * <p>The rule itself lives in {@link QueenAllyUnits}, exactly as it does for the arrow and the trident of a spider
     * of the brood, so a shot of the queen, a shot of a skeleton spider and the volley of the beast all answer it in
     * one place.
     */
    @Override
    protected boolean canHitEntity(Entity target)
    {
        return super.canHitEntity(target) && !QueenAllyUnits.isAllyOf(this.getOwner(), target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);

        if (this.level() instanceof ServerLevel)
        {
            ServerLevel level = (ServerLevel) this.level();
            Entity hit = result.getEntity();
            WebElement element = this.element();

            // A shield that is really held up in front of this shot stops the whole impact (Sweatlord round): no
            // damage, no poison, no fire, no webbed, no capture roll and no web block. The question is asked once, for
            // every shooter (the queen, a spider of the brood, a later one) and for every body that can hold a shield
            // (a player, a prepared human, a Sweatlord Steve, a modded guard) - it is the vanilla rule of
            // LivingEntity#isDamageSourceBlocked, not a weaker copy of it, so a shot in the back still lands.
            if (hit instanceof LivingEntity shielded
                    && ShieldProjectileInteraction.blocksProjectile(shielded, this, this.getOwner()))
            {
                ShieldProjectileInteraction.playBlockedFeedback(level, shielded);
                this.discard();
                return;
            }

            // Impact burst on any hit, an armour stand or a boat is still hit by web.
            spawnImpactParticles(level, this.getX(), this.getY(), this.getZ());

            if (element.isElemental())
            {
                // An elemental shot deals its real damage to every living being it may hit at all, before any web
                // rule. A creature of the shooter's own never reaches this line - canHitEntity above already flew the
                // shot through it, so its allies take neither damage nor poison nor fire. For everybody else the
                // damage runs through the ordinary damage event, which also carries the pack provocation, so nothing is
                // provoked a second time here.
                if (hit instanceof LivingEntity living)
                {
                    WebElementEffects.applyShotHit(level, this, living);
                }
            }
            else if (hit instanceof LivingEntity living)
            {
                // Shooting web at a living being is an attack, so this happens before the webbed effect,
                // before the capture roll and before the projectile is discarded. It deals no damage.
                //
                // Who is blamed depends on who fired the shot, and that is the one thing a spider and its queen do not
                // share. The shot of a spider of the brood blames that spider, exactly like an arrow of the same spider
                // does, so the prey turns on the one that spat the web at it (Special Spiders web shot fix). The shot
                // of the queen herself keeps the provocation her design asks for: the victim and its pack are marked as
                // retaliating against her, and the volley id of that call only decides whether the pack alert already
                // ran for this victim in this salvo. Everything else about this hit stays independent of every other
                // projectile.
                if (this.getOwner() instanceof SpiderMinionEntity spider)
                {
                    SpiderProvocation.provoke(spider, living);
                }
                else if (this.queenBehindTheShot() instanceof ServerPlayer queen)
                {
                    spiderqueenhungrycraft.hostility.QueenProvocation.provoke(queen, living,
                            spiderqueenhungrycraft.hostility.QueenProvocation.Cause.WEB_SHOT, this.volleyId);
                }

                // The pack rules of the human units see this hit as well (human pack aggro round). A web shot deals no
                // damage at all, so the hurt event that carries the rest of those rules never fires for it, and this is
                // the one place that has to tell the humans by hand.
                if (this.getOwner() instanceof LivingEntity shooter)
                {
                    spiderqueenhungrycraft.hostility.HumanProvocation.provoke(living, shooter,
                            spiderqueenhungrycraft.hostility.HumanProvocation.Cause.WEB_SHOT);
                }
            }

            if (WebCaptureRules.isWebAffectable(hit))
            {
                LivingEntity target = (LivingEntity) hit;
                playHitFeedback(level, target);

                // Webbed first, then the capture roll reads the new, higher Webbed level. An elemental shot
                // only stacks the web and never reaches that roll.
                applyWebbed(target);

                if (element.captureAllowed())
                {
                    CocoonCapture.tryCapture(level, target, QueenWebRules.currentWebbedAmplifier(target));
                }
            }
            else
            {
                // Armour stands, boats, minecarts, items and projectiles only get the thud, no flash,
                // no knockback, no webbed, no capture.
                playWebImpactSound(level, this.getX(), this.getY(), this.getZ());
            }
        }

        // Never pierces and never damages.
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result)
    {
        super.onHitBlock(result);

        if (this.level() instanceof ServerLevel)
        {
            ServerLevel level = (ServerLevel) this.level();

            // Impact burst at the real impact point, before the web block is placed.
            Vec3 impact = result.getLocation();
            spawnImpactParticles(level, impact.x, impact.y, impact.z);
            playWebImpactSound(level, impact.x, impact.y, impact.z);

            BlockPos target = result.getBlockPos().relative(result.getDirection());
            BlockState state = level.getBlockState(target);
            if (state.canBeReplaced())
            {
                level.setBlockAndUpdate(target, this.element().webBlock().defaultBlockState());
            }
            // Otherwise the shot simply disappears, the block in the way is never broken.
        }

        this.discard();
    }

    /**
     * Vanilla hurt animation for the target without any damage, plus a tiny nudge along the flight
     * direction and a clear hit sound. No damage source, no invulnerability, no armor, no statistics
     * and no other onHurt handlers are involved.
     */
    /**
     * @return the queen this shot belongs to: the shooter herself when she fired it, or the queen of a spider of the
     *         brood that fired it (a skeleton spider in web mode, Special Spiders round I). Null when neither of them
     *         is a queen player.
     *
     * <p>It exists so that the one web projectile of the mod keeps one single set of rules for every shooter: the
     * queen who spits web and the spider that spits web for her go through exactly the same hit handling.
     */
    @Nullable
    private Player queenBehindTheShot()
    {
        if (this.getOwner() instanceof Player queen)
        {
            return queen;
        }

        if (this.getOwner() instanceof SpiderMinionEntity spider)
        {
            return spider.getOwnerPlayer();
        }

        return null;
    }

    private void playHitFeedback(ServerLevel level, LivingEntity target)
    {
        level.getChunkSource().broadcastAndSend(target, new ClientboundHurtAnimationPacket(target));
        playCreatureHitSound(level, target);

        double resistance = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance <= 0.0D)
        {
            return;
        }

        Vec3 direction = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
        if (direction.lengthSqr() < 1.0E-6D)
        {
            return;
        }

        Vec3 push = direction.normalize().scale(HIT_KNOCKBACK * resistance);
        target.push(push.x, 0.0D, push.z);
    }

    /**
     * The creature's own hurt sound, so a hit sounds like the prey crying out. The damage source is
     * only used as an argument for the accessor, no damage is ever dealt or triggered. Falls back to
     * the generic hurt sound when a creature has none of its own.
     */
    private static void playCreatureHitSound(ServerLevel level, LivingEntity target)
    {
        SoundEvent sound = LivingEntitySounds.hurtSound(target, level.damageSources().generic());
        if (sound == null)
        {
            sound = SoundEvents.GENERIC_HURT;
        }

        level.playSound(null, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                sound, target.getSoundSource(), 1.0F, 1.0F);
    }

    /** A soft web thud for everything that is not a creature, for example a block or an armour stand. */
    private static void playWebImpactSound(ServerLevel level, double x, double y, double z)
    {
        level.playSound(null, x, y, z, SoundEvents.WOOL_HIT, SoundSource.NEUTRAL, 0.6F, 1.2F);
    }

    /**
     * The impact burst of the shot: the vanilla cobweb debris for the ordinary shot, the particle of the
     * element for a poison or fire shot.
     */
    private void spawnImpactParticles(ServerLevel level, double x, double y, double z)
    {
        if (this.element() == WebElement.NORMAL)
        {
            spawnBreakParticles(level, x, y, z);
            return;
        }

        level.sendParticles(this.element().particle(), x, y, z, ELEMENT_IMPACT_PARTICLES, ELEMENT_IMPACT_SPREAD,
                ELEMENT_IMPACT_SPREAD, ELEMENT_IMPACT_SPREAD, ELEMENT_IMPACT_SPEED);
    }

    /** Reuses the vanilla cobweb debris particles, no new textures needed. */
    private static void spawnBreakParticles(ServerLevel level, double x, double y, double z)
    {
        level.sendParticles(cobwebParticles(), x, y, z, BREAK_PARTICLES, BREAK_PARTICLE_SPREAD, BREAK_PARTICLE_SPREAD,
                BREAK_PARTICLE_SPREAD, BREAK_PARTICLE_SPEED);
    }

    /** The single particle type of the web features: a vanilla cobweb being broken. */
    public static ParticleOptions cobwebParticles()
    {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
    }

    /** Adds one Webbed level, up to Webbed IV, and refreshes its duration. */
    public static void applyWebbed(LivingEntity target)
    {
        int current = QueenWebRules.currentWebbedAmplifier(target);
        int amplifier = QueenWebRules.nextWebbedAmplifier(current);
        int duration = QueenWebRules.webbedDurationTicks(current);

        // ambient = false, particles = false, HUD icon = false
        target.addEffect(new MobEffectInstance(HungryCraftEffects.WEBBED.get(), duration, amplifier, false, false, false));
    }
}
