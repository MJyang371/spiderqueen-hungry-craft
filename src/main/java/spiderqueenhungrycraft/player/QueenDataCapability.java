package spiderqueenhungrycraft.player;

import java.util.Optional;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;

/**
 * Registers the Spider Queen player capability and exposes the small helper API gameplay code is
 * supposed to use, so nobody has to look the capability up by hand.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class QueenDataCapability
{
    public static final Capability<QueenData> QUEEN_DATA = CapabilityManager.get(new CapabilityToken<>()
    {
    });

    private QueenDataCapability()
    {
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(QueenData.class);
    }

    public static Optional<QueenData> get(Player player)
    {
        return player.getCapability(QUEEN_DATA).resolve();
    }

    public static boolean isQueen(Player player)
    {
        return get(player).map(QueenData::isQueen).orElse(false);
    }

    public static void setQueen(Player player, boolean value)
    {
        get(player).ifPresent(data -> data.setQueen(value));
    }
}
