package spiderqueenhungrycraft.stomach;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The living core of one Stomach Cell (Phase 8E1-GATE2A).
 *
 * <p>A core is a real {@link LivingEntity} that stands for the stomach wall itself: it has real health, it can be
 * attacked, it can be an attacker, and it is the thing every mob inside the cavity can hate. It is deliberately
 * nothing else - it never moves, never collides, never pushes, is never rendered, never drops loot, is not an
 * occupant of its own cell, is never digested and never counts as food.
 *
 * <p>Every core belongs to exactly one cell: it knows its owner and its cell index, and it sits in the middle of
 * that cell. Because it is registered as a runtime only entity, it is never written to the save file, so a restart
 * can neither duplicate a core nor keep a cell loaded through one.
 */
public class StomachCoreEntity extends LivingEntity
{
    /** NBT keys of the runtime identity, used when a core is saved by anything else. */
    private static final String KEY_OWNER = "StomachOwner";
    private static final String KEY_CELL_INDEX = "StomachCellIndex";

    /**
     * Damage that may never kill a core: the environment is not an attacker. Everything that has an attacker
     * entity - a mob, a player, an arrow, a thrown potion, a player lit explosion - still hurts the core, because
     * the stomach has to be fightable.
     */
    private static final Set<ResourceKey<DamageType>> ENVIRONMENT_DAMAGE = Set.of(DamageTypes.IN_FIRE,
            DamageTypes.ON_FIRE, DamageTypes.LAVA, DamageTypes.HOT_FLOOR, DamageTypes.IN_WALL, DamageTypes.CRAMMING,
            DamageTypes.DROWN, DamageTypes.STARVE, DamageTypes.CACTUS, DamageTypes.FALL, DamageTypes.FLY_INTO_WALL,
            DamageTypes.FREEZE, DamageTypes.FELL_OUT_OF_WORLD);

    /** The owner of the cell this core is; null while a core has no identity yet. */
    @Nullable
    private UUID owner;

    /** The allocation index of the cell this core is; -1 while a core has no identity yet. */
    private int cellIndex = -1;

    private int regenTicker;
    private int armorTicker;
    private int pulseTicker;
    private int tauntTicker;

    public StomachCoreEntity(EntityType<? extends StomachCoreEntity> entityType, Level level)
    {
        super(entityType, level);
        this.setNoGravity(true);
        this.setHealth(StomachConstants.CORE_MAX_HEALTH);
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** The attributes of a core: a wall that is full of health and impossible to knock around. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, StomachConstants.CORE_MAX_HEALTH)
                .add(Attributes.KNOCKBACK_RESISTANCE, StomachConstants.CORE_KNOCKBACK_RESISTANCE)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    /** @return a brand new core of that cell, already anchored and at full health. */
    public static StomachCoreEntity forCell(ServerLevel level, StomachCell cell)
    {
        StomachCoreEntity core = new StomachCoreEntity(HungryCraftEntities.STOMACH_CORE.get(), level);
        core.placeAtCell(cell);

        return core;
    }

    /**
     * The maximum health of the wall is the constant and not the platform attribute: the health attribute of the
     * platform clamps every value to 1024, and a stomach wall is deliberately far above that. Every question about
     * the maximum health goes through this method - including the clamp of the vanilla health setter - so the wall
     * really lives with its full 9999.
     */
    @Override
    public double getAttributeValue(Attribute attribute)
    {
        return attribute == Attributes.MAX_HEALTH ? StomachConstants.CORE_MAX_HEALTH
                : super.getAttributeValue(attribute);
    }

    // ------------------------------------------------------------------ identity

    /** @return true when this core is the core of exactly that cell. */
    public boolean belongsTo(StomachCell cell)
    {
        return owner != null && owner.equals(cell.owner()) && cellIndex == cell.index();
    }

    @Nullable
    public UUID ownerUUID()
    {
        return owner;
    }

    public int cellIndex()
    {
        return cellIndex;
    }

    /** @return the cell of this core, when its identity is known. */
    public Optional<StomachCell> cell()
    {
        return owner == null || cellIndex < 0 ? Optional.empty()
                : Optional.of(new StomachCell(owner, cellIndex, StomachCellGeometry.centerFor(cellIndex)));
    }

    /** Gives the core its identity and puts it back into the middle of its cell, fully armored at once. */
    public void placeAtCell(StomachCell cell)
    {
        owner = cell.owner();
        cellIndex = cell.index();
        anchor();
        StomachCoreArmorPolicy.refresh(this);
    }

    /** Puts the core back to its fixed spot whenever anything moved it even a little. */
    private void anchor()
    {
        if (cellIndex < 0)
        {
            return;
        }

        BlockPos center = StomachCellGeometry.centerFor(cellIndex);
        double x = center.getX() + 0.5D;
        double z = center.getZ() + 0.5D;
        double y = center.getY() - StomachCellGeometry.INNER_RADIUS;

        if (this.getX() != x || this.getY() != y || this.getZ() != z)
        {
            this.setPos(x, y, z);
        }

        this.setDeltaMovement(Vec3.ZERO);
    }

    // ------------------------------------------------------------------ physics

    /**
     * The attack surface of a core: one cube that covers the cavity of its cell, so every mob inside the cell is
     * within reach of the wall no matter where it stands.
     */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return EntityDimensions.fixed(StomachConstants.CORE_WIDTH, StomachConstants.CORE_HEIGHT);
    }

    /** A core is not pushable, so no entity collision can ever move it. */
    @Override
    public boolean isPushable()
    {
        return false;
    }

    /** Nothing collides with a core: players, mobs and items walk straight through the wall. */
    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    /**
     * A core is not pickable in any sense: the crosshair of a client never selects it, so it can never steal the
     * attack from a zombie or another player standing in the same cell. An arrow therefore passes through the core
     * itself and would fly on until it hits the wall - which the projectile routing of the stomach reads as a hit
     * on the wall of the cell - while a real melee attack reaches it through the wall attack routing.
     */
    @Override
    public boolean isPickable()
    {
        return false;
    }

    /** Neither water nor any other fluid can drag a core along. */
    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    /** No other entity can push the core. */
    @Override
    public void push(Entity entity)
    {
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** No knockback, no explosion impulse, no water current: the core stays exactly where it is. */
    @Override
    public void push(double x, double y, double z)
    {
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** A core never travels anywhere by itself. */
    @Override
    public void travel(Vec3 movement)
    {
        this.setDeltaMovement(Vec3.ZERO);
    }

    /** No living step logic at all: the wall has no AI, no swimming and no friction to calculate. */
    @Override
    public void aiStep()
    {
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * The wall is one stomach, but it is not one wound: a hit that just landed must never swallow the hits of the
     * other creatures in the same cell. Every real hit therefore reaches the wall on its own, while the attack
     * cooldown of whoever attacks stays exactly what vanilla gives it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        this.invulnerableTime = 0;

        return super.hurt(source, amount);
    }

    /**
     * The environment cannot kill a core. Real combat always can: only damage without any attacker entity is
     * refused, and only for the damage types of the world around the cell.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        if (super.isInvulnerableTo(source))
        {
            return true;
        }

        if (source.getEntity() != null || source.getDirectEntity() != null)
        {
            return false;
        }

        return ENVIRONMENT_DAMAGE.stream().anyMatch(source::is);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void tick()
    {
        this.setDeltaMovement(Vec3.ZERO);
        super.tick();
        this.setDeltaMovement(Vec3.ZERO);

        if (this.level().isClientSide || this.isRemoved())
        {
            return;
        }

        anchor();

        if (cellIndex < 0)
        {
            // A core that never got an identity - for example a summoned one - has no business in a stomach.
            this.discard();
            return;
        }

        serverTick((ServerLevel) this.level());
    }

    /** Everything a core does: it keeps its armor, its health, its digestion and the hatred of its cell. */
    private void serverTick(ServerLevel level)
    {
        Optional<StomachCell> cell = cell();

        if (cell.isEmpty())
        {
            return;
        }

        if (++armorTicker >= StomachConstants.CORE_REGEN_INTERVAL_TICKS)
        {
            armorTicker = 0;
            StomachCoreArmorPolicy.refresh(this);
        }

        if (++regenTicker >= StomachConstants.CORE_REGEN_INTERVAL_TICKS)
        {
            regenTicker = 0;
            this.setHealth(this.getMaxHealth());
        }

        if (++pulseTicker >= StomachConstants.DIGESTIVE_PULSE_INTERVAL_TICKS)
        {
            pulseTicker = 0;
            digestivePulse(level, cell.get());
        }

        if (++tauntTicker >= StomachConstants.CORE_TAUNT_INTERVAL_TICKS)
        {
            tauntTicker = 0;
            taunt(level, cell.get());
        }
    }

    // ------------------------------------------------------------------ digestion

    /** The digestive pulse: the wall hurts everything that really is inside its cell. */
    private void digestivePulse(ServerLevel level, StomachCell cell)
    {
        DamageSource source = level.damageSources().mobAttack(this);
        int digested = 0;

        for (LivingEntity target : StomachCellManager.occupants(level, cell))
        {
            if (target == this || !target.isAlive() || target.isRemoved() || target.isSpectator())
            {
                continue;
            }

            // The rest is vanilla: armor mitigates, a creative player stays invulnerable, nothing is forced.
            boolean hurt = target.hurt(source, StomachConstants.DIGESTIVE_PULSE_DAMAGE);

            // Only a hit that really went through is answered: a creative player, an invulnerable one or any
            // damage that was cancelled gets neither acid nor sound.
            if (hurt)
            {
                StomachDigestionFx.feedback(level, target);
                digested++;
            }
        }
    }

    // ------------------------------------------------------------------ taunt

    /** Second line of defence of the hatred: an idle fighting mob of the cell is pointed at the wall. */
    private void taunt(ServerLevel level, StomachCell cell)
    {
        for (LivingEntity entity : StomachCellManager.occupants(level, cell))
        {
            if (!(entity instanceof Mob mob) || entity == this || !mob.isAlive() || mob.isRemoved() || mob.isNoAi()
                    || !mob.canAttack(this) || !bringsOwnAttack(mob))
            {
                continue;
            }

            LivingEntity current = mob.getTarget();

            // A living target that is not this core is never taken away from the mob.
            if (current != null && current != this && current.isAlive())
            {
                continue;
            }

            mob.setTarget(this);
        }
    }

    /** @return true when that mob attacks with its own melee: no AI is grafted onto cows and villagers. */
    private static boolean bringsOwnAttack(Mob mob)
    {
        return mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0D;
    }

    // ------------------------------------------------------------------ feedback

    /** The wall never grunts: the fleshy hit sound of a hit core is played by StomachCoreEvents. */
    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound()
    {
        return null;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        if (owner != null)
        {
            tag.putUUID(KEY_OWNER, owner);
        }

        tag.putInt(KEY_CELL_INDEX, cellIndex);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        owner = tag.hasUUID(KEY_OWNER) ? tag.getUUID(KEY_OWNER) : null;
        cellIndex = tag.contains(KEY_CELL_INDEX) ? tag.getInt(KEY_CELL_INDEX) : -1;
        anchor();
        StomachCoreArmorPolicy.refresh(this);
    }

    // ------------------------------------------------------------------ the living contract

    @Override
    public Iterable<ItemStack> getArmorSlots()
    {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack)
    {
        // A core holds nothing, so there is nothing to set.
    }

    @Override
    public HumanoidArm getMainArm()
    {
        return HumanoidArm.RIGHT;
    }
}


