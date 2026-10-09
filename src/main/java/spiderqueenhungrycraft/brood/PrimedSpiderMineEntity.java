package spiderqueenhungrycraft.brood;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import spiderqueenhungrycraft.registry.HungryCraftEntities;

/**
 * The primed delayed spider mine (Spider Trigger round): the block that already left the world and is about to go off.
 *
 * <p><b>It is the block of TNT of the game.</b> It extends {@link PrimedTnt}, so the fall, the little hop it starts
 * with, the fuse of four seconds, the smoke it breathes, the white flash the renderer paints over it and the fact
 * that a punch or an explosion can set it off early all come from the game itself and none of it is written here.
 *
 * <p><b>What is written here is the one method the game calls when the fuse runs out.</b> Instead of the plain blast
 * of TNT it hands over to {@link SpiderMineExplosion#blast}, which is the very same method a spider mine block uses:
 * the eight imp spiders of the placer, the window that lets them walk out of their own blast, and - when the sceptre
 * was the trigger - those eight as the selection of their queen.
 *
 * <p>The placer travels with the entity and not with the click, exactly like it does for the block: a delayed mine a
 * stranger sets off still gives its spiders to the queen who placed it, and it still does so four seconds later, when
 * that queen may already be somewhere else entirely.
 */
public class PrimedSpiderMineEntity extends PrimedTnt
{
    /** The fuse of a block of TNT: four seconds, and the "few seconds of white flashing" of the design. */
    public static final int FUSE_TICKS = 80;

    /** NBT key of the placer of the mine this entity came out of. */
    private static final String KEY_OWNER = "MineOwner";

    /** NBT key of the trigger, because a fuse can outlive the moment it was lit. */
    private static final String KEY_BY_SCEPTER = "MineByScepter";

    /** The queen of the eight spiders this entity is about to deliver. */
    @Nullable
    private UUID mineOwner;

    /** True when the sceptre of the queen lit this fuse, which is the trigger that hands the spiders over as a group. */
    private boolean byScepter;

    public PrimedSpiderMineEntity(EntityType<? extends PrimedSpiderMineEntity> entityType, Level level)
    {
        super(entityType, level);
    }

    /** The entity of one fired delayed mine, built exactly like the block of TNT of the game builds one. */
    public PrimedSpiderMineEntity(Level level, double x, double y, double z, @Nullable UUID owner, boolean byScepter)
    {
        super(HungryCraftEntities.PRIMED_SPIDER_MINE.get(), level);

        this.setPos(x, y, z);

        // The little hop of a freshly primed block of TNT, so a mine that was set off looks alive.
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        this.setDeltaMovement(-Math.sin(angle) * 0.02D, 0.2D, -Math.cos(angle) * 0.02D);
        this.setFuse(FUSE_TICKS);
        this.xo = x;
        this.yo = y;
        this.zo = z;

        this.mineOwner = owner;
        this.byScepter = byScepter;
    }

    /**
     * The end of the fuse: the blast of a spider mine, from where this entity has fallen to in the meantime.
     *
     * <p>It is deliberately the very method the block uses and not a copy of it, so a delayed mine and an immediate
     * one can never deliver a different brood or protect it differently.
     */
    @Override
    protected void explode()
    {
        if (this.level() instanceof ServerLevel server)
        {
            SpiderMineExplosion.blast(server, new Vec3(this.getX(), this.getY(0.0625D), this.getZ()), this.mineOwner,
                    this.byScepter, this);
        }
    }

    /** @return the placer of the mine this entity came out of, or null when it had none. */
    @Nullable
    public UUID getMineOwner()
    {
        return this.mineOwner;
    }

    /** @return true when the sceptre of the queen lit this fuse. */
    public boolean isByScepter()
    {
        return this.byScepter;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);

        if (this.mineOwner != null)
        {
            tag.putUUID(KEY_OWNER, this.mineOwner);
        }

        tag.putBoolean(KEY_BY_SCEPTER, this.byScepter);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID(KEY_OWNER))
        {
            this.mineOwner = tag.getUUID(KEY_OWNER);
        }

        this.byScepter = tag.getBoolean(KEY_BY_SCEPTER);
    }
}
