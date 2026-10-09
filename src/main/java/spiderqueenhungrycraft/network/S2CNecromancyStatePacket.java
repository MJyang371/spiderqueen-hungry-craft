package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;

/**
 * Server to client mirror of the necromancy marker of one dragon (Necromancy IV prototype 2).
 *
 * <p><b>Why this packet exists.</b> The marker itself lives in the Forge persistent data of the entity
 * ({@code spiderqueenhungrycraft.compat.iceandfire.NecromancyMarker}), and that compound tag never crosses the wire:
 * Forge writes it in {@code Entity#saveWithoutId} and reads it in {@code Entity#load}, and nothing else - neither
 * {@code ClientboundAddEntityPacket} nor {@code ClientboundSetEntityDataPacket} nor {@code ServerEntity} mentions it.
 * The rendering thread of a client therefore can <b>not</b> see a marker, and a pure client side hook (the one that
 * keeps a revived dragon looking like bones) has no way to ask about it.
 *
 * <p>So the server tells it. This packet carries the value of the marker for one entity, and nothing else:
 * <ul>
 * <li>it is sent when a dragon is woken up and when it is put back, to everybody who can see it,</li>
 * <li>it is sent again to a single player the moment that player starts tracking the dragon, which is what makes the
 * skeleton survive a relog, a chunk reload and a dimension change without any further bookkeeping.</li>
 * </ul>
 *
 * <p>The client keeps the answer in {@code ClientNecromancyState} and never answers back: like every other server to
 * client packet of this mod, this one is an answer and never a request.
 */
public class S2CNecromancyStatePacket
{
    private final int entityId;
    private final boolean revived;
    private final boolean mine;
    private final int stance;
    private final int order;
    private final boolean canBreathe;

    /** The answer of a dragon that is not the one of the receiving player: only the marker, as it always was. */
    public S2CNecromancyStatePacket(int entityId, boolean revived)
    {
        this(entityId, revived, false, 0, 0, false);
    }

    /** The fuller answer the owner of a contract is told, so that her inspect HUD can show the state of her skeleton. */
    public S2CNecromancyStatePacket(int entityId, boolean revived, boolean mine, int stance, int order,
            boolean canBreathe)
    {
        this.entityId = entityId;
        this.revived = revived;
        this.mine = mine;
        this.stance = stance;
        this.order = order;
        this.canBreathe = canBreathe;
    }

    public S2CNecromancyStatePacket(FriendlyByteBuf buffer)
    {
        this.entityId = buffer.readVarInt();
        this.revived = buffer.readBoolean();
        this.mine = buffer.readBoolean();
        this.stance = buffer.readVarInt();
        this.order = buffer.readVarInt();
        this.canBreathe = buffer.readBoolean();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeVarInt(this.entityId);
        buffer.writeBoolean(this.revived);
        buffer.writeBoolean(this.mine);
        buffer.writeVarInt(this.stance);
        buffer.writeVarInt(this.order);
        buffer.writeBoolean(this.canBreathe);
    }

    /** @return the entity id the marker belongs to. */
    public int entityId()
    {
        return this.entityId;
    }

    /** @return true when the server says that HungryCraft has that dragon woken up right now. */
    public boolean revived()
    {
        return this.revived;
    }

    /** @return true when that dragon is under the contract of the player this packet was sent to. */
    public boolean mine()
    {
        return this.mine;
    }

    /** @return the ordinal of the standing order of that contract, see {@code SpiderOrderMode}. */
    public int stance()
    {
        return this.stance;
    }

    /** @return the ordinal of the errand of that contract, see {@code NecromancyContract.Order}. */
    public int order()
    {
        return this.order;
    }

    /** @return true when that dragon is old enough to be sent on a bombardment. */
    public boolean canBreathe()
    {
        return this.canBreathe;
    }

    public static void handle(S2CNecromancyStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client draws bones, so the handler stays a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.compat.iceandfire.ClientNecromancyState.set(packet.entityId(), packet.revived(),
                    packet.mine(), packet.stance(), packet.order(), packet.canBreathe());
        }

        context.setPacketHandled(true);
    }
}
