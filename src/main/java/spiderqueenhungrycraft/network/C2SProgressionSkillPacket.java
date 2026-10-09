package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.progression.ProgressionFeedback;
import spiderqueenhungrycraft.progression.ProgressionService;

/**
 * Client to server request to buy one skill node (Skill Progression round).
 *
 * <p>It names the node and the core, and nothing else - not the price, not the level and not the prerequisites, all of
 * which come from the definition the server holds. The client says "I would like to buy this node, at this core", and
 * the server looks the node up again, decides whether her level and her prerequisites allow it, whether she owns it
 * already and whether she can pay. A modified client can ask for whatever it wants and is answered by the very same
 * rules.
 */
public class C2SProgressionSkillPacket
{
    private final BlockPos corePos;
    private final ResourceLocation skillId;

    public C2SProgressionSkillPacket(BlockPos corePos, ResourceLocation skillId)
    {
        this.corePos = corePos;
        this.skillId = skillId;
    }

    public C2SProgressionSkillPacket(FriendlyByteBuf buffer)
    {
        this.corePos = buffer.readBlockPos();
        this.skillId = buffer.readResourceLocation();
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeBlockPos(this.corePos);
        buffer.writeResourceLocation(this.skillId);
    }

    public static void handle(C2SProgressionSkillPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();

        if (sender != null)
        {
            ProgressionFeedback feedback = ProgressionService.unlockSkill(sender, packet.corePos, packet.skillId);

            HungryCraftNetwork.sendToPlayer(new S2CProgressionFeedbackPacket(feedback), sender);

            // A skill that was really bought changes two pictures at once: the node of the interface, and the set of
            // keys the client has to keep silent (or to let through) while its player runs around. Both are sent here,
            // and the Soul HUD of the player rolls down through the ordinary soul sync of the spending itself.
            if (feedback == ProgressionFeedback.SKILL_OK)
            {
                ProgressionService.sendSkillState(sender);
                ProgressionService.sendSnapshot(sender, packet.corePos);
            }
        }

        context.setPacketHandled(true);
    }
}
