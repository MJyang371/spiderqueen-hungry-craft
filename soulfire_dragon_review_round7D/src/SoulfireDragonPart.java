package spiderqueenhungrycraft.soulfire;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/**
 * One logical bone-dragon hitbox. The owner handles all AI, flying, death and NBT.
 * It is NOT an independent world entity and must not be spawned with addFreshEntity.
 */
public final class SoulfireDragonPart extends PartEntity<SoulfireDragonEntity>
{
    private final String partName;
    private final float baseWidth;
    private final float baseHeight;
    private EntityDimensions size;

    public SoulfireDragonPart(SoulfireDragonEntity dragon, String partName, float width, float height)
    {
        super(dragon);
        this.partName = partName;
        this.baseWidth = width;
        this.baseHeight = height;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    public String getPartName()
    {
        return this.partName;
    }

    public void resize(float stageScale)
    {
        this.size = EntityDimensions.scalable(this.baseWidth * stageScale, this.baseHeight * stageScale);
        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return this.size == null ? EntityDimensions.scalable(0.5F, 0.5F) : this.size;
    }

    @Override
    public boolean isPickable()
    {
        return true;
    }

    @Override
    public boolean is(Entity other)
    {
        return this == other || this.getParent() == other;
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        // One shared health pool. Hits against parts cannot spawn duplicate corpse records.
        return this.getParent().hurt(source, amount);
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }
}
