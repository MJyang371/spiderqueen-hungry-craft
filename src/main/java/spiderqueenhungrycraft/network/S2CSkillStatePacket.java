package spiderqueenhungrycraft.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * The bought skills of one player, as the server holds them (Skill Progression round).
 *
 * <p>It is the smallest picture of the growth system there is: the identities of the unlocked nodes and nothing else.
 * It is sent when the player joins, when she changes the world and when she respawns, and again whenever a skill is
 * bought or revoked, so the client always knows what its player owns.
 *
 * <p><b>It is a permission list, not a permission.</b> A client uses it for exactly one thing - to stay silent when a
 * key of a skill its player does not own is pressed - and the server never trusts a word of it: every ability is gated
 * again on the server, from the save file of that player. A modified client that believes it owns everything still
 * gets nothing.
 */
public class S2CSkillStatePacket
{
    /** A cap on the entries one packet may carry, so a broken sender can not make a client allocate forever. */
    private static final int MAX_SKILLS = 256;

    private final List<ResourceLocation> skills;

    public S2CSkillStatePacket(Collection<ResourceLocation> skills)
    {
        this.skills = List.copyOf(skills);
    }

    public S2CSkillStatePacket(FriendlyByteBuf buffer)
    {
        int size = Math.max(0, Math.min(buffer.readVarInt(), MAX_SKILLS));
        List<ResourceLocation> read = new ArrayList<>(size);

        for (int index = 0; index < size; index++)
        {
            read.add(buffer.readResourceLocation());
        }

        this.skills = List.copyOf(read);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.skills.size());

        for (ResourceLocation skill : this.skills)
        {
            buffer.writeResourceLocation(skill);
        }
    }

    /** @return the bought skills of the player this picture belongs to. */
    public List<ResourceLocation> skills()
    {
        return this.skills;
    }

    public static void handle(S2CSkillStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has keys to keep silent, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.SkillClientState.accept(packet.skills);
        }

        context.setPacketHandled(true);
    }
}
