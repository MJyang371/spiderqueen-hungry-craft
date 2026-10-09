package spiderqueenhungrycraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import spiderqueenhungrycraft.SpiderQueenHungryCraft;
import spiderqueenhungrycraft.beast.QueenBeastState;
import spiderqueenhungrycraft.network.C2SBeastChargePressPacket;
import spiderqueenhungrycraft.network.C2SBeastChargeReleasePacket;
import spiderqueenhungrycraft.network.C2SEmpoweredAttackPacket;
import spiderqueenhungrycraft.network.C2SNecromancyCastPacket;
import spiderqueenhungrycraft.network.C2SQueenBeastTransformPacket;
import spiderqueenhungrycraft.network.C2SQueenSniffPacket;
import spiderqueenhungrycraft.network.C2SWallCrawlTogglePacket;
import spiderqueenhungrycraft.network.C2SWebShotPacket;
import spiderqueenhungrycraft.network.HungryCraftNetwork;
import spiderqueenhungrycraft.progression.QueenSkills;

/**
 * Client side input. The client only requests a shot, the server decides whether it really happens.
 */
@Mod.EventBusSubscriber(modid = SpiderQueenHungryCraft.MODID, value = Dist.CLIENT)
public final class HungryCraftClientEvents
{
    /** Press edge memory of the charge key, so a held key never repeats its press. */
    private static boolean chargeWasDown;

    private HungryCraftClientEvents()
    {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.getConnection() == null)
        {
            return;
        }

        while (HungryCraftKeyMappings.WEB_SHOT.consumeClick())
        {
            HungryCraftNetwork.sendToServer(new C2SWebShotPacket());
        }

        while (HungryCraftKeyMappings.EMPOWERED_ATTACK.consumeClick())
        {
            // The stance of the queen is a bought skill (Skill Progression round). A locked key is silent: the click is
            // consumed and thrown away, so no packet leaves, nothing is said, no sound is played and nothing is
            // remembered - and the very same click can not fire later either, because it was never kept.
            if (SkillClientState.has(QueenSkills.QUEEN_STANCE))
            {
                HungryCraftNetwork.sendToServer(new C2SEmpoweredAttackPacket());
            }
        }

        while (HungryCraftKeyMappings.WALL_CRAWL.consumeClick())
        {
            // Normal queen: the key is the wall climb toggle, which is an innate ability and never locked. Beast: the
            // same key starts a charge, and the release below ends it. A beast never toggles the wall climb mode.
            if (QueenBeastState.isTransformed(minecraft.player) && SkillClientState.has(QueenSkills.QUEEN_BEAST))
            {
                HungryCraftNetwork.sendToServer(new C2SBeastChargePressPacket());
            }
            else
            {
                HungryCraftNetwork.sendToServer(new C2SWallCrawlTogglePacket());
            }
        }

        trackChargeKey(minecraft);

        while (HungryCraftKeyMappings.BEAST_TRANSFORM.consumeClick())
        {
            // The form itself is a bought skill (Skill Progression round). A locked key is silent: no packet leaves, so
            // the server never counts a tap, never asks for a full hunger bar and never says a word about it.
            if (SkillClientState.has(QueenSkills.QUEEN_BEAST))
            {
                HungryCraftNetwork.sendToServer(new C2SQueenBeastTransformPacket());
            }
        }

        while (HungryCraftKeyMappings.SNIFF.consumeClick())
        {
            // The sniff is a bought skill (Skill Progression round): locked, the key is a silent no-op. There is no
            // cooldown, no line and no scan on the client side of this at all - the whole sense is the server's.
            if (SkillClientState.has(QueenSkills.QUEEN_SNIFF))
            {
                HungryCraftNetwork.sendToServer(new C2SQueenSniffPacket());
            }
        }

        while (HungryCraftKeyMappings.NECROMANCY_IV.consumeClick())
        {
            // 亡灵契约 IV (Necromancy IV round): the same silence for a locked key. The skill belongs to an optional
            // compatibility, so whether there is anything for it to do at all is a question only the server answers -
            // it is the one that knows whether Ice and Fire is installed and what the crosshair really points at.
            if (SkillClientState.has(QueenSkills.NECROMANCY_IV))
            {
                HungryCraftNetwork.sendToServer(new C2SNecromancyCastPacket());
            }
        }
    }

    /**
     * The release edge of the charge key (Phase 8D). A beast only ever sends one press and one release per
     * physical key press, never a message per tick while the key is held.
     */
    private static void trackChargeKey(Minecraft minecraft)
    {
        boolean down = QueenBeastState.isTransformed(minecraft.player) && minecraft.screen == null
                && SkillClientState.has(QueenSkills.QUEEN_BEAST)
                && HungryCraftKeyMappings.WALL_CRAWL.isDown();

        if (!down && chargeWasDown)
        {
            HungryCraftNetwork.sendToServer(new C2SBeastChargeReleasePacket());
        }

        chargeWasDown = down;
    }
}
