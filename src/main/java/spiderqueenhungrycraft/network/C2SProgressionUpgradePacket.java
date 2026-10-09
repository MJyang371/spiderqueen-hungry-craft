package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.server.level.ServerPlayer;
import spiderqueenhungrycraft.progression.ProgressionFeedback;
import spiderqueenhungrycraft.progression.ProgressionService;

/**
 * Client to server request to raise the Queen Level (Progression Foundation round).
 *
 * <p>It carries the level the player clicked and the core whose interface was open, and that is all it is: a request.
 * The client says "I would like to reach level thirty eight, from this core", and the server decides everything else -
 * whether that core is open for that player, whether thirty eight is the next level, whether it is inside the maximum
 * of the definition and whether the player can pay. A modified client can ask for whatever it wants and will be
 * answered by the very same rules.
 */
public class C2SProgressionUpgradePacket
{
    private final BlockPos corePos;
    private final int targetLevel;

    public C2SProgressionUpgradePacket(BlockPos corePos, int targetLevel)
    {
        this.corePos = corePos;
        this.targetLevel = targetLevel;
    }

    public C2SProgressionUpgradePacket(FriendlyByteBuf buffer)
    {
        this.corePos = buffer.readBlockPos();
        this.targetLevel = buffer.readVarInt();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(this.corePos);
        buffer.writeVarInt(this.targetLevel);
    }

    public static void handle(C2SProgressionUpgradePacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            ProgressionFeedback feedback = ProgressionService.upgrade(sender, packet.corePos, packet.targetLevel);

            HungryCraftNetwork.sendToPlayer(new S2CProgressionFeedbackPacket(feedback), sender);

            // A level that really changed is a snapshot that really changed: the screen draws the new state and the
            // Soul number of the HUD rolls down through the ordinary soul sync of the spending itself.
            if (feedback == ProgressionFeedback.UPGRADE_OK)
            {
                ProgressionService.sendSnapshot(sender, packet.corePos);
            }
        }

        context.setPacketHandled(true);
    }
}
