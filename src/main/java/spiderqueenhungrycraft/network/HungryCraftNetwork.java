package spiderqueenhungrycraft.network;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.bed.SpiderBedHibernation;

/** Minimal networking of the mod: a single client to server channel. */
public final class HungryCraftNetwork
{
    private static final String PROTOCOL_VERSION = "18";

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(ResourceLocation.fromNamespaceAndPath(SpiderQueenHungryCraft.MODID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    private HungryCraftNetwork()
    {
    }

    /**
     * Wraps a client to server action of the mod, so a queen who is hibernating in her spider bed can never
     * act: her web shot, her venom, her wall crawl, her beast form and every beast skill are refused before
     * the handler even runs, whatever a modified client sends.
     *
     * <p>Only requests are wrapped with this, never an answer to something the server asked for.
     */
    private static <T> BiConsumer<T, Supplier<NetworkEvent.Context>> actionGuard(
            BiConsumer<T, Supplier<NetworkEvent.Context>> handler)
    {
        return (message, contextSupplier) ->
        {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();

            if (sender != null && SpiderBedHibernation.blocksActions(sender))
            {
                context.setPacketHandled(true);
                return;
            }

            handler.accept(message, contextSupplier);
        };
    }

    public static void register()
    {
        int id = 0;

        CHANNEL.messageBuilder(C2SWebShotPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWebShotPacket::encode)
                .decoder(C2SWebShotPacket::new)
                .consumerMainThread(actionGuard(C2SWebShotPacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SEmpoweredAttackPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SEmpoweredAttackPacket::encode)
                .decoder(C2SEmpoweredAttackPacket::new)
                .consumerMainThread(actionGuard(C2SEmpoweredAttackPacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SWallCrawlTogglePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SWallCrawlTogglePacket::encode)
                .decoder(C2SWallCrawlTogglePacket::new)
                .consumerMainThread(actionGuard(C2SWallCrawlTogglePacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CWallClimbStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CWallClimbStatePacket::encode)
                .decoder(S2CWallClimbStatePacket::new)
                .consumerMainThread(S2CWallClimbStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SQueenBeastTransformPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SQueenBeastTransformPacket::encode)
                .decoder(C2SQueenBeastTransformPacket::new)
                .consumerMainThread(actionGuard(C2SQueenBeastTransformPacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CQueenBeastStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CQueenBeastStatePacket::encode)
                .decoder(S2CQueenBeastStatePacket::new)
                .consumerMainThread(S2CQueenBeastStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SBeastBitePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBeastBitePacket::encode)
                .decoder(C2SBeastBitePacket::new)
                .consumerMainThread(actionGuard(C2SBeastBitePacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CBeastBiteFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeastBiteFeedbackPacket::encode)
                .decoder(S2CBeastBiteFeedbackPacket::new)
                .consumerMainThread(S2CBeastBiteFeedbackPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CBeastHurtFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeastHurtFeedbackPacket::encode)
                .decoder(S2CBeastHurtFeedbackPacket::new)
                .consumerMainThread(S2CBeastHurtFeedbackPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CBeastTuningPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeastTuningPacket::encode)
                .decoder(S2CBeastTuningPacket::new)
                .consumerMainThread(S2CBeastTuningPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SBeastJumpPressPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBeastJumpPressPacket::encode)
                .decoder(C2SBeastJumpPressPacket::new)
                .consumerMainThread(actionGuard(C2SBeastJumpPressPacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SBeastJumpReleasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBeastJumpReleasePacket::encode)
                .decoder(C2SBeastJumpReleasePacket::new)
                .consumerMainThread(actionGuard(C2SBeastJumpReleasePacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SBeastChargePressPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBeastChargePressPacket::encode)
                .decoder(C2SBeastChargePressPacket::new)
                .consumerMainThread(actionGuard(C2SBeastChargePressPacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SBeastChargeReleasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBeastChargeReleasePacket::encode)
                .decoder(C2SBeastChargeReleasePacket::new)
                .consumerMainThread(actionGuard(C2SBeastChargeReleasePacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CBeastStompFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeastStompFeedbackPacket::encode)
                .decoder(S2CBeastStompFeedbackPacket::new)
                .consumerMainThread(S2CBeastStompFeedbackPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CBeastChargeStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBeastChargeStatePacket::encode)
                .decoder(S2CBeastChargeStatePacket::new)
                .consumerMainThread(S2CBeastChargeStatePacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SStomachWallAttackPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SStomachWallAttackPacket::encode)
                .decoder(C2SStomachWallAttackPacket::new)
                .consumerMainThread(actionGuard(C2SStomachWallAttackPacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CStomachStatusPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CStomachStatusPacket::encode)
                .decoder(S2CStomachStatusPacket::new)
                .consumerMainThread(S2CStomachStatusPacket::handle)
                .add();
        CHANNEL.messageBuilder(S2CStomachSoundPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CStomachSoundPacket::encode)
                .decoder(S2CStomachSoundPacket::new)
                .consumerMainThread(S2CStomachSoundPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CDevourFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CDevourFeedbackPacket::encode)
                .decoder(S2CDevourFeedbackPacket::new)
                .consumerMainThread(S2CDevourFeedbackPacket::handle)
                .add();

        CHANNEL.messageBuilder(C2SDevourInputPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SDevourInputPacket::encode)
                .decoder(C2SDevourInputPacket::new)
                .consumerMainThread(actionGuard(C2SDevourInputPacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CSpiderBedHibernationPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSpiderBedHibernationPacket::encode)
                .decoder(S2CSpiderBedHibernationPacket::new)
                .consumerMainThread(S2CSpiderBedHibernationPacket::handle)
                .add();

        // Deliberately not guarded: getting up again has to work exactly while a hibernation runs.
        CHANNEL.messageBuilder(C2SSpiderBedLeaveHibernationPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSpiderBedLeaveHibernationPacket::encode)
                .decoder(C2SSpiderBedLeaveHibernationPacket::new)
                .consumerMainThread(C2SSpiderBedLeaveHibernationPacket::handle)
                .add();

        // The protected range of a territory core goes to the one player who asked for it (Phase LAIR-CORE-1).
        CHANNEL.messageBuilder(S2CTerritoryRangePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CTerritoryRangePacket::encode)
                .decoder(S2CTerritoryRangePacket::new)
                .consumerMainThread(S2CTerritoryRangePacket::handle)
                .add();

        // The silk of a queen (feature SILK-CAPACITY) is a value only the server decides, and her own client
        // needs it to draw the web bar. It only ever travels to the player herself.
        CHANNEL.messageBuilder(S2CQueenSilkPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CQueenSilkPacket::encode)
                .decoder(S2CQueenSilkPacket::new)
                .consumerMainThread(S2CQueenSilkPacket::handle)
                .add();

        // The queen sceptre as a long range command tool (Queen Scepter command patch): the client asks, the server
        // casts the command ray itself and answers only when a command really happened.
        CHANNEL.messageBuilder(C2SQueenScepterUsePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SQueenScepterUsePacket::encode)
                .decoder(C2SQueenScepterUsePacket::new)
                .consumerMainThread(actionGuard(C2SQueenScepterUsePacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CQueenScepterPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CQueenScepterPacket::encode)
                .decoder(S2CQueenScepterPacket::new)
                .consumerMainThread(S2CQueenScepterPacket::handle)
                .add();

        // The brood ability key (Brood Ability framework): the client only says "the key was pressed", the server
        // decides which spiders answer and what their abilities do.
        CHANNEL.messageBuilder(C2SBroodAbilityCastPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBroodAbilityCastPacket::encode)
                .decoder(C2SBroodAbilityCastPacket::new)
                .consumerMainThread(actionGuard(C2SBroodAbilityCastPacket::handle))
                .add();

        // The five selection keys of the brood (Brood Selection + Inspect HUD round): one request per press, and the
        // only payload that ever travels is the list of spiders this client thinks are on screen.
        CHANNEL.messageBuilder(C2SBroodSelectionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SBroodSelectionPacket::encode)
                .decoder(C2SBroodSelectionPacket::new)
                .consumerMainThread(actionGuard(C2SBroodSelectionPacket::handle))
                .add();

        // The drag selection of the sceptre (Brood RTS Mouse Selection round): two points, and the server works out the
        // rest from its own entities.
        CHANNEL.messageBuilder(C2SSelectionBoxPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSelectionBoxPacket::encode)
                .decoder(C2SSelectionBoxPacket::new)
                .consumerMainThread(actionGuard(C2SSelectionBoxPacket::handle))
                .add();

        // The echo of a selection (Brood Command Group round): the server owns the selection, the queen only draws it.
        CHANNEL.messageBuilder(S2CBroodSelectionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CBroodSelectionPacket::encode)
                .decoder(S2CBroodSelectionPacket::new)
                .consumerMainThread(S2CBroodSelectionPacket::handle)
                .add();

        // The sniff of the queen (Spider Trigger round follow-up): the key asks, the server pays the hunger, waits out
        // the cooldown and hands out the outlines.
        CHANNEL.messageBuilder(C2SQueenSniffPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SQueenSniffPacket::encode)
                .decoder(C2SQueenSniffPacket::new)
                .consumerMainThread(actionGuard(C2SQueenSniffPacket::handle))
                .add();

        // The souls of a player (Soul Foundation V1): one small server to client packet per change, plus one after
        // every login, respawn and dimension change. The client never answers it and never decides an amount.
        CHANNEL.messageBuilder(S2CSoulsPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSoulsPacket::encode)
                .decoder(S2CSoulsPacket::new)
                .consumerMainThread(S2CSoulsPacket::handle)
                .add();

        // The growth interface (Progression Foundation round). Two requests of a client - raise the level, buy a
        // product - and two answers of the server: the state to draw and the verdict on what it just asked for.
        CHANNEL.messageBuilder(C2SProgressionUpgradePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SProgressionUpgradePacket::encode)
                .decoder(C2SProgressionUpgradePacket::new)
                .consumerMainThread(actionGuard(C2SProgressionUpgradePacket::handle))
                .add();

        CHANNEL.messageBuilder(C2SProgressionPurchasePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SProgressionPurchasePacket::encode)
                .decoder(C2SProgressionPurchasePacket::new)
                .consumerMainThread(actionGuard(C2SProgressionPurchasePacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CProgressionSnapshotPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CProgressionSnapshotPacket::encode)
                .decoder(S2CProgressionSnapshotPacket::new)
                .consumerMainThread(S2CProgressionSnapshotPacket::handle)
                .add();

        CHANNEL.messageBuilder(S2CProgressionFeedbackPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CProgressionFeedbackPacket::encode)
                .decoder(S2CProgressionFeedbackPacket::new)
                .consumerMainThread(S2CProgressionFeedbackPacket::handle)
                .add();

        // The skill tree (Skill Progression round): the purchase of one node, and the list of the bought ones. The
        // list is what keeps the key of a locked skill silent on the client while its player runs around, so it is
        // sent on every login, respawn and dimension change as well as after every purchase and revoke.
        CHANNEL.messageBuilder(C2SProgressionSkillPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SProgressionSkillPacket::encode)
                .decoder(C2SProgressionSkillPacket::new)
                .consumerMainThread(actionGuard(C2SProgressionSkillPacket::handle))
                .add();

        CHANNEL.messageBuilder(S2CSkillStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSkillStatePacket::encode)
                .decoder(S2CSkillStatePacket::new)
                .consumerMainThread(S2CSkillStatePacket::handle)
                .add();

        // 亡灵契约 IV (Necromancy IV round): the key asks, the server decides. No payload, so no client can name a
        // target of its own - the server casts the ray from the real eyes of the real player.
        CHANNEL.messageBuilder(C2SNecromancyCastPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SNecromancyCastPacket::encode)
                .decoder(C2SNecromancyCastPacket::new)
                .consumerMainThread(actionGuard(C2SNecromancyCastPacket::handle))
                .add();

        // The optional Ice and Fire compatibility (Necromancy IV prototype 2): the server tells a client which dragons
        // it has woken up. The value mirrors the necromancy marker, which lives in the persistent data of the entity
        // and is never part of any entity packet, so the rendering thread of a client could not learn it any other
        // way. Sent to everybody who can see the dragon, and again to each player who starts tracking it.
        CHANNEL.messageBuilder(S2CNecromancyStatePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CNecromancyStatePacket::encode)
                .decoder(S2CNecromancyStatePacket::new)
                .consumerMainThread(S2CNecromancyStatePacket::handle)
                .add();

    }

    /** Client side helper. */
    public static void sendToServer(Object message)
    {
        CHANNEL.sendToServer(message);
    }

    /** Server side helper. */
    public static void sendToPlayer(Object message, ServerPlayer player)
    {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Server side helper: everyone who can currently see that entity, including the entity itself. */
    public static void sendToTrackingAndSelf(Object message, ServerPlayer player)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), message);
    }

    /** Server side helper: everyone who can currently see that entity. */
    public static void sendToTrackingEntity(Object message, net.minecraft.world.entity.Entity entity)
    {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), message);
    }
}
