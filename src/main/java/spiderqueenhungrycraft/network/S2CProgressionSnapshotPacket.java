package spiderqueenhungrycraft.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.progression.ProgressionDefinition;
import spiderqueenhungrycraft.progression.ProgressionSnapshot;
import spiderqueenhungrycraft.progression.SkillNodeDefinition;

/**
 * The growth interface of one player, as the server holds it (Progression Foundation round).
 *
 * <p>It carries everything the screen draws and nothing it does not need: the core the interface belongs to, the
 * Queen Level, the souls, the four numbers of the pricing rule and the products that are for sale. It is sent when
 * the interface is opened and again after every accepted request, so the screen is always a picture of the state the
 * server enforced - never of a data pack the client happens to have installed.
 *
 * <p>The prices travel as whole numbers and the soul balance as a {@code long}, so no price and no balance is ever
 * rounded on the way.
 */
public class S2CProgressionSnapshotPacket
{
    /** A cap on the products one snapshot may carry, so a broken sender can not make a client allocate forever. */
    private static final int MAX_SHOP_ENTRIES = 512;

    /** The same for the nodes of the tree, and for the prerequisites of one node. */
    private static final int MAX_SKILLS = 256;

    private final ProgressionSnapshot snapshot;

    public S2CProgressionSnapshotPacket(ProgressionSnapshot snapshot)
    {
        this.snapshot = snapshot;
    }

    public S2CProgressionSnapshotPacket(FriendlyByteBuf buffer)
    {
        this.snapshot = read(buffer);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        write(buffer, this.snapshot);
    }

    public ProgressionSnapshot snapshot()
    {
        return this.snapshot;
    }

    private static void write(FriendlyByteBuf buffer, ProgressionSnapshot snapshot)
    {
        buffer.writeBlockPos(snapshot.corePos());
        buffer.writeVarInt(snapshot.queenLevel());
        buffer.writeVarLong(snapshot.souls());

        ProgressionDefinition definition = snapshot.definition();
        buffer.writeVarInt(definition.maxLevel());
        buffer.writeVarInt(definition.baseCost());
        buffer.writeVarInt(definition.levelsPerTier());
        buffer.writeVarInt(definition.tierIncrement());

        buffer.writeBoolean(snapshot.legendaryEnabled());
        buffer.writeBoolean(snapshot.legendaryEligible());

        buffer.writeVarInt(snapshot.shop().size());

        for (ProgressionSnapshot.ShopEntryView entry : snapshot.shop())
        {
            buffer.writeResourceLocation(entry.id());
            buffer.writeResourceLocation(entry.itemId());
            buffer.writeVarInt(entry.count());
            buffer.writeVarLong(entry.price());
        }

        buffer.writeVarInt(snapshot.skills().size());

        for (ProgressionSnapshot.SkillNodeView node : snapshot.skills())
        {
            buffer.writeResourceLocation(node.id());
            buffer.writeVarInt(node.requiredLevel());
            buffer.writeVarLong(node.soulCost());
            buffer.writeVarInt(node.anchorLevel());
            buffer.writeEnum(node.side());
            buffer.writeVarInt(node.lane());
            buffer.writeVarInt(node.parents().size());

            for (ResourceLocation parent : node.parents())
            {
                buffer.writeResourceLocation(parent);
            }

            buffer.writeResourceLocation(node.icon());
            buffer.writeUtf(node.nameKey());
            buffer.writeUtf(node.descriptionKey());
            buffer.writeEnum(node.inputHint());
        }
    }

    private static ProgressionSnapshot read(FriendlyByteBuf buffer)
    {
        BlockPos corePos = buffer.readBlockPos();
        int queenLevel = buffer.readVarInt();
        long souls = buffer.readVarLong();
        ProgressionDefinition definition = new ProgressionDefinition(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readVarInt(), buffer.readVarInt());
        boolean legendaryEnabled = buffer.readBoolean();
        boolean legendaryEligible = buffer.readBoolean();
        int size = Math.max(0, Math.min(buffer.readVarInt(), MAX_SHOP_ENTRIES));
        List<ProgressionSnapshot.ShopEntryView> shop = new ArrayList<>(size);

        for (int index = 0; index < size; index++)
        {
            shop.add(new ProgressionSnapshot.ShopEntryView(buffer.readResourceLocation(),
                    buffer.readResourceLocation(), buffer.readVarInt(), buffer.readVarLong()));
        }

        int skillCount = Math.max(0, Math.min(buffer.readVarInt(), MAX_SKILLS));
        List<ProgressionSnapshot.SkillNodeView> skills = new ArrayList<>(skillCount);

        for (int index = 0; index < skillCount; index++)
        {
            ResourceLocation id = buffer.readResourceLocation();
            int requiredLevel = buffer.readVarInt();
            long soulCost = buffer.readVarLong();
            int anchorLevel = buffer.readVarInt();
            SkillNodeDefinition.Side side = buffer.readEnum(SkillNodeDefinition.Side.class);
            int lane = buffer.readVarInt();
            int parentCount = Math.max(0, Math.min(buffer.readVarInt(), MAX_SKILLS));
            List<ResourceLocation> parents = new ArrayList<>(parentCount);

            for (int parent = 0; parent < parentCount; parent++)
            {
                parents.add(buffer.readResourceLocation());
            }

            ResourceLocation icon = buffer.readResourceLocation();
            String nameKey = buffer.readUtf();
            String descriptionKey = buffer.readUtf();
            SkillNodeDefinition.InputHint inputHint = buffer.readEnum(SkillNodeDefinition.InputHint.class);

            skills.add(new ProgressionSnapshot.SkillNodeView(id, requiredLevel, soulCost, anchorLevel, side, lane,
                    List.copyOf(parents), icon, nameKey, descriptionKey, inputHint));
        }

        return new ProgressionSnapshot(corePos, queenLevel, souls, definition, List.copyOf(shop), List.copyOf(skills),
                legendaryEnabled, legendaryEligible);
    }

    public static void handle(S2CProgressionSnapshotPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client can open or refresh a screen, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            spiderqueenhungrycraft.client.ProgressionClientState.acceptSnapshot(packet.snapshot);
        }

        context.setPacketHandled(true);
    }
}
