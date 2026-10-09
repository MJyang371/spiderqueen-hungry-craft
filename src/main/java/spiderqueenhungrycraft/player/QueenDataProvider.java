package spiderqueenhungrycraft.player;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Capability provider attached to every player entity. Serialization is delegated to
 * {@link QueenData} so the data is stored in the entity's "ForgeCaps" tag and therefore survives
 * world reloads.
 */
public class QueenDataProvider implements ICapabilitySerializable<CompoundTag>
{
    private final QueenData data = new QueenData();
    private final LazyOptional<QueenData> optionalData = LazyOptional.of(() -> this.data);

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side)
    {
        return QueenDataCapability.QUEEN_DATA.orEmpty(cap, this.optionalData);
    }

    @Override
    public CompoundTag serializeNBT()
    {
        return this.data.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        this.data.deserializeNBT(nbt);
    }

    /**
     * Invalidates the wrapped optional. Called when the owning player is removed so nothing keeps
     * handing out the data of a dead provider.
     */
    public void invalidate()
    {
        this.optionalData.invalidate();
    }
}
