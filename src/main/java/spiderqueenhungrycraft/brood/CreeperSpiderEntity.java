package spiderqueenhungrycraft.brood;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import spiderqueenhungrycraft.brood.ability.MegaExplosionAbility;
import spiderqueenhungrycraft.brood.ability.SpiderAbility;

/**
 * The creeper spider (Phase X): a spider of the brood that carries the blast of a creeper, and nothing else of it.
 *
 * <p>It hatches from a cocoon whose prey was exactly {@code minecraft:creeper} (see {@code CocoonEntity#hatchling}),
 * and from that moment on it is a spider of the queen like every other species of this mod: same owner, same sceptre
 * commands, same follow / hold / patrol, same selection, same faction and same carrier. It is deliberately <b>not</b> a
 * creeper and not a {@code Creeper} subclass: no creeper AI, no creeper hitbox, no creeper sound set and no lightning
 * form - it has the body, the walk and the wall climbing of a spider, and one thing of its own, the blast.
 *
 * <p><b>It fights like every other spider of the brood</b> - the ordinary leap, the ordinary bite, thirty five health,
 * six damage and a walk 30 percent faster than the rest of the brood - and it carries two blasts on top of that:
 * <ul>
 * <li>the <b>death blast</b>: whatever kills this spider is followed by a small explosion of the power of a block of TNT
 * that hurts and knocks back but never breaks a block,</li>
 * <li>the <b>mega blast</b>: a <b>manual ability</b> of the queen, exactly like the rage of a zombie spider or the
 * ammunition switch of a skeleton spider ({@link MegaExplosionAbility}): she selects the spiders she means and presses
 * the ability key of her sceptre. Those spiders hiss, swell and blink for {@link #FUSE_TICKS} ticks and then blow
 * themselves up with twice the power of TNT and a real hole in the ground. The spider is consumed by its own blast - no
 * corpse, no loot and no second blast. It never fires on its own: a creeper spider that was not ordered to does not
 * explode.</li>
 * </ul>
 *
 * <p>Neither blast ever hurts the side it fights for: the queen of this spider and every creature of her colony are
 * removed from the blast before any damage is dealt (see {@link CreeperSpiderExplosionUtil#isFriendly}).
 *
 * <p><b>Speed.</b> It walks 30 percent faster than the other spiders of the brood, which is much of the point of the
 * species: a player should feel that this one is coming for them.
 */
public class CreeperSpiderEntity extends SpiderMinionEntity
{
    /** Thirty five health: more than an ordinary spider of the brood, less than a zombie spider. */
    public static final double MAX_HEALTH = 35.0D;

    /** Six damage: the bite of a creeper spider is the heaviest of the brood. */
    public static final double ATTACK_DAMAGE = 6.0D;

    /**
     * The walking speed of this species: 1.3 times the speed of every other spider of the brood, which is 0.39 against
     * the 0.3 of a vanilla spider. It is written as a multiplication on purpose, so a change of the speed of the brood
     * can never leave this one behind.
     */
    public static final double MOVE_SPEED = SpiderMinionEntity.MOVEMENT_SPEED * 1.3D;

    /** The 1.4 x 0.9 hitbox of a vanilla spider: this species is not bigger, it is louder. */
    public static final float COLLISION_WIDTH = 1.4F;
    public static final float COLLISION_HEIGHT = 0.9F;

    /**
     * The fuse of the mega blast: thirty ticks, one and a half seconds - the fuse of a creeper of the game, and short
     * enough that a queen can use it in the middle of a fight and still hit something with it.
     */
    public static final int FUSE_TICKS = 30;

    /**
     * The fuse of this spider in ticks, 0 while it is not primed. It is synced, because the client draws the swelling
     * and the blinking of the fuse from it - the one piece of state the two sides share about the blast.
     */
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData
            .defineId(CreeperSpiderEntity.class, EntityDataSerializers.INT);

    /** True once a blast of this spider has been fired, so no second one can ever follow. */
    private boolean blasted;

    public CreeperSpiderEntity(EntityType<? extends CreeperSpiderEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /**
     * The attributes of this species.
     *
     * <p>The base of every spider of the brood carries the health, the damage, the armor and the knockback resistance of
     * this mod, and the last line overwrites the walking speed that base set: an attribute that is added to a builder a
     * second time is the very same instance with a new base value, so {@link #MOVE_SPEED} really is the speed of this
     * spider and not a second entry next to the old one.
     */
    public static AttributeSupplier.Builder createAttributes()
    {
        return createMinionAttributes(MAX_HEALTH, ATTACK_DAMAGE).add(Attributes.MOVEMENT_SPEED, MOVE_SPEED);
    }

    /**
     * The goals of this spider are the goals of every spider of the brood: it is an ordinary melee spider first (leap,
     * bite, follow, hold, patrol, the commands of the sceptre).
     *
     * <p>Nothing about the mega blast is a goal any more: the blast is a manual ability of the queen
     * ({@link MegaExplosionAbility}), so this species has no AI of its own that could spend its life on its own.
     */
    @Override
    protected void registerGoals()
    {
        super.registerGoals();
    }

    /** The ability of this species: the mega blast, and it is the queen who presses the key for it. */
    @Override
    public SpiderAbility getAbility()
    {
        return MegaExplosionAbility.INSTANCE;
    }

    /**
     * One tick of this spider: the ordinary tick of a spider of the brood, plus the burning fuse.
     *
     * <p>The fuse is counted here and not in a goal, because it is not an AI decision any more: once the queen lit it,
     * it burns down whatever the spider is doing - walking, biting, standing at its post - and the spider blows up at
     * zero.
     */
    @Override
    public void tick()
    {
        super.tick();

        if (!this.level().isClientSide && this.isPrimed())
        {
            this.tickFuse();
        }
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.getEntityData().define(DATA_FUSE, 0);
    }

    // ------------------------------------------------------------------ the fuse

    /** @return the ticks left of the fuse, 0 while this spider is not primed. */
    public int getFuse()
    {
        return this.getEntityData().get(DATA_FUSE);
    }

    /** @return true while the fuse of the mega blast is burning. */
    public boolean isPrimed()
    {
        return this.getFuse() > 0;
    }

    /** Sets the fuse. Only the fuse of this spider writes it, and the countdown never counts upwards. */
    private void setFuse(int ticks)
    {
        this.getEntityData().set(DATA_FUSE, Math.max(0, ticks));
    }

    /**
     * Lights the fuse of the mega blast: the hiss of a creeper of the game, the countdown of {@link #FUSE_TICKS} ticks
     * and the puffs of smoke that grow with it.
     *
     * <p>A spider that is already primed is not primed again, and a spider that already blasted is not primed at all:
     * the fuse of a blast is a countdown, not a switch.
     */
    public void prime()
    {
        if (this.level().isClientSide || this.isPrimed() || this.blasted)
        {
            return;
        }

        this.setFuse(FUSE_TICKS);

        // The very sound a creeper makes, taken from the game itself: no audio file of this mod is involved.
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CREEPER_PRIMED,
                SoundSource.HOSTILE, 1.0F, 0.5F);
    }

    /**
     * One tick of the burning fuse, called by the tick of this spider while it is primed.
     *
     * <p>It counts down, puffs smoke that grows with the progress (a player has to be able to see what is coming), and
     * fires the mega blast when the countdown reaches zero.
     *
     * @return true when the blast went off with this tick
     */
    public boolean tickFuse()
    {
        int fuse = this.getFuse();

        if (fuse <= 0)
        {
            return false;
        }

        fuse--;
        this.setFuse(fuse);

        // Smoke, denser towards the end: the same trick the swelling of a creeper plays on the eye, drawn with the
        // particles of the game and with no animation of its own.
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel)
        {
            int puffs = 1 + (FUSE_TICKS - fuse) / 12;

            for (int index = 0; index < puffs; index++)
            {
                serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                        this.getX() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                        this.getY() + (double) this.getBbHeight() * 0.5D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * (double) this.getBbWidth(),
                        1, 0.0D, 0.05D, 0.0D, 0.01D);
            }
        }

        if (fuse <= 0)
        {
            this.detonate();

            return true;
        }

        return false;
    }

    /**
     * @param partialTick the partial tick of the frame, so the swelling is smooth and not a step per tick
     * @return how far the fuse has burned, from 0 (not primed) to 1 (about to go off), for the renderer of this spider
     */
    public float fuseProgress(float partialTick)
    {
        if (!this.isPrimed())
        {
            return 0.0F;
        }

        return Mth.clamp(1.0F - ((float) this.getFuse() - partialTick) / (float) FUSE_TICKS, 0.0F, 1.0F);
    }

    /**
     * The mega blast: a real explosion of twice the power of a block of TNT, one that breaks blocks and that spares the
     * queen of this spider and her brood, and the end of this spider.
     *
     * <p>It is a one shot thing and nothing else: the spider is consumed by its own blast ({@link #discard()}), so no
     * corpse, no loot, no experience and no cooldown state is left behind, and a second call does nothing at all.
     */
    public void detonate()
    {
        if (this.level().isClientSide || this.blasted)
        {
            return;
        }

        this.blasted = true;
        CreeperSpiderExplosionUtil.detonateMega(this);

        // Consumed, not killed: a killed spider would die again in the ordinary way and leave a corpse behind the smoke.
        this.discard();
    }

    /**
     * The death of a creeper spider is its small blast.
     *
     * <p>Whatever killed it - a sword, an arrow, a fall - the spider leaves the same farewell: an explosion of the power
     * of a block of TNT that hurts and knocks back everything hostile around it and that breaks nothing. A spider whose
     * fuse was already burning never reaches its own mega blast this way, which is the one way a player can stop it:
     * kill it before it counts to thirty.
     *
     * <p>The blast is fired here, before the death of the game runs, so the loot and the experience of this spider are
     * untouched by it - and a spider that already blasted itself away never reaches this method at all, because
     * {@link #detonate()} removes it instead of killing it.
     */
    @Override
    public void die(DamageSource source)
    {
        // A burning fuse dies with its spider: the smoke and the swelling belong to a spider that is still counting,
        // and a body on the ground has nothing left to count. The blast itself cannot fire twice anyway.
        this.setFuse(0);

        if (!this.level().isClientSide && !this.blasted)
        {
            this.blasted = true;
            CreeperSpiderExplosionUtil.detonateSmall(this);
        }

        super.die(source);
    }

    /**
     * The fuse travels with the save file, so a spider that was primed when the world was closed is still primed when it
     * is opened again instead of quietly forgetting its own countdown.
     */
    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt("CreeperSpiderFuse", this.getFuse());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setFuse(tag.getInt("CreeperSpiderFuse"));
    }
}
