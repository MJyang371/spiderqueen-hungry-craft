package spiderqueenhungrycraft.network;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent;
import spiderqueenhungrycraft.beast.QueenBeastTuning;
import spiderqueenhungrycraft.client.QueenBeastClientTuning;

/**
 * Server to client sync of the beast tuning (Phase 8B-POLISH-2), sent on login and after every datapack
 * reload.
 *
 * <p>The camera impulse of the bite is a client presentation, but the numbers live in the datapack on the
 * server, so the client can only know them because the server told it. The armor and the bite sizes ride along
 * for debug overlays and for the log of a pack author, they are never used by the client to decide anything:
 * what really counts are the values the server holds when the bite is executed, which is why a client can
 * never talk itself into a longer reach or a cheaper cooldown.
 */
public class S2CBeastTuningPacket
{
    private final double armorBonus;
    private final float biteWidth;
    private final float biteHeight;
    private final float biteDepth;
    private final int shakeTicks;
    private final float shakePitch;
    private final float shakeYaw;
    private final float shakeRoll;
    private final float shakeFov;
    private final int jumpMaxChargeTicks;
    private final int stompShakeTicks;
    private final float stompShakePitch;
    private final float stompShakeYaw;
    private final float stompShakeRoll;
    private final float stompShakeFov;
    private final float chargeFovWidening;

    public S2CBeastTuningPacket(double armorBonus, float biteWidth, float biteHeight, float biteDepth,
            int shakeTicks, float shakePitch, float shakeYaw, float shakeRoll, float shakeFov,
            int jumpMaxChargeTicks, int stompShakeTicks, float stompShakePitch, float stompShakeYaw,
            float stompShakeRoll, float stompShakeFov, float chargeFovWidening)
    {
        this.armorBonus = armorBonus;
        this.biteWidth = biteWidth;
        this.biteHeight = biteHeight;
        this.biteDepth = biteDepth;
        this.shakeTicks = shakeTicks;
        this.shakePitch = shakePitch;
        this.shakeYaw = shakeYaw;
        this.shakeRoll = shakeRoll;
        this.shakeFov = shakeFov;
        this.jumpMaxChargeTicks = jumpMaxChargeTicks;
        this.stompShakeTicks = stompShakeTicks;
        this.stompShakePitch = stompShakePitch;
        this.stompShakeYaw = stompShakeYaw;
        this.stompShakeRoll = stompShakeRoll;
        this.stompShakeFov = stompShakeFov;
        this.chargeFovWidening = chargeFovWidening;
    }

    public S2CBeastTuningPacket(FriendlyByteBuf buffer)
    {
        this.armorBonus = buffer.readDouble();
        this.biteWidth = buffer.readFloat();
        this.biteHeight = buffer.readFloat();
        this.biteDepth = buffer.readFloat();
        this.shakeTicks = buffer.readVarInt();
        this.shakePitch = buffer.readFloat();
        this.shakeYaw = buffer.readFloat();
        this.shakeRoll = buffer.readFloat();
        this.shakeFov = buffer.readFloat();
        this.jumpMaxChargeTicks = buffer.readVarInt();
        this.stompShakeTicks = buffer.readVarInt();
        this.stompShakePitch = buffer.readFloat();
        this.stompShakeYaw = buffer.readFloat();
        this.stompShakeRoll = buffer.readFloat();
        this.stompShakeFov = buffer.readFloat();
        this.chargeFovWidening = buffer.readFloat();
    }

    /** The packet of the values the server currently uses. */
    public static S2CBeastTuningPacket ofCurrent()
    {
        QueenBeastTuning.Values values = QueenBeastTuning.get();
        QueenBeastTuning.Stomp stomp = values.stomp();

        return new S2CBeastTuningPacket(values.armorBonus(), (float) values.biteWidth(),
                (float) values.biteHeight(), (float) values.biteDepth(), values.shakeTicks(), values.shakePitch(),
                values.shakeYaw(), values.shakeRoll(), (float) values.shakeFov(), values.jump().maxChargeTicks(),
                stomp.shakeTicks(), stomp.shakePitch(), stomp.shakeYaw(), stomp.shakeRoll(),
                (float) stomp.shakeFov(),
                (float) spiderqueenhungrycraft.beast.QueenBeastConstants.BEAST_CHARGE_FOV_WIDENING);
    }

    public void encode(FriendlyByteBuf buffer)
    {
        buffer.writeDouble(this.armorBonus);
        buffer.writeFloat(this.biteWidth);
        buffer.writeFloat(this.biteHeight);
        buffer.writeFloat(this.biteDepth);
        buffer.writeVarInt(this.shakeTicks);
        buffer.writeFloat(this.shakePitch);
        buffer.writeFloat(this.shakeYaw);
        buffer.writeFloat(this.shakeRoll);
        buffer.writeFloat(this.shakeFov);
        buffer.writeVarInt(this.jumpMaxChargeTicks);
        buffer.writeVarInt(this.stompShakeTicks);
        buffer.writeFloat(this.stompShakePitch);
        buffer.writeFloat(this.stompShakeYaw);
        buffer.writeFloat(this.stompShakeRoll);
        buffer.writeFloat(this.stompShakeFov);
        buffer.writeFloat(this.chargeFovWidening);
    }

    public static void handle(S2CBeastTuningPacket packet, Supplier<NetworkEvent.Context> contextSupplier)
    {
        NetworkEvent.Context context = contextSupplier.get();

        // Only a client has a camera to shake, so the handler is a no-op on a dedicated server.
        if (FMLEnvironment.dist == Dist.CLIENT)
        {
            QueenBeastClientTuning.apply(packet.armorBonus, packet.biteWidth, packet.biteHeight,
                    packet.biteDepth, packet.shakeTicks, packet.shakePitch, packet.shakeYaw, packet.shakeRoll,
                    packet.shakeFov, packet.jumpMaxChargeTicks, packet.stompShakeTicks, packet.stompShakePitch,
                    packet.stompShakeYaw, packet.stompShakeRoll, packet.stompShakeFov, packet.chargeFovWidening);
        }

        context.setPacketHandled(true);
    }
}
