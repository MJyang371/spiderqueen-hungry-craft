package spiderqueenhungrycraft.network;

import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.client.StomachSoundClient;

/**
 * One sound of a stomach, on its way to the owner of that stomach (Phase 8E2A-POLISH-2).
 *
 * <p>Only the server sends this, and only for a sound that really happened inside a loaded cell of that owner.
 * It carries the metadata of the sound and nothing else - no audio, no recording, no history: the client plays
 * the very same sound event again, at fifty percent of its volume, right at the body of the owner, as if the
 * noise came from inside them.
 *
 * <p>The owner UUID travels with it so a client can drop anything that is not meant for it, and the cell index
 * is carried for the debug output and for a future validation. The sound itself is a real resource location, so
 * a sound of another mod is relayed exactly like a vanilla one.
 */
public class S2CStomachSoundPacket
{
    private final UUID owner;
    private final int cellIndex;
    private final ResourceLocation sound;
    private final SoundSource source;
    private final float volume;
    private final float pitch;

    public S2CStomachSoundPacket(UUID owner, int cellIndex, ResourceLocation sound, SoundSource source, float volume,
            float pitch)
    {
        this.owner = owner;
        this.cellIndex = cellIndex;
        this.sound = sound;
        this.source = source;
        this.volume = volume;
        this.pitch = pitch;
    }

    public S2CStomachSoundPacket(FriendlyByteBuf buffer)
    {
        this.owner = buffer.readUUID();
        this.cellIndex = buffer.readVarInt();
        this.sound = buffer.readResourceLocation();
        this.source = buffer.readEnum(SoundSource.class);
        this.volume = buffer.readFloat();
        this.pitch = buffer.readFloat();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeUUID(this.owner);
        buffer.writeVarInt(this.cellIndex);
        buffer.writeResourceLocation(this.sound);
        buffer.writeEnum(this.source);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
    }

    public static void handle(S2CStomachSoundPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has a sound manager, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            StomachSoundClient.accept(packet);
        }

        context.setPacketHandled(true);
    }

    public UUID owner()
    {
        return this.owner;
    }

    public int cellIndex()
    {
        return this.cellIndex;
    }

    public ResourceLocation sound()
    {
        return this.sound;
    }

    public SoundSource source()
    {
        return this.source;
    }

    public float volume()
    {
        return this.volume;
    }

    public float pitch()
    {
        return this.pitch;
    }
}
